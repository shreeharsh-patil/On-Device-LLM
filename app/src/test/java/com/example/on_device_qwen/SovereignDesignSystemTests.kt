package com.example.on_device_qwen

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.example.on_device_qwen.ui.designsystem.DarkSovereignColors
import com.example.on_device_qwen.ui.designsystem.LightSovereignColors
import com.example.on_device_qwen.ui.designsystem.SovereignMotion
import com.example.on_device_qwen.ui.designsystem.WindowWidthClass
import com.example.on_device_qwen.ui.designsystem.computeAdaptiveLayoutInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

class SovereignDesignSystemTests {

    @Test
    fun adaptiveLayoutThresholdsMatchSpec() {
        val compact = computeAdaptiveLayoutInfo(windowWidthDp = 412.dp, windowHeightDp = 915.dp)
        assertEquals(WindowWidthClass.COMPACT, compact.widthClass)
        assertTrue(compact.isCompact)
        assertFalse(compact.usePersistentDrawer)
        assertFalse(compact.useCenteredModalSheet)
        assertEquals(16.dp, compact.horizontalGutter)
        assertEquals(700.dp, compact.maxContentWidth)
        assertEquals(640.dp, compact.maxSheetWidth)

        val compactUpper = computeAdaptiveLayoutInfo(windowWidthDp = 599.dp, windowHeightDp = 800.dp)
        assertEquals(WindowWidthClass.COMPACT, compactUpper.widthClass)

        val mediumLower = computeAdaptiveLayoutInfo(windowWidthDp = 600.dp, windowHeightDp = 900.dp)
        assertEquals(WindowWidthClass.MEDIUM, mediumLower.widthClass)
        assertTrue(mediumLower.isMedium)
        assertFalse(mediumLower.usePersistentDrawer)
        assertTrue(mediumLower.useCenteredModalSheet)
        assertEquals(24.dp, mediumLower.horizontalGutter)

        val mediumUpper = computeAdaptiveLayoutInfo(windowWidthDp = 839.dp, windowHeightDp = 900.dp)
        assertEquals(WindowWidthClass.MEDIUM, mediumUpper.widthClass)

        val expanded = computeAdaptiveLayoutInfo(windowWidthDp = 840.dp, windowHeightDp = 1200.dp)
        assertEquals(WindowWidthClass.EXPANDED, expanded.widthClass)
        assertTrue(expanded.isExpanded)
        assertTrue(expanded.usePersistentDrawer)
        assertTrue(expanded.useCenteredModalSheet)
        assertEquals(24.dp, expanded.horizontalGutter)
    }

    @Test
    fun lightAndDarkSchemesEliminateDefaultMaterialPurple() {
        val m3DefaultPurples = setOf(
            Color(0xFF6750A4),
            Color(0xFFD0BCFF),
            Color(0xFF625B71),
            Color(0xFFCCC2DC),
            Color(0xFF7D5260),
            Color(0xFFEFB8C8),
            Color(0xFF6C63FF),
        )

        for (palette in listOf(LightSovereignColors, DarkSovereignColors)) {
            val scheme = palette.toMaterial3ColorScheme()
            val mappedSlots = listOf(
                scheme.primary,
                scheme.onPrimary,
                scheme.primaryContainer,
                scheme.onPrimaryContainer,
                scheme.secondary,
                scheme.onSecondary,
                scheme.secondaryContainer,
                scheme.onSecondaryContainer,
                scheme.tertiary,
                scheme.onTertiary,
                scheme.tertiaryContainer,
                scheme.onTertiaryContainer,
                scheme.background,
                scheme.onBackground,
                scheme.surface,
                scheme.onSurface,
                scheme.surfaceVariant,
                scheme.onSurfaceVariant,
                scheme.error,
                scheme.errorContainer,
            )
            mappedSlots.forEach { color ->
                assertFalse("Default M3/legacy purple leaked into scheme: $color", color in m3DefaultPurples)
            }
            assertEquals(Color.Transparent, scheme.surfaceTint)
        }

        assertEquals(Color(0xFFFAF9F6), LightSovereignColors.bgCanvas)
        assertEquals(Color(0xFF0B0C0F), DarkSovereignColors.bgCanvas)
        assertNotEquals(LightSovereignColors.bgCanvas, DarkSovereignColors.bgCanvas)
    }

    @Test
    fun primaryAndSecondaryTextMeetWcagAaContrast() {
        for (palette in listOf(LightSovereignColors, DarkSovereignColors)) {
            val surfaces = listOf(
                "bgCanvas" to palette.bgCanvas,
                "surfaceLow" to palette.surfaceLow,
                "surfaceBase" to palette.surfaceBase,
                "surfaceHigh" to palette.surfaceHigh,
                "surfaceHighest" to palette.surfaceHighest,
                "codeBackground" to palette.codeBackground,
                "codeHeader" to palette.codeHeader,
            )
            val foregrounds = listOf(
                "textPrimary" to palette.textPrimary,
                "textSecondary" to palette.textSecondary,
                "textMuted" to palette.textMuted,
                "accentPrimary" to palette.accentPrimary,
                "llamaViolet" to palette.llamaViolet,
                "success" to palette.success,
                "amber" to palette.amber,
                "error" to palette.error,
            )
            for ((fgName, fg) in foregrounds) {
                for ((bgName, bg) in surfaces) {
                    val ratio = contrastRatio(fg, bg)
                    assertTrue(
                        "$fgName on $bgName (isDark=${palette.isDark}) must meet WCAG AA >= 4.5:1 (was $ratio)",
                        ratio >= 4.5,
                    )
                }
            }

            val containerPairs = listOf(
                Triple("onAccent/accentPrimary", palette.onAccent, palette.accentPrimary),
                Triple("onCtaInk/ctaInk", palette.onCtaInk, palette.ctaInk),
                Triple("onAccentContainer/accentContainer", palette.onAccentContainer, palette.accentContainer),
                Triple("onLlamaVioletContainer/llamaVioletContainer", palette.onLlamaVioletContainer, palette.llamaVioletContainer),
                Triple("onSuccessContainer/successContainer", palette.onSuccessContainer, palette.successContainer),
                Triple("onAmberContainer/amberContainer", palette.onAmberContainer, palette.amberContainer),
                Triple("onErrorContainer/errorContainer", palette.onErrorContainer, palette.errorContainer),
            )
            for ((label, fg, bg) in containerPairs) {
                val ratio = contrastRatio(fg, bg)
                assertTrue(
                    "$label (isDark=${palette.isDark}) must meet WCAG AA >= 4.5:1 (was $ratio)",
                    ratio >= 4.5,
                )
            }

            val m3 = palette.toMaterial3ColorScheme()
            assertTrue(
                "Material onError on error must meet WCAG AA >= 4.5:1",
                contrastRatio(m3.onError, m3.error) >= 4.5,
            )
        }
    }

    @Test
    fun motionTokensRespectReducedMotion() {
        val standard = SovereignMotion(reducedMotion = false)
        assertTrue(standard.spatialFast<Float>() is SpringSpec)
        assertTrue(standard.spatialExpressive<Float>() is SpringSpec)
        assertEquals(1400, standard.pulseDurationMs)
        assertEquals(1400, SovereignMotion.PulseDurationMs)

        val reduced = SovereignMotion(reducedMotion = true)
        assertTrue(reduced.spatialFast<Float>() is SnapSpec)
        assertTrue(reduced.spatialExpressive<Float>() is SnapSpec)
    }

    private fun contrastRatio(fg: Color, bg: Color): Double {
        val l1 = fg.luminance().toDouble()
        val l2 = bg.luminance().toDouble()
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }
}
