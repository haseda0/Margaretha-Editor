package id.haseda.margaretha.timeline

import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * One rendered piece of the main track. Preview (ExoPlayer clipping) and export (Transformer
 * clipping) are both built from these segments, so they use the exact same in/out points.
 */
data class RenderSegment(
    val clipId: String,
    val uri: String,
    val startMs: Long,
    val endMs: Long,
    val volume: Float,
    val colorLook: ColorLook = ColorLook.ORIGINAL,
    val kind: MediaKind = MediaKind.VIDEO,
    val hasAudio: Boolean = true,
    val speedPermille: Int = 1000,
    /** Start on the rendered timeline (sum of the previous segments' rendered durations). */
    val timelineStartUs: Long = 0L,
    val audioFadeInUs: Long = 0L,
    val audioFadeOutUs: Long = 0L,
    val transform: Transform = Transform.IDENTITY,
    val crop: Crop = Crop.NONE,
    val grade: ColorGrade = ColorGrade.NEUTRAL,
    val fx: ClipFx = ClipFx.NONE,
    val enter: Transition = Transition.NONE,
    val exit: Transition = Transition.NONE,
    /** Needed by players to recognise still images behind extension-less content URIs. */
    val mimeType: String = "",
) {
    /** Rendered duration, after the speed change. */
    val durationUs: Long get() = (endMs - startMs) * US_PER_MS * 1000L / speedPermille
    val timelineEndUs: Long get() = timelineStartUs + durationUs
    val speed: Float get() = speedPermille / 1000f

    /** True when the picture needs no GPU work beyond the colour look. */
    val hasVisualEdits: Boolean
        get() = !transform.isIdentity || !crop.isNone || !grade.isNeutral || !fx.isNone || !enter.isNone || !exit.isNone
}

/**
 * Which GPU stages a clip needs. Values inside a stage (how much blur, which grade) can change
 * without rebuilding the pipeline; only a different structure requires a rebuild.
 */
data class EffectStructure(
    val crop: Crop,
    val look: ColorLook,
    val grade: Boolean,
    val blur: Boolean,
    val matrix: Boolean,
) {
    /** True when the picture can go straight from the decoder to the screen/encoder. */
    val isEmpty: Boolean get() = crop.isNone && look == ColorLook.ORIGINAL && !grade && !blur && !matrix

    companion object {
        fun of(s: RenderSegment): EffectStructure {
            val kinds = setOf(s.enter.kind, s.exit.kind) - TransitionKind.NONE
            return EffectStructure(
                crop = s.crop,
                look = s.colorLook,
                // Fade, wipe, zoom and blur transitions darken/reveal the picture in the grade stage.
                grade = !s.grade.isNeutral || s.fx.sharpen > 0f || s.fx.vignette > 0f || s.fx.grain > 0f ||
                    s.transform.opacity < 1f || kinds.any { it != TransitionKind.SLIDE },
                blur = s.fx.blur > 0f || TransitionKind.BLUR in kinds,
                matrix = s.transform.copy(opacity = 1f) != Transform.IDENTITY ||
                    TransitionKind.SLIDE in kinds || TransitionKind.ZOOM in kinds,
            )
        }
    }
}

/** A piece of sound on an audio track, already cut to the programme length. */
data class AudioSegment(
    val clipId: String,
    val uri: String,
    val startMs: Long,
    val endMs: Long,
    val timelineStartUs: Long,
    val volume: Float,
    val fadeInUs: Long = 0L,
    val fadeOutUs: Long = 0L,
) {
    val durationUs: Long get() = (endMs - startMs) * US_PER_MS
    val timelineEndUs: Long get() = timelineStartUs + durationUs
}

/** Everything the preview and the exporter render, derived from one [Project] snapshot. */
data class RenderPlan(
    val video: List<RenderSegment>,
    val audioTracks: List<List<AudioSegment>>,
    val texts: List<TextItem>,
    val durationUs: Long,
) {
    fun textsAt(timeUs: Long): List<TextItem> = texts.filter { it.containsUs(timeUs) }

    /**
     * Texts grouped into layers in which no two items are on screen at once (greedy interval
     * colouring). The exporter draws one overlay per layer, however many cues a layer holds.
     */
    fun textLayers(): List<List<TextItem>> {
        val layers = mutableListOf<MutableList<TextItem>>()
        texts.sortedBy { it.startUs }.forEach { item ->
            val free = layers.firstOrNull { it.last().endUs <= item.startUs }
            if (free != null) free += item else layers += mutableListOf(item)
        }
        return layers
    }
}

object RenderSegments {
    /** Millisecond rounding is shared by preview and export: adjacent split pieces stay gapless. */
    fun of(project: Project): List<RenderSegment> {
        var cursor = 0L
        return project.mainTrack.clips.mapNotNull { clip ->
            val asset = project.assets[clip.assetId] ?: return@mapNotNull null
            val start = clip.sourceInUs / US_PER_MS
            val end = clip.sourceOutUs / US_PER_MS
            if (end <= start) return@mapNotNull null
            RenderSegment(
                clipId = clip.id,
                uri = asset.uri,
                startMs = start,
                endMs = end,
                volume = if (asset.hasAudio) clip.effectiveVolume else 0f,
                colorLook = clip.colorLook,
                kind = asset.kind,
                hasAudio = asset.hasAudio,
                speedPermille = clip.speedPermille,
                timelineStartUs = cursor,
                audioFadeInUs = clip.audioFadeInUs,
                audioFadeOutUs = clip.audioFadeOutUs,
                transform = clip.transform,
                crop = clip.crop,
                grade = clip.grade,
                fx = clip.fx,
                enter = clip.enter,
                exit = clip.exit,
                mimeType = asset.mimeType,
            ).also { cursor += it.durationUs }
        }
    }

    fun plan(project: Project): RenderPlan {
        val video = of(project)
        val total = video.lastOrNull()?.timelineEndUs ?: 0L
        val audio = project.audioTracks.filter { it.enabled }.map { track ->
            track.clips.sortedBy { it.timelineStartUs }.mapNotNull { clip -> audioSegment(project, clip, total) }
        }.filter { it.isNotEmpty() }
        val texts = project.tracks.filter { it.isTextual && it.enabled }.flatMap { it.texts }
            .filter { it.startUs < total }
            .mapNotNull { item ->
                val duration = minOf(item.durationUs, total - item.startUs)
                if (duration < TextItem.MIN_US) null else if (duration == item.durationUs) item else item.copy(durationUs = duration)
            }
        return RenderPlan(video, audio, texts, total)
    }

    private fun audioSegment(project: Project, clip: Clip, totalUs: Long): AudioSegment? {
        val asset = project.assets[clip.assetId] ?: return null
        if (!asset.hasAudio || clip.effectiveVolume <= 0f || clip.timelineStartUs >= totalUs) return null
        val start = clip.sourceInUs / US_PER_MS
        var end = clip.sourceOutUs / US_PER_MS
        val overshootUs = clip.timelineStartUs + (end - start) * US_PER_MS - totalUs
        if (overshootUs > 0) end -= (overshootUs + US_PER_MS - 1) / US_PER_MS
        if (end - start < 20) return null
        return AudioSegment(clip.id, asset.uri, start, end, clip.timelineStartUs, clip.effectiveVolume, clip.audioFadeInUs, clip.audioFadeOutUs)
    }
}

/**
 * Time-based maths shared by the preview (drawn by the UI) and the export (drawn by GPU/audio
 * processors). Keeping it here means both pipelines animate identically and it is unit-tested.
 */
object Motion {
    /** Linear fade gain 0..1 at [offsetUs] inside something [durationUs] long. */
    fun fadeGain(offsetUs: Long, durationUs: Long, fadeInUs: Long, fadeOutUs: Long): Float {
        if (durationUs <= 0) return 1f
        // Fades never overlap: each is capped at half of the clip.
        val fin = fadeInUs.coerceIn(0L, durationUs / 2)
        val fout = fadeOutUs.coerceIn(0L, durationUs / 2)
        val t = offsetUs.coerceIn(0L, durationUs)
        var gain = 1f
        if (fin > 0 && t < fin) gain = t.toFloat() / fin
        if (fout > 0 && t > durationUs - fout) gain = minOf(gain, (durationUs - t).toFloat() / fout)
        return gain.coerceIn(0f, 1f)
    }

    data class TransitionState(val kind: TransitionKind, val visibility: Float, val entering: Boolean) {
        val isIdle: Boolean get() = kind == TransitionKind.NONE || visibility >= 1f
    }

    val IDLE = TransitionState(TransitionKind.NONE, 1f, true)

    /** Which transition is active at [offsetUs] of a segment and how visible the picture is (eased 0..1). */
    fun transition(segment: RenderSegment, offsetUs: Long): TransitionState {
        val d = segment.durationUs
        val t = offsetUs.coerceIn(0L, d)
        val enterUs = if (segment.enter.isNone) 0L else segment.enter.durationUs.coerceAtMost(d / 2)
        val exitUs = if (segment.exit.isNone) 0L else segment.exit.durationUs.coerceAtMost(d / 2)
        return when {
            enterUs > 0 && t < enterUs -> TransitionState(segment.enter.kind, ease(t.toFloat() / enterUs), entering = true)
            exitUs > 0 && t > d - exitUs -> TransitionState(segment.exit.kind, ease((d - t).toFloat() / exitUs), entering = false)
            else -> IDLE
        }
    }

    /** Smoothstep: gentle start and stop. */
    fun ease(x: Float): Float {
        val c = x.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    data class TextPose(val alpha: Float, val offsetY: Float, val scale: Float)

    private const val TEXT_ANIM_US = 300_000L

    /** Pose of an animated text at [timeUs]; offsetY is a fraction of the frame height (+ = down). */
    fun textPose(item: TextItem, timeUs: Long): TextPose {
        val anim = minOf(TEXT_ANIM_US, item.durationUs / 2)
        val t = timeUs - item.startUs
        val k = when {
            item.animation == TextAnimation.NONE || anim <= 0 -> 1f
            t < anim -> ease(t.toFloat() / anim)
            t > item.durationUs - anim -> ease((item.durationUs - t).toFloat() / anim)
            else -> 1f
        }
        return when (item.animation) {
            TextAnimation.NONE -> TextPose(1f, 0f, 1f)
            TextAnimation.FADE -> TextPose(k, 0f, 1f)
            TextAnimation.SLIDE_UP -> TextPose(k, (1f - k) * 0.05f, 1f)
            TextAnimation.POP -> TextPose(k, 0f, 0.7f + 0.3f * k)
        }
    }
}

enum class ResolutionPreset(val shortSide: Int, val label: String) {
    P720(720, "720p"),
    P1080(1080, "1080p"),
    P1440(1440, "1440p"),
    P2160(2160, "4K"),
}

enum class QualityPreset(val label: String, val bitsPerPixel: Double) {
    STANDARD("Standar", 0.08),
    HIGH("Kualitas Tinggi", 0.12),
    MAXIMUM("Maksimum", 0.18),
}

enum class VideoCodec(val mimeType: String, val label: String, val bitrateFactor: Double) {
    H264("video/avc", "H.264 (paling kompatibel)", 1.0),
    HEVC("video/hevc", "HEVC / H.265 (lebih kecil)", 0.65),
}

data class ExportSettings(
    val resolution: ResolutionPreset = ResolutionPreset.P1080,
    val frameRate: Int = 30,
    val quality: QualityPreset = QualityPreset.HIGH,
    val codec: VideoCodec = VideoCodec.H264,
    val audioBitrate: Int = 192_000,
    /** Overrides the quality preset when set (bits per second). */
    val customVideoBitrate: Int? = null,
) {
    companion object {
        val FRAME_RATES = listOf(24, 25, 30, 50, 60)
        val AUDIO_BITRATES = listOf(128_000, 192_000, 256_000, 320_000)
    }
}

data class OutputSize(val width: Int, val height: Int) {
    override fun toString(): String = "${width}×$height"
}

data class ExportPlan(
    val segments: List<RenderSegment>,
    val render: RenderPlan,
    val size: OutputSize,
    val frameRate: Int,
    val videoMimeType: String,
    val videoBitrate: Int,
    val audioBitrate: Int,
    val expectedDurationUs: Long,
    val estimatedBytes: Long,
)

sealed interface PlanOutcome {
    data class Ready(val plan: ExportPlan) : PlanOutcome
    data class Invalid(val reason: String) : PlanOutcome
}

object ExportPlanner {
    const val MIN_VIDEO_BITRATE = 1_000_000
    const val MAX_VIDEO_BITRATE = 100_000_000

    /** Output size for the canvas aspect at the requested short side, both sides even. */
    fun outputSize(aspect: AspectRatio, resolution: ResolutionPreset): OutputSize {
        val short = resolution.shortSide
        val longSide = even((short.toDouble() * maxOf(aspect.width, aspect.height) / minOf(aspect.width, aspect.height)).roundToInt())
        return if (aspect.width >= aspect.height) OutputSize(longSide, short) else OutputSize(short, longSide)
    }

    fun videoBitrate(settings: ExportSettings, size: OutputSize): Int {
        settings.customVideoBitrate?.let { return it.coerceIn(MIN_VIDEO_BITRATE, MAX_VIDEO_BITRATE) }
        val bps = size.width.toDouble() * size.height * settings.frameRate *
            settings.quality.bitsPerPixel * settings.codec.bitrateFactor
        return bps.roundToLong().coerceIn(MIN_VIDEO_BITRATE.toLong(), MAX_VIDEO_BITRATE.toLong()).toInt()
    }

    /** Estimate including ~2% MP4 container overhead. It is an estimate, not a promise. */
    fun estimateBytes(videoBitrate: Int, audioBitrate: Int, durationUs: Long): Long {
        val seconds = durationUs.toDouble() / US_PER_SECOND
        return ((videoBitrate.toLong() + audioBitrate) * seconds / 8.0 * 1.02).roundToLong()
    }

    fun plan(project: Project, settings: ExportSettings): PlanOutcome {
        if (settings.frameRate !in ExportSettings.FRAME_RATES) return PlanOutcome.Invalid("Frame rate tidak didukung.")
        val clips = project.mainTrack.clips
        if (clips.isEmpty()) return PlanOutcome.Invalid("Timeline masih kosong. Tambahkan video dulu.")
        val missing = clips.firstOrNull { project.assets[it.assetId] == null }
        if (missing != null) return PlanOutcome.Invalid("Ada klip yang media sumbernya hilang.")
        val render = RenderSegments.plan(project)
        val segments = render.video
        if (segments.isEmpty()) return PlanOutcome.Invalid("Tidak ada klip yang bisa dirender.")
        val size = outputSize(project.canvas.aspect, settings.resolution)
        val vb = videoBitrate(settings, size)
        val duration = segments.sumOf { it.durationUs }
        return PlanOutcome.Ready(
            ExportPlan(
                segments = segments,
                render = render,
                size = size,
                frameRate = settings.frameRate,
                videoMimeType = settings.codec.mimeType,
                videoBitrate = vb,
                audioBitrate = settings.audioBitrate,
                expectedDurationUs = duration,
                estimatedBytes = estimateBytes(vb, settings.audioBitrate, duration),
            ),
        )
    }

    /**
     * Final check on the rendered file: the measured duration must match the plan within
     * max(250 ms, 2%). Encoders round to frame boundaries, so exact equality is not expected.
     */
    fun durationMatches(expectedUs: Long, actualUs: Long): Boolean {
        if (expectedUs <= 0 || actualUs <= 0) return false
        val tolerance = maxOf(250_000L, expectedUs / 50)
        return kotlin.math.abs(expectedUs - actualUs) <= tolerance
    }

    private fun even(value: Int): Int = if (value % 2 == 0) value else value + 1
}

object Timecode {
    /** Formats as mm:ss.ff (frames) or h:mm:ss.ff for long timelines. */
    fun format(timeUs: Long, frameRate: Int = 30): String {
        val t = timeUs.coerceAtLeast(0L)
        val totalSeconds = t / US_PER_SECOND
        val frames = ((t % US_PER_SECOND) * frameRate / US_PER_SECOND).toInt()
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        val ff = frames.toString().padStart(2, '0')
        return if (h > 0) "$h:${pad(m)}:${pad(s)}.$ff" else "${pad(m)}:${pad(s)}.$ff"
    }

    /** Short human duration like 1:05 or 0:07. */
    fun short(timeUs: Long): String {
        val totalSeconds = (timeUs.coerceAtLeast(0L) + 500_000L) / US_PER_SECOND
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) "$h:${pad(m)}:${pad(s)}" else "$m:${pad(s)}"
    }

    fun bytes(value: Long): String = when {
        value >= 1_000_000_000L -> String.format(java.util.Locale.US, "%.2f GB", value / 1e9)
        value >= 1_000_000L -> String.format(java.util.Locale.US, "%.1f MB", value / 1e6)
        value >= 1_000L -> String.format(java.util.Locale.US, "%.0f KB", value / 1e3)
        else -> "$value B"
    }

    private fun pad(v: Long) = v.toString().padStart(2, '0')
}
