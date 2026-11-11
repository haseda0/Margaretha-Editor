package id.haseda.margaretha.media

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import id.haseda.margaretha.timeline.RenderSegment
import id.haseda.margaretha.timeline.US_PER_MS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Real preview of the main track: an ExoPlayer playlist of clipped media items built from the
 * same [RenderSegment]s the exporter uses, so in/out points match the rendered file.
 * Must be used from the main thread.
 */
@OptIn(UnstableApi::class)
class PreviewController(context: Context, private val scope: CoroutineScope) {
    val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        setSeekParameters(SeekParameters.EXACT)
        repeatMode = Player.REPEAT_MODE_OFF
    }

    private var segments: List<RenderSegment> = emptyList()
    private var starts: LongArray = LongArray(0)
    private var totalUs = 0L
    private var ticker: Job? = null

    private val _positionUs = MutableStateFlow(0L)
    val positionUs: StateFlow<Long> = _positionUs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) startTicker() else {
                    stopTicker()
                    publishPosition()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { applyVolume(); applyColorLook() }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) _positionUs.value = totalUs
            }

            override fun onPlayerError(error: PlaybackException) {
                _error.value = "Pratinjau gagal: ${error.errorCodeName}"
            }
        })
    }

    /** Rebuilds the playlist only when clip structure changed; volume-only edits are applied live. */
    fun setSegments(newSegments: List<RenderSegment>, keepTimeUs: Long) {
        val structureChanged = newSegments.map { it.structureKey() } != segments.map { it.structureKey() }
        segments = newSegments
        starts = LongArray(newSegments.size)
        var acc = 0L
        newSegments.forEachIndexed { i, s ->
            starts[i] = acc
            acc += s.durationUs
        }
        totalUs = acc
        if (!structureChanged) {
            applyVolume()
            applyColorLook()
            return
        }
        _error.value = null
        if (newSegments.isEmpty()) {
            player.clearMediaItems()
            _positionUs.value = 0L
            return
        }
        val items = newSegments.map { s ->
            MediaItem.Builder()
                .setUri(s.uri)
                .setMediaId(s.clipId)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(s.startMs)
                        .setEndPositionMs(s.endMs)
                        .build(),
                )
                .build()
        }
        val target = keepTimeUs.coerceIn(0L, (totalUs - 1).coerceAtLeast(0L))
        val (index, offsetUs) = locate(target)
        val wasPlaying = player.playWhenReady
        player.setVideoEffects(colorEffects(newSegments.getOrNull(index)?.colorLook ?: id.haseda.margaretha.timeline.ColorLook.ORIGINAL))
        player.setMediaItems(items, index, offsetUs / US_PER_MS)
        player.prepare()
        player.playWhenReady = wasPlaying
        _positionUs.value = target
        applyVolume()
        applyColorLook()
    }

    private fun RenderSegment.structureKey() = Triple(clipId, uri, startMs to endMs)

    private fun locate(timeUs: Long): Pair<Int, Long> {
        if (starts.isEmpty()) return 0 to 0L
        var index = starts.indexOfLast { it <= timeUs }.coerceAtLeast(0)
        var offset = timeUs - starts[index]
        val dur = segments[index].durationUs
        if (offset >= dur) {
            // Exactly on a boundary: belongs to the next clip (or end of the last one).
            if (index < segments.lastIndex) {
                index++
                offset = 0L
            } else offset = dur
        }
        return index to offset
    }

    fun currentTimelineUs(): Long {
        val i = player.currentMediaItemIndex
        if (i !in starts.indices) return 0L
        return (starts[i] + player.currentPosition * US_PER_MS).coerceIn(0L, totalUs)
    }

    fun seekTo(timeUs: Long) {
        if (segments.isEmpty()) return
        val t = timeUs.coerceIn(0L, totalUs)
        val (index, offset) = locate(t)
        player.seekTo(index, offset / US_PER_MS)
        _positionUs.value = t
    }

    fun togglePlay() = if (player.isPlaying) pause() else play()

    fun play() {
        if (segments.isEmpty()) return
        if (player.playbackState == Player.STATE_ENDED || _positionUs.value >= totalUs - 50_000L) seekTo(0L)
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        player.play()
    }

    fun pause() {
        player.pause()
        publishPosition()
    }

    private fun applyVolume() {
        val i = player.currentMediaItemIndex
        player.volume = segments.getOrNull(i)?.volume ?: 1f
    }

    private fun applyColorLook() {
        val i = player.currentMediaItemIndex
        player.setVideoEffects(segments.getOrNull(i)?.let { colorEffects(it.colorLook) } ?: emptyList())
    }

    private fun publishPosition() {
        if (segments.isNotEmpty()) _positionUs.value = currentTimelineUs()
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                _positionUs.value = currentTimelineUs()
                delay(16)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    fun clear() {
        stopTicker()
        segments = emptyList()
        starts = LongArray(0)
        totalUs = 0L
        player.stop()
        player.clearMediaItems()
        _positionUs.value = 0L
        _error.value = null
    }

    fun release() {
        stopTicker()
        player.release()
    }
}
