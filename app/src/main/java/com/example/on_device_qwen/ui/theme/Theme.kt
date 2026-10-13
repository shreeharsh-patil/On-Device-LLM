package com.example.on_device_qwen.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// ── ChatGPT-Inspired Minimalist Palette ──────────────────────────────────────

val ChatGptGreen       = Color(0xFF10A37F)
val QwenPrimary        = Color(0xFF10A37F)
val QwenPrimaryDark    = Color(0xFF0E8C6D)
val QwenSecondary      = Color(0xFF19C37D)
val QwenError          = Color(0xFFEF4444)

// Neutral Dark Canvas (inspired by ChatGPT dark mode)
val DarkBackground     = Color(0xFF171717)
val DarkSurface        = Color(0xFF212121)
val DarkSurfaceVariant = Color(0xFF2F2F2F)
val DarkTextPrimary    = Color(0xFFECECEC)
val DarkTextSecondary  = Color(0xFFB4B4B4)
val DarkUserBubble     = Color(0xFF2F2F2F)

// Neutral Light Canvas (inspired by ChatGPT light mode)
val LightBackground    = Color(0xFFFFFFFF)
val LightSurface       = Color(0xFFF9F9F9)
val LightSurfaceVariant = Color(0xFFF4F4F4)
val LightTextPrimary   = Color(0xFF0D0D0D)
val LightTextSecondary = Color(0xFF707070)
val LightUserBubble    = Color(0xFFF4F4F4)

val QwenUserBubble     = Color(0xFF2F2F2F)
val QwenBotBubble      = Color.Transparent

private val DarkColorScheme = darkColorScheme(
    primary            = QwenPrimary,
    onPrimary          = Color.White,
    secondary          = QwenSecondary,
    onSecondary        = Color.White,
    background         = DarkBackground,
    surface            = DarkSurface,
    surfaceVariant     = DarkSurfaceVariant,
    onBackground       = DarkTextPrimary,
    onSurface          = DarkTextPrimary,
    onSurfaceVariant   = DarkTextSecondary,
    outline            = Color(0xFF383838),
    outlineVariant     = Color(0xFF2B2B2B),
    error              = QwenError,
)

private val LightColorScheme = lightColorScheme(
    primary            = Color(0xFF0D0D0D),
    onPrimary          = Color.White,
    secondary          = QwenPrimary,
    onSecondary        = Color.White,
    background         = LightBackground,
    surface            = LightSurface,
    surfaceVariant     = LightSurfaceVariant,
    onBackground       = LightTextPrimary,
    onSurface          = LightTextPrimary,
    onSurfaceVariant   = LightTextSecondary,
    outline            = Color(0xFFE5E5E5),
    outlineVariant     = Color(0xFFECECEC),
    error              = QwenError,
)

@Composable
fun OnDeviceQwenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme  -> DarkColorScheme
        else       -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography(),
        content     = content,
    )
}
