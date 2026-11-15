package com.example.on_device_qwen.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.on_device_qwen.R
import com.example.on_device_qwen.model.MemoryCategory
import com.example.on_device_qwen.model.MemoryItem
import com.example.on_device_qwen.ui.designsystem.SovereignAlertDialog
import com.example.on_device_qwen.ui.designsystem.SovereignBadge
import com.example.on_device_qwen.ui.designsystem.SovereignElevation
import com.example.on_device_qwen.ui.designsystem.SovereignEmptyState
import com.example.on_device_qwen.ui.designsystem.SovereignHairlineCard
import com.example.on_device_qwen.ui.designsystem.SovereignIconButton
import com.example.on_device_qwen.ui.designsystem.SovereignModalSheet
import com.example.on_device_qwen.ui.designsystem.SovereignPrimaryButton
import com.example.on_device_qwen.ui.designsystem.SovereignSearchBar
import com.example.on_device_qwen.ui.designsystem.SovereignSecondaryButton
import com.example.on_device_qwen.ui.designsystem.SovereignStatusTone
import com.example.on_device_qwen.ui.designsystem.SovereignSurface
import com.example.on_device_qwen.ui.designsystem.SovereignSwitchRow
import com.example.on_device_qwen.ui.designsystem.SovereignTextField
import com.example.on_device_qwen.ui.designsystem.SovereignTheme
import com.example.on_device_qwen.viewmodel.MemoryUiState

@Composable
fun MemoryManagerDialog(
    state: MemoryUiState,
    onToggleMemory: (Boolean) -> Unit,
    onSaveMemory: (MemoryItem) -> Unit,
    onUpdateMemory: (String, String, MemoryCategory?) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onClearAllMemories: () -> Unit,
    onSearchMemories: (String) -> Unit,
    onSelectCategory: (MemoryCategory?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = SovereignTheme.colors
    val typography = SovereignTheme.typography
    val spacing = SovereignTheme.spacing

    var showAddDialog by remember { mutableStateOf(false) }
    var memoryToEdit by remember { mutableStateOf<MemoryItem?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    if (showAddDialog || memoryToEdit != null) {
        val editing = memoryToEdit
        var contentText by remember(editing) { mutableStateOf(editing?.content ?: "") }
        var category by remember(editing) { mutableStateOf(editing?.category ?: MemoryCategory.USER) }

        SovereignAlertDialog(
            title = if (editing != null) "Edit Memory" else stringResource(R.string.memory_add_action),
            confirmText = stringResource(R.string.label_save),
            confirmEnabled = contentText.isNotBlank(),
            onConfirm = {
                val clean = contentText.trim()
                if (editing != null) {
                    onUpdateMemory(editing.id, clean, category)
                } else {
                    onSaveMemory(MemoryItem(category = category, content = clean))
                }
                showAddDialog = false
                memoryToEdit = null
            },
            onDismissRequest = {
                showAddDialog = false
                memoryToEdit = null
            },
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    ) {
                        MemoryCategory.entries.forEach { cat ->
                            MemoryCategoryChip(
                                label = cat.displayName,
                                selected = category == cat,
                                onClick = { category = cat },
                            )
                        }
                    }
                    SovereignTextField(
                        value = contentText,
                        onValueChange = { contentText = it },
                        label = "Memory detail",
                        placeholder = "e.g., Prefer concise Kotlin code without boilerplate.",
                        singleLine = false,
                        minLines = 3,
                        maxLines = 6,
                    )
                }
            },
        )
    }

    if (showClearConfirm) {
        SovereignAlertDialog(
            title = stringResource(R.string.memory_clear_title),
            text = stringResource(R.string.memory_clear_body, state.totalMemoryCount),
            confirmText = stringResource(R.string.memory_clear_all),
            isDestructive = true,
            onConfirm = {
                onClearAllMemories()
                showClearConfirm = false
            },
            onDismissRequest = { showClearConfirm = false },
        )
    }

    SovereignModalSheet(
        title = stringResource(R.string.memory_title),
        subtitle = "${state.totalMemoryCount} stored locally",
        onDismissRequest = onDismiss,
        headerActions = {
            if (state.totalMemoryCount > 0) {
                SovereignIconButton(
                    onClick = { showClearConfirm = true },
                    contentDescription = stringResource(R.string.memory_clear_all),
                    contentColor = colors.error,
                ) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
        },
        stickyHeaderExtra = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                SovereignSwitchRow(
                    title = stringResource(R.string.memory_enable_title),
                    subtitle = stringResource(R.string.memory_enable_desc),
                    checked = state.isMemoryEnabled,
                    onCheckedChange = onToggleMemory,
                    leadingIcon = Icons.Rounded.Psychology,
                    hapticFeedback = SovereignTheme.hapticsEnabled,
                )
                SovereignSearchBar(
                    query = state.searchQuery,
                    onQueryChange = onSearchMemories,
                    placeholder = stringResource(R.string.memory_search_placeholder),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    MemoryCategoryChip(
                        label = "All",
                        selected = state.selectedCategory == null,
                        onClick = { onSelectCategory(null) },
                    )
                    MemoryCategory.entries.forEach { cat ->
                        MemoryCategoryChip(
                            label = cat.displayName,
                            selected = state.selectedCategory == cat,
                            onClick = { onSelectCategory(cat) },
                        )
                    }
                }
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = spacing.lg, vertical = spacing.md),
        ) {
            SovereignPrimaryButton(
                text = stringResource(R.string.memory_add_action),
                onClick = { showAddDialog = true },
                leadingIcon = Icons.Rounded.Add,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(spacing.md))

            if (state.memories.isEmpty()) {
                SovereignEmptyState(
                    title = stringResource(R.string.memory_empty_title),
                    subtitle = stringResource(R.string.memory_empty_body),
                    icon = Icons.Rounded.Psychology,
                    action = if (state.searchQuery.isNotBlank() || state.selectedCategory != null) {
                        {
                            SovereignSecondaryButton(
                                text = stringResource(R.string.drawer_clear_search),
                                onClick = {
                                    onSearchMemories("")
                                    onSelectCategory(null)
                                },
                            )
                        }
                    } else null,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    contentPadding = PaddingValues(bottom = spacing.md),
                ) {
                    items(state.memories, key = { it.id }) { item ->
                        val tone = when (item.category) {
                            MemoryCategory.USER -> SovereignStatusTone.ACCENT
                            MemoryCategory.PROJECT -> SovereignStatusTone.VIOLET
                            MemoryCategory.EPISODIC -> SovereignStatusTone.NEUTRAL
                        }
                        SovereignHairlineCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SovereignBadge(
                                    text = item.category.displayName,
                                    tone = tone,
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    SovereignIconButton(
                                        onClick = { memoryToEdit = item },
                                        contentDescription = "Edit memory",
                                    ) {
                                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                    SovereignIconButton(
                                        onClick = { onDeleteMemory(item.id) },
                                        contentDescription = "Delete memory",
                                        contentColor = colors.error,
                                    ) {
                                        Icon(
                                            Icons.Rounded.DeleteOutline,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(spacing.xs))
                            Text(
                                text = item.content,
                                style = typography.chatProse,
                                color = colors.textPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryCategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = SovereignTheme.colors
    SovereignSurface(
        shape = SovereignTheme.shapes.pill,
        color = if (selected) colors.accentContainer else colors.surfaceBase,
        border = BorderStroke(
            SovereignElevation.hairlineWidth,
            if (selected) colors.accentPrimary else colors.hairlineSubtle,
        ),
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(SovereignTheme.shapes.pill)
            .clickable(role = Role.RadioButton, onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                text = label,
                style = SovereignTheme.typography.actionLabel,
                color = if (selected) colors.onAccentContainer else colors.textSecondary,
            )
        }
    }
}
