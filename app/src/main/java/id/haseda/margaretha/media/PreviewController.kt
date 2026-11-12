package id.haseda.margaretha.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import id.haseda.margaretha.timeline.AudioSegment
import id.haseda.margaretha.timeline.EffectStructure
import id.haseda.margaretha.timeline.Motion
import id.haseda.margaretha.timeline.RenderPlan
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
import kotlin.math.abs

/**
 * Real preview: an ExoPlayer playlist of clipped media items built from the same [RenderPlan]
 * the exporter uses, so in/out points, speed, effects and audio match the rendered file.
 * Audio tracks play on small companion players kept in sync with the picture.
 * Must be used from the main thread.
 */
@OptIn(UnstableApi::class)
class PreviewController(private val context: Context, private val scope: CoroutineScope) {
    val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        setSeekParameters(SeekParameters.EXACT)
        repeatMode = Player.REPEAT_MODE_OFF
    }

    private var plan = RenderPlan(emptyList(), emptyList(), emptyList(), 0L)
    private var segments: List<RenderSegment> = emptyList()
    private var starts: LongArray = LongArray(0)
    private var totalUs = 0L
    private var canvasAspect = 16f / 9f
    private var ticker: Job? = null
    private val companions = mutableListOf<CompanionAudio>()

    /** Effect chain currently installed on the player, and the clip it reads its values from. */
    private var installed: EffectStructure? = null
    @Volatile private var liveSegment: RenderSegment? = null

    /** User playback rate (0.5×, 1×, 2×) multiplied with each clip's own speed. */
    var rate: Float = 1f
        set(value) {
            field = value
            applyItemState()
        }

    /** Set after the effect pipeline failed on this device: preview plays without GPU effects. */
    private var safeMode = false

    /** Before/after: when true the picture is shown without colour work, effects or transforms. */
    var bypassEffects: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            applyEffects(force = true)
            redraw()
        }

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
                    syncCompanions(playing = false)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = applyItemState()

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    _positionUs.value = totalUs
                    syncCompanions(playing = false)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                id.haseda.margaretha.data.ErrorLog.record("PREVIEW ${error.errorCodeName}", error, segments.joinToString { it.kind.toString() + " " + it.uri })
                // Many devices cannot run the GPU effect pipeline. Fall back to plain playback once
                // instead of leaving a black, frozen preview.
                if (!safeMode && segments.isNotEmpty()) {
                    safeMode = true
                    val t = _positionUs.value
                    installed = null
                    player.setVideoEffects(emptyList())
                    val (index, offset) = locate(t.coerceIn(0L, (totalUs - 1).coerceAtLeast(0L)))
                    player.setMediaItems(segments.map { it.toMediaItem() }, index, sourceMs(index, offset))
                    player.prepare()
                    _error.value = "Efek pratinjau tidak didukung HP ini; video diputar tanpa efek (hasil ekspor tetap memakai efek)."
                    return
                }
                _error.value = "Pratinjau gagal: ${error.errorCodeName}"
            }
        })
    }

    /** Rebuilds the playlist only when clip structure changed; value-only edits are applied live. */
    fun setPlan(newPlan: RenderPlan, aspect: Float, keepTimeUs: Long) {
        val newSegments = newPlan.video
        val structureChanged = newSegments.map { it.structureKey() } != segments.map { it.structureKey() }
        val aspectChanged = aspect != canvasAspect
        plan = newPlan
        segments = newSegments
        canvasAspect = aspect
        starts = LongArray(newSegments.size) { newSegments[it].timelineStartUs }
        totalUs = newPlan.durationUs
        rebuildCompanions(newPlan.audioTracks)
        if (!structureChanged) {
            applyItemState(forceEffects = aspectChanged)
            syncCompanions(playing = player.isPlaying)
            if (!player.isPlaying) redraw()
            return
        }
        _error.value = null
        if (newSegments.isEmpty()) {
            player.clearMediaItems()
            _positionUs.value = 0L
            syncCompanions(playing = false)
            return
        }
        val target = keepTimeUs.coerceIn(0L, (totalUs - 1).coerceAtLeast(0L))
        val (index, offsetUs) = locate(target)
        val wasPlaying = player.playWhenReady
        // Effects must be installed before prepare(); the chain of the first shown clip goes in now.
        installed = null
        applyEffects(force = true, index = index)
        player.setMediaItems(newSegments.map { it.toMediaItem() }, index, sourceMs(index, offsetUs))
        player.prepare()
        player.playWhenReady = wasPlaying
        _positionUs.value = target
        applyItemState()
        syncCompanions(playing = false)
    }

    private fun RenderSegment.structureKey() = listOf(clipId, uri, startMs, endMs, speedPermille, kind)

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

    /** Position inside the source media (what the player seeks to) for a timeline offset. */
    private fun sourceMs(index: Int, offsetUs: Long): Long = offsetUs * segments[index].speedPermille / 1000L / US_PER_MS

    fun currentTimelineUs(): Long {
        val i = player.currentMediaItemIndex
        if (i !in starts.indices) return 0L
        val inClipUs = player.currentPosition * US_PER_MS * 1000L / segments[i].speedPermille
        return (starts[i] + inClipUs.coerceIn(0L, segments[i].durationUs)).coerceIn(0L, totalUs)
    }

    fun seekTo(timeUs: Long) {
        if (segments.isEmpty()) return
        val t = timeUs.coerceIn(0L, totalUs)
        val (index, offset) = locate(t)
        _positionUs.value = t
        player.seekTo(index, sourceMs(index, offset))
        if (index == player.currentMediaItemIndex) applyLevels()
        syncCompanions(playing = player.isPlaying)
    }

    /** Pushes the current frame through the effect chain again (needed while paused). */
    private fun redraw() {
        if (segments.isEmpty() || player.isPlaying) return
        val (index, offset) = locate(_positionUs.value)
        player.seekTo(index, sourceMs(index, offset))
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

    fun stop() {
        player.pause()
        seekTo(0L)
    }

    /** Applies everything that depends on which clip is on screen: effects, speed and levels. */
    private fun applyItemState(forceEffects: Boolean = false) {
        val seg = segments.getOrNull(player.currentMediaItemIndex)
        liveSegment = seg
        applyEffects(force = forceEffects)
        val speed = ((seg?.speed ?: 1f) * rate).coerceIn(0.1f, 8f)
        if (player.playbackParameters.speed != speed) player.playbackParameters = PlaybackParameters(speed)
        applyLevels()
    }

    private fun applyLevels() {
        val seg = segments.getOrNull(player.currentMediaItemIndex)
        player.volume = if (seg == null) 1f else {
            val offset = _positionUs.value - seg.timelineStartUs
            seg.volume * Motion.fadeGain(offset, seg.durationUs, seg.audioFadeInUs, seg.audioFadeOutUs)
        }
    }

    /**
     * Installs the GPU chain of the clip on screen. The chain reads the clip's values on every
     * frame, so it is only rebuilt when the set of effects changes, not while a slider moves.
     */
    private fun applyEffects(force: Boolean, index: Int = player.currentMediaItemIndex) {
        val seg = segments.getOrNull(index)
        liveSegment = seg
        if (seg == null || bypassEffects || safeMode) {
            if (installed?.isEmpty == false || force) player.setVideoEffects(emptyList())
            installed = null
            return
        }
        val structure = EffectStructure.of(seg)
        // Clips that need the same set of effects share one chain: no pipeline rebuild at the cut.
        if (!force && installed == structure) return
        installed = structure
        if (structure.isEmpty) {
            player.setVideoEffects(emptyList())
            return
        }
        // Preview time comes from the playhead, so it is right while paused and scrubbing too.
        val time = ClipTime { (_positionUs.value - (liveSegment?.timelineStartUs ?: 0L)).coerceAtLeast(0L) }
        val presentation = if (structure.matrix) Presentation.createForAspectRatio(canvasAspect, Presentation.LAYOUT_SCALE_TO_FIT) else null
        player.setVideoEffects(StudioEffects.build(structure, { liveSegment ?: seg }, canvasAspect, time, presentation))
    }

    // ---- Audio tracks --------------------------------------------------------------------------

    private fun rebuildCompanions(tracks: List<List<AudioSegment>>) {
        while (companions.size > tracks.size) companions.removeAt(companions.lastIndex).release()
        while (companions.size < tracks.size) companions += CompanionAudio(context)
        companions.forEachIndexed { i, c -> c.segments = tracks[i] }
    }

    private fun syncCompanions(playing: Boolean) {
        val t = _positionUs.value
        companions.forEach { it.sync(t, playing && t < totalUs, rate) }
    }

    // ---- Clock ---------------------------------------------------------------------------------

    private fun publishPosition() {
        if (segments.isNotEmpty()) _positionUs.value = currentTimelineUs()
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                _positionUs.value = currentTimelineUs()
                applyLevels()
                syncCompanions(playing = true)
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
        plan = RenderPlan(emptyList(), emptyList(), emptyList(), 0L)
        segments = emptyList()
        starts = LongArray(0)
        totalUs = 0L
        installed = null
        liveSegment = null
        player.stop()
        player.clearMediaItems()
        player.setVideoEffects(emptyList())
        rebuildCompanions(emptyList())
        _positionUs.value = 0L
        _error.value = null
    }

    fun release() {
        stopTicker()
        rebuildCompanions(emptyList())
        player.release()
    }
}

/** Plays one audio track of the project alongside the picture. One clip is audible at a time. */
@OptIn(UnstableApi::class)
private class CompanionAudio(context: Context) {
    private val player: ExoPlayer = ExoPlayer.Builder(context).build().apply { setSeekParameters(SeekParameters.EXACT) }
    private var loaded: AudioSegment? = null

    var segments: List<AudioSegment> = emptyList()
        set(value) {
            field = value
            // A clip that changed or disappeared must not keep playing its old media.
            if (loaded != null && value.none { it.sameMedia(loaded) }) unload()
        }

    private fun AudioSegment.sameMedia(other: AudioSegment?) =
        other != null && clipId == other.clipId && uri == other.uri && startMs == other.startMs && endMs == other.endMs

    private fun unload() {
        player.stop()
        player.clearMediaItems()
        loaded = null
    }

    fun sync(timelineUs: Long, playing: Boolean, rate: Float) {
        val seg = segments.firstOrNull { timelineUs >= it.timelineStartUs && timelineUs < it.timelineEndUs }
        if (seg == null) {
            if (player.playWhenReady) player.pause()
            return
        }
        val offsetMs = (timelineUs - seg.timelineStartUs) / US_PER_MS
        if (!seg.sameMedia(loaded)) {
            player.setMediaItem(
                MediaItem.Builder().setUri(seg.uri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder().setStartPositionMs(seg.startMs).setEndPositionMs(seg.endMs).build(),
                    ).build(),
                offsetMs,
            )
            player.prepare()
        } else if (!playing || !player.playWhenReady || abs(player.currentPosition - offsetMs) > DRIFT_MS) {
            // Re-align when starting, when scrubbing while paused, or after drifting from the picture.
            if (abs(player.currentPosition - offsetMs) > 30) player.seekTo(offsetMs)
        }
        loaded = seg
        player.volume = seg.volume * Motion.fadeGain(timelineUs - seg.timelineStartUs, seg.durationUs, seg.fadeInUs, seg.fadeOutUs)
        if (player.playbackParameters.speed != rate) player.playbackParameters = PlaybackParameters(rate)
        if (player.playWhenReady != playing) player.playWhenReady = playing
    }

    fun release() = player.release()

    private companion object {
        const val DRIFT_MS = 120L
    }
}
