package id.haseda.margaretha.media

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import id.haseda.margaretha.timeline.MediaKind
import id.haseda.margaretha.timeline.RenderSegment
import id.haseda.margaretha.timeline.SubtitleCue
import id.haseda.margaretha.timeline.US_PER_MS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

/** Native whisper.cpp bridge (see app/src/main/cpp). */
object WhisperNative {
    fun interface ProgressListener {
        fun onProgress(percent: Int)
    }

    init {
        System.loadLibrary("margaretha_whisper")
    }

    @JvmStatic external fun nativeInit(modelPath: String): Long
    @JvmStatic external fun nativeFree(ctx: Long)
    @JvmStatic external fun nativeAbort()
    @JvmStatic external fun nativeTranscribe(ctx: Long, pcm: FloatArray, language: String, threads: Int, listener: ProgressListener?): ByteArray?
}

/** Speech-to-subtitles, fully on the device. Language: "id", "en" or "auto". */
class AutoSubtitles(private val context: Context) {

    /** Transcribes the sound of every video clip on the main track; times are on the project timeline. */
    suspend fun transcribe(segments: List<RenderSegment>, language: String, onProgress: (Float) -> Unit): List<SubtitleCue> =
        withContext(Dispatchers.Default) {
            val speaking = segments.filter { it.kind == MediaKind.VIDEO && it.hasAudio && it.volume > 0f }
            require(speaking.isNotEmpty()) { "Tidak ada klip video bersuara di timeline." }
            val ctx = WhisperNative.nativeInit(modelFile().absolutePath)
            check(ctx != 0L) { "Model subtitle gagal dimuat." }
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
            val cues = mutableListOf<SubtitleCue>()
            try {
                speaking.forEachIndexed { i, seg ->
                    coroutineContext.ensureActive()
                    val pcm = decodeMono16k(Uri.parse(seg.uri), seg.startMs, seg.endMs)
                    if (pcm.size < SAMPLE_RATE / 2) return@forEachIndexed
                    val base = i.toFloat() / speaking.size
                    val bytes = WhisperNative.nativeTranscribe(ctx, pcm, language, threads) { p ->
                        onProgress(base + p / 100f / speaking.size)
                    } ?: run {
                        coroutineContext.ensureActive()
                        error("Pengenalan suara gagal.")
                    }
                    String(bytes, Charsets.UTF_8).lineSequence().forEach { line ->
                        val parts = line.split('\t', limit = 3)
                        if (parts.size < 3) return@forEach
                        val text = parts[2].trim()
                        if (text.isEmpty() || text.startsWith("[") || text.startsWith("(")) return@forEach
                        // whisper time is centiseconds inside the source range; map through clip speed.
                        val s = seg.timelineStartUs + parts[0].toLong() * 10_000L * 1000L / seg.speedPermille
                        val e = seg.timelineStartUs + parts[1].toLong() * 10_000L * 1000L / seg.speedPermille
                        val end = minOf(e, seg.timelineStartUs + seg.durationUs)
                        if (end > s) cues += SubtitleCue(s, end, text)
                    }
                }
            } finally {
                WhisperNative.nativeFree(ctx)
            }
            onProgress(1f)
            cues
        }

    fun cancel() = WhisperNative.nativeAbort()

    /** The bundled model is copied out of the APK once, because whisper.cpp reads it from a file path. */
    private fun modelFile(): File {
        val out = File(context.filesDir, "whisper/$MODEL")
        if (out.exists() && out.length() > 0) return out
        out.parentFile?.mkdirs()
        val tmp = File(out.parentFile, "$MODEL.tmp")
        context.assets.open("whisper/$MODEL").use { input -> tmp.outputStream().use { input.copyTo(it, 1 shl 16) } }
        tmp.renameTo(out)
        return out
    }

    /** Decodes [startMs, endMs) of the first audio track to 16 kHz mono float PCM. */
    private suspend fun decodeMono16k(uri: Uri, startMs: Long, endMs: Long): FloatArray {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)
        val track = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: run { extractor.release(); return FloatArray(0) }
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()
        extractor.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var floatPcm = false
        val startUs = startMs * US_PER_MS
        val endUs = endMs * US_PER_MS
        val mono = FloatList((endMs - startMs).coerceAtLeast(0L).toInt() * 16 + 1024)
        // Box-filter straight down to 16 kHz while decoding, so long clips never hold full-rate audio.
        var inCount = 0L
        var acc = 0f
        var accN = 0
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        try {
            while (!outputDone) {
                coroutineContext.ensureActive()
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buf = codec.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0 || extractor.sampleTime > endUs) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    }
                    outIndex >= 0 -> {
                        val buf = codec.getOutputBuffer(outIndex)!!.order(ByteOrder.nativeOrder())
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        val frames = info.size / ((if (floatPcm) 4 else 2) * channels)
                        val frameUs = 1_000_000L / rate
                        for (fIdx in 0 until frames) {
                            var sum = 0f
                            for (c in 0 until channels) sum += if (floatPcm) buf.float else buf.short / 32768f
                            val t = info.presentationTimeUs + fIdx * frameUs
                            if (t in startUs until endUs) {
                                val outIdx = inCount * SAMPLE_RATE / rate
                                if (outIdx > mono.size && accN > 0) {
                                    val v = acc / accN
                                    while (mono.size < outIdx) mono.add(v)
                                    acc = 0f
                                    accN = 0
                                }
                                acc += sum / channels
                                accN++
                                inCount++
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            extractor.release()
        }
        if (accN > 0) mono.add(acc / accN)
        return mono.toArray()
    }

    private class FloatList(capacity: Int) {
        private var data = FloatArray(capacity.coerceAtLeast(16))
        var size = 0
            private set
        fun add(v: Float) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = v
        }
        fun toArray(): FloatArray = data.copyOf(size)
    }

    companion object {
        const val MODEL = "ggml-base-q5_1.bin"
        const val SAMPLE_RATE = 16_000
    }
}
