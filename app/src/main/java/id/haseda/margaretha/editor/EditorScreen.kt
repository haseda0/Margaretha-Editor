package id.haseda.margaretha.editor

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import id.haseda.margaretha.home.RenameDialog
import id.haseda.margaretha.media.ExportState
import id.haseda.margaretha.timeline.Project
import id.haseda.margaretha.timeline.ColorLook
import id.haseda.margaretha.timeline.Timecode
import id.haseda.margaretha.ui.AccentPill
import id.haseda.margaretha.ui.IconAction
import id.haseda.margaretha.ui.MColors
import id.haseda.margaretha.ui.MIcons
import id.haseda.margaretha.ui.QuietButton
import id.haseda.margaretha.ui.TimecodeStyle
import id.haseda.margaretha.ui.ToolButton

@Composable
fun EditorScreen(vm: EditorViewModel, onClose: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val position by vm.preview.positionUs.collectAsStateWithLifecycle()
    val playing by vm.preview.isPlaying.collectAsStateWithLifecycle()
    val previewError by vm.preview.error.collectAsStateWithLifecycle()
    val exportState by vm.export.state.collectAsStateWithLifecycle()
    val exportBusy = exportState is ExportState.Running || exportState is ExportState.Verifying

    val snackbar = remember { SnackbarHostState() }
    var showExport by rememberSaveable { mutableStateOf(false) }
    var volumeClipId by rememberSaveable { mutableStateOf<String?>(null) }
    var lookClipId by rememberSaveable { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var confirmStopExport by remember { mutableStateOf(false) }
    var relinkAssetId by rememberSaveable { mutableStateOf<String?>(null) }

    val addPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30)) { uris: List<Uri> ->
        vm.importMedia(uris)
    }
    val relinkPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        val id = relinkAssetId
        if (uri != null && id != null) vm.relink(id, uri)
        relinkAssetId = null
    }
    val pickVideos = { addPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        vm.pause()
        vm.flushSave()
    }
    BackHandler {
        when {
            exportBusy -> confirmStopExport = true
            showExport -> showExport = false
            else -> onClose()
        }
    }
    val view = LocalView.current
    DisposableEffect(exportBusy) {
        view.keepScreenOn = exportBusy
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(state.messageId) {
        val msg = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
        vm.consumeMessage(state.messageId)
    }
    LaunchedEffect(previewError) { previewError?.let { snackbar.showSnackbar(it) } }

    Box(Modifier.fillMaxSize().background(MColors.Base)) {
        val project = state.project
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MColors.Accent)
            }
            state.loadError != null || project == null -> LoadError(state.loadError ?: "Proyek tidak tersedia.", onClose)
            else -> BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                val landscape = maxWidth > maxHeight && maxWidth > 600.dp
                val top: @Composable () -> Unit = {
                    TopBar(
                        project = project,
                        state = state,
                        onBack = { if (exportBusy) confirmStopExport = true else onClose() },
                        onRename = { renaming = true },
                        onUndo = vm::undo,
                        onRedo = vm::redo,
                        onExport = { showExport = true },
                    )
                    if (state.missingAssetIds.isNotEmpty()) {
                        MissingMediaBanner(project, state.missingAssetIds) { assetId ->
                            relinkAssetId = assetId
                            relinkPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        }
                    }
                }
                val controls: @Composable () -> Unit = {
                    Transport(project, position, playing, onToggle = vm::togglePlay, onStep = vm::stepFrames)
                    TimelineView(
                        project = project,
                        positionUs = position,
                        selectedClipId = state.selectedClipId,
                        cache = vm.thumbnails,
                        onScrub = vm::scrubTo,
                        onSelect = vm::select,
                        onTrim = vm::commitTrim,
                        onAdd = pickVideos,
                    )
                    SelectionHint(vm, state, position)
                    Toolbar(
                        hasClips = project.mainTrack.clips.isNotEmpty(),
                        importing = state.importing,
                        onAdd = pickVideos,
                        onSplit = vm::splitAtPlayhead,
                        onTrimStart = vm::trimStartToPlayhead,
                        onTrimEnd = vm::trimEndToPlayhead,
                        onColor = {
                            val target = vm.clip(state.selectedClipId) ?: project.mainTrack.clipAt(position)
                            if (target != null) { vm.select(target.id); lookClipId = target.id }
                        },
                        onVolume = {
                            val target = vm.clip(state.selectedClipId) ?: project.mainTrack.clipAt(position)
                            if (target != null) {
                                vm.select(target.id)
                                volumeClipId = target.id
                            }
                        },
                        onDuplicate = vm::duplicateSelected,
                        onMoveLeft = { vm.moveSelected(-1) },
                        onMoveRight = { vm.moveSelected(1) },
                        onDelete = vm::deleteSelected,
                    )
                }
                if (landscape) {
                    Row(Modifier.fillMaxSize()) {
                        PreviewPane(vm, project, playing, pickVideos, Modifier.weight(1.2f).fillMaxSize())
                        Column(Modifier.weight(1f).fillMaxSize()) {
                            top()
                            Spacer(Modifier.weight(1f))
                            controls()
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        top()
                        PreviewPane(vm, project, playing, pickVideos, Modifier.weight(1f).fillMaxWidth())
                        controls()
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 140.dp, start = 16.dp, end = 16.dp))
    }

    if (showExport && state.project != null) {
        ExportSheet(vm = vm, exportState = exportState, onDismiss = { if (!exportBusy) { showExport = false; vm.export.reset() } })
    }
    val volumeClip = vm.clip(volumeClipId)
    val lookClip = vm.clip(lookClipId)
    if (lookClip != null) {
        ColorLookSheet(lookClip.colorLook, onDismiss = { lookClipId = null }) { look ->
            vm.setColorLook(lookClip.id, look)
            lookClipId = null
        }
    }
    if (volumeClip != null) {
        VolumeSheet(volumeClip, onDismiss = { volumeClipId = null }) { v, muted ->
            vm.setAudio(volumeClip.id, v, muted)
            volumeClipId = null
        }
    }
    if (renaming) {
        RenameDialog(initial = state.project?.name.orEmpty(), onDismiss = { renaming = false }) {
            renaming = false
            vm.rename(it)
        }
    }
    if (confirmStopExport) {
        AlertDialog(
            onDismissRequest = { confirmStopExport = false },
            containerColor = MColors.Surface2,
            title = { Text("Ekspor sedang berjalan") },
            text = { Text("Hentikan ekspor? File yang belum selesai akan dihapus.") },
            confirmButton = {
                TextButton(onClick = { confirmStopExport = false; vm.export.cancel() }) { Text("Hentikan", color = MColors.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmStopExport = false }) { Text("Lanjutkan ekspor") } },
        )
    }
}

@Composable
private fun TopBar(
    project: Project,
    state: EditorUiState,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onExport: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconAction(MIcons.Back, "Kembali ke beranda", onBack)
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClickLabel = "Ganti nama proyek", onClick = onRename)
                .padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            Text(project.name, style = MaterialTheme.typography.titleSmall, color = MColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val (label, color) = when (state.saveStatus) {
                SaveStatus.SAVED -> "Tersimpan · ${project.canvas.aspect.label}" to MColors.TextFaint
                SaveStatus.PENDING -> "Menyimpan…" to MColors.TextFaint
                SaveStatus.ERROR -> "Gagal menyimpan" to MColors.Danger
            }
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        }
        IconAction(MIcons.Undo, state.undoLabel?.let { "Urungkan: $it" } ?: "Urungkan", onUndo, enabled = state.canUndo)
        IconAction(MIcons.Redo, state.redoLabel?.let { "Ulangi: $it" } ?: "Ulangi", onRedo, enabled = state.canRedo)
        Spacer(Modifier.width(4.dp))
        AccentPill("Ekspor", onExport, icon = MIcons.Export, enabled = project.mainTrack.clips.isNotEmpty())
        Spacer(Modifier.width(6.dp))
    }
}

@Composable
private fun MissingMediaBanner(project: Project, missing: Set<String>, onRelink: (String) -> Unit) {
    val first = missing.first()
    val name = project.assets[first]?.displayName ?: "media"
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MColors.Warning.copy(alpha = 0.12f))
            .border(1.dp, MColors.Warning.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MIcons.Warning, contentDescription = null, tint = MColors.Warning, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            if (missing.size == 1) "\"$name\" tidak ditemukan." else "${missing.size} media tidak ditemukan.",
            style = MaterialTheme.typography.bodySmall,
            color = MColors.Text,
            modifier = Modifier.weight(1f),
            maxLines = 2,
        )
        TextButton(onClick = { onRelink(first) }) {
            Icon(MIcons.Link, contentDescription = null, tint = MColors.Warning, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Tautkan ulang", color = MColors.Warning)
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PreviewPane(vm: EditorViewModel, project: Project, playing: Boolean, onAdd: () -> Unit, modifier: Modifier) {
    BoxWithConstraints(modifier.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        val ratio = project.canvas.aspect.ratio
        val w = minOf(maxWidth, maxHeight * ratio)
        val h = w / ratio
        Box(
            Modifier
                .size(w, h)
                .clip(RoundedCornerShape(6.dp))
                .background(androidx.compose.ui.graphics.Color.Black)
                .semantics { contentDescription = "Pratinjau video. Ketuk untuk putar atau jeda." },
            contentAlignment = Alignment.Center,
        ) {
            if (project.mainTrack.clips.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Timeline masih kosong", style = MaterialTheme.typography.titleSmall, color = MColors.Text)
                    Spacer(Modifier.height(12.dp))
                    AccentPill("Tambah video", onAdd, icon = MIcons.Add)
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setShutterBackgroundColor(android.graphics.Color.BLACK)
                            setKeepContentOnPlayerReset(true)
                            player = vm.preview.player
                        }
                    },
                    update = { it.player = vm.preview.player },
                    onRelease = { it.player = null },
                    modifier = Modifier.fillMaxSize(),
                )
                // Touch layer above the SurfaceView: tap toggles playback.
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(onClickLabel = if (playing) "Jeda" else "Putar") { vm.togglePlay() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (!playing) {
                        Box(
                            Modifier.size(56.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(MIcons.Play, contentDescription = null, tint = MColors.Text.copy(alpha = 0.9f), modifier = Modifier.size(26.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Transport(project: Project, position: Long, playing: Boolean, onToggle: () -> Unit, onStep: (Int) -> Unit) {
    val fps = project.canvas.frameRate
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f)) {
            Text(Timecode.format(position, fps), style = TimecodeStyle, color = MColors.Text)
            Text(" / " + Timecode.format(project.durationUs, fps), style = TimecodeStyle, color = MColors.TextFaint)
        }
        IconAction(MIcons.StepBack, "Mundur satu frame", { onStep(-1) })
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(MColors.Surface3, MColors.Surface2)))
                .border(1.dp, MColors.LineStrong, CircleShape)
                .clickable(onClickLabel = if (playing) "Jeda" else "Putar", onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (playing) MIcons.Pause else MIcons.Play, contentDescription = if (playing) "Jeda" else "Putar", tint = MColors.Text, modifier = Modifier.size(24.dp))
        }
        IconAction(MIcons.StepForward, "Maju satu frame", { onStep(1) })
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun SelectionHint(vm: EditorViewModel, state: EditorUiState, position: Long) {
    val project = state.project ?: return
    val clip = vm.clip(state.selectedClipId)
    val text = when {
        project.mainTrack.clips.isEmpty() -> "Tambahkan video untuk mulai menyunting."
        clip != null -> {
            val index = project.mainTrack.indexOf(clip.id) + 1
            val vol = if (clip.muted) "bisu" else "volume ${(clip.volume * 100).toInt()}%"
            "Klip $index/${project.mainTrack.clips.size} terpilih · ${Timecode.short(clip.durationUs)} · $vol"
        }
        else -> "Ketuk klip untuk memilih · alat bekerja pada klip di playhead"
    }
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = if (clip != null) MColors.AccentBright else MColors.TextFaint,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun Toolbar(
    hasClips: Boolean,
    importing: Boolean,
    onAdd: () -> Unit,
    onSplit: () -> Unit,
    onTrimStart: () -> Unit,
    onTrimEnd: () -> Unit,
    onVolume: () -> Unit,
    onColor: () -> Unit,
    onDuplicate: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MColors.Surface1)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ToolButton(MIcons.Add, if (importing) "Memuat…" else "Tambah", onAdd, enabled = !importing)
        ToolButton(MIcons.Split, "Belah", onSplit, enabled = hasClips)
        ToolButton(MIcons.TrimStart, "Trim awal", onTrimStart, enabled = hasClips)
        ToolButton(MIcons.TrimEnd, "Trim akhir", onTrimEnd, enabled = hasClips)
        ToolButton(MIcons.Volume, "Volume", onVolume, enabled = hasClips)
        ToolButton(MIcons.Sparkle, "Warna", onColor, enabled = hasClips)
        ToolButton(MIcons.Duplicate, "Duplikat", onDuplicate, enabled = hasClips)
        ToolButton(MIcons.MoveLeft, "Geser kiri", onMoveLeft, enabled = hasClips)
        ToolButton(MIcons.MoveRight, "Geser kanan", onMoveRight, enabled = hasClips)
        ToolButton(MIcons.Delete, "Hapus", onDelete, enabled = hasClips, danger = true)
    }
}


@Composable
private fun ColorLookSheet(current: ColorLook, onDismiss: () -> Unit, onApply: (ColorLook) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Color Studio", color = MColors.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Look diterapkan ke klip terpilih dan ikut dirender saat ekspor.", color = MColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                ColorLook.entries.forEach { look ->
                    TextButton(onClick = { onApply(look) }, modifier = Modifier.fillMaxWidth()) {
                        Text((if (look == current) "✓  " else "    ") + look.title, color = if (look == current) MColors.AccentBright else MColors.Text)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup", color = MColors.Accent) } },
        containerColor = MColors.Surface2,
    )
}

@Composable
private fun LoadError(reason: String, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(MIcons.Warning, contentDescription = null, tint = MColors.Danger, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text("Proyek tidak bisa dibuka", style = MaterialTheme.typography.titleMedium, color = MColors.Text)
        Text(reason, style = MaterialTheme.typography.bodyMedium, color = MColors.TextMuted, modifier = Modifier.padding(vertical = 8.dp))
        QuietButton("Kembali", onBack, icon = MIcons.Back)
    }
}
