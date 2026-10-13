package com.example.on_device_qwen.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.on_device_qwen.model.BenchmarkMetrics
import com.example.on_device_qwen.ui.theme.QwenPrimary
import com.example.on_device_qwen.ui.theme.QwenSecondary

@Composable
fun BenchmarkBadge(
    benchmark: BenchmarkMetrics,
    isStreaming: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            0.5.dp,
            if (isStreaming) QwenSecondary.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // TPS
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = "TPS",
                    tint = QwenPrimary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = if (benchmark.decodeTps > 0) {
                        String.format(java.util.Locale.US, "%.1f TPS", benchmark.decodeTps)
                    } else if (isStreaming) {
                        "measuring…"
                    } else {
                        "— TPS"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = QwenPrimary
                )
            }

            Text("•", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outlineVariant)

            // TTFT
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = "TTFT",
                    tint = QwenSecondary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = if (benchmark.ttftMs > 0) {
                        "TTFT ${benchmark.ttftMs}ms"
                    } else if (isStreaming) {
                        "TTFT…"
                    } else {
                        "TTFT —"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text("•", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outlineVariant)

            // Backend
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = when {
                    benchmark.backend.contains("NPU", ignoreCase = true) -> Color(0xFF1B5E20).copy(alpha = 0.25f)
                    benchmark.backend.contains("GPU", ignoreCase = true) -> Color(0xFFE65100).copy(alpha = 0.25f)
                    else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = "Backend",
                        tint = when {
                            benchmark.backend.contains("NPU", ignoreCase = true) -> Color(0xFF4CAF50)
                            benchmark.backend.contains("GPU", ignoreCase = true) -> Color(0xFFFF9800)
                            else -> MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = benchmark.backend,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        fontWeight = FontWeight.Bold,
                        color = when {
                            benchmark.backend.contains("NPU", ignoreCase = true) -> Color(0xFF4CAF50)
                            benchmark.backend.contains("GPU", ignoreCase = true) -> Color(0xFFFF9800)
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }

            if (!isStreaming && benchmark.decodeTokens > 0) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${benchmark.decodeTokens} tok" + (if (benchmark.totalDurationMs > 0) " (${String.format(java.util.Locale.US, "%.1fs", benchmark.totalDurationMs / 1000.0)})" else ""),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}
