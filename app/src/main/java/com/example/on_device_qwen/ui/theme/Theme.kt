package com.example.on_device_qwen.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.on_device_qwen.ui.designsystem.DarkSovereignColors
import com.example.on_device_qwen.ui.designsystem.LightSovereignColors
import com.example.on_device_qwen.ui.designsystem.LocalAdaptiveLayoutInfo
import com.example.on_device_qwen.ui.designsystem.LocalHapticsEnabled
import com.example.on_device_qwen.ui.designsystem.LocalReducedMotion
import com.example.on_device_qwen.ui.designsystem.LocalSovereignColors
import com.example.on_device_qwen.ui.designsystem.LocalSovereignMotion
import com.example.on_device_qwen.ui.designsystem.LocalSovereignShapes
import com.example.on_device_qwen.ui.designsystem.LocalSovereignSpacing
import com.example.on_device_qwen.ui.designsystem.SovereignMotion
import com.example.on_device_qwen.ui.designsystem.SovereignShapes
import com.example.on_device_qwen.ui.designsystem.SovereignSpacing
import com.example.on_device_qwen.ui.designsystem.SovereignTypography
import com.example.on_device_qwen.ui.designsystem.rememberAdaptiveLayoutInfo
import com.example.on_device_qwen.ui.designsystem.rememberReducedMotion

// Legacy token aliases retained temporarily until Stage B components migrate to SovereignTheme.colors.
val ChatGptGreen       = Color(0xFF0369A1)
val QwenPrimary        = Color(0xFF0369A1)
val QwenPrimaryDark    = Color(0xFF083358)
val QwenSecondary      = Color(0xFF38BDF8)
val QwenError          = Color(0xFFB91C1C)

val DarkBackground     = Color(0xFF0B0C0F)
val DarkSurface        = Color(0xFF121419)
val DarkSurfaceVariant = Color(0xFF1E222B)
val DarkTextPrimary    = Color(0xFFF1F2F4)
val DarkTextSecondary  = Color(0xFFA6ACB8)
val DarkUserBubble     = Color(0xFF1E222B)

val LightBackground     = Color(0xFFFAF9F6)
val LightSurface        = Color(0xFFF3F1EC)
val LightSurfaceVariant = Color(0xFFECEAE3)
val LightTextPrimary    = Color(0xFF121316)
val LightTextSecondary  = Color(0xFF4F545F)
val LightUserBubble     = Color(0xFFECEAE3)

val QwenUserBubble     = Color(0xFF1E222B)
val QwenBotBubble      = Color.Transparent

@Composable
fun OnDeviceQwenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    hapticsEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val sovereignColors = if (darkTheme) DarkSovereignColors else LightSovereignColors
    val sovereignSpacing = remember { SovereignSpacing() }
    val sovereignShapes = remember { SovereignShapes() }
    val reducedMotion = rememberReducedMotion()
    val sovereignMotion = remember(reducedMotion) { SovereignMotion(reducedMotion = reducedMotion) }
    val adaptiveLayoutInfo = rememberAdaptiveLayoutInfo()

    val baseScheme = remember(sovereignColors) { sovereignColors.toMaterial3ColorScheme() }
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        val dynamic = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        baseScheme.copy(
            primary = dynamic.primary,
            onPrimary = dynamic.onPrimary,
            primaryContainer = dynamic.primaryContainer,
            onPrimaryContainer = dynamic.onPrimaryContainer,
        )
    } else {
        baseScheme
    }

    CompositionLocalProvider(
        LocalSovereignColors provides sovereignColors,
        LocalSovereignSpacing provides sovereignSpacing,
        LocalSovereignShapes provides sovereignShapes,
        LocalSovereignMotion provides sovereignMotion,
        LocalReducedMotion provides reducedMotion,
        LocalHapticsEnabled provides hapticsEnabled,
        LocalAdaptiveLayoutInfo provides adaptiveLayoutInfo,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SovereignTypography.material3Typography,
            shapes = sovereignShapes.toMaterial3Shapes(),
            content = content,
        )
    }
}
