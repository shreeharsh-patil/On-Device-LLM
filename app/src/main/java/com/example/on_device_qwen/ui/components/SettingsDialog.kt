package com.example.on_device_qwen.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.on_device_qwen.data.preferences.PerformanceMode
import com.example.on_device_qwen.data.preferences.ThemeMode
import com.example.on_device_qwen.domain.performance.DeviceThermalState
import com.example.on_device_qwen.model.BackendType
import com.example.on_device_qwen.ui.theme.QwenPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    performanceMode: PerformanceMode,
    onPerformanceModeChange: (PerformanceMode) -> Unit,
    backendType: BackendType,
    onBackendTypeChange: (BackendType) -> Unit,
    threadCount: Int,
    onThreadCountChange: (Int) -> Unit,
    temperature: Float,
    onTemperatureChange: (Float) -> Unit,
    topP: Float,
    onTopPChange: (Float) -> Unit,
    maxTokens: Int,
    onMaxTokensChange: (Int) -> Unit,
    systemPrompt: String,
    onSystemPromptChange: (String) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    thermalState: DeviceThermalState,
    batteryTemp: Float,
    batteryPercent: Int,
    isCharging: Boolean,
    availRamMb: Long,
    totalRamMb: Long,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.90f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider()

                // Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // 1. Performance Profile
                    SettingsSection(title = "Performance Profile") {
                        PerformanceMode.entries.forEach { mode ->
                            val isSelected = mode == performanceMode
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onPerformanceModeChange(mode) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { onPerformanceModeChange(mode) }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(mode.displayName, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            mode.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }

                    // 2. Hardware Acceleration
                    SettingsSection(title = "Hardware Backend") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BackendType.entries.forEach { b ->
                                FilterChip(
                                    selected = b == backendType,
                                    onClick = { onBackendTypeChange(b) },
                                    label = { Text(b.chipText, fontWeight = FontWeight.Bold) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // 3. CPU Thread Count
                    SettingsSection(title = "Inference Threads: $threadCount") {
                        val maxCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(4)
                        Slider(
                            value = threadCount.toFloat(),
                            onValueChange = { onThreadCountChange(it.toInt()) },
                            valueRange = 2f..maxCores.toFloat(),
                            steps = maxCores - 3
                        )
                        Text(
                            text = "Higher thread count increases TPS but consumes more battery and generates heat.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 4. Generation Hyperparameters
                    SettingsSection(title = "Generation Parameters") {
                        Text(
                            text = "Temperature: ${String.format(java.util.Locale.US, "%.2f", temperature)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Slider(
                            value = temperature,
                            onValueChange = onTemperatureChange,
                            valueRange = 0.1f..1.5f
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = "Top-P: ${String.format(java.util.Locale.US, "%.2f", topP)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Slider(
                            value = topP,
                            onValueChange = onTopPChange,
                            valueRange = 0.1f..1.0f
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = "Max Output Tokens: $maxTokens",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Slider(
                            value = maxTokens.toFloat(),
                            onValueChange = { onMaxTokensChange(it.toInt()) },
                            valueRange = 256f..4096f,
                            steps = 14
                        )
                    }

                    // 5. System Prompt
                    SettingsSection(title = "Custom System Prompt") {
                        OutlinedTextField(
                            value = systemPrompt,
                            onValueChange = onSystemPromptChange,
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 6,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // 6. Theme Mode
                    SettingsSection(title = "Theme") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = mode == themeMode,
                                    onClick = { onThemeModeChange(mode) },
                                    label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // 7. Device Diagnostics
                    SettingsSection(title = "Device Health & Thermal State") {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Thermal Status", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        thermalState.label,
                                        fontWeight = FontWeight.Bold,
                                        color = if (thermalState.isWarning) MaterialTheme.colorScheme.error else QwenPrimary
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Battery Temp", style = MaterialTheme.typography.bodyMedium)
                                    Text("${String.format(java.util.Locale.US, "%.1f", batteryTemp)}°C", fontWeight = FontWeight.Bold)
                                }
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Battery Level", style = MaterialTheme.typography.bodyMedium)
                                    Text("$batteryPercent% ${if (isCharging) "(Charging)" else ""}", fontWeight = FontWeight.Bold)
                                }
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Available RAM", style = MaterialTheme.typography.bodyMedium)
                                    Text("${availRamMb}MB / ${totalRamMb}MB", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        content()
    }
}
