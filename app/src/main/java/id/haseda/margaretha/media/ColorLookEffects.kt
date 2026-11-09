package id.haseda.margaretha.media

import androidx.media3.common.Effect
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.RgbFilter
import id.haseda.margaretha.timeline.ColorLook

/** Shared GPU look stack for preview and final render. */
fun colorEffects(look: ColorLook): List<Effect> {
    if (look == ColorLook.ORIGINAL) return emptyList()
    return buildList {
        if (look.monochrome) add(RgbFilter.createGrayscaleFilter())
        add(
            HslAdjustment.Builder()
                .adjustHue(look.hue)
                .adjustSaturation(look.saturation)
                .adjustLightness(look.lightness)
                .build(),
        )
        if (look.contrast != 0f) add(Contrast(look.contrast))
    }
}
