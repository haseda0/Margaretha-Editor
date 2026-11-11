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

/** Adds media to the project library without placing it on the timeline. */
data class AddAssets(val assets: List<MediaAsset>) : Edit {
    override val label: String get() = "Impor ${assets.size} media"
}

/** Removes an unused asset from the library. */
data class RemoveAsset(val assetId: String) : Edit {
    override val label: String get() = "Hapus media"
}

/** Places the sound of [asset] on an audio track at (or right after) [atTimelineUs]. */
data class AddAudioClip(val asset: MediaAsset, val newClipId: String, val trackId: String, val atTimelineUs: Long) : Edit {
    override val label: String get() = "Tambah audio"
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

/** Moves a clip of a free (audio) track to a new start time. */
data class MoveFreeClip(val clipId: String, val newStartUs: Long) : Edit {
    override val label: String get() = "Geser klip"
}

/** Removes clips; magnetic tracks ripple automatically. */
data class DeleteClips(val clipIds: Set<String>) : Edit {
    override val label: String get() = if (clipIds.size == 1) "Hapus klip" else "Hapus ${clipIds.size} klip"
}

data class DuplicateClip(val clipId: String, val newClipId: String) : Edit {
    override val label: String get() = "Duplikat klip"
}

/** Null fades keep the clip's current fades. */
data class SetClipAudio(
    val clipId: String,
    val volume: Float,
    val muted: Boolean,
    val fadeInUs: Long? = null,
    val fadeOutUs: Long? = null,
) : Edit {
    override val label: String get() = if (muted) "Bisukan klip" else "Ubah audio"
}

/** A null [intensity] keeps the clip's current intensity. */
data class SetClipColorLook(val clipId: String, val look: ColorLook, val intensity: Float? = null) : Edit {
    override val label: String get() = "Warna: ${look.title}"
}

/** Adds a keyframe at the timeline offset, or replaces the one already there. */
data class SetKeyframe(val clipId: String, val offsetUs: Long, val transform: Transform) : Edit {
    override val label: String get() = "Keyframe"
}

data class RemoveKeyframe(val clipId: String, val offsetUs: Long) : Edit {
    override val label: String get() = "Hapus keyframe"
}

/** Removes all keyframes; the clip keeps the pose it had at [keepOffsetUs]. */
data class ClearKeyframes(val clipId: String, val keepOffsetUs: Long) : Edit {
    override val label: String get() = "Hapus semua keyframe"
}

/** Swaps the media behind a clip while keeping its edits. */
data class ReplaceClipMedia(val clipId: String, val asset: MediaAsset) : Edit {
    override val label: String get() = "Ganti media"
}

data class SetClipSpeed(val clipId: String, val speedPermille: Int) : Edit {
    override val label: String get() = "Kecepatan ${speedPermille / 1000f}×"
}

data class SetClipTransform(val clipId: String, val transform: Transform, val crop: Crop) : Edit {
    override val label: String get() = "Ubah posisi/ukuran"
}

data class SetClipGrade(val clipId: String, val grade: ColorGrade) : Edit {
    override val label: String get() = "Color grading"
}

data class SetClipFx(val clipId: String, val fx: ClipFx) : Edit {
    override val label: String get() = "Efek"
}

data class SetClipTransitions(val clipId: String, val enter: Transition, val exit: Transition) : Edit {
    override val label: String get() = "Transisi"
}

data class AddText(val item: TextItem, val trackId: String) : Edit {
    override val label: String get() = if (trackId == Project.SUBTITLE_TRACK_ID) "Tambah subtitle" else "Tambah teks"
}

/** Replaces the text item with the same id (content, timing, position or style). */
data class UpdateText(val item: TextItem) : Edit {
    override val label: String get() = "Ubah teks"
}

data class DeleteTexts(val textIds: Set<String>) : Edit {
    override val label: String get() = if (textIds.size == 1) "Hapus teks" else "Hapus ${textIds.size} teks"
}

/** Adds many subtitle cues at once (SRT import). [replace] clears the existing cues first. */
data class ImportSubtitles(val items: List<TextItem>, val replace: Boolean) : Edit {
    override val label: String get() = "Impor ${items.size} subtitle"
}

data class SetTrackFlags(val trackId: String, val enabled: Boolean, val locked: Boolean) : Edit {
    override val label: String get() = "Ubah trek"
}

data class SetCanvas(val canvas: Canvas) : Edit {
    override val label: String get() = "Ubah kanvas ${canvas.aspect.label}"
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

    /** Still images get this duration when dropped on the timeline. */
    const val DEFAULT_IMAGE_US: Long = 3_000_000L

    /** Nominal "source length" of a still image: the longest an image clip can be stretched. */
    const val IMAGE_ASSET_US: Long = 600_000_000L
    const val MAX_FADE_US: Long = 10_000_000L
    const val MAX_SUBTITLE_IMPORT = 5_000

    fun apply(project: Project, edit: Edit, nowMs: Long = project.updatedAtMs): EditOutcome {
        val result = try {
            when (edit) {
                is AddClips -> addClips(project, edit)
                is AddAssets -> addAssets(project, edit)
                is RemoveAsset -> removeAsset(project, edit)
                is AddAudioClip -> addAudio(project, edit)
                is SplitClip -> split(project, edit)
                is TrimClip -> trim(project, edit)
                is MoveClip -> move(project, edit)
                is MoveFreeClip -> moveFree(project, edit)
                is DeleteClips -> delete(project, edit)
                is DuplicateClip -> duplicate(project, edit)
                is SetClipAudio -> setAudio(project, edit)
                is SetClipColorLook -> updateClip(project, edit.clipId, videoOnly = true) {
                    it.copy(colorLook = edit.look, lookIntensity = edit.intensity ?: it.lookIntensity)
                }
                is SetKeyframe -> setKeyframe(project, edit)
                is RemoveKeyframe -> removeKeyframe(project, edit)
                is ClearKeyframes -> updateClip(project, edit.clipId, videoOnly = true) {
                    it.copy(transform = it.transformAtOffsetUs(edit.keepOffsetUs.coerceIn(0L, it.durationUs)), keyframes = emptyList())
                }
                is ReplaceClipMedia -> replaceMedia(project, edit)
                is SetClipSpeed -> setSpeed(project, edit)
                is SetClipTransform -> updateClip(project, edit.clipId, videoOnly = true) { it.copy(transform = edit.transform, crop = edit.crop) }
                is SetClipGrade -> updateClip(project, edit.clipId, videoOnly = true) { it.copy(grade = edit.grade) }
                is SetClipFx -> updateClip(project, edit.clipId, videoOnly = true) { it.copy(fx = edit.fx) }
                is SetClipTransitions -> updateClip(project, edit.clipId, videoOnly = true) { it.copy(enter = edit.enter, exit = edit.exit) }
                is AddText -> addText(project, edit)
                is UpdateText -> updateText(project, edit)
                is DeleteTexts -> deleteTexts(project, edit)
                is ImportSubtitles -> importSubtitles(project, edit)
                is SetTrackFlags -> setTrackFlags(project, edit)
                is SetCanvas -> if (edit.canvas == project.canvas) reject("Tidak ada perubahan.") else EditOutcome.Applied(project.copy(canvas = edit.canvas))
                is RenameProject -> rename(project, edit)
                is RelinkAsset -> relink(project, edit)
            }
        } catch (e: IllegalArgumentException) {
            // Model invariants (ranges, minimum durations) are the last line of defence.
            reject("Nilai tidak valid: ${e.message}")
        }
        return when (result) {
            is EditOutcome.Applied -> EditOutcome.Applied(result.project.copy(updatedAtMs = nowMs))
            is EditOutcome.Rejected -> result
        }
    }

    private fun reject(reason: String) = EditOutcome.Rejected(reason)

    private fun lockedReason(track: Track): String? = if (track.locked) "Trek terkunci. Buka kuncinya dulu." else null

    private fun allIds(project: Project): Set<String> =
        project.tracks.flatMap { t -> t.clips.map { it.id } + t.texts.map { it.id } }.toSet()

    private fun assetProblem(asset: MediaAsset): String? = when {
        asset.id.isBlank() -> "ID media tidak boleh kosong."
        asset.uri.isBlank() || asset.displayName.isBlank() || asset.mimeType.isBlank() -> "Metadata media \"${asset.displayName}\" tidak valid."
        asset.kind != MediaKind.AUDIO && (asset.width <= 0 || asset.height <= 0) -> "Metadata media \"${asset.displayName}\" tidak valid."
        asset.durationUs < MIN_CLIP_US -> "\"${asset.displayName}\" terlalu pendek."
        else -> null
    }

    /** Merges assets into the library; the same asset may be reused, a conflicting one may not. */
    private fun mergeAssets(project: Project, assets: List<MediaAsset>): Pair<Map<String, MediaAsset>?, String?> {
        if (assets.map { it.id }.toSet().size != assets.size) return null to "Daftar media berisi ID aset duplikat."
        assets.forEach { asset ->
            assetProblem(asset)?.let { return null to it }
            val existing = project.assets[asset.id]
            if (existing != null && existing != asset) return null to "ID media bentrok dengan media lain di proyek."
        }
        return (project.assets + assets.associateBy { it.id }) to null
    }

    /** Returns the track, creating a known free track on first use. */
    private fun ensureTrack(project: Project, trackId: String): Pair<Project, Track>? {
        project.track(trackId)?.let { return project to it }
        val kind = Project.FREE_TRACKS.firstOrNull { it.first == trackId }?.second ?: return null
        val track = Track(trackId, kind, emptyList(), magnetic = false)
        val order = listOf(Project.MAIN_TRACK_ID) + Project.FREE_TRACKS.map { it.first }
        val tracks = (project.tracks + track).sortedBy { order.indexOf(it.id).let { i -> if (i < 0) order.size else i } }
        return project.copy(tracks = tracks) to track
    }

    private fun addClips(project: Project, edit: AddClips): EditOutcome {
        if (edit.assets.isEmpty()) return reject("Tidak ada media yang dipilih.")
        if (edit.assets.size != edit.newClipIds.size) return reject("Jumlah ID klip tidak cocok.")
        if (edit.newClipIds.any { it.isBlank() }) return reject("ID klip tidak boleh kosong.")
        edit.assets.forEach { asset ->
            if (asset.id.isBlank()) return reject("ID media tidak boleh kosong.")
            if (!asset.hasVideo || asset.kind == MediaKind.AUDIO) return reject("\"${asset.displayName}\" tidak memiliki trek video.")
        }
        val (assets, problem) = mergeAssets(project, edit.assets)
        if (assets == null) return reject(problem ?: "Media tidak valid.")
        val track = project.mainTrack
        lockedReason(track)?.let { return reject(it) }
        val existingIds = allIds(project)
        if (edit.newClipIds.any { it in existingIds } || edit.newClipIds.toSet().size != edit.newClipIds.size) {
            return reject("ID klip bentrok.")
        }
        val newClips = edit.assets.mapIndexed { i, asset ->
            val out = if (asset.kind == MediaKind.IMAGE) minOf(DEFAULT_IMAGE_US, asset.durationUs) else asset.durationUs
            Clip(id = edit.newClipIds[i], assetId = asset.id, sourceInUs = 0L, sourceOutUs = out)
        }
        val index = (edit.insertIndex ?: track.clips.size).coerceIn(0, track.clips.size)
        val clips = track.clips.toMutableList().apply { addAll(index, newClips) }
        return EditOutcome.Applied(
            project.copy(assets = assets).replaceTrack(track.copy(clips = clips).normalized()),
        )
    }

    private fun addAssets(project: Project, edit: AddAssets): EditOutcome {
        if (edit.assets.isEmpty()) return reject("Tidak ada media yang dipilih.")
        val (assets, problem) = mergeAssets(project, edit.assets)
        if (assets == null) return reject(problem ?: "Media tidak valid.")
        if (assets == project.assets) return reject("Media sudah ada di proyek.")
        return EditOutcome.Applied(project.copy(assets = assets))
    }

    private fun removeAsset(project: Project, edit: RemoveAsset): EditOutcome {
        if (!project.assets.containsKey(edit.assetId)) return reject("Media tidak ditemukan di proyek.")
        val uses = project.tracks.sumOf { t -> t.clips.count { it.assetId == edit.assetId } }
        if (uses > 0) return reject("Media masih dipakai $uses klip. Hapus klipnya dulu.")
        return EditOutcome.Applied(project.copy(assets = project.assets - edit.assetId))
    }

    private fun addAudio(project: Project, edit: AddAudioClip): EditOutcome {
        val asset = edit.asset
        if (!asset.hasAudio) return reject("\"${asset.displayName}\" tidak memiliki suara.")
        if (edit.newClipId.isBlank() || edit.newClipId in allIds(project)) return reject("ID klip bentrok.")
        val (assets, problem) = mergeAssets(project, listOf(asset))
        if (assets == null) return reject(problem ?: "Media tidak valid.")
        val (withTrack, track) = ensureTrack(project, edit.trackId) ?: return reject("Trek tidak ditemukan.")
        if (track.kind != TrackKind.AUDIO) return reject("Audio hanya bisa ditaruh di trek audio.")
        lockedReason(track)?.let { return reject(it) }
        var clip = Clip(edit.newClipId, asset.id, 0L, asset.durationUs, timelineStartUs = edit.atTimelineUs.coerceAtLeast(0L))
        clip = clip.copy(timelineStartUs = firstFreeStart(track, clip))
        return EditOutcome.Applied(
            withTrack.copy(assets = assets).replaceTrack(track.copy(clips = track.clips + clip).normalized()),
        )
    }

    /** Earliest start at or after the clip's wanted start that does not overlap the track. */
    private fun firstFreeStart(track: Track, clip: Clip): Long {
        var start = clip.timelineStartUs
        while (true) {
            val blocker = track.clips.sortedBy { it.timelineStartUs }
                .firstOrNull { it.id != clip.id && start < it.timelineEndUs && it.timelineStartUs < start + clip.durationUs }
                ?: return start
            start = blocker.timelineEndUs
        }
    }

    private fun split(project: Project, edit: SplitClip): EditOutcome {
        val (track, clip) = project.findClip(edit.clipId) ?: return reject("Klip tidak ditemukan.")
        lockedReason(track)?.let { return reject(it) }
        if (edit.newClipId.isBlank() || edit.newClipId in allIds(project)) return reject("ID klip bentrok.")
        val offset = edit.atTimelineUs - clip.timelineStartUs
        if (offset < MIN_CLIP_US || clip.durationUs - offset < MIN_CLIP_US) {
            return reject("Posisi belah terlalu dekat dengan tepi klip.")
        }
        val cut = clip.sourceAtOffsetUs(offset)
        // Each half keeps only the fade/transition on its own outer edge.
        val left = clip.copy(sourceOutUs = cut, audioFadeOutUs = 0L, exit = Transition.NONE)
        val right = clip.copy(
            id = edit.newClipId,
            sourceInUs = cut,
            timelineStartUs = clip.timelineStartUs + offset,
            audioFadeInUs = 0L,
            enter = Transition.NONE,
        )
        val index = track.indexOf(clip.id)
        val clips = track.clips.toMutableList().apply {
            set(index, left)
            add(index + 1, right)
        }
        return EditOutcome.Applied(project.replaceTrack(track.copy(clips = clips).normalized()))
    }

    private fun trim(project: Project, edit: TrimClip): EditOutcome {
        val (track, clip) = project.findClip(edit.clipId) ?: return reject("Klip tidak ditemukan.")
        lockedReason(track)?.let { return reject(it) }
        val asset = project.assets[clip.assetId] ?: return reject("Media sumber hilang.")
        if (edit.newSourceInUs < 0 || edit.newSourceOutUs > asset.durationUs) {
            return reject("Batas pangkas di luar durasi media.")
        }
        if ((edit.newSourceOutUs - edit.newSourceInUs) * 1000L / clip.speedPermille < MIN_CLIP_US) return reject("Klip terlalu pendek.")
        if (edit.newSourceInUs == clip.sourceInUs && edit.newSourceOutUs == clip.sourceOutUs) {
            return reject("Tidak ada perubahan.")
        }
        var trimmed = clip.copy(sourceInUs = edit.newSourceInUs, sourceOutUs = edit.newSourceOutUs)
        if (!track.magnetic) {
            // Free track: trimming the head keeps the tail where it is on the timeline.
            val shift = (edit.newSourceInUs - clip.sourceInUs) * 1000L / clip.speedPermille
            val start = clip.timelineStartUs + shift
            if (start < 0) return reject("Klip tidak bisa diperpanjang melewati awal timeline.")
            trimmed = trimmed.copy(timelineStartUs = start)
            if (track.overlaps(trimmed)) return reject("Bertabrakan dengan klip lain di trek ini.")
        }
        val clips = track.clips.map { if (it.id == clip.id) trimmed else it }
        return EditOutcome.Applied(project.replaceTrack(track.copy(clips = clips).normalized()))
    }

    private fun move(project: Project, edit: MoveClip): EditOutcome {
        val (track, clip) = project.findClip(edit.clipId) ?: return reject("Klip tidak ditemukan.")
        lockedReason(track)?.let { return reject(it) }
        if (!track.magnetic) return reject("Pindah urutan hanya untuk trek utama.")
        val from = track.indexOf(clip.id)
        val to = edit.toIndex.coerceIn(0, track.clips.lastIndex)
        if (from == to) return reject("Klip sudah di posisi itu.")
        val clips = track.clips.toMutableList().apply { add(to, removeAt(from)) }
        return EditOutcome.Applied(project.replaceTrack(track.copy(clips = clips).normalized()))
    }

    private fun moveFree(project: Project, edit: MoveFreeClip): EditOutcome {
        val (track, clip) = project.findClip(edit.clipId) ?: return reject("Klip tidak ditemukan.")
        lockedReason(track)?.let { return reject(it) }
        if (track.magnetic) return reject("Klip trek utama diurutkan, bukan digeser bebas.")
        val start = edit.newStartUs.coerceAtLeast(0L)
        if (start == clip.timelineStartUs) return reject("Tidak ada perubahan.")
        val moved = clip.copy(timelineStartUs = start)
        if (track.overlaps(moved)) return reject("Bertabrakan dengan klip lain di trek ini.")
        return EditOutcome.Applied(project.replaceTrack(track.copy(clips = track.clips.map { if (it.id == clip.id) moved else it }).normalized()))
    }

    private fun delete(project: Project, edit: DeleteClips): EditOutcome {
        if (edit.clipIds.isEmpty()) return reject("Tidak ada klip dipilih.")
        val found = edit.clipIds.map { project.findClip(it) ?: return reject("Klip tidak ditemukan.") }
        found.forEach { (track, _) -> lockedReason(track)?.let { return reject(it) } }
        val tracks = project.tracks.map { t ->
            if (t.clips.none { it.id in edit.clipIds }) t
            else t.copy(clips = t.clips.filterNot { it.id in edit.clipIds }).normalized()
        }
        // Assets stay in the project library; they are removed explicitly with RemoveAsset.
        return EditOutcome.Applied(project.copy(tracks = tracks))
    }

    private fun duplicate(project: Project, edit: DuplicateClip): EditOutcome {
        val (track, clip) = project.findClip(edit.clipId) ?: return reject("Klip tidak ditemukan.")
        lockedReason(track)?.let { return reject(it) }
        if (edit.newClipId.isBlank() || edit.newClipId in allIds(project)) return reject("ID klip bentrok.")
        val index = track.indexOf(clip.id)
        var copy = clip.copy(id = edit.newClipId, timelineStartUs = clip.timelineEndUs)
        if (!track.magnetic) copy = copy.copy(timelineStartUs = firstFreeStart(track, copy))
        val clips = track.clips.toMutableList().apply { add(index + 1, copy) }
        return EditOutcome.Applied(project.replaceTrack(track.copy(clips = clips).normalized()))
    }

    /** Applies a per-clip property change; rejects no-ops, locked tracks and (optionally) audio clips. */
    private fun updateClip(project: Project, clipId: String, videoOnly: Boolean = false, change: (Clip) -> Clip): EditOutcome {
        val (track, clip) = project.findClip(clipId) ?: return reject("Klip tidak ditemukan.")
        lockedReason(track)?.let { return reject(it) }
        if (videoOnly && track.kind == TrackKind.AUDIO) return reject("Pengaturan ini hanya untuk klip video.")
        val updated = change(clip)
        if (updated == clip) return reject("Tidak ada perubahan.")
        if (!track.magnetic && track.overlaps(updated)) return reject("Bertabrakan dengan klip lain di trek ini.")
        return EditOutcome.Applied(project.replaceTrack(track.copy(clips = track.clips.map { if (it.id == clip.id) updated else it }).normalized()))
    }

    private fun setAudio(project: Project, edit: SetClipAudio): EditOutcome {
        if (edit.volume !in 0f..1f) return reject("Volume harus 0–100%.")
        val fades = listOfNotNull(edit.fadeInUs, edit.fadeOutUs)
        if (fades.any { it !in 0L..MAX_FADE_US }) return reject("Durasi fade harus 0–10 detik.")
        return updateClip(project, edit.clipId) {
            it.copy(
                volume = edit.volume,
                muted = edit.muted,
                audioFadeInUs = edit.fadeInUs ?: it.audioFadeInUs,
                audioFadeOutUs = edit.fadeOutUs ?: it.audioFadeOutUs,
            )
        }
    }

    private fun setSpeed(project: Project, edit: SetClipSpeed): EditOutcome {
        if (edit.speedPermille !in Clip.MIN_SPEED..Clip.MAX_SPEED) return reject("Kecepatan harus 0,25×–4×.")
        val (track, clip) = project.findClip(edit.clipId) ?: return reject("Klip tidak ditemukan.")
        if (track.kind == TrackKind.AUDIO) return reject("Kecepatan hanya untuk klip video.")
        if (project.assets[clip.assetId]?.kind == MediaKind.IMAGE) return reject("Gambar tidak punya kecepatan. Ubah durasinya dengan memangkas.")
        if (clip.sourceDurationUs * 1000L / edit.speedPermille < MIN_CLIP_US) return reject("Klip jadi terlalu pendek pada kecepatan itu.")
        return updateClip(project, edit.clipId) { it.copy(speedPermille = edit.speedPermille) }
    }

    private fun setKeyframe(project: Project, edit: SetKeyframe): EditOutcome {
        val (_, clip) = project.findClip(edit.clipId) ?: return reject("Klip tidak ditemukan.")
        if (edit.offsetUs !in 0L..clip.durationUs) return reject("Keyframe harus berada di dalam klip.")
        val sourceUs = clip.sourceAtOffsetUs(edit.offsetUs)
        val existing = Keyframes.near(clip.keyframes, sourceUs)
        if (existing == null && clip.keyframes.size >= Keyframes.MAX_PER_CLIP) return reject("Maksimal ${Keyframes.MAX_PER_CLIP} keyframe per klip.")
        val kept = clip.keyframes.filterNot { it === existing }
        val keyframes = (kept + Keyframe(existing?.sourceUs ?: sourceUs, edit.transform)).sortedBy { it.sourceUs }
        return updateClip(project, edit.clipId, videoOnly = true) { it.copy(keyframes = keyframes) }
    }

    private fun removeKeyframe(project: Project, edit: RemoveKeyframe): EditOutcome {
        val (_, clip) = project.findClip(edit.clipId) ?: return reject("Klip tidak ditemukan.")
        val existing = Keyframes.near(clip.keyframes, clip.sourceAtOffsetUs(edit.offsetUs)) ?: return reject("Tidak ada keyframe di posisi ini.")
        val rest = clip.keyframes.filterNot { it === existing }
        // Removing the last keyframe leaves the clip resting in that keyframe's pose.
        return updateClip(project, edit.clipId, videoOnly = true) {
            it.copy(keyframes = rest, transform = if (rest.isEmpty()) existing.transform else it.transform)
        }
    }

    private fun replaceMedia(project: Project, edit: ReplaceClipMedia): EditOutcome {
        val (track, clip) = project.findClip(edit.clipId) ?: return reject("Klip tidak ditemukan.")
        val asset = edit.asset
        if (asset.id == clip.assetId) return reject("Klip sudah memakai media itu.")
        if (track.kind == TrackKind.AUDIO) {
            if (!asset.hasAudio) return reject("\"${asset.displayName}\" tidak memiliki suara.")
        } else if (!asset.hasVideo || asset.kind == MediaKind.AUDIO) {
            return reject("\"${asset.displayName}\" tidak memiliki gambar.")
        }
        val (assets, problem) = mergeAssets(project, listOf(asset))
        if (assets == null) return reject(problem ?: "Media tidak valid.")
        // The clip keeps its timeline length; it starts at the head of the new media.
        val neededUs = clip.sourceDurationUs
        if (asset.durationUs < neededUs) return reject("Media pengganti lebih pendek dari klip (${Timecode.short(neededUs)}).")
        val speed = if (asset.kind == MediaKind.IMAGE) 1000 else clip.speedPermille
        val sourceLength = clip.durationUs * speed / 1000L
        val outcome = updateClip(project.copy(assets = assets), edit.clipId) {
            it.copy(assetId = asset.id, sourceInUs = 0L, sourceOutUs = sourceLength.coerceAtLeast(MIN_CLIP_US), speedPermille = speed, keyframes = emptyList())
        }
        return outcome
    }

    private fun textProblem(item: TextItem): String? = when {
        item.id.isBlank() -> "ID teks tidak boleh kosong."
        item.text.isBlank() -> "Teks tidak boleh kosong."
        item.text.length > TextItem.MAX_LENGTH -> "Teks maksimal ${TextItem.MAX_LENGTH} karakter."
        else -> null
    }

    private fun addText(project: Project, edit: AddText): EditOutcome {
        textProblem(edit.item)?.let { return reject(it) }
        if (edit.item.id in allIds(project)) return reject("ID teks bentrok.")
        val (withTrack, track) = ensureTrack(project, edit.trackId) ?: return reject("Trek tidak ditemukan.")
        if (!track.isTextual) return reject("Teks hanya bisa ditaruh di trek teks.")
        lockedReason(track)?.let { return reject(it) }
        return EditOutcome.Applied(withTrack.replaceTrack(track.copy(texts = track.texts + edit.item).normalized()))
    }

    private fun updateText(project: Project, edit: UpdateText): EditOutcome {
        textProblem(edit.item)?.let { return reject(it) }
        val (track, current) = project.findText(edit.item.id) ?: return reject("Teks tidak ditemukan.")
        lockedReason(track)?.let { return reject(it) }
        if (current == edit.item) return reject("Tidak ada perubahan.")
        return EditOutcome.Applied(project.replaceTrack(track.copy(texts = track.texts.map { if (it.id == current.id) edit.item else it }).normalized()))
    }

    private fun deleteTexts(project: Project, edit: DeleteTexts): EditOutcome {
        if (edit.textIds.isEmpty()) return reject("Tidak ada teks dipilih.")
        val found = edit.textIds.map { project.findText(it) ?: return reject("Teks tidak ditemukan.") }
        found.forEach { (track, _) -> lockedReason(track)?.let { return reject(it) } }
        return EditOutcome.Applied(
            project.copy(tracks = project.tracks.map { t -> if (t.texts.none { it.id in edit.textIds }) t else t.copy(texts = t.texts.filterNot { it.id in edit.textIds }) }),
        )
    }

    private fun importSubtitles(project: Project, edit: ImportSubtitles): EditOutcome {
        if (edit.items.isEmpty()) return reject("Tidak ada subtitle untuk diimpor.")
        if (edit.items.size > MAX_SUBTITLE_IMPORT) return reject("Terlalu banyak subtitle (maks $MAX_SUBTITLE_IMPORT).")
        edit.items.forEach { item -> textProblem(item)?.let { return reject(it) } }
        val (withTrack, track) = ensureTrack(project, Project.SUBTITLE_TRACK_ID) ?: return reject("Trek tidak ditemukan.")
        lockedReason(track)?.let { return reject(it) }
        val kept = if (edit.replace) emptyList() else track.texts
        val taken = allIds(withTrack) - (if (edit.replace) track.texts.map { it.id }.toSet() else emptySet())
        val ids = edit.items.map { it.id }
        if (ids.toSet().size != ids.size || ids.any { it in taken }) return reject("ID teks bentrok.")
        return EditOutcome.Applied(withTrack.replaceTrack(track.copy(texts = kept + edit.items).normalized()))
    }

    private fun setTrackFlags(project: Project, edit: SetTrackFlags): EditOutcome {
        val track = project.track(edit.trackId) ?: return reject("Trek tidak ditemukan.")
        if (track.kind == TrackKind.MAIN_VIDEO && !edit.enabled) return reject("Trek video utama tidak bisa disembunyikan.")
        if (track.enabled == edit.enabled && track.locked == edit.locked) return reject("Tidak ada perubahan.")
        return EditOutcome.Applied(project.replaceTrack(track.copy(enabled = edit.enabled, locked = edit.locked)))
    }

    private fun relink(project: Project, edit: RelinkAsset): EditOutcome {
        val current = project.assets[edit.assetId] ?: return reject("Media tidak ditemukan di proyek.")
        if (current.kind != edit.replacement.kind) return reject("File pengganti harus berjenis sama.")
        if (current.kind != MediaKind.AUDIO && !edit.replacement.hasVideo) return reject("File pengganti tidak memiliki video.")
        val clips = project.tracks.flatMap { it.clips }.filter { it.assetId == edit.assetId }
        if (clips.any { c -> project.findClip(c.id)?.first?.kind == TrackKind.AUDIO } && !edit.replacement.hasAudio) {
            return reject("File pengganti tidak memiliki suara.")
        }
        val needed = clips.maxOfOrNull { it.sourceOutUs } ?: 0L
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
