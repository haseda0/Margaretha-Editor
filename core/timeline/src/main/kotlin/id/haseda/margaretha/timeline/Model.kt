package id.haseda.margaretha.timeline

/**
 * Margaretha Editor timeline model.
 *
 * All time values are integer microseconds (Long). No floating point is used for positions or
 * durations, so repeated edits never accumulate drift.
 */
const val US_PER_MS = 1_000L
const val US_PER_SECOND = 1_000_000L

/** A source media file referenced by a project. Originals are never modified. */
data class MediaAsset(
    val id: String,
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val durationUs: Long,
    val width: Int,
    val height: Int,
    /** Rotation metadata of the source (0, 90, 180, 270). */
    val rotationDegrees: Int,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
    /** Nominal frame rate if known, otherwise 0. */
    val frameRate: Float = 0f,
) {
    /** Width as the viewer sees it, after rotation metadata is applied. */
    val displayWidth: Int get() = if (rotationDegrees % 180 != 0) height else width

    /** Height as the viewer sees it, after rotation metadata is applied. */
    val displayHeight: Int get() = if (rotationDegrees % 180 != 0) width else height
}

/**
 * A non-destructive reference to a section of a [MediaAsset].
 *
 * Schema v1 deliberately contains only properties that the preview AND export pipelines render.
 * Speed, transform, crop, effect stack and keyframes are added in later schema versions together
 * with their rendering implementation (see docs/TIMELINE_ENGINE.md).
 */
data class Clip(
    val id: String,
    val assetId: String,
    val sourceInUs: Long,
    val sourceOutUs: Long,
    val timelineStartUs: Long = 0L,
    /** Linear gain 0.0..1.0 applied to the clip's original audio. */
    val volume: Float = 1f,
    val muted: Boolean = false,
) {
    init {
        require(sourceInUs >= 0) { "sourceInUs must be >= 0" }
        require(sourceOutUs > sourceInUs) { "sourceOutUs must be > sourceInUs" }
        require(timelineStartUs >= 0) { "timelineStartUs must be >= 0" }
        require(volume in 0f..1f) { "volume must be within 0..1" }
    }

    val durationUs: Long get() = sourceOutUs - sourceInUs
    val timelineEndUs: Long get() = timelineStartUs + durationUs

    /** Gain actually rendered, combining mute and volume. */
    val effectiveVolume: Float get() = if (muted) 0f else volume

    fun containsTimelineUs(timeUs: Long): Boolean = timeUs >= timelineStartUs && timeUs < timelineEndUs
}

enum class TrackKind { MAIN_VIDEO, OVERLAY_VIDEO, AUDIO, TEXT }

/**
 * A track of clips. A magnetic track keeps its clips contiguous starting at 0: positions are
 * derived from order, so deleting a clip ripples the following clips left.
 */
data class Track(
    val id: String,
    val kind: TrackKind,
    val clips: List<Clip>,
    val magnetic: Boolean,
) {
    val endUs: Long get() = clips.maxOfOrNull { it.timelineEndUs } ?: 0L

    fun indexOf(clipId: String): Int = clips.indexOfFirst { it.id == clipId }

    fun clip(clipId: String): Clip? = clips.firstOrNull { it.id == clipId }

    /** Clip under [timeUs]; at the exact end of the track the last clip is returned. */
    fun clipAt(timeUs: Long): Clip? = locate(timeUs)?.clip

    /** Maps a timeline time to (clip, offset inside clip). Clamps to the track range. */
    fun locate(timeUs: Long): TimelinePosition? {
        if (clips.isEmpty()) return null
        val t = timeUs.coerceAtLeast(0L)
        clips.forEachIndexed { index, clip ->
            if (clip.containsTimelineUs(t)) return TimelinePosition(index, clip, t - clip.timelineStartUs)
        }
        val last = clips.last()
        return if (t >= last.timelineEndUs) {
            TimelinePosition(clips.lastIndex, last, last.durationUs)
        } else {
            // Inside a gap (only possible on non-magnetic tracks): snap to next clip start.
            val nextIndex = clips.indexOfFirst { it.timelineStartUs > t }
            if (nextIndex >= 0) TimelinePosition(nextIndex, clips[nextIndex], 0L) else null
        }
    }

    /** Re-derives contiguous positions for magnetic tracks. */
    fun normalized(): Track {
        if (!magnetic) return copy(clips = clips.sortedBy { it.timelineStartUs })
        var cursor = 0L
        val laidOut = clips.map { clip ->
            val placed = if (clip.timelineStartUs == cursor) clip else clip.copy(timelineStartUs = cursor)
            cursor += clip.durationUs
            placed
        }
        return copy(clips = laidOut)
    }
}

data class TimelinePosition(val clipIndex: Int, val clip: Clip, val offsetUs: Long) {
    val timelineUs: Long get() = clip.timelineStartUs + offsetUs
}

enum class AspectRatio(val width: Int, val height: Int, val label: String, val usage: String) {
    PORTRAIT_9_16(9, 16, "9:16", "TikTok · Reels · Shorts"),
    LANDSCAPE_16_9(16, 9, "16:9", "YouTube · Layar lebar"),
    SQUARE_1_1(1, 1, "1:1", "Feed persegi"),
    PORTRAIT_4_5(4, 5, "4:5", "Feed potret");

    val ratio: Float get() = width.toFloat() / height.toFloat()
}

data class Canvas(val aspect: AspectRatio, val frameRate: Int = 30) {
    init {
        require(frameRate in 1..120) { "frameRate out of range" }
    }

    val frameDurationUs: Long get() = US_PER_SECOND / frameRate
}

data class Project(
    val id: String,
    val name: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val canvas: Canvas,
    val assets: Map<String, MediaAsset>,
    val tracks: List<Track>,
) {
    val mainTrack: Track
        get() = tracks.firstOrNull { it.kind == TrackKind.MAIN_VIDEO }
            ?: error("Project has no main video track")

    val durationUs: Long get() = tracks.maxOfOrNull { it.endUs } ?: 0L

    val clipCount: Int get() = tracks.sumOf { it.clips.size }

    fun findClip(clipId: String): Pair<Track, Clip>? {
        for (track in tracks) {
            val clip = track.clip(clipId)
            if (clip != null) return track to clip
        }
        return null
    }

    fun replaceTrack(track: Track): Project =
        copy(tracks = tracks.map { if (it.id == track.id) track else it })

    companion object {
        const val MAIN_TRACK_ID = "track-main"

        fun create(id: String, name: String, canvas: Canvas, nowMs: Long): Project = Project(
            id = id,
            name = name,
            createdAtMs = nowMs,
            updatedAtMs = nowMs,
            canvas = canvas,
            assets = emptyMap(),
            tracks = listOf(Track(MAIN_TRACK_ID, TrackKind.MAIN_VIDEO, emptyList(), magnetic = true)),
        )
    }
}
