package com.example.on_device_qwen.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.on_device_qwen.domain.modelmanager.ModelCatalogItem
import com.example.on_device_qwen.model.BackendType
import com.example.on_device_qwen.model.ModelCompatibilityStatus
import com.example.on_device_qwen.model.ModelFormat
import com.example.on_device_qwen.model.ModelMetadata
import com.example.on_device_qwen.ui.designsystem.SovereignAlertDialog
import com.example.on_device_qwen.ui.designsystem.SovereignBadge
import com.example.on_device_qwen.ui.designsystem.SovereignElevation
import com.example.on_device_qwen.ui.designsystem.SovereignEmptyState
import com.example.on_device_qwen.ui.designsystem.SovereignHairlineCard
import com.example.on_device_qwen.ui.designsystem.SovereignIconButton
import com.example.on_device_qwen.ui.designsystem.SovereignModalSheet
import com.example.on_device_qwen.ui.designsystem.SovereignPrimaryButton
import com.example.on_device_qwen.ui.designsystem.SovereignSecondaryButton
import com.example.on_device_qwen.ui.designsystem.SovereignSegmentedControl
import com.example.on_device_qwen.ui.designsystem.SovereignStatusDot
import com.example.on_device_qwen.ui.designsystem.SovereignStatusTone
import com.example.on_device_qwen.ui.designsystem.SovereignTheme
import com.example.on_device_qwen.ui.designsystem.SovereignTonalButton
import com.example.on_device_qwen.ui.designsystem.SovereignTypography
import java.io.File
import java.util.Locale

internal const val MODEL_RAM_HEADROOM_MB = 600L

internal data class ModelRamAssessment(
    val label: String,
    val tone: SovereignStatusTone,
    val isLoadable: Boolean,
    val reason: String? = null,
)

internal data class RamBudgetSummary(
    val labelText: String,
    val freeFraction: Float?,
    val tone: SovereignStatusTone,
)

private enum class ModelManagerTab {
    INSTALLED,
    CATALOG,
}

/**
 * Evaluates file integrity, readability, and memory safety for an installed [ModelMetadata]
 * against the device's currently available RAM ([availRamMb]).
 */
internal fun assessInstalledModelSafety(
    model: ModelMetadata,
    availRamMb: Long,
): ModelRamAssessment {
    if (model.isCorrupted || model.compatibilityStatus == ModelCompatibilityStatus.CORRUPTED) {
        return ModelRamAssessment(
            label = "Corrupted",
            tone = SovereignStatusTone.ERROR,
            isLoadable = false,
            reason = model.compatibilityMessage
                ?: "Model file appears corrupted or incomplete and cannot be loaded.",
        )
    }

    if (!model.isReadable) {
        return ModelRamAssessment(
            label = "Unreadable",
            tone = SovereignStatusTone.ERROR,
            isLoadable = false,
            reason = model.compatibilityMessage
                ?: "File cannot be read. Re-import via SAF or grant storage access.",
        )
    }

    if (model.compatibilityStatus == ModelCompatibilityStatus.UNSUPPORTED_FORMAT) {
        return ModelRamAssessment(
            label = "Unsupported",
            tone = SovereignStatusTone.ERROR,
            isLoadable = false,
            reason = model.compatibilityMessage
                ?: "This model format is not supported by the active runtime.",
        )
    }

    val estimatedMb = when {
        model.estimatedRamMb > 0L -> model.estimatedRamMb
        model.fileSizeMb > 0L -> model.fileSizeMb
        else -> 0L
    }

    if (model.compatibilityStatus == ModelCompatibilityStatus.INSUFFICIENT_RAM ||
        (availRamMb > 0L && estimatedMb > 0L && estimatedMb > availRamMb)
    ) {
        val reason = model.compatibilityMessage ?: if (availRamMb > 0L && estimatedMb > 0L) {
            "Estimated $estimatedMb MB RAM exceeds $availRamMb MB free; loading may fail or swap slowly."
        } else {
            "Insufficient free RAM to safely load this model."
        }
        return ModelRamAssessment(
            label = "Exceeds Free RAM",
            tone = SovereignStatusTone.ERROR,
            isLoadable = true,
            reason = reason,
        )
    }

    if (model.compatibilityStatus == ModelCompatibilityStatus.TIGHT_RAM ||
        (availRamMb > 0L && estimatedMb > 0L && availRamMb < estimatedMb + MODEL_RAM_HEADROOM_MB)
    ) {
        val headroom = if (availRamMb > 0L && estimatedMb > 0L) {
            (availRamMb - estimatedMb).coerceAtLeast(0L)
        } else {
            null
        }
        val reason = model.compatibilityMessage ?: if (headroom != null) {
            "Leaves $headroom MB headroom (${MODEL_RAM_HEADROOM_MB} MB recommended)."
        } else {
            "Memory headroom is tight for this model."
        }
        return ModelRamAssessment(
            label = "Tight RAM",
            tone = SovereignStatusTone.WARNING,
            isLoadable = true,
            reason = reason,
        )
    }

    if (availRamMb <= 0L) {
        return ModelRamAssessment(
            label = "RAM Unknown",
            tone = SovereignStatusTone.NEUTRAL,
            isLoadable = true,
            reason = model.compatibilityMessage,
        )
    }

    return ModelRamAssessment(
        label = "Fits RAM",
        tone = SovereignStatusTone.SUCCESS,
        isLoadable = true,
        reason = model.compatibilityMessage,
    )
}

/**
 * Evaluates a verified [ModelCatalogItem] against device RAM constraints before downloading.
 */
internal fun assessCatalogModelRam(
    item: ModelCatalogItem,
    availRamMb: Long,
    totalRamMb: Long = 0L,
): ModelRamAssessment {
    if (availRamMb > 0L) {
        return when {
            availRamMb >= item.recommendedRamMb -> ModelRamAssessment(
                label = "Fits RAM",
                tone = SovereignStatusTone.SUCCESS,
                isLoadable = true,
            )
            availRamMb >= item.minRamMb -> ModelRamAssessment(
                label = "Tight RAM",
                tone = SovereignStatusTone.WARNING,
                isLoadable = true,
                reason = "Meets ${item.minRamMb} MB min; ${item.recommendedRamMb} MB recommended.",
            )
            totalRamMb >= item.recommendedRamMb -> ModelRamAssessment(
                label = "Exceeds Free RAM",
                tone = SovereignStatusTone.ERROR,
                isLoadable = true,
                reason = "Needs ${item.minRamMb}+ MB free (${availRamMb} MB free now; close background apps).",
            )
            else -> ModelRamAssessment(
                label = "Exceeds Free RAM",
                tone = SovereignStatusTone.ERROR,
                isLoadable = true,
                reason = "Requires at least ${item.minRamMb} MB RAM (${availRamMb} MB free).",
            )
        }
    }

    if (totalRamMb > 0L) {
        return when {
            totalRamMb >= item.recommendedRamMb -> ModelRamAssessment(
                label = "Fits RAM",
                tone = SovereignStatusTone.SUCCESS,
                isLoadable = true,
            )
            totalRamMb >= item.minRamMb -> ModelRamAssessment(
                label = "Tight RAM",
                tone = SovereignStatusTone.WARNING,
                isLoadable = true,
                reason = "Device total RAM (${totalRamMb} MB) is near minimum (${item.minRamMb} MB).",
            )
            else -> ModelRamAssessment(
                label = "Exceeds Free RAM",
                tone = SovereignStatusTone.ERROR,
                isLoadable = true,
                reason = "Requires at least ${item.minRamMb} MB RAM (${totalRamMb} MB total).",
            )
        }
    }

    return ModelRamAssessment(
        label = "RAM Unknown",
        tone = SovereignStatusTone.NEUTRAL,
        isLoadable = true,
    )
}

/**
 * Computes the RAM budget bar state from available and total physical device memory.
 */
internal fun computeRamBudgetSummary(
    availRamMb: Long,
    totalRamMb: Long,
): RamBudgetSummary {
    if (totalRamMb > 0L) {
        val clampedAvail = availRamMb.coerceIn(0L, totalRamMb)
        val freeFraction = (clampedAvail.toFloat() / totalRamMb.toFloat()).coerceIn(0f, 1f)
        val tone = when {
            freeFraction < 0.15f || clampedAvail < 800L -> SovereignStatusTone.ERROR
            freeFraction < 0.30f || clampedAvail < 1800L -> SovereignStatusTone.WARNING
            else -> SovereignStatusTone.SUCCESS
        }
        return RamBudgetSummary(
            labelText = "$clampedAvail / $totalRamMb MB free",
            freeFraction = freeFraction,
            tone = tone,
        )
    }

    val tone = when {
        availRamMb <= 0L -> SovereignStatusTone.NEUTRAL
        availRamMb < 800L -> SovereignStatusTone.ERROR
        availRamMb < 1800L -> SovereignStatusTone.WARNING
        else -> SovereignStatusTone.SUCCESS
    }
    return RamBudgetSummary(
        labelText = if (availRamMb > 0L) "$availRamMb MB free" else "RAM status unavailable",
        freeFraction = null,
        tone = tone,
    )
}

/**
 * Matches a catalog entry to discovered [installedModels] using format, SHA-256 (when present),
 * download filename, and normalized model name tokens.
 */
internal fun isCatalogItemInstalled(
    item: ModelCatalogItem,
    installedModels: List<ModelMetadata>,
): Boolean {
    if (installedModels.isEmpty()) return false
    val urlFileName = item.downloadUrl.substringAfterLast('/').substringBefore('?')
    val urlStem = urlFileName.substringBeforeLast('.')
    val normUrlStem = normalizeModelKey(urlStem)
    val baseItemName = item.name.replace(Regex("\\s*\\([^)]*\\)"), "").trim()
    val normItemName = normalizeModelKey(baseItemName)

    return installedModels.any { model ->
        if (model.format != item.format) return@any false
        if (!item.sha256.isNullOrBlank() && !model.sha256.isNullOrBlank() &&
            item.sha256.equals(model.sha256, ignoreCase = true)
        ) {
            return@any true
        }
        if (urlFileName.isNotEmpty() && model.file.name.equals(urlFileName, ignoreCase = true)) {
            return@any true
        }
        val normModelFile = normalizeModelKey(model.file.nameWithoutExtension)
        val normModelName = normalizeModelKey(model.name)
        val matchesUrlStem = normUrlStem.isNotEmpty() && (
            normModelFile == normUrlStem ||
                normModelName == normUrlStem ||
                normModelFile.contains(normUrlStem)
            )
        val matchesItemName = normItemName.isNotEmpty() && (
            normModelFile.contains(normItemName) ||
                normModelName.contains(normItemName)
            )
        matchesUrlStem || matchesItemName
    }
}

private fun normalizeModelKey(raw: String): String =
    raw.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")

@Composable
fun ModelManagerDialog(
    models: List<ModelMetadata>,
    activeModel: ModelMetadata?,
    availRamMb: Long,
    importProgress: Float?,
    onSelectModel: (ModelMetadata) -> Unit,
    onImportUri: (Uri) -> Unit,
    onDeleteModel: (ModelMetadata) -> Unit,
    onDismiss: () -> Unit,
    totalRamMb: Long = 0L,
    currentBackend: BackendType = BackendType.CPU,
    isEngineReady: Boolean = false,
    onSwitchBackend: ((BackendType) -> Unit)? = null,
) {
    val colors = SovereignTheme.colors
    val adaptive = SovereignTheme.adaptive
    val motion = SovereignTheme.motion
    val spacing = SovereignTheme.spacing
    val gutter = spacing.screenGutter(adaptive)
    val context = LocalContext.current

    var selectedTab by remember { mutableStateOf(ModelManagerTab.INSTALLED) }
    var pendingDeleteModel by remember { mutableStateOf<ModelMetadata?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            onImportUri(uri)
        }
    }

    val launchImportPicker = {
        filePickerLauncher.launch(arrayOf("*/*"))
    }

    val ramBudget = remember(availRamMb, totalRamMb) {
        computeRamBudgetSummary(availRamMb = availRamMb, totalRamMb = totalRamMb)
    }

    SovereignModalSheet(
        title = "Models",
        subtitle = "On-device GGUF & LiteRT-LM",
        onDismissRequest = onDismiss,
        headerActions = {
            SovereignTonalButton(
                text = if (importProgress != null) "Importing" else "Import",
                onClick = launchImportPicker,
                enabled = importProgress == null,
                leadingIcon = Icons.Rounded.FileOpen,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                hapticFeedback = true,
            )
        },
        stickyHeaderExtra = {
            StickyHardwareHeader(
                activeModel = activeModel,
                currentBackend = currentBackend,
                isEngineReady = isEngineReady,
                ramBudget = ramBudget,
                onSwitchBackend = onSwitchBackend,
                importProgress = importProgress,
            )
        },
    ) {
        Spacer(Modifier.height(12.dp))

        Box(modifier = Modifier.padding(horizontal = gutter)) {
            SovereignSegmentedControl(
                options = ModelManagerTab.entries,
                selected = selectedTab,
                onSelect = { selectedTab = it },
                label = { tab ->
                    when (tab) {
                        ModelManagerTab.INSTALLED -> "Installed (${models.size})"
                        ModelManagerTab.CATALOG -> "Catalog (${ModelCatalogItem.VERIFIED_CATALOG.size})"
                    }
                },
            )
        }

        Spacer(Modifier.height(10.dp))

        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                fadeIn(motion.effectsQuick()) togetherWith fadeOut(motion.effectsQuick())
            },
            label = "ModelManagerTabContent",
            modifier = Modifier.weight(1f),
        ) { tab ->
            when (tab) {
                ModelManagerTab.INSTALLED -> {
                    if (models.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = gutter, vertical = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            SovereignEmptyState(
                                title = "No models installed",
                                subtitle = "Import a local .gguf or .litertlm file from storage, or browse the verified catalog to download weights.",
                                icon = Icons.Rounded.Layers,
                                action = {
                                    SovereignPrimaryButton(
                                        text = "Import from Storage",
                                        onClick = launchImportPicker,
                                        enabled = importProgress == null,
                                        leadingIcon = Icons.Rounded.FileOpen,
                                        useAccent = true,
                                        hapticFeedback = true,
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    SovereignSecondaryButton(
                                        text = "Browse Catalog (${ModelCatalogItem.VERIFIED_CATALOG.size})",
                                        onClick = { selectedTab = ModelManagerTab.CATALOG },
                                        leadingIcon = Icons.Rounded.Explore,
                                        hapticFeedback = true,
                                    )
                                },
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = gutter,
                                end = gutter,
                                top = 6.dp,
                                bottom = 20.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(models, key = { it.file.absolutePath }) { model ->
                                val isActive = isSameModelFile(activeModel?.file, model.file)
                                InstalledModelCard(
                                    model = model,
                                    isActive = isActive,
                                    availRamMb = availRamMb,
                                    onSelect = { onSelectModel(model) },
                                    onRequestDelete = { pendingDeleteModel = model },
                                )
                            }
                        }
                    }
                }

                ModelManagerTab.CATALOG -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = gutter,
                            end = gutter,
                            top = 6.dp,
                            bottom = 20.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(key = "catalog_hint") {
                            Text(
                                text = "Download verified open-weights in your browser, then tap Import to load them offline.",
                                style = SovereignTypography.bodyCompact,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(bottom = 2.dp),
                            )
                        }
                        items(ModelCatalogItem.VERIFIED_CATALOG, key = { it.id }) { item ->
                            val isInstalled = remember(item.id, models) {
                                isCatalogItemInstalled(item = item, installedModels = models)
                            }
                            val ramAssessment = remember(item.id, availRamMb, totalRamMb) {
                                assessCatalogModelRam(
                                    item = item,
                                    availRamMb = availRamMb,
                                    totalRamMb = totalRamMb,
                                )
                            }
                            CatalogModelCard(
                                item = item,
                                isInstalled = isInstalled,
                                ramAssessment = ramAssessment,
                                isCompact = adaptive.isCompact,
                                onViewRepo = { openExternalUrl(context, item.effectiveRepoUrl) },
                                onDownload = { openExternalUrl(context, item.downloadUrl) },
                                onCopyUrl = { copyUrlToClipboard(context, item.name, item.downloadUrl) },
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDeleteModel?.let { target ->
        val isPendingActive = isSameModelFile(activeModel?.file, target.file)
        SovereignAlertDialog(
            title = "Delete Model?",
            text = buildString {
                append("Remove \"${target.name}\" (${target.fileSizeMb} MB)?\n\n")
                append("App-imported copies are permanently removed from storage; external files are unregistered from the model list.")
                if (isPendingActive) {
                    append("\n\nThis model is currently active and will be unloaded.")
                }
            },
            confirmText = "Delete",
            dismissText = "Cancel",
            isDestructive = true,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    tint = colors.error,
                    modifier = Modifier.size(22.dp),
                )
            },
            onConfirm = {
                val modelToDelete = target
                pendingDeleteModel = null
                onDeleteModel(modelToDelete)
            },
            onDismissRequest = { pendingDeleteModel = null },
        )
    }
}

@Composable
private fun StickyHardwareHeader(
    activeModel: ModelMetadata?,
    currentBackend: BackendType,
    isEngineReady: Boolean,
    ramBudget: RamBudgetSummary,
    onSwitchBackend: ((BackendType) -> Unit)?,
    importProgress: Float?,
) {
    val colors = SovereignTheme.colors
    val shapes = SovereignTheme.shapes
    val motion = SovereignTheme.motion

    val activeDotTone = when {
        activeModel != null && isEngineReady -> SovereignStatusTone.SUCCESS
        activeModel != null -> SovereignStatusTone.WARNING
        else -> SovereignStatusTone.NEUTRAL
    }

    val ramBarColor = when (ramBudget.tone) {
        SovereignStatusTone.ERROR -> colors.error
        SovereignStatusTone.WARNING -> colors.amber
        SovereignStatusTone.SUCCESS -> colors.success
        else -> colors.accentPrimary
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SovereignHairlineCard(
            containerColor = colors.surfaceLow,
            borderColor = colors.hairlineSubtle,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        ) {
            // Active Model & Backend Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SovereignStatusDot(
                        tone = activeDotTone,
                        size = 8.dp,
                        pulsing = false,
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = activeModel?.name ?: "No active model",
                            style = SovereignTypography.pillTitle,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val statusSub = if (activeModel != null) {
                            val readiness = if (isEngineReady) "Ready" else "Selected"
                            "$readiness · ${currentBackend.displayLabel(activeModel.engineType)}"
                        } else {
                            "Select or import a model below"
                        }
                        Text(
                            text = statusSub,
                            style = SovereignTypography.monoTag,
                            color = colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                SovereignBadge(
                    text = currentBackend.chipText,
                    tone = if (activeModel != null && isEngineReady) {
                        SovereignStatusTone.ACCENT
                    } else {
                        SovereignStatusTone.NEUTRAL
                    },
                    leadingDot = true,
                )
            }

            if (onSwitchBackend != null) {
                Spacer(Modifier.height(8.dp))
                BackendQuickSwitcher(
                    activeModel = activeModel,
                    currentBackend = currentBackend,
                    onSwitchBackend = onSwitchBackend,
                )
            }

            Spacer(Modifier.height(10.dp))

            // RAM Budget Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "RAM BUDGET",
                    style = SovereignTypography.monoTag,
                    color = colors.textMuted,
                )
                Text(
                    text = ramBudget.labelText,
                    style = SovereignTypography.telemetry,
                    color = colors.textPrimary,
                )
            }

            ramBudget.freeFraction?.let { targetFraction ->
                Spacer(Modifier.height(6.dp))
                val animatedFraction by animateFloatAsState(
                    targetValue = targetFraction,
                    animationSpec = motion.effectsQuick(),
                    label = "RamBudgetFraction",
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(shapes.pill)
                        .background(colors.surfaceHighest),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedFraction.coerceIn(0.02f, 1f))
                            .clip(shapes.pill)
                            .background(ramBarColor),
                    )
                }
            }
        }

        // Animated Linear Import Progress Card
        AnimatedVisibility(
            visible = importProgress != null,
            enter = fadeIn(motion.effectsQuick()) + expandVertically(motion.spatialFast()),
            exit = fadeOut(motion.effectsQuick()) + shrinkVertically(motion.spatialFast()),
        ) {
            importProgress?.let { progress ->
                val clamped = progress.coerceIn(0f, 1f)
                val pct = (clamped * 100f).toInt()
                SovereignHairlineCard(
                    containerColor = colors.accentContainer.copy(alpha = 0.55f),
                    borderColor = colors.accentPrimary.copy(alpha = 0.35f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Importing model file…",
                            style = SovereignTypography.pillTitle,
                            color = colors.onAccentContainer,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "$pct%",
                            style = SovereignTypography.telemetry,
                            color = colors.accentPrimary,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { clamped },
                        color = colors.accentPrimary,
                        trackColor = colors.surfaceHighest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(shapes.pill),
                    )
                }
            }
        }
    }
}

@Composable
private fun BackendQuickSwitcher(
    activeModel: ModelMetadata?,
    currentBackend: BackendType,
    onSwitchBackend: (BackendType) -> Unit,
) {
    val colors = SovereignTheme.colors
    val shapes = SovereignTheme.shapes
    val motion = SovereignTheme.motion
    val haptics = LocalHapticFeedback.current
    val hapticsEnabled = SovereignTheme.hapticsEnabled

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "BACKEND",
            style = SovereignTypography.monoTag,
            color = colors.textMuted,
            modifier = Modifier.padding(end = 2.dp),
        )
        BackendType.entries.forEach { backend ->
            val isSelected = backend == currentBackend
            val isSupported = activeModel == null || backend.isSupportedBy(activeModel.engineType)
            val canTap = isSupported && !isSelected

            val pillBg by animateColorAsState(
                targetValue = when {
                    isSelected -> colors.accentContainer
                    isSupported -> colors.surfaceBase
                    else -> colors.surfaceLow
                },
                animationSpec = motion.effectsQuick(),
                label = "BackendPillBg",
            )
            val pillFg by animateColorAsState(
                targetValue = when {
                    isSelected -> colors.onAccentContainer
                    isSupported -> colors.textPrimary
                    else -> colors.textMuted.copy(alpha = 0.55f)
                },
                animationSpec = motion.effectsQuick(),
                label = "BackendPillFg",
            )
            val pillBorder = when {
                isSelected -> colors.accentPrimary.copy(alpha = 0.45f)
                isSupported -> colors.hairlineSubtle
                else -> colors.hairlineSubtle.copy(alpha = 0.5f)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(shapes.xs)
                    .clickable(enabled = canTap) {
                        if (hapticsEnabled) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        onSwitchBackend(backend)
                    }
                    .semantics {
                        this.role = Role.Tab
                        this.selected = isSelected
                        this.contentDescription = buildString {
                            append("${backend.chipText} backend")
                            if (!isSupported) append(", unavailable for current model")
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = shapes.xs,
                    color = pillBg,
                    border = BorderStroke(SovereignElevation.hairlineWidth, pillBorder),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = backend.chipText,
                            style = SovereignTypography.monoTag,
                            color = pillFg,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InstalledModelCard(
    model: ModelMetadata,
    isActive: Boolean,
    availRamMb: Long,
    onSelect: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val colors = SovereignTheme.colors
    val shapes = SovereignTheme.shapes
    val haptics = LocalHapticFeedback.current
    val hapticsEnabled = SovereignTheme.hapticsEnabled

    val ramSafety = remember(model, availRamMb) {
        assessInstalledModelSafety(model = model, availRamMb = availRamMb)
    }

    val cardContainer = when {
        !ramSafety.isLoadable -> colors.surfaceLow.copy(alpha = 0.7f)
        isActive -> colors.surfaceHigh
        else -> colors.surfaceLow
    }
    val cardBorder = when {
        isActive -> colors.accentPrimary
        !ramSafety.isLoadable -> colors.error.copy(alpha = 0.45f)
        ramSafety.tone == SovereignStatusTone.ERROR -> colors.error.copy(alpha = 0.35f)
        else -> colors.hairlineSubtle
    }

    SovereignHairlineCard(
        onClick = if (ramSafety.isLoadable) {
            {
                if (hapticsEnabled) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                onSelect()
            }
        } else {
            null
        },
        enabled = ramSafety.isLoadable,
        selected = isActive,
        containerColor = cardContainer,
        borderColor = cardBorder,
        contentPadding = PaddingValues(14.dp),
    ) {
        // Title + Active Badge + Delete 48dp Icon Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.name,
                    style = SovereignTypography.cardTitle,
                    color = if (ramSafety.isLoadable) colors.textPrimary else colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (isActive) {
                Spacer(Modifier.width(8.dp))
                SovereignBadge(
                    text = "ACTIVE",
                    tone = SovereignStatusTone.ACCENT,
                    leadingDot = true,
                )
            }

            SovereignIconButton(
                onClick = onRequestDelete,
                contentDescription = "Delete ${model.name}",
                visualSize = 36.dp,
                contentColor = colors.textSecondary,
                hapticFeedback = true,
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // Format, Capability, & RAM Safety Pills
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ModelFormatBadge(model.format)
            if (model.hasVision) {
                SovereignBadge(
                    text = "VLM",
                    tone = SovereignStatusTone.SUCCESS,
                )
            }
            if (model.isAbliterated) {
                SovereignBadge(
                    text = "Abliterated",
                    tone = SovereignStatusTone.WARNING,
                )
            }
            SovereignBadge(
                text = ramSafety.label,
                tone = ramSafety.tone,
                leadingDot = true,
            )
        }

        Spacer(Modifier.height(10.dp))

        // Tabular Monospace Hardware Spec Row
        Text(
            text = "${model.quantization} · ${model.fileSizeMb} MB · est. ${model.estimatedRamMb} MB RAM · ctx ${model.contextLength}",
            style = SovereignTypography.monoTag,
            color = colors.textSecondary,
        )

        if (!ramSafety.isLoadable && !ramSafety.reason.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = shapes.xs,
                color = colors.errorContainer,
                border = BorderStroke(
                    SovereignElevation.hairlineWidth,
                    colors.error.copy(alpha = 0.30f),
                ),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = colors.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = ramSafety.reason,
                        style = SovereignTypography.bodyCompact,
                        color = colors.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            if (!ramSafety.reason.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = ramSafety.reason,
                    style = SovereignTypography.bodyCompact,
                    color = if (ramSafety.tone == SovereignStatusTone.ERROR) colors.error else colors.amber,
                )
            }
            if (model.hasVision && !model.supportsVisionModality && !model.visionUnsupportedReason.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = model.visionUnsupportedReason,
                    style = SovereignTypography.bodyCompact,
                    color = colors.textMuted,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = if (isActive) {
                        Icons.Rounded.CheckCircleOutline
                    } else {
                        Icons.Rounded.PlayArrow
                    },
                    contentDescription = null,
                    tint = if (isActive) colors.accentPrimary else colors.textSecondary,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = if (isActive) "Active model · Tap to reload" else "Tap to load model",
                    style = SovereignTypography.actionLabel,
                    color = if (isActive) colors.accentPrimary else colors.textSecondary,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CatalogModelCard(
    item: ModelCatalogItem,
    isInstalled: Boolean,
    ramAssessment: ModelRamAssessment,
    isCompact: Boolean,
    onViewRepo: () -> Unit,
    onDownload: () -> Unit,
    onCopyUrl: () -> Unit,
) {
    val colors = SovereignTheme.colors

    SovereignHairlineCard(
        containerColor = colors.surfaceLow,
        borderColor = if (isInstalled) colors.success.copy(alpha = 0.35f) else colors.hairlineSubtle,
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = SovereignTypography.cardTitle,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "by ${item.publisher}",
                    style = SovereignTypography.bodyCompact,
                    color = colors.textSecondary,
                )
            }
            if (isInstalled) {
                Spacer(Modifier.width(8.dp))
                SovereignBadge(
                    text = "Installed",
                    tone = SovereignStatusTone.SUCCESS,
                    leadingDot = true,
                )
            }
        }

        if (item.description.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = item.description,
                style = SovereignTypography.bodyCompact,
                color = colors.textPrimary,
            )
        }

        if (!item.capabilityNote.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.capabilityNote,
                style = SovereignTypography.thinkingBody,
                color = colors.textMuted,
            )
        }

        Spacer(Modifier.height(10.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ModelFormatBadge(item.format)
            SovereignBadge(
                text = item.quantization,
                tone = SovereignStatusTone.NEUTRAL,
            )
            SovereignBadge(
                text = item.fileSizeFormatted,
                tone = SovereignStatusTone.NEUTRAL,
            )
            SovereignBadge(
                text = "Rec. ${item.recommendedRamMb} MB",
                tone = SovereignStatusTone.NEUTRAL,
            )
            if (item.hasVision) {
                SovereignBadge(
                    text = "VLM",
                    tone = SovereignStatusTone.SUCCESS,
                )
            }
            if (item.isAbliterated) {
                SovereignBadge(
                    text = "Abliterated",
                    tone = SovereignStatusTone.WARNING,
                )
            }
            SovereignBadge(
                text = ramAssessment.label,
                tone = ramAssessment.tone,
                leadingDot = true,
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Min ${item.minRamMb} MB RAM · Backends: ${item.supportedBackends.joinToString("/") { it.chipText }}",
            style = SovereignTypography.monoTag,
            color = colors.textMuted,
        )

        if (!ramAssessment.reason.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = ramAssessment.reason,
                style = SovereignTypography.bodyCompact,
                color = if (ramAssessment.tone == SovereignStatusTone.ERROR) colors.error else colors.amber,
            )
        }

        Spacer(Modifier.height(12.dp))

        CatalogActionRow(
            isCompact = isCompact,
            onViewRepo = onViewRepo,
            onCopyUrl = onCopyUrl,
            onDownload = onDownload,
        )
    }
}

@Composable
private fun ModelFormatBadge(format: ModelFormat) {
    when (format) {
        ModelFormat.GGUF -> SovereignBadge(
            text = "GGUF",
            tone = SovereignStatusTone.VIOLET,
        )
        ModelFormat.LITERT -> SovereignBadge(
            text = "LITERT",
            tone = SovereignStatusTone.ACCENT,
        )
    }
}

@Composable
private fun CatalogActionRow(
    isCompact: Boolean,
    onViewRepo: () -> Unit,
    onCopyUrl: () -> Unit,
    onDownload: () -> Unit,
) {
    val buttonPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
    val secondaryButtons: @Composable RowScope.() -> Unit = {
        SovereignSecondaryButton(
            text = "View Repo",
            onClick = onViewRepo,
            leadingIcon = Icons.AutoMirrored.Rounded.OpenInNew,
            contentPadding = buttonPadding,
            hapticFeedback = true,
            modifier = Modifier.weight(1f),
        )
        SovereignSecondaryButton(
            text = "Copy URL",
            onClick = onCopyUrl,
            leadingIcon = Icons.Rounded.ContentCopy,
            contentPadding = buttonPadding,
            hapticFeedback = true,
            modifier = Modifier.weight(1f),
        )
    }
    val downloadButton: @Composable (Modifier) -> Unit = { mod ->
        SovereignPrimaryButton(
            text = "Download",
            onClick = onDownload,
            leadingIcon = Icons.Rounded.Download,
            contentPadding = buttonPadding,
            hapticFeedback = true,
            modifier = mod,
        )
    }

    if (isCompact) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = secondaryButtons,
            )
            downloadButton(Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            secondaryButtons()
            downloadButton(Modifier.weight(1f))
        }
    }
}

private fun isSameModelFile(a: File?, b: File?): Boolean {
    if (a == null || b == null) return false
    if (a.absolutePath == b.absolutePath) return true
    return try {
        a.canonicalPath == b.canonicalPath
    } catch (_: Exception) {
        false
    }
}

private fun openExternalUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "No app available to open link", Toast.LENGTH_SHORT).show()
    }
}

private fun copyUrlToClipboard(context: Context, modelName: String, url: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    if (clipboard != null) {
        clipboard.setPrimaryClip(ClipData.newPlainText("$modelName Download URL", url))
        Toast.makeText(context, "Download URL copied to clipboard", Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(context, "Clipboard unavailable", Toast.LENGTH_SHORT).show()
    }
}
