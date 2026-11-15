package com.example.on_device_qwen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.on_device_qwen.R
import com.example.on_device_qwen.model.ConversationSession
import com.example.on_device_qwen.ui.designsystem.SovereignAlertDialog
import com.example.on_device_qwen.ui.designsystem.SovereignBadge
import com.example.on_device_qwen.ui.designsystem.SovereignDivider
import com.example.on_device_qwen.ui.designsystem.SovereignEmptyState
import com.example.on_device_qwen.ui.designsystem.SovereignIconButton
import com.example.on_device_qwen.ui.designsystem.SovereignPrimaryButton
import com.example.on_device_qwen.ui.designsystem.SovereignSearchBar
import com.example.on_device_qwen.ui.designsystem.SovereignSecondaryButton
import com.example.on_device_qwen.ui.designsystem.SovereignStatusTone
import com.example.on_device_qwen.ui.designsystem.SovereignTextField
import com.example.on_device_qwen.ui.designsystem.SovereignTheme
import java.util.Calendar
import java.util.Locale

@Composable
fun ChatNavigationDrawerContent(
    conversations: List<ConversationSession>,
    activeConversationId: String?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelectConversation: (ConversationSession) -> Unit,
    onNewChat: () -> Unit,
    onRenameConversation: (ConversationSession, String) -> Unit,
    onDeleteConversation: (ConversationSession) -> Unit,
    onTogglePin: (ConversationSession) -> Unit,
    onShareConversation: (ConversationSession) -> Unit,
    onOpenModelManager: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    batteryPercent: Int,
    batteryTemp: Float,
    availableRamMb: Long,
    showCloseButton: Boolean = false,
    onCloseDrawer: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = SovereignTheme.colors
    val typography = SovereignTheme.typography
    val spacing = SovereignTheme.spacing

    var sessionToRename by remember { mutableStateOf<ConversationSession?>(null) }
    var renameText by remember { mutableStateOf("") }
    var sessionToDelete by remember { mutableStateOf<ConversationSession?>(null) }

    sessionToRename?.let { target ->
        SovereignAlertDialog(
            title = stringResource(R.string.drawer_rename_title),
            confirmText = stringResource(R.string.label_save),
            confirmEnabled = renameText.isNotBlank(),
            onConfirm = {
                onRenameConversation(target, renameText.trim())
                sessionToRename = null
            },
            onDismissRequest = { sessionToRename = null },
            content = {
                SovereignTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = stringResource(R.string.drawer_rename_label),
                    singleLine = true,
                )
            },
        )
    }

    sessionToDelete?.let { target ->
        SovereignAlertDialog(
            title = stringResource(R.string.drawer_delete_title),
            text = stringResource(R.string.drawer_delete_body, target.title),
            confirmText = stringResource(R.string.label_delete),
            isDestructive = true,
            onConfirm = {
                onDeleteConversation(target)
                sessionToDelete = null
            },
            onDismissRequest = { sessionToDelete = null },
        )
    }

    val pinnedChats = remember(conversations) {
        conversations.filter { it.isPinned }.sortedByDescending { it.updatedAt }
    }
    val recentGroups = remember(conversations) {
        groupUnpinnedConversationsByDate(conversations.filterNot { it.isPinned })
    }

    ModalDrawerSheet(
        modifier = modifier
            .width(312.dp)
            .fillMaxHeight(),
        drawerContainerColor = colors.surfaceLow,
        drawerContentColor = colors.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_display_name),
                        style = typography.sheetHeader,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.app_tagline),
                        style = typography.bodyCompact,
                        color = colors.textMuted,
                    )
                }
                if (showCloseButton) {
                    SovereignIconButton(
                        onClick = onCloseDrawer,
                        contentDescription = stringResource(R.string.cd_close),
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                SovereignPrimaryButton(
                    text = stringResource(R.string.drawer_new_chat),
                    onClick = onNewChat,
                    leadingIcon = Icons.Rounded.Add,
                    useAccent = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SovereignSearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    placeholder = stringResource(R.string.drawer_search_placeholder),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(spacing.sm))
            SovereignDivider()

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = spacing.sm, vertical = spacing.sm),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (conversations.isEmpty()) {
                    item(key = "empty_drawer") {
                        if (searchQuery.isNotBlank()) {
                            SovereignEmptyState(
                                title = stringResource(R.string.drawer_empty_search, searchQuery.trim()),
                                action = {
                                    SovereignSecondaryButton(
                                        text = stringResource(R.string.drawer_clear_search),
                                        onClick = { onSearchQueryChange("") },
                                    )
                                },
                                modifier = Modifier.padding(vertical = spacing.lg),
                            )
                        } else {
                            SovereignEmptyState(
                                title = stringResource(R.string.drawer_empty_title),
                                subtitle = stringResource(R.string.empty_chat_subtitle),
                                modifier = Modifier.padding(vertical = spacing.lg),
                            )
                        }
                    }
                } else {
                    if (pinnedChats.isNotEmpty()) {
                        item(key = "header_pinned") {
                            DrawerSectionTitle(stringResource(R.string.drawer_section_pinned))
                        }
                        items(pinnedChats, key = { "pin_${it.id}" }) { session ->
                            ConversationRowItem(
                                session = session,
                                isActive = session.id == activeConversationId,
                                onSelect = { onSelectConversation(session) },
                                onRename = {
                                    sessionToRename = session
                                    renameText = session.title
                                },
                                onTogglePin = { onTogglePin(session) },
                                onShare = { onShareConversation(session) },
                                onDelete = { sessionToDelete = session },
                            )
                        }
                    }

                    recentGroups.forEach { (groupLabel, groupItems) ->
                        item(key = "header_$groupLabel") {
                            DrawerSectionTitle(groupLabel)
                        }
                        items(groupItems, key = { it.id }) { session ->
                            ConversationRowItem(
                                session = session,
                                isActive = session.id == activeConversationId,
                                onSelect = { onSelectConversation(session) },
                                onRename = {
                                    sessionToRename = session
                                    renameText = session.title
                                },
                                onTogglePin = { onTogglePin(session) },
                                onShare = { onShareConversation(session) },
                                onDelete = { sessionToDelete = session },
                            )
                        }
                    }
                }
            }

            SovereignDivider()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.sm, vertical = spacing.xs),
            ) {
                DrawerActionRow(
                    label = stringResource(R.string.label_models),
                    icon = Icons.Rounded.Storage,
                    onClick = onOpenModelManager,
                )
                DrawerActionRow(
                    label = stringResource(R.string.label_memory),
                    icon = Icons.Rounded.Psychology,
                    onClick = onOpenMemory,
                )
                DrawerActionRow(
                    label = stringResource(R.string.label_diagnostics),
                    icon = Icons.Rounded.MonitorHeart,
                    onClick = onOpenDiagnostics,
                )
                DrawerActionRow(
                    label = stringResource(R.string.label_settings),
                    icon = Icons.Rounded.Settings,
                    onClick = onOpenSettings,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceBase.copy(alpha = 0.7f))
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Memory,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = colors.accentPrimary,
                    )
                    Text(
                        text = stringResource(R.string.drawer_telemetry_ram, availableRamMb.toString()),
                        style = typography.telemetry,
                        color = colors.textSecondary,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BatteryChargingFull,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = colors.textMuted,
                    )
                    Text(
                        text = String.format(Locale.US, "%d%% · %.1f°C", batteryPercent, batteryTemp),
                        style = typography.telemetry,
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerSectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        style = SovereignTheme.typography.monoTag,
        color = SovereignTheme.colors.textMuted,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun ConversationRowItem(
    session: ConversationSession,
    isActive: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onTogglePin: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = SovereignTheme.colors
    val typography = SovereignTheme.typography
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(SovereignTheme.shapes.sm)
            .background(if (isActive) colors.accentContainer else Color.Transparent)
            .clickable(role = Role.Button, onClick = onSelect)
            .padding(start = 12.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (session.isPinned) {
            Icon(
                imageVector = Icons.Rounded.PushPin,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = colors.accentPrimary,
            )
            Spacer(Modifier.width(8.dp))
        }

        Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                text = session.title.ifBlank { "Untitled Chat" },
                style = typography.pillTitle,
                color = if (isActive) colors.onAccentContainer else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (session.lastMessagePreview.isNotBlank()) {
                Text(
                    text = session.lastMessagePreview,
                    style = typography.bodyCompact,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (isActive) {
            SovereignBadge(
                text = stringResource(R.string.label_active),
                tone = SovereignStatusTone.ACCENT,
            )
            Spacer(Modifier.width(2.dp))
        }

        Box {
            SovereignIconButton(
                onClick = { menuExpanded = true },
                contentDescription = stringResource(R.string.cd_more_options),
                contentColor = colors.textMuted,
            ) {
                Icon(Icons.Rounded.MoreVert, contentDescription = null, modifier = Modifier.size(18.dp))
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                containerColor = colors.surfaceHigh,
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(if (session.isPinned) R.string.label_unpin else R.string.label_pin),
                            color = colors.textPrimary,
                        )
                    },
                    leadingIcon = { Icon(Icons.Rounded.PushPin, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onTogglePin()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.label_rename), color = colors.textPrimary) },
                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.label_share_markdown), color = colors.textPrimary) },
                    leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onShare()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.label_delete), color = colors.error) },
                    leadingIcon = {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = colors.error)
                    },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun DrawerActionRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val colors = SovereignTheme.colors
    val typography = SovereignTheme.typography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(SovereignTheme.shapes.sm)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(19.dp),
            tint = colors.textSecondary,
        )
        Text(
            text = label,
            style = typography.pillTitle,
            color = colors.textPrimary,
        )
    }
}

private fun groupUnpinnedConversationsByDate(
    list: List<ConversationSession>,
): List<Pair<String, List<ConversationSession>>> {
    if (list.isEmpty()) return emptyList()
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val todayStart = cal.timeInMillis
    val yesterdayStart = todayStart - 86_400_000L
    val weekStart = todayStart - 7L * 86_400_000L

    val buckets = linkedMapOf<String, MutableList<ConversationSession>>(
        "Today" to mutableListOf(),
        "Yesterday" to mutableListOf(),
        "Previous 7 Days" to mutableListOf(),
        "Older" to mutableListOf(),
    )
    list.sortedByDescending { it.updatedAt }.forEach { session ->
        val key = when {
            session.updatedAt >= todayStart -> "Today"
            session.updatedAt >= yesterdayStart -> "Yesterday"
            session.updatedAt >= weekStart -> "Previous 7 Days"
            else -> "Older"
        }
        buckets.getValue(key).add(session)
    }
    return buckets.entries.filter { it.value.isNotEmpty() }.map { it.key to it.value }
}
