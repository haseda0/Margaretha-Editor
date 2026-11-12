package id.haseda.margaretha.data

import android.content.Context

/** Where finished videos go. */
enum class ExportDestination { GALLERY, FOLDER }

/** Small, durable user preferences. Project data never lives here. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Frame rate given to newly created projects. */
    var defaultFrameRate: Int
        get() = prefs.getInt(KEY_FPS, 30).takeIf { it in FRAME_RATES } ?: 30
        set(value) = prefs.edit().putInt(KEY_FPS, value).apply()

    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING, value).apply()

    /** Tree URI of the folder the user picked for exports and backups; null when none. */
    var storageTreeUri: String?
        get() = prefs.getString(KEY_TREE, null)
        set(value) = prefs.edit().putString(KEY_TREE, value).apply()

    var exportDestination: ExportDestination
        get() = runCatching { ExportDestination.valueOf(prefs.getString(KEY_DESTINATION, null).orEmpty()) }.getOrDefault(ExportDestination.GALLERY)
        set(value) = prefs.edit().putString(KEY_DESTINATION, value.name).apply()

    /** Copy every project file into the user folder whenever the editor is left. */
    var autoBackup: Boolean
        get() = prefs.getBoolean(KEY_AUTO_BACKUP, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_BACKUP, value).apply()

    companion object {
        val FRAME_RATES = listOf(24, 25, 30, 60)
        private const val KEY_FPS = "default_frame_rate"
        private const val KEY_ONBOARDING = "onboarding_done"
        private const val KEY_TREE = "storage_tree_uri"
        private const val KEY_DESTINATION = "export_destination"
        private const val KEY_AUTO_BACKUP = "auto_backup"
    }
}
