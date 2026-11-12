package id.haseda.margaretha.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Real audio waveforms for the timeline: the file's audio track is decoded once in the background
 * and reduced to one peak per [BUCKET_US]. Files that cannot be decoded simply have no waveform.
 */
class WaveformCache(private val context: Context) {
    private val peaks = ConcurrentHashMap<String, FloatArray>()
    private val failed = ConcurrentHashMap.newKeySet<String>()
    private val slot = Semaphore(1)

    fun peek(uri: String): FloatArray? = peaks[uri]

    suspend fun get(uri: String): FloatArray? {
        peaks[uri]?.let { return it }
        if (uri in failed) return null
        return slot.withPermit {
            peaks[uri] ?: withContext(Dispatchers.IO) { runCatching { decode(uri) }.getOrNull() }
                ?.also { peaks[uri] = it }
                ?: null.also { failed += uri }
        }
    }

    fun trim() = peaks.clear()

    private fun decode(uri: String): FloatArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, Uri.parse(uri), null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else return null
            if (durationUs <= 0 || durationUs > MAX_DURATION_US) return null
            extractor.selectTrack(track)
            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }
            val result = FloatArray((durationUs / BUCKET_US).toInt() + 1)
            val info = MediaCodec.BufferInfo()
            val deadline = SystemClock.elapsedRealtime() + MAX_DECODE_MS
            var inputDone = false
            while (SystemClock.elapsedRealtime() < deadline) {
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (index >= 0) {
                        val size = codec.getInputBuffer(index)?.let { extractor.readSampleData(it, 0) } ?: -1
                        if (size < 0) {
                            codec.queueInputBuffer(index, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val out = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                if (out >= 0) {
                    val buffer = codec.getOutputBuffer(out)
                    if (buffer != null && info.size > 0) {
                        buffer.position(info.offset).limit(info.offset + info.size)
                        // Decoders emit 16-bit PCM by default; sampling every 16th value is plenty for a peak.
                        val samples = buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
                        var max = 0
                        var i = 0
                        while (i < samples.limit()) {
                            val v = abs(samples.get(i).toInt())
                            if (v > max) max = v
                            i += 16
                        }
                        val bucket = (info.presentationTimeUs / BUCKET_US).toInt().coerceIn(0, result.lastIndex)
                        result[bucket] = maxOf(result[bucket], max / 32768f)
                    }
                    codec.releaseOutputBuffer(out, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return result
                }
            }
            // Ran out of time on a very long file: an incomplete waveform would mislead, so show none.
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    companion object {
        const val BUCKET_US = 50_000L
        private const val TIMEOUT_US = 10_000L
        private const val MAX_DURATION_US = 30L * 60 * 1_000_000
        private const val MAX_DECODE_MS = 20_000L
    }
}
