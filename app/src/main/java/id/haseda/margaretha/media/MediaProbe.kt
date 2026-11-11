package id.haseda.margaretha.media

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import id.haseda.margaretha.timeline.MediaAsset
import id.haseda.margaretha.timeline.MediaKind
import id.haseda.margaretha.timeline.TimelineEngine
import id.haseda.margaretha.timeline.US_PER_MS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

sealed interface ProbeResult {
    data class Ok(val asset: MediaAsset) : ProbeResult
    data class Failed(val name: String, val reason: String) : ProbeResult
}

/** Reads real metadata from picked video, image or audio and checks that this device can decode it. */
class MediaProbe(private val context: Context) {
    private val resolver: ContentResolver = context.contentResolver

    suspend fun probe(uri: Uri, assetId: String = UUID.randomUUID().toString()): ProbeResult = withContext(Dispatchers.IO) {
        val name = displayName(uri) ?: "Media"
        val type = mimeOf(uri)
        if (type?.startsWith("image/") == true) return@withContext probeImage(uri, assetId, name, type)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            fun meta(key: Int): String? = retriever.extractMetadata(key)
            val hasVideo = meta(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
            val durationMs = meta(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            if (durationMs == null || durationMs <= 0) {
                return@withContext ProbeResult.Failed(name, "Durasi tidak terbaca — file mungkin rusak atau formatnya tidak didukung.")
            }
            if (!hasVideo) {
                if (meta(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != "yes") {
                    return@withContext ProbeResult.Failed(name, "Tidak ada trek video maupun audio.")
                }
                return@withContext ProbeResult.Ok(
                    MediaAsset(
                        id = assetId, uri = uri.toString(), displayName = name,
                        mimeType = type ?: meta(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "audio/*",
                        durationUs = durationMs * US_PER_MS, width = 0, height = 0, rotationDegrees = 0,
                        hasVideo = false, hasAudio = true, kind = MediaKind.AUDIO,
                    ),
                )
            }
            val width = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            if (width <= 0 || height <= 0) return@withContext ProbeResult.Failed(name, "Ukuran video tidak terbaca.")
            val rotation = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val hasAudio = meta(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            val frameRate = if (Build.VERSION.SDK_INT >= 28) {
                val frames = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toLongOrNull()
                if (frames != null && frames > 0) frames * 1000f / durationMs else 0f
            } else 0f

            decoderProblem(uri)?.let { return@withContext ProbeResult.Failed(name, it) }

            val mime = type ?: meta(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "video/*"
            ProbeResult.Ok(
                MediaAsset(
                    id = assetId,
                    uri = uri.toString(),
                    displayName = name,
                    mimeType = mime,
                    durationUs = durationMs * US_PER_MS,
                    width = width,
                    height = height,
                    rotationDegrees = ((rotation % 360) + 360) % 360,
                    hasVideo = true,
                    hasAudio = hasAudio,
                    frameRate = frameRate,
                ),
            )
        } catch (e: SecurityException) {
            ProbeResult.Failed(name, "Izin membaca file ditolak.")
        } catch (e: Exception) {
            ProbeResult.Failed(name, "File tidak bisa dibaca (${e.javaClass.simpleName}).")
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun mimeOf(uri: Uri): String? = resolver.getType(uri)
        ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(uri.toString()).lowercase())

    /** Reads the real pixel size of a still image (EXIF rotation applied) without decoding it. */
    private fun probeImage(uri: Uri, assetId: String, name: String, type: String): ProbeResult = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            ProbeResult.Failed(name, "Gambar tidak bisa dibaca atau formatnya tidak didukung perangkat ini.")
        } else {
            val orientation = resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
            val sideways = orientation in setOf(
                ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_ROTATE_270,
                ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_TRANSVERSE,
            )
            ProbeResult.Ok(
                MediaAsset(
                    id = assetId, uri = uri.toString(), displayName = name, mimeType = type,
                    durationUs = TimelineEngine.IMAGE_ASSET_US,
                    width = if (sideways) bounds.outHeight else bounds.outWidth,
                    height = if (sideways) bounds.outWidth else bounds.outHeight,
                    rotationDegrees = 0, hasVideo = true, hasAudio = false, kind = MediaKind.IMAGE,
                ),
            )
        }
    } catch (e: SecurityException) {
        ProbeResult.Failed(name, "Izin membaca file ditolak.")
    } catch (e: Exception) {
        ProbeResult.Failed(name, "File tidak bisa dibaca (${e.javaClass.simpleName}).")
    }

    /** Returns a reason when no decoder on this device can handle the video track. */
    private fun decoderProblem(uri: Uri): String? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            val format = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
                .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
                ?: return "Tidak ada trek video."
            val mime = format.getString(MediaFormat.KEY_MIME)
            // Frame rate keys can make findDecoderForFormat overly strict on some builds.
            if (Build.VERSION.SDK_INT >= 29) format.removeKey(MediaFormat.KEY_FRAME_RATE)
            val decoder = MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(format)
            if (decoder == null) "Codec video ($mime) tidak didukung perangkat ini." else null
        } catch (e: Exception) {
            null // Extractor failures are not conclusive; the player will report real errors.
        } finally {
            extractor.release()
        }
    }

    fun displayName(uri: Uri): String? = runCatching {
        if (uri.scheme == ContentResolver.SCHEME_FILE) return@runCatching uri.lastPathSegment
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    /** True when the URI can still be opened (detects moved/deleted sources). */
    suspend fun isReachable(uri: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { resolver.openFileDescriptor(Uri.parse(uri), "r")?.use { true } ?: false }.getOrDefault(false)
    }

    /**
     * Keeps read access across restarts. Photo Picker / SAF grants are persisted; if the provider
     * refuses, the video is copied into private app storage so the project keeps working.
     */
    suspend fun makeDurable(uri: Uri, assetId: String): Uri = withContext(Dispatchers.IO) {
        if (uri.scheme == ContentResolver.SCHEME_FILE) return@withContext uri
        val persisted = runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.isSuccess
        if (persisted) return@withContext uri
        val dir = File(context.filesDir, "media").apply { mkdirs() }
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(resolver.getType(uri)) ?: "mp4"
        val target = File(dir, "$assetId.$ext")
        resolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
            ?: return@withContext uri
        Uri.fromFile(target)
    }
}
