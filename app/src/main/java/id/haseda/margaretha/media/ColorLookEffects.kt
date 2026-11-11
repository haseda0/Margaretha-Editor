package id.haseda.margaretha.media

import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.RgbFilter
import id.haseda.margaretha.timeline.ColorLook

/** Shared GPU look stack for preview and final render. [intensity] 0..1 blends towards the original. */
@OptIn(UnstableApi::class)
fun colorEffects(look: ColorLook, intensity: Float = 1f): List<Effect> {
    val k = intensity.coerceIn(0f, 1f)
    if (look == ColorLook.ORIGINAL || k <= 0f) return emptyList()
    return buildList {
        // The hard greyscale filter cannot be blended; below full strength the saturation drop does the work.
        if (look.monochrome && k >= 1f) add(RgbFilter.createGrayscaleFilter())
        add(
            HslAdjustment.Builder()
                .adjustHue(look.hue * k)
                .adjustSaturation(look.saturation * k)
                .adjustLightness(look.lightness * k)
                .build(),
        )
        if (look.contrast != 0f) add(Contrast(look.contrast * k))
    }
}
