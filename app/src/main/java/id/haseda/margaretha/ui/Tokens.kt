package id.haseda.margaretha.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Margaretha studio palette v2 — tuned in OKLCH (deep indigo-plum surfaces h≈285-292, warm light h≈55-72), shipped as static sRGB.
 * Graphite surfaces carry a faint cool tint (shadows lean blue, ~h 265), while light and the
 * single accent lean warm (~h 50). One accent only; semantic colours are always paired with text.
 */
object MColors {
    val Abyss = Color(0xFF0C0B1A)       // L .14
    val Base = Color(0xFF141326)        // L .18 app background
    val Surface1 = Color(0xFF1E1C32)    // L .21 panels
    val Surface2 = Color(0xFF29253E)    // L .25 raised
    val Surface3 = Color(0xFF36304E)    // L .30 controls
    val Line = Color(0xFF3F3B59)
    val LineStrong = Color(0xFF5E5B81)
    val Text = Color(0xFFFBF2E8)        // warm white, L .95
    val TextMuted = Color(0xFFB9BBD8)   // 8.1:1 on Base
    val TextFaint = Color(0xFF989CBB)   // 5.7:1 on Base, 4.9:1 on Surface2 (panels and dialogs)
    val Accent = Color(0xFFF7A062)      // "Margaretha amber-rose" L .77 C .10 h 50
    val AccentBright = Color(0xFFFCCDA5)
    val AccentDeep = Color(0xFFA14E2B)
    val OnAccent = Color(0xFF2D1107)
    val Playhead = Color(0xFFFFE7CB)
    val ClipBody = Color(0xFF38345D)
    val Danger = Color(0xFFEF656B)
    val Success = Color(0xFF61D19A)
    val Warning = Color(0xFFF1C961)

    // Timeline rows: one hue per kind of content, all at the same lightness so none shouts.
    val TrackText = Color(0xFFEF9F67)
    val TrackSubtitle = Color(0xFFC4A2ED)
    val TrackMusic = Color(0xFF58CBB4)
    val TrackVoice = Color(0xFF75BBF6)

    // Materials. Light falls from the top-left: faces brighten upward, edges catch a thin highlight.
    val MetalLight = Color(0xFF9C9CB4)      // satin aluminium, lit side
    val MetalMid = Color(0xFF5B5B74)
    val MetalDark = Color(0xFF2D2B41)       // satin aluminium, shaded side
    val Glass = Color(0xFF05050F)           // smoked glass behind read-outs
    val EdgeLight = Color(0x24FFFFFF)       // chamfer highlight
    val EdgeDark = Color(0x8C000000)        // contact shadow
    val Led = Color(0xFFFFB669)             // lit indicator, warmer than the accent
}

val BrandSerif = FontFamily.Serif

/** Tabular figures keep timecodes from jittering while they update. */
val TimecodeStyle = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, fontFeatureSettings = "tnum", letterSpacing = 0.2.sp)

