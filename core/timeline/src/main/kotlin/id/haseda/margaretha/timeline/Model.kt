package id.haseda.margaretha.timeline

/**
 * Margaretha Editor timeline model.
 *
 * All time values are integer microseconds (Long). No floating point is used for positions or
 * durations, so repeated edits never accumulate drift.
 */
const val US_PER_MS = 1_000L
const val US_PER_SECOND = 1_000_000L

enum class MediaKind { VIDEO, IMAGE, AUDIO }

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
    val kind: MediaKind = MediaKind.VIDEO,
) {
    /** Width as the viewer sees it, after rotation metadata is applied. */
    val displayWidth: Int get() = if (rotationDegrees % 180 != 0) height else width

    /** Height as the viewer sees it, after rotation metadata is applied. */
    val displayHeight: Int get() = if (rotationDegrees % 180 != 0) width else height
}

/** Geometric placement of a clip inside the output frame. Offsets are fractions of the frame. */
data class Transform(
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val opacity: Float = 1f,
) {
    init {
        require(scale in MIN_SCALE..MAX_SCALE) { "scale out of range" }
        require(rotation in -360f..360f) { "rotation out of range" }
        require(offsetX in -1f..1f && offsetY in -1f..1f) { "offset out of range" }
        require(opacity in 0f..1f) { "opacity out of range" }
    }

    val isIdentity: Boolean get() = this == IDENTITY

    companion object {
        const val MIN_SCALE = 0.1f
        const val MAX_SCALE = 5f
        val IDENTITY = Transform()
    }
}

/**
 * A pose of the clip at a moment of its **source** media. Storing source time means trimming,
 * splitting and speed changes never have to rewrite keyframes: the motion stays glued to the
 * pictures it was designed for.
 */
data class Keyframe(val sourceUs: Long, val transform: Transform) {
    init {
        require(sourceUs >= 0) { "keyframe time must be >= 0" }
    }
}

object Keyframes {
    /** Two keyframes closer than this are the same keyframe (about one frame at 30 fps). */
    const val MERGE_US = 33_000L
    const val MAX_PER_CLIP = 200

    /** Pose at [sourceUs]: held before the first and after the last keyframe, linear in between. */
    fun poseAt(keyframes: List<Keyframe>, base: Transform, sourceUs: Long): Transform {
        if (keyframes.isEmpty()) return base
        val first = keyframes.first()
        val last = keyframes.last()
        if (sourceUs <= first.sourceUs) return first.transform
        if (sourceUs >= last.sourceUs) return last.transform
        val i = keyframes.indexOfLast { it.sourceUs <= sourceUs }
        val a = keyframes[i]
        val b = keyframes[i + 1]
        val k = (sourceUs - a.sourceUs).toFloat() / (b.sourceUs - a.sourceUs).toFloat()
        fun mix(x: Float, y: Float) = x + (y - x) * k
        return Transform(
            scale = mix(a.transform.scale, b.transform.scale),
            rotation = mix(a.transform.rotation, b.transform.rotation),
            offsetX = mix(a.transform.offsetX, b.transform.offsetX),
            offsetY = mix(a.transform.offsetY, b.transform.offsetY),
            // Flips cannot be blended; the earlier keyframe decides.
            flipH = a.transform.flipH,
            flipV = a.transform.flipV,
            opacity = mix(a.transform.opacity, b.transform.opacity),
        )
    }

    fun near(keyframes: List<Keyframe>, sourceUs: Long): Keyframe? =
        keyframes.minByOrNull { kotlin.math.abs(it.sourceUs - sourceUs) }?.takeIf { kotlin.math.abs(it.sourceUs - sourceUs) <= MERGE_US }
}

/** Fractions (0..MAX) removed from each side of the source picture. */
data class Crop(val left: Float = 0f, val top: Float = 0f, val right: Float = 0f, val bottom: Float = 0f) {
    init {
        require(listOf(left, top, right, bottom).all { it in 0f..MAX }) { "crop out of range" }
        require(left + right <= MAX_TOTAL && top + bottom <= MAX_TOTAL) { "crop removes too much" }
    }

    val isNone: Boolean get() = this == NONE

    companion object {
        const val MAX = 0.45f
        const val MAX_TOTAL = 0.9f
        val NONE = Crop()
    }
}

/** Manual colour grade. Every control is -1..1 with 0 meaning "unchanged". */
data class ColorGrade(
    val exposure: Float = 0f,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f,
) {
    init {
        require(values().all { it in -1f..1f }) { "grade value out of range" }
    }

    fun values(): List<Float> = listOf(exposure, brightness, contrast, saturation, temperature, tint, highlights, shadows, whites, blacks)

    val isNeutral: Boolean get() = this == NEUTRAL

    companion object {
        val NEUTRAL = ColorGrade()
    }
}

/** Stylising filters. Every control is 0..1 with 0 meaning "off". */
data class ClipFx(val blur: Float = 0f, val sharpen: Float = 0f, val vignette: Float = 0f, val grain: Float = 0f) {
    init {
        require(listOf(blur, sharpen, vignette, grain).all { it in 0f..1f }) { "effect value out of range" }
    }

    val isNone: Boolean get() = this == NONE

    companion object {
        val NONE = ClipFx()
    }
}

/**
 * How a clip appears/disappears. Transitions animate against black (or the neighbouring clip's
 * own transition); they are rendered identically in preview and export.
 */
enum class TransitionKind(val title: String) {
    NONE("Tanpa"),
    FADE("Fade"),
    SLIDE("Slide"),
    ZOOM("Zoom"),
    WIPE("Wipe"),
    BLUR("Blur"),
}

data class Transition(val kind: TransitionKind = TransitionKind.NONE, val durationUs: Long = DEFAULT_US) {
    init {
        require(durationUs in MIN_US..MAX_US) { "transition duration out of range" }
    }

    val isNone: Boolean get() = kind == TransitionKind.NONE

    companion object {
        const val MIN_US = 100_000L
        const val MAX_US = 3_000_000L
        const val DEFAULT_US = 500_000L
        val NONE = Transition()
    }
}

/**
 * A non-destructive reference to a section of a [MediaAsset].
 *
 * Every property here is rendered by both the preview and the export pipeline.
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
    val colorLook: ColorLook = ColorLook.ORIGINAL,
    /** Playback speed in thousandths (1000 = normal). Integer so durations never drift. */
    val speedPermille: Int = 1000,
    val audioFadeInUs: Long = 0L,
    val audioFadeOutUs: Long = 0L,
    val transform: Transform = Transform.IDENTITY,
    val crop: Crop = Crop.NONE,
    val grade: ColorGrade = ColorGrade.NEUTRAL,
    val fx: ClipFx = ClipFx.NONE,
    val enter: Transition = Transition.NONE,
    val exit: Transition = Transition.NONE,
    /** Strength of [colorLook], 0..1. */
    val lookIntensity: Float = 1f,
    /** Motion keyframes sorted by source time; empty means the static [transform] is used. */
    val keyframes: List<Keyframe> = emptyList(),
) {
    init {
        require(lookIntensity in 0f..1f) { "look intensity out of range" }
        require(keyframes.zipWithNext().all { (a, b) -> a.sourceUs < b.sourceUs }) { "keyframes must be sorted and unique" }
        require(sourceInUs >= 0) { "sourceInUs must be >= 0" }
        require(sourceOutUs > sourceInUs) { "sourceOutUs must be > sourceInUs" }
        require(timelineStartUs >= 0) { "timelineStartUs must be >= 0" }
        require(volume in 0f..1f) { "volume must be within 0..1" }
        require(speedPermille in MIN_SPEED..MAX_SPEED) { "speed out of range" }
        require(audioFadeInUs >= 0 && audioFadeOutUs >= 0) { "fade must be >= 0" }
    }

    val sourceDurationUs: Long get() = sourceOutUs - sourceInUs

    /** Duration on the timeline, after the speed change. */
    val durationUs: Long get() = sourceDurationUs * 1000L / speedPermille
    val timelineEndUs: Long get() = timelineStartUs + durationUs
    val speed: Float get() = speedPermille / 1000f

    /** Gain actually rendered, combining mute and volume. */
    val effectiveVolume: Float get() = if (muted) 0f else volume

    fun containsTimelineUs(timeUs: Long): Boolean = timeUs >= timelineStartUs && timeUs < timelineEndUs

    /** Source time shown at a timeline offset inside this clip. */
    fun sourceAtOffsetUs(offsetUs: Long): Long = sourceInUs + offsetUs * speedPermille / 1000L

    /** Timeline offset inside this clip at which a source time is shown. */
    fun offsetOfSourceUs(sourceUs: Long): Long = (sourceUs - sourceInUs) * 1000L / speedPermille

    /** Pose (static or keyframed) at a timeline offset inside this clip. */
    fun transformAtOffsetUs(offsetUs: Long): Transform = Keyframes.poseAt(keyframes, transform, sourceAtOffsetUs(offsetUs))

    companion object {
        const val MIN_SPEED = 250
        const val MAX_SPEED = 4000
    }
}

/** Real-time color looks supported by both Media3 preview and MP4 export. */
enum class ColorLook(val title: String, val hue: Float, val saturation: Float, val lightness: Float, val contrast: Float, val monochrome: Boolean = false) {
    ORIGINAL("Asli", 0f, 0f, 0f, 0f),
    CINEMATIC("Cinematic", -4f, -18f, -2f, 0.16f),
    VIVID("Vivid", 0f, 24f, 2f, 0.08f),
    WARM("Warm", 12f, 12f, 3f, 0.04f),
    COOL("Cool", -12f, 8f, 0f, 0.08f),
    FADE("Matte Fade", 0f, -12f, 8f, -0.12f),
    MONOCHROME("B&W", 0f, -100f, 0f, 0.12f, true),
}

enum class TrackKind { MAIN_VIDEO, OVERLAY_VIDEO, AUDIO, TEXT, SUBTITLE }

enum class FontChoice(val title: String) { SANS("Sans"), SERIF("Serif"), MONO("Mono"), CONDENSED("Condensed"), ROUNDED("Rounded") }

enum class TextAlign { LEFT, CENTER, RIGHT }

enum class TextAnimation(val title: String) { NONE("Tanpa"), FADE("Fade"), SLIDE_UP("Naik"), POP("Pop") }

/** Colours are 0xAARRGGBB. Sizes are relative to the frame so every resolution looks the same. */
data class TextStyle(
    val font: FontChoice = FontChoice.SANS,
    /** Text height as a fraction of the frame height. */
    val size: Float = 0.06f,
    val bold: Boolean = true,
    val italic: Boolean = false,
    val color: Long = 0xFFFFFFFF,
    val align: TextAlign = TextAlign.CENTER,
    val strokeColor: Long = 0xFF000000,
    /** Outline thickness as a fraction of the text size (0 = none). */
    val strokeWidth: Float = 0f,
    /** Drop shadow strength 0..1. */
    val shadow: Float = 0.5f,
    /** Background plate colour; alpha 0 means no plate. */
    val backgroundColor: Long = 0x00000000,
) {
    init {
        require(size in MIN_SIZE..MAX_SIZE) { "text size out of range" }
        require(strokeWidth in 0f..0.3f) { "stroke out of range" }
        require(shadow in 0f..1f) { "shadow out of range" }
        require(listOf(color, strokeColor, backgroundColor).all { it in 0L..0xFFFFFFFFL }) { "colour out of range" }
    }

    companion object {
        const val MIN_SIZE = 0.02f
        const val MAX_SIZE = 0.3f
        val TITLE = TextStyle()
        val SUBTITLE = TextStyle(size = 0.045f, bold = false, shadow = 0.3f, backgroundColor = 0x99000000)
    }
}

/** A title or subtitle. [x]/[y] are the centre of the text block as fractions of the frame. */
data class TextItem(
    val id: String,
    val text: String,
    val startUs: Long,
    val durationUs: Long,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val style: TextStyle = TextStyle.TITLE,
    val animation: TextAnimation = TextAnimation.NONE,
    /** Clockwise degrees. */
    val rotation: Float = 0f,
    val opacity: Float = 1f,
) {
    init {
        require(rotation in -180f..180f) { "text rotation out of range" }
        require(opacity in 0f..1f) { "text opacity out of range" }
        require(startUs >= 0) { "startUs must be >= 0" }
        require(durationUs >= MIN_US) { "text too short" }
        require(x in 0f..1f && y in 0f..1f) { "text position out of range" }
    }

    val endUs: Long get() = startUs + durationUs

    fun containsUs(timeUs: Long): Boolean = timeUs >= startUs && timeUs < endUs

    companion object {
        const val MIN_US = 100_000L
        const val MAX_LENGTH = 500
        const val SUBTITLE_Y = 0.86f
    }
}

/**
 * A track of clips (or of text items for TEXT/SUBTITLE tracks). A magnetic track keeps its clips
 * contiguous starting at 0: positions are derived from order, so deleting a clip ripples the
 * following clips left. Free (non-magnetic) tracks keep explicit positions.
 */
data class Track(
    val id: String,
    val kind: TrackKind,
    val clips: List<Clip>,
    val magnetic: Boolean,
    val texts: List<TextItem> = emptyList(),
    /** Disabled audio tracks are silent; disabled text tracks are hidden. */
    val enabled: Boolean = true,
    /** Locked tracks reject every edit. */
    val locked: Boolean = false,
) {
    val endUs: Long get() = maxOf(clips.maxOfOrNull { it.timelineEndUs } ?: 0L, texts.maxOfOrNull { it.endUs } ?: 0L)

    val isTextual: Boolean get() = kind == TrackKind.TEXT || kind == TrackKind.SUBTITLE

    fun indexOf(clipId: String): Int = clips.indexOfFirst { it.id == clipId }

    fun clip(clipId: String): Clip? = clips.firstOrNull { it.id == clipId }

    fun text(textId: String): TextItem? = texts.firstOrNull { it.id == textId }

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

    /** Re-derives contiguous positions for magnetic tracks; sorts free tracks by start time. */
    fun normalized(): Track {
        if (!magnetic) return copy(clips = clips.sortedBy { it.timelineStartUs }, texts = texts.sortedBy { it.startUs })
        var cursor = 0L
        val laidOut = clips.map { clip ->
            val placed = if (clip.timelineStartUs == cursor) clip else clip.copy(timelineStartUs = cursor)
            cursor += clip.durationUs
            placed
        }
        return copy(clips = laidOut)
    }

    /** True when [candidate] would overlap another clip of this (free) track. */
    fun overlaps(candidate: Clip): Boolean = clips.any {
        it.id != candidate.id && candidate.timelineStartUs < it.timelineEndUs && it.timelineStartUs < candidate.timelineEndUs
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

/**
 * Output frame shape. [aspect] is a preset; a custom size (both sides set) overrides its ratio.
 */
data class Canvas(val aspect: AspectRatio, val frameRate: Int = 30, val customWidth: Int = 0, val customHeight: Int = 0) {
    init {
        require(frameRate in 1..120) { "frameRate out of range" }
        require((customWidth == 0 && customHeight == 0) || (customWidth in MIN_SIDE..MAX_SIDE && customHeight in MIN_SIDE..MAX_SIDE)) {
            "custom size out of range"
        }
    }

    val isCustom: Boolean get() = customWidth > 0
    val ratio: Float get() = if (isCustom) customWidth.toFloat() / customHeight else aspect.ratio
    val label: String get() = if (isCustom) "${customWidth}×$customHeight" else aspect.label

    companion object {
        const val MIN_SIDE = 144
        const val MAX_SIDE = 4096
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
    /** Account that owns the project; empty for projects made without signing in. */
    val ownerId: String = "",
) {
    val mainTrack: Track
        get() = tracks.firstOrNull { it.kind == TrackKind.MAIN_VIDEO }
            ?: error("Project has no main video track")

    /** Programme length: the main video track defines it; other tracks are cut to fit. */
    val durationUs: Long get() = mainTrack.endUs

    val clipCount: Int get() = tracks.sumOf { it.clips.size }

    val audioTracks: List<Track> get() = tracks.filter { it.kind == TrackKind.AUDIO }

    fun track(trackId: String): Track? = tracks.firstOrNull { it.id == trackId }

    fun findText(textId: String): Pair<Track, TextItem>? {
        for (track in tracks) {
            val item = track.text(textId)
            if (item != null) return track to item
        }
        return null
    }

    /** Text items visible at [timeUs] on enabled tracks, titles first then subtitles. */
    fun textsAt(timeUs: Long): List<TextItem> =
        tracks.filter { it.isTextual && it.enabled }.flatMap { t -> t.texts.filter { it.containsUs(timeUs) } }

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
        const val MUSIC_TRACK_ID = "track-music"
        const val VOICE_TRACK_ID = "track-voice"
        const val TEXT_TRACK_ID = "track-text"
        const val SUBTITLE_TRACK_ID = "track-subtitle"

        /** Free tracks are created on first use; this is their fixed stacking order. */
        val FREE_TRACKS: List<Pair<String, TrackKind>> = listOf(
            TEXT_TRACK_ID to TrackKind.TEXT,
            SUBTITLE_TRACK_ID to TrackKind.SUBTITLE,
            MUSIC_TRACK_ID to TrackKind.AUDIO,
            VOICE_TRACK_ID to TrackKind.AUDIO,
        )

        fun create(id: String, name: String, canvas: Canvas, nowMs: Long, ownerId: String = ""): Project = Project(
            id = id,
            name = name,
            createdAtMs = nowMs,
            updatedAtMs = nowMs,
            canvas = canvas,
            assets = emptyMap(),
            tracks = listOf(Track(MAIN_TRACK_ID, TrackKind.MAIN_VIDEO, emptyList(), magnetic = true)),
            ownerId = ownerId,
        )
    }
}
