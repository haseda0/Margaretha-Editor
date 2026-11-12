package id.haseda.margaretha.home

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.haseda.margaretha.account.AccountState
import id.haseda.margaretha.data.AppSettings
import id.haseda.margaretha.data.ExportDestination
import id.haseda.margaretha.data.StorageStatus
import id.haseda.margaretha.data.UserStorage
import id.haseda.margaretha.timeline.Timecode
import id.haseda.margaretha.ui.HardSwitch
import id.haseda.margaretha.ui.IconAction
import id.haseda.margaretha.ui.MColors
import id.haseda.margaretha.ui.MIcons
import id.haseda.margaretha.ui.MiniChip
import id.haseda.margaretha.ui.PanelLabel
import id.haseda.margaretha.ui.QuietButton
import kotlinx.coroutines.launch

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MColors.Surface1).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun Hint(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MColors.TextFaint)

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = if (enabled) MColors.Text else MColors.TextFaint)
            Hint(sub)
        }
        Spacer(Modifier.width(12.dp))
        HardSwitch(checked, onChange, title, enabled = enabled)
    }
}

/** Account, storage, export, backup, privacy and diagnostics in one place. */
@Composable
fun SettingsScreen(vm: HomeViewModel, onBack: () -> Unit, onAccount: () -> Unit) {
    BackHandler(onBack = onBack)
    val state by vm.state.collectAsStateWithLifecycle()
    val account by vm.account.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings = vm.settings
    var fps by remember { mutableStateOf(settings.defaultFrameRate) }
    var destination by remember { mutableStateOf(settings.exportDestination) }
    var autoBackup by remember { mutableStateOf(settings.autoBackup) }
    var cacheBytes by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) { cacheBytes = vm.cacheBytes() }
    val folderReady = state.storage is StorageStatus.Ready

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? -> if (uri != null) vm.adoptFolder(uri) }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> if (uri != null) vm.restoreFrom(uri) }

    Box(Modifier.fillMaxSize().background(MColors.Base).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(Modifier.fillMaxSize().widthIn(max = 640.dp).align(Alignment.TopCenter)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconAction(MIcons.Back, "Kembali", onBack)
                Text("Pengaturan", style = MaterialTheme.typography.titleLarge, color = MColors.Text)
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PanelLabel("Akun")
                Card {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onAccount).padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(MIcons.Lock, contentDescription = null, tint = MColors.Accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            when (val a = account) {
                                is AccountState.SignedIn -> {
                                    Text(a.user.displayName, style = MaterialTheme.typography.bodyMedium, color = MColors.Text)
                                    Hint(a.user.email)
                                }
                                AccountState.SignedOut -> {
                                    Text("Masuk atau daftar", style = MaterialTheme.typography.bodyMedium, color = MColors.Text)
                                    Hint("Opsional. Semua alat tetap terbuka tanpa akun.")
                                }
                                AccountState.Unconfigured -> {
                                    Text("Mode lokal", style = MaterialTheme.typography.bodyMedium, color = MColors.Text)
                                    Hint("Server akun belum dikonfigurasi di build ini.")
                                }
                            }
                        }
                    }
                }

                PanelLabel("Penyimpanan")
                Card {
                    val (title, detail, tone) = when (val s = state.storage) {
                        StorageStatus.AppManaged -> Triple("Dikelola aplikasi", "Proyek ada di penyimpanan aplikasi; hasil ekspor masuk Galeri.", MColors.TextMuted)
                        is StorageStatus.Ready -> Triple("Folder: ${s.folderName}", "Ekspor dan cadangan bisa ditulis ke ${UserStorage.ROOT}/ di folder ini.", MColors.Success)
                        is StorageStatus.Unavailable -> Triple("Folder tidak tersedia", s.reason, MColors.Warning)
                    }
                    Text(title, style = MaterialTheme.typography.bodyMedium, color = tone)
                    Hint(detail)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuietButton(if (state.storage == StorageStatus.AppManaged) "Pilih folder" else "Ganti folder", { folderPicker.launch(null) }, icon = MIcons.Library)
                        if (state.storage != StorageStatus.AppManaged) QuietButton("Lepas", vm::releaseFolder)
                    }
                    Hint("Bisa memori internal, kartu SD, atau USB OTG bila HP mendukung. File di penyimpanan aplikasi ikut terhapus saat aplikasi dihapus; file di folder pilihanmu tidak.")
                }

                PanelLabel("Ekspor")
                Card {
                    Text("Simpan hasil ke", style = MaterialTheme.typography.bodyMedium, color = MColors.Text)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniChip("Galeri", destination == ExportDestination.GALLERY, {
                            destination = ExportDestination.GALLERY
                            settings.exportDestination = destination
                        })
                        MiniChip("Folder pilihan", destination == ExportDestination.FOLDER, {
                            destination = ExportDestination.FOLDER
                            settings.exportDestination = destination
                        }, enabled = folderReady)
                    }
                    if (!folderReady) Hint("Pilih folder dulu untuk mengekspor ke sana.")
                    Hint("Bila folder tidak bisa dipakai saat ekspor, video tetap disimpan ke Galeri dan kamu diberi tahu.")
                }

                PanelLabel("Cadangan & pemulihan")
                Card {
                    SwitchRow(
                        "Cadangkan otomatis",
                        "Salin file proyek ke ${UserStorage.ROOT}/${UserStorage.PROJECTS} setiap kali keluar dari editor.",
                        checked = autoBackup && folderReady,
                        enabled = folderReady,
                    ) {
                        autoBackup = it
                        settings.autoBackup = it
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuietButton("Cadangkan semua", vm::backupAllToFolder, icon = MIcons.Export)
                        QuietButton("Pulihkan dari file", { restorePicker.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }, icon = MIcons.Add)
                    }
                    if (!folderReady) Hint("\"Cadangkan semua\" butuh folder pilihan. Tanpa folder, cadangkan per proyek lewat menu ⋮ di beranda.")
                    Hint("Cadangan berisi susunan suntingan, bukan salinan video. Saat dipulihkan di HP lain, tautkan ulang medianya.")
                }

                PanelLabel("Proyek baru")
                Card {
                    Text("Frame rate", style = MaterialTheme.typography.bodyMedium, color = MColors.Text)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppSettings.FRAME_RATES.forEach { f ->
                            MiniChip("$f", fps == f, {
                                fps = f
                                settings.defaultFrameRate = f
                            })
                        }
                    }
                }

                PanelLabel("Cache & kinerja")
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Cache: " + (cacheBytes?.let { Timecode.bytes(it) } ?: "menghitung…"), style = MaterialTheme.typography.bodyMedium, color = MColors.Text)
                            Hint("Hanya file sementara. Proyek, media, dan hasil ekspor aman.")
                        }
                        QuietButton("Bersihkan", {
                            scope.launch {
                                vm.clearCache()
                                cacheBytes = vm.cacheBytes()
                            }
                        })
                    }
                    if (state.exports.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Riwayat ekspor: ${state.exports.size} entri", style = MaterialTheme.typography.bodyMedium, color = MColors.Text)
                                Hint("Menghapus riwayat tidak menghapus videonya.")
                            }
                            QuietButton("Hapus riwayat", vm::clearExportHistory)
                        }
                    }
                }

                PanelLabel("Privasi")
                Card {
                    Hint("• Tidak ada iklan, pelacak, atau analitik.")
                    Hint("• Video, foto, audio, dan proyek tidak pernah dikirim ke server.")
                    Hint("• Internet hanya dipakai untuk masuk akun (email, kata sandi, nama tampilan).")
                    Hint("• Mikrofon hanya dipakai saat kamu menekan Rekam.")
                }

                PanelLabel("Diagnostik")
                Card {
                    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "-" }
                    Hint("Margaretha Editor $version")
                    Hint("Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}) · ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                    Hint("Proyek tampil: ${state.projects.size} · Akun: " + when (account) {
                        is AccountState.SignedIn -> "masuk"
                        AccountState.SignedOut -> "keluar"
                        AccountState.Unconfigured -> "tidak dikonfigurasi"
                    })
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
