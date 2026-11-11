package id.haseda.margaretha.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.annotation.OptIn
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextureOverlay
import id.haseda.margaretha.timeline.FontChoice
import id.haseda.margaretha.timeline.Motion
import id.haseda.margaretha.timeline.OutputSize
import id.haseda.margaretha.timeline.TextAlign
import id.haseda.margaretha.timeline.TextItem
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Draws a title/subtitle into a bitmap. The preview draws this bitmap over the player and the
 * exporter uploads the very same bitmap as a GPU overlay, so both show identical text.
 */
object TextRenderer {
    private const val MAX_WIDTH_FRACTION = 0.9f
    private const val MAX_SIDE = 4096

    fun typeface(font: FontChoice, bold: Boolean, italic: Boolean): Typeface {
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        val family = when (font) {
            FontChoice.SANS -> "sans-serif"
            FontChoice.SERIF -> "serif"
            FontChoice.MONO -> "monospace"
            FontChoice.CONDENSED -> "sans-serif-condensed"
            FontChoice.ROUNDED -> "casual"
        }
        return Typeface.create(family, style)
    }

    /** Renders [item] for a frame of [frameWidth]×[frameHeight] pixels. */
    fun render(item: TextItem, frameWidth: Int, frameHeight: Int): Bitmap {
        val style = item.style
        val textSize = (style.size * frameHeight).coerceAtLeast(4f)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.textSize = textSize
            typeface = typeface(style.font, style.bold, style.italic)
            color = style.color.toInt()
        }
        val maxWidth = (frameWidth * MAX_WIDTH_FRACTION).roundToInt().coerceAtLeast(16)
        val alignment = when (style.align) {
            TextAlign.LEFT -> Layout.Alignment.ALIGN_NORMAL
            TextAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
            TextAlign.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        }
        val wanted = item.text.lines().maxOfOrNull { ceil(paint.measureText(it)).toInt() } ?: 0
        val layoutWidth = wanted.coerceIn(1, maxWidth)
        fun layout(p: TextPaint) = StaticLayout.Builder.obtain(item.text, 0, item.text.length, p, layoutWidth)
            .setAlignment(alignment)
            .setIncludePad(false)
            .build()

        val fill = layout(paint)
        val stroke = style.strokeWidth * textSize
        val shadowRadius = style.shadow * textSize * 0.12f
        val hasPlate = (style.backgroundColor ushr 24) != 0L
        val pad = ceil(maxOf(stroke, shadowRadius * 2f, if (hasPlate) textSize * 0.35f else 0f) + 2f).toInt()
        val width = (fill.width + pad * 2).coerceIn(1, MAX_SIDE)
        val height = (fill.height + pad * 2).coerceIn(1, MAX_SIDE)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (hasPlate) {
            val plate = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = style.backgroundColor.toInt() }
            val r = textSize * 0.25f
            canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), r, r, plate)
        }
        canvas.translate(pad.toFloat(), pad.toFloat())
        if (stroke > 0f) {
            val outline = TextPaint(paint).apply {
                this.style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeJoin = Paint.Join.ROUND
                color = style.strokeColor.toInt()
                if (shadowRadius > 0f) setShadowLayer(shadowRadius, 0f, shadowRadius * 0.5f, 0xCC000000.toInt())
            }
            layout(outline).draw(canvas)
        } else if (shadowRadius > 0f) {
            paint.setShadowLayer(shadowRadius, 0f, shadowRadius * 0.5f, 0xCC000000.toInt())
        }
        fill.draw(canvas)
        return bitmap
    }
}

/**
 * Burns the project's texts into exported frames. Texts are grouped into layers of
 * non-overlapping items; each layer is a single GPU overlay that swaps its bitmap when the
 * active text changes, so hundreds of subtitle cues cost one texture, not hundreds.
 */
@OptIn(UnstableApi::class)
object TextOverlays {
    /**
     * Overlay effect for one exported item that covers [itemStartUs, itemStartUs + itemDurationUs)
     * of the timeline, or null when no text is visible during it.
     */
    fun effect(layers: List<List<TextItem>>, itemStartUs: Long, itemDurationUs: Long, size: OutputSize, clock: ClipTime): OverlayEffect? {
        val end = itemStartUs + itemDurationUs
        val overlays: List<TextureOverlay> = layers.mapNotNull { layer ->
            val visible = layer.filter { it.startUs < end && it.endUs > itemStartUs }
            if (visible.isEmpty()) null else TextLayerOverlay(visible, itemStartUs, size, clock)
        }
        return if (overlays.isEmpty()) null else OverlayEffect(overlays)
    }
}

@OptIn(UnstableApi::class)
private class TextLayerOverlay(
    private val items: List<TextItem>,
    private val itemStartUs: Long,
    private val size: OutputSize,
    private val clock: ClipTime,
) : BitmapOverlay() {
    private val blank: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    private var currentId: String? = null
    private var current: Bitmap = blank

    private fun active(presentationTimeUs: Long): TextItem? {
        val t = itemStartUs + clock.offsetUs(presentationTimeUs)
        return items.firstOrNull { it.containsUs(t) }
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val item = active(presentationTimeUs)
        if (item?.id != currentId) {
            // The previous bitmap is left to the garbage collector: the overlay may still compare against it.
            current = if (item == null) blank else TextRenderer.render(item, size.width, size.height)
            currentId = item?.id
        }
        return current
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val item = active(presentationTimeUs) ?: return StaticOverlaySettings.Builder().setAlphaScale(0f).build()
        val pose = Motion.textPose(item, itemStartUs + clock.offsetUs(presentationTimeUs))
        // Frame anchors are -1..1 with y pointing up; the model uses 0..1 with y pointing down.
        val x = (item.x * 2f - 1f).coerceIn(-1f, 1f)
        val y = (1f - (item.y + pose.offsetY) * 2f).coerceIn(-1f, 1f)
        return StaticOverlaySettings.Builder()
            .setBackgroundFrameAnchor(x, y)
            .setScale(pose.scale, pose.scale)
            // The overlay API rotates counter-clockwise; the model stores clockwise degrees.
            .setRotationDegrees(-item.rotation)
            .setAlphaScale(pose.alpha * item.opacity)
            .build()
    }
}
