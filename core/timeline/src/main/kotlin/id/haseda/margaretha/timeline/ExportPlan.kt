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
) {
    val durationUs: Long get() = (endMs - startMs) * US_PER_MS
}

object RenderSegments {
    /** Millisecond rounding is shared by preview and export: adjacent split pieces stay gapless. */
    fun of(project: Project): List<RenderSegment> = project.mainTrack.clips.mapNotNull { clip ->
        val asset = project.assets[clip.assetId] ?: return@mapNotNull null
        val start = clip.sourceInUs / US_PER_MS
        val end = clip.sourceOutUs / US_PER_MS
        if (end <= start) null else RenderSegment(clip.id, asset.uri, start, end, clip.effectiveVolume, clip.colorLook)
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
        val segments = RenderSegments.of(project)
        val size = outputSize(project.canvas.aspect, settings.resolution)
        val vb = videoBitrate(settings, size)
        val duration = segments.sumOf { it.durationUs }
        return PlanOutcome.Ready(
            ExportPlan(
                segments = segments,
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
