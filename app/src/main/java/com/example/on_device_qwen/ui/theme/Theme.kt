package com.example.on_device_qwen.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Local AI design language: quiet neutral surfaces, high-contrast type, and a
 * single indigo accent. All screens consume the same Material 3 semantic roles.
 */
val ChatGptGreen = Color(0xFF7775EC) // Kept for source compatibility.
val QwenPrimary = Color(0xFF7775EC)
val QwenPrimaryDark = Color(0xFF5B59CE)
val QwenSecondary = Color(0xFF7775EC)
val QwenError = Color(0xFFF27682)

val DarkBackground = Color(0xFF0D1019)
val DarkSurface = Color(0xFF171B28)
val DarkSurfaceVariant = Color(0xFF242A3B)
val DarkTextPrimary = Color(0xFFF3F4FA)
val DarkTextSecondary = Color(0xFFAFB5C8)
val DarkUserBubble = Color(0xFF292E40)

val LightBackground = Color(0xFFF8F9FD)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF0F2F8)
val LightTextPrimary = Color(0xFF171B29)
val LightTextSecondary = Color(0xFF5F687D)
val LightUserBubble = Color(0xFFEDEEFF)

val QwenUserBubble = DarkUserBubble
val QwenBotBubble = Color.Transparent

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFADB5FF),
    onPrimary = Color(0xFF19204B),
    primaryContainer = Color(0xFF343C66),
    onPrimaryContainer = Color(0xFFE8EAFF),
    secondary = Color(0xFFBEC8F6),
    onSecondary = Color(0xFF1B2545),
    secondaryContainer = Color(0xFF333B56),
    onSecondaryContainer = Color(0xFFEEF0FF),
    tertiary = Color(0xFF83D4C5),
    tertiaryContainer = Color(0xFF244943),
    onTertiaryContainer = Color(0xFFCCFFF4),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    surfaceContainer = Color(0xFF1C2231),
    surfaceContainerHigh = Color(0xFF272E41),
    outline = Color(0xFF555D73),
    outlineVariant = Color(0xFF343C50),
    error = QwenError,
    onError = Color(0xFF3D0B13),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF5354CB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5E6FF),
    onPrimaryContainer = Color(0xFF25276B),
    secondary = Color(0xFF555F8F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7EBFA),
    onSecondaryContainer = Color(0xFF293252),
    tertiary = Color(0xFF197969),
    tertiaryContainer = Color(0xFFD2F7ED),
    onTertiaryContainer = Color(0xFF0B5348),
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainer = Color(0xFFF3F4FA),
    surfaceContainerHigh = Color(0xFFE9ECF5),
    outline = Color(0xFF8D95A9),
    outlineVariant = Color(0xFFDDE2EC),
    error = Color(0xFFB42E4A),
    onError = Color.White,
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 27.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 25.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun OnDeviceQwenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
