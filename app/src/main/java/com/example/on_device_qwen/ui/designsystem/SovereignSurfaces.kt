package com.example.on_device_qwen.ui.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Zero-tonal-tint Surface obeying Obsidian & Vellum elevation rules.
 */
@Composable
fun SovereignSurface(
    modifier: Modifier = Modifier,
    shape: Shape = SovereignTheme.shapes.md,
    color: Color = SovereignTheme.colors.surfaceLow,
    contentColor: Color = SovereignTheme.colors.textPrimary,
    border: BorderStroke? = BorderStroke(
        SovereignElevation.hairlineWidth,
        SovereignTheme.colors.hairlineSubtle,
    ),
    shadowElevation: Dp = SovereignElevation.none,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = color,
        contentColor = contentColor,
        border = border,
        tonalElevation = 0.dp,
        shadowElevation = shadowElevation,
        content = content,
    )
}

/**
 * Precision 1.dp hairline card used for model entries, settings groups,
 * memory items, and starter prompt cards.
 */
@Composable
fun SovereignHairlineCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
    shape: Shape = SovereignTheme.shapes.md,
    containerColor: Color = if (selected) SovereignTheme.colors.surfaceHigh else SovereignTheme.colors.surfaceLow,
    borderColor: Color = if (selected) SovereignTheme.colors.accentPrimary else SovereignTheme.colors.hairlineSubtle,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    shadowElevation: Dp = SovereignElevation.none,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = SovereignTheme.colors
    val motion = SovereignTheme.motion

    val animatedBg by animateColorAsState(
        targetValue = containerColor,
        animationSpec = motion.effectsQuick(),
        label = "HairlineCardBg",
    )
    val animatedBorder by animateColorAsState(
        targetValue = borderColor,
        animationSpec = motion.effectsQuick(),
        label = "HairlineCardBorder",
    )

    val borderStroke = BorderStroke(SovereignElevation.hairlineWidth, animatedBorder)

    if (onClick != null) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            color = animatedBg,
            contentColor = colors.textPrimary,
            border = borderStroke,
            tonalElevation = 0.dp,
            shadowElevation = shadowElevation,
            modifier = modifier
                .defaultMinSize(minHeight = 48.dp)
                .semantics {
                    role = Role.Button
                    if (selected) this.selected = true
                },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                content = content,
            )
        }
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = animatedBg,
            contentColor = colors.textPrimary,
            border = borderStroke,
            tonalElevation = 0.dp,
            shadowElevation = shadowElevation,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                content = content,
            )
        }
    }
}

/**
 * Crisp 1.dp hairline divider matching the Obsidian & Vellum border scale.
 */
@Composable
fun SovereignDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = SovereignElevation.hairlineWidth,
    color: Color = SovereignTheme.colors.hairlineSubtle,
) {
    HorizontalDivider(
        modifier = modifier,
        thickness = thickness,
        color = color,
    )
}
