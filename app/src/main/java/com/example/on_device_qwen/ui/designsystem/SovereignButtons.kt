package com.example.on_device_qwen.ui.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Icon button enforcing a strict 48.dp minimum accessible touch target while
 * allowing compact visual framing ([visualSize]) inside dense toolbars/cards.
 */
@Composable
fun SovereignIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    visualSize: Dp = 40.dp,
    containerColor: Color = Color.Transparent,
    contentColor: Color = SovereignTheme.colors.textSecondary,
    disabledContentColor: Color = SovereignTheme.colors.textMuted.copy(alpha = 0.45f),
    border: BorderStroke? = null,
    shape: Shape = SovereignTheme.shapes.pill,
    hapticFeedback: Boolean = false,
    hapticType: HapticFeedbackType = HapticFeedbackType.TextHandleMove,
    selected: Boolean = false,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val hapticsEnabled = SovereignTheme.hapticsEnabled
    val motion = SovereignTheme.motion
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled && !motion.reducedMotion) 0.94f else 1f,
        animationSpec = motion.spatialFast(),
        label = "SovereignIconButtonScale",
    )

    val resolvedContentColor by animateColorAsState(
        targetValue = if (enabled) contentColor else disabledContentColor,
        animationSpec = motion.effectsQuick(),
        label = "SovereignIconButtonFg",
    )

    val resolvedContainerColor by animateColorAsState(
        targetValue = if (enabled) containerColor else containerColor.copy(alpha = 0.5f),
        animationSpec = motion.effectsQuick(),
        label = "SovereignIconButtonBg",
    )

    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .semantics(mergeDescendants = true) {
                this.role = Role.Button
                this.contentDescription = contentDescription
                if (selected) this.selected = true
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            onClick = {
                if (hapticFeedback && hapticsEnabled) {
                    haptics.performHapticFeedback(hapticType)
                }
                onClick()
            },
            enabled = enabled,
            shape = shape,
            color = resolvedContainerColor,
            contentColor = resolvedContentColor,
            border = border,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            interactionSource = interactionSource,
            modifier = Modifier
                .size(visualSize)
                .scale(scale),
        ) {
            Box(contentAlignment = Alignment.Center) {
                CompositionLocalProvider(LocalContentColor provides resolvedContentColor) {
                    content()
                }
            }
        }
    }
}

/**
 * Primary editorial CTA button. Uses high-contrast [SovereignColors.ctaInk] by
 * default or [SovereignColors.accentPrimary] when [useAccent] is true.
 */
@Composable
fun SovereignPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    useAccent: Boolean = false,
    leadingIcon: ImageVector? = null,
    shape: Shape = SovereignTheme.shapes.sm,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    hapticFeedback: Boolean = false,
) {
    val colors = SovereignTheme.colors
    val targetBg = when {
        !enabled -> colors.surfaceBase
        useAccent -> colors.accentPrimary
        else -> colors.ctaInk
    }
    val targetFg = when {
        !enabled -> colors.textMuted
        useAccent -> colors.onAccent
        else -> colors.onCtaInk
    }
    SovereignButtonBase(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled && !loading,
        loading = loading,
        leadingIcon = leadingIcon,
        containerColor = targetBg,
        contentColor = targetFg,
        border = null,
        shape = shape,
        contentPadding = contentPadding,
        hapticFeedback = hapticFeedback,
    )
}

/**
 * Hairline outlined secondary action button.
 */
@Composable
fun SovereignSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
    containerColor: Color = SovereignTheme.colors.surfaceLow,
    contentColor: Color = SovereignTheme.colors.textPrimary,
    borderColor: Color = SovereignTheme.colors.hairlineStrong,
    shape: Shape = SovereignTheme.shapes.sm,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    hapticFeedback: Boolean = false,
) {
    val colors = SovereignTheme.colors
    SovereignButtonBase(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled && !loading,
        loading = loading,
        leadingIcon = leadingIcon,
        containerColor = if (enabled) containerColor else colors.surfaceLow.copy(alpha = 0.6f),
        contentColor = if (enabled) contentColor else colors.textMuted,
        border = BorderStroke(
            SovereignElevation.hairlineWidth,
            if (enabled) borderColor else colors.hairlineSubtle,
        ),
        shape = shape,
        contentPadding = contentPadding,
        hapticFeedback = hapticFeedback,
    )
}

/**
 * Subtle tonal container button (defaulting to Sovereign Cyan-Azure container).
 */
@Composable
fun SovereignTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
    containerColor: Color = SovereignTheme.colors.accentContainer,
    contentColor: Color = SovereignTheme.colors.onAccentContainer,
    border: BorderStroke? = BorderStroke(
        SovereignElevation.hairlineWidth,
        SovereignTheme.colors.accentPrimary.copy(alpha = 0.25f),
    ),
    shape: Shape = SovereignTheme.shapes.sm,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    hapticFeedback: Boolean = false,
) {
    val colors = SovereignTheme.colors
    SovereignButtonBase(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled && !loading,
        loading = loading,
        leadingIcon = leadingIcon,
        containerColor = if (enabled) containerColor else colors.surfaceBase,
        contentColor = if (enabled) contentColor else colors.textMuted,
        border = if (enabled) border else BorderStroke(SovereignElevation.hairlineWidth, colors.hairlineSubtle),
        shape = shape,
        contentPadding = contentPadding,
        hapticFeedback = hapticFeedback,
    )
}

@Composable
private fun SovereignButtonBase(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    loading: Boolean,
    leadingIcon: ImageVector?,
    containerColor: Color,
    contentColor: Color,
    border: BorderStroke?,
    shape: Shape,
    contentPadding: PaddingValues,
    hapticFeedback: Boolean,
) {
    val haptics = LocalHapticFeedback.current
    val hapticsEnabled = SovereignTheme.hapticsEnabled
    val motion = SovereignTheme.motion

    val animatedBg by animateColorAsState(
        targetValue = containerColor,
        animationSpec = motion.effectsQuick(),
        label = "SovereignButtonBg",
    )
    val animatedFg by animateColorAsState(
        targetValue = contentColor,
        animationSpec = motion.effectsQuick(),
        label = "SovereignButtonFg",
    )

    Surface(
        onClick = {
            if (hapticFeedback && hapticsEnabled) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            onClick()
        },
        enabled = enabled,
        shape = shape,
        color = animatedBg,
        contentColor = animatedFg,
        border = border,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .semantics { role = Role.Button },
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = animatedFg,
                )
                Spacer(Modifier.width(8.dp))
            } else if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = animatedFg,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = SovereignTypography.actionLabel,
                color = animatedFg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
