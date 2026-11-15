package com.example.on_device_qwen.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.on_device_qwen.model.BenchmarkMetrics
import java.util.Locale

/**
 * Performance data is available on demand, without overwhelming ordinary chat.
 * The backend label is the value reported by the inference engine.
 */
@Composable
fun BenchmarkBadge(
    benchmark: BenchmarkMetrics,
    isStreaming: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(RoundedCornerShape(14.dp))
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Icon(
                    Icons.Default.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (benchmark.decodeTps > 0) String.format(Locale.US, "%.1f tok/s", benchmark.decodeTps)
                    else if (isStreaming) "Measuring speed…" else "Performance details",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(10.dp))
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Hide performance details" else "Show performance details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
            AnimatedVisibility(expanded) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(3.dp))
                    PerformanceDetail("Backend", benchmark.backend)
                    PerformanceDetail("Time to first token", if (benchmark.ttftMs > 0) benchmark.ttftMs.toString() + " ms" else "—")
                    PerformanceDetail("Output tokens", benchmark.decodeTokens.toString())
                    if (benchmark.prefillTokens != null) {
                        PerformanceDetail("Input tokens", benchmark.prefillTokens.toString())
                    }
                    PerformanceDetail("Elapsed time", String.format(Locale.US, "%.1f s", benchmark.totalDurationMs / 1000.0))
                }
            }
        }
    }
}

@Composable
private fun PerformanceDetail(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}
