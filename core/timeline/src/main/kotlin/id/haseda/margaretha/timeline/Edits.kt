package id.haseda.margaretha.timeline

/**
 * Explicit, validated editing commands. Every command is pure: it maps a [Project] to a new
 * [Project] or is rejected with a human readable reason. IDs for new clips are supplied by the
 * caller so the engine stays deterministic and testable.
 */
sealed interface Edit {
    val label: String
}

data class AddClips(
    val assets: List<MediaAsset>,
    val newClipIds: List<String>,
    /** Insert position in the main track; null appends at the end. */
    val insertIndex: Int? = null,
) : Edit {
    override val label: String get() = "Tambah ${assets.size} klip"
}

data class SplitClip(val clipId: String, val atTimelineUs: Long, val newClipId: String) : Edit {
    override val label: String get() = "Belah klip"
}

/** Sets both source points at once; covers trim-start and trim-end. */
data class TrimClip(val clipId: String, val newSourceInUs: Long, val newSourceOutUs: Long) : Edit {
    override val label: String get() = "Pangkas klip"
}

data class MoveClip(val clipId: String, val toIndex: Int) : Edit {
    override val label: String get() = "Pindah klip"
}

/** Removes clips; magnetic tracks ripple automatically. */
data class DeleteClips(val clipIds: Set<String>) : Edit {
    override val label: String get() = if (clipIds.size == 1) "Hapus klip" else "Hapus ${clipIds.size} klip"
}

data class DuplicateClip(val clipId: String, val newClipId: String) : Edit {
    override val label: String get() = "Duplikat klip"
}

data class SetClipAudio(val clipId: String, val volume: Float, val muted: Boolean) : Edit {
    override val label: String get() = if (muted) "Bisukan klip" else "Ubah volume"
}

data class RenameProject(val name: String) : Edit {
    override val label: String get() = "Ganti nama"
}

/** Points an asset at a new file (moved/deleted original). Clip edits are kept. */
data class RelinkAsset(val assetId: String, val replacement: MediaAsset) : Edit {
    override val label: String get() = "Tautkan ulang media"
}

sealed interface EditOutcome {
    data class Applied(val project: Project) : EditOutcome
    data class Rejected(val reason: String) : EditOutcome
}

object TimelineEngine {
    /** Shortest clip the engine allows (0.1 s). Prevents unusable sliver clips. */
    const val MIN_CLIP_US: Long = 100_000L
    const val MAX_NAME_LENGTH = 80

    fun apply(project: Project, edit: Edit, nowMs: Long = project.updatedAtMs): EditOutcome {
        val result = when (edit) {
            is AddClips -> addClips(project, edit)
            is SplitClip -> split(project, edit)
            is TrimClip -> trim(project, edit)
            is MoveClip -> move(project, edit)
            is DeleteClips -> delete(project, edit)
            is DuplicateClip -> duplicate(project, edit)
            is SetClipAudio -> setAudio(project, edit)
            is RenameProject -> rename(project, edit)
            is RelinkAsset -> relink(project, edit)
        }
        return when (result) {
            is EditOutcome.Applied -> EditOutcome.Applied(result.project.copy(updatedAtMs = nowMs))
            is EditOutcome.Rejected -> result
        }
    }

    private fun reject(reason: String) = EditOutcome.Rejected(reason)

    private fun addClips(project: Project, edit: AddClips): EditOutcome {
        if (edit.assets.isEmpty()) return reject("Tidak ada media yang dipilih.")
        if (edit.assets.size != edit.newClipIds.size) return reject("Jumlah ID klip tidak cocok.")
        edit.assets.forEach { asset ->
            if (!asset.hasVideo) return reject("\"${asset.displayName}\" tidak memiliki trek video.")
            if (asset.durationUs < MIN_CLIP_US) return reject("\"${asset.displayName}\" terlalu pendek.")
        }
        val track = project.mainTrack
        val existingIds = project.tracks.flatMap { t -> t.clips.map { it.id } }.toSet()
        if (edit.newClipIds.any { it in existingIds } || edit.newClipIds.toSet().size != edit.newClipIds.size) {
            return reject("ID klip bentrok.")
        }
        val newClips = edit.assets.mapIndexed { i, asset ->
            Clip(id = edit.newClipIds[i], assetId = asset.id, sourceInUs = 0L, sourceOutUs = asset.durationUs)
        }
        val index = (edit.insertIndex ?: track.clips.size).coerceIn(0, track.clips.size)
        val clips = track.clips.toMutableList().apply { addAll(index, newClips) }
        val assets = project.assets + edit.assets.associateBy { it.id }
        return EditOutcome.Applied(
            project.copy(assets = assets).replaceTrack(track.copy(clips = clips).normalized()),
        )
    }

    private fun split(project: Project, edit: SplitClip): EditOutcome {
        val (track, clip) = project.findClip(edit.clipId) ?: return reject("Klip tidak ditemukan.")
        if (project.findClip(edit.newClipId) != null) return reject("ID klip bentrok.")
        val offset = edit.atTimelineUs - clip.timelineStartUs
        if (offset < MIN_CLIP_US || clip.durationUs - offset < MIN_CLIP_US) {
            return reject("Posisi belah terlalu dekat dengan tepi klip.")
        }
        val cut = clip.sourceInUs + offset
        val left = clip.copy(sourceOutUs = cut)
        val right = clip.copy(id = edit.newClipId, sourceInUs = cut, timelineStartUs = clip.timelineStartUs + offset)
        val index = track.indexOf(clip.id)
        val clips = track.clips.toMutableList().apply {
            set(index, left)
            add(index + 1, right)
        }
        return EditOutcome.Applied(project.replaceTrack(track.copy(clips = clips).normalized()))
    }

    private fun trim(project: Project, edit: TrimClip): EditOutcome {
        val (track, clip) = project.findClip(edit.clipId) ?: return reject("Klip tidak ditemukan.")
        val asset = project.assets[clip.assetId] ?: return reject("Media sumber hilang.")
        if (edit.newSourceInUs < 0 || edit.newSourceOutUs > asset.durationUs) {
            return reject("Batas pangkas di luar durasi media.")
        }
        if (edit.newSourceOutUs - edit.newSourceInUs < MIN_CLIP_US) return reject("Klip terlalu pendek.")
        if (edit.newSourceInUs == clip.sourceInUs && edit.newSourceOutUs == clip.sourceOutUs) {
            return reject("Tidak ada perubahan.")
        }
        val trimmed = clip.copy(sourceInUs = edit.newSourceInUs, sourceOutUs = edit.newSourceOutUs)
        val clips = track.clips.map { if (it.id == clip.id) trimmed else it }
        return EditOutcome.Applied(project.replaceTrack(track.copy(clips = clips).normalized()))
    }

    private fun move(project: Project, edit: MoveClip): EditOutcome {
        val (track, clip) = project.findClip(edit.clipId) ?: return reject("Klip tidak ditemukan.")
        if (!track.magnetic) return reject("Pindah urutan hanya untuk trek utama.")
        val from = track.indexOf(clip.id)
        val to = edit.toIndex.coerceIn(0, track.clips.lastIndex)
        if (from == to) return reject("Klip sudah di posisi itu.")
        val clips = track.clips.toMutableList().apply { add(to, removeAt(from)) }
        return EditOutcome.Applied(project.replaceTrack(track.copy(clips = clips).normalized()))
    }

    private fun delete(project: Project, edit: DeleteClips): EditOutcome {
        if (edit.clipIds.isEmpty()) return reject("Tidak ada klip dipilih.")
        val missing = edit.clipIds.filter { project.findClip(it) == null }
        if (missing.isNotEmpty()) return reject("Klip tidak ditemukan.")
        val tracks = project.tracks.map { t ->
            if (t.clips.none { it.id in edit.clipIds }) t
            else t.copy(clips = t.clips.filterNot { it.id in edit.clipIds }).normalized()
        }
        val usedAssets = tracks.flatMap { t -> t.clips.map { it.assetId } }.toSet()
        return EditOutcome.Applied(
            project.copy(tracks = tracks, assets = project.assets.filterKeys { it in usedAssets }),
        )
    }

    private fun duplicate(project: Project, edit: DuplicateClip): EditOutcome {
        val (track, clip) = project.findClip(edit.clipId) ?: return reject("Klip tidak ditemukan.")
        if (project.findClip(edit.newClipId) != null) return reject("ID klip bentrok.")
        val index = track.indexOf(clip.id)
        val copy = clip.copy(id = edit.newClipId, timelineStartUs = clip.timelineEndUs)
        val clips = track.clips.toMutableList().apply { add(index + 1, copy) }
        return EditOutcome.Applied(project.replaceTrack(track.copy(clips = clips).normalized()))
    }

    private fun setAudio(project: Project, edit: SetClipAudio): EditOutcome {
        val (track, clip) = project.findClip(edit.clipId) ?: return reject("Klip tidak ditemukan.")
        if (edit.volume !in 0f..1f) return reject("Volume harus 0–100%.")
        if (clip.volume == edit.volume && clip.muted == edit.muted) return reject("Tidak ada perubahan.")
        val updated = clip.copy(volume = edit.volume, muted = edit.muted)
        return EditOutcome.Applied(project.replaceTrack(track.copy(clips = track.clips.map { if (it.id == clip.id) updated else it })))
    }

    private fun relink(project: Project, edit: RelinkAsset): EditOutcome {
        if (!project.assets.containsKey(edit.assetId)) return reject("Media tidak ditemukan di proyek.")
        if (!edit.replacement.hasVideo) return reject("File pengganti tidak memiliki video.")
        val needed = project.tracks.flatMap { it.clips }.filter { it.assetId == edit.assetId }.maxOfOrNull { it.sourceOutUs } ?: 0L
        if (edit.replacement.durationUs < needed) {
            return reject("File pengganti lebih pendek dari bagian yang dipakai (${Timecode.short(needed)}).")
        }
        val replacement = edit.replacement.copy(id = edit.assetId)
        return EditOutcome.Applied(project.copy(assets = project.assets + (edit.assetId to replacement)))
    }

    private fun rename(project: Project, edit: RenameProject): EditOutcome {
        val name = edit.name.trim()
        if (name.isEmpty()) return reject("Nama proyek tidak boleh kosong.")
        if (name.length > MAX_NAME_LENGTH) return reject("Nama proyek maksimal $MAX_NAME_LENGTH karakter.")
        if (name == project.name) return reject("Tidak ada perubahan.")
        return EditOutcome.Applied(project.copy(name = name))
    }
}

/**
 * Editing session with a real command history. Each applied [Edit] is recorded with the project
 * state before and after it; undo/redo restore those immutable snapshots exactly.
 */
class EditorSession(initial: Project, private val historyLimit: Int = 200) {
    data class HistoryEntry(val label: String, val before: Project, val after: Project)

    var project: Project = initial
        private set

    private val undoStack = ArrayDeque<HistoryEntry>()
    private val redoStack = ArrayDeque<HistoryEntry>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val undoLabel: String? get() = undoStack.lastOrNull()?.label
    val redoLabel: String? get() = redoStack.lastOrNull()?.label
    val undoDepth: Int get() = undoStack.size

    fun perform(edit: Edit, nowMs: Long = project.updatedAtMs): EditOutcome {
        val outcome = TimelineEngine.apply(project, edit, nowMs)
        if (outcome is EditOutcome.Applied) {
            undoStack.addLast(HistoryEntry(edit.label, project, outcome.project))
            while (undoStack.size > historyLimit) undoStack.removeFirst()
            redoStack.clear()
            project = outcome.project
        }
        return outcome
    }

    /** Returns the label of the undone edit, or null if nothing to undo. */
    fun undo(): String? {
        val entry = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(entry)
        project = entry.before
        return entry.label
    }

    fun redo(): String? {
        val entry = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(entry)
        project = entry.after
        return entry.label
    }
}
