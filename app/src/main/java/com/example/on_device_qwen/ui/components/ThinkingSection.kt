package com.example.on_device_qwen.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.on_device_qwen.ui.designsystem.LocalReducedMotion
import com.example.on_device_qwen.ui.designsystem.SovereignElevation
import com.example.on_device_qwen.ui.designsystem.SovereignTheme
import com.example.on_device_qwen.ui.designsystem.SovereignTypography

/**
 * Resolves the initial/effective expanded state for a [ThinkingSection].
 *
 * - Historical/completed messages default to collapsed when [userOverride] is null.
 * - Actively reasoning messages expand automatically once [thinkingText] is non-blank.
 * - Explicit user toggles ([userOverride]) always take precedence per `messageId`.
 */
internal fun resolveThinkingExpandedState(
    isThinking: Boolean,
    thinkingText: String,
    userOverride: Boolean?
): Boolean {
    return userOverride ?: (isThinking && thinkingText.isNotBlank())
}

/**
 * Expandable drawer for deep model reasoning (<think>...</think>).
 * Expansion state is keyed per [messageId] and respects [LocalReducedMotion].
 */
@Composable
fun ThinkingSection(
    thinkingText: String,
    isThinking: Boolean,
    modifier: Modifier = Modifier,
    messageId: Long = 0L
) {
    val colors = SovereignTheme.colors
    val reducedMotion = LocalReducedMotion.current
    var userOverride by remember(messageId) { mutableStateOf<Boolean?>(null) }
    val expanded = resolveThinkingExpandedState(
        isThinking = isThinking,
        thinkingText = thinkingText,
        userOverride = userOverride
    )

    val borderColor = if (isThinking) {
        colors.llamaViolet.copy(alpha = 0.6f)
    } else {
        colors.hairlineSubtle
    }

    Surface(
        color = colors.surfaceLow,
        shape = SovereignTheme.shapes.md,
        border = BorderStroke(SovereignElevation.hairlineWidth, borderColor),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
            // Header row with >=48.dp touch target
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(SovereignTheme.shapes.sm)
                    .clickable(role = Role.Button) {
                        userOverride = !expanded
                    }
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        stateDescription = if (expanded) "Expanded" else "Collapsed"
                        contentDescription = if (isThinking) {
                            "Thinking deeply, tap to ${if (expanded) "collapse" else "expand"}"
                        } else {
                            "Thought process, tap to ${if (expanded) "collapse" else "expand"}"
                        }
                    }
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                if (isThinking) {
                    PulsingThinkingDot(reducedMotion = reducedMotion)
                } else {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = colors.llamaViolet
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isThinking) "Thinking deeply…" else "Thought process",
                    style = SovereignTypography.bodyCompact.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isThinking) colors.llamaViolet else colors.textSecondary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = colors.textSecondary
                )
            }

            // Expanded body content with IntrinsicSize.Min parent Row for the left accent bar
            AnimatedVisibility(
                visible = expanded,
                enter = if (reducedMotion) {
                    EnterTransition.None
                } else {
                    expandVertically(tween(180)) + fadeIn(tween(150))
                },
                exit = if (reducedMotion) {
                    ExitTransition.None
                } else {
                    shrinkVertically(tween(160)) + fadeOut(tween(120))
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .padding(start = 6.dp, end = 6.dp, top = 2.dp, bottom = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(2.5.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        colors.llamaViolet.copy(alpha = 0.85f),
                                        colors.accentPrimary.copy(alpha = 0.35f)
                                    )
                                )
                            )
                    )
                    Spacer(Modifier.width(10.dp))
                    if (thinkingText.isNotBlank()) {
                        Text(
                            text = thinkingText,
                            style = SovereignTypography.thinkingBody.copy(fontStyle = FontStyle.Italic),
                            color = colors.textSecondary,
                            modifier = Modifier.weight(1f)
                        )
                    } else if (isThinking) {
                        Text(
                            text = "Formulating reasoning chain…",
                            style = SovereignTypography.thinkingBody.copy(fontStyle = FontStyle.Italic),
                            color = colors.textMuted,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Backwards-compatible overload for callers passing `thinkText = ...`.
 */
@Deprecated(
    message = "Use ThinkingSection(thinkingText = ..., isThinking = ..., modifier = ..., messageId = ...)",
    replaceWith = ReplaceWith("ThinkingSection(thinkingText = thinkText, isThinking = isThinking, modifier = modifier)")
)
@Composable
fun ThinkingSection(
    thinkText: String,
    isThinking: Boolean,
    modifier: Modifier
) {
    ThinkingSection(
        thinkingText = thinkText,
        isThinking = isThinking,
        modifier = modifier,
        messageId = 0L
    )
}

@Composable
private fun PulsingThinkingDot(reducedMotion: Boolean) {
    val colors = SovereignTheme.colors
    val alpha = if (reducedMotion) {
        0.9f
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse_think")
        val animated by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, easing = EaseInOutCubic),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_alpha"
        )
        animated
    }
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(colors.llamaViolet.copy(alpha = alpha))
    )
}
