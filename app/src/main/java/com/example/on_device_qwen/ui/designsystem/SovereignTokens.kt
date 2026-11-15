package com.example.on_device_qwen.ui.designsystem

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.on_device_qwen.R

// ─────────────────────────────────────────────────────────────────────────────
// Color Palette — "Obsidian & Vellum — Sovereign Silicon"
// ─────────────────────────────────────────────────────────────────────────────

@Immutable
data class SovereignColors(
    val isDark: Boolean,
    val bgCanvas: Color,
    val surfaceLow: Color,
    val surfaceBase: Color,
    val surfaceHigh: Color,
    val surfaceHighest: Color,
    val userBubble: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val hairlineSubtle: Color,
    val hairlineStrong: Color,
    val accentPrimary: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val ctaInk: Color,
    val onCtaInk: Color,
    val llamaViolet: Color,
    val llamaVioletContainer: Color,
    val onLlamaVioletContainer: Color,
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val amber: Color,
    val amberContainer: Color,
    val onAmberContainer: Color,
    val error: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val codeBackground: Color,
    val codeHeader: Color,
    val scrim: Color,
) {
    /**
     * Maps every Material 3 [ColorScheme] token explicitly so no unmapped slot
     * ever falls back to M3 default purple.
     */
    fun toMaterial3ColorScheme(): ColorScheme = if (isDark) {
        darkColorScheme(
            primary = accentPrimary,
            onPrimary = onAccent,
            primaryContainer = accentContainer,
            onPrimaryContainer = onAccentContainer,
            inversePrimary = Color(0xFF0369A1),
            secondary = ctaInk,
            onSecondary = onCtaInk,
            secondaryContainer = surfaceBase,
            onSecondaryContainer = textPrimary,
            tertiary = llamaViolet,
            onTertiary = Color(0xFF140A26),
            tertiaryContainer = llamaVioletContainer,
            onTertiaryContainer = onLlamaVioletContainer,
            background = bgCanvas,
            onBackground = textPrimary,
            surface = surfaceBase,
            onSurface = textPrimary,
            surfaceVariant = surfaceBase,
            onSurfaceVariant = textSecondary,
            surfaceTint = Color.Transparent,
            inverseSurface = Color(0xFFF1F2F4),
            inverseOnSurface = Color(0xFF0B0C0F),
            error = error,
            onError = Color(0xFF28080A),
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            outline = hairlineStrong,
            outlineVariant = hairlineSubtle,
            scrim = scrim,
            surfaceBright = surfaceHigh,
            surfaceDim = Color(0xFF08090B),
            surfaceContainerLowest = bgCanvas,
            surfaceContainerLow = surfaceLow,
            surfaceContainer = surfaceBase,
            surfaceContainerHigh = surfaceHigh,
            surfaceContainerHighest = surfaceHighest,
        )
    } else {
        lightColorScheme(
            primary = accentPrimary,
            onPrimary = onAccent,
            primaryContainer = accentContainer,
            onPrimaryContainer = onAccentContainer,
            inversePrimary = Color(0xFF38BDF8),
            secondary = ctaInk,
            onSecondary = onCtaInk,
            secondaryContainer = surfaceBase,
            onSecondaryContainer = textPrimary,
            tertiary = llamaViolet,
            onTertiary = Color.White,
            tertiaryContainer = llamaVioletContainer,
            onTertiaryContainer = onLlamaVioletContainer,
            background = bgCanvas,
            onBackground = textPrimary,
            surface = surfaceBase,
            onSurface = textPrimary,
            surfaceVariant = surfaceBase,
            onSurfaceVariant = textSecondary,
            surfaceTint = Color.Transparent,
            inverseSurface = Color(0xFF191C23),
            inverseOnSurface = Color(0xFFF1F2F4),
            error = error,
            onError = Color.White,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            outline = hairlineStrong,
            outlineVariant = hairlineSubtle,
            scrim = scrim,
            surfaceBright = surfaceHigh,
            surfaceDim = Color(0xFFEAE7DF),
            surfaceContainerLowest = surfaceHigh,
            surfaceContainerLow = surfaceLow,
            surfaceContainer = surfaceBase,
            surfaceContainerHigh = surfaceHigh,
            surfaceContainerHighest = surfaceHighest,
        )
    }
}

val LightSovereignColors = SovereignColors(
    isDark = false,
    bgCanvas = Color(0xFFFAF9F6),
    surfaceLow = Color(0xFFF3F1EC),
    surfaceBase = Color(0xFFECEAE3),
    surfaceHigh = Color(0xFFFFFFFF),
    surfaceHighest = Color(0xFFE4E1D8),
    userBubble = Color(0xFFECEAE3),
    textPrimary = Color(0xFF121316),
    textSecondary = Color(0xFF4F545F),
    textMuted = Color(0xFF616774),
    hairlineSubtle = Color(0x14121316),
    hairlineStrong = Color(0x29121316),
    accentPrimary = Color(0xFF0369A1),
    onAccent = Color(0xFFFFFFFF),
    accentContainer = Color(0xFFE0F2FE),
    onAccentContainer = Color(0xFF083358),
    ctaInk = Color(0xFF121316),
    onCtaInk = Color(0xFFFAF9F6),
    llamaViolet = Color(0xFF6D28D9),
    llamaVioletContainer = Color(0xFFEDE9FE),
    onLlamaVioletContainer = Color(0xFF4C1D95),
    success = Color(0xFF047857),
    successContainer = Color(0xFFD1FAE5),
    onSuccessContainer = Color(0xFF064E3B),
    amber = Color(0xFFB45309),
    amberContainer = Color(0xFFFEF3C7),
    onAmberContainer = Color(0xFF78350F),
    error = Color(0xFFDC2626),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    codeBackground = Color(0xFFF3F1EC),
    codeHeader = Color(0xFFFFFFFF),
    scrim = Color(0x66121316),
)

val DarkSovereignColors = SovereignColors(
    isDark = true,
    bgCanvas = Color(0xFF0B0C0F),
    surfaceLow = Color(0xFF121419),
    surfaceBase = Color(0xFF191C23),
    surfaceHigh = Color(0xFF1E222B),
    surfaceHighest = Color(0xFF262B36),
    userBubble = Color(0xFF1E222B),
    textPrimary = Color(0xFFF1F2F4),
    textSecondary = Color(0xFFA6ACB8),
    textMuted = Color(0xFF868E9E),
    hairlineSubtle = Color(0x14FFFFFF),
    hairlineStrong = Color(0x24FFFFFF),
    accentPrimary = Color(0xFF38BDF8),
    onAccent = Color(0xFF06151E),
    accentContainer = Color(0xFF13283D),
    onAccentContainer = Color(0xFFCFE8FF),
    ctaInk = Color(0xFFF1F2F4),
    onCtaInk = Color(0xFF0B0C0F),
    llamaViolet = Color(0xFFA78BFA),
    llamaVioletContainer = Color(0xFF241A3D),
    onLlamaVioletContainer = Color(0xFFDDD6FE),
    success = Color(0xFF34D399),
    successContainer = Color(0xFF0D2920),
    onSuccessContainer = Color(0xFFA7F3D0),
    amber = Color(0xFFFBBF24),
    amberContainer = Color(0xFF2D210B),
    onAmberContainer = Color(0xFFFDE68A),
    error = Color(0xFFF87171),
    errorContainer = Color(0xFF331518),
    onErrorContainer = Color(0xFFFECACA),
    codeBackground = Color(0xFF121419),
    codeHeader = Color(0xFF1E222B),
    scrim = Color(0x99000000),
)

// ─────────────────────────────────────────────────────────────────────────────
// Offline Bundled Typography — Inter + Instrument Serif + JetBrains Mono (tnum)
// ─────────────────────────────────────────────────────────────────────────────

object SovereignTypography {

    val InterFontFamily: FontFamily = FontFamily(
        Font(
            resId = R.font.inter_variable,
            weight = FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(400)),
        ),
        Font(
            resId = R.font.inter_variable,
            weight = FontWeight.Medium,
            variationSettings = FontVariation.Settings(FontVariation.weight(500)),
        ),
        Font(
            resId = R.font.inter_variable,
            weight = FontWeight.SemiBold,
            variationSettings = FontVariation.Settings(FontVariation.weight(600)),
        ),
        Font(
            resId = R.font.inter_variable,
            weight = FontWeight.Bold,
            variationSettings = FontVariation.Settings(FontVariation.weight(700)),
        ),
    )

    val EditorialSerifFontFamily: FontFamily = FontFamily(
        Font(
            resId = R.font.instrument_serif_regular,
            weight = FontWeight.Normal,
        ),
    )

    val JetBrainsMonoFontFamily: FontFamily = FontFamily(
        Font(
            resId = R.font.jetbrains_mono_variable,
            weight = FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(400)),
        ),
        Font(
            resId = R.font.jetbrains_mono_variable,
            weight = FontWeight.Medium,
            variationSettings = FontVariation.Settings(FontVariation.weight(500)),
        ),
        Font(
            resId = R.font.jetbrains_mono_variable,
            weight = FontWeight.SemiBold,
            variationSettings = FontVariation.Settings(FontVariation.weight(600)),
        ),
    )

    val editorialGreeting = TextStyle(
        fontFamily = EditorialSerifFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.3).sp,
    )

    val sheetHeader = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
    )

    val cardTitle = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.1).sp,
    )

    val pillTitle = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    )

    val chatProse = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.5.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp,
    )

    val bodyCompact = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    )

    val thinkingBody = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.1.sp,
    )

    val actionLabel = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    )

    val codeBody = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum",
    )

    val telemetry = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum",
    )

    val monoTag = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.3.sp,
        fontFeatureSettings = "tnum",
    )

    val material3Typography: Typography = Typography(
        displayLarge = editorialGreeting.copy(fontSize = 36.sp, lineHeight = 42.sp),
        displayMedium = editorialGreeting.copy(fontSize = 32.sp, lineHeight = 38.sp),
        displaySmall = editorialGreeting,
        headlineLarge = editorialGreeting.copy(fontSize = 30.sp, lineHeight = 36.sp),
        headlineMedium = editorialGreeting,
        headlineSmall = sheetHeader.copy(fontSize = 22.sp, lineHeight = 28.sp),
        titleLarge = sheetHeader,
        titleMedium = cardTitle,
        titleSmall = pillTitle,
        bodyLarge = chatProse,
        bodyMedium = bodyCompact,
        bodySmall = thinkingBody.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
        labelLarge = actionLabel,
        labelMedium = telemetry,
        labelSmall = monoTag,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Spacing, Shapes, Elevation
// ─────────────────────────────────────────────────────────────────────────────

@Immutable
data class SovereignSpacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val touchTargetMin: Dp = 48.dp,
) {
    fun screenGutter(adaptive: AdaptiveLayoutInfo): Dp =
        if (adaptive.isCompact) lg else xl
}

@Immutable
data class SovereignShapes(
    val xs: CornerBasedShape = RoundedCornerShape(6.dp),
    val sm: CornerBasedShape = RoundedCornerShape(10.dp),
    val md: CornerBasedShape = RoundedCornerShape(14.dp),
    val lg: CornerBasedShape = RoundedCornerShape(20.dp),
    val xl: CornerBasedShape = RoundedCornerShape(26.dp),
    val pill: CornerBasedShape = RoundedCornerShape(50),
    val userBubble: CornerBasedShape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomEnd = 6.dp,
        bottomStart = 20.dp,
    ),
) {
    fun toMaterial3Shapes(): Shapes = Shapes(
        extraSmall = xs,
        small = sm,
        medium = md,
        large = lg,
        extraLarge = xl,
    )
}

object SovereignElevation {
    val none: Dp = 0.dp
    val hairlineWidth: Dp = 1.dp
    val floatingOverlay: Dp = 6.dp
}

// ─────────────────────────────────────────────────────────────────────────────
// Motion Tokens & Reduced Motion Support
// ─────────────────────────────────────────────────────────────────────────────

object SovereignMotionDefaults {
    const val PulseDurationMs: Int = 1400
    const val EffectsDurationMs: Int = 150

    fun <T> spatialFast(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 500f,
    )

    fun <T> spatialExpressive(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.78f,
        stiffness = 420f,
    )

    fun <T> effectsQuick(): FiniteAnimationSpec<T> = tween(
        durationMillis = EffectsDurationMs,
        easing = FastOutSlowInEasing,
    )
}

@Immutable
data class SovereignMotion(
    val reducedMotion: Boolean = false,
    val pulseDurationMs: Int = SovereignMotionDefaults.PulseDurationMs,
) {
    val SpatialFast: FiniteAnimationSpec<Float>
        get() = spatialFast()

    val SpatialExpressive: FiniteAnimationSpec<Float>
        get() = spatialExpressive()

    val EffectsQuick: FiniteAnimationSpec<Float>
        get() = effectsQuick()

    fun <T> spatialFast(): FiniteAnimationSpec<T> =
        if (reducedMotion) snap() else SovereignMotionDefaults.spatialFast()

    fun <T> spatialExpressive(): FiniteAnimationSpec<T> =
        if (reducedMotion) snap() else SovereignMotionDefaults.spatialExpressive()

    fun <T> effectsQuick(): FiniteAnimationSpec<T> =
        if (reducedMotion) tween(durationMillis = 80) else SovereignMotionDefaults.effectsQuick()

    companion object {
        const val PulseDurationMs: Int = SovereignMotionDefaults.PulseDurationMs
        val SpatialFast: FiniteAnimationSpec<Float> = SovereignMotionDefaults.spatialFast()
        val SpatialExpressive: FiniteAnimationSpec<Float> = SovereignMotionDefaults.spatialExpressive()
        val EffectsQuick: FiniteAnimationSpec<Float> = SovereignMotionDefaults.effectsQuick()
    }
}

@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    fun readScale(): Boolean = try {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    } catch (_: Exception) {
        false
    }

    var reduced by remember { mutableStateOf(readScale()) }

    DisposableEffect(context) {
        val resolver = context.contentResolver
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = readScale()
            }
        }
        try {
            resolver.registerContentObserver(uri, false, observer)
        } catch (_: Exception) {
        }
        onDispose {
            try {
                resolver.unregisterContentObserver(observer)
            } catch (_: Exception) {
            }
        }
    }
    return reduced
}

// ─────────────────────────────────────────────────────────────────────────────
// Adaptive Layout Info
// ─────────────────────────────────────────────────────────────────────────────

enum class WindowWidthClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

@Immutable
data class AdaptiveLayoutInfo(
    val widthClass: WindowWidthClass,
    val windowWidthDp: Dp,
    val windowHeightDp: Dp,
    val maxContentWidth: Dp = 700.dp,
    val maxSheetWidth: Dp = 640.dp,
) {
    val isCompact: Boolean get() = widthClass == WindowWidthClass.COMPACT
    val isMedium: Boolean get() = widthClass == WindowWidthClass.MEDIUM
    val isExpanded: Boolean get() = widthClass == WindowWidthClass.EXPANDED
    val horizontalGutter: Dp get() = if (isCompact) 16.dp else 24.dp
    val usePersistentDrawer: Boolean get() = widthClass == WindowWidthClass.EXPANDED
    val useCenteredModalSheet: Boolean get() = widthClass != WindowWidthClass.COMPACT
}

fun computeAdaptiveLayoutInfo(windowWidthDp: Dp, windowHeightDp: Dp): AdaptiveLayoutInfo {
    val widthClass = when {
        windowWidthDp < 600.dp -> WindowWidthClass.COMPACT
        windowWidthDp < 840.dp -> WindowWidthClass.MEDIUM
        else -> WindowWidthClass.EXPANDED
    }
    return AdaptiveLayoutInfo(
        widthClass = widthClass,
        windowWidthDp = windowWidthDp,
        windowHeightDp = windowHeightDp,
    )
}

@Composable
fun rememberAdaptiveLayoutInfo(): AdaptiveLayoutInfo {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val containerSize = LocalWindowInfo.current.containerSize

    val widthDp = if (containerSize.width > 0) {
        with(density) { containerSize.width.toDp() }
    } else {
        configuration.screenWidthDp.dp
    }
    val heightDp = if (containerSize.height > 0) {
        with(density) { containerSize.height.toDp() }
    } else {
        configuration.screenHeightDp.dp
    }

    return remember(widthDp, heightDp) {
        computeAdaptiveLayoutInfo(windowWidthDp = widthDp, windowHeightDp = heightDp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CompositionLocals & SovereignTheme Accessor
// ─────────────────────────────────────────────────────────────────────────────

val LocalSovereignColors = staticCompositionLocalOf { LightSovereignColors }
val LocalSovereignSpacing = staticCompositionLocalOf { SovereignSpacing() }
val LocalSovereignShapes = staticCompositionLocalOf { SovereignShapes() }
val LocalSovereignMotion = staticCompositionLocalOf { SovereignMotion() }
val LocalReducedMotion = staticCompositionLocalOf { false }
val LocalHapticsEnabled = staticCompositionLocalOf { true }
val LocalAdaptiveLayoutInfo = staticCompositionLocalOf {
    AdaptiveLayoutInfo(
        widthClass = WindowWidthClass.COMPACT,
        windowWidthDp = 412.dp,
        windowHeightDp = 915.dp,
    )
}

object SovereignTheme {
    val colors: SovereignColors
        @Composable
        @androidx.compose.runtime.ReadOnlyComposable
        get() = LocalSovereignColors.current

    val typography: SovereignTypography
        get() = SovereignTypography

    val spacing: SovereignSpacing
        @Composable
        @androidx.compose.runtime.ReadOnlyComposable
        get() = LocalSovereignSpacing.current

    val shapes: SovereignShapes
        @Composable
        @androidx.compose.runtime.ReadOnlyComposable
        get() = LocalSovereignShapes.current

    val elevation: SovereignElevation
        get() = SovereignElevation

    val motion: SovereignMotion
        @Composable
        @androidx.compose.runtime.ReadOnlyComposable
        get() = LocalSovereignMotion.current

    val adaptive: AdaptiveLayoutInfo
        @Composable
        @androidx.compose.runtime.ReadOnlyComposable
        get() = LocalAdaptiveLayoutInfo.current

    val reducedMotion: Boolean
        @Composable
        @androidx.compose.runtime.ReadOnlyComposable
        get() = LocalReducedMotion.current

    val hapticsEnabled: Boolean
        @Composable
        @androidx.compose.runtime.ReadOnlyComposable
        get() = LocalHapticsEnabled.current
}
