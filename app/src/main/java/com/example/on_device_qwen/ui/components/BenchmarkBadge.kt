package com.example.on_device_qwen.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.on_device_qwen.model.BenchmarkMetrics
import com.example.on_device_qwen.ui.designsystem.SovereignElevation
import com.example.on_device_qwen.ui.designsystem.SovereignTheme
import com.example.on_device_qwen.ui.designsystem.SovereignTypography
import java.util.Locale

/**
 * Displays honest per-message inference telemetry (decode TPS, prefill TPS when > 0, TTFT,
 * token counts, and execution backend badge) using Sovereign editorial telemetry tokens.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BenchmarkBadge(
    metrics: BenchmarkMetrics,
    backendLabel: String? = null,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false
) {
    val colors = SovereignTheme.colors
    val resolvedBackend = backendLabel?.takeIf { it.isNotBlank() } ?: metrics.backend

    val borderColor = if (isStreaming) {
        colors.accentPrimary.copy(alpha = 0.45f)
    } else {
        colors.hairlineSubtle
    }

    val (backendBg, backendFg) = when {
        resolvedBackend.contains("NPU", ignoreCase = true) ||
            resolvedBackend.contains("HTP", ignoreCase = true) ||
            resolvedBackend.contains("DSP", ignoreCase = true) ->
            colors.successContainer to colors.onSuccessContainer
        resolvedBackend.contains("GPU", ignoreCase = true) ||
            resolvedBackend.contains("OpenCL", ignoreCase = true) ||
            resolvedBackend.contains("Vulkan", ignoreCase = true) ->
            colors.amberContainer to colors.onAmberContainer
        else ->
            colors.accentContainer to colors.onAccentContainer
    }

    Surface(
        color = colors.surfaceLow,
        shape = SovereignTheme.shapes.sm,
        border = BorderStroke(SovereignElevation.hairlineWidth, borderColor),
        modifier = modifier.fillMaxWidth()
    ) {
        FlowRow(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Decode throughput
            TelemetryMetricItem(
                icon = Icons.Default.Speed,
                iconTint = colors.accentPrimary,
                text = when {
                    metrics.decodeTps > 0.0 -> String.format(Locale.US, "%.1f TPS", metrics.decodeTps)
                    isStreaming -> "measuring…"
                    else -> "— TPS"
                },
                textColor = colors.accentPrimary,
                bold = true
            )

            // Prefill throughput (shown when available and > 0)
            val prefill = metrics.prefillTps
            if (prefill != null && prefill > 0.0) {
                TelemetryMetricItem(
                    icon = Icons.Default.Bolt,
                    iconTint = colors.textSecondary,
                    text = String.format(Locale.US, "In %.1f TPS", prefill),
                    textColor = colors.textSecondary,
                    bold = false
                )
            }

            // Time to first token
            TelemetryMetricItem(
                icon = Icons.Default.Timer,
                iconTint = colors.textSecondary,
                text = when {
                    metrics.ttftMs > 0L -> "TTFT ${metrics.ttftMs}ms"
                    isStreaming -> "TTFT…"
                    else -> "TTFT —"
                },
                textColor = colors.textSecondary,
                bold = false
            )

            // Honest execution backend pill
            Surface(
                shape = SovereignTheme.shapes.xs,
                color = backendBg
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = "Backend",
                        tint = backendFg,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = resolvedBackend,
                        style = SovereignTypography.monoTag,
                        color = backendFg
                    )
                }
            }

            // Token & duration breakdown
            val decodeTok = metrics.decodeTokens
            if (decodeTok > 0) {
                val prefillTok = metrics.prefillTokens ?: 0
                val totalTok = prefillTok + decodeTok
                val tokenText = if (isStreaming) {
                    "$decodeTok tok"
                } else {
                    val promptDetail = if (prefillTok > 0) " ($totalTok total)" else ""
                    val durationDetail = if (metrics.totalDurationMs > 0L) {
                        " • ${String.format(Locale.US, "%.1fs", metrics.totalDurationMs / 1000.0)}"
                    } else ""
                    "$decodeTok tok$promptDetail$durationDetail"
                }
                Text(
                    text = tokenText,
                    style = SovereignTypography.telemetry,
                    fontWeight = if (isStreaming) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isStreaming) colors.accentPrimary else colors.textMuted,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }
    }
}

/**
 * Backwards-compatible overload for callers passing `benchmark = ..., isStreaming = ...`.
 */
@Deprecated(
    message = "Use BenchmarkBadge(metrics = ..., backendLabel = ..., modifier = ..., isStreaming = ...)",
    replaceWith = ReplaceWith("BenchmarkBadge(metrics = benchmark, modifier = modifier, isStreaming = isStreaming)")
)
@Composable
fun BenchmarkBadge(
    benchmark: BenchmarkMetrics,
    isStreaming: Boolean,
    modifier: Modifier = Modifier
) {
    BenchmarkBadge(
        metrics = benchmark,
        backendLabel = benchmark.backend,
        modifier = modifier,
        isStreaming = isStreaming
    )
}

@Composable
private fun TelemetryMetricItem(
    icon: ImageVector,
    iconTint: Color,
    text: String,
    textColor: Color,
    bold: Boolean
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(13.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = text,
            style = SovereignTypography.telemetry,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            color = textColor
        )
    }
}
