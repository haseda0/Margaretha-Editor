package id.haseda.margaretha.editor

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.haseda.margaretha.MargarethaApp
import id.haseda.margaretha.data.LoadResult
import id.haseda.margaretha.media.CodecSupport
import id.haseda.margaretha.media.ExportEngine
import id.haseda.margaretha.media.PreviewController
import id.haseda.margaretha.media.ProbeResult
import id.haseda.margaretha.timeline.AddAssets
import id.haseda.margaretha.timeline.AddAudioClip
import id.haseda.margaretha.timeline.AddClips
import id.haseda.margaretha.timeline.AddText
import id.haseda.margaretha.timeline.Canvas
import id.haseda.margaretha.timeline.Clip
import id.haseda.margaretha.timeline.ColorLook
import id.haseda.margaretha.timeline.DeleteClips
import id.haseda.margaretha.timeline.DeleteTexts
import id.haseda.margaretha.timeline.DuplicateClip
import id.haseda.margaretha.timeline.ImportSubtitles
import id.haseda.margaretha.timeline.MediaKind
import id.haseda.margaretha.timeline.RemoveAsset
import id.haseda.margaretha.timeline.SetCanvas
import id.haseda.margaretha.timeline.SetTrackFlags
import id.haseda.margaretha.timeline.Srt
import id.haseda.margaretha.timeline.TextItem
import id.haseda.margaretha.timeline.TextStyle
import id.haseda.margaretha.timeline.TrackKind
import id.haseda.margaretha.timeline.UpdateText
import id.haseda.margaretha.timeline.Edit
import id.haseda.margaretha.timeline.EditOutcome
import id.haseda.margaretha.timeline.EditorSession
import id.haseda.margaretha.timeline.ExportPlanner
import id.haseda.margaretha.timeline.ExportSettings
import id.haseda.margaretha.timeline.MediaAsset
import id.haseda.margaretha.timeline.MoveClip
import id.haseda.margaretha.timeline.PlanOutcome
import id.haseda.margaretha.timeline.Project
import id.haseda.margaretha.timeline.RelinkAsset
import id.haseda.margaretha.timeline.RenameProject
import id.haseda.margaretha.timeline.RenderSegments
import id.haseda.margaretha.timeline.ResolutionPreset
import id.haseda.margaretha.timeline.SetClipAudio
import id.haseda.margaretha.timeline.SetClipColorLook
import id.haseda.margaretha.timeline.SplitClip
import id.haseda.margaretha.timeline.TimelineEngine
import id.haseda.margaretha.timeline.TrimClip
import id.haseda.margaretha.timeline.VideoCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.UUID

enum class SaveStatus { SAVED, PENDING, ERROR }

data class EditorUiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val project: Project? = null,
    val selectedClipId: String? = null,
    val selectedTextId: String? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val undoLabel: String? = null,
    val redoLabel: String? = null,
    val importing: Boolean = false,
    val missingAssetIds: Set<String> = emptySet(),
    val saveStatus: SaveStatus = SaveStatus.SAVED,
    val message: String? = null,
    val messageId: Long = 0L,
)

/** Capability-checked export choices shown on the export sheet. */
data class ExportOptions(
    val settings: ExportSettings,
    val supportedResolutions: Set<ResolutionPreset>,
    val hevcAvailable: Boolean,
)

/**
 * Activity-scoped editor state holder. Owns the single source of truth ([EditorSession]),
 * the preview player and the export engine. [open]/[close] switch projects.
 */
class EditorViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as MargarethaApp).container
    private val repository = container.repository
    private val probe = container.probe

    val preview = PreviewController(app, viewModelScope)
    val export = ExportEngine(app, viewModelScope)
    val thumbnails = container.thumbnails
    val waveforms = container.waveforms

    private var session: EditorSession? = null
    private var saveJob: Job? = null
    private var lastSeekAtMs = 0L
    private var pendingSeek: Job? = null

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private val _exportSettings = MutableStateFlow(ExportSettings())
    val exportSettings: StateFlow<ExportSettings> = _exportSettings.asStateFlow()

    init {
        export.cleanupOrphans()
    }

    // ---- Lifecycle -----------------------------------------------------------------------------

    private var currentId: String? = null
    private var pendingImports: List<Uri> = emptyList()

    /** Idempotent: opening the already-open (or loading) project only queues the imports. */
    fun open(projectId: String, importUris: List<Uri> = emptyList()) {
        if (currentId == projectId) {
            if (importUris.isNotEmpty()) {
                if (session != null) importMedia(importUris) else pendingImports = pendingImports + importUris
            }
            return
        }
        currentId = projectId
        pendingImports = importUris
        session = null
        preview.clear()
        _state.value = EditorUiState(loading = true)
        viewModelScope.launch {
            when (val r = repository.load(projectId)) {
                is LoadResult.Failed -> _state.value = EditorUiState(loading = false, loadError = r.reason)
                is LoadResult.Loaded -> {
                    if (currentId != projectId) return@launch
                    session = EditorSession(r.project)
                    repository.markSessionOpen(projectId)
                    publish(resetSelection = true)
                    _state.update { it.copy(loading = false) }
                    if (r.recoveredFromBackup) toast("File proyek rusak — dipulihkan dari cadangan terakhir.")
                    checkMissingMedia()
                    val queued = pendingImports
                    pendingImports = emptyList()
                    if (queued.isNotEmpty()) importMedia(queued)
                }
            }
        }
    }

    /** Called when leaving the editor normally: flush autosave and end the session marker. */
    fun close() {
        currentId = null
        pendingImports = emptyList()
        export.reset()
        preview.clear()
        preview.bypassEffects = false
        draftEdit = null
        val p = session?.project
        session = null
        _state.value = EditorUiState()
        viewModelScope.launch {
            withContext(NonCancellable) {
                saveJob?.cancel()
                if (p != null) runCatching { repository.save(p) }
                repository.clearSession()
            }
        }
    }

    fun flushSave() {
        val p = session?.project ?: return
        saveJob?.cancel()
        viewModelScope.launch { withContext(NonCancellable) { saveNow(p) } }
    }

    override fun onCleared() {
        session?.project?.let { p -> runCatching { kotlinx.coroutines.runBlocking { repository.save(p) } } }
        preview.release()
        export.release()
    }

    // ---- Editing ------------------------------------------------------------------------------

    private fun perform(edit: Edit, select: String? = _state.value.selectedClipId, toastOnSuccess: String? = null): Boolean {
        val s = session ?: return false
        draftEdit = null
        return when (val outcome = s.perform(edit, System.currentTimeMillis())) {
            is EditOutcome.Applied -> {
                publish(selected = select)
                scheduleSave()
                toastOnSuccess?.let(::toast)
                true
            }
            is EditOutcome.Rejected -> {
                toast(outcome.reason)
                false
            }
        }
    }

    fun undo() {
        val s = session ?: return
        draftEdit = null
        val label = s.undo() ?: return
        publish(selected = _state.value.selectedClipId?.takeIf { s.project.findClip(it) != null })
        scheduleSave()
        toast("Dibatalkan: $label")
    }

    fun redo() {
        val s = session ?: return
        draftEdit = null
        val label = s.redo() ?: return
        publish(selected = _state.value.selectedClipId?.takeIf { s.project.findClip(it) != null })
        scheduleSave()
        toast("Diulang: $label")
    }

    fun select(clipId: String?) = _state.update { it.copy(selectedClipId = clipId, selectedTextId = null) }

    fun selectText(textId: String?) = _state.update { it.copy(selectedTextId = textId, selectedClipId = null) }

    /** Runs any engine command as one undoable step (used by timeline drags and panels). */
    fun apply(edit: Edit): Boolean = perform(edit)

    // ---- Live drafts: sliders preview a change without flooding the undo history ----------------

    private var draftEdit: Edit? = null

    /** Shows [edit] in the preview and UI without recording it. Call [commitDraft] on release. */
    fun draft(edit: Edit) {
        val s = session ?: return
        val outcome = TimelineEngine.apply(s.project, edit)
        if (outcome is EditOutcome.Applied) {
            draftEdit = edit
            show(outcome.project)
        } else if (draftEdit != null) {
            // Dragged back to the starting value: nothing left to commit.
            draftEdit = null
            show(s.project)
        }
    }

    fun commitDraft() {
        val edit = draftEdit ?: return
        draftEdit = null
        perform(edit)
    }

    fun cancelDraft() {
        if (draftEdit == null) return
        draftEdit = null
        session?.let { show(it.project) }
    }

    /** Tools act on the selected clip, or on the clip under the playhead when nothing is selected. */
    private fun targetClip(): Clip? {
        val p = session?.project ?: return null
        _state.value.selectedClipId?.let { id -> p.findClip(id)?.let { return it.second } }
        return p.mainTrack.clipAt(preview.positionUs.value)
    }

    fun splitAtPlayhead() {
        val clip = targetClip() ?: return toast("Tidak ada klip di posisi playhead.")
        val at = preview.positionUs.value
        if (!clip.containsTimelineUs(at)) return toast("Playhead tidak berada di klip terpilih.")
        val newId = newId()
        perform(SplitClip(clip.id, at, newId), select = newId)
    }

    fun trimStartToPlayhead() {
        val clip = targetClip() ?: return toast("Pilih klip dulu.")
        val at = preview.positionUs.value
        if (!clip.containsTimelineUs(at)) return toast("Letakkan playhead di dalam klip.")
        val offset = at - clip.timelineStartUs
        if (perform(TrimClip(clip.id, clip.sourceAtOffsetUs(offset), clip.sourceOutUs), select = clip.id)) {
            // Main-track clips ripple left, so the playhead follows the new head; audio stays put.
            if (!isAudioClip(clip.id)) seekTo(clip.timelineStartUs)
        }
    }

    fun trimEndToPlayhead() {
        val clip = targetClip() ?: return toast("Pilih klip dulu.")
        val at = preview.positionUs.value
        if (!clip.containsTimelineUs(at)) return toast("Letakkan playhead di dalam klip.")
        perform(TrimClip(clip.id, clip.sourceInUs, clip.sourceAtOffsetUs(at - clip.timelineStartUs)), select = clip.id)
    }

    fun commitTrim(clipId: String, newIn: Long, newOut: Long) {
        perform(TrimClip(clipId, newIn, newOut), select = clipId)
    }

    fun deleteSelected() {
        _state.value.selectedTextId?.let { id ->
            if (perform(DeleteTexts(setOf(id)))) _state.update { it.copy(selectedTextId = null) }
            return
        }
        val clip = targetClip() ?: return toast("Pilih klip yang ingin dihapus.")
        perform(DeleteClips(setOf(clip.id)), select = null)
    }

    fun duplicateSelected() {
        val p = session?.project ?: return
        _state.value.selectedTextId?.let { id ->
            val (track, item) = p.findText(id) ?: return
            val copy = item.copy(id = newId(), startUs = item.endUs)
            if (perform(AddText(copy, track.id))) selectText(copy.id)
            return
        }
        val clip = targetClip() ?: return toast("Pilih klip dulu.")
        val id = newId()
        perform(DuplicateClip(clip.id, id), select = id)
    }

    // ---- Text & subtitles ----------------------------------------------------------------------

    /** Adds a title or subtitle at the playhead and selects it for editing. */
    fun addText(subtitle: Boolean) {
        val p = session?.project ?: return
        if (p.durationUs <= 0) return toast("Tambahkan video dulu, lalu beri teks.")
        val start = preview.positionUs.value.coerceIn(0L, (p.durationUs - TextItem.MIN_US).coerceAtLeast(0L))
        val item = TextItem(
            id = newId(),
            text = if (subtitle) "Subtitle" else "Teks",
            startUs = start,
            durationUs = minOf(if (subtitle) 2_500_000L else 3_000_000L, p.durationUs - start).coerceAtLeast(TextItem.MIN_US),
            y = if (subtitle) TextItem.SUBTITLE_Y else 0.5f,
            style = if (subtitle) TextStyle.SUBTITLE else TextStyle.TITLE,
        )
        if (perform(AddText(item, if (subtitle) Project.SUBTITLE_TRACK_ID else Project.TEXT_TRACK_ID))) selectText(item.id)
    }

    fun text(textId: String?): TextItem? = textId?.let { _state.value.project?.findText(it)?.second }

    fun importSrt(uri: Uri, replace: Boolean) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                        // Subtitle files are tiny; refuse anything that clearly is not one.
                        val bytes = input.readNBytesCompat(MAX_SRT_BYTES + 1)
                        if (bytes.size > MAX_SRT_BYTES) null else bytes.toString(Charsets.UTF_8)
                    }
                }.getOrNull()
            } ?: return@launch toast("File subtitle tidak bisa dibaca atau terlalu besar.")
            val parsed = Srt.parse(text)
            if (parsed.cues.isEmpty()) return@launch toast("Tidak ada subtitle yang terbaca. Pastikan filenya berformat .srt.")
            val items = Srt.toTextItems(parsed.cues, parsed.cues.map { newId() })
            val note = if (parsed.skipped > 0) " (${parsed.skipped} blok rusak dilewati)" else ""
            perform(ImportSubtitles(items, replace), toastOnSuccess = "${items.size} subtitle diimpor$note.")
        }
    }

    fun exportSrt(uri: Uri) {
        val cues = Srt.fromTextItems(session?.project?.track(Project.SUBTITLE_TRACK_ID)?.texts.orEmpty())
        if (cues.isEmpty()) return toast("Belum ada subtitle untuk diekspor.")
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { it.write(Srt.format(cues).toByteArray(Charsets.UTF_8)) } != null
                }.getOrDefault(false)
            }
            toast(if (ok) "${cues.size} subtitle disimpan sebagai .srt." else "Gagal menulis file subtitle.")
        }
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (out.size() < limit) {
            val n = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    // ---- Tracks, canvas, library ---------------------------------------------------------------

    fun setTrackFlags(trackId: String, enabled: Boolean, locked: Boolean) {
        perform(SetTrackFlags(trackId, enabled, locked))
    }

    fun setCanvas(canvas: Canvas) {
        perform(SetCanvas(canvas))
    }

    /** Puts a library asset on the timeline: pictures after the selection, sound at the playhead. */
    fun addAssetToTimeline(assetId: String, trackId: String = Project.MUSIC_TRACK_ID) {
        val p = session?.project ?: return
        val asset = p.assets[assetId] ?: return
        val id = newId()
        if (asset.kind == MediaKind.AUDIO) {
            if (perform(AddAudioClip(asset, id, trackId, preview.positionUs.value), select = id)) toast("Audio ditambahkan di playhead.")
        } else {
            val selectedIndex = _state.value.selectedClipId?.let { p.mainTrack.indexOf(it) } ?: -1
            perform(AddClips(listOf(asset), listOf(id), if (selectedIndex >= 0) selectedIndex + 1 else null), select = id)
        }
    }

    /** Uses the sound of a library video as an audio clip (e.g. keep a voice, replace the picture). */
    fun addAssetAudio(assetId: String, trackId: String) {
        val asset = session?.project?.assets?.get(assetId) ?: return
        val id = newId()
        perform(AddAudioClip(asset, id, trackId, preview.positionUs.value), select = id)
    }

    fun removeAsset(assetId: String) {
        if (perform(RemoveAsset(assetId), toastOnSuccess = "Media dihapus dari proyek.")) checkMissingMedia()
    }

    fun moveSelected(delta: Int) {
        val clip = targetClip() ?: return toast("Pilih klip dulu.")
        val track = session?.project?.mainTrack ?: return
        val index = track.indexOf(clip.id)
        val to = index + delta
        if (to !in track.clips.indices) return toast(if (delta < 0) "Sudah klip pertama." else "Sudah klip terakhir.")
        if (perform(MoveClip(clip.id, to), select = clip.id)) {
            session?.project?.findClip(clip.id)?.second?.let { seekTo(it.timelineStartUs) }
        }
    }

    fun setColorLook(clipId: String, look: ColorLook) {
        perform(SetClipColorLook(clipId, look), select = clipId)
    }

    fun rename(name: String) {
        perform(RenameProject(name))
    }

    // ---- Media import & relink ------------------------------------------------------------------

    /**
     * Imports picked files. Video and images go to the main track, sound to [audioTrackId];
     * with [toLibraryOnly] everything is only added to the project's media library.
     */
    fun importMedia(uris: List<Uri>, audioTrackId: String = Project.MUSIC_TRACK_ID, toLibraryOnly: Boolean = false) {
        if (uris.isEmpty() || session == null) return
        _state.update { it.copy(importing = true) }
        viewModelScope.launch {
            val slots = Semaphore(3)
            val results = uris.map { uri ->
                async {
                    slots.withPermit {
                        val assetId = UUID.randomUUID().toString()
                        val durable = probe.makeDurable(uri, assetId)
                        probe.probe(durable, assetId)
                    }
                }
            }.awaitAll()
            val ok = results.filterIsInstance<ProbeResult.Ok>().map { it.asset }
            val failed = results.filterIsInstance<ProbeResult.Failed>()
            _state.update { it.copy(importing = false) }
            val visual = ok.filter { it.kind != MediaKind.AUDIO }
            val audio = ok.filter { it.kind == MediaKind.AUDIO }
            if (toLibraryOnly) {
                if (ok.isNotEmpty()) perform(AddAssets(ok), toastOnSuccess = "${ok.size} media masuk ke pustaka.")
            } else {
                if (visual.isNotEmpty()) {
                    val track = session?.project?.mainTrack
                    val selectedIndex = _state.value.selectedClipId?.let { track?.indexOf(it) } ?: -1
                    val insertAt = if (selectedIndex >= 0) selectedIndex + 1 else null
                    val ids = visual.map { newId() }
                    perform(AddClips(visual, ids, insertAt), select = ids.first())
                }
                // Several sounds are laid end to end from the playhead; the engine avoids overlaps.
                var at = preview.positionUs.value
                audio.forEach { asset ->
                    val id = newId()
                    if (perform(AddAudioClip(asset, id, audioTrackId, at), select = id)) at += asset.durationUs
                }
            }
            if (failed.isNotEmpty()) {
                val first = failed.first()
                toast(
                    if (failed.size == 1) "\"${first.name}\" dilewati: ${first.reason}"
                    else "${failed.size} file dilewati. Contoh: \"${first.name}\" — ${first.reason}",
                )
            }
        }
    }

    fun relink(assetId: String, uri: Uri) {
        viewModelScope.launch {
            val durable = probe.makeDurable(uri, assetId)
            when (val r = probe.probe(durable, assetId)) {
                is ProbeResult.Failed -> toast("Tidak bisa memakai \"${r.name}\": ${r.reason}")
                is ProbeResult.Ok -> {
                    val applied = perform(RelinkAsset(assetId, r.asset), toastOnSuccess = "Media ditautkan ulang.")
                    if (applied) checkMissingMedia()
                }
            }
        }
    }

    private fun checkMissingMedia() {
        val p = session?.project ?: return
        viewModelScope.launch {
            val missing = p.assets.values.filterNot { probe.isReachable(it.uri) }.map(MediaAsset::id).toSet()
            _state.update { it.copy(missingAssetIds = missing) }
        }
    }

    // ---- Preview -------------------------------------------------------------------------------

    fun togglePlay() = preview.togglePlay()

    fun stop() = preview.stop()

    private val _rate = MutableStateFlow(1f)
    val rate: StateFlow<Float> = _rate.asStateFlow()

    fun cycleRate() {
        val next = RATES[(RATES.indexOf(_rate.value) + 1) % RATES.size]
        _rate.value = next
        preview.rate = next
    }

    /** Before/after comparison: hold to see the picture without colour work and effects. */
    fun compareOriginal(active: Boolean) {
        preview.bypassEffects = active
    }

    fun pause() = preview.pause()

    fun stepFrames(frames: Int) {
        val p = session?.project ?: return
        preview.pause()
        seekTo(preview.positionUs.value + frames * p.canvas.frameDurationUs)
    }

    fun seekTo(timeUs: Long) = preview.seekTo(timeUs)

    /** Scrubbing: the playhead follows the finger instantly, decoder seeks are throttled to ~25/s. */
    fun scrubTo(timeUs: Long, final: Boolean = false) {
        if (preview.isPlaying.value) preview.pause()
        val now = System.currentTimeMillis()
        pendingSeek?.cancel()
        if (final || now - lastSeekAtMs >= 40) {
            lastSeekAtMs = now
            preview.seekTo(timeUs)
        } else {
            pendingSeek = viewModelScope.launch {
                delay(40)
                lastSeekAtMs = System.currentTimeMillis()
                preview.seekTo(timeUs)
            }
        }
    }

    // ---- Export --------------------------------------------------------------------------------

    fun exportOptions(): ExportOptions? {
        val p = session?.project ?: return null
        val s = _exportSettings.value
        val supported = ResolutionPreset.entries.filter { res ->
            val size = ExportPlanner.outputSize(p.canvas.aspect, res)
            CodecSupport.supports(s.codec.mimeType, size.width, size.height, s.frameRate)
        }.toSet()
        return ExportOptions(s, supported, CodecSupport.hasEncoder(VideoCodec.HEVC.mimeType))
    }

    fun updateExportSettings(settings: ExportSettings) {
        _exportSettings.value = settings
    }

    fun startExport() {
        val p = session?.project ?: return
        preview.pause()
        when (val outcome = ExportPlanner.plan(p, _exportSettings.value)) {
            is PlanOutcome.Invalid -> toast(outcome.reason)
            is PlanOutcome.Ready -> {
                if (_state.value.missingAssetIds.isNotEmpty()) return toast("Tautkan ulang media yang hilang sebelum ekspor.")
                flushSave()
                export.start(outcome.plan, p.name)
            }
        }
    }

    // ---- Internals -----------------------------------------------------------------------------

    private fun publish(selected: String? = _state.value.selectedClipId, resetSelection: Boolean = false) {
        val s = session ?: return
        val p = s.project
        _state.update {
            it.copy(
                selectedClipId = if (resetSelection) null else selected?.takeIf { id -> p.findClip(id) != null },
                selectedTextId = if (resetSelection) null else it.selectedTextId?.takeIf { id -> p.findText(id) != null },
                canUndo = s.canUndo,
                canRedo = s.canRedo,
                undoLabel = s.undoLabel,
                redoLabel = s.redoLabel,
            )
        }
        show(p)
    }

    /** Pushes a project snapshot (committed or draft) to the UI and the preview player. */
    private fun show(p: Project) {
        _state.update { it.copy(project = p) }
        preview.setPlan(RenderSegments.plan(p), p.canvas.aspect.ratio, preview.positionUs.value.coerceAtMost(p.durationUs))
    }

    private fun scheduleSave() {
        val p = session?.project ?: return
        _state.update { it.copy(saveStatus = SaveStatus.PENDING) }
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            saveNow(p)
        }
    }

    private suspend fun saveNow(p: Project) {
        try {
            repository.save(p)
            if (session?.project === p) _state.update { it.copy(saveStatus = SaveStatus.SAVED) }
        } catch (e: Exception) {
            _state.update { it.copy(saveStatus = SaveStatus.ERROR) }
            toast("Gagal menyimpan proyek: ${e.message}")
        }
    }

    fun consumeMessage(id: Long) = _state.update { if (it.messageId == id) it.copy(message = null) else it }

    private fun toast(text: String) = _state.update { it.copy(message = text, messageId = it.messageId + 1) }

    private fun newId() = UUID.randomUUID().toString()

    /** Clip lookup helper for UI panels; reads the shown project so live drafts are reflected. */
    fun clip(clipId: String?): Clip? = clipId?.let { _state.value.project?.findClip(it)?.second }

    /** True when the clip sits on an audio track (only audio tools apply). */
    fun isAudioClip(clipId: String?): Boolean =
        clipId?.let { _state.value.project?.findClip(it)?.first?.kind } == TrackKind.AUDIO

    companion object {
        val MIN_CLIP_US = TimelineEngine.MIN_CLIP_US
        val RATES = listOf(1f, 2f, 0.5f)
        private const val MAX_SRT_BYTES = 2 * 1024 * 1024
    }
}
