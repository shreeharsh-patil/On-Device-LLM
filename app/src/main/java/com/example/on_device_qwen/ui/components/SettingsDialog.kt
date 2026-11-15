package com.example.on_device_qwen.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.WarningAmber
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.on_device_qwen.data.preferences.PerformanceMode
import com.example.on_device_qwen.data.preferences.ThemeMode
import com.example.on_device_qwen.domain.performance.DeviceThermalState
import com.example.on_device_qwen.model.BackendType
import com.example.on_device_qwen.model.GenerationConfig
import com.example.on_device_qwen.model.PromptProfile
import com.example.on_device_qwen.model.UnrestrictedMode
import com.example.on_device_qwen.model.sanitizeSystemPrompt
import com.example.on_device_qwen.ui.designsystem.SovereignAlertDialog
import com.example.on_device_qwen.ui.designsystem.SovereignBadge
import com.example.on_device_qwen.ui.designsystem.SovereignDivider
import com.example.on_device_qwen.ui.designsystem.SovereignElevation
import com.example.on_device_qwen.ui.designsystem.SovereignHairlineCard
import com.example.on_device_qwen.ui.designsystem.SovereignIconButton
import com.example.on_device_qwen.ui.designsystem.SovereignModalSheet
import com.example.on_device_qwen.ui.designsystem.SovereignPrimaryButton
import com.example.on_device_qwen.ui.designsystem.SovereignSecondaryButton
import com.example.on_device_qwen.ui.designsystem.SovereignSectionHeader
import com.example.on_device_qwen.ui.designsystem.SovereignSegmentedControl
import com.example.on_device_qwen.ui.designsystem.SovereignSettingsRow
import com.example.on_device_qwen.ui.designsystem.SovereignSliderRow
import com.example.on_device_qwen.ui.designsystem.SovereignStatusDot
import com.example.on_device_qwen.ui.designsystem.SovereignStatusTone
import com.example.on_device_qwen.ui.designsystem.SovereignSurface
import com.example.on_device_qwen.ui.designsystem.SovereignSwitchRow
import com.example.on_device_qwen.ui.designsystem.SovereignTextField
import com.example.on_device_qwen.ui.designsystem.SovereignTheme
import com.example.on_device_qwen.ui.designsystem.SovereignTypography
import java.util.Locale
import kotlin.math.roundToInt

private const val REPETITION_PENALTY_MIN = 0.5f
private const val REPETITION_PENALTY_MAX = 2.0f
private const val MAX_OUTPUT_TOKENS_SLIDER_MIN = 256
private const val MAX_OUTPUT_TOKENS_SLIDER_STEP = 256
private const val CONTEXT_LENGTH_SLIDER_STEP = 1024
private const val DRAFT_PROFILE_ID = "__sovereign_draft_profile__"

internal data class ThreadSliderBounds(
    val minThreads: Int,
    val maxThreads: Int,
    val steps: Int,
    val isAdjustable: Boolean,
)

internal fun resolveThreadSliderBounds(availableProcessors: Int): ThreadSliderBounds {
    val safeCores = availableProcessors.coerceAtLeast(1)
    val minThreads = GenerationConfig.MIN_THREAD_COUNT
    val maxThreads = GenerationConfig.MAX_THREAD_COUNT.coerceAtMost(safeCores).coerceAtLeast(minThreads)
    val isAdjustable = maxThreads > minThreads
    val steps = (maxThreads - minThreads - 1).coerceAtLeast(0)
    return ThreadSliderBounds(
        minThreads = minThreads,
        maxThreads = maxThreads,
        steps = steps,
        isAdjustable = isAdjustable,
    )
}

internal fun isBuiltInProfile(profileId: String): Boolean =
    profileId == PromptProfile.DEFAULT_ID || profileId == PromptProfile.UNRESTRICTED_ID

internal fun resolveProfileToSave(
    activeProfile: PromptProfile,
    profiles: List<PromptProfile>,
    editorName: String,
    editorText: String,
    isCreatingNew: Boolean,
    newProfileId: String = DRAFT_PROFILE_ID,
): PromptProfile {
    val trimmedName = editorName.trim()
    if (isCreatingNew) {
        return PromptProfile(
            id = newProfileId,
            name = trimmedName,
            instructionText = editorText,
            effectiveSystemPrompt = editorText,
        )
    }

    val existingByName = profiles.firstOrNull {
        it.name.equals(trimmedName, ignoreCase = true)
    }
    val base = when {
        existingByName != null -> existingByName
        // Avoid overwriting a built-in profile's identity when saving under a new custom name.
        isBuiltInProfile(activeProfile.id) && !trimmedName.equals(activeProfile.name, ignoreCase = true) ->
            PromptProfile(
                id = newProfileId,
                name = trimmedName,
                instructionText = editorText,
                effectiveSystemPrompt = editorText,
            )
        else -> activeProfile
    }
    return base.copy(
        name = trimmedName,
        instructionText = editorText,
        effectiveSystemPrompt = editorText,
    )
}

internal fun mapThermalSeverityTone(state: DeviceThermalState): SovereignStatusTone = when (state) {
    DeviceThermalState.NOMINAL -> SovereignStatusTone.SUCCESS
    DeviceThermalState.LIGHT -> SovereignStatusTone.ACCENT
    DeviceThermalState.MODERATE -> SovereignStatusTone.WARNING
    DeviceThermalState.SEVERE,
    DeviceThermalState.CRITICAL -> SovereignStatusTone.ERROR
}

internal fun computeAvailableRamFraction(availRamMb: Long, totalRamMb: Long): Float {
    if (totalRamMb <= 0L) return 0f
    return (availRamMb.toFloat() / totalRamMb.toFloat()).coerceIn(0f, 1f)
}

@OptIn(ExperimentalLayoutApi::class)
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
    chatMode: UnrestrictedMode,
    onChatModeChange: (UnrestrictedMode) -> Unit,
    activeProfile: PromptProfile,
    onActiveProfileChange: (PromptProfile) -> Unit,
    profiles: List<PromptProfile>,
    onProfileSaved: (PromptProfile) -> Unit,
    onProfileDeleted: (String) -> Unit,
    onProfileReset: (String) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    thermalState: DeviceThermalState,
    batteryTemp: Float,
    batteryPercent: Int,
    isCharging: Boolean,
    availRamMb: Long,
    totalRamMb: Long,
    isMemoryEnabled: Boolean = true,
    onToggleMemory: (Boolean) -> Unit = {},
    onManageMemories: () -> Unit = {},
    onDismiss: () -> Unit,
    repetitionPenalty: Float = 1.15f,
    onRepetitionPenaltyChange: (Float) -> Unit = {},
    contextLength: Int = 4096,
    onContextLengthChange: (Int) -> Unit = {},
    hapticsEnabled: Boolean = true,
    onHapticsEnabledChange: (Boolean) -> Unit = {},
    runtimeNotices: List<String> = emptyList(),
    memoryCount: Int = 0,
) {
    val colors = SovereignTheme.colors
    val spacing = SovereignTheme.spacing
    val shapes = SovereignTheme.shapes
    val adaptive = SovereignTheme.adaptive

    var isCreatingNewProfile by remember { mutableStateOf(false) }
    var profilePendingDelete by remember { mutableStateOf<PromptProfile?>(null) }

    var editorName by remember(activeProfile.id, activeProfile.name, isCreatingNewProfile) {
        mutableStateOf(if (isCreatingNewProfile) "" else activeProfile.name)
    }
    var editorText by remember(
        activeProfile.id,
        activeProfile.instructionText,
        systemPrompt,
        isCreatingNewProfile,
    ) {
        mutableStateOf(
            if (isCreatingNewProfile) "" else activeProfile.instructionText.ifBlank { systemPrompt }
        )
    }
    var savedConfirmationName by remember { mutableStateOf<String?>(null) }

    val candidateProfile = remember(
        isCreatingNewProfile,
        activeProfile,
        profiles,
        editorName,
        editorText,
    ) {
        resolveProfileToSave(
            activeProfile = activeProfile,
            profiles = profiles,
            editorName = editorName,
            editorText = editorText,
            isCreatingNew = isCreatingNewProfile,
        )
    }
    val validationError = remember(candidateProfile) { candidateProfile.validate() }
    val isDirty = isCreatingNewProfile ||
        editorName.trim() != activeProfile.name ||
        editorText != activeProfile.instructionText.ifBlank { systemPrompt }

    profilePendingDelete?.let { target ->
        SovereignAlertDialog(
            title = "Delete Prompt Profile",
            text = "Permanently delete \"${target.name}\"? This custom prompt profile cannot be recovered.",
            confirmText = "Delete",
            dismissText = "Cancel",
            isDestructive = true,
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = colors.error,
                    modifier = Modifier.size(20.dp),
                )
            },
            onConfirm = {
                onProfileDeleted(target.id)
                if (target.id == activeProfile.id) {
                    isCreatingNewProfile = false
                }
                profilePendingDelete = null
            },
            onDismissRequest = { profilePendingDelete = null },
        )
    }

    SovereignModalSheet(
        title = "Settings",
        subtitle = "${chatMode.displayName} · ${activeProfile.name}",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = spacing.screenGutter(adaptive),
                    vertical = spacing.lg,
                ),
            verticalArrangement = Arrangement.spacedBy(spacing.xl),
        ) {
            // ── 1. General & Appearance ───────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                SovereignSectionHeader(title = "General & Appearance")
                SovereignHairlineCard(contentPadding = PaddingValues(0.dp)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Theme",
                            style = SovereignTypography.pillTitle,
                            color = colors.textPrimary,
                        )
                        SovereignSegmentedControl(
                            options = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK),
                            selected = themeMode,
                            onSelect = onThemeModeChange,
                            label = { mode ->
                                when (mode) {
                                    ThemeMode.SYSTEM -> "System"
                                    ThemeMode.LIGHT -> "Light"
                                    ThemeMode.DARK -> "Dark"
                                }
                            },
                            icon = { mode ->
                                when (mode) {
                                    ThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto
                                    ThemeMode.LIGHT -> Icons.Rounded.LightMode
                                    ThemeMode.DARK -> Icons.Rounded.DarkMode
                                }
                            },
                        )
                    }
                    SovereignDivider()
                    SovereignSwitchRow(
                        title = "Haptic Feedback",
                        subtitle = "Tactile pulse when sending, switching modes, or adjusting controls",
                        leadingIcon = Icons.Rounded.Vibration,
                        checked = hapticsEnabled,
                        onCheckedChange = onHapticsEnabledChange,
                    )
                }
            }

            // ── 2. AI Mode & Prompt Profiles ──────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                SovereignSectionHeader(title = "AI Mode & Prompt Profiles")

                // 2a. Inference Mode Selector
                val isUnrestricted = chatMode == UnrestrictedMode.UNRESTRICTED
                SovereignHairlineCard(
                    contentPadding = PaddingValues(16.dp),
                    borderColor = if (isUnrestricted) colors.amber.copy(alpha = 0.5f) else colors.hairlineSubtle,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Inference Mode",
                            style = SovereignTypography.pillTitle,
                            color = colors.textPrimary,
                        )
                        SovereignBadge(
                            text = chatMode.displayName,
                            tone = if (isUnrestricted) SovereignStatusTone.WARNING else SovereignStatusTone.ACCENT,
                            leadingDot = true,
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        InferenceModeOptionCard(
                            mode = UnrestrictedMode.STANDARD,
                            selected = chatMode == UnrestrictedMode.STANDARD,
                            onClick = { onChatModeChange(UnrestrictedMode.STANDARD) },
                            modifier = Modifier.weight(1f),
                        )
                        InferenceModeOptionCard(
                            mode = UnrestrictedMode.UNRESTRICTED,
                            selected = chatMode == UnrestrictedMode.UNRESTRICTED,
                            onClick = { onChatModeChange(UnrestrictedMode.UNRESTRICTED) },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    SovereignSurface(
                        shape = shapes.sm,
                        color = if (isUnrestricted) colors.amberContainer else colors.surfaceBase,
                        border = BorderStroke(
                            SovereignElevation.hairlineWidth,
                            if (isUnrestricted) colors.amber.copy(alpha = 0.45f) else colors.hairlineSubtle,
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                imageVector = if (isUnrestricted) Icons.Rounded.WarningAmber else Icons.Rounded.Info,
                                contentDescription = null,
                                tint = if (isUnrestricted) colors.amber else colors.textSecondary,
                                modifier = Modifier
                                    .padding(top = 1.dp)
                                    .size(16.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = if (isUnrestricted) {
                                    "Unrestricted offline prompt mode active. Updates the on-device system prompt for direct technical output; it does not alter weights or limits inside the model file."
                                } else {
                                    "Standard mode applies a balanced on-device system prompt. All inference remains 100% offline."
                                },
                                style = SovereignTypography.bodyCompact,
                                color = if (isUnrestricted) colors.onAmberContainer else colors.textSecondary,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // 2b. Prompt Profile Selector Chips
                SovereignHairlineCard(contentPadding = PaddingValues(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Prompt Profiles",
                            style = SovereignTypography.pillTitle,
                            color = colors.textPrimary,
                        )
                        SovereignBadge(
                            text = "${profiles.size} saved",
                            tone = SovereignStatusTone.NEUTRAL,
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        profiles.forEach { profile ->
                            val isSelected = !isCreatingNewProfile && profile.id == activeProfile.id
                            val builtIn = isBuiltInProfile(profile.id)
                            val canDelete = !builtIn && profiles.size > 1
                            PromptProfileChip(
                                profile = profile,
                                selected = isSelected,
                                isBuiltIn = builtIn,
                                canDelete = canDelete,
                                onSelect = {
                                    isCreatingNewProfile = false
                                    savedConfirmationName = null
                                    onActiveProfileChange(profile)
                                },
                                onReset = {
                                    onProfileReset(profile.id)
                                    if (profile.id == activeProfile.id) {
                                        isCreatingNewProfile = false
                                        PromptProfile.builtInProfiles()
                                            .firstOrNull { it.id == profile.id }
                                            ?.let { defaultProfile ->
                                                editorName = defaultProfile.name
                                                editorText = defaultProfile.instructionText
                                            }
                                    }
                                    savedConfirmationName = null
                                },
                                onRequestDelete = {
                                    profilePendingDelete = profile
                                },
                            )
                        }

                        // New custom profile chip
                        Surface(
                            onClick = {
                                isCreatingNewProfile = true
                                editorName = ""
                                editorText = ""
                                savedConfirmationName = null
                            },
                            shape = shapes.sm,
                            color = if (isCreatingNewProfile) colors.accentContainer else colors.surfaceBase,
                            contentColor = if (isCreatingNewProfile) colors.onAccentContainer else colors.textPrimary,
                            border = BorderStroke(
                                SovereignElevation.hairlineWidth,
                                if (isCreatingNewProfile) colors.accentPrimary else colors.hairlineStrong,
                            ),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .semantics {
                                    role = Role.Button
                                    selected = isCreatingNewProfile
                                },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = "New Profile",
                                    style = SovereignTypography.actionLabel,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // 2c. Editable Active Profile & System Prompt Card
                val nameError = remember(candidateProfile) {
                    candidateProfile.copy(instructionText = "valid").validate()
                }
                val instructionError = remember(candidateProfile) {
                    candidateProfile.copy(name = "valid").validate()
                }
                val instructionCountColor = when {
                    editorText.length > PromptProfile.MAX_INSTRUCTION_CHARS -> colors.error
                    editorText.length >= (PromptProfile.MAX_INSTRUCTION_CHARS * 0.9f).toInt() -> colors.amber
                    else -> colors.textMuted
                }

                SovereignHairlineCard(
                    contentPadding = PaddingValues(16.dp),
                    borderColor = if (validationError != null) colors.error.copy(alpha = 0.55f) else colors.hairlineSubtle,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (isCreatingNewProfile) {
                                "Create Prompt Profile"
                            } else {
                                "Active Profile & System Prompt"
                            },
                            style = SovereignTypography.pillTitle,
                            color = colors.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        when {
                            validationError != null -> SovereignBadge(
                                text = "Invalid",
                                tone = SovereignStatusTone.ERROR,
                                leadingDot = true,
                            )
                            isDirty -> SovereignBadge(
                                text = "Unsaved",
                                tone = SovereignStatusTone.WARNING,
                            )
                            savedConfirmationName != null -> SovereignBadge(
                                text = "Saved",
                                tone = SovereignStatusTone.SUCCESS,
                                leadingDot = true,
                            )
                            else -> SovereignBadge(
                                text = if (isBuiltInProfile(activeProfile.id)) "Built-in" else "Custom",
                                tone = SovereignStatusTone.ACCENT,
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    SovereignTextField(
                        value = editorName,
                        onValueChange = {
                            editorName = it
                            savedConfirmationName = null
                        },
                        label = "Profile Name",
                        placeholder = "e.g. Technical Reviewer",
                        singleLine = true,
                        errorText = nameError,
                        isError = nameError != null,
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "System Prompt / Instruction",
                            style = SovereignTypography.actionLabel,
                            color = if (instructionError != null) colors.error else colors.textSecondary,
                        )
                        Text(
                            text = "${editorText.length} / ${PromptProfile.MAX_INSTRUCTION_CHARS}",
                            style = SovereignTypography.telemetry,
                            color = instructionCountColor,
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    SovereignTextField(
                        value = editorText,
                        onValueChange = {
                            editorText = it
                            savedConfirmationName = null
                        },
                        placeholder = "Specify assistant role, output structure, and constraints…",
                        minLines = 3,
                        maxLines = 7,
                        errorText = instructionError,
                        isError = instructionError != null,
                    )

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (isCreatingNewProfile || isDirty) {
                            SovereignSecondaryButton(
                                text = if (isCreatingNewProfile) "Cancel" else "Discard",
                                onClick = {
                                    isCreatingNewProfile = false
                                    editorName = activeProfile.name
                                    editorText = activeProfile.instructionText.ifBlank { systemPrompt }
                                    savedConfirmationName = null
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        SovereignPrimaryButton(
                            text = if (isCreatingNewProfile) "Create & Apply" else "Save & Apply",
                            leadingIcon = Icons.Rounded.Save,
                            useAccent = true,
                            enabled = validationError == null,
                            hapticFeedback = true,
                            onClick = {
                                val cleanPrompt = sanitizeSystemPrompt(
                                    candidateProfile.effectiveSystemPrompt.ifBlank { candidateProfile.instructionText }
                                )
                                val resolvedId = if (candidateProfile.id == DRAFT_PROFILE_ID) {
                                    java.util.UUID.randomUUID().toString()
                                } else {
                                    candidateProfile.id
                                }
                                val savedProfile = candidateProfile.copy(
                                    id = resolvedId,
                                    instructionText = candidateProfile.instructionText.trim(),
                                    effectiveSystemPrompt = cleanPrompt,
                                )
                                onProfileSaved(savedProfile)
                                onActiveProfileChange(savedProfile)
                                onSystemPromptChange(cleanPrompt)
                                isCreatingNewProfile = false
                                savedConfirmationName = savedProfile.name
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // ── 3. Inference & Performance ────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                SovereignSectionHeader(title = "Inference & Performance")

                // 3a. PerformanceMode selector
                SovereignHairlineCard(contentPadding = PaddingValues(16.dp)) {
                    Text(
                        text = "Performance Profile",
                        style = SovereignTypography.pillTitle,
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PerformanceMode.entries.forEach { mode ->
                            PerformanceModeOptionCard(
                                mode = mode,
                                selected = mode == performanceMode,
                                onClick = { onPerformanceModeChange(mode) },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // 3b. Hardware Backend + CPU Threads
                SovereignHairlineCard(contentPadding = PaddingValues(0.dp)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Hardware Backend",
                                style = SovereignTypography.pillTitle,
                                color = colors.textPrimary,
                            )
                            SovereignBadge(
                                text = backendType.chipText,
                                tone = SovereignStatusTone.ACCENT,
                            )
                        }

                        SovereignSegmentedControl(
                            options = BackendType.entries,
                            selected = backendType,
                            onSelect = onBackendTypeChange,
                            label = { it.chipText },
                            icon = { backend ->
                                when (backend) {
                                    BackendType.CPU -> Icons.Rounded.Memory
                                    BackendType.GPU -> Icons.Rounded.DeveloperBoard
                                    BackendType.NPU -> Icons.Rounded.AutoAwesome
                                }
                            },
                        )

                        Text(
                            text = "${backendType.label}. Switching backend reloads the active model.",
                            style = SovereignTypography.bodyCompact,
                            color = colors.textSecondary,
                        )
                    }

                    SovereignDivider()

                    val availableCores = remember { Runtime.getRuntime().availableProcessors().coerceAtLeast(1) }
                    val threadBounds = remember(availableCores) { resolveThreadSliderBounds(availableCores) }
                    val clampedThreadCount = threadCount.coerceIn(threadBounds.minThreads, threadBounds.maxThreads)

                    SovereignSliderRow(
                        title = "CPU Thread Count",
                        subtitle = if (threadBounds.isAdjustable) {
                            "Higher counts increase decode speed but add heat and battery draw"
                        } else {
                            "Single-core CPU detected (${threadBounds.minThreads} thread fixed)"
                        },
                        value = if (threadBounds.isAdjustable) clampedThreadCount.toFloat() else 1f,
                        onValueChange = { raw ->
                            if (threadBounds.isAdjustable) {
                                onThreadCountChange(
                                    raw.roundToInt().coerceIn(threadBounds.minThreads, threadBounds.maxThreads)
                                )
                            }
                        },
                        valueRange = if (threadBounds.isAdjustable) {
                            threadBounds.minThreads.toFloat()..threadBounds.maxThreads.toFloat()
                        } else {
                            1f..2f
                        },
                        steps = threadBounds.steps,
                        enabled = threadBounds.isAdjustable,
                        formattedValue = "$clampedThreadCount ${if (clampedThreadCount == 1) "thread" else "threads"}",
                        minLabel = "${threadBounds.minThreads}",
                        maxLabel = "${threadBounds.maxThreads}",
                    )
                }
            }

            // ── 4. Generation Parameters ──────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                SovereignSectionHeader(title = "Generation Parameters")

                SovereignHairlineCard(contentPadding = PaddingValues(0.dp)) {
                    val clampedTemp = temperature.coerceIn(
                        GenerationConfig.MIN_TEMPERATURE,
                        GenerationConfig.MAX_TEMPERATURE,
                    )
                    SovereignSliderRow(
                        title = "Temperature",
                        subtitle = "Controls sampling randomness and diversity",
                        value = clampedTemp,
                        onValueChange = { raw ->
                            val snapped = ((raw * 20f).roundToInt() / 20f).coerceIn(
                                GenerationConfig.MIN_TEMPERATURE,
                                GenerationConfig.MAX_TEMPERATURE,
                            )
                            onTemperatureChange(snapped)
                        },
                        valueRange = GenerationConfig.MIN_TEMPERATURE..GenerationConfig.MAX_TEMPERATURE,
                        formattedValue = String.format(Locale.US, "%.2f", clampedTemp),
                        minLabel = "0.00 · Deterministic",
                        maxLabel = "2.00 · Creative",
                    )

                    SovereignDivider()

                    val clampedTopP = topP.coerceIn(
                        GenerationConfig.MIN_TOP_P,
                        GenerationConfig.MAX_TOP_P,
                    )
                    SovereignSliderRow(
                        title = "Top-P",
                        subtitle = "Nucleus cumulative probability cutoff",
                        value = clampedTopP,
                        onValueChange = { raw ->
                            val snapped = ((raw * 20f).roundToInt() / 20f).coerceIn(
                                GenerationConfig.MIN_TOP_P,
                                GenerationConfig.MAX_TOP_P,
                            )
                            onTopPChange(snapped)
                        },
                        valueRange = GenerationConfig.MIN_TOP_P..GenerationConfig.MAX_TOP_P,
                        formattedValue = String.format(Locale.US, "%.2f", clampedTopP),
                        minLabel = "0.00 · Narrow",
                        maxLabel = "1.00 · Full",
                    )

                    SovereignDivider()

                    val clampedPenalty = repetitionPenalty.coerceIn(
                        REPETITION_PENALTY_MIN,
                        REPETITION_PENALTY_MAX,
                    )
                    SovereignSliderRow(
                        title = "Repetition Penalty",
                        subtitle = "Discourages repeating recent tokens and phrases",
                        value = clampedPenalty,
                        onValueChange = { raw ->
                            val snapped = ((raw * 20f).roundToInt() / 20f).coerceIn(
                                REPETITION_PENALTY_MIN,
                                REPETITION_PENALTY_MAX,
                            )
                            onRepetitionPenaltyChange(snapped)
                        },
                        valueRange = REPETITION_PENALTY_MIN..REPETITION_PENALTY_MAX,
                        formattedValue = String.format(Locale.US, "%.2f", clampedPenalty),
                        minLabel = "0.50 · Loose",
                        maxLabel = "2.00 · Strict",
                    )

                    SovereignDivider()

                    val clampedMaxTokens = maxTokens.coerceIn(
                        MAX_OUTPUT_TOKENS_SLIDER_MIN,
                        GenerationConfig.MAX_MAX_TOKENS,
                    )
                    val maxTokensSteps = ((GenerationConfig.MAX_MAX_TOKENS - MAX_OUTPUT_TOKENS_SLIDER_MIN) /
                        MAX_OUTPUT_TOKENS_SLIDER_STEP) - 1
                    SovereignSliderRow(
                        title = "Max Output Tokens",
                        subtitle = "Maximum length of each generated response",
                        value = clampedMaxTokens.toFloat(),
                        onValueChange = { raw ->
                            val snapped = ((raw / MAX_OUTPUT_TOKENS_SLIDER_STEP).roundToInt() *
                                MAX_OUTPUT_TOKENS_SLIDER_STEP).coerceIn(
                                MAX_OUTPUT_TOKENS_SLIDER_MIN,
                                GenerationConfig.MAX_MAX_TOKENS,
                            )
                            onMaxTokensChange(snapped)
                        },
                        valueRange = MAX_OUTPUT_TOKENS_SLIDER_MIN.toFloat()..GenerationConfig.MAX_MAX_TOKENS.toFloat(),
                        steps = maxTokensSteps,
                        formattedValue = "$maxTokens tok",
                        minLabel = "$MAX_OUTPUT_TOKENS_SLIDER_MIN",
                        maxLabel = "${GenerationConfig.MAX_MAX_TOKENS}",
                    )

                    SovereignDivider()

                    val clampedContext = contextLength.coerceIn(
                        GenerationConfig.MIN_CONTEXT_LENGTH,
                        GenerationConfig.MAX_CONTEXT_LENGTH,
                    )
                    val contextSteps = ((GenerationConfig.MAX_CONTEXT_LENGTH - GenerationConfig.MIN_CONTEXT_LENGTH) /
                        CONTEXT_LENGTH_SLIDER_STEP) - 1
                    SovereignSliderRow(
                        title = "Context Length",
                        subtitle = "Prompt + history window (applied on next model load)",
                        value = clampedContext.toFloat(),
                        onValueChange = { raw ->
                            val snapped = ((raw / CONTEXT_LENGTH_SLIDER_STEP).roundToInt() *
                                CONTEXT_LENGTH_SLIDER_STEP).coerceIn(
                                GenerationConfig.MIN_CONTEXT_LENGTH,
                                GenerationConfig.MAX_CONTEXT_LENGTH,
                            )
                            onContextLengthChange(snapped)
                        },
                        valueRange = GenerationConfig.MIN_CONTEXT_LENGTH.toFloat()..GenerationConfig.MAX_CONTEXT_LENGTH.toFloat(),
                        steps = contextSteps,
                        formattedValue = "$contextLength ctx",
                        minLabel = "${GenerationConfig.MIN_CONTEXT_LENGTH}",
                        maxLabel = "${GenerationConfig.MAX_CONTEXT_LENGTH}",
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Live Runtime Support & Parameter Audit Card
                val hasNotices = runtimeNotices.isNotEmpty()
                SovereignHairlineCard(
                    contentPadding = PaddingValues(16.dp),
                    containerColor = if (hasNotices) {
                        colors.amberContainer.copy(alpha = 0.4f)
                    } else {
                        colors.successContainer.copy(alpha = 0.35f)
                    },
                    borderColor = if (hasNotices) {
                        colors.amber.copy(alpha = 0.45f)
                    } else {
                        colors.success.copy(alpha = 0.35f)
                    },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Runtime Support & Parameter Audit",
                            style = SovereignTypography.pillTitle,
                            color = colors.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        SovereignBadge(
                            text = if (hasNotices) {
                                "${runtimeNotices.size} ${if (runtimeNotices.size == 1) "Notice" else "Notices"}"
                            } else {
                                "Valid"
                            },
                            tone = if (hasNotices) SovereignStatusTone.WARNING else SovereignStatusTone.SUCCESS,
                            leadingDot = true,
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    if (!hasNotices) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircleOutline,
                                contentDescription = null,
                                tint = colors.success,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "All parameters valid",
                                style = SovereignTypography.bodyCompact,
                                color = colors.onSuccessContainer,
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            runtimeNotices.forEach { notice ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.WarningAmber,
                                        contentDescription = null,
                                        tint = colors.amber,
                                        modifier = Modifier
                                            .padding(top = 2.dp)
                                            .size(16.dp),
                                    )
                                    Text(
                                        text = notice,
                                        style = SovereignTypography.bodyCompact,
                                        color = colors.onAmberContainer,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── 5. Memory ─────────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                SovereignSectionHeader(title = "Memory")
                SovereignHairlineCard(contentPadding = PaddingValues(0.dp)) {
                    SovereignSwitchRow(
                        title = "Cross-Chat Memory",
                        subtitle = "Recall key user & project facts locally across sessions",
                        leadingIcon = Icons.Rounded.Psychology,
                        checked = isMemoryEnabled,
                        onCheckedChange = onToggleMemory,
                    )
                    SovereignDivider()
                    SovereignSettingsRow(
                        title = "Manage Stored Memories",
                        subtitle = "Inspect, search, edit, or delete local SQLite memories",
                        leadingIcon = Icons.Rounded.Storage,
                        onClick = onManageMemories,
                        trailing = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                SovereignBadge(
                                    text = memoryCount.toString(),
                                    tone = if (memoryCount > 0) {
                                        SovereignStatusTone.ACCENT
                                    } else {
                                        SovereignStatusTone.NEUTRAL
                                    },
                                )
                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    tint = colors.textMuted,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                    )
                }
            }

            // ── 6. Device Diagnostics ─────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                SovereignSectionHeader(title = "Device Diagnostics")
                SovereignHairlineCard(contentPadding = PaddingValues(16.dp)) {
                    val thermalTone = mapThermalSeverityTone(thermalState)
                    val isThermalAlert = thermalState == DeviceThermalState.SEVERE ||
                        thermalState == DeviceThermalState.CRITICAL

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Thermal State",
                            style = SovereignTypography.bodyCompact,
                            color = colors.textSecondary,
                        )
                        SovereignBadge(
                            text = thermalState.label,
                            tone = thermalTone,
                            leadingDot = true,
                            pulsingDot = isThermalAlert,
                        )
                    }

                    SovereignDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Battery Temperature",
                            style = SovereignTypography.bodyCompact,
                            color = colors.textSecondary,
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f °C", batteryTemp),
                            style = SovereignTypography.telemetry,
                            color = colors.textPrimary,
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Battery Level",
                            style = SovereignTypography.bodyCompact,
                            color = colors.textSecondary,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (isCharging) {
                                SovereignBadge(
                                    text = "Charging",
                                    tone = SovereignStatusTone.SUCCESS,
                                    leadingIcon = Icons.Rounded.Bolt,
                                )
                            }
                            Text(
                                text = "$batteryPercent%",
                                style = SovereignTypography.telemetry,
                                color = colors.textPrimary,
                            )
                        }
                    }

                    SovereignDivider(modifier = Modifier.padding(vertical = 12.dp))

                    val availFraction = remember(availRamMb, totalRamMb) {
                        computeAvailableRamFraction(availRamMb, totalRamMb)
                    }
                    val ramBarColor = when {
                        availFraction <= 0.15f -> colors.error
                        availFraction <= 0.35f -> colors.amber
                        else -> colors.success
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Available RAM",
                            style = SovereignTypography.bodyCompact,
                            color = colors.textSecondary,
                        )
                        Text(
                            text = "$availRamMb / $totalRamMb MB",
                            style = SovereignTypography.telemetry,
                            color = colors.textPrimary,
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(shapes.pill)
                            .background(colors.surfaceBase),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(availFraction)
                                .fillMaxHeight()
                                .clip(shapes.pill)
                                .background(ramBarColor),
                        )
                    }
                }
            }

            Spacer(Modifier.height(spacing.md))
        }
    }
}

@Composable
private fun InferenceModeOptionCard(
    mode: UnrestrictedMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SovereignTheme.colors
    val isUnrestricted = mode == UnrestrictedMode.UNRESTRICTED

    val containerColor = when {
        selected && isUnrestricted -> colors.amberContainer.copy(alpha = 0.5f)
        selected -> colors.accentContainer.copy(alpha = 0.55f)
        else -> colors.surfaceBase
    }
    val borderColor = when {
        selected && isUnrestricted -> colors.amber
        selected -> colors.accentPrimary
        else -> colors.hairlineSubtle
    }
    val iconTint = when {
        selected && isUnrestricted -> colors.amber
        selected -> colors.accentPrimary
        else -> colors.textSecondary
    }

    SovereignHairlineCard(
        onClick = onClick,
        selected = selected,
        containerColor = containerColor,
        borderColor = borderColor,
        contentPadding = PaddingValues(12.dp),
        modifier = modifier.semantics {
            role = Role.RadioButton
            this.selected = selected
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = if (isUnrestricted) Icons.Rounded.Bolt else Icons.Rounded.Shield,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = mode.displayName,
                style = SovereignTypography.actionLabel,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (isUnrestricted) "Direct technical prompt" else "Balanced assistant",
            style = SovereignTypography.bodyCompact,
            color = colors.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PromptProfileChip(
    profile: PromptProfile,
    selected: Boolean,
    isBuiltIn: Boolean,
    canDelete: Boolean,
    onSelect: () -> Unit,
    onReset: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val colors = SovereignTheme.colors
    val motion = SovereignTheme.motion
    val shapes = SovereignTheme.shapes

    val bgColor by animateColorAsState(
        targetValue = if (selected) colors.accentContainer else colors.surfaceBase,
        animationSpec = motion.effectsQuick(),
        label = "ProfileChipBg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) colors.accentPrimary else colors.hairlineSubtle,
        animationSpec = motion.effectsQuick(),
        label = "ProfileChipBorder",
    )
    val labelColor = if (selected) colors.onAccentContainer else colors.textPrimary

    Surface(
        shape = shapes.sm,
        color = bgColor,
        contentColor = labelColor,
        border = BorderStroke(SovereignElevation.hairlineWidth, borderColor),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(shapes.sm)
            .clickable(onClick = onSelect)
            .semantics {
                role = Role.Tab
                this.selected = selected
            },
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = if (isBuiltIn || canDelete) 2.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                SovereignStatusDot(
                    tone = SovereignStatusTone.ACCENT,
                    size = 6.dp,
                )
                Spacer(Modifier.width(7.dp))
            }
            Text(
                text = profile.name,
                style = SovereignTypography.actionLabel,
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 10.dp),
            )
            if (isBuiltIn) {
                SovereignIconButton(
                    onClick = onReset,
                    contentDescription = "Reset ${profile.name} profile to default",
                    visualSize = 32.dp,
                    contentColor = if (selected) colors.onAccentContainer else colors.textSecondary,
                    hapticFeedback = true,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
            } else if (canDelete) {
                SovereignIconButton(
                    onClick = onRequestDelete,
                    contentDescription = "Delete ${profile.name} profile",
                    visualSize = 32.dp,
                    contentColor = colors.error,
                    hapticFeedback = true,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PerformanceModeOptionCard(
    mode: PerformanceMode,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = SovereignTheme.colors
    val icon: ImageVector = when (mode) {
        PerformanceMode.ECO -> Icons.Rounded.BatterySaver
        PerformanceMode.BALANCED -> Icons.Rounded.Speed
        PerformanceMode.PERFORMANCE -> Icons.Rounded.RocketLaunch
    }
    SovereignHairlineCard(
        onClick = onClick,
        selected = selected,
        containerColor = if (selected) colors.accentContainer.copy(alpha = 0.45f) else colors.surfaceBase,
        borderColor = if (selected) colors.accentPrimary else colors.hairlineSubtle,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                role = Role.RadioButton
                this.selected = selected
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) colors.accentPrimary else colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mode.displayName,
                    style = SovereignTypography.pillTitle,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = mode.description,
                    style = SovereignTypography.bodyCompact,
                    color = colors.textSecondary,
                )
            }
            if (selected) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = colors.accentPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
