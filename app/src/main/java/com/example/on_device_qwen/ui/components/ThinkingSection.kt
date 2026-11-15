package com.example.on_device_qwen.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import com.example.on_device_qwen.ui.designsystem.SovereignHairlineCard
import com.example.on_device_qwen.ui.designsystem.SovereignStatusDot
import com.example.on_device_qwen.ui.designsystem.SovereignStatusTone
import com.example.on_device_qwen.ui.designsystem.SovereignTheme

/**
 * Expandable drawer for model reasoning and chain-of-thought (`<think>...</think>`).
 * Auto-expands while actively thinking and collapses cleanly once reasoning completes
 * unless manually toggled by the user.
 */
@Composable
fun ThinkingSection(
    thinkText: String,
    isThinking: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = SovereignTheme.colors
    val motion = SovereignTheme.motion
    val shapes = SovereignTheme.shapes
    val spacing = SovereignTheme.spacing

    var expanded by remember { mutableStateOf(isThinking) }
    var userOverrode by remember { mutableStateOf(false) }
    var previousIsThinking by remember { mutableStateOf(isThinking) }

    LaunchedEffect(isThinking) {
        if (previousIsThinking != isThinking) {
            if (!userOverrode) {
                expanded = isThinking
            }
            previousIsThinking = isThinking
        }
    }

    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = motion.spatialFast(),
        label = "ThinkingChevronRotation",
    )

    val labelText = if (isThinking) "Thinking…" else "Thought process"
    val guideColor = if (isThinking) {
        colors.accentPrimary
    } else {
        colors.accentPrimary.copy(alpha = 0.55f)
    }

    SovereignHairlineCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = colors.surfaceLow,
        borderColor = if (isThinking) {
            colors.accentPrimary.copy(alpha = 0.38f)
        } else {
            colors.hairlineSubtle
        },
        contentPadding = PaddingValues(0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = motion.spatialFast()),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = spacing.touchTargetMin)
                    .clip(shapes.md)
                    .clickable {
                        userOverrode = true
                        expanded = !expanded
                    }
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        contentDescription = labelText
                        stateDescription = if (expanded) "Expanded" else "Collapsed"
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                SovereignStatusDot(
                    tone = SovereignStatusTone.ACCENT,
                    size = 8.dp,
                    pulsing = isThinking,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = labelText,
                    style = SovereignTheme.typography.actionLabel,
                    color = if (isThinking) colors.accentPrimary else colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(chevronRotation),
                )
            }

            if (expanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                        .drawBehind {
                            val strokePx = 2.dp.toPx()
                            drawRoundRect(
                                color = guideColor,
                                topLeft = Offset.Zero,
                                size = Size(strokePx, size.height),
                                cornerRadius = CornerRadius(strokePx / 2f, strokePx / 2f),
                            )
                        }
                        .padding(start = 12.dp),
                ) {
                    Text(
                        text = thinkText.ifBlank { "Formulating reasoning chain…" },
                        style = SovereignTheme.typography.thinkingBody,
                        color = if (thinkText.isNotBlank()) colors.textSecondary else colors.textMuted,
                    )
                }
            }
        }
    }
}
