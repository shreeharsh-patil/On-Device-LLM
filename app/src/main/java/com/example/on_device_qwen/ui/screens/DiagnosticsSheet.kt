package com.example.on_device_qwen.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.on_device_qwen.R
import com.example.on_device_qwen.domain.engine.EngineCapabilities
import com.example.on_device_qwen.model.EngineType
import com.example.on_device_qwen.model.ExecutionBackendInfo
import com.example.on_device_qwen.model.ModelMetadata
import com.example.on_device_qwen.model.SupportedParameter
import com.example.on_device_qwen.ui.designsystem.SovereignBadge
import com.example.on_device_qwen.ui.designsystem.SovereignHairlineCard
import com.example.on_device_qwen.ui.designsystem.SovereignModalSheet
import com.example.on_device_qwen.ui.designsystem.SovereignSectionHeader
import com.example.on_device_qwen.ui.designsystem.SovereignStatusTone
import com.example.on_device_qwen.ui.designsystem.SovereignTheme
import com.example.on_device_qwen.viewmodel.DeviceHardwareInfo
import java.util.Locale

@Composable
fun DiagnosticsSheet(
    modelTitle: String,
    activeModel: ModelMetadata?,
    activeEngineType: EngineType,
    backendInfo: ExecutionBackendInfo?,
    capabilities: EngineCapabilities?,
    hardwareInfo: DeviceHardwareInfo,
    supportedParameters: List<SupportedParameter>,
    notices: List<String>,
    onDismiss: () -> Unit,
) {
    val colors = SovereignTheme.colors
    val typography = SovereignTheme.typography
    val spacing = SovereignTheme.spacing

    SovereignModalSheet(
        title = stringResource(R.string.diagnostics_title),
        subtitle = modelTitle,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                SovereignSectionHeader(title = stringResource(R.string.diagnostics_device_section))
                SovereignHairlineCard(modifier = Modifier.fillMaxWidth()) {
                    DiagRow("RAM Free / Total", "${hardwareInfo.availRamMb} MB / ${hardwareInfo.totalRamMb} MB")
                    DiagRow("CPU Cores", "${hardwareInfo.cpuCores} cores")
                    DiagRow("Primary ABI", hardwareInfo.primaryAbi.ifBlank { "arm64-v8a" })
                    DiagRow(
                        "Supported ABIs",
                        hardwareInfo.supportedAbis.joinToString(", ").ifBlank { "arm64-v8a" },
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                SovereignSectionHeader(title = stringResource(R.string.diagnostics_thermal_section))
                SovereignHairlineCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Thermal Status", style = typography.chatProse, color = colors.textSecondary)
                        SovereignBadge(
                            text = hardwareInfo.thermalState.label,
                            tone = if (hardwareInfo.thermalState.isWarning) {
                                SovereignStatusTone.WARNING
                            } else {
                                SovereignStatusTone.SUCCESS
                            },
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    DiagRow(
                        "Battery",
                        "${hardwareInfo.batteryPercent}%${if (hardwareInfo.isCharging) " (Charging)" else ""}",
                    )
                    DiagRow(
                        "Temperature",
                        String.format(Locale.US, "%.1f °C", hardwareInfo.batteryTempCelsius),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                SovereignSectionHeader(title = stringResource(R.string.diagnostics_engine_section))
                SovereignHairlineCard(modifier = Modifier.fillMaxWidth()) {
                    DiagRow("Engine Runtime", activeEngineType.displayName)
                    DiagRow("Model Format", activeModel?.format?.name ?: "None")
                    DiagRow("Architecture", activeModel?.architecture ?: "Unknown")
                    DiagRow("Quantization", activeModel?.quantization ?: "Unknown")
                    backendInfo?.let { info ->
                        DiagRow("Requested Backend", info.requested.chipText)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Active Backend", style = typography.chatProse, color = colors.textSecondary)
                            SovereignBadge(
                                text = if (info.isFallback) "${info.active.chipText} (Fallback)" else info.active.chipText,
                                tone = if (info.isFallback) SovereignStatusTone.WARNING else SovereignStatusTone.SUCCESS,
                            )
                        }
                        info.fallbackReason?.takeIf { it.isNotBlank() }?.let { reason ->
                            Spacer(Modifier.height(4.dp))
                            Text(text = reason, style = typography.bodyCompact, color = colors.amber)
                        }
                        if (info.effectiveContextLength > 0) {
                            DiagRow("Effective Context", "${info.effectiveContextLength} tokens")
                        }
                        if (info.effectiveThreads > 0) {
                            DiagRow("Effective Threads", "${info.effectiveThreads}")
                        }
                    }
                }
            }

            capabilities?.let { caps ->
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    SovereignSectionHeader(title = stringResource(R.string.diagnostics_capabilities_section))
                    SovereignHairlineCard(modifier = Modifier.fillMaxWidth()) {
                        DiagRow("Vision Input", if (caps.supportsVision) "Supported" else "Disabled (Text-only)")
                        caps.unsupportedVisionReason?.takeIf { !caps.supportsVision && it.isNotBlank() }?.let { reason ->
                            Text(text = reason, style = typography.bodyCompact, color = colors.textMuted)
                        }
                        DiagRow("GPU Acceleration", if (caps.supportsGpu) "Available" else "Unavailable")
                        DiagRow("NPU Acceleration", if (caps.supportsNpu) "Available" else "Unavailable")
                        DiagRow("KV Cache Reuse", if (caps.supportsKvReuse) "Yes" else "Per-turn stateless")
                        DiagRow("Max Context / Output", "${caps.maxContextLength} / ${caps.maxOutputTokens}")
                    }
                }
            }

            if (supportedParameters.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    SovereignSectionHeader(title = stringResource(R.string.diagnostics_params_section))
                    SovereignHairlineCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            supportedParameters.forEach { param ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = param.displayName,
                                            style = typography.pillTitle,
                                            color = colors.textPrimary,
                                        )
                                        SovereignBadge(
                                            text = if (param.supportedAtRuntime) "Active" else "Ignored by Runtime",
                                            tone = if (param.supportedAtRuntime) {
                                                SovereignStatusTone.SUCCESS
                                            } else {
                                                SovereignStatusTone.NEUTRAL
                                            },
                                        )
                                    }
                                    Text(
                                        text = param.notes,
                                        style = typography.bodyCompact,
                                        color = colors.textSecondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (notices.isNotEmpty()) {
                SovereignHairlineCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = colors.amber.copy(alpha = 0.45f),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        notices.forEach { note ->
                            Text("• $note", style = typography.bodyCompact, color = colors.amber)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagRow(label: String, value: String) {
    val colors = SovereignTheme.colors
    val typography = SovereignTheme.typography
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = typography.chatProse,
            color = colors.textSecondary,
        )
        Text(
            text = value,
            style = typography.telemetry,
            color = colors.textPrimary,
        )
    }
}
