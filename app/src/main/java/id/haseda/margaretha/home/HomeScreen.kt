package id.haseda.margaretha.home

import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.haseda.margaretha.data.ProjectSummary
import id.haseda.margaretha.media.ThumbnailCache
import id.haseda.margaretha.timeline.AspectRatio
import id.haseda.margaretha.timeline.Timecode
import id.haseda.margaretha.ui.AccentPill
import id.haseda.margaretha.ui.IconAction
import id.haseda.margaretha.ui.MColors
import id.haseda.margaretha.ui.MIcons
import id.haseda.margaretha.ui.QuietButton
import id.haseda.margaretha.ui.VideoFrame
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(vm: HomeViewModel, onOpen: (String, List<Uri>) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showNew by rememberSaveable { mutableStateOf(false) }
    var pendingName by rememberSaveable { mutableStateOf("") }
    var pendingAspect by rememberSaveable { mutableStateOf(AspectRatio.PORTRAIT_9_16) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30)) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                vm.create(pendingName, pendingAspect)?.let { id -> onOpen(id, uris) }
            }
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MColors.Base)
            .drawBehind {
                // Warm key light top-left, cool fill bottom-right: depth without decoration.
                drawCircle(Brush.radialGradient(listOf(MColors.Accent.copy(alpha = 0.10f), Color.Transparent), center = Offset(size.width * 0.1f, 0f), radius = size.width * 0.9f), radius = size.width * 0.9f, center = Offset(size.width * 0.1f, 0f))
                drawCircle(Brush.radialGradient(listOf(Color(0xFF3A4C7A).copy(alpha = 0.16f), Color.Transparent), center = Offset(size.width, size.height), radius = size.width), radius = size.width, center = Offset(size.width, size.height))
            }
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().widthIn(max = 720.dp).align(Alignment.TopCenter),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Header(onSettings = { showSettings = true }) }
            state.recoverable?.let { rec ->
                item {
                    RecoveryBanner(rec, onResume = { onOpen(rec.id, emptyList()) }, onDismiss = vm::dismissRecovery)
                }
            }
            item {
                NewProjectCard(onClick = {
                    pendingName = defaultName()
                    showNew = true
                })
            }
            if (state.projects.isNotEmpty()) {
                item {
                    Text(
                        "Proyek kamu",
                        style = MaterialTheme.typography.titleMedium,
                        color = MColors.TextMuted,
                        modifier = Modifier.padding(top = 10.dp, start = 4.dp),
                    )
                }
                items(state.projects, key = { it.id }) { p ->
                    ProjectRow(
                        p = p,
                        cache = vm.thumbnails,
                        onOpen = { onOpen(p.id, emptyList()) },
                        onRename = { vm.rename(p.id, it) },
                        onDuplicate = { vm.duplicate(p.id) },
                        onDelete = { vm.delete(p.id) },
                    )
                }
            } else if (!state.loading) {
                item { EmptyState() }
            }
            item {
                Text(
                    "Offline · tanpa akun · tanpa watermark. Video aslimu tidak pernah diubah.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MColors.TextFaint,
                    modifier = Modifier.padding(top = 18.dp, start = 4.dp),
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }

    if (showNew) {
        NewProjectDialog(
            name = pendingName,
            aspect = pendingAspect,
            onName = { pendingName = it },
            onAspect = { pendingAspect = it },
            onDismiss = { showNew = false },
            onPick = {
                showNew = false
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            },
        )
    }
    if (showSettings) SettingsDialog(vm) { showSettings = false }
}

@Composable
private fun SettingsDialog(vm: HomeViewModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var fps by remember { mutableStateOf(vm.settings.defaultFrameRate) }
    var cacheBytes by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) { cacheBytes = vm.cacheBytes() }
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "-" }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MColors.Surface2,
        title = { Text("Pengaturan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("FRAME RATE PROYEK BARU", style = MaterialTheme.typography.labelSmall, color = MColors.TextFaint)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    id.haseda.margaretha.data.AppSettings.FRAME_RATES.forEach { f ->
                        id.haseda.margaretha.ui.MiniChip("$f", fps == f, {
                            fps = f
                            vm.settings.defaultFrameRate = f
                        })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("PENYIMPANAN", style = MaterialTheme.typography.labelSmall, color = MColors.TextFaint)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Cache: " + (cacheBytes?.let { Timecode.bytes(it) } ?: "menghitung…"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MColors.Text,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        scope.launch {
                            vm.clearCache()
                            cacheBytes = vm.cacheBytes()
                        }
                    }) { Text("Bersihkan", color = MColors.Accent) }
                }
                Text("Hanya file sementara yang dihapus. Proyek, media, dan hasil ekspor aman.", style = MaterialTheme.typography.bodySmall, color = MColors.TextFaint)
                Spacer(Modifier.height(8.dp))
                Text("TENTANG", style = MaterialTheme.typography.labelSmall, color = MColors.TextFaint)
                Text("Margaretha Editor $version", style = MaterialTheme.typography.bodyMedium, color = MColors.Text)
                Text("Bekerja sepenuhnya offline. Tanpa akun, tanpa iklan, tanpa watermark.", style = MaterialTheme.typography.bodySmall, color = MColors.TextFaint)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Selesai", color = MColors.Accent) } },
    )
}

private fun defaultName(): String = "Proyek " + SimpleDateFormat("d MMM, HH.mm", Locale.forLanguageTag("id-ID")).format(Date())

@Composable
private fun Header(onSettings: () -> Unit) {
    Column(Modifier.padding(start = 4.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(MIcons.Sparkle, contentDescription = null, tint = MColors.Accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("EDITOR", style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 3.sp), color = MColors.Accent, modifier = Modifier.weight(1f))
            id.haseda.margaretha.ui.IconAction(MIcons.Settings, "Pengaturan", onSettings, tint = MColors.TextMuted)
        }
        Spacer(Modifier.height(4.dp))
        Text("Margaretha", style = MaterialTheme.typography.displaySmall, color = MColors.Text)
        Text("Studio video pribadimu.", style = MaterialTheme.typography.bodyMedium, color = MColors.TextMuted)
    }
}

@Composable
private fun NewProjectCard(onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF2B2530), MColors.Surface1, Color(0xFF1A2030))))
            .border(1.dp, MColors.Line, shape)
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(listOf(MColors.AccentBright, MColors.Accent))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(MIcons.Add, contentDescription = null, tint = MColors.OnAccent, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("Proyek Baru", style = MaterialTheme.typography.titleLarge, color = MColors.Text)
            Text("Pilih format, pilih video, mulai menyunting.", style = MaterialTheme.typography.bodyMedium, color = MColors.TextMuted)
        }
    }
}

@Composable
private fun RecoveryBanner(p: ProjectSummary, onResume: () -> Unit, onDismiss: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MColors.Warning.copy(alpha = 0.10f))
            .border(1.dp, MColors.Warning.copy(alpha = 0.45f), shape)
            .padding(16.dp),
    ) {
        Text("Sesi terakhir belum ditutup", style = MaterialTheme.typography.titleSmall, color = MColors.Warning)
        Text(
            "\"${p.name}\" tersimpan otomatis sampai suntingan terakhir. Lanjutkan dari sana?",
            style = MaterialTheme.typography.bodyMedium,
            color = MColors.Text,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AccentPill("Lanjutkan", onResume)
            QuietButton("Abaikan", onDismiss)
        }
    }
}

@Composable
private fun EmptyState() {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(MIcons.Film, contentDescription = null, tint = MColors.TextFaint, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text("Belum ada proyek", style = MaterialTheme.typography.titleMedium, color = MColors.Text)
        Text("Ketuk Proyek Baru untuk memulai.", style = MaterialTheme.typography.bodyMedium, color = MColors.TextMuted)
    }
}

@Composable
private fun ProjectRow(
    p: ProjectSummary,
    cache: ThumbnailCache,
    onOpen: () -> Unit,
    onRename: (String) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MColors.Surface1)
            .border(1.dp, MColors.Line, shape)
            .clickable(onClick = onOpen)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 64.dp, height = 64.dp).clip(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            if (p.coverUri != null) {
                VideoFrame(cache, p.coverUri, p.coverTimeUs, 64.dp, Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().background(MColors.Surface3), contentAlignment = Alignment.Center) {
                    Icon(MIcons.Film, contentDescription = null, tint = MColors.TextFaint, modifier = Modifier.size(24.dp))
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.titleMedium, color = MColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${p.clipCount} klip · ${Timecode.short(p.durationUs)} · ${p.aspect.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MColors.TextMuted,
            )
            Text(
                DateUtils.getRelativeTimeSpanString(p.updatedAtMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MColors.TextFaint,
            )
        }
        Box {
            IconAction(MIcons.More, "Opsi proyek", onClick = { menu = true }, tint = MColors.TextMuted)
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Ganti nama") }, leadingIcon = { Icon(MIcons.Edit, null) }, onClick = { menu = false; renaming = true })
                DropdownMenuItem(text = { Text("Duplikat") }, leadingIcon = { Icon(MIcons.Duplicate, null) }, onClick = { menu = false; onDuplicate() })
                DropdownMenuItem(
                    text = { Text("Hapus", color = MColors.Danger) },
                    leadingIcon = { Icon(MIcons.Delete, null, tint = MColors.Danger) },
                    onClick = { menu = false; deleting = true },
                )
            }
        }
    }

    if (renaming) {
        RenameDialog(initial = p.name, onDismiss = { renaming = false }, onConfirm = { renaming = false; onRename(it) })
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            containerColor = MColors.Surface2,
            title = { Text("Hapus proyek?") },
            text = { Text("\"${p.name}\" akan dihapus permanen. Video asli di galeri tidak ikut terhapus.") },
            confirmButton = { TextButton(onClick = { deleting = false; onDelete() }) { Text("Hapus", color = MColors.Danger) } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Batal") } },
        )
    }
}

@Composable
fun RenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MColors.Surface2,
        title = { Text("Ganti nama proyek") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 80) text = it },
                singleLine = true,
                colors = fieldColors(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MColors.Accent,
    unfocusedBorderColor = MColors.Line,
    cursorColor = MColors.Accent,
    focusedTextColor = MColors.Text,
    unfocusedTextColor = MColors.Text,
)

@Composable
private fun NewProjectDialog(
    name: String,
    aspect: AspectRatio,
    onName: (String) -> Unit,
    onAspect: (AspectRatio) -> Unit,
    onDismiss: () -> Unit,
    onPick: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MColors.Surface1,
        title = { Text("Proyek baru", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 80) onName(it) },
                    label = { Text("Nama") },
                    singleLine = true,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Format", style = MaterialTheme.typography.labelLarge, color = MColors.TextMuted)
                AspectRatio.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { a -> AspectOption(a, a == aspect, Modifier.weight(1f)) { onAspect(a) } }
                    }
                }
            }
        },
        confirmButton = { AccentPill("Pilih video", onPick, icon = MIcons.Film) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal", color = MColors.TextMuted) } },
    )
}

@Composable
private fun AspectOption(a: AspectRatio, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) MColors.Accent.copy(alpha = 0.14f) else MColors.Surface2)
            .border(1.dp, if (selected) MColors.Accent else MColors.Line, shape)
            .clickable(onClick = onClick)
            .padding(10.dp)
            .heightIn(min = 92.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(36.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .height(if (a.ratio >= 1f) (32f / a.ratio).dp else 32.dp)
                    .aspectRatio(a.ratio)
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.6.dp, if (selected) MColors.Accent else MColors.TextMuted, RoundedCornerShape(4.dp)),
            )
        }
        Text(a.label, style = MaterialTheme.typography.labelLarge, color = if (selected) MColors.AccentBright else MColors.Text, fontWeight = FontWeight.SemiBold)
        Text(a.usage, style = MaterialTheme.typography.labelSmall, color = MColors.TextFaint, maxLines = 2)
    }
}
