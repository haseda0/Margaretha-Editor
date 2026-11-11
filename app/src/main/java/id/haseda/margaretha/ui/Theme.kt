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
