package id.haseda.margaretha.editor

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
import id.haseda.margaretha.media.TextRenderer
import id.haseda.margaretha.timeline.MediaKind
import id.haseda.margaretha.timeline.Motion
import id.haseda.margaretha.timeline.Project
import id.haseda.margaretha.timeline.TextItem
import id.haseda.margaretha.timeline.Timecode
import id.haseda.margaretha.timeline.UpdateText
import id.haseda.margaretha.ui.AccentPill
import id.haseda.margaretha.ui.IconAction
import id.haseda.margaretha.ui.MColors
import id.haseda.margaretha.ui.MIcons
import id.haseda.margaretha.ui.QuietButton
import id.haseda.margaretha.ui.TimecodeStyle
import id.haseda.margaretha.ui.ToolButton

/** Tool groups shown at the bottom. Each group reveals only the tools that belong to it. */
private enum class ToolTab(val title: String, val icon: ImageVector) {
    EDIT("Edit", MIcons.Split),
    AUDIO("Audio", MIcons.Music),
    TEXT("Teks", MIcons.Text),
    EFFECTS("Efek", MIcons.Effects),
    COLOR("Warna", MIcons.Palette),
    MEDIA("Media", MIcons.Library),
}

@Composable
fun EditorScreen(vm: EditorViewModel, onClose: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val position by vm.preview.positionUs.collectAsStateWithLifecycle()
    val playing by vm.preview.isPlaying.collectAsStateWithLifecycle()
    val rate by vm.rate.collectAsStateWithLifecycle()
    val previewError by vm.preview.error.collectAsStateWithLifecycle()
    val exportState by vm.export.state.collectAsStateWithLifecycle()
    val exportBusy = exportState is ExportState.Running || exportState is ExportState.Verifying

    val snackbar = remember { SnackbarHostState() }
    var showExport by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(ToolTab.EDIT) }
    var panel by rememberSaveable { mutableStateOf(Panel.NONE) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var showTracks by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmStopExport by remember { mutableStateOf(false) }
    var askSrtMode by remember { mutableStateOf<Uri?>(null) }
    var relinkAssetId by rememberSaveable { mutableStateOf<String?>(null) }
    var audioTarget by rememberSaveable { mutableStateOf(Project.MUSIC_TRACK_ID) }

    val addPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30)) { uris: List<Uri> ->
        vm.importMedia(uris)
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        vm.importMedia(uris, audioTrackId = audioTarget)
    }
    val srtPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            // Ask before touching existing subtitles; an empty track is simply filled.
            if (state.project?.track(Project.SUBTITLE_TRACK_ID)?.texts.isNullOrEmpty()) vm.importSrt(uri, replace = false) else askSrtMode = uri
        }
    }
    val srtSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-subrip")) { uri: Uri? ->
        if (uri != null) vm.exportSrt(uri)
    }
    val relinkVisual = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        val id = relinkAssetId
        if (uri != null && id != null) vm.relink(id, uri)
        relinkAssetId = null
    }
    val relinkAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val id = relinkAssetId
        if (uri != null && id != null) vm.relink(id, uri)
        relinkAssetId = null
    }
    val pickVisual = { addPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
    val pickAudio = { trackId: String ->
        audioTarget = trackId
        audioPicker.launch(arrayOf("audio/*"))
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        vm.pause()
        vm.commitDraft()
        vm.flushSave()
    }
    BackHandler {
        when {
            exportBusy -> confirmStopExport = true
            showExport -> showExport = false
            fullscreen -> fullscreen = false
            panel != Panel.NONE -> panel = Panel.NONE
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

    // Hardware keyboard shortcuts (tablets, Chromebooks, desktop mode).
    val focus = remember { FocusRequester() }
    LaunchedEffect(state.loading) { if (!state.loading) runCatching { focus.requestFocus() } }
    val shortcuts = Modifier
        .focusRequester(focus)
        .focusTarget()
        .onKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown || panel == Panel.TEXT) return@onKeyEvent false
            when {
                e.key == Key.Spacebar -> vm.togglePlay()
                e.isCtrlPressed && e.key == Key.Z && e.isShiftPressed -> vm.redo()
                e.isCtrlPressed && e.key == Key.Z -> vm.undo()
                e.isCtrlPressed && e.key == Key.Y -> vm.redo()
                e.isCtrlPressed && e.key == Key.D -> vm.duplicateSelected()
                e.key == Key.S -> vm.splitAtPlayhead()
                e.key == Key.Delete || e.key == Key.Backspace -> vm.deleteSelected()
                e.key == Key.DirectionLeft -> vm.stepFrames(-1)
                e.key == Key.DirectionRight -> vm.stepFrames(1)
                e.key == Key.MoveHome -> vm.seekTo(0L)
                else -> return@onKeyEvent false
            }
            true
        }

    Box(Modifier.fillMaxSize().background(MColors.Base).then(shortcuts)) {
        val project = state.project
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MColors.Accent)
            }
            state.loadError != null || project == null -> LoadError(state.loadError ?: "Proyek tidak tersedia.", onClose)
            else -> BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                val landscape = maxWidth > maxHeight && maxWidth > 600.dp
                val hasClips = project.mainTrack.clips.isNotEmpty()
                // Tools act on the selected clip, or on the clip under the playhead.
                val targetClip = vm.clip(state.selectedClipId) ?: project.mainTrack.clipAt(position)
                val targetIsAudio = vm.isAudioClip(targetClip?.id)
                val selectedText = vm.text(state.selectedTextId)
                val openPanel = { p: Panel ->
                    if (targetClip != null && state.selectedTextId == null) vm.select(targetClip.id)
                    panel = p
                }
                // A panel closes itself when the thing it edits disappears (deleted, undone).
                LaunchedEffect(panel, targetClip?.id, selectedText?.id, targetIsAudio) {
                    val needsVideoClip = panel in setOf(Panel.SPEED, Panel.TRANSFORM, Panel.LOOK, Panel.GRADE, Panel.FX, Panel.TRANSITION)
                    if ((needsVideoClip && (targetClip == null || targetIsAudio)) || (panel == Panel.AUDIO && targetClip == null) || (panel == Panel.TEXT && selectedText == null)) {
                        panel = Panel.NONE
                    }
                }

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
                            if (project.assets[assetId]?.kind == MediaKind.AUDIO) relinkAudio.launch(arrayOf("audio/*"))
                            else relinkVisual.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        }
                    }
                }
                val preview: @Composable (Modifier) -> Unit = { m ->
                    PreviewPane(vm, project, state.selectedTextId, position, playing, pickVisual, m) { textId ->
                        vm.selectText(textId)
                        tab = ToolTab.TEXT
                        panel = Panel.TEXT
                    }
                }
                val transport: @Composable () -> Unit = {
                    Transport(
                        project, position, playing, rate, fullscreen,
                        onToggle = vm::togglePlay,
                        onStop = vm::stop,
                        onStep = vm::stepFrames,
                        onRate = vm::cycleRate,
                        onFullscreen = { fullscreen = !fullscreen },
                    )
                }
                val controls: @Composable () -> Unit = {
                    transport()
                    TimelineView(
                        project = project,
                        positionUs = position,
                        selectedClipId = state.selectedClipId,
                        selectedTextId = state.selectedTextId,
                        cache = vm.thumbnails,
                        onScrub = vm::scrubTo,
                        onSelect = { id ->
                            vm.select(id)
                            if (panel == Panel.TEXT) panel = Panel.NONE
                        },
                        onSelectText = { id ->
                            vm.selectText(id)
                            tab = ToolTab.TEXT
                        },
                        onEdit = { vm.apply(it) },
                        onAdd = pickVisual,
                    )
                    when (panel) {
                        Panel.NONE -> {
                            SelectionHint(vm, state, position)
                            ToolRow(
                                tab = tab,
                                hasClips = hasClips,
                                importing = state.importing,
                                clipTools = targetClip != null && !targetIsAudio && state.selectedTextId == null,
                                mainClipSelected = targetClip != null && !targetIsAudio,
                                anyClip = targetClip != null,
                                textSelected = selectedText != null,
                                hasSubtitles = !project.track(Project.SUBTITLE_TRACK_ID)?.texts.isNullOrEmpty(),
                                onAdd = pickVisual,
                                onSplit = vm::splitAtPlayhead,
                                onTrimStart = vm::trimStartToPlayhead,
                                onTrimEnd = vm::trimEndToPlayhead,
                                onDuplicate = vm::duplicateSelected,
                                onMoveLeft = { vm.moveSelected(-1) },
                                onMoveRight = { vm.moveSelected(1) },
                                onDelete = vm::deleteSelected,
                                onPanel = openPanel,
                                onTracks = { showTracks = true },
                                onAddMusic = { pickAudio(Project.MUSIC_TRACK_ID) },
                                onAddVoice = { pickAudio(Project.VOICE_TRACK_ID) },
                                onAddText = {
                                    vm.addText(subtitle = false)
                                    panel = Panel.TEXT
                                },
                                onAddSubtitle = {
                                    vm.addText(subtitle = true)
                                    panel = Panel.TEXT
                                },
                                onEditText = { panel = Panel.TEXT },
                                onImportSrt = { srtPicker.launch(arrayOf("application/x-subrip", "text/plain", "application/octet-stream")) },
                                onExportSrt = { srtSaver.launch("${project.name}.srt") },
                            )
                        }
                        Panel.SPEED -> targetClip?.let { SpeedPanel(vm, it) { panel = Panel.NONE } }
                        Panel.TRANSFORM -> targetClip?.let { TransformPanel(vm, it) { panel = Panel.NONE } }
                        Panel.AUDIO -> targetClip?.let { AudioPanel(vm, it, project.assets[it.assetId]?.hasAudio == true) { panel = Panel.NONE } }
                        Panel.LOOK -> targetClip?.let { LookPanel(vm, it) { panel = Panel.NONE } }
                        Panel.GRADE -> targetClip?.let { GradePanel(vm, it) { panel = Panel.NONE } }
                        Panel.FX -> targetClip?.let { FxPanel(vm, it) { panel = Panel.NONE } }
                        Panel.TRANSITION -> targetClip?.let { TransitionPanel(vm, it) { panel = Panel.NONE } }
                        Panel.TEXT -> selectedText?.let { item ->
                            TextPanel(vm, item, project.findText(item.id)?.first?.id == Project.SUBTITLE_TRACK_ID) { panel = Panel.NONE }
                        }
                        Panel.CANVAS -> CanvasPanel(vm, project) { panel = Panel.NONE }
                        Panel.MEDIA -> MediaPanel(vm, project, state.missingAssetIds, vm.thumbnails, pickVisual, { pickAudio(Project.MUSIC_TRACK_ID) }) { panel = Panel.NONE }
                    }
                    if (panel == Panel.NONE) {
                        TabBar(tab) {
                            tab = it
                            if (it == ToolTab.MEDIA) panel = Panel.MEDIA
                        }
                    }
                }
                when {
                    fullscreen -> Column(Modifier.fillMaxSize()) {
                        preview(Modifier.weight(1f).fillMaxWidth())
                        transport()
                    }
                    landscape -> Row(Modifier.fillMaxSize()) {
                        preview(Modifier.weight(1.1f).fillMaxSize())
                        Column(Modifier.weight(1f).fillMaxSize()) {
                            top()
                            Spacer(Modifier.weight(1f))
                            controls()
                        }
                    }
                    else -> Column(Modifier.fillMaxSize()) {
                        top()
                        preview(Modifier.weight(1f).fillMaxWidth())
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
    if (showTracks) {
        state.project?.let { p -> TracksDialog(p, vm::setTrackFlags) { showTracks = false } }
    }
    askSrtMode?.let { uri ->
        AlertDialog(
            onDismissRequest = { askSrtMode = null },
            containerColor = MColors.Surface2,
            title = { Text("Proyek sudah punya subtitle") },
            text = { Text("Ganti subtitle yang ada dengan isi file ini, atau tambahkan di sampingnya?") },
            confirmButton = { TextButton(onClick = { askSrtMode = null; vm.importSrt(uri, replace = true) }) { Text("Ganti", color = MColors.Danger) } },
            dismissButton = { TextButton(onClick = { askSrtMode = null; vm.importSrt(uri, replace = false) }) { Text("Tambahkan") } },
        )
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
private fun PreviewPane(
    vm: EditorViewModel,
    project: Project,
    selectedTextId: String?,
    position: Long,
    playing: Boolean,
    onAdd: () -> Unit,
    modifier: Modifier,
    onTextTapped: (String) -> Unit,
) {
    BoxWithConstraints(modifier.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        val ratio = project.canvas.aspect.ratio
        val w = minOf(maxWidth, maxHeight * ratio)
        val h = w / ratio
        var frame by remember { mutableStateOf(IntSize.Zero) }
        Box(
            Modifier
                .size(w, h)
                .clip(RoundedCornerShape(6.dp))
                .background(androidx.compose.ui.graphics.Color.Black)
                .onSizeChanged { frame = it }
                .semantics { contentDescription = "Pratinjau video. Ketuk untuk putar atau jeda." },
            contentAlignment = Alignment.Center,
        ) {
            if (project.mainTrack.clips.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Timeline masih kosong", style = MaterialTheme.typography.titleSmall, color = MColors.Text)
                    Text("Mulai dengan video atau foto dari galeri.", style = MaterialTheme.typography.bodySmall, color = MColors.TextFaint, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(12.dp))
                    AccentPill("Tambah media", onAdd, icon = MIcons.Add)
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
                if (frame.width > 0) {
                    project.textsAt(position).forEach { item ->
                        androidx.compose.runtime.key(item.id) {
                            TextLayer(vm, item, frame, position, selected = item.id == selectedTextId, onTap = { onTextTapped(item.id) })
                        }
                    }
                }
            }
        }
    }
}

/**
 * One title/subtitle drawn over the preview with the same bitmap the exporter burns in.
 * Tap selects it; the selected text can be dragged to a new position.
 */
@Composable
private fun TextLayer(vm: EditorViewModel, item: TextItem, frame: IntSize, position: Long, selected: Boolean, onTap: () -> Unit) {
    // Timing and position do not change the pixels, so they must not trigger a re-render.
    val look = item.copy(startUs = 0L, durationUs = TextItem.MIN_US, x = 0.5f, y = 0.5f)
    val bitmap = remember(look, frame) { TextRenderer.render(look, frame.width, frame.height).asImageBitmap() }
    val pose = Motion.textPose(item, position)
    val current by rememberUpdatedState(item)
    Image(
        bitmap = bitmap,
        contentDescription = "Teks: ${item.text}",
        modifier = Modifier
            .offset {
                IntOffset(
                    (item.x * frame.width - bitmap.width / 2f).toInt(),
                    ((item.y + pose.offsetY) * frame.height - bitmap.height / 2f).toInt(),
                )
            }
            .graphicsLayer {
                alpha = pose.alpha
                scaleX = pose.scale
                scaleY = pose.scale
            }
            .then(if (selected) Modifier.border(1.5.dp, MColors.Accent, RoundedCornerShape(4.dp)) else Modifier)
            .clickable(onClickLabel = "Pilih teks", onClick = onTap)
            .then(
                if (!selected) Modifier else Modifier.pointerInput(item.id, frame) {
                    var x = 0f
                    var y = 0f
                    detectDragGestures(
                        onDragStart = {
                            x = current.x
                            y = current.y
                        },
                        onDragEnd = { vm.commitDraft() },
                        onDragCancel = { vm.cancelDraft() },
                    ) { change, drag ->
                        change.consume()
                        x = (x + drag.x / frame.width).coerceIn(0f, 1f)
                        y = (y + drag.y / frame.height).coerceIn(0f, 1f)
                        vm.draft(UpdateText(current.copy(x = x, y = y)))
                    }
                },
            ),
    )
}

@Composable
private fun Transport(
    project: Project,
    position: Long,
    playing: Boolean,
    rate: Float,
    fullscreen: Boolean,
    onToggle: () -> Unit,
    onStop: () -> Unit,
    onStep: (Int) -> Unit,
    onRate: () -> Unit,
    onFullscreen: () -> Unit,
) {
    val fps = project.canvas.frameRate
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(Timecode.format(position, fps), style = TimecodeStyle, color = MColors.Text, maxLines = 1)
            Text(Timecode.format(project.durationUs, fps), style = MaterialTheme.typography.labelSmall, color = MColors.TextFaint, maxLines = 1)
        }
        IconAction(MIcons.Stop, "Berhenti dan kembali ke awal", onStop, size = 44.dp, tint = MColors.TextMuted)
        IconAction(MIcons.StepBack, "Mundur satu frame", { onStep(-1) }, size = 44.dp)
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
        IconAction(MIcons.StepForward, "Maju satu frame", { onStep(1) }, size = 44.dp)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClickLabel = "Ganti kecepatan pratinjau", onClick = onRate)
                    .padding(horizontal = 10.dp, vertical = 12.dp),
            ) {
                Text(if (rate == 1f) "1×" else "$rate×".replace(".0×", "×"), style = TimecodeStyle, color = if (rate == 1f) MColors.TextMuted else MColors.AccentBright)
            }
            IconAction(MIcons.Fullscreen, if (fullscreen) "Keluar dari layar penuh" else "Pratinjau layar penuh", onFullscreen, size = 44.dp, tint = if (fullscreen) MColors.Accent else MColors.TextMuted)
        }
    }
}

@Composable
private fun SelectionHint(vm: EditorViewModel, state: EditorUiState, position: Long) {
    val project = state.project ?: return
    val clip = vm.clip(state.selectedClipId)
    val text = vm.text(state.selectedTextId)
    val hint = when {
        project.mainTrack.clips.isEmpty() -> "Tambahkan video atau foto untuk mulai menyunting."
        text != null -> "Teks terpilih · ${Timecode.short(text.durationUs)} · seret di timeline untuk memindah, tarik ujungnya untuk durasi"
        clip != null && vm.isAudioClip(clip.id) -> {
            val vol = if (clip.muted) "bisu" else "volume ${(clip.volume * 100).toInt()}%"
            "Audio terpilih · ${Timecode.short(clip.durationUs)} · $vol · seret untuk memindah"
        }
        clip != null -> {
            val index = project.mainTrack.indexOf(clip.id) + 1
            val vol = if (clip.muted) "bisu" else "volume ${(clip.volume * 100).toInt()}%"
            "Klip $index/${project.mainTrack.clips.size} terpilih · ${Timecode.short(clip.durationUs)} · $vol"
        }
        else -> "Ketuk klip untuk memilih · alat bekerja pada klip di playhead"
    }
    Text(
        hint,
        style = MaterialTheme.typography.labelSmall,
        color = if (clip != null || text != null) MColors.AccentBright else MColors.TextFaint,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The tools of the active group. Only what is relevant right now is enabled. */
@Composable
private fun ToolRow(
    tab: ToolTab,
    hasClips: Boolean,
    importing: Boolean,
    clipTools: Boolean,
    mainClipSelected: Boolean,
    anyClip: Boolean,
    textSelected: Boolean,
    hasSubtitles: Boolean,
    onAdd: () -> Unit,
    onSplit: () -> Unit,
    onTrimStart: () -> Unit,
    onTrimEnd: () -> Unit,
    onDuplicate: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onDelete: () -> Unit,
    onPanel: (Panel) -> Unit,
    onTracks: () -> Unit,
    onAddMusic: () -> Unit,
    onAddVoice: () -> Unit,
    onAddText: () -> Unit,
    onAddSubtitle: () -> Unit,
    onEditText: () -> Unit,
    onImportSrt: () -> Unit,
    onExportSrt: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MColors.Surface1)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        when (tab) {
            ToolTab.EDIT -> {
                ToolButton(MIcons.Add, if (importing) "Memuat…" else "Tambah", onAdd, enabled = !importing)
                ToolButton(MIcons.Split, "Belah", onSplit, enabled = anyClip)
                ToolButton(MIcons.TrimStart, "Trim awal", onTrimStart, enabled = anyClip)
                ToolButton(MIcons.TrimEnd, "Trim akhir", onTrimEnd, enabled = anyClip)
                ToolButton(MIcons.Speed, "Kecepatan", { onPanel(Panel.SPEED) }, enabled = clipTools)
                ToolButton(MIcons.Transform, "Posisi", { onPanel(Panel.TRANSFORM) }, enabled = clipTools)
                ToolButton(MIcons.Duplicate, "Duplikat", onDuplicate, enabled = anyClip || textSelected)
                ToolButton(MIcons.MoveLeft, "Geser kiri", onMoveLeft, enabled = mainClipSelected)
                ToolButton(MIcons.MoveRight, "Geser kanan", onMoveRight, enabled = mainClipSelected)
                ToolButton(MIcons.Canvas, "Kanvas", { onPanel(Panel.CANVAS) })
                ToolButton(MIcons.Layers, "Trek", onTracks)
                ToolButton(MIcons.Delete, "Hapus", onDelete, enabled = anyClip || textSelected, danger = true)
            }
            ToolTab.AUDIO -> {
                ToolButton(MIcons.Music, "Musik", onAddMusic, enabled = hasClips)
                ToolButton(MIcons.Add, "Suara/efek", onAddVoice, enabled = hasClips)
                ToolButton(MIcons.Volume, "Volume", { onPanel(Panel.AUDIO) }, enabled = anyClip)
                ToolButton(MIcons.Split, "Belah", onSplit, enabled = anyClip)
                ToolButton(MIcons.Duplicate, "Duplikat", onDuplicate, enabled = anyClip)
                ToolButton(MIcons.Layers, "Trek", onTracks)
                ToolButton(MIcons.Delete, "Hapus", onDelete, enabled = anyClip, danger = true)
            }
            ToolTab.TEXT -> {
                ToolButton(MIcons.Text, "Teks baru", onAddText, enabled = hasClips)
                ToolButton(MIcons.Subtitle, "Subtitle", onAddSubtitle, enabled = hasClips)
                ToolButton(MIcons.Edit, "Ubah", onEditText, enabled = textSelected)
                ToolButton(MIcons.Duplicate, "Duplikat", onDuplicate, enabled = textSelected)
                ToolButton(MIcons.Add, "Impor SRT", onImportSrt, enabled = hasClips)
                ToolButton(MIcons.Export, "Ekspor SRT", onExportSrt, enabled = hasSubtitles)
                ToolButton(MIcons.Layers, "Trek", onTracks)
                ToolButton(MIcons.Delete, "Hapus", onDelete, enabled = textSelected, danger = true)
            }
            ToolTab.EFFECTS -> {
                ToolButton(MIcons.Transition, "Transisi", { onPanel(Panel.TRANSITION) }, enabled = clipTools)
                ToolButton(MIcons.Effects, "Efek", { onPanel(Panel.FX) }, enabled = clipTools)
            }
            ToolTab.COLOR -> {
                ToolButton(MIcons.Sparkle, "Look", { onPanel(Panel.LOOK) }, enabled = clipTools)
                ToolButton(MIcons.Palette, "Grading", { onPanel(Panel.GRADE) }, enabled = clipTools)
            }
            ToolTab.MEDIA -> ToolButton(MIcons.Library, "Pustaka", { onPanel(Panel.MEDIA) })
        }
    }
}

@Composable
private fun TabBar(current: ToolTab, onSelect: (ToolTab) -> Unit) {
    Row(Modifier.fillMaxWidth().background(MColors.Abyss).padding(horizontal = 4.dp)) {
        ToolTab.entries.forEach { t ->
            val selected = t == current
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClickLabel = "Buka alat ${t.title}") { onSelect(t) }
                    .padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(t.icon, contentDescription = null, tint = if (selected) MColors.Accent else MColors.TextFaint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.height(3.dp))
                Text(t.title, style = MaterialTheme.typography.labelSmall, color = if (selected) MColors.AccentBright else MColors.TextFaint, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Box(Modifier.size(width = 16.dp, height = 2.dp).clip(CircleShape).background(if (selected) MColors.Accent else androidx.compose.ui.graphics.Color.Transparent))
            }
        }
    }
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
