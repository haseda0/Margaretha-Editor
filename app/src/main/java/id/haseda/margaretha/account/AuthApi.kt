package id.haseda.margaretha.account

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class AccountUser(
    val id: String,
    val email: String,
    val displayName: String,
    val emailConfirmed: Boolean,
    /** Address waiting for confirmation after a change-email request. */
    val pendingEmail: String?,
)

data class AccountSession(val accessToken: String, val refreshToken: String, val expiresAtEpochSec: Long, val user: AccountUser)

sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>

    /** [network] is true when the server was never reached (offline, timeout). */
    data class Failed(val message: String, val code: String? = null, val status: Int = 0, val network: Boolean = false) : ApiResult<Nothing>
}

/**
 * Minimal Supabase Auth (GoTrue) client over HTTPS. Only the project URL and the publishable key
 * are used; every privileged rule is enforced by the server.
 */
class AuthApi(baseUrl: String, private val apiKey: String) {
    private val base = baseUrl.trimEnd('/')

    suspend fun signUp(email: String, password: String, displayName: String): ApiResult<AccountSession?> =
        call("POST", "/auth/v1/signup?redirect_to=$REDIRECT", body = JSONObject().put("email", email).put("password", password).put("data", JSONObject().put("display_name", displayName))) { json ->
            // With email confirmation on, the server returns the user without a session.
            if (json.has("access_token")) parseSession(json) else null
        }

    suspend fun signIn(email: String, password: String): ApiResult<AccountSession> =
        call("POST", "/auth/v1/token?grant_type=password", body = JSONObject().put("email", email).put("password", password), parse = ::parseSession)

    suspend fun refresh(refreshToken: String): ApiResult<AccountSession> =
        call("POST", "/auth/v1/token?grant_type=refresh_token", body = JSONObject().put("refresh_token", refreshToken), parse = ::parseSession)

    suspend fun signOut(accessToken: String): ApiResult<Unit> = call("POST", "/auth/v1/logout", token = accessToken) { }

    suspend fun recover(email: String): ApiResult<Unit> =
        call("POST", "/auth/v1/recover?redirect_to=$REDIRECT", body = JSONObject().put("email", email)) { }

    suspend fun resendVerification(email: String): ApiResult<Unit> =
        call("POST", "/auth/v1/resend?redirect_to=$REDIRECT", body = JSONObject().put("type", "signup").put("email", email)) { }

    suspend fun user(accessToken: String): ApiResult<AccountUser> = call("GET", "/auth/v1/user", token = accessToken, parse = ::parseUser)

    suspend fun updateUser(accessToken: String, email: String? = null, password: String? = null, displayName: String? = null): ApiResult<AccountUser> {
        val body = JSONObject()
        if (email != null) body.put("email", email)
        if (password != null) body.put("password", password)
        if (displayName != null) body.put("data", JSONObject().put("display_name", displayName))
        return call("PUT", "/auth/v1/user?redirect_to=$REDIRECT", token = accessToken, body = body, parse = ::parseUser)
    }

    /** Runs the server function that deletes the caller's own account (see backend/). */
    suspend fun deleteAccount(accessToken: String): ApiResult<Unit> =
        call("POST", "/rest/v1/rpc/delete_own_account", token = accessToken, body = JSONObject()) { }

    private suspend fun <T> call(
        method: String,
        path: String,
        token: String? = null,
        body: JSONObject? = null,
        parse: (JSONObject) -> T,
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer ${token ?: apiKey}")
                setRequestProperty("Accept", "application/json")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
            }
            val status = connection.responseCode
            val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            val json = runCatching { JSONObject(text.ifBlank { "{}" }) }.getOrDefault(JSONObject())
            if (status in 200..299) ApiResult.Ok(parse(json)) else failure(status, json)
        } catch (e: IOException) {
            ApiResult.Failed("Tidak bisa terhubung ke server. Periksa koneksi internet lalu coba lagi.", network = true)
        } catch (e: Exception) {
            ApiResult.Failed("Jawaban server tidak dikenali (${e.javaClass.simpleName}).")
        } finally {
            connection?.disconnect()
        }
    }

    private fun failure(status: Int, json: JSONObject): ApiResult.Failed {
        val code = json.optString("error_code").ifBlank { json.optString("error") }.ifBlank { json.optString("code") }.ifBlank { null }
        val raw = json.optString("msg").ifBlank { json.optString("error_description") }.ifBlank { json.optString("message") }
        val message = when {
            code == "invalid_credentials" || raw.contains("Invalid login", true) -> "Email atau kata sandi salah."
            code == "email_not_confirmed" || raw.contains("not confirmed", true) -> "Email belum diverifikasi. Buka tautan di email verifikasi, atau kirim ulang."
            code == "user_already_exists" || code == "email_exists" || raw.contains("already registered", true) -> "Email ini sudah terdaftar. Silakan masuk."
            code == "weak_password" || raw.contains("Password should", true) -> "Kata sandi terlalu lemah. Gunakan minimal 8 karakter."
            code == "same_password" -> "Kata sandi baru harus berbeda dari yang lama."
            code == "over_email_send_rate_limit" || code == "over_request_rate_limit" || status == 429 -> "Terlalu banyak percobaan. Tunggu sebentar lalu coba lagi."
            code == "validation_failed" || raw.contains("invalid format", true) -> "Format email tidak valid."
            code == "signup_disabled" -> "Pendaftaran sedang dinonaktifkan di server."
            code == "session_not_found" || code == "refresh_token_not_found" || code == "refresh_token_already_used" || code == "bad_jwt" -> "Sesi berakhir. Silakan masuk lagi."
            status == 404 && raw.contains("delete_own_account") -> "Server belum menyiapkan penghapusan akun (migrasi belum dijalankan)."
            status >= 500 -> "Server sedang bermasalah. Coba lagi nanti."
            raw.isNotBlank() -> raw
            else -> "Permintaan ditolak server ($status)."
        }
        return ApiResult.Failed(message, code, status)
    }

    private fun parseSession(json: JSONObject) = AccountSession(
        accessToken = json.getString("access_token"),
        refreshToken = json.getString("refresh_token"),
        expiresAtEpochSec = json.optLong("expires_at").takeIf { it > 0 } ?: (System.currentTimeMillis() / 1000 + json.optLong("expires_in", 3600)),
        user = parseUser(json.getJSONObject("user")),
    )

    private fun parseUser(json: JSONObject): AccountUser {
        val email = json.optString("email")
        return AccountUser(
            id = json.getString("id"),
            email = email,
            displayName = json.optJSONObject("user_metadata")?.optString("display_name").orEmpty().ifBlank { email.substringBefore('@') },
            emailConfirmed = !json.isNull("email_confirmed_at") || !json.isNull("confirmed_at"),
            pendingEmail = json.optString("new_email").ifBlank { null },
        )
    }

    companion object {
        /** Deep link the verification / recovery emails return to. Must be allowed in the Supabase project. */
        const val REDIRECT_URI = "margaretha://auth"
        private val REDIRECT: String = URLEncoder.encode(REDIRECT_URI, "UTF-8")
        private const val TIMEOUT_MS = 15_000

        fun sessionToJson(s: AccountSession): String = JSONObject()
            .put("access", s.accessToken).put("refresh", s.refreshToken).put("expiresAt", s.expiresAtEpochSec)
            .put("id", s.user.id).put("email", s.user.email).put("name", s.user.displayName)
            .put("confirmed", s.user.emailConfirmed).put("pendingEmail", s.user.pendingEmail ?: "")
            .toString()

        fun sessionFromJson(text: String): AccountSession? = runCatching {
            val j = JSONObject(text)
            AccountSession(
                j.getString("access"), j.getString("refresh"), j.getLong("expiresAt"),
                AccountUser(j.getString("id"), j.getString("email"), j.getString("name"), j.optBoolean("confirmed", true), j.optString("pendingEmail").ifBlank { null }),
            )
        }.getOrNull()
    }
}
