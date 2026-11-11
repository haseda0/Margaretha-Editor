package id.haseda.margaretha.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Margaretha's physical design language.
 *
 * Three depth levels, lit from the top-left:
 *   1. structural surfaces — flat graphite panels (no bevel),
 *   2. functional controls — raised satin-metal faces with a chamfer highlight and contact shadow,
 *   3. precision details   — recessed wells (read-outs, wheel slots) and engraved ticks.
 * Only things you can press or turn are raised; only things you read or slide inside are recessed.
 */

/** A raised control face: lit top edge, shaded bottom edge. [pressed] pushes it into the panel. */
fun Modifier.raised(corner: Dp, pressed: Boolean = false, lit: Boolean = false): Modifier = this
    .clip(RoundedCornerShape(corner))
    .drawBehind {
        val r = CornerRadius(corner.toPx())
        val top = if (lit) MColors.AccentDeep else MColors.Surface3
        val bottom = if (lit) Color(0xFF6E3F2A) else MColors.Surface2
        drawRoundRect(Brush.verticalGradient(if (pressed) listOf(bottom, top) else listOf(top, bottom)), cornerRadius = r)
        val edge = 1.dp.toPx()
        // Chamfer: a hairline of light on top, a hairline of shadow underneath.
        drawRoundRect(
            Brush.verticalGradient(listOf(if (pressed) MColors.EdgeDark else MColors.EdgeLight, Color.Transparent), endY = size.height * 0.5f),
            cornerRadius = r,
            style = Stroke(edge),
        )
        drawRoundRect(
            Brush.verticalGradient(listOf(Color.Transparent, if (pressed) MColors.EdgeLight else MColors.EdgeDark), startY = size.height * 0.5f),
            cornerRadius = r,
            style = Stroke(edge),
        )
    }

/** A well cut into the panel: dark floor, shadow under the upper lip, a faint catch-light below. */
fun Modifier.recessed(corner: Dp, floor: Color = MColors.Glass): Modifier = this
    .clip(RoundedCornerShape(corner))
    .background(floor)
    .drawWithContent {
        drawContent()
        val r = CornerRadius(corner.toPx())
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xB3000000), Color.Transparent), endY = 5.dp.toPx()), cornerRadius = r)
        drawRoundRect(
            Brush.verticalGradient(listOf(Color.Transparent, MColors.EdgeLight), startY = size.height - 2.dp.toPx()),
            cornerRadius = r,
            style = Stroke(1.dp.toPx()),
        )
    }

/**
 * Rotary knob. Drag up/right to raise, down/left to lower; double-tap returns to [resetTo].
 * The lit arc and the engraved pointer both show the value; a detent is felt while turning.
 */
@Composable
fun Knob(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onChange: (Float) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 60.dp,
    resetTo: Float? = null,
    /** Where the lit arc starts; defaults to [resetTo] so centred controls light from their middle. */
    arcFrom: Float? = resetTo,
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    val span = range.endInclusive - range.start
    val fraction = ((value - range.start) / span).coerceIn(0f, 1f)
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    val done by rememberUpdatedState(onDone)
    Column(modifier.width(size + 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .size(size)
                .semantics {
                    contentDescription = label
                    stateDescription = valueText
                    progressBarRangeInfo = ProgressBarRangeInfo(value, range)
                    setProgress { target ->
                        change(target.coerceIn(range.start, range.endInclusive))
                        done()
                        true
                    }
                }
                .pointerInput(enabled, range) {
                    if (!enabled) return@pointerInput
                    var live = 0f
                    var lastDetent = 0
                    detectDragGestures(
                        onDragStart = {
                            live = current
                            lastDetent = ((live - range.start) / span * DETENTS).roundToInt()
                        },
                        onDragEnd = { done() },
                        onDragCancel = { done() },
                    ) { input, drag ->
                        input.consume()
                        // A full sweep takes about 220dp of travel: precise enough for a thumb.
                        live = (live + (drag.x - drag.y) / 220.dp.toPx() * span).coerceIn(range.start, range.endInclusive)
                        val detent = ((live - range.start) / span * DETENTS).roundToInt()
                        if (detent != lastDetent) {
                            lastDetent = detent
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        change(live)
                    }
                }
                .pointerInput(enabled, resetTo) {
                    if (!enabled || resetTo == null) return@pointerInput
                    detectTapGestures(onDoubleTap = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        change(resetTo)
                        done()
                    })
                },
        ) {
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            val outer = this.size.minDimension / 2f
            val ring = outer - 2.dp.toPx()
            val body = outer - 8.dp.toPx()
            val dim = if (enabled) 1f else 0.4f
            // Value arc: 270° sweep starting bottom-left.
            val arcSize = Size(ring * 2, ring * 2)
            val arcTopLeft = Offset(c.x - ring, c.y - ring)
            drawArc(MColors.Surface3, 135f, 270f, false, arcTopLeft, arcSize, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            // The lit arc starts at the resting value, so a centred control lights left or right of its middle.
            val origin = if (arcFrom != null) ((arcFrom - range.start) / span).coerceIn(0f, 1f) else 0f
            if (fraction != origin) {
                drawArc(
                    MColors.Accent.copy(alpha = dim), 135f + 270f * minOf(origin, fraction), 270f * abs(fraction - origin),
                    false, arcTopLeft, arcSize, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            // Contact shadow, then the satin-metal body lit from the top-left.
            drawCircle(Color(0x99000000), body, c + Offset(0f, 2.5.dp.toPx()))
            drawCircle(
                Brush.linearGradient(
                    listOf(MColors.MetalLight, MColors.MetalMid, MColors.MetalDark),
                    start = c - Offset(body, body),
                    end = c + Offset(body, body),
                ),
                body,
                c,
                alpha = dim,
            )
            drawCircle(MColors.EdgeLight, body, c, style = Stroke(1.dp.toPx()))
            // Specular catch-light on the lit shoulder.
            drawArc(
                Color.White.copy(alpha = 0.22f * dim), 200f, 50f, false,
                c - Offset(body - 1.5.dp.toPx(), body - 1.5.dp.toPx()), Size((body - 1.5.dp.toPx()) * 2, (body - 1.5.dp.toPx()) * 2),
                style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round),
            )
            // Knurled grip.
            for (i in 0 until 32) {
                val a = i / 32f * 2f * PI.toFloat()
                val dir = Offset(cos(a), sin(a))
                drawLine(Color(0x38000000), c + dir * (body - 5.dp.toPx()), c + dir * (body - 1.dp.toPx()), strokeWidth = 1.dp.toPx())
            }
            // Top cap, slightly domed.
            val cap = body - 7.dp.toPx()
            drawCircle(
                Brush.linearGradient(listOf(MColors.MetalMid, MColors.MetalDark), start = c - Offset(cap, cap), end = c + Offset(cap, cap)),
                cap,
                c,
                alpha = dim,
            )
            drawCircle(Color(0x4D000000), cap, c, style = Stroke(1.dp.toPx()))
            // Engraved pointer.
            val angle = (135f + 270f * fraction) / 180f * PI.toFloat()
            val dir = Offset(cos(angle), sin(angle))
            drawLine(
                MColors.AccentBright.copy(alpha = dim),
                c + dir * (cap * 0.35f),
                c + dir * (cap - 2.dp.toPx()),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(valueText, style = TimecodeStyle, color = if (resetTo != null && value != resetTo) MColors.AccentBright else MColors.Text, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MColors.TextFaint, maxLines = 1, textAlign = TextAlign.Center)
    }
}

private const val DETENTS = 40

/**
 * Jog wheel: a ribbed cylinder seen through a slot. Rolling it steps the playhead one frame per
 * rib, with a click felt on each frame — the precise companion to the coarse timeline scrub.
 */
@Composable
fun JogWheel(onStep: (frames: Int) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val haptics = LocalHapticFeedback.current
    var phase by remember { mutableFloatStateOf(0f) }
    val step by rememberUpdatedState(onStep)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(34.dp)
            .recessed(10.dp)
            .semantics { contentDescription = "Roda jog. Geser untuk maju atau mundur per frame." }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val ribPx = RIB.toPx()
                var carried = 0f
                detectHorizontalDragGestures(onDragStart = { carried = 0f }) { input, dx ->
                    input.consume()
                    phase += dx
                    carried += dx
                    val frames = (carried / ribPx).toInt()
                    if (frames != 0) {
                        carried -= frames * ribPx
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        // Rolling the surface to the left moves time forward, like dragging the timeline.
                        step(-frames)
                    }
                }
            },
    ) {
        val ribPx = RIB.toPx()
        val w = size.width
        val h = size.height
        val half = w / 2f
        // Cylinder body: brighter in the middle where it faces the viewer.
        drawRect(Brush.horizontalGradient(listOf(MColors.Glass, MColors.MetalDark, MColors.MetalMid, MColors.MetalDark, MColors.Glass)))
        // Ribs are evenly spaced on the cylinder, so they bunch up towards the edges.
        val count = (PI * half / ribPx).toInt()
        val shift = (phase / ribPx) % 1f
        for (i in -count / 2..count / 2) {
            val theta = ((i + shift) / (count / 2f)) * (PI.toFloat() / 2f)
            if (abs(theta) >= PI.toFloat() / 2f) continue
            val x = half + sin(theta) * half
            val facing = cos(theta)
            drawLine(Color.Black.copy(alpha = 0.55f * facing), Offset(x, 4.dp.toPx()), Offset(x, h - 4.dp.toPx()), strokeWidth = 2.dp.toPx() * facing + 0.5f)
            drawLine(Color.White.copy(alpha = 0.10f * facing), Offset(x + 1.5.dp.toPx(), 4.dp.toPx()), Offset(x + 1.5.dp.toPx(), h - 4.dp.toPx()), strokeWidth = 1.dp.toPx())
        }
        // Fixed index mark above the wheel.
        drawLine(MColors.Accent.copy(alpha = if (enabled) 1f else 0.3f), Offset(half, 0f), Offset(half, 6.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    }
}

private val RIB = 12.dp

/** Physical toggle: a metal cap sliding in a slot, with an indicator lamp when on. */
@Composable
fun HardSwitch(checked: Boolean, onChange: (Boolean) -> Unit, label: String, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier
            .size(width = 56.dp, height = 32.dp)
            .recessed(16.dp)
            .clickable(
                enabled = enabled,
                role = Role.Switch,
                onClickLabel = label,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onChange(!checked)
            }
            .semantics { stateDescription = if (checked) "Aktif" else "Nonaktif" }
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        // Lamp at the far side of the slot.
        Box(
            Modifier
                .align(if (checked) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(horizontal = 8.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(if (checked) MColors.Led else MColors.Surface3),
        )
        Canvas(Modifier.size(26.dp)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension / 2f
            drawCircle(Color(0x80000000), r, c + Offset(0f, 1.5.dp.toPx()))
            drawCircle(
                Brush.linearGradient(listOf(MColors.MetalLight, MColors.MetalMid, MColors.MetalDark), start = Offset.Zero, end = Offset(size.width, size.height)),
                r - 1.dp.toPx(),
                c,
                alpha = if (enabled) 1f else 0.4f,
            )
            drawCircle(MColors.EdgeLight, r - 1.dp.toPx(), c, style = Stroke(1.dp.toPx()))
        }
    }
}

/** Timecode window: amber figures behind smoked glass, like a deck's counter. */
@Composable
fun Readout(primary: String, secondary: String, modifier: Modifier = Modifier) {
    Column(modifier.recessed(8.dp).padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text(primary, style = TimecodeStyle, color = MColors.Led, maxLines = 1)
        Text(secondary, style = MaterialTheme.typography.labelSmall, color = MColors.TextFaint, maxLines = 1)
    }
}

/** A row of knobs on a shared control strip. */
@Composable
fun KnobStrip(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(MColors.Surface2, MColors.Surface1)))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Top,
    ) { content() }
}

/**
 * Fader: a lit strip in a milled groove with a metal cap. The lit part grows from [origin]
 * (the resting value), so centred controls read as "left of / right of neutral" at a glance.
 */
@Composable
fun GrooveSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    origin: Float = range.start,
    label: String = "",
    valueText: String = "",
    enabled: Boolean = true,
) {
    val span = range.endInclusive - range.start
    val fraction = ((value - range.start) / span).coerceIn(0f, 1f)
    val originFraction = ((origin - range.start) / span).coerceIn(0f, 1f)
    val change by rememberUpdatedState(onChange)
    val done by rememberUpdatedState(onDone)
    val current by rememberUpdatedState(value)
    Canvas(
        modifier
            .height(40.dp)
            .semantics {
                if (label.isNotEmpty()) contentDescription = label
                if (valueText.isNotEmpty()) stateDescription = valueText
                progressBarRangeInfo = ProgressBarRangeInfo(value, range)
                setProgress { target ->
                    change(target.coerceIn(range.start, range.endInclusive))
                    done()
                    true
                }
            }
            .pointerInput(enabled, range) {
                if (!enabled) return@pointerInput
                val inset = CAP.toPx() / 2f
                fun at(x: Float) = range.start + ((x - inset) / (size.width - 2 * inset)).coerceIn(0f, 1f) * span
                detectTapGestures { tap ->
                    change(at(tap.x))
                    done()
                }
            }
            .pointerInput(enabled, range) {
                if (!enabled) return@pointerInput
                val inset = CAP.toPx() / 2f
                var live = 0f
                detectHorizontalDragGestures(
                    onDragStart = { live = current },
                    onDragEnd = { done() },
                    onDragCancel = { done() },
                ) { input, dx ->
                    input.consume()
                    // Relative drag: the cap follows the finger without jumping to it.
                    live = (live + dx / (size.width - 2 * inset) * span).coerceIn(range.start, range.endInclusive)
                    change(live)
                }
            },
    ) {
        val inset = CAP.toPx() / 2f
        val y = size.height / 2f
        val left = inset
        val right = size.width - inset
        fun x(f: Float) = left + (right - left) * f
        val groove = 6.dp.toPx()
        val dim = if (enabled) 1f else 0.4f
        // Milled groove with a shadowed upper lip.
        drawRoundRect(MColors.Glass, Offset(left - groove / 2, y - groove / 2), Size(right - left + groove, groove), CornerRadius(groove / 2))
        drawLine(MColors.EdgeLight, Offset(left, y + groove / 2), Offset(right, y + groove / 2), strokeWidth = 1.dp.toPx())
        // Lit strip from the resting value to the current one.
        if (fraction != originFraction) {
            drawLine(MColors.Accent.copy(alpha = dim), Offset(x(minOf(originFraction, fraction)), y), Offset(x(maxOf(originFraction, fraction)), y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }
        if (originFraction > 0f && originFraction < 1f) {
            drawLine(MColors.LineStrong, Offset(x(originFraction), y - 7.dp.toPx()), Offset(x(originFraction), y - 4.dp.toPx()), strokeWidth = 1.5.dp.toPx())
        }
        // Metal cap with a grip line.
        val cx = x(fraction)
        val capW = CAP.toPx() * 0.62f
        val capH = CAP.toPx()
        val capTopLeft = Offset(cx - capW / 2, y - capH / 2)
        val capRadius = CornerRadius(5.dp.toPx())
        drawRoundRect(Color(0x80000000), capTopLeft + Offset(0f, 2.dp.toPx()), Size(capW, capH), capRadius)
        drawRoundRect(
            Brush.verticalGradient(listOf(MColors.MetalLight, MColors.MetalMid, MColors.MetalDark), startY = capTopLeft.y, endY = capTopLeft.y + capH),
            capTopLeft, Size(capW, capH), capRadius, alpha = dim,
        )
        drawRoundRect(MColors.EdgeLight, capTopLeft, Size(capW, capH), capRadius, style = Stroke(1.dp.toPx()))
        drawLine(MColors.AccentBright.copy(alpha = dim), Offset(cx, y - capH * 0.28f), Offset(cx, y + capH * 0.28f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    }
}

private val CAP = 26.dp
