package com.example.on_device_qwen.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.on_device_qwen.R
import com.example.on_device_qwen.data.preferences.PerformanceMode
import com.example.on_device_qwen.data.preferences.ThemeMode
import com.example.on_device_qwen.model.BackendType
import com.example.on_device_qwen.model.GenerationConfig
import com.example.on_device_qwen.model.PromptProfile
import com.example.on_device_qwen.model.UnrestrictedMode
import com.example.on_device_qwen.ui.designsystem.SovereignAlertDialog
import com.example.on_device_qwen.ui.designsystem.SovereignBadge
import com.example.on_device_qwen.ui.designsystem.SovereignBanner
import com.example.on_device_qwen.ui.designsystem.SovereignDivider
import com.example.on_device_qwen.ui.designsystem.SovereignHairlineCard
import com.example.on_device_qwen.ui.designsystem.SovereignIconButton
import com.example.on_device_qwen.ui.designsystem.SovereignModalSheet
import com.example.on_device_qwen.ui.designsystem.SovereignPrimaryButton
import com.example.on_device_qwen.ui.designsystem.SovereignSecondaryButton
import com.example.on_device_qwen.ui.designsystem.SovereignSectionHeader
import com.example.on_device_qwen.ui.designsystem.SovereignSegmentedControl
import com.example.on_device_qwen.ui.designsystem.SovereignSettingsRow
import com.example.on_device_qwen.ui.designsystem.SovereignSliderRow
import com.example.on_device_qwen.ui.designsystem.SovereignStatusTone
import com.example.on_device_qwen.ui.designsystem.SovereignSwitchRow
import com.example.on_device_qwen.ui.designsystem.SovereignTextField
import com.example.on_device_qwen.ui.designsystem.SovereignTheme
import com.example.on_device_qwen.viewmodel.SettingsUiState
import java.util.Locale
import kotlin.math.roundToInt

private enum class SettingsTab {
    GENERAL, INFERENCE, PROMPTS, DATA
}

@Composable
fun SettingsDialog(
    state: SettingsUiState,
    onThemeModeChange: (ThemeMode) -> Unit,
    onHapticsEnabledChange: (Boolean) -> Unit,
    onPerformanceModeChange: (PerformanceMode) -> Unit,
    onBackendTypeChange: (BackendType) -> Unit,
    onThreadCountChange: (Int) -> Unit,
    onTemperatureChange: (Float) -> Unit,
    onTopPChange: (Float) -> Unit,
    onRepetitionPenaltyChange: (Float) -> Unit,
    onMaxTokensChange: (Int) -> Unit,
    onContextLengthChange: (Int) -> Unit,
    onSystemPromptChange: (String) -> Unit,
    onChatModeChange: (UnrestrictedMode) -> Unit,
    onActiveProfileChange: (PromptProfile) -> Unit,
    onProfileSaved: (PromptProfile) -> String?,
    onProfileDeleted: (String) -> Unit,
    onProfileReset: (String) -> Unit,
    onToggleMemory: (Boolean) -> Unit,
    onManageMemories: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = SovereignTheme.colors
    val typography = SovereignTheme.typography
    val spacing = SovereignTheme.spacing
    val haptic = LocalHapticFeedback.current
    val onSliderFinished: (() -> Unit)? = remember(state.hapticsEnabled, haptic) {
        if (state.hapticsEnabled) {
            { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
        } else {
            null
        }
    }

    var selectedTab by remember { mutableStateOf(SettingsTab.GENERAL) }
    var showAdvancedInference by remember { mutableStateOf(false) }

    var editingProfile by remember { mutableStateOf<PromptProfile?>(null) }
    var isCreatingProfile by remember { mutableStateOf(false) }
    var profileNameDraft by remember { mutableStateOf("") }
    var profileTextDraft by remember { mutableStateOf("") }
    var profileSaveError by remember { mutableStateOf<String?>(null) }
    var profileToDelete by remember { mutableStateOf<PromptProfile?>(null) }

    if (isCreatingProfile || editingProfile != null) {
        val current = editingProfile
        SovereignAlertDialog(
            title = stringResource(if (current == null) R.string.settings_new_profile else R.string.settings_edit_profile),
            confirmText = stringResource(R.string.label_save),
            confirmEnabled = profileNameDraft.isNotBlank() && profileTextDraft.isNotBlank(),
            onConfirm = {
                val candidate = (current ?: PromptProfile(name = "", instructionText = "")).copy(
                    name = profileNameDraft.trim(),
                    instructionText = profileTextDraft.trim(),
                    effectiveSystemPrompt = profileTextDraft.trim(),
                )
                val err = onProfileSaved(candidate)
                if (err == null) {
                    editingProfile = null
                    isCreatingProfile = false
                    profileSaveError = null
                } else {
                    profileSaveError = err
                }
            },
            onDismissRequest = {
                editingProfile = null
                isCreatingProfile = false
                profileSaveError = null
            },
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    SovereignTextField(
                        value = profileNameDraft,
                        onValueChange = { profileNameDraft = it },
                        label = "Profile Name",
                        singleLine = true,
                    )
                    SovereignTextField(
                        value = profileTextDraft,
                        onValueChange = { profileTextDraft = it },
                        label = "System Instruction",
                        minLines = 3,
                        maxLines = 6,
                        errorText = profileSaveError,
                    )
                }
            },
        )
    }

    profileToDelete?.let { target ->
        SovereignAlertDialog(
            title = stringResource(R.string.settings_delete_profile_title),
            text = stringResource(R.string.settings_delete_profile_body, target.name),
            confirmText = stringResource(R.string.label_delete),
            isDestructive = true,
            onConfirm = {
                onProfileDeleted(target.id)
                profileToDelete = null
            },
            onDismissRequest = { profileToDelete = null },
        )
    }

    val tabGeneral = stringResource(R.string.settings_tab_general)
    val tabInference = stringResource(R.string.settings_tab_inference)
    val tabPrompts = stringResource(R.string.settings_tab_prompts)
    val tabData = stringResource(R.string.settings_tab_memory)

    SovereignModalSheet(
        title = stringResource(R.string.settings_title),
        subtitle = "${state.activeEngineType.displayName} · ${state.activeProfile.name}",
        onDismissRequest = onDismiss,
        stickyHeaderExtra = {
            SovereignSegmentedControl(
                options = SettingsTab.entries,
                selected = selectedTab,
                onSelect = { selectedTab = it },
                label = { tab ->
                    when (tab) {
                        SettingsTab.GENERAL -> tabGeneral
                        SettingsTab.INFERENCE -> tabInference
                        SettingsTab.PROMPTS -> tabPrompts
                        SettingsTab.DATA -> tabData
                    }
                },
                hapticFeedback = state.hapticsEnabled,
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            when (selectedTab) {
                SettingsTab.GENERAL -> {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        SovereignSectionHeader(title = stringResource(R.string.settings_section_appearance))
                        SovereignSegmentedControl(
                            options = ThemeMode.entries,
                            selected = state.themeMode,
                            onSelect = onThemeModeChange,
                            label = { mode ->
                                when (mode) {
                                    ThemeMode.SYSTEM -> "System"
                                    ThemeMode.LIGHT -> "Light"
                                    ThemeMode.DARK -> "Dark"
                                }
                            },
                            hapticFeedback = state.hapticsEnabled,
                        )
                        SovereignSwitchRow(
                            title = stringResource(R.string.settings_haptics_title),
                            subtitle = stringResource(R.string.settings_haptics_desc),
                            checked = state.hapticsEnabled,
                            onCheckedChange = onHapticsEnabledChange,
                            leadingIcon = Icons.Rounded.Vibration,
                            hapticFeedback = state.hapticsEnabled,
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        SovereignSectionHeader(title = stringResource(R.string.settings_section_runtime))
                        SovereignSegmentedControl(
                            options = BackendType.entries,
                            selected = state.backendType,
                            onSelect = onBackendTypeChange,
                            label = { it.chipText },
                            hapticFeedback = state.hapticsEnabled,
                        )
                        state.executionBackendInfo?.let { info ->
                            if (info.isFallback) {
                                SovereignBanner(
                                    message = "Fallback active: running on ${info.active.chipText} instead of ${info.requested.chipText}" +
                                        (info.fallbackReason?.let { " — $it" } ?: ""),
                                    tone = SovereignStatusTone.WARNING,
                                )
                            }
                        }

                        SovereignSegmentedControl(
                            options = PerformanceMode.entries,
                            selected = state.performanceMode,
                            onSelect = onPerformanceModeChange,
                            label = { mode ->
                                when (mode) {
                                    PerformanceMode.ECO -> "Eco"
                                    PerformanceMode.BALANCED -> "Balanced"
                                    PerformanceMode.PERFORMANCE -> "Performance"
                                }
                            },
                            hapticFeedback = state.hapticsEnabled,
                        )
                        Text(
                            text = state.performanceMode.description,
                            style = typography.bodyCompact,
                            color = colors.textSecondary,
                        )

                        SovereignHairlineCard(modifier = Modifier.fillMaxWidth()) {
                            SovereignSliderRow(
                                title = "CPU Threads",
                                value = state.threadCount.toFloat(),
                                onValueChange = { onThreadCountChange(it.roundToInt()) },
                                valueRange = GenerationConfig.MIN_THREAD_COUNT.toFloat()..GenerationConfig.MAX_THREAD_COUNT.toFloat(),
                                steps = (GenerationConfig.MAX_THREAD_COUNT - GenerationConfig.MIN_THREAD_COUNT - 1).coerceAtLeast(0),
                                formattedValue = "${state.threadCount} threads",
                                minLabel = "${GenerationConfig.MIN_THREAD_COUNT}",
                                maxLabel = "${GenerationConfig.MAX_THREAD_COUNT}",
                                onValueChangeFinished = onSliderFinished,
                            )
                        }
                    }
                }

                SettingsTab.INFERENCE -> {
                    if (state.validationErrors.isNotEmpty()) {
                        SovereignBanner(
                            message = state.validationErrors.first(),
                            tone = SovereignStatusTone.ERROR,
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        SovereignSectionHeader(title = stringResource(R.string.settings_section_generation))

                        SovereignHairlineCard(modifier = Modifier.fillMaxWidth()) {
                            SovereignSliderRow(
                                title = "Temperature",
                                subtitle = "Lower = focused & deterministic, higher = varied",
                                value = state.temperature,
                                onValueChange = onTemperatureChange,
                                valueRange = GenerationConfig.MIN_TEMPERATURE..GenerationConfig.MAX_TEMPERATURE,
                                formattedValue = String.format(Locale.US, "%.2f", state.temperature),
                                minLabel = "0.0",
                                maxLabel = "2.0",
                                onValueChangeFinished = onSliderFinished,
                            )
                            SovereignDivider(modifier = Modifier.padding(vertical = spacing.xs))
                            SovereignSliderRow(
                                title = "Max Output Tokens",
                                value = state.maxTokens.toFloat(),
                                onValueChange = { onMaxTokensChange(((it / 64f).roundToInt() * 64).coerceAtLeast(64)) },
                                valueRange = 64f..GenerationConfig.MAX_MAX_TOKENS.toFloat(),
                                formattedValue = "${state.maxTokens} tok",
                                minLabel = "64",
                                maxLabel = "${GenerationConfig.MAX_MAX_TOKENS}",
                                onValueChangeFinished = onSliderFinished,
                            )
                        }

                        SovereignSecondaryButton(
                            text = stringResource(
                                if (showAdvancedInference) R.string.settings_advanced_toggle_hide
                                else R.string.settings_advanced_toggle_show,
                            ),
                            onClick = { showAdvancedInference = !showAdvancedInference },
                            leadingIcon = Icons.Rounded.Speed,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        AnimatedVisibility(visible = showAdvancedInference) {
                            SovereignHairlineCard(modifier = Modifier.fillMaxWidth()) {
                                SovereignSliderRow(
                                    title = "Top-P (Nucleus Sampling)",
                                    value = state.topP,
                                    onValueChange = onTopPChange,
                                    valueRange = GenerationConfig.MIN_TOP_P..GenerationConfig.MAX_TOP_P,
                                    formattedValue = String.format(Locale.US, "%.2f", state.topP),
                                    minLabel = "0.0",
                                    maxLabel = "1.0",
                                    onValueChangeFinished = onSliderFinished,
                                )
                                SovereignDivider(modifier = Modifier.padding(vertical = spacing.xs))
                                SovereignSliderRow(
                                    title = "Repetition Penalty",
                                    value = state.repetitionPenalty,
                                    onValueChange = onRepetitionPenaltyChange,
                                    valueRange = GenerationConfig.MIN_REPETITION_PENALTY..GenerationConfig.MAX_REPETITION_PENALTY,
                                    formattedValue = String.format(Locale.US, "%.2f", state.repetitionPenalty),
                                    minLabel = "0.1",
                                    maxLabel = "2.0",
                                    onValueChangeFinished = onSliderFinished,
                                )
                                SovereignDivider(modifier = Modifier.padding(vertical = spacing.xs))
                                SovereignSliderRow(
                                    title = "Context Length",
                                    subtitle = "Token window reserved for history + output",
                                    value = state.contextLength.toFloat(),
                                    onValueChange = {
                                        val snapped = ((it / 512f).roundToInt() * 512).coerceIn(
                                            GenerationConfig.MIN_CONTEXT_LENGTH,
                                            GenerationConfig.MAX_CONTEXT_LENGTH,
                                        )
                                        onContextLengthChange(snapped)
                                    },
                                    valueRange = GenerationConfig.MIN_CONTEXT_LENGTH.toFloat()..GenerationConfig.MAX_CONTEXT_LENGTH.toFloat(),
                                    formattedValue = "${state.contextLength} tok",
                                    minLabel = "${GenerationConfig.MIN_CONTEXT_LENGTH}",
                                    maxLabel = "${GenerationConfig.MAX_CONTEXT_LENGTH}",
                                    onValueChangeFinished = onSliderFinished,
                                )
                            }
                        }
                    }

                    // Honest runtime parameter audit
                    if (state.supportedParameters.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            SovereignSectionHeader(title = stringResource(R.string.settings_runtime_audit_title))
                            SovereignHairlineCard(modifier = Modifier.fillMaxWidth()) {
                                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                                    state.supportedParameters.forEach { param ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = param.displayName,
                                                    style = typography.pillTitle,
                                                    color = colors.textPrimary,
                                                )
                                                Text(
                                                    text = param.notes,
                                                    style = typography.bodyCompact,
                                                    color = colors.textSecondary,
                                                )
                                            }
                                            Spacer(Modifier.size(spacing.sm))
                                            SovereignBadge(
                                                text = if (param.supportedAtRuntime) "Active" else "App Only",
                                                tone = if (param.supportedAtRuntime) {
                                                    SovereignStatusTone.SUCCESS
                                                } else {
                                                    SovereignStatusTone.NEUTRAL
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                SettingsTab.PROMPTS -> {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        SovereignSectionHeader(title = stringResource(R.string.settings_section_profiles))
                        SovereignSegmentedControl(
                            options = UnrestrictedMode.entries,
                            selected = state.chatMode,
                            onSelect = onChatModeChange,
                            label = { it.displayName },
                            hapticFeedback = state.hapticsEnabled,
                        )
                        Text(
                            text = if (state.chatMode == UnrestrictedMode.UNRESTRICTED) {
                                "Unrestricted applies a minimal, non-preachy system instruction at the app layer. Model-weight behaviors remain governed by the loaded weights."
                            } else {
                                "Standard applies helpful assistant framing and balanced response defaults."
                            },
                            style = typography.bodyCompact,
                            color = colors.textSecondary,
                        )
                    }

                    SovereignPrimaryButton(
                        text = stringResource(R.string.settings_new_profile),
                        onClick = {
                            editingProfile = null
                            isCreatingProfile = true
                            profileNameDraft = ""
                            profileTextDraft = ""
                            profileSaveError = null
                        },
                        leadingIcon = Icons.Rounded.Add,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        state.profiles.forEach { profile ->
                            val isSelected = profile.id == state.activeProfile.id
                            val isBuiltIn = profile.id == PromptProfile.DEFAULT_ID ||
                                profile.id == PromptProfile.UNRESTRICTED_ID

                            SovereignHairlineCard(
                                modifier = Modifier.fillMaxWidth(),
                                selected = isSelected,
                                onClick = { onActiveProfileChange(profile) },
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(
                                            text = profile.name,
                                            style = typography.cardTitle,
                                            color = colors.textPrimary,
                                        )
                                        if (isSelected) {
                                            SovereignBadge(
                                                text = stringResource(R.string.label_active),
                                                tone = SovereignStatusTone.ACCENT,
                                            )
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        SovereignIconButton(
                                            onClick = {
                                                isCreatingProfile = false
                                                editingProfile = profile
                                                profileNameDraft = profile.name
                                                profileTextDraft = profile.instructionText
                                                profileSaveError = null
                                            },
                                            contentDescription = "Edit profile",
                                        ) {
                                            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                        }
                                        if (isBuiltIn) {
                                            SovereignIconButton(
                                                onClick = { onProfileReset(profile.id) },
                                                contentDescription = "Reset built-in profile",
                                            ) {
                                                Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                            }
                                        } else {
                                            SovereignIconButton(
                                                onClick = { profileToDelete = profile },
                                                contentDescription = "Delete profile",
                                                contentColor = colors.error,
                                            ) {
                                                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(spacing.xs))
                                Text(
                                    text = profile.instructionText,
                                    style = typography.bodyCompact,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                    }

                    SovereignTextField(
                        value = state.systemPrompt,
                        onValueChange = onSystemPromptChange,
                        label = "Active System Prompt",
                        minLines = 3,
                        maxLines = 6,
                    )
                }

                SettingsTab.DATA -> {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        SovereignSectionHeader(title = stringResource(R.string.settings_section_privacy))
                        SovereignSwitchRow(
                            title = stringResource(R.string.memory_enable_title),
                            subtitle = stringResource(R.string.memory_enable_desc),
                            checked = state.persistentMemoryEnabled,
                            onCheckedChange = onToggleMemory,
                            leadingIcon = Icons.Rounded.Psychology,
                            hapticFeedback = state.hapticsEnabled,
                        )
                        SovereignSettingsRow(
                            title = stringResource(R.string.settings_manage_memories),
                            leadingIcon = Icons.Rounded.Psychology,
                            showChevron = true,
                            onClick = onManageMemories,
                        )
                        SovereignSettingsRow(
                            title = stringResource(R.string.settings_open_diagnostics),
                            subtitle = "${state.hardwareInfo.availRamMb} MB RAM free · ${state.hardwareInfo.thermalState.label}",
                            leadingIcon = Icons.Rounded.MonitorHeart,
                            showChevron = true,
                            onClick = onOpenDiagnostics,
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        SovereignSectionHeader(title = stringResource(R.string.settings_section_about))
                        SovereignHairlineCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shield,
                                    contentDescription = null,
                                    tint = colors.accentPrimary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = stringResource(R.string.app_display_name),
                                    style = typography.cardTitle,
                                    color = colors.textPrimary,
                                )
                            }
                            Spacer(Modifier.height(spacing.xs))
                            Text(
                                text = stringResource(R.string.empty_chat_subtitle),
                                style = typography.bodyCompact,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}
