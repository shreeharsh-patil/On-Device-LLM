package com.example.on_device_qwen.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.on_device_qwen.model.MemoryCategory
import com.example.on_device_qwen.model.MemoryItem
import com.example.on_device_qwen.ui.designsystem.SovereignAlertDialog
import com.example.on_device_qwen.ui.designsystem.SovereignBadge
import com.example.on_device_qwen.ui.designsystem.SovereignElevation
import com.example.on_device_qwen.ui.designsystem.SovereignEmptyState
import com.example.on_device_qwen.ui.designsystem.SovereignHairlineCard
import com.example.on_device_qwen.ui.designsystem.SovereignIconButton
import com.example.on_device_qwen.ui.designsystem.SovereignModalSheet
import com.example.on_device_qwen.ui.designsystem.SovereignSearchBar
import com.example.on_device_qwen.ui.designsystem.SovereignSecondaryButton
import com.example.on_device_qwen.ui.designsystem.SovereignStatusTone
import com.example.on_device_qwen.ui.designsystem.SovereignSwitchRow
import com.example.on_device_qwen.ui.designsystem.SovereignTextField
import com.example.on_device_qwen.ui.designsystem.SovereignTheme
import com.example.on_device_qwen.ui.designsystem.SovereignTonalButton
import com.example.on_device_qwen.ui.designsystem.SovereignTypography
import java.text.SimpleDateFormat
import java.util.Date
import java.util.EnumMap
import java.util.Locale

internal fun countMemoriesByCategory(memories: List<MemoryItem>): Map<MemoryCategory, Int> {
    val counts = EnumMap<MemoryCategory, Int>(MemoryCategory::class.java)
    MemoryCategory.entries.forEach { counts[it] = 0 }
    memories.forEach { item ->
        counts[item.category] = (counts[item.category] ?: 0) + 1
    }
    return counts
}

internal fun filterMemories(
    memories: List<MemoryItem>,
    selectedCategory: MemoryCategory?,
    query: String,
): List<MemoryItem> {
    val trimmedQuery = query.trim()
    return memories.filter { item ->
        (selectedCategory == null || item.category == selectedCategory) &&
            (trimmedQuery.isBlank() ||
                item.content.contains(trimmedQuery, ignoreCase = true) ||
                item.keywords.contains(trimmedQuery, ignoreCase = true))
    }
}

internal fun mapMemoryCategoryTone(category: MemoryCategory): SovereignStatusTone = when (category) {
    MemoryCategory.USER -> SovereignStatusTone.ACCENT
    MemoryCategory.PROJECT -> SovereignStatusTone.VIOLET
    MemoryCategory.EPISODIC -> SovereignStatusTone.SUCCESS
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemoryManagerDialog(
    memories: List<MemoryItem>,
    isMemoryEnabled: Boolean,
    onToggleMemory: (Boolean) -> Unit,
    onSaveMemory: (MemoryItem) -> Unit,
    onUpdateMemory: (String, String, MemoryCategory?) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onClearAllMemories: () -> Unit,
    onSearchMemories: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = SovereignTheme.colors
    val spacing = SovereignTheme.spacing
    val shapes = SovereignTheme.shapes
    val motion = SovereignTheme.motion
    val adaptive = SovereignTheme.adaptive

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<MemoryCategory?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var memoryToEdit by remember { mutableStateOf<MemoryItem?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val latestSearchQuery by rememberUpdatedState(searchQuery)
    val latestOnSearchMemories by rememberUpdatedState(onSearchMemories)
    DisposableEffect(Unit) {
        onDispose {
            if (latestSearchQuery.isNotEmpty()) {
                latestOnSearchMemories("")
            }
        }
    }

    // Memoize one formatter for the entire sheet — zero per-card allocation.
    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    val categoryCounts = remember(memories) {
        countMemoriesByCategory(memories)
    }

    val filteredMemories = remember(memories, selectedCategory, searchQuery) {
        filterMemories(memories, selectedCategory, searchQuery)
    }

    // Add / Edit Memory Dialog
    if (showAddDialog || memoryToEdit != null) {
        val editing = memoryToEdit
        var contentText by remember(editing) { mutableStateOf(editing?.content ?: "") }
        var category by remember(editing) { mutableStateOf(editing?.category ?: MemoryCategory.USER) }
        val trimmedContent = contentText.trim()

        SovereignAlertDialog(
            title = if (editing != null) "Edit Memory" else "Add Memory",
            confirmText = if (editing != null) "Save Changes" else "Save Memory",
            dismissText = "Cancel",
            confirmEnabled = trimmedContent.isNotEmpty(),
            onConfirm = {
                if (trimmedContent.isNotEmpty()) {
                    if (editing != null) {
                        onUpdateMemory(editing.id, trimmedContent, category)
                    } else {
                        onSaveMemory(MemoryItem(category = category, content = trimmedContent))
                    }
                    showAddDialog = false
                    memoryToEdit = null
                }
            },
            onDismissRequest = {
                showAddDialog = false
                memoryToEdit = null
            },
        ) {
            Text(
                text = "Category",
                style = SovereignTypography.actionLabel,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MemoryCategory.entries.forEach { cat ->
                    val isSelected = category == cat
                    Surface(
                        onClick = { category = cat },
                        shape = shapes.sm,
                        color = if (isSelected) colors.accentContainer else colors.surfaceBase,
                        contentColor = if (isSelected) colors.onAccentContainer else colors.textSecondary,
                        border = BorderStroke(
                            SovereignElevation.hairlineWidth,
                            if (isSelected) colors.accentPrimary else colors.hairlineSubtle,
                        ),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .semantics {
                                role = Role.RadioButton
                                selected = isSelected
                            },
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = cat.displayName,
                                style = SovereignTypography.actionLabel,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            SovereignTextField(
                value = contentText,
                onValueChange = { contentText = it },
                label = "Memory Content",
                placeholder = "e.g. Prefers concise Kotlin code examples",
                minLines = 3,
                maxLines = 6,
                supportingText = "${trimmedContent.length} characters",
            )
        }
    }

    // Destructive Clear All Confirmation Dialog
    if (showClearConfirm) {
        SovereignAlertDialog(
            title = "Clear All Memories",
            text = "Permanently delete all ${memories.size} cross-chat memories stored locally in SQLite? This action cannot be undone.",
            confirmText = "Delete All",
            dismissText = "Cancel",
            isDestructive = true,
            icon = {
                Icon(
                    imageVector = Icons.Outlined.DeleteSweep,
                    contentDescription = null,
                    tint = colors.error,
                    modifier = Modifier.size(20.dp),
                )
            },
            onConfirm = {
                onClearAllMemories()
                showClearConfirm = false
            },
            onDismissRequest = { showClearConfirm = false },
        )
    }

    SovereignModalSheet(
        title = "Persistent Memory",
        subtitle = "${memories.size} ${if (memories.size == 1) "memory" else "memories"} · Local SQLite",
        onDismissRequest = onDismiss,
        headerActions = {
            if (memories.isNotEmpty()) {
                SovereignIconButton(
                    onClick = { showClearConfirm = true },
                    contentDescription = "Clear all memories",
                    contentColor = colors.error,
                    hapticFeedback = true,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            SovereignIconButton(
                onClick = { showAddDialog = true },
                contentDescription = "Add memory",
                contentColor = colors.accentPrimary,
                hapticFeedback = true,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
            }
        },
        stickyHeaderExtra = {
            SovereignSearchBar(
                query = searchQuery,
                onQueryChange = {
                    searchQuery = it
                    onSearchMemories(it)
                },
                placeholder = "Search memories…",
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MemoryCategoryFilterPill(
                    label = "All",
                    count = memories.size,
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                )
                MemoryCategory.entries.forEach { cat ->
                    val count = categoryCounts[cat] ?: 0
                    MemoryCategoryFilterPill(
                        label = cat.name,
                        count = count,
                        selected = selectedCategory == cat,
                        onClick = {
                            selectedCategory = if (selectedCategory == cat) null else cat
                        },
                    )
                }
            }
        },
    ) {
        val horizontalGutter = spacing.screenGutter(adaptive)
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = horizontalGutter,
                vertical = spacing.lg,
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            item(key = "sqlite_master_toggle") {
                SovereignHairlineCard(contentPadding = PaddingValues(0.dp)) {
                    SovereignSwitchRow(
                        title = "Enable Persistent Memory",
                        subtitle = if (isMemoryEnabled) {
                            "Stored locally in SQLite — never leaves device"
                        } else {
                            "Retrieval paused · Stored locally in SQLite — never leaves device"
                        },
                        leadingIcon = Icons.Rounded.Lock,
                        checked = isMemoryEnabled,
                        onCheckedChange = onToggleMemory,
                    )
                }
            }

            if (filteredMemories.isEmpty()) {
                item(key = "memory_empty_state") {
                    if (memories.isEmpty() && searchQuery.isBlank() && selectedCategory == null) {
                        SovereignEmptyState(
                            title = "No Memories Saved Yet",
                            subtitle = "Relevant facts from your conversations are consolidated locally in SQLite, or you can add one manually.",
                            icon = Icons.Rounded.Psychology,
                            action = {
                                SovereignTonalButton(
                                    text = "Add Memory",
                                    leadingIcon = Icons.Rounded.Add,
                                    onClick = { showAddDialog = true },
                                    hapticFeedback = true,
                                )
                            },
                        )
                    } else {
                        SovereignEmptyState(
                            title = "No Matching Memories",
                            subtitle = "Try clearing your search query or switching category filters.",
                            icon = Icons.Rounded.SearchOff,
                            action = {
                                SovereignSecondaryButton(
                                    text = "Reset Filters",
                                    onClick = {
                                        searchQuery = ""
                                        selectedCategory = null
                                        onSearchMemories("")
                                    },
                                )
                            },
                        )
                    }
                }
            } else {
                items(filteredMemories, key = { it.id }) { memory ->
                    MemoryCardItem(
                        memory = memory,
                        dateFormatter = dateFormatter,
                        onEdit = { memoryToEdit = memory },
                        onDelete = { onDeleteMemory(memory.id) },
                        modifier = Modifier.animateItem(
                            fadeInSpec = motion.effectsQuick<Float>(),
                            placementSpec = motion.spatialFast<IntOffset>(),
                            fadeOutSpec = motion.effectsQuick<Float>(),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryCategoryFilterPill(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = SovereignTheme.colors
    val shapes = SovereignTheme.shapes
    val motion = SovereignTheme.motion

    val bg by animateColorAsState(
        targetValue = if (selected) colors.accentContainer else colors.surfaceLow,
        animationSpec = motion.effectsQuick(),
        label = "MemoryFilterBg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) colors.accentPrimary else colors.hairlineSubtle,
        animationSpec = motion.effectsQuick(),
        label = "MemoryFilterBorder",
    )
    val fg = if (selected) colors.onAccentContainer else colors.textSecondary

    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(shapes.pill)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Tab
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = shapes.pill,
            color = bg,
            contentColor = fg,
            border = BorderStroke(SovereignElevation.hairlineWidth, borderColor),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = label,
                    style = SovereignTypography.actionLabel,
                    color = fg,
                    maxLines = 1,
                )
                Text(
                    text = count.toString(),
                    style = SovereignTypography.monoTag,
                    color = if (selected) colors.accentPrimary else colors.textMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun MemoryCardItem(
    memory: MemoryItem,
    dateFormatter: SimpleDateFormat,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SovereignTheme.colors
    val dateStr = remember(memory.updatedAt, dateFormatter) {
        dateFormatter.format(Date(memory.updatedAt))
    }

    SovereignHairlineCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                SovereignBadge(
                    text = memory.category.displayName,
                    tone = mapMemoryCategoryTone(memory.category),
                )
                if (memory.isPinned) {
                    SovereignBadge(
                        text = "Pinned",
                        tone = SovereignStatusTone.NEUTRAL,
                        leadingIcon = Icons.Rounded.PushPin,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = dateStr,
                    style = SovereignTypography.monoTag,
                    color = colors.textMuted,
                )
                Spacer(Modifier.width(4.dp))
                SovereignIconButton(
                    onClick = onEdit,
                    contentDescription = "Edit memory",
                    visualSize = 36.dp,
                    contentColor = colors.textSecondary,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
                SovereignIconButton(
                    onClick = onDelete,
                    contentDescription = "Delete memory",
                    visualSize = 36.dp,
                    contentColor = colors.error,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = memory.content,
            style = SovereignTypography.bodyCompact,
            color = colors.textPrimary,
            modifier = Modifier.padding(end = 10.dp),
        )
    }
}
