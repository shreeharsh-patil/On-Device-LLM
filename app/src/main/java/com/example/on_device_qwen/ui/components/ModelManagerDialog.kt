package com.example.on_device_qwen.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.on_device_qwen.R
import com.example.on_device_qwen.domain.modelmanager.ImportProgress
import com.example.on_device_qwen.domain.modelmanager.ModelCatalogItem
import com.example.on_device_qwen.domain.modelmanager.ModelImportState
import com.example.on_device_qwen.model.Modality
import com.example.on_device_qwen.model.ModelCompatibilityStatus
import com.example.on_device_qwen.model.ModelMetadata
import com.example.on_device_qwen.ui.designsystem.SovereignAlertDialog
import com.example.on_device_qwen.ui.designsystem.SovereignBadge
import com.example.on_device_qwen.ui.designsystem.SovereignBanner
import com.example.on_device_qwen.ui.designsystem.SovereignEmptyState
import com.example.on_device_qwen.ui.designsystem.SovereignErrorCard
import com.example.on_device_qwen.ui.designsystem.SovereignHairlineCard
import com.example.on_device_qwen.ui.designsystem.SovereignIconButton
import com.example.on_device_qwen.ui.designsystem.SovereignModalSheet
import com.example.on_device_qwen.ui.designsystem.SovereignPrimaryButton
import com.example.on_device_qwen.ui.designsystem.SovereignSecondaryButton
import com.example.on_device_qwen.ui.designsystem.SovereignSegmentedControl
import com.example.on_device_qwen.ui.designsystem.SovereignStatusTone
import com.example.on_device_qwen.ui.designsystem.SovereignTheme
import com.example.on_device_qwen.viewmodel.CatalogModelRecommendation
import com.example.on_device_qwen.viewmodel.ModelHubUiState

private enum class ModelHubTab {
    INSTALLED, DISCOVER, IMPORT
}

@Composable
fun ModelManagerDialog(
    state: ModelHubUiState,
    onSelectModel: (ModelMetadata) -> Unit,
    onImportUri: (Uri) -> Unit,
    onDeleteModel: (ModelMetadata) -> Unit,
    onDismissImportError: () -> Unit,
    onRefreshModels: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = SovereignTheme.colors
    val typography = SovereignTheme.typography
    val spacing = SovereignTheme.spacing
    val context = LocalContext.current

    var selectedTab by remember { mutableStateOf(ModelHubTab.INSTALLED) }
    var modelToDelete by remember { mutableStateOf<ModelMetadata?>(null) }
    var inspectedInstalledModel by remember { mutableStateOf<ModelMetadata?>(null) }
    var inspectedCatalogItem by remember { mutableStateOf<CatalogModelRecommendation?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            selectedTab = ModelHubTab.IMPORT
            onImportUri(uri)
        }
    }

    modelToDelete?.let { target ->
        SovereignAlertDialog(
            title = stringResource(R.string.model_hub_delete_title),
            text = stringResource(R.string.model_hub_delete_body, target.name),
            confirmText = stringResource(R.string.label_delete),
            isDestructive = true,
            onConfirm = {
                onDeleteModel(target)
                modelToDelete = null
            },
            onDismissRequest = { modelToDelete = null },
        )
    }

    inspectedInstalledModel?.let { meta ->
        SovereignAlertDialog(
            title = meta.name,
            confirmText = stringResource(R.string.model_hub_load_action),
            dismissText = stringResource(R.string.cd_close),
            onConfirm = {
                inspectedInstalledModel = null
                onSelectModel(meta)
            },
            onDismissRequest = { inspectedInstalledModel = null },
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DetailKV("Format", meta.format.name)
                    DetailKV("Runtime", meta.engineType.displayName)
                    DetailKV("Architecture", meta.architecture)
                    DetailKV("Quantization", meta.quantization)
                    DetailKV("File Size", "${meta.fileSizeMb} MB")
                    DetailKV("Estimated RAM", "${meta.estimatedRamMb} MB")
                    DetailKV("Context Limit", "${meta.contextLength} tokens")
                    DetailKV("Vision Modality", if (meta.supportsVisionModality) "Metadata reports Vision" else "Text only")
                    meta.compatibilityMessage?.let { msg ->
                        Spacer(Modifier.height(4.dp))
                        Text(text = msg, style = typography.bodyCompact, color = colors.amber)
                    }
                    Text(
                        text = meta.file.absolutePath,
                        style = typography.telemetry,
                        color = colors.textMuted,
                    )
                }
            },
        )
    }

    val noBrowserToast = stringResource(R.string.model_hub_no_browser_toast)
    val openExternalUrl: (String) -> Unit = { url ->
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, noBrowserToast, Toast.LENGTH_SHORT).show()
        }
    }

    inspectedCatalogItem?.let { rec ->
        val item = rec.item
        SovereignAlertDialog(
            title = item.name,
            confirmText = stringResource(R.string.model_hub_download_action),
            dismissText = stringResource(R.string.cd_close),
            onConfirm = {
                inspectedCatalogItem = null
                openExternalUrl(item.downloadUrl)
            },
            onDismissRequest = { inspectedCatalogItem = null },
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DetailKV("Publisher", item.publisher)
                    DetailKV("Format", "${item.format.extension.uppercase()} · ${item.quantization}")
                    DetailKV("File Size", item.fileSizeFormatted)
                    DetailKV("Min / Rec RAM", "${item.minRamMb} MB / ${item.recommendedRamMb} MB")
                    DetailKV("Device Fit", rec.fitSummary)
                    item.sha256?.let { sha ->
                        DetailKV("SHA-256", "${sha.take(16)}…")
                    }
                    if (item.description.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(text = item.description, style = typography.bodyCompact, color = colors.textSecondary)
                    }
                    item.capabilityNote?.let { note ->
                        Text(text = note, style = typography.bodyCompact, color = colors.amber)
                    }
                }
            },
        )
    }

    val installedTabLabel = stringResource(R.string.model_hub_tab_installed)
    val discoverTabLabel = stringResource(R.string.model_hub_tab_discover)
    val importTabLabel = stringResource(R.string.model_hub_tab_import)

    val subtitleText = buildString {
        append("${state.availRamMb} MB free")
        if (state.totalRamMb > 0) append(" / ${state.totalRamMb} MB")
        append(" · ${state.currentBackend.chipText}")
    }

    SovereignModalSheet(
        title = stringResource(R.string.model_hub_title),
        subtitle = subtitleText,
        onDismissRequest = onDismiss,
        headerActions = {
            SovereignIconButton(
                onClick = onRefreshModels,
                contentDescription = stringResource(R.string.label_retry),
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        },
        stickyHeaderExtra = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                SovereignSegmentedControl(
                    options = ModelHubTab.entries,
                    selected = selectedTab,
                    onSelect = { selectedTab = it },
                    label = { tab ->
                        when (tab) {
                            ModelHubTab.INSTALLED -> "$installedTabLabel (${state.discoveredModels.size})"
                            ModelHubTab.DISCOVER -> discoverTabLabel
                            ModelHubTab.IMPORT -> importTabLabel
                        }
                    },
                    hapticFeedback = SovereignTheme.hapticsEnabled,
                )
                SovereignBanner(
                    message = state.importError,
                    tone = SovereignStatusTone.ERROR,
                    onDismiss = onDismissImportError,
                )
            }
        },
    ) {
        when (selectedTab) {
            ModelHubTab.INSTALLED -> InstalledModelsContent(
                models = state.discoveredModels,
                activeModel = state.activeModel,
                onSelectModel = onSelectModel,
                onInspectModel = { inspectedInstalledModel = it },
                onDeleteRequest = { modelToDelete = it },
                onGoToImport = { filePickerLauncher.launch(arrayOf("*/*")) },
                onGoToDiscover = { selectedTab = ModelHubTab.DISCOVER },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            ModelHubTab.DISCOVER -> DiscoverCatalogContent(
                recommendations = state.catalogRecommendations,
                onInspect = { inspectedCatalogItem = it },
                onOpenDownload = { openExternalUrl(it.item.downloadUrl) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            ModelHubTab.IMPORT -> ImportModelContent(
                importState = state.importState,
                onPickFile = { filePickerLauncher.launch(arrayOf("*/*")) },
                onRetry = {
                    onDismissImportError()
                    filePickerLauncher.launch(arrayOf("*/*"))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

@Composable
private fun InstalledModelsContent(
    models: List<ModelMetadata>,
    activeModel: ModelMetadata?,
    onSelectModel: (ModelMetadata) -> Unit,
    onInspectModel: (ModelMetadata) -> Unit,
    onDeleteRequest: (ModelMetadata) -> Unit,
    onGoToImport: () -> Unit,
    onGoToDiscover: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SovereignTheme.colors
    val typography = SovereignTheme.typography
    val spacing = SovereignTheme.spacing

    if (models.isEmpty()) {
        Column(
            modifier = modifier.padding(horizontal = spacing.lg, vertical = spacing.lg),
            verticalArrangement = Arrangement.Center,
        ) {
            SovereignEmptyState(
                title = stringResource(R.string.model_hub_empty_installed_title),
                subtitle = stringResource(R.string.model_hub_empty_installed_body),
                icon = Icons.Rounded.Storage,
                action = {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        SovereignPrimaryButton(
                            text = stringResource(R.string.model_hub_import_button),
                            onClick = onGoToImport,
                            leadingIcon = Icons.Rounded.FileOpen,
                            useAccent = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        SovereignSecondaryButton(
                            text = stringResource(R.string.model_hub_tab_discover),
                            onClick = onGoToDiscover,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        items(models, key = { it.file.absolutePath }) { model ->
            val isActive = activeModel?.file?.absolutePath == model.file.absolutePath
            val compatTone = when (model.compatibilityStatus) {
                ModelCompatibilityStatus.COMPATIBLE -> SovereignStatusTone.SUCCESS
                ModelCompatibilityStatus.TIGHT_RAM -> SovereignStatusTone.WARNING
                ModelCompatibilityStatus.INSUFFICIENT_RAM,
                ModelCompatibilityStatus.UNSUPPORTED_FORMAT,
                ModelCompatibilityStatus.CORRUPTED,
                -> SovereignStatusTone.ERROR
            }

            SovereignHairlineCard(
                modifier = Modifier.fillMaxWidth(),
                selected = isActive,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = model.name,
                            style = typography.cardTitle,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${model.format.name} · ${model.quantization} · ${model.fileSizeMb} MB (~${model.estimatedRamMb} MB RAM)",
                            style = typography.telemetry,
                            color = colors.textMuted,
                        )
                    }
                    if (isActive) {
                        SovereignBadge(
                            text = stringResource(R.string.label_active),
                            tone = SovereignStatusTone.ACCENT,
                            leadingDot = true,
                        )
                    }
                }

                Spacer(Modifier.height(spacing.xs))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SovereignBadge(
                        text = model.compatibilityStatus.name.replace('_', ' '),
                        tone = compatTone,
                    )
                    if (model.supportsVisionModality) {
                        SovereignBadge(text = "Vision", tone = SovereignStatusTone.VIOLET)
                    }
                    if (model.isAbliterated) {
                        SovereignBadge(text = "Uncensored", tone = SovereignStatusTone.NEUTRAL)
                    }
                }

                model.compatibilityMessage?.let { msg ->
                    Spacer(Modifier.height(spacing.xs))
                    Text(
                        text = msg,
                        style = typography.bodyCompact,
                        color = colors.amber,
                    )
                }

                Spacer(Modifier.height(spacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SovereignPrimaryButton(
                        text = if (isActive) stringResource(R.string.label_active) else stringResource(R.string.model_hub_load_action),
                        onClick = { onSelectModel(model) },
                        enabled = !isActive && model.compatibilityStatus != ModelCompatibilityStatus.CORRUPTED,
                        useAccent = !isActive,
                        leadingIcon = Icons.Rounded.AutoAwesome,
                        modifier = Modifier.weight(1f),
                    )
                    SovereignIconButton(
                        onClick = { onInspectModel(model) },
                        contentDescription = stringResource(R.string.model_hub_details_action),
                    ) {
                        Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(19.dp))
                    }
                    SovereignIconButton(
                        onClick = { onDeleteRequest(model) },
                        contentDescription = stringResource(R.string.label_delete),
                        contentColor = colors.error,
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(19.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverCatalogContent(
    recommendations: List<CatalogModelRecommendation>,
    onInspect: (CatalogModelRecommendation) -> Unit,
    onOpenDownload: (CatalogModelRecommendation) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SovereignTheme.colors
    val typography = SovereignTheme.typography
    val spacing = SovereignTheme.spacing

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        items(recommendations, key = { it.item.id }) { rec ->
            val item: ModelCatalogItem = rec.item
            val fitTone = when {
                rec.isRecommendedFit -> SovereignStatusTone.SUCCESS
                rec.fitsDeviceRam -> SovereignStatusTone.WARNING
                else -> SovereignStatusTone.ERROR
            }

            SovereignHairlineCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onInspect(rec) },
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
                            text = "${item.publisher} · ${item.format.extension.uppercase()} · ${item.quantization} · ${item.fileSizeFormatted}",
                            style = typography.telemetry,
                            color = colors.textMuted,
                        )
                    }
                    SovereignBadge(
                        text = if (rec.isRecommendedFit) "Recommended" else if (rec.fitsDeviceRam) "Tight" else "Too Large",
                        tone = fitTone,
                    )
                }

                Spacer(Modifier.height(spacing.xs))
                Text(
                    text = item.description,
                    style = typography.bodyCompact,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(spacing.xs))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SovereignBadge(text = rec.fitSummary, tone = fitTone)
                    if (Modality.VISION in item.modalities) {
                        SovereignBadge(text = "Vision", tone = SovereignStatusTone.VIOLET)
                    }
                    if (item.isAbliterated) {
                        SovereignBadge(text = "Uncensored", tone = SovereignStatusTone.NEUTRAL)
                    }
                }
                item.capabilityNote?.let { note ->
                    Spacer(Modifier.height(spacing.xs))
                    Text(text = note, style = typography.bodyCompact, color = colors.textMuted)
                }
                Spacer(Modifier.height(spacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    SovereignSecondaryButton(
                        text = stringResource(R.string.model_hub_download_action),
                        onClick = { onOpenDownload(rec) },
                        leadingIcon = Icons.Rounded.OpenInBrowser,
                        modifier = Modifier.weight(1f),
                    )
                    SovereignIconButton(
                        onClick = { onInspect(rec) },
                        contentDescription = stringResource(R.string.model_hub_details_action),
                    ) {
                        Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(19.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportModelContent(
    importState: ModelImportState,
    onPickFile: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SovereignTheme.colors
    val typography = SovereignTheme.typography
    val spacing = SovereignTheme.spacing

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        SovereignHairlineCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.model_hub_import_title),
                style = typography.cardTitle,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(spacing.xs))
            Text(
                text = stringResource(R.string.model_hub_import_desc),
                style = typography.bodyCompact,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(spacing.md))
            val isImporting = importState.progress in listOf(
                ImportProgress.COPYING,
                ImportProgress.VERIFYING,
                ImportProgress.FINALIZING,
            )
            SovereignPrimaryButton(
                text = stringResource(R.string.model_hub_import_button),
                onClick = onPickFile,
                enabled = !isImporting,
                loading = isImporting,
                leadingIcon = Icons.Rounded.FileOpen,
                useAccent = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (importState.progress == ImportProgress.FAILED) {
            SovereignErrorCard(
                title = ImportProgress.FAILED.displayLabel,
                message = importState.errorMessage ?: "Model import failed.",
                actions = {
                    SovereignSecondaryButton(
                        text = stringResource(R.string.label_retry),
                        onClick = onRetry,
                        leadingIcon = Icons.Rounded.Refresh,
                    )
                },
            )
        } else if (importState.progress != ImportProgress.IDLE) {
            SovereignHairlineCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = importState.fileName.ifBlank { "Selected model" },
                    style = typography.pillTitle,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = importState.progress.displayLabel,
                    style = typography.bodyCompact,
                    color = colors.accentPrimary,
                )
                Spacer(Modifier.height(spacing.sm))
                val fraction = importState.fraction
                if (fraction != null) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.accentPrimary,
                        trackColor = colors.surfaceBase,
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.accentPrimary,
                        trackColor = colors.surfaceBase,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailKV(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = SovereignTheme.typography.bodyCompact,
            color = SovereignTheme.colors.textSecondary,
        )
        Text(
            text = value,
            style = SovereignTheme.typography.telemetry,
            color = SovereignTheme.colors.textPrimary,
        )
    }
}
