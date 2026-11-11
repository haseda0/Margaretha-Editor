package id.haseda.margaretha.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    val TextFaint = Color(0xFF7A8193)   // 4.6:1 on Base
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
}

private val scheme = darkColorScheme(
    primary = MColors.Accent,
    onPrimary = MColors.OnAccent,
    primaryContainer = MColors.AccentDeep,
    onPrimaryContainer = MColors.Text,
    secondary = MColors.TextMuted,
    onSecondary = MColors.Base,
    background = MColors.Base,
    onBackground = MColors.Text,
    surface = MColors.Surface1,
    onSurface = MColors.Text,
    surfaceVariant = MColors.Surface2,
    onSurfaceVariant = MColors.TextMuted,
    surfaceContainer = MColors.Surface1,
    surfaceContainerHigh = MColors.Surface2,
    surfaceContainerHighest = MColors.Surface3,
    surfaceContainerLow = MColors.Base,
    surfaceContainerLowest = MColors.Abyss,
    outline = MColors.LineStrong,
    outlineVariant = MColors.Line,
    error = MColors.Danger,
    onError = MColors.Abyss,
    scrim = Color(0xCC050608),
)

val BrandSerif = FontFamily.Serif

private val typography = Typography(
    displaySmall = TextStyle(fontFamily = BrandSerif, fontWeight = FontWeight.Normal, fontSize = 34.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontFamily = BrandSerif, fontWeight = FontWeight.Normal, fontSize = 24.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.4.sp),
)

/** Tabular figures keep timecodes from jittering while they update. */
val TimecodeStyle = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, fontFeatureSettings = "tnum", letterSpacing = 0.2.sp)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun MargarethaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
}
