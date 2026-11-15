package com.example.on_device_qwen.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * On-Device's original visual identity: calm neutrals, a precise periwinkle accent
 * and generous, consistent typography. All screens use MaterialTheme tokens so
 * contrast remains correct in both system themes.
 */
val ChatGptGreen = Color(0xFF6578D6) // Legacy compatibility: prefer MaterialTheme colors.
val QwenPrimary = Color(0xFF6578D6)
val QwenPrimaryDark = Color(0xFF465CC6)
val QwenSecondary = Color(0xFF8F9CE4)
val QwenError = Color(0xFFE76B72)

val DarkBackground = Color(0xFF10131A)
val DarkSurface = Color(0xFF191D27)
val DarkSurfaceVariant = Color(0xFF242A36)
val DarkTextPrimary = Color(0xFFF2F3F8)
val DarkTextSecondary = Color(0xFFB0B8C9)
val DarkUserBubble = Color(0xFF252C3B)

val LightBackground = Color(0xFFF7F8FC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFECF0F7)
val LightTextPrimary = Color(0xFF1A2233)
val LightTextSecondary = Color(0xFF5B6578)
val LightUserBubble = Color(0xFFECF0F7)

val QwenUserBubble = DarkUserBubble
val QwenBotBubble = Color.Transparent

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFBDC8FF),
    onPrimary = Color(0xFF202E70),
    primaryContainer = Color(0xFF343F72),
    onPrimaryContainer = Color(0xFFE0E6FF),
    secondary = Color(0xFFB6C2D9),
    onSecondary = Color(0xFF243044),
    tertiary = Color(0xFFC6BCEB),
    onTertiaryContainer = Color(0xFFEAE3FF),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = Color(0xFF657086),
    outlineVariant = Color(0xFF394251),
    error = Color(0xFFFFB4B8),
    onError = Color(0xFF68000E)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF465CC6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDFE5FF),
    onPrimaryContainer = Color(0xFF223579),
    secondary = Color(0xFF53617A),
    onSecondary = Color.White,
    tertiary = Color(0xFF665E91),
    onTertiaryContainer = Color(0xFF302957),
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = Color(0xFF747F94),
    outlineVariant = Color(0xFFD5DAE5),
    error = Color(0xFFB32636),
    onError = Color.White
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-1.0).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.8).sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 27.sp, lineHeight = 34.sp, letterSpacing = (-0.6).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.4).sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 17.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun OnDeviceQwenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
