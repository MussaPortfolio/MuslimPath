package com.muslimpath.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Palette taken from the logo: deep green, gold, cream.
val Green900 = Color(0xFF0B3A27)
val Green800 = Color(0xFF0F4A32)
val Green600 = Color(0xFF2E6B4F)
val Green100 = Color(0xFFDCE9E1)
val Gold = Color(0xFFB98A2E)
val GoldLight = Color(0xFFE2C27A)
val Cream = Color(0xFFFBF6EA)
val CreamDark = Color(0xFFF1E7CF)
val Ink = Color(0xFF1E2622)

private val Light = lightColorScheme(
    primary = Green800,
    onPrimary = Color.White,
    primaryContainer = Green100,
    onPrimaryContainer = Green900,
    secondary = Gold,
    onSecondary = Color.White,
    secondaryContainer = CreamDark,
    onSecondaryContainer = Color(0xFF4A3712),
    tertiary = Green600,
    background = Cream,
    onBackground = Ink,
    surface = Cream,
    onSurface = Ink,
    surfaceVariant = CreamDark,
    onSurfaceVariant = Color(0xFF55605A),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFCF5),
    surfaceContainer = Color(0xFFF6EFDF),
    surfaceContainerHigh = Color(0xFFF1E9D6),
    outline = Color(0xFFB9B29F),
    outlineVariant = Color(0xFFE2D9C3),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8FCBAA),
    onPrimary = Green900,
    primaryContainer = Green800,
    onPrimaryContainer = Green100,
    secondary = GoldLight,
    onSecondary = Color(0xFF3A2A08),
    secondaryContainer = Color(0xFF4A3A1A),
    onSecondaryContainer = Color(0xFFF5E3BC),
    tertiary = Color(0xFFA6D3BB),
    background = Color(0xFF0F1613),
    onBackground = Color(0xFFE6E2D8),
    surface = Color(0xFF0F1613),
    onSurface = Color(0xFFE6E2D8),
    surfaceVariant = Color(0xFF26302B),
    onSurfaceVariant = Color(0xFFC2C9C3),
    surfaceContainerLowest = Color(0xFF0A100D),
    surfaceContainerLow = Color(0xFF151D19),
    surfaceContainer = Color(0xFF19221E),
    surfaceContainerHigh = Color(0xFF212B26),
    outline = Color(0xFF6E7872),
    outlineVariant = Color(0xFF38423D),
)

private val Serif = FontFamily.Serif
private val base = Typography()
private val AppTypography = Typography(
    displaySmall = base.displaySmall.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 26.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
)

/** Arabic text is rendered larger with generous line height for diacritics. */
val ArabicStyle = TextStyle(fontSize = 28.sp, lineHeight = 52.sp)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun MuslimPathTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) Dark else Light,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
