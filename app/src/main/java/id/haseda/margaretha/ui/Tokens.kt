package id.haseda.margaretha.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Margaretha studio palette — tuned in OKLCH, shipped as static sRGB.
 * Graphite surfaces carry a faint cool tint (shadows lean blue, ~h 265), while light and the
 * single accent lean warm (~h 50). One accent only; semantic colours are always paired with text.
 */
object MColors {
    val Abyss = Color(0xFF0B0C10)       // L .14
    val Base = Color(0xFF111318)        // L .18 app background
    val Surface1 = Color(0xFF171A21)    // L .21 panels
    val Surface2 = Color(0xFF1E222B)    // L .25 raised
    val Surface3 = Color(0xFF282D38)    // L .30 controls
    val Line = Color(0xFF2F3542)
    val LineStrong = Color(0xFF465063)
    val Text = Color(0xFFF3EFEA)        // warm white, L .95
    val TextMuted = Color(0xFFA7ADBB)   // 8.1:1 on Base
    val TextFaint = Color(0xFF878EA0)   // 5.7:1 on Base, 4.9:1 on Surface2 (panels and dialogs)
    val Accent = Color(0xFFE8A27A)      // "Margaretha amber-rose" L .77 C .10 h 50
    val AccentBright = Color(0xFFF6C3A2)
    val AccentDeep = Color(0xFF9C5B3D)
    val OnAccent = Color(0xFF2A160C)
    val Playhead = Color(0xFFFFE6D3)
    val ClipBody = Color(0xFF2A3040)
    val Danger = Color(0xFFEB6F75)
    val Success = Color(0xFF6FCB9C)
    val Warning = Color(0xFFE9C46A)

    // Timeline rows: one hue per kind of content, all at the same lightness so none shouts.
    val TrackText = Color(0xFFE8A27A)
    val TrackSubtitle = Color(0xFFB9A6F0)
    val TrackMusic = Color(0xFF6FCBB8)
    val TrackVoice = Color(0xFF7FB2F0)

    // Materials. Light falls from the top-left: faces brighten upward, edges catch a thin highlight.
    val MetalLight = Color(0xFF9299A8)      // satin aluminium, lit side
    val MetalMid = Color(0xFF555C6B)
    val MetalDark = Color(0xFF2A2E38)       // satin aluminium, shaded side
    val Glass = Color(0xFF07080B)           // smoked glass behind read-outs
    val EdgeLight = Color(0x24FFFFFF)       // chamfer highlight
    val EdgeDark = Color(0x8C000000)        // contact shadow
    val Led = Color(0xFFFFB86B)             // lit indicator, warmer than the accent
}

val BrandSerif = FontFamily.Serif

/** Tabular figures keep timecodes from jittering while they update. */
val TimecodeStyle = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, fontFeatureSettings = "tnum", letterSpacing = 0.2.sp)

