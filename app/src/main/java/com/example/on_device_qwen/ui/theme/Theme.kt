package com.example.on_device_qwen.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// ── Colour palette ──────────────────────────────────────────────────────────

// Brand colours inspired by Qwen's identity — deep indigo / violet
val QwenPrimary        = Color(0xFF6C63FF)
val QwenPrimaryDark    = Color(0xFF4A42D9)
val QwenSecondary      = Color(0xFF03DAC6)
val QwenBackground     = Color(0xFF0F0F1A)
val QwenSurface        = Color(0xFF1A1A2E)
val QwenSurfaceVariant = Color(0xFF252540)
val QwenOnPrimary      = Color(0xFFFFFFFF)
val QwenOnSurface      = Color(0xFFE0E0F0)
val QwenOnSurfaceVar   = Color(0xFFA0A0C0)
val QwenError          = Color(0xFFFF6B6B)

val QwenUserBubble     = Color(0xFF6C63FF)
val QwenBotBubble      = Color(0xFF252540)

private val DarkColorScheme = darkColorScheme(
    primary            = QwenPrimary,
    onPrimary          = QwenOnPrimary,
    secondary          = QwenSecondary,
    background         = QwenBackground,
    surface            = QwenSurface,
    surfaceVariant     = QwenSurfaceVariant,
    onBackground       = QwenOnSurface,
    onSurface          = QwenOnSurface,
    onSurfaceVariant   = QwenOnSurfaceVar,
    error              = QwenError,
)

private val LightColorScheme = lightColorScheme(
    primary            = QwenPrimaryDark,
    onPrimary          = Color.White,
    secondary          = QwenSecondary,
    background         = Color(0xFFF5F5FF),
    surface            = Color.White,
    surfaceVariant     = Color(0xFFEEEEFF),
    onBackground       = Color(0xFF1A1A2E),
    onSurface          = Color(0xFF1A1A2E),
    onSurfaceVariant   = Color(0xFF666680),
    error              = QwenError,
)

@Composable
fun OnDeviceQwenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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
