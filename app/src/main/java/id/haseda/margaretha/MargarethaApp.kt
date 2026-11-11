package id.haseda.margaretha

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import id.haseda.margaretha.data.ProjectRepository
import id.haseda.margaretha.media.MediaProbe
import id.haseda.margaretha.media.ThumbnailCache

/** Manual dependency container — the graph is small enough that a DI framework is not justified. */
class AppContainer(context: Context) {
    val repository = ProjectRepository(context)
    val probe = MediaProbe(context)
    val thumbnails = ThumbnailCache(context)
}

class MargarethaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.repository.cleanupStaleTemps()
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) container.thumbnails.trim()
    }
}
