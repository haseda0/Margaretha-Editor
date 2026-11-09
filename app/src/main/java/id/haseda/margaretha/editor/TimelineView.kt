package id.haseda.margaretha.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.haseda.margaretha.media.ThumbnailCache
import id.haseda.margaretha.timeline.Clip
import id.haseda.margaretha.timeline.EditOutcome
import id.haseda.margaretha.timeline.Project
import id.haseda.margaretha.timeline.Timecode
import id.haseda.margaretha.timeline.TimelineEngine
import id.haseda.margaretha.timeline.TrimClip
import id.haseda.margaretha.timeline.US_PER_SECOND
import id.haseda.margaretha.ui.MColors
import id.haseda.margaretha.ui.MIcons
import id.haseda.margaretha.ui.VideoFrame
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private enum class Edge { START, END }

private data class TrimDrag(val clipId: String, val edge: Edge, val newIn: Long, val newOut: Long, val originalIn: Long)

private val RULER_H = 22.dp
private val TRACK_H = 62.dp
private val TILE_W = 44.dp

/**
 * Centre-playhead timeline: drag to scrub, pinch to zoom, tap to select, drag the selected
 * clip's handles to trim (committed as one undoable edit on release). Scrub snaps to clip edges.
 */
@Composable
fun TimelineView(
    project: Project,
    positionUs: Long,
    selectedClipId: String?,
    cache: ThumbnailCache,
    onScrub: (timeUs: Long, final: Boolean) -> Unit,
    onSelect: (String?) -> Unit,
    onTrim: (clipId: String, newIn: Long, newOut: Long) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var pxPerSecond by remember { mutableFloatStateOf(with(density) { 64.dp.toPx() }) }
    var trim by remember { mutableStateOf<TrimDrag?>(null) }
    val textMeasurer = rememberTextMeasurer()

    // While a handle is dragged, preview the trimmed layout without touching history.
    val shown: Project = trim?.let { t ->
        (TimelineEngine.apply(project, TrimClip(t.clipId, t.newIn, t.newOut)) as? EditOutcome.Applied)?.project
    } ?: project
    val clips = shown.mainTrack.clips

    val currentPosition by rememberUpdatedState(positionUs)
    val currentProject by rememberUpdatedState(project)
    val currentSelected by rememberUpdatedState(selectedClipId)
    val currentOnTrim by rememberUpdatedState(onTrim)
    val currentPxPerSecond by rememberUpdatedState(pxPerSecond)
    val currentOnScrub by rememberUpdatedState(onScrub)
    val currentOnSelect by rememberUpdatedState(onSelect)

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(RULER_H + TRACK_H + 26.dp)
            .background(MColors.Abyss)
            .semantics { contentDescription = "Timeline. Geser untuk mencari posisi, cubit untuk zoom." }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val center = size.width / 2f
                    val pps = currentPxPerSecond
                    fun xAt(t: Long) = center + (t - currentPosition) / US_PER_SECOND.toFloat() * pps

                    // 1) Trim: a press on a handle of the selected clip (measured in this fixed frame).
                    val selected = currentSelected?.let { id -> currentProject.mainTrack.clip(id) }
                    val trackTopPx = (RULER_H + 6.dp).toPx()
                    val handlePx = 20.dp.toPx()
                    val slop = 10.dp.toPx()
                    val inTrackY = down.position.y in trackTopPx..(trackTopPx + TRACK_H.toPx())
                    val edge = if (selected != null && inTrackY) {
                        val l = xAt(selected.timelineStartUs)
                        val r = xAt(selected.timelineEndUs)
                        when {
                            down.position.x in (l - slop)..(l + handlePx) -> Edge.START
                            down.position.x in (r - handlePx)..(r + slop) -> Edge.END
                            else -> null
                        }
                    } else null
                    if (selected != null && edge != null) {
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null) {
                                trim = null
                                break
                            }
                            val dx = change.positionChange().x
                            change.consume()
                            val deltaUs = dx / pps * US_PER_SECOND
                            if (!change.pressed) {
                                trim = updateTrim(trim, currentProject, selected, edge, deltaUs, DragPhase.END, currentOnTrim)
                                break
                            }
                            if (dx != 0f) trim = updateTrim(trim, currentProject, selected, edge, deltaUs, DragPhase.MOVE, currentOnTrim)
                        }
                        return@awaitEachGesture
                    }

                    // 2) Scrub / pinch-zoom / tap-to-select.
                    var dragging = false
                    var time = currentPosition
                    var travelled = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        if (!dragging) {
                            travelled += abs(pan.x)
                            if (travelled > viewConfiguration.touchSlop || zoom != 1f) dragging = true
                        }
                        if (dragging) {
                            if (zoom != 1f) {
                                pxPerSecond = (pxPerSecond * zoom).coerceIn(6.dp.toPx(), 600.dp.toPx())
                            } else if (event.changes.size == 1) {
                                time = (time - (pan.x / currentPxPerSecond * US_PER_SECOND).toLong())
                                    .coerceIn(0L, currentProject.durationUs)
                                currentOnScrub(time, false)
                            }
                            event.changes.forEach { it.consume() }
                        }
                        if (event.changes.none { it.pressed }) break
                    }
                    if (dragging) {
                        currentOnScrub(snap(time, currentProject.mainTrack.clips, currentPxPerSecond), true)
                    } else {
                        val tapTime = currentPosition + ((down.position.x - center) / pps * US_PER_SECOND).toLong()
                        val hit = currentProject.mainTrack.clips.firstOrNull { tapTime >= it.timelineStartUs && tapTime < it.timelineEndUs }
                        currentOnSelect(hit?.id)
                    }
                }
            },
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val center = widthPx / 2f
        val pxPerUs = pxPerSecond / US_PER_SECOND
        fun xOf(t: Long) = center + (t - positionUs) * pxPerUs

        // ---- Ruler ----
        Canvas(Modifier.fillMaxWidth().height(RULER_H)) {
            val minSpacing = 84.dp.toPx()
            val steps = longArrayOf(100_000, 250_000, 500_000, 1_000_000, 2_000_000, 5_000_000, 10_000_000, 15_000_000, 30_000_000, 60_000_000, 120_000_000, 300_000_000)
            val major = steps.firstOrNull { it * pxPerUs >= minSpacing } ?: steps.last()
            val minor = major / 5
            val startT = ((positionUs - center / pxPerUs) / minor).toLong().coerceAtLeast(0) * minor
            val endT = (positionUs + (widthPx - center) / pxPerUs).toLong()
            var t = startT
            val labelStyle = TextStyle(color = MColors.TextFaint, fontSize = 10.sp, fontFeatureSettings = "tnum")
            while (t <= endT) {
                val x = xOf(t)
                val isMajor = t % major == 0L
                drawLine(
                    color = if (isMajor) MColors.TextFaint else MColors.Line,
                    start = Offset(x, size.height - if (isMajor) 9.dp.toPx() else 5.dp.toPx()),
                    end = Offset(x, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
                if (isMajor) {
                    drawText(textMeasurer, Timecode.short(t).let { if (major < US_PER_SECOND) "${it}.${(t % US_PER_SECOND) / 100_000}" else it }, Offset(x + 3.dp.toPx(), 1.dp.toPx()), labelStyle)
                }
                t += minor
            }
        }

        // ---- Clips ----
        val trackTop = RULER_H + 6.dp
        val trackHeightPx = with(density) { TRACK_H.toPx() }
        val tilePx = with(density) { TILE_W.toPx() }
        val trimIndex = trim?.let { t -> clips.indexOfFirst { it.id == t.clipId } } ?: -1
        val startShiftPx = trim?.takeIf { it.edge == Edge.START }?.let { (it.newIn - it.originalIn) * pxPerUs } ?: 0f

        clips.forEachIndexed { index, clip ->
            val shift = if (trimIndex >= 0 && index >= trimIndex) startShiftPx else 0f
            val x = xOf(clip.timelineStartUs) + shift
            val w = clip.durationUs * pxPerUs
            if (x + w < -tilePx || x > widthPx + tilePx) return@forEachIndexed
            key(clip.id) {
            val asset = shown.assets[clip.assetId]
            val selected = clip.id == selectedClipId
            val shape = RoundedCornerShape(8.dp)
            Box(
                Modifier
                    .offset { IntOffset(x.roundToInt(), trackTop.roundToPx()) }
                    .requiredWidth(with(density) { (w - 2.dp.toPx()).coerceAtLeast(1f).toDp() })
                    .height(TRACK_H)
                    .clip(shape)
                    .background(MColors.ClipBody)
                    .border(if (selected) 2.dp else 1.dp, if (selected) MColors.Accent else MColors.Line, shape),
            ) {
                // Thumbnail tiles, only those on screen.
                val firstTile = floor(((-x).coerceAtLeast(0f)) / tilePx).toInt()
                val lastTile = ceil(((widthPx - x).coerceAtMost(w)) / tilePx).toInt()
                if (asset != null) {
                    for (i in firstTile until lastTile.coerceAtMost(ceil(w / tilePx).toInt())) {
                        val tileTime = clip.sourceInUs + ((i * tilePx + tilePx / 2) / pxPerUs).toLong()
                        VideoFrame(
                            cache = cache,
                            uri = asset.uri,
                            timeUs = tileTime.coerceAtMost(clip.sourceOutUs - 1),
                            heightDp = TRACK_H,
                            modifier = Modifier
                                .offset { IntOffset((i * tilePx).roundToInt(), 0) }
                                .width(TILE_W)
                                .fillMaxHeight(),
                        )
                    }
                }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent, Color.Transparent))))
                if (w > with(density) { 40.dp.toPx() }) {
                    Row(Modifier.padding(start = if (selected) 22.dp else 6.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(Timecode.short(clip.durationUs), style = MaterialTheme.typography.labelSmall, color = MColors.Text)
                        if (clip.muted || clip.volume == 0f) {
                            Spacer(Modifier.width(4.dp))
                            Icon(MIcons.Mute, contentDescription = "Bisu", tint = MColors.Warning, modifier = Modifier.size(12.dp))
                        }
                    }
                }
                if (selected) {
                    TrimHandle(Modifier.align(Alignment.CenterStart), "Pegangan pangkas awal")
                    TrimHandle(Modifier.align(Alignment.CenterEnd), "Pegangan pangkas akhir")
                }
            }
            }
        }

        // ---- Add button after the last clip ----
        val endX = xOf(shown.mainTrack.endUs) + if (trimIndex >= 0) startShiftPx else 0f
        Box(
            Modifier
                .offset { IntOffset((endX + 8.dp.toPx()).roundToInt(), (trackTop + 9.dp).roundToPx()) }
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MColors.Surface3)
                .border(1.dp, MColors.LineStrong, RoundedCornerShape(12.dp))
                .clickable(onClickLabel = "Tambah video", onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(MIcons.Add, contentDescription = "Tambah video", tint = MColors.Text, modifier = Modifier.size(22.dp))
        }

        // ---- Playhead ----
        Canvas(Modifier.fillMaxSize()) {
            val x = center
            drawLine(MColors.Playhead.copy(alpha = 0.25f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 6.dp.toPx())
            drawLine(MColors.Playhead, Offset(x, 2.dp.toPx()), Offset(x, size.height - 4.dp.toPx()), strokeWidth = 2.dp.toPx())
            drawRoundRect(
                MColors.Playhead,
                topLeft = Offset(x - 5.dp.toPx(), 0f),
                size = Size(10.dp.toPx(), 7.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }
    }
}

private enum class DragPhase { MOVE, END, CANCEL }

private fun updateTrim(
    current: TrimDrag?,
    project: Project,
    clip: Clip,
    edge: Edge,
    deltaUs: Float,
    phase: DragPhase,
    onTrim: (String, Long, Long) -> Unit,
): TrimDrag? {
    val asset = project.assets[clip.assetId] ?: return null
    if (phase == DragPhase.CANCEL) return null
    val base = current?.takeIf { it.clipId == clip.id && it.edge == edge }
        ?: TrimDrag(clip.id, edge, clip.sourceInUs, clip.sourceOutUs, clip.sourceInUs)
    val min = TimelineEngine.MIN_CLIP_US
    val next = when (edge) {
        Edge.START -> base.copy(newIn = (base.newIn + deltaUs.toLong()).coerceIn(0L, base.newOut - min))
        Edge.END -> base.copy(newOut = (base.newOut + deltaUs.toLong()).coerceIn(base.newIn + min, asset.durationUs))
    }
    return when (phase) {
        DragPhase.MOVE -> next
        DragPhase.END -> {
            if (next.newIn != clip.sourceInUs || next.newOut != clip.sourceOutUs) onTrim(clip.id, next.newIn, next.newOut)
            null
        }
        DragPhase.CANCEL -> null
    }
}

/** Visual grip; the drag itself is handled by the timeline in a fixed coordinate frame. */
@Composable
private fun TrimHandle(modifier: Modifier, label: String) {
    Box(
        modifier
            .width(20.dp)
            .fillMaxHeight()
            .background(MColors.Accent)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(3.dp).height(20.dp).clip(RoundedCornerShape(2.dp)).background(MColors.OnAccent.copy(alpha = 0.7f)))
    }
}

/** Snaps to the nearest clip boundary within 12dp-equivalent; otherwise returns the time as is. */
private fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.snap(timeUs: Long, clips: List<Clip>, pxPerSecond: Float): Long {
    val thresholdUs = (12.dp.toPx() / pxPerSecond * US_PER_SECOND).toLong()
    val candidates = clips.flatMap { listOf(it.timelineStartUs, it.timelineEndUs) }
    val nearest = candidates.minByOrNull { abs(it - timeUs) } ?: return timeUs
    return if (abs(nearest - timeUs) <= thresholdUs) nearest else timeUs
}
