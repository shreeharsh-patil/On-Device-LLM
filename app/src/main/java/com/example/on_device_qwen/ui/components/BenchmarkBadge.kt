package com.example.on_device_qwen.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.on_device_qwen.model.BenchmarkMetrics
import com.example.on_device_qwen.ui.designsystem.SovereignElevation
import com.example.on_device_qwen.ui.designsystem.SovereignStatusDot
import com.example.on_device_qwen.ui.designsystem.SovereignStatusTone
import com.example.on_device_qwen.ui.designsystem.SovereignSurface
import com.example.on_device_qwen.ui.designsystem.SovereignTheme
import java.util.Locale

/**
 * Compact tabular-numeral (`JetBrainsMonoFontFamily`, `tnum`) telemetry pill
 * displaying decode speed, time-to-first-token, output token count, and active backend.
 */
@Composable
fun BenchmarkBadge(
    benchmark: BenchmarkMetrics,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = SovereignTheme.colors
    val shapes = SovereignTheme.shapes
    val typography = SovereignTheme.typography

    val tpsText = when {
        benchmark.decodeTps > 0.0 -> String.format(Locale.US, "%.1f tok/s", benchmark.decodeTps)
        isStreaming -> "measuring…"
        else -> "— tok/s"
    }
    val ttftText = when {
        benchmark.ttftMs > 0L -> "${benchmark.ttftMs} ms TTFT"
        isStreaming -> "TTFT…"
        else -> "TTFT —"
    }
    val tokensText = when {
        benchmark.decodeTokens > 0 -> "${benchmark.decodeTokens} tok"
        isStreaming -> "0 tok"
        else -> null
    }

    val backendColor = when {
        benchmark.backend.contains("NPU", ignoreCase = true) -> colors.success
        benchmark.backend.contains("GPU", ignoreCase = true) -> colors.amber
        benchmark.backend.contains("llama", ignoreCase = true) ||
            benchmark.backend.contains("GGUF", ignoreCase = true) -> colors.llamaViolet
        else -> colors.accentPrimary
    }
    val dotTone = when {
        isStreaming -> SovereignStatusTone.ACCENT
        benchmark.backend.contains("NPU", ignoreCase = true) -> SovereignStatusTone.SUCCESS
        benchmark.backend.contains("GPU", ignoreCase = true) -> SovereignStatusTone.WARNING
        else -> SovereignStatusTone.ACCENT
    }

    val summaryDescription = buildString {
        append("Telemetry: ")
        append(tpsText)
        append(", ")
        append(ttftText)
        if (tokensText != null) {
            append(", ")
            append(tokensText)
        }
        append(", backend ")
        append(benchmark.backend)
    }

    SovereignSurface(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = summaryDescription
        },
        shape = shapes.sm,
        color = colors.surfaceLow,
        border = BorderStroke(
            SovereignElevation.hairlineWidth,
            if (isStreaming) colors.accentPrimary.copy(alpha = 0.35f) else colors.hairlineSubtle,
        ),
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SovereignStatusDot(
                tone = dotTone,
                size = 6.dp,
                pulsing = isStreaming,
            )
            Spacer(Modifier.width(1.dp))
            Text(
                text = tpsText,
                style = typography.monoTag,
                color = if (benchmark.decodeTps > 0.0 || isStreaming) {
                    colors.accentPrimary
                } else {
                    colors.textSecondary
                },
                maxLines = 1,
            )
            TelemetrySeparator()
            Text(
                text = ttftText,
                style = typography.monoTag,
                color = colors.textSecondary,
                maxLines = 1,
            )
            if (tokensText != null) {
                TelemetrySeparator()
                Text(
                    text = tokensText,
                    style = typography.monoTag,
                    color = if (isStreaming) colors.textPrimary else colors.textSecondary,
                    maxLines = 1,
                )
            }
            TelemetrySeparator()
            Text(
                text = benchmark.backend,
                style = typography.monoTag,
                color = backendColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TelemetrySeparator() {
    Text(
        text = "·",
        style = SovereignTheme.typography.monoTag,
        color = SovereignTheme.colors.textMuted,
    )
}
