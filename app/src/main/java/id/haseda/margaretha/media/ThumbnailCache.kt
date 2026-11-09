package id.haseda.margaretha.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Asynchronous, memory-bounded thumbnail source for the timeline and project cards.
 * Frames are decoded at thumbnail size (never full resolution where the API allows it),
 * at most two at a time, and retrievers are pooled per source file.
 */
class ThumbnailCache(private val context: Context) {
    private val memoryKb = (Runtime.getRuntime().maxMemory() / 1024 / 12).toInt().coerceIn(8 * 1024, 48 * 1024)
    private val cache = object : LruCache<String, Bitmap>(memoryKb) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private val decodeSlots = Semaphore(2)
    private val poolLock = Mutex()
    private val pool = LinkedHashMap<String, PooledRetriever>(4, 0.75f, true)

    private class PooledRetriever(val retriever: MediaMetadataRetriever, val lock: Mutex = Mutex())

    /** Bucketing to 1/4 s lets neighbouring tiles share cached frames. */
    fun key(uri: String, timeUs: Long, heightPx: Int) = "$uri|${timeUs / 250_000}|$heightPx"

    fun peek(uri: String, timeUs: Long, heightPx: Int): Bitmap? = cache.get(key(uri, timeUs, heightPx))

    suspend fun get(uri: String, timeUs: Long, heightPx: Int): Bitmap? {
        val k = key(uri, timeUs, heightPx)
        cache.get(k)?.let { return it }
        return decodeSlots.withPermit {
            cache.get(k) ?: withContext(Dispatchers.IO) { decode(uri, (timeUs / 250_000) * 250_000 + 125_000, heightPx) }
                ?.also { cache.put(k, it) }
        }
    }

    private suspend fun decode(uri: String, timeUs: Long, heightPx: Int): Bitmap? {
        val pooled = retrieverFor(uri) ?: return null
        return pooled.lock.withLock {
            runCatching {
                val r = pooled.retriever
                if (Build.VERSION.SDK_INT >= 27) {
                    r.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, heightPx * 2, heightPx)
                } else {
                    r.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { full ->
                        val scale = heightPx.toFloat() / full.height
                        val w = (full.width * scale).roundToInt().coerceAtLeast(1)
                        Bitmap.createScaledBitmap(full, w, heightPx, true).also { if (it !== full) full.recycle() }
                    }
                }
            }.getOrNull()
        }
    }

    private suspend fun retrieverFor(uri: String): PooledRetriever? = poolLock.withLock {
        pool[uri]?.let { return@withLock it }
        val r = MediaMetadataRetriever()
        val ok = runCatching { withContext(Dispatchers.IO) { r.setDataSource(context, Uri.parse(uri)) } }.isSuccess
        if (!ok) {
            runCatching { r.release() }
            return@withLock null
        }
        val pooled = PooledRetriever(r)
        pool[uri] = pooled
        while (pool.size > MAX_RETRIEVERS) {
            val eldest = pool.entries.first()
            pool.remove(eldest.key)
            // Release under its own lock so an in-flight decode is not interrupted.
            eldest.value.lock.withLock { runCatching { eldest.value.retriever.release() } }
        }
        pooled
    }

    fun trim() = cache.evictAll()

    companion object {
        private const val MAX_RETRIEVERS = 4
    }
}
