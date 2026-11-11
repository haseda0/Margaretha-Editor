package id.haseda.margaretha.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Records a voice-over to an AAC file in app storage. The caller must hold RECORD_AUDIO.
 * A recording that is too short or failed leaves no file behind.
 */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAtMs = 0L

    val isRecording: Boolean get() = recorder != null

    /** Milliseconds recorded so far. */
    val elapsedMs: Long get() = if (isRecording) System.currentTimeMillis() - startedAtMs else 0L

    /** Starts recording; returns a readable reason when the microphone cannot be used. */
    fun start(): String? {
        if (isRecording) return null
        val dir = File(context.filesDir, "media").apply { mkdirs() }
        val target = File(dir, "suara-${System.currentTimeMillis()}.m4a")
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(48_000)
            r.setAudioEncodingBitRate(128_000)
            r.setOutputFile(target.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            file = target
            startedAtMs = System.currentTimeMillis()
            null
        } catch (e: Exception) {
            runCatching { r.release() }
            target.delete()
            "Mikrofon tidak bisa dipakai (${e.javaClass.simpleName}). Mungkin sedang digunakan aplikasi lain."
        }
    }

    /** Stops and returns the recorded file, or null when nothing usable was captured. */
    fun stop(): File? {
        val r = recorder ?: return null
        val target = file
        val longEnough = elapsedMs >= MIN_MS
        recorder = null
        file = null
        // stop() throws when no audio data was received; the file is unusable then.
        val ok = runCatching { r.stop() }.isSuccess
        runCatching { r.release() }
        return if (ok && longEnough && target != null && target.length() > 0) target else null.also { target?.delete() }
    }

    fun cancel() {
        stop()?.delete()
    }

    private companion object {
        const val MIN_MS = 400L
    }
}
