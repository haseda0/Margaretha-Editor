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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.haseda.margaretha.media.ThumbnailCache
import id.haseda.margaretha.media.WaveformCache
import id.haseda.margaretha.timeline.Clip
import id.haseda.margaretha.timeline.Edit
import id.haseda.margaretha.timeline.EditOutcome
import id.haseda.margaretha.timeline.MoveFreeClip
import id.haseda.margaretha.timeline.Project
import id.haseda.margaretha.timeline.TextItem
import id.haseda.margaretha.timeline.Track
import id.haseda.margaretha.timeline.TrackKind
import id.haseda.margaretha.timeline.UpdateText
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
private val ROW_H = 26.dp
private val ROW_GAP = 4.dp

private enum class DragMode { MOVE, START, END }

/** A clip or text shown as a bar on one of the free rows under the main track. */
private data class RowItem(
    val id: String,
    val startUs: Long,
    val endUs: Long,
    val label: String,
    val isText: Boolean,
    /** Audio bars only: where the sound comes from, for the waveform. */
    val uri: String? = null,
    val sourceInUs: Long = 0L,
)

private fun freeRows(project: Project): List<Track> =
    project.tracks.filter { it.kind != TrackKind.MAIN_VIDEO && (it.clips.isNotEmpty() || it.texts.isNotEmpty()) }

private fun rowItems(project: Project, track: Track): List<RowItem> =
    if (track.isTextual) track.texts.map { RowItem(it.id, it.startUs, it.endUs, it.text.lineSequence().first(), true) }
    else track.clips.map {
        val asset = project.assets[it.assetId]
        RowItem(it.id, it.timelineStartUs, it.timelineEndUs, asset?.displayName.orEmpty(), false, asset?.uri, it.sourceInUs)
    }

private fun rowColor(track: Track): Color = when {
    track.kind == TrackKind.TEXT -> MColors.TrackText
    track.kind == TrackKind.SUBTITLE -> MColors.TrackSubtitle
    track.id == Project.VOICE_TRACK_ID -> MColors.TrackVoice
    else -> MColors.TrackMusic
}

/** The engine command a drag on a free-row item stands for; null when the item vanished. */
private fun freeEdit(project: Project, track: Track, item: RowItem, mode: DragMode, deltaUs: Long, snap: (Long) -> Long): Edit? {
    if (item.isText) {
        val t = track.text(item.id) ?: return null
        val min = TextItem.MIN_US
        return when (mode) {
            DragMode.MOVE -> UpdateText(t.copy(startUs = snap((t.startUs + deltaUs).coerceAtLeast(0L))))
            DragMode.START -> {
                val start = snap(t.startUs + deltaUs).coerceIn(0L, t.endUs - min)
                UpdateText(t.copy(startUs = start, durationUs = t.endUs - start))
            }
            DragMode.END -> UpdateText(t.copy(durationUs = (snap(t.endUs + deltaUs) - t.startUs).coerceAtLeast(min)))
        }
    }
    val c = track.clip(item.id) ?: return null
    val asset = project.assets[c.assetId] ?: return null
    val min = TimelineEngine.MIN_CLIP_US
    return when (mode) {
        DragMode.MOVE -> MoveFreeClip(c.id, snap((c.timelineStartUs + deltaUs).coerceAtLeast(0L)))
        DragMode.START -> TrimClip(c.id, (c.sourceInUs + deltaUs).coerceIn(0L, c.sourceOutUs - min), c.sourceOutUs)
        DragMode.END -> TrimClip(c.id, c.sourceInUs, (c.sourceOutUs + deltaUs).coerceIn(c.sourceInUs + min, asset.durationUs))
    }
}

/**
 * Centre-playhead timeline: drag to scrub, pinch to zoom, tap to select, drag the selected
 * clip's handles to trim (committed as one undoable edit on release). Scrub snaps to clip edges.
 * Rows under the main track hold texts, subtitles and audio: drag a selected bar to move it,
 * drag its ends to change its length.
 */
@Composable
fun TimelineView(
    project: Project,
    positionUs: Long,
    selectedClipId: String?,
    selectedTextId: String?,
    cache: ThumbnailCache,
    waveforms: WaveformCache,
    onScrub: (timeUs: Long, final: Boolean) -> Unit,
    onSelect: (String?) -> Unit,
    onSelectText: (String?) -> Unit,
    onEdit: (Edit) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onTrim: (String, Long, Long) -> Unit = { clipId, newIn, newOut -> onEdit(TrimClip(clipId, newIn, newOut)) }
    var pending by remember { mutableStateOf<Edit?>(null) }
    val density = LocalDensity.current
    var pxPerSecond by remember { mutableFloatStateOf(with(density) { 64.dp.toPx() }) }
    var trim by remember { mutableStateOf<TrimDrag?>(null) }
    val textMeasurer = rememberTextMeasurer()

    // While a handle is dragged, preview the trimmed layout without touching history.
    val shown: Project = (pending ?: trim?.let { t -> TrimClip(t.clipId, t.newIn, t.newOut) })?.let { edit ->
        (TimelineEngine.apply(project, edit) as? EditOutcome.Applied)?.project
    } ?: project
    val clips = shown.mainTrack.clips
    val rows = freeRows(shown)
    val currentSelectedText by rememberUpdatedState(selectedTextId)
    val currentOnSelectText by rememberUpdatedState(onSelectText)
    val currentOnEdit by rememberUpdatedState(onEdit)

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
            .height(RULER_H + TRACK_H + 26.dp + (ROW_H + ROW_GAP) * rows.size)
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
                            // Handles move in timeline time; the source moves faster on sped-up clips.
                            val deltaUs = dx / pps * US_PER_SECOND * selected.speed
                            if (!change.pressed) {
                                trim = updateTrim(trim, currentProject, selected, edge, deltaUs, DragPhase.END, currentOnTrim)
                                break
                            }
                            if (dx != 0f) trim = updateTrim(trim, currentProject, selected, edge, deltaUs, DragPhase.MOVE, currentOnTrim)
                        }
                        return@awaitEachGesture
                    }

                    // 2) Free rows: drag the selected bar to move it, drag its ends to resize it.
                    val rowsTopPx = trackTopPx + TRACK_H.toPx() + 6.dp.toPx()
                    val rowPitchPx = (ROW_H + ROW_GAP).toPx()
                    val downTime = currentPosition + ((down.position.x - center) / pps * US_PER_SECOND).toLong()
                    val gestureRows = freeRows(currentProject)
                    val rowTrack = if (down.position.y >= rowsTopPx) gestureRows.getOrNull(((down.position.y - rowsTopPx) / rowPitchPx).toInt()) else null
                    val rowHit = rowTrack?.let { t -> rowItems(currentProject, t).firstOrNull { downTime >= it.startUs && downTime < it.endUs } }
                    if (rowTrack != null && rowHit != null && !rowTrack.locked && (rowHit.id == currentSelected || rowHit.id == currentSelectedText)) {
                        down.consume()
                        val l = xAt(rowHit.startUs)
                        val r = xAt(rowHit.endUs)
                        val grip = minOf(16.dp.toPx(), (r - l) / 3f)
                        val mode = when {
                            down.position.x < l + grip -> DragMode.START
                            down.position.x > r - grip -> DragMode.END
                            else -> DragMode.MOVE
                        }
                        val snapPoints = currentProject.mainTrack.clips.flatMap { listOf(it.timelineStartUs, it.timelineEndUs) } + currentPosition + 0L
                        val thresholdUs = (10.dp.toPx() / pps * US_PER_SECOND).toLong()
                        val snapTo: (Long) -> Long = { t -> snapPoints.minByOrNull { abs(it - t) }?.takeIf { abs(it - t) <= thresholdUs } ?: t }
                        var total = 0f
                        var moved = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null) {
                                pending = null
                                break
                            }
                            total += change.positionChange().x
                            change.consume()
                            if (abs(total) > viewConfiguration.touchSlop) moved = true
                            if (moved) pending = freeEdit(currentProject, rowTrack, rowHit, mode, (total / pps * US_PER_SECOND).toLong(), snapTo)
                            if (!change.pressed) {
                                val edit = pending
                                pending = null
                                if (moved && edit != null) currentOnEdit(edit)
                                break
                            }
                        }
                        return@awaitEachGesture
                    }

                    // 3) Scrub / pinch-zoom / tap-to-select.
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
                    } else if (rowTrack != null) {
                        when {
                            rowHit == null -> currentOnSelect(null)
                            rowHit.isText -> currentOnSelectText(rowHit.id)
                            else -> currentOnSelect(rowHit.id)
                        }
                    } else {
                        val hit = currentProject.mainTrack.clips.firstOrNull { downTime >= it.timelineStartUs && downTime < it.timelineEndUs }
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
        val trimSpeed = trim?.let { t -> project.mainTrack.clip(t.clipId)?.speed } ?: 1f
        val startShiftPx = trim?.takeIf { it.edge == Edge.START }?.let { (it.newIn - it.originalIn) * pxPerUs / trimSpeed } ?: 0f

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
                        val tileTime = clip.sourceAtOffsetUs(((i * tilePx + tilePx / 2) / pxPerUs).toLong())
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
                        Text(
                            Timecode.short(clip.durationUs) + if (clip.speedPermille != 1000) " · ${clip.speed}×" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MColors.Text,
                        )
                        if (clip.muted || clip.volume == 0f) {
                            Spacer(Modifier.width(4.dp))
                            Icon(MIcons.Mute, contentDescription = "Bisu", tint = MColors.Warning, modifier = Modifier.size(12.dp))
                        }
                    }
                }
                // Keyframe markers along the bottom edge of the clip.
                clip.keyframes.forEach { k ->
                    val kx = clip.offsetOfSourceUs(k.sourceUs) * pxPerUs
                    if (kx in 0f..w) {
                        Icon(
                            MIcons.Diamond,
                            contentDescription = null,
                            tint = MColors.AccentBright,
                            modifier = Modifier.offset { IntOffset((kx - 5.dp.toPx()).roundToInt(), (TRACK_H - 13.dp).roundToPx()) }.size(10.dp),
                        )
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

        // ---- Free rows: text, subtitles, audio ----
        rows.forEachIndexed { rowIndex, track ->
            val top = trackTop + TRACK_H + 6.dp + (ROW_H + ROW_GAP) * rowIndex
            val color = rowColor(track)
            rowItems(shown, track).forEach { item ->
                val x = xOf(item.startUs)
                val w = (item.endUs - item.startUs) * pxPerUs
                if (x + w < 0f || x > widthPx) return@forEach
                key(item.id) {
                    val selected = item.id == selectedClipId || item.id == selectedTextId
                    val shape = RoundedCornerShape(6.dp)
                    Box(
                        Modifier
                            .offset { IntOffset(x.roundToInt(), top.roundToPx()) }
                            .requiredWidth(with(density) { (w - 1.dp.toPx()).coerceAtLeast(2f).toDp() })
                            .height(ROW_H)
                            .alpha(if (track.enabled) 1f else 0.4f)
                            .clip(shape)
                            .background(color.copy(alpha = if (selected) 0.42f else 0.24f))
                            .border(if (selected) 2.dp else 1.dp, if (selected) MColors.Accent else color.copy(alpha = 0.6f), shape),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (item.uri != null) Waveform(waveforms, item.uri, item.sourceInUs, pxPerUs, firstVisiblePx = (-x).coerceAtLeast(0f), lastVisiblePx = (widthPx - x).coerceAtMost(w), color = color)
                        if (w > with(density) { 28.dp.toPx() }) {
                            Text(
                                item.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MColors.Text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 6.dp),
                            )
                        }
                    }
                }
            }
            // Row badge pinned to the left edge: what the row holds and whether it is locked.
            Box(
                Modifier
                    .offset { IntOffset(4.dp.roundToPx(), (top + 3.dp).roundToPx()) }
                    .size(20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MColors.Abyss.copy(alpha = 0.85f))
                    .semantics { contentDescription = "Trek ${trackTitle(track)}" + if (track.locked) ", terkunci" else "" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (track.locked) MIcons.Lock else trackIcon(track), contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
            }
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

/** Peak bars of an audio clip; only the part on screen is drawn. Empty until the file is decoded. */
@Composable
private fun Waveform(cache: WaveformCache, uri: String, sourceInUs: Long, pxPerUs: Float, firstVisiblePx: Float, lastVisiblePx: Float, color: Color) {
    val peaks by produceState(initialValue = cache.peek(uri), uri) { if (value == null) value = cache.get(uri) }
    val data = peaks ?: return
    Canvas(Modifier.fillMaxSize()) {
        val step = 3.dp.toPx()
        val mid = size.height / 2f
        var px = floor(firstVisiblePx / step) * step
        while (px < lastVisiblePx) {
            val bucket = ((sourceInUs + (px / pxPerUs).toLong()) / WaveformCache.BUCKET_US).toInt()
            val peak = data.getOrNull(bucket) ?: 0f
            val half = (peak * mid * 0.9f).coerceAtLeast(0.5.dp.toPx())
            drawLine(color.copy(alpha = 0.85f), Offset(px + step / 2f, mid - half), Offset(px + step / 2f, mid + half), strokeWidth = 1.5.dp.toPx())
            px += step
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
