package com.example.on_device_qwen.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Compact monospace section micro-header with optional trailing slot.
 */
@Composable
fun SovereignSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    uppercase: Boolean = true,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = SovereignTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (uppercase) title.uppercase() else title,
            style = SovereignTypography.monoTag,
            color = colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                content = trailing,
            )
        }
    }
}

/**
 * Font-scale safe interactive settings/navigation row (min 52.dp height, wraps cleanly).
 */
@Composable
fun SovereignSettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    leadingTint: Color = SovereignTheme.colors.textSecondary,
    trailingText: String? = null,
    showChevron: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = SovereignTheme.colors
    val rowModifier = modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 52.dp)
        .let { base ->
            if (onClick != null) {
                base
                    .clickable(enabled = enabled, onClick = onClick)
                    .semantics { role = Role.Button }
            } else {
                base
            }
        }
        .padding(horizontal = 16.dp, vertical = 12.dp)

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = if (enabled) leadingTint else colors.textMuted,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = SovereignTypography.pillTitle,
                color = if (enabled) colors.textPrimary else colors.textMuted,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = SovereignTypography.bodyCompact,
                    color = colors.textSecondary,
                )
            }
        }

        if (!trailingText.isNullOrBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = trailingText,
                style = SovereignTypography.telemetry,
                color = colors.textSecondary,
            )
        }

        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            CompositionLocalProvider(LocalContentColor provides colors.textSecondary) {
                trailing()
            }
        } else if (showChevron) {
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Accessible toggle row with Sovereign Cyan-Azure switch styling and optional haptics.
 */
@Composable
fun SovereignSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    hapticFeedback: Boolean = true,
) {
    val colors = SovereignTheme.colors
    val haptics = LocalHapticFeedback.current
    val hapticsEnabled = SovereignTheme.hapticsEnabled

    val toggle = {
        val next = !checked
        if (hapticFeedback && hapticsEnabled) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        onCheckedChange(next)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .clickable(enabled = enabled, role = Role.Switch, onClick = toggle)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = if (checked && enabled) colors.accentPrimary else colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = SovereignTypography.pillTitle,
                color = if (enabled) colors.textPrimary else colors.textMuted,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = SovereignTypography.bodyCompact,
                    color = colors.textSecondary,
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Switch(
            checked = checked,
            onCheckedChange = null, // handled by full-row toggle for 48.dp+ target
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onAccent,
                checkedTrackColor = colors.accentPrimary,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = colors.textSecondary,
                uncheckedTrackColor = colors.surfaceBase,
                uncheckedBorderColor = colors.hairlineStrong,
                disabledCheckedThumbColor = colors.textMuted,
                disabledCheckedTrackColor = colors.surfaceBase,
                disabledUncheckedThumbColor = colors.textMuted,
                disabledUncheckedTrackColor = colors.surfaceLow,
                disabledUncheckedBorderColor = colors.hairlineSubtle,
            ),
        )
    }
}

/**
 * Parameter slider row displaying a tabular-numeral JetBrains Mono value pill
 * and optional min/max range hint labels.
 */
@Composable
fun SovereignSliderRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    formattedValue: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    steps: Int = 0,
    minLabel: String? = null,
    maxLabel: String? = null,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val colors = SovereignTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = SovereignTypography.pillTitle,
                    color = if (enabled) colors.textPrimary else colors.textMuted,
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = SovereignTypography.bodyCompact,
                        color = colors.textSecondary,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Surface(
                shape = SovereignTheme.shapes.xs,
                color = colors.surfaceBase,
                border = BorderStroke(SovereignElevation.hairlineWidth, colors.hairlineSubtle),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Text(
                    text = formattedValue,
                    style = SovereignTypography.telemetry,
                    color = if (enabled) colors.accentPrimary else colors.textMuted,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = colors.accentPrimary,
                activeTrackColor = colors.accentPrimary,
                inactiveTrackColor = colors.surfaceHighest,
                activeTickColor = colors.onAccent.copy(alpha = 0.55f),
                inactiveTickColor = colors.textMuted.copy(alpha = 0.4f),
                disabledThumbColor = colors.textMuted,
                disabledActiveTrackColor = colors.textMuted.copy(alpha = 0.4f),
                disabledInactiveTrackColor = colors.surfaceBase,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        if (!minLabel.isNullOrBlank() || !maxLabel.isNullOrBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = minLabel.orEmpty(),
                    style = SovereignTypography.monoTag,
                    color = colors.textMuted,
                )
                Text(
                    text = maxLabel.orEmpty(),
                    style = SovereignTypography.monoTag,
                    color = colors.textMuted,
                )
            }
        }
    }
}
