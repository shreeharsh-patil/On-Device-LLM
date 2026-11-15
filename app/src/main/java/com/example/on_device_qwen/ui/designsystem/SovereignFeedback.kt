package com.example.on_device_qwen.ui.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.on_device_qwen.R

/**
 * Centered editorial empty-state display for zero-history / empty search / uninitialized states.
 */
@Composable
fun SovereignEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    emblem: (@Composable () -> Unit)? = null,
    titleStyle: TextStyle = SovereignTypography.cardTitle,
    action: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = SovereignTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (emblem != null) {
            emblem()
            Spacer(Modifier.height(16.dp))
        } else if (icon != null) {
            Surface(
                shape = CircleShape,
                color = colors.surfaceLow,
                border = BorderStroke(SovereignElevation.hairlineWidth, colors.hairlineStrong),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Text(
            text = title,
            style = titleStyle,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )

        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = SovereignTypography.bodyCompact,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp),
            )
        }

        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

/**
 * Structured error card for engine initialization, runtime diagnostics, and
 * hardware recovery states.
 */
@Composable
fun SovereignErrorCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.ErrorOutline,
    tone: SovereignStatusTone = SovereignStatusTone.ERROR,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = SovereignTheme.colors
    val palette = tone.resolvePalette()

    Surface(
        modifier = modifier
            .widthIn(max = 440.dp)
            .fillMaxWidth(),
        shape = SovereignTheme.shapes.md,
        color = colors.surfaceLow,
        border = BorderStroke(SovereignElevation.hairlineWidth, palette.border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = SovereignTheme.shapes.xs,
                    color = palette.container,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier.size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = palette.dot,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = title,
                    style = SovereignTypography.cardTitle,
                    color = colors.textPrimary,
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = message,
                style = SovereignTypography.bodyCompact,
                color = colors.textSecondary,
            )

            if (actions != null) {
                Spacer(Modifier.height(16.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = actions,
                )
            }
        }
    }
}

/**
 * Dismissible inline warning/validation/info banner with live-region announcement
 * and smooth spring reveal respecting [LocalReducedMotion].
 */
@Composable
fun SovereignBanner(
    message: String?,
    modifier: Modifier = Modifier,
    tone: SovereignStatusTone = SovereignStatusTone.WARNING,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val motion = SovereignTheme.motion
    AnimatedVisibility(
        visible = !message.isNullOrBlank(),
        enter = fadeIn(motion.effectsQuick()) + expandVertically(motion.spatialFast()),
        exit = fadeOut(motion.effectsQuick()) + shrinkVertically(motion.spatialFast()),
        modifier = modifier,
    ) {
        if (!message.isNullOrBlank()) {
            val palette = tone.resolvePalette()
            val icon = when (tone) {
                SovereignStatusTone.ERROR -> Icons.Rounded.ErrorOutline
                SovereignStatusTone.WARNING -> Icons.Rounded.WarningAmber
                SovereignStatusTone.SUCCESS -> Icons.Rounded.CheckCircleOutline
                else -> Icons.Rounded.Info
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite },
                shape = SovereignTheme.shapes.sm,
                color = palette.container,
                contentColor = palette.onContainer,
                border = BorderStroke(SovereignElevation.hairlineWidth, palette.border),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = palette.dot,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = message,
                        style = SovereignTypography.bodyCompact,
                        color = palette.onContainer,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                    )
                    if (!actionLabel.isNullOrBlank() && onAction != null) {
                        TextButton(
                            onClick = onAction,
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                        ) {
                            Text(
                                text = actionLabel,
                                style = SovereignTypography.actionLabel,
                                color = palette.onContainer,
                            )
                        }
                    }
                    if (onDismiss != null) {
                        SovereignIconButton(
                            onClick = onDismiss,
                            contentDescription = stringResource(R.string.cd_dismiss_banner),
                            visualSize = 36.dp,
                            contentColor = palette.onContainer,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Animated 3-dot prefill/streaming presence indicator. Renders smooth staggered
 * wave animation by default and falls back to crisp static dots when
 * [LocalReducedMotion] is enabled.
 */
@Composable
fun SovereignTypingIndicator(
    modifier: Modifier = Modifier,
    dotSize: Dp = 6.dp,
    dotSpacing: Dp = 5.dp,
    color: Color = SovereignTheme.colors.accentPrimary,
) {
    val reducedMotion = SovereignTheme.reducedMotion
    val cycleMs = SovereignTheme.motion.pulseDurationMs

    Row(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(dotSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (reducedMotion) {
            repeat(3) { idx ->
                Box(
                    modifier = Modifier
                        .size(dotSize)
                        .alpha(if (idx == 1) 0.9f else 0.65f)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        } else {
            val transition = rememberInfiniteTransition(label = "SovereignTypingDots")
            repeat(3) { index ->
                val delay = index * 160
                val phase by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = keyframes {
                            durationMillis = cycleMs
                            0f at delay
                            1f at (delay + 280).coerceAtMost(cycleMs)
                            0f at (delay + 620).coerceAtMost(cycleMs)
                            0f at cycleMs
                        },
                        repeatMode = RepeatMode.Restart,
                    ),
                    label = "TypingDotPhase_$index",
                )

                Box(
                    modifier = Modifier
                        .size(dotSize)
                        .graphicsLayer {
                            alpha = 0.35f + (0.65f * phase)
                            translationY = -3.dp.toPx() * phase
                            val s = 0.88f + (0.18f * phase)
                            scaleX = s
                            scaleY = s
                        }
                        .clip(CircleShape)
                        .background(color),
                )
            }
        }
    }
}
