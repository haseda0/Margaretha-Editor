package id.haseda.margaretha.data

import android.content.Context

/** Small, durable user preferences. Project data never lives here. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Frame rate given to newly created projects. */
    var defaultFrameRate: Int
        get() = prefs.getInt(KEY_FPS, 30).takeIf { it in FRAME_RATES } ?: 30
        set(value) = prefs.edit().putInt(KEY_FPS, value).apply()

    companion object {
        val FRAME_RATES = listOf(24, 25, 30, 60)
        private const val KEY_FPS = "default_frame_rate"
    }
}
