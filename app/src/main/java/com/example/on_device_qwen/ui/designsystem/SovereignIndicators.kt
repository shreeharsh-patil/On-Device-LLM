package com.example.on_device_qwen.ui.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class SovereignStatusTone {
    NEUTRAL,
    ACCENT,
    SUCCESS,
    WARNING,
    ERROR,
    VIOLET,
}

internal data class TonePalette(
    val dot: Color,
    val container: Color,
    val onContainer: Color,
    val border: Color,
)

@Composable
internal fun SovereignStatusTone.resolvePalette(): TonePalette {
    val c = SovereignTheme.colors
    return when (this) {
        SovereignStatusTone.NEUTRAL -> TonePalette(
            dot = c.textMuted,
            container = c.surfaceBase,
            onContainer = c.textSecondary,
            border = c.hairlineSubtle,
        )
        SovereignStatusTone.ACCENT -> TonePalette(
            dot = c.accentPrimary,
            container = c.accentContainer,
            onContainer = c.onAccentContainer,
            border = c.accentPrimary.copy(alpha = 0.28f),
        )
        SovereignStatusTone.SUCCESS -> TonePalette(
            dot = c.success,
            container = c.successContainer,
            onContainer = c.onSuccessContainer,
            border = c.success.copy(alpha = 0.28f),
        )
        SovereignStatusTone.WARNING -> TonePalette(
            dot = c.amber,
            container = c.amberContainer,
            onContainer = c.onAmberContainer,
            border = c.amber.copy(alpha = 0.30f),
        )
        SovereignStatusTone.ERROR -> TonePalette(
            dot = c.error,
            container = c.errorContainer,
            onContainer = c.onErrorContainer,
            border = c.error.copy(alpha = 0.30f),
        )
        SovereignStatusTone.VIOLET -> TonePalette(
            dot = c.llamaViolet,
            container = c.llamaVioletContainer,
            onContainer = c.onLlamaVioletContainer,
            border = c.llamaViolet.copy(alpha = 0.28f),
        )
    }
}

/**
 * Compact status indicator dot. Supports subtle breathing pulse that
 * automatically settles to a static solid dot under [LocalReducedMotion].
 */
@Composable
fun SovereignStatusDot(
    tone: SovereignStatusTone = SovereignStatusTone.SUCCESS,
    modifier: Modifier = Modifier,
    size: Dp = 8.dp,
    pulsing: Boolean = false,
) {
    val palette = tone.resolvePalette()
    val reducedMotion = SovereignTheme.reducedMotion
    val pulseDuration = SovereignTheme.motion.pulseDurationMs

    val alpha = if (pulsing && !reducedMotion) {
        val transition = rememberInfiniteTransition(label = "StatusDotPulse")
        val animatedAlpha by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = pulseDuration / 2),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "StatusDotAlpha",
        )
        animatedAlpha
    } else {
        1f
    }

    Box(
        modifier = modifier
            .size(size)
            .alpha(alpha)
            .clip(CircleShape)
            .background(palette.dot),
    )
}

/**
 * Compact monospace pill badge for formats (GGUF, LITERT), quantization tags,
 * RAM safety pills, thermal state, and telemetry metadata.
 */
@Composable
fun SovereignBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: SovereignStatusTone = SovereignStatusTone.NEUTRAL,
    leadingDot: Boolean = false,
    pulsingDot: Boolean = false,
    leadingIcon: ImageVector? = null,
    shape: Shape = SovereignTheme.shapes.xs,
    textStyle: TextStyle = SovereignTypography.monoTag,
) {
    val palette = tone.resolvePalette()
    Surface(
        modifier = modifier,
        shape = shape,
        color = palette.container,
        contentColor = palette.onContainer,
        border = BorderStroke(SovereignElevation.hairlineWidth, palette.border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (leadingDot) {
                SovereignStatusDot(
                    tone = tone,
                    size = 6.dp,
                    pulsing = pulsingDot,
                )
                Spacer(Modifier.width(5.dp))
            } else if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = palette.onContainer,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = text,
                style = textStyle,
                color = palette.onContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Segmented pill selector with smooth active surface elevation and 48.dp
 * interactive hit targets per option.
 */
@Composable
fun <T> SovereignSegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ((T) -> ImageVector?)? = null,
    badge: ((T) -> String?)? = null,
    shape: Shape = SovereignTheme.shapes.sm,
    hapticFeedback: Boolean = true,
) {
    val colors = SovereignTheme.colors
    val motion = SovereignTheme.motion
    val haptics = LocalHapticFeedback.current
    val hapticsEnabled = SovereignTheme.hapticsEnabled

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = colors.surfaceLow,
        border = BorderStroke(SovereignElevation.hairlineWidth, colors.hairlineSubtle),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                val bg by animateColorAsState(
                    targetValue = if (isSelected) colors.surfaceHigh else Color.Transparent,
                    animationSpec = motion.effectsQuick(),
                    label = "SegmentBg",
                )
                val fg by animateColorAsState(
                    targetValue = when {
                        !enabled -> colors.textMuted
                        isSelected -> colors.textPrimary
                        else -> colors.textSecondary
                    },
                    animationSpec = motion.effectsQuick(),
                    label = "SegmentFg",
                )
                val borderColor by animateColorAsState(
                    targetValue = if (isSelected) colors.hairlineStrong else Color.Transparent,
                    animationSpec = motion.effectsQuick(),
                    label = "SegmentBorder",
                )

                val optionIcon = icon?.invoke(option)
                val optionBadge = badge?.invoke(option)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(SovereignTheme.shapes.xs)
                        .clickable(enabled = enabled && !isSelected) {
                            if (hapticFeedback && hapticsEnabled) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            onSelect(option)
                        }
                        .semantics {
                            this.role = Role.Tab
                            this.selected = isSelected
                        }
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = SovereignTheme.shapes.xs,
                        color = bg,
                        border = if (isSelected) {
                            BorderStroke(SovereignElevation.hairlineWidth, borderColor)
                        } else {
                            null
                        },
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 38.dp)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (optionIcon != null) {
                                Icon(
                                    imageVector = optionIcon,
                                    contentDescription = null,
                                    tint = if (isSelected) colors.accentPrimary else fg,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(5.dp))
                            }
                            Text(
                                text = label(option),
                                style = SovereignTypography.actionLabel,
                                color = fg,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!optionBadge.isNullOrBlank()) {
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = optionBadge,
                                    style = SovereignTypography.monoTag,
                                    color = if (isSelected) colors.accentPrimary else colors.textMuted,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
