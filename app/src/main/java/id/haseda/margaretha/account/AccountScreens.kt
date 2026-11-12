package id.haseda.margaretha.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.haseda.margaretha.home.fieldColors
import id.haseda.margaretha.ui.AccentPill
import id.haseda.margaretha.ui.IconAction
import id.haseda.margaretha.ui.MColors
import id.haseda.margaretha.ui.studioBackdrop
import id.haseda.margaretha.ui.MIcons
import id.haseda.margaretha.ui.MiniChip
import id.haseda.margaretha.ui.PanelLabel
import id.haseda.margaretha.ui.QuietButton
import kotlinx.coroutines.launch

private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")
private const val MIN_PASSWORD = 8

private fun emailProblem(email: String): String? = if (EMAIL.matches(email.trim())) null else "Masukkan alamat email yang valid."
private fun passwordProblem(password: String): String? = if (password.length >= MIN_PASSWORD) null else "Kata sandi minimal $MIN_PASSWORD karakter."

/** Page frame shared by the account screens: back button, title and a scrolling, keyboard-aware body. */
@Composable
private fun AccountFrame(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize().studioBackdrop().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        Column(Modifier.fillMaxSize().widthIn(max = 520.dp).align(Alignment.TopCenter)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconAction(MIcons.Back, "Kembali", onBack)
                Text(title, style = MaterialTheme.typography.titleLarge, color = MColors.Text)
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) { content() }
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, error: String? = null, password: Boolean = false, email: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else if (email) KeyboardType.Email else KeyboardType.Text),
        colors = fieldColors(),
    )
}

@Composable
private fun Notice(text: String, color: Color = MColors.Warning) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MColors.Text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(12.dp),
    )
}

@Composable
private fun Busy(busy: Boolean, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
        if (busy) CircularProgressIndicator(color = MColors.Accent, modifier = Modifier.size(28.dp)) else content()
    }
}

private enum class AuthMode { LOGIN, REGISTER, FORGOT }

/** Sign in, register and password recovery. Closes itself once a session exists. */
@Composable
fun AuthScreen(account: AccountRepository, onBack: () -> Unit) {
    val state by account.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var mode by rememberSaveable { mutableStateOf(AuthMode.LOGIN) }
    var email by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var showErrors by remember { mutableStateOf(false) }
    var canResend by remember { mutableStateOf(false) }

    LaunchedEffect(state) { if (state is AccountState.SignedIn) onBack() }

    fun run(block: suspend () -> Unit) {
        showErrors = true
        error = null
        info = null
        busy = true
        scope.launch {
            block()
            busy = false
        }
    }

    AccountFrame(
        when (mode) {
            AuthMode.LOGIN -> "Masuk"
            AuthMode.REGISTER -> "Buat akun"
            AuthMode.FORGOT -> "Lupa kata sandi"
        },
        onBack,
    ) {
        if (state is AccountState.Unconfigured) {
            Notice("Fitur akun belum aktif di build ini karena alamat server belum dikonfigurasi. Semua fitur penyuntingan tetap bisa dipakai tanpa akun; proyekmu disimpan di perangkat.")
            Spacer(Modifier.height(16.dp))
            QuietButton("Kembali", onBack, icon = MIcons.Back)
            return@AccountFrame
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            MiniChip("Masuk", mode == AuthMode.LOGIN, { mode = AuthMode.LOGIN; error = null; info = null })
            MiniChip("Daftar", mode == AuthMode.REGISTER, { mode = AuthMode.REGISTER; error = null; info = null })
        }
        Text(
            when (mode) {
                AuthMode.LOGIN -> "Masuk untuk menandai proyek sebagai milikmu di perangkat ini."
                AuthMode.REGISTER -> "Akun hanya menyimpan email dan nama tampilan. Video dan proyek tidak pernah diunggah."
                AuthMode.FORGOT -> "Kami kirim tautan ke emailmu untuk membuat kata sandi baru."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MColors.TextMuted,
            modifier = Modifier.padding(top = 12.dp),
        )
        val emailError = emailProblem(email).takeIf { showErrors }
        val passwordError = passwordProblem(password).takeIf { showErrors && mode != AuthMode.FORGOT }
        val nameError = "Isi nama tampilan.".takeIf { showErrors && mode == AuthMode.REGISTER && name.isBlank() }
        if (mode == AuthMode.REGISTER) Field(name, { name = it.take(40) }, "Nama tampilan", nameError)
        Field(email, { email = it.trim() }, "Email", emailError, email = true)
        if (mode != AuthMode.FORGOT) Field(password, { password = it }, "Kata sandi", passwordError, password = true)

        error?.let { Notice(it, MColors.Danger) }
        info?.let { Notice(it, MColors.Success) }

        Busy(busy) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (mode) {
                    AuthMode.LOGIN -> AccentPill("Masuk", {
                        if (emailProblem(email) != null || passwordProblem(password) != null) showErrors = true
                        else run {
                            when (val r = account.signIn(email, password)) {
                                is ApiResult.Ok -> Unit
                                is ApiResult.Failed -> {
                                    error = r.message
                                    canResend = r.code == "email_not_confirmed"
                                }
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth())
                    AuthMode.REGISTER -> AccentPill("Daftar", {
                        if (emailProblem(email) != null || passwordProblem(password) != null || name.isBlank()) showErrors = true
                        else run {
                            when (val r = account.signUp(email, password, name)) {
                                is ApiResult.Ok -> if (!r.value) {
                                    info = "Akun dibuat. Buka email $email dan ketuk tautan verifikasi, lalu masuk."
                                    canResend = true
                                    mode = AuthMode.LOGIN
                                }
                                is ApiResult.Failed -> error = r.message
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth())
                    AuthMode.FORGOT -> AccentPill("Kirim tautan", {
                        if (emailProblem(email) != null) showErrors = true
                        else run {
                            when (val r = account.sendPasswordReset(email)) {
                                is ApiResult.Ok -> info = "Jika $email terdaftar, tautan pemulihan sudah dikirim. Buka tautannya di HP ini."
                                is ApiResult.Failed -> error = r.message
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth())
                }
                if (mode == AuthMode.LOGIN) TextButton(onClick = { mode = AuthMode.FORGOT; error = null; info = null }) { Text("Lupa kata sandi?", color = MColors.Accent) }
                if (mode == AuthMode.FORGOT) TextButton(onClick = { mode = AuthMode.LOGIN; error = null; info = null }) { Text("Kembali ke masuk", color = MColors.Accent) }
                if (canResend && mode == AuthMode.LOGIN) {
                    TextButton(onClick = {
                        if (emailProblem(email) == null) run {
                            when (val r = account.resendVerification(email)) {
                                is ApiResult.Ok -> info = "Email verifikasi dikirim ulang ke $email."
                                is ApiResult.Failed -> error = r.message
                            }
                        }
                    }) { Text("Kirim ulang email verifikasi", color = MColors.Accent) }
                }
            }
        }
    }
}

/** Profile and account management for the signed-in user. */
@Composable
fun AccountScreen(account: AccountRepository, onBack: () -> Unit) {
    val state by account.state.collectAsStateWithLifecycle()
    val signedIn = state as? AccountState.SignedIn
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }

    LaunchedEffect(signedIn == null) { if (signedIn == null) onBack() }
    LaunchedEffect(Unit) { account.refreshProfile() }
    if (signedIn == null) return
    val user = signedIn.user

    fun run(success: String, block: suspend () -> ApiResult<Unit>) {
        error = null
        info = null
        busy = true
        scope.launch {
            when (val r = block()) {
                is ApiResult.Ok -> info = success
                is ApiResult.Failed -> error = r.message
            }
            busy = false
        }
    }

    AccountFrame("Akun", onBack) {
        error?.let { Notice(it, MColors.Danger) }
        info?.let { Notice(it, MColors.Success) }

        if (signedIn.needsNewPassword) {
            var fresh by remember { mutableStateOf("") }
            PanelLabel("Kata sandi baru")
            Text("Kamu masuk lewat tautan pemulihan. Buat kata sandi baru sekarang.", style = MaterialTheme.typography.bodySmall, color = MColors.TextMuted)
            Field(fresh, { fresh = it }, "Kata sandi baru", passwordProblem(fresh).takeIf { fresh.isNotEmpty() }, password = true)
            Busy(busy) {
                AccentPill("Simpan kata sandi", { if (passwordProblem(fresh) == null) run("Kata sandi baru tersimpan.") { account.setRecoveredPassword(fresh) } }, modifier = Modifier.fillMaxWidth())
            }
            return@AccountFrame
        }

        PanelLabel("Profil")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(MColors.AccentDeep), contentAlignment = Alignment.Center) {
                Text(user.displayName.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = MColors.Text)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(user.displayName, style = MaterialTheme.typography.titleMedium, color = MColors.Text)
                Text(user.email, style = MaterialTheme.typography.bodySmall, color = MColors.TextMuted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (user.emailConfirmed) MIcons.Check else MIcons.Warning, contentDescription = null, tint = if (user.emailConfirmed) MColors.Success else MColors.Warning, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (user.emailConfirmed) "Email terverifikasi" else "Email belum diverifikasi", style = MaterialTheme.typography.labelSmall, color = MColors.TextFaint)
                }
            }
        }
        user.pendingEmail?.let { Notice("Menunggu konfirmasi email baru: $it. Buka tautan yang dikirim ke alamat itu.") }

        var name by remember(user.displayName) { mutableStateOf(user.displayName) }
        Field(name, { name = it.take(40) }, "Nama tampilan")
        if (name.trim() != user.displayName && name.isNotBlank()) {
            Busy(busy) { QuietButton("Simpan nama", { run("Nama tampilan diperbarui.") { account.setDisplayName(name) } }, modifier = Modifier.fillMaxWidth()) }
        }

        PanelLabel("Ganti email", Modifier.padding(top = 8.dp))
        var newEmail by remember { mutableStateOf("") }
        Field(newEmail, { newEmail = it.trim() }, "Email baru", emailProblem(newEmail).takeIf { newEmail.isNotEmpty() }, email = true)
        if (newEmail.isNotEmpty()) {
            Busy(busy) {
                QuietButton("Kirim tautan konfirmasi", {
                    if (emailProblem(newEmail) == null) run("Tautan konfirmasi dikirim ke $newEmail. Email berganti setelah tautan dibuka.") { account.changeEmail(newEmail) }
                }, modifier = Modifier.fillMaxWidth())
            }
        }

        PanelLabel("Ganti kata sandi", Modifier.padding(top = 8.dp))
        var current by remember { mutableStateOf("") }
        var fresh by remember { mutableStateOf("") }
        Field(current, { current = it }, "Kata sandi saat ini", password = true)
        Field(fresh, { fresh = it }, "Kata sandi baru", passwordProblem(fresh).takeIf { fresh.isNotEmpty() }, password = true)
        if (current.isNotEmpty() && fresh.isNotEmpty()) {
            Busy(busy) {
                QuietButton("Ganti kata sandi", {
                    if (passwordProblem(fresh) == null) run("Kata sandi diganti.") {
                        account.changePassword(current, fresh).also { if (it is ApiResult.Ok) { current = ""; fresh = "" } }
                    }
                }, modifier = Modifier.fillMaxWidth())
            }
        }

        PanelLabel("Sesi", Modifier.padding(top = 8.dp))
        Text("Keluar tidak menghapus apa pun. Proyekmu tetap di perangkat dan muncul lagi saat kamu masuk.", style = MaterialTheme.typography.bodySmall, color = MColors.TextMuted)
        Spacer(Modifier.height(8.dp))
        QuietButton("Keluar", { scope.launch { account.signOut() } }, modifier = Modifier.fillMaxWidth())

        PanelLabel("Zona berbahaya", Modifier.padding(top = 8.dp))
        QuietButton("Hapus akun", { deleting = true }, modifier = Modifier.fillMaxWidth(), color = MColors.Danger, icon = MIcons.Delete)
    }

    if (deleting) {
        var password by remember { mutableStateOf("") }
        var deleteError by remember { mutableStateOf<String?>(null) }
        var working by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!working) deleting = false },
            containerColor = MColors.Surface2,
            title = { Text("Hapus akun secara permanen?") },
            text = {
                Column {
                    Text("Akun ${user.email} dihapus dari server dan tidak bisa dipulihkan. Proyek di perangkat ini tidak dihapus dan menjadi proyek lokal.", style = MaterialTheme.typography.bodyMedium, color = MColors.TextMuted)
                    Field(password, { password = it }, "Kata sandi untuk konfirmasi", deleteError, password = true)
                }
            },
            confirmButton = {
                TextButton(enabled = password.isNotEmpty() && !working, onClick = {
                    working = true
                    scope.launch {
                        when (val r = account.deleteAccount(password)) {
                            is ApiResult.Ok -> deleting = false
                            is ApiResult.Failed -> deleteError = r.message
                        }
                        working = false
                    }
                }) { Text(if (working) "Menghapus…" else "Hapus akun", color = MColors.Danger) }
            },
            dismissButton = { TextButton(enabled = !working, onClick = { deleting = false }) { Text("Batal") } },
        )
    }
}
