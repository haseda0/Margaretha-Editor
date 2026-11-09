package id.haseda.margaretha.media

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodecList
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import id.haseda.margaretha.media.colorEffects
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import id.haseda.margaretha.timeline.ExportPlan
import id.haseda.margaretha.timeline.ExportPlanner
import id.haseda.margaretha.timeline.Timecode
import id.haseda.margaretha.timeline.US_PER_MS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

data class ExportOutput(
    val uri: Uri,
    val locationLabel: String,
    val sizeBytes: Long,
    val durationUs: Long,
    val width: Int,
    val height: Int,
)

sealed interface ExportState {
    data object Idle : ExportState
    data class Running(val progress: Int?, val startedAtMs: Long) : ExportState
    data object Verifying : ExportState
    data class Success(val output: ExportOutput, val elapsedMs: Long) : ExportState
    data class Failed(val message: String, val technical: String?) : ExportState
    data object Cancelled : ExportState
}

/**
 * Real export: Media3 Transformer renders the plan to a private temp file, the result is verified
 * (playable, expected duration, audio+video present), then published to the gallery. A failed or
 * cancelled export never leaves a partial file behind.
 */
@OptIn(UnstableApi::class)
class ExportEngine(private val context: Context, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<ExportState>(ExportState.Idle)
    val state: StateFlow<ExportState> = _state.asStateFlow()

    private var transformer: Transformer? = null
    private var progressJob: Job? = null
    private var tempFile: File? = null

    val isBusy: Boolean get() = _state.value is ExportState.Running || _state.value is ExportState.Verifying

    val locationLabel: String
        get() = if (Build.VERSION.SDK_INT >= 29) "Galeri › Movies/$FOLDER" else "Penyimpanan aplikasi › Movies (bisa dibagikan)"

    fun start(plan: ExportPlan, baseName: String) {
        if (isBusy) return
        cleanupTemp()
        val freeBytes = runCatching { StatFs(context.cacheDir.absolutePath).availableBytes }.getOrDefault(Long.MAX_VALUE)
        val needed = plan.estimatedBytes * 2 + 50_000_000L // temp file + published copy + margin
        if (freeBytes < needed) {
            _state.value = ExportState.Failed(
                "Penyimpanan tidak cukup. Butuh sekitar ${Timecode.bytes(needed)}, tersedia ${Timecode.bytes(freeBytes)}.",
                null,
            )
            return
        }

        val dir = File(context.cacheDir, "export").apply { mkdirs() }
        val out = File(dir, "render-${System.currentTimeMillis()}.mp4")
        tempFile = out
        val startedAt = System.currentTimeMillis()
        _state.value = ExportState.Running(null, startedAt)

        val composition = buildComposition(plan)
        val t = Transformer.Builder(context)
            .setVideoMimeType(plan.videoMimeType)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(
                DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(plan.videoBitrate).build())
                    .setEnableFallback(true)
                    .build(),
            )
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    stopProgress()
                    transformer = null
                    _state.value = ExportState.Verifying
                    scope.launch { finish(out, plan, baseName, startedAt) }
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    stopProgress()
                    transformer = null
                    cleanupTemp()
                    _state.value = ExportState.Failed(explain(exportException), "${exportException.errorCodeName}: ${exportException.message}")
                }
            })
            .build()
        transformer = t
        try {
            t.start(composition, out.absolutePath)
        } catch (e: Exception) {
            transformer = null
            cleanupTemp()
            _state.value = ExportState.Failed("Ekspor tidak dapat dimulai.", e.toString())
            return
        }
        progressJob = scope.launch {
            val holder = ProgressHolder()
            while (isActive) {
                val current = transformer ?: break
                val p = if (current.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) holder.progress else null
                val s = _state.value
                if (s is ExportState.Running && p != null && p != s.progress) _state.value = s.copy(progress = p)
                delay(200)
            }
        }
    }

    fun cancel() {
        val t = transformer ?: return
        t.cancel()
        transformer = null
        stopProgress()
        cleanupTemp()
        _state.value = ExportState.Cancelled
    }

    fun reset() {
        if (!isBusy) _state.value = ExportState.Idle
    }

    private fun buildComposition(plan: ExportPlan): Composition {
        val presentation = Presentation.createForWidthAndHeight(plan.size.width, plan.size.height, Presentation.LAYOUT_SCALE_TO_FIT)
        val items = plan.segments.map { s ->
            val mediaItem = MediaItem.Builder()
                .setUri(s.uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(s.startMs)
                        .setEndPositionMs(s.endMs)
                        .build(),
                )
                .build()
            val audio: List<AudioProcessor> = if (s.volume < 1f) listOf(volumeProcessor(s.volume)) else emptyList()
            EditedMediaItem.Builder(mediaItem)
                .setFrameRate(plan.frameRate)
                .setEffects(Effects(audio, colorEffects(s.colorLook) + presentation))
                .build()
        }
        // Audio+video track types: clips without sound contribute silence, keeping A/V in sync.
        val sequence = EditedMediaItemSequence.withAudioAndVideoFrom(items)
        return Composition.Builder(sequence)
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            .build()
    }

    /**
     * Gain stage: mono/stereo keep their layout (identity matrix scaled by the clip volume);
     * 3–6 channel sources are downmixed to stereo at constant power, then scaled.
     */
    private fun volumeProcessor(volume: Float): AudioProcessor = ChannelMixingAudioProcessor().apply {
        for (channels in 1..2) putChannelMixingMatrix(ChannelMixingMatrix.createForConstantGain(channels, channels).scaleBy(volume))
        for (channels in 3..6) putChannelMixingMatrix(ChannelMixingMatrix.createForConstantPower(channels, 2).scaleBy(volume))
    }

    private suspend fun finish(file: File, plan: ExportPlan, baseName: String, startedAt: Long) {
        val verification = withContext(Dispatchers.IO) { verify(file, plan) }
        if (verification != null) {
            cleanupTemp()
            _state.value = ExportState.Failed("Verifikasi hasil gagal: $verification", null)
            return
        }
        try {
            val output = withContext(Dispatchers.IO) { publish(file, baseName, plan) }
            _state.value = ExportState.Success(output, System.currentTimeMillis() - startedAt)
        } catch (e: Exception) {
            _state.value = ExportState.Failed("Video selesai dirender tetapi gagal disimpan ke galeri.", e.toString())
        } finally {
            cleanupTemp()
        }
    }

    /** Returns a problem description, or null when the file is a valid, complete video. */
    private fun verify(file: File, plan: ExportPlan): String? {
        if (!file.exists() || file.length() == 0L) return "file kosong."
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(file.absolutePath)
            if (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != "yes") return "tidak ada trek video."
            if (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != "yes") return "tidak ada trek audio."
            val durationUs = (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * US_PER_MS
            if (!ExportPlanner.durationMatches(plan.expectedDurationUs, durationUs)) {
                return "durasi ${Timecode.format(durationUs)} tidak sesuai rencana ${Timecode.format(plan.expectedDurationUs)}."
            }
            null
        } catch (e: Exception) {
            "file tidak bisa dibaca (${e.javaClass.simpleName})."
        } finally {
            runCatching { r.release() }
        }
    }

    private fun publish(file: File, baseName: String, plan: ExportPlan): ExportOutput {
        val (w, h, durUs) = readShape(file, plan)
        val displayName = "${safeName(baseName)}_${System.currentTimeMillis() / 1000}.mp4"
        val size = file.length()
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$FOLDER")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore menolak entri baru")
            try {
                resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                    ?: throw IOException("Tidak bisa menulis ke galeri")
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            return ExportOutput(uri, "Movies/$FOLDER/$displayName", size, durUs, w, h)
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "").apply { mkdirs() }
            val target = File(dir, displayName)
            file.copyTo(target, overwrite = true)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", target)
            return ExportOutput(uri, target.absolutePath, size, durUs, w, h)
        }
    }

    private fun readShape(file: File, plan: ExportPlan): Triple<Int, Int, Long> {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(file.absolutePath)
            var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: plan.size.width
            var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: plan.size.height
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot % 180 != 0) w = h.also { h = w }
            val d = (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * US_PER_MS
            Triple(w, h, d)
        } finally {
            runCatching { r.release() }
        }
    }

    private fun explain(e: ExportException): String = when (e.errorCode) {
        in 2000..2999 -> "Media sumber tidak dapat dibaca. Mungkin file dipindah, dihapus, atau izinnya hilang."
        in 3000..3999 -> "Perangkat tidak bisa mendekode salah satu video sumber."
        in 4000..4999 -> "Encoder perangkat menolak pengaturan ini. Coba resolusi lebih rendah, 30 FPS, atau H.264."
        in 5000..5999 -> "Pemrosesan frame video gagal di GPU perangkat."
        in 6000..6999 -> "Pemrosesan audio gagal."
        in 7000..7999 -> "Gagal menulis file MP4 (penyimpanan penuh?)."
        else -> "Ekspor gagal karena kesalahan tak terduga."
    }

    private fun stopProgress() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun cleanupTemp() {
        tempFile?.delete()
        tempFile = null
    }

    /** Deletes renders orphaned by a process death during a previous export. */
    fun cleanupOrphans() {
        File(context.cacheDir, "export").listFiles().orEmpty().forEach { it.delete() }
    }

    fun release() {
        transformer?.cancel()
        transformer = null
        stopProgress()
        cleanupTemp()
    }

    private fun safeName(name: String) = name.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().replace(' ', '_').ifEmpty { "Margaretha" }.take(40)

    companion object {
        const val FOLDER = "Margaretha Editor"
    }
}

/** Probes hardware encoders so the export screen only offers settings this device can encode. */
object CodecSupport {
    private val encoders by lazy {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { it.isEncoder }
    }

    fun supports(mimeType: String, width: Int, height: Int, frameRate: Int): Boolean = encoders.any { info ->
        info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) } && runCatching {
            val caps = info.getCapabilitiesForType(mimeType).videoCapabilities
            caps.areSizeAndRateSupported(width, height, frameRate.toDouble()) ||
                caps.areSizeAndRateSupported(height, width, frameRate.toDouble())
        }.getOrDefault(false)
    }

    fun hasEncoder(mimeType: String): Boolean = encoders.any { info -> info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) } }
}
