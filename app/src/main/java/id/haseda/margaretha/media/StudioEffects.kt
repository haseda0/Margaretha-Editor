package id.haseda.margaretha.media

import android.content.Context
import android.graphics.Matrix
import android.opengl.GLES20
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.Crop
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.Presentation
import id.haseda.margaretha.timeline.EffectStructure
import id.haseda.margaretha.timeline.MediaKind
import id.haseda.margaretha.timeline.Motion
import id.haseda.margaretha.timeline.RenderSegment
import id.haseda.margaretha.timeline.TransitionKind
import java.io.IOException

/** Tells an effect how far into its clip the frame being drawn is (microseconds). */
fun interface ClipTime {
    fun offsetUs(presentationTimeUs: Long): Long
}

/**
 * Export clock: measures time from the first frame of the item, so it does not depend on which
 * timestamp origin the frame processor uses for that item.
 */
class FrameClock : ClipTime {
    private var first = Long.MIN_VALUE

    override fun offsetUs(presentationTimeUs: Long): Long {
        if (first == Long.MIN_VALUE) first = presentationTimeUs
        return (presentationTimeUs - first).coerceAtLeast(0L)
    }
}

/** The same media item for preview (ExoPlayer) and export (Transformer). */
@OptIn(UnstableApi::class)
fun RenderSegment.toMediaItem(): MediaItem {
    val builder = MediaItem.Builder().setUri(uri).setMediaId(clipId)
    if (kind == MediaKind.IMAGE) {
        builder.setImageDurationMs(endMs - startMs)
        if (mimeType.isNotEmpty()) builder.setMimeType(mimeType)
    } else {
        builder.setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs).setEndPositionMs(endMs).build(),
        )
    }
    return builder.build()
}

/**
 * Builds the GPU effect chain of one clip. Preview and export call the same code; only the clock
 * and the final presentation differ. Order matters: colour work happens on the source picture,
 * then the picture is fitted to the canvas, then it is moved/scaled inside the canvas.
 */
@OptIn(UnstableApi::class)
object StudioEffects {
    /**
     * @param segment read on every frame, so slider changes show without rebuilding the chain.
     * @param presentation fits the picture to the canvas; required whenever the chain moves the picture.
     */
    fun build(structure: EffectStructure, segment: () -> RenderSegment, canvasAspect: Float, time: ClipTime, presentation: Presentation?): List<Effect> = buildList {
        val c = structure.crop
        if (!c.isNone) add(Crop(-1f + 2f * c.left, 1f - 2f * c.right, -1f + 2f * c.bottom, 1f - 2f * c.top))
        addAll(colorEffects(structure.look, structure.lookIntensity))
        if (structure.grade) add(GradeEffect(segment, time))
        if (structure.blur) {
            add(BlurEffect(segment, time, horizontal = true))
            add(BlurEffect(segment, time, horizontal = false))
        }
        if (presentation != null) add(presentation)
        if (structure.matrix) add(TransformEffect(segment, canvasAspect, time))
    }
}

private const val VERTEX = "shaders/studio_vertex.glsl"

@OptIn(UnstableApi::class)
private class StudioShaderProgram(
    context: Context,
    fragmentAsset: String,
    useHdr: Boolean,
    private val bind: (program: GlProgram, width: Int, height: Int, presentationTimeUs: Long) -> Unit,
) : BaseGlShaderProgram(useHdr, 1) {
    private val program: GlProgram = try {
        GlProgram(context, VERTEX, fragmentAsset).apply {
            setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
        }
    } catch (e: IOException) {
        throw VideoFrameProcessingException(e)
    } catch (e: GlUtil.GlException) {
        throw VideoFrameProcessingException(e)
    }
    private var width = 1
    private var height = 1

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        width = inputWidth
        height = inputHeight
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.setFloatsUniform("uTexel", floatArrayOf(1f / width, 1f / height))
            bind(program, width, height, presentationTimeUs)
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        try {
            program.delete()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }
}

/** Colour grade, sharpen, vignette, grain, opacity and the fade/wipe part of transitions. */
@OptIn(UnstableApi::class)
private class GradeEffect(private val segment: () -> RenderSegment, private val time: ClipTime) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        StudioShaderProgram(context, "shaders/studio_grade_fragment.glsl", useHdr) { p, _, _, pts ->
            val s = segment()
            val g = s.grade
            val offsetUs = time.offsetUs(pts)
            val state = Motion.transition(s, offsetUs)
            val fades = state.kind == TransitionKind.FADE || state.kind == TransitionKind.BLUR || state.kind == TransitionKind.ZOOM
            p.setFloatUniform("uExposure", g.exposure)
            p.setFloatUniform("uBrightness", g.brightness)
            p.setFloatUniform("uContrast", g.contrast)
            p.setFloatUniform("uSaturation", g.saturation)
            p.setFloatUniform("uTemperature", g.temperature)
            p.setFloatUniform("uTint", g.tint)
            p.setFloatUniform("uHighlights", g.highlights)
            p.setFloatUniform("uShadows", g.shadows)
            p.setFloatUniform("uWhites", g.whites)
            p.setFloatUniform("uBlacks", g.blacks)
            p.setFloatUniform("uSharpen", s.fx.sharpen)
            p.setFloatUniform("uVignette", s.fx.vignette)
            p.setFloatUniform("uGrain", s.fx.grain)
            p.setFloatUniform("uSeed", (pts / 1000L % 997L).toFloat())
            p.setFloatUniform("uFade", if (fades) state.visibility else 1f)
            p.setFloatUniform("uWipe", if (state.kind == TransitionKind.WIPE) state.visibility else 1f)
            p.setFloatUniform("uWipeExit", if (state.entering) 0f else 1f)
            p.setFloatUniform("uOpacity", s.transformAt(offsetUs).opacity)
        }
}

/** One pass of the separable blur; strength combines the blur effect and a blur transition. */
@OptIn(UnstableApi::class)
private class BlurEffect(private val segment: () -> RenderSegment, private val time: ClipTime, private val horizontal: Boolean) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        StudioShaderProgram(context, "shaders/studio_blur_fragment.glsl", useHdr) { p, w, h, pts ->
            val s = segment()
            val state = Motion.transition(s, time.offsetUs(pts))
            val transition = if (state.kind == TransitionKind.BLUR) 1f - state.visibility else 0f
            val shortSide = minOf(w, h)
            p.setFloatsUniform("uDirection", if (horizontal) floatArrayOf(1f, 0f) else floatArrayOf(0f, 1f))
            p.setFloatUniform("uRadius", (s.fx.blur * 0.03f + transition * 0.04f) * shortSide)
        }
}

/** Scale, rotation, position and flip inside the canvas, plus slide/zoom transitions. */
@OptIn(UnstableApi::class)
private class TransformEffect(
    private val segment: () -> RenderSegment,
    private val canvasAspect: Float,
    private val time: ClipTime,
) : MatrixTransformation {
    private val matrix = Matrix()

    override fun getMatrix(presentationTimeUs: Long): Matrix {
        val s = segment()
        val offsetUs = time.offsetUs(presentationTimeUs)
        val t = s.transformAt(offsetUs)
        val state = Motion.transition(s, offsetUs)
        val hidden = 1f - state.visibility
        var scale = t.scale
        // Normalised device coordinates: the canvas spans -1..1 and y points up.
        var dx = t.offsetX * 2f
        val dy = -t.offsetY * 2f
        when (state.kind) {
            TransitionKind.SLIDE -> dx += if (state.entering) hidden * 2f else -hidden * 2f
            TransitionKind.ZOOM -> scale *= if (state.entering) 1f - 0.4f * hidden else 1f + 0.4f * hidden
            else -> Unit
        }
        matrix.reset()
        matrix.setScale(if (t.flipH) -scale else scale, if (t.flipV) -scale else scale)
        // Rotate in square pixels, otherwise a non-square canvas would shear the picture.
        matrix.postScale(canvasAspect, 1f)
        matrix.postRotate(-t.rotation)
        matrix.postScale(1f / canvasAspect, 1f)
        matrix.postTranslate(dx, dy)
        return matrix
    }
}
