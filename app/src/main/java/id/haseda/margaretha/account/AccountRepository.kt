package id.haseda.margaretha.account

import android.net.Uri
import id.haseda.margaretha.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface AccountState {
    /** This build has no backend configured; the app works in local mode only. */
    data object Unconfigured : AccountState
    data object SignedOut : AccountState

    /** [needsNewPassword] is set after the user opened a password-recovery link. */
    data class SignedIn(val user: AccountUser, val needsNewPassword: Boolean = false) : AccountState
}

/**
 * Single source of truth for who is signed in. The stored session makes the account available
 * offline; tokens are refreshed lazily and a refresh that the server rejects ends the session.
 */
class AccountRepository(private val store: SessionStore) {
    private val api: AuthApi? =
        if (BuildConfig.SUPABASE_URL.startsWith("https://") && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()) {
            AuthApi(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
        } else null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val refreshLock = Mutex()
    private var session: AccountSession? = null

    private val _state = MutableStateFlow<AccountState>(if (api == null) AccountState.Unconfigured else AccountState.SignedOut)
    val state: StateFlow<AccountState> = _state.asStateFlow()

    /** One-shot notices for the UI (session expired, email verified, ...). */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()
    fun consumeNotice() { _notice.value = null }

    val isConfigured: Boolean get() = api != null

    /** Owner id stamped on new projects and used to filter the project list; empty when signed out. */
    val ownerId: String get() = (_state.value as? AccountState.SignedIn)?.user?.id.orEmpty()

    init {
        if (api != null) {
            store.load()?.let { saved ->
                session = saved
                _state.value = AccountState.SignedIn(saved.user)
                // Confirm the session in the background; staying offline keeps the user signed in.
                scope.launch { validToken() }
            }
        }
    }

    private fun adopt(newSession: AccountSession, needsNewPassword: Boolean = false) {
        session = newSession
        store.save(newSession)
        _state.value = AccountState.SignedIn(newSession.user, needsNewPassword)
    }

    private fun drop(notice: String? = null) {
        session = null
        store.clear()
        _state.value = AccountState.SignedOut
        if (notice != null) _notice.value = notice
    }

    /** A token that is valid for at least a minute, refreshing if needed; null when signed out or offline. */
    private suspend fun validToken(): String? = refreshLock.withLock {
        val api = api ?: return null
        val current = session ?: return null
        if (current.expiresAtEpochSec - System.currentTimeMillis() / 1000 > 60) return current.accessToken
        when (val r = api.refresh(current.refreshToken)) {
            is ApiResult.Ok -> {
                adopt(r.value, (_state.value as? AccountState.SignedIn)?.needsNewPassword == true)
                r.value.accessToken
            }
            is ApiResult.Failed -> {
                // Only a real rejection ends the session; a network failure does not.
                if (!r.network && r.status in 400..499) drop("Sesi berakhir. Silakan masuk lagi.")
                null
            }
        }
    }

    private suspend fun <T> withToken(block: suspend (AuthApi, String) -> ApiResult<T>): ApiResult<T> {
        val api = api ?: return NOT_CONFIGURED
        val token = validToken() ?: return if (session == null) ApiResult.Failed("Kamu belum masuk.") else OFFLINE
        return block(api, token)
    }

    suspend fun signUp(email: String, password: String, displayName: String): ApiResult<Boolean> {
        val api = api ?: return NOT_CONFIGURED
        return when (val r = api.signUp(email.trim(), password, displayName.trim())) {
            is ApiResult.Failed -> r
            is ApiResult.Ok -> {
                r.value?.let { adopt(it) }
                // true = signed in right away; false = a verification email was sent.
                ApiResult.Ok(r.value != null)
            }
        }
    }

    suspend fun signIn(email: String, password: String): ApiResult<Unit> {
        val api = api ?: return NOT_CONFIGURED
        return when (val r = api.signIn(email.trim(), password)) {
            is ApiResult.Failed -> r
            is ApiResult.Ok -> {
                adopt(r.value)
                ApiResult.Ok(Unit)
            }
        }
    }

    /** Signing out only forgets the session. Projects stay on the device untouched. */
    suspend fun signOut() {
        val token = session?.accessToken
        drop()
        if (token != null) api?.signOut(token)
    }

    suspend fun sendPasswordReset(email: String): ApiResult<Unit> = api?.recover(email.trim()) ?: NOT_CONFIGURED

    suspend fun resendVerification(email: String): ApiResult<Unit> = api?.resendVerification(email.trim()) ?: NOT_CONFIGURED

    suspend fun refreshProfile(): ApiResult<Unit> = withToken { api, token ->
        when (val r = api.user(token)) {
            is ApiResult.Failed -> r
            is ApiResult.Ok -> {
                session?.let { adopt(it.copy(user = r.value), (_state.value as? AccountState.SignedIn)?.needsNewPassword == true) }
                ApiResult.Ok(Unit)
            }
        }
    }

    private suspend fun update(email: String? = null, password: String? = null, displayName: String? = null): ApiResult<Unit> = withToken { api, token ->
        when (val r = api.updateUser(token, email, password, displayName)) {
            is ApiResult.Failed -> r
            is ApiResult.Ok -> {
                session?.let { adopt(it.copy(user = r.value)) }
                ApiResult.Ok(Unit)
            }
        }
    }

    suspend fun setDisplayName(name: String): ApiResult<Unit> = update(displayName = name.trim())

    /** The new address only takes effect after the user confirms it from the email sent there. */
    suspend fun changeEmail(newEmail: String): ApiResult<Unit> = update(email = newEmail.trim())

    /** Requires the current password, which also proves a recent sign-in to the server. */
    suspend fun changePassword(currentPassword: String, newPassword: String): ApiResult<Unit> {
        val api = api ?: return NOT_CONFIGURED
        val email = session?.user?.email ?: return ApiResult.Failed("Kamu belum masuk.")
        when (val check = api.signIn(email, currentPassword)) {
            is ApiResult.Failed -> return if (check.network) check else ApiResult.Failed("Kata sandi saat ini salah.")
            is ApiResult.Ok -> adopt(check.value)
        }
        return update(password = newPassword)
    }

    /** Finishes a password recovery started from the email link. */
    suspend fun setRecoveredPassword(newPassword: String): ApiResult<Unit> = update(password = newPassword)

    /** Deletes the account on the server. Local projects are kept and become unowned. */
    suspend fun deleteAccount(password: String): ApiResult<Unit> {
        val api = api ?: return NOT_CONFIGURED
        val email = session?.user?.email ?: return ApiResult.Failed("Kamu belum masuk.")
        when (val check = api.signIn(email, password)) {
            is ApiResult.Failed -> return if (check.network) check else ApiResult.Failed("Kata sandi salah.")
            is ApiResult.Ok -> adopt(check.value)
        }
        return when (val r = withToken { a, token -> a.deleteAccount(token) }) {
            is ApiResult.Failed -> r
            is ApiResult.Ok -> {
                drop()
                r
            }
        }
    }

    /**
     * Handles `margaretha://auth#...` links from verification and recovery emails.
     * Returns true when the link was meant for this app.
     */
    fun handleDeepLink(uri: Uri?): Boolean {
        if (uri == null || uri.scheme != "margaretha" || uri.host != "auth") return false
        val api = api ?: return true
        val params = (uri.encodedFragment ?: uri.encodedQuery).orEmpty().split('&').mapNotNull {
            val parts = it.split('=', limit = 2)
            if (parts.size == 2) Uri.decode(parts[0]) to Uri.decode(parts[1].replace('+', ' ')) else null
        }.toMap()
        params["error_description"]?.let { description ->
            _notice.value = if (description.contains("expired", true)) "Tautan sudah kedaluwarsa. Minta tautan baru." else "Tautan tidak valid: $description"
            return true
        }
        val access = params["access_token"] ?: return true
        val refresh = params["refresh_token"] ?: return true
        val type = params["type"].orEmpty()
        scope.launch {
            when (val r = api.user(access)) {
                is ApiResult.Failed -> _notice.value = if (r.network) OFFLINE.message else "Tautan tidak bisa diverifikasi. Minta tautan baru."
                is ApiResult.Ok -> {
                    val expiresAt = System.currentTimeMillis() / 1000 + (params["expires_in"]?.toLongOrNull() ?: 3600L)
                    adopt(AccountSession(access, refresh, expiresAt, r.value), needsNewPassword = type == "recovery")
                    _notice.value = when (type) {
                        "recovery" -> "Buat kata sandi baru untuk menyelesaikan pemulihan."
                        "email_change" -> "Email akun berhasil diganti."
                        else -> "Email terverifikasi. Selamat datang, ${r.value.displayName}."
                    }
                }
            }
        }
        return true
    }

    private companion object {
        val NOT_CONFIGURED = ApiResult.Failed("Fitur akun belum dikonfigurasi di build ini.")
        val OFFLINE = ApiResult.Failed("Tidak bisa terhubung ke server. Periksa koneksi internet lalu coba lagi.", network = true)
    }
}
