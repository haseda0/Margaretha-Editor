package id.haseda.margaretha.media

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import id.haseda.margaretha.timeline.Motion
import id.haseda.margaretha.timeline.US_PER_SECOND
import java.nio.ByteBuffer

@OptIn(UnstableApi::class)
object AudioFx {
    /**
     * Gain stage: mono/stereo keep their layout (identity matrix scaled by the clip volume);
     * 3–6 channel sources are downmixed to stereo at constant power, then scaled.
     */
    fun volume(volume: Float): AudioProcessor = ChannelMixingAudioProcessor().apply {
        for (channels in 1..2) putChannelMixingMatrix(ChannelMixingMatrix.createForConstantGain(channels, channels).scaleBy(volume))
        for (channels in 3..6) putChannelMixingMatrix(ChannelMixingMatrix.createForConstantPower(channels, 2).scaleBy(volume))
    }

    /** Volume plus fade in/out for something [durationUs] long; empty when nothing needs changing. */
    fun chain(volume: Float, durationUs: Long, fadeInUs: Long, fadeOutUs: Long): List<AudioProcessor> = buildList {
        if (volume < 1f) add(volume(volume))
        if (fadeInUs > 0 || fadeOutUs > 0) add(FadeAudioProcessor(durationUs, fadeInUs, fadeOutUs))
    }

    fun constantSpeed(speed: Float): SpeedProvider = object : SpeedProvider {
        override fun getSpeed(timeUs: Long): Float = speed
        override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
    }
}

/**
 * Sample-accurate fade in/out. Time is counted from the samples that pass through, so it stays
 * correct whatever timestamps the source file carries. 16-bit and float PCM are handled; any
 * other encoding is passed through untouched rather than failing the export.
 */
@OptIn(UnstableApi::class)
class FadeAudioProcessor(private val durationUs: Long, private val fadeInUs: Long, private val fadeOutUs: Long) : BaseAudioProcessor() {
    private var framesDone = 0L

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat =
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT || inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) inputAudioFormat
        else AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        val format = inputAudioFormat
        val frames = inputBuffer.remaining() / format.bytesPerFrame
        if (frames == 0) return
        val out = replaceOutputBuffer(frames * format.bytesPerFrame)
        val float = format.encoding == C.ENCODING_PCM_FLOAT
        repeat(frames) {
            val gain = Motion.fadeGain(framesDone * US_PER_SECOND / format.sampleRate, durationUs, fadeInUs, fadeOutUs)
            repeat(format.channelCount) {
                if (float) out.putFloat(inputBuffer.float * gain) else out.putShort((inputBuffer.short * gain).toInt().toShort())
            }
            framesDone++
        }
        inputBuffer.position(inputBuffer.limit())
        out.flip()
    }

    @Deprecated("Media3 routes the newer flush callback through this one.")
    override fun onFlush() {
        framesDone = 0L
    }

    override fun onReset() {
        framesDone = 0L
    }
}
