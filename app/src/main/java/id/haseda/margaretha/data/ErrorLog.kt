package id.haseda.margaretha.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Small on-device log of crashes and playback/export errors, so the user can copy and report them. */
object ErrorLog {
    private const val MAX_BYTES = 64 * 1024
    private var file: File? = null

    fun install(context: Context) {
        file = File(context.filesDir, "error-log.txt")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            record("CRASH [${thread.name}]", e)
            previous?.uncaughtException(thread, e)
        }
    }

    @Synchronized
    fun record(tag: String, e: Throwable? = null, message: String? = null) {
        val f = file ?: return
        runCatching {
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val body = buildString {
                append("== ").append(time).append(' ').append(tag).append('\n')
                message?.let { append(it).append('\n') }
                e?.let { append(it.stackTraceToString().lines().take(25).joinToString("\n")).append('\n') }
            }
            if (f.length() > MAX_BYTES) f.writeText(f.readText().takeLast(MAX_BYTES / 2))
            f.appendText(body)
        }
    }

    fun read(): String = runCatching { file?.takeIf { it.exists() }?.readText() }.getOrNull().orEmpty()

    fun clear() {
        runCatching { file?.delete() }
    }
}
