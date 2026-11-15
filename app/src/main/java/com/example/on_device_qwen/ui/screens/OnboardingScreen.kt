package com.example.on_device_qwen.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.on_device_qwen.R
import com.example.on_device_qwen.model.Modality
import com.example.on_device_qwen.ui.designsystem.SovereignBadge
import com.example.on_device_qwen.ui.designsystem.SovereignErrorCard
import com.example.on_device_qwen.ui.designsystem.SovereignHairlineCard
import com.example.on_device_qwen.ui.designsystem.SovereignPrimaryButton
import com.example.on_device_qwen.ui.designsystem.SovereignSecondaryButton
import com.example.on_device_qwen.ui.designsystem.SovereignStatusTone
import com.example.on_device_qwen.ui.designsystem.SovereignTheme
import com.example.on_device_qwen.ui.designsystem.SovereignTonalButton
import com.example.on_device_qwen.viewmodel.CatalogModelRecommendation
import com.example.on_device_qwen.viewmodel.DeviceHardwareInfo
import com.example.on_device_qwen.viewmodel.EngineState
import java.io.File
import java.util.Locale

@Composable
fun OnboardingScreen(
    engineState: EngineState,
    hardwareInfo: DeviceHardwareInfo,
    recommendations: List<CatalogModelRecommendation>,
    showFirstRunGuide: Boolean,
    onImportModelClick: () -> Unit,
    onOpenModelHub: () -> Unit,
    onRetryEngine: () -> Unit,
    onSelectCandidate: (File) -> Unit,
    onContinueDismissGuide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SovereignTheme.colors
    val typography = SovereignTheme.typography
    val spacing = SovereignTheme.spacing
    val adaptive = SovereignTheme.adaptive

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = adaptive.maxContentWidth)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = adaptive.horizontalGutter, vertical = spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            when (engineState) {
                is EngineState.Loading -> {
                    SovereignHairlineCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(spacing.xl),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.md),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.5.dp,
                                color = colors.accentPrimary,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.onboarding_loading_title),
                                    style = typography.cardTitle,
                                    color = colors.textPrimary,
                                    modifier = Modifier.semantics { heading() },
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = engineState.progress,
                                    style = typography.bodyCompact,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                        Spacer(Modifier.height(spacing.md))
                        SovereignSecondaryButton(
                            text = stringResource(R.string.label_models),
                            onClick = onOpenModelHub,
                            leadingIcon = Icons.Rounded.Storage,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                is EngineState.Error -> {
                    SovereignErrorCard(
                        title = engineState.title,
                        message = engineState.message,
                        actions = {
                            if (engineState.candidateFile != null) {
                                SovereignPrimaryButton(
                                    text = "Load ${engineState.candidateFile.name}",
                                    onClick = { onSelectCandidate(engineState.candidateFile) },
                                    leadingIcon = Icons.Rounded.AutoAwesome,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(spacing.sm))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                            ) {
                                SovereignTonalButton(
                                    text = stringResource(R.string.onboarding_import_cta),
                                    onClick = onImportModelClick,
                                    leadingIcon = Icons.Rounded.FileOpen,
                                    modifier = Modifier.weight(1f),
                                )
                                SovereignSecondaryButton(
                                    text = stringResource(R.string.label_retry),
                                    onClick = onRetryEngine,
                                    leadingIcon = Icons.Rounded.Refresh,
                                )
                            }
                        },
                    )
                }

                is EngineState.Idle,
                is EngineState.Ready -> Unit
            }

            if (showFirstRunGuide || engineState is EngineState.Idle) {
                SovereignBadge(
                    text = stringResource(R.string.onboarding_badge),
                    tone = SovereignStatusTone.ACCENT,
                    leadingIcon = Icons.Rounded.Lock,
                )

                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Text(
                        text = stringResource(R.string.onboarding_title),
                        style = typography.editorialGreeting,
                        color = colors.textPrimary,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(R.string.onboarding_subtitle),
                        style = typography.chatProse,
                        color = colors.textSecondary,
                    )
                }

                HardwareProfileCard(hardwareInfo = hardwareInfo)

                if (recommendations.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Text(
                            text = stringResource(R.string.onboarding_recommended_title).uppercase(),
                            style = typography.monoTag,
                            color = colors.textMuted,
                            modifier = Modifier.semantics { heading() },
                        )
                        recommendations.take(3).forEach { rec ->
                            CatalogRecommendationCard(
                                recommendation = rec,
                                onInspectClick = onOpenModelHub,
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    SovereignPrimaryButton(
                        text = stringResource(R.string.onboarding_import_cta),
                        onClick = onImportModelClick,
                        leadingIcon = Icons.Rounded.FolderOpen,
                        useAccent = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        SovereignSecondaryButton(
                            text = stringResource(R.string.onboarding_browse_hub_cta),
                            onClick = onOpenModelHub,
                            leadingIcon = Icons.Rounded.Storage,
                            modifier = Modifier.weight(1f),
                        )
                        if (showFirstRunGuide) {
                            SovereignSecondaryButton(
                                text = stringResource(R.string.onboarding_continue_cta),
                                onClick = onContinueDismissGuide,
                            )
                        }
                    }
                }
            } else if (engineState is EngineState.Error) {
                HardwareProfileCard(hardwareInfo = hardwareInfo)
                SovereignSecondaryButton(
                    text = stringResource(R.string.onboarding_browse_hub_cta),
                    onClick = onOpenModelHub,
                    leadingIcon = Icons.Rounded.Storage,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun HardwareProfileCard(hardwareInfo: DeviceHardwareInfo) {
    val colors = SovereignTheme.colors
    val typography = SovereignTheme.typography
    val spacing = SovereignTheme.spacing

    val totalStr = formatRam(hardwareInfo.totalRamMb)
    val availStr = formatRam(hardwareInfo.availRamMb)
    val abiStr = hardwareInfo.primaryAbi.ifBlank { "arm64-v8a" }
    val backendsStr = hardwareInfo.availableBackends.joinToString(" · ") { it.chipText }

    SovereignHairlineCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Icon(
                imageVector = Icons.Rounded.Memory,
                contentDescription = null,
                tint = colors.accentPrimary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.onboarding_specs_title),
                style = typography.cardTitle,
                color = colors.textPrimary,
            )
        }
        Spacer(Modifier.height(spacing.sm))
        Text(
            text = stringResource(R.string.onboarding_specs_ram, availStr, totalStr),
            style = typography.bodyCompact,
            color = colors.textSecondary,
        )
        Text(
            text = stringResource(R.string.onboarding_specs_cpu, hardwareInfo.cpuCores, abiStr),
            style = typography.bodyCompact,
            color = colors.textSecondary,
        )
        Text(
            text = stringResource(R.string.onboarding_specs_backends, backendsStr),
            style = typography.telemetry,
            color = colors.textPrimary,
        )
    }
}

@Composable
private fun CatalogRecommendationCard(
    recommendation: CatalogModelRecommendation,
    onInspectClick: () -> Unit,
) {
    val colors = SovereignTheme.colors
    val typography = SovereignTheme.typography
    val spacing = SovereignTheme.spacing
    val item = recommendation.item

    val tone = when {
        recommendation.isRecommendedFit -> SovereignStatusTone.SUCCESS
        recommendation.fitsDeviceRam -> SovereignStatusTone.WARNING
        else -> SovereignStatusTone.ERROR
    }

    SovereignHairlineCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onInspectClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = typography.cardTitle,
                    color = colors.textPrimary,
                )
                Text(
                    text = "${item.format.extension.uppercase()} · ${item.quantization} · ${item.fileSizeFormatted}",
                    style = typography.telemetry,
                    color = colors.textMuted,
                )
            }
            SovereignBadge(
                text = if (recommendation.isRecommendedFit) "Recommended" else if (recommendation.fitsDeviceRam) "Tight Fit" else "Heavy",
                tone = tone,
            )
        }
        Spacer(Modifier.height(spacing.xs))
        Text(
            text = recommendation.fitSummary,
            style = typography.bodyCompact,
            color = colors.textSecondary,
        )
        if (Modality.VISION in item.modalities) {
            Spacer(Modifier.height(spacing.xs))
            SovereignBadge(
                text = "Vision Capable",
                tone = SovereignStatusTone.VIOLET,
            )
        }
    }
}

private fun formatRam(ramMb: Long): String {
    if (ramMb <= 0L) return "Unknown"
    return if (ramMb >= 1024L) {
        String.format(Locale.US, "%.1f GB", ramMb / 1024.0)
    } else {
        "$ramMb MB"
    }
}
