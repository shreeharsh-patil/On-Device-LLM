package com.example.on_device_qwen.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalDrawerSheet
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.on_device_qwen.R
import com.example.on_device_qwen.model.ConversationSession
import com.example.on_device_qwen.ui.designsystem.SovereignAlertDialog
import com.example.on_device_qwen.ui.designsystem.SovereignBadge
import com.example.on_device_qwen.ui.designsystem.SovereignDivider
import com.example.on_device_qwen.ui.designsystem.SovereignElevation
import com.example.on_device_qwen.ui.designsystem.SovereignEmptyState
import com.example.on_device_qwen.ui.designsystem.SovereignIconButton
import com.example.on_device_qwen.ui.designsystem.SovereignPrimaryButton
import com.example.on_device_qwen.ui.designsystem.SovereignSearchBar
import com.example.on_device_qwen.ui.designsystem.SovereignSecondaryButton
import com.example.on_device_qwen.ui.designsystem.SovereignSectionHeader
import com.example.on_device_qwen.ui.designsystem.SovereignStatusDot
import com.example.on_device_qwen.ui.designsystem.SovereignStatusTone
import com.example.on_device_qwen.ui.designsystem.SovereignSurface
import com.example.on_device_qwen.ui.designsystem.SovereignTextField
import com.example.on_device_qwen.ui.designsystem.SovereignTheme
import com.example.on_device_qwen.ui.designsystem.SovereignTypography
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

internal object DrawerSectionLabels {
    const val PINNED = "Pinned"
    const val TODAY = "Today"
    const val YESTERDAY = "Yesterday"
    const val PREVIOUS_7_DAYS = "Previous 7 Days"
    const val OLDER = "Older"
}

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
    onOpenModelManager: () -> Unit,
    onOpenMemory: () -> Unit = {},
    onOpenSettings: () -> Unit,
    batteryPercent: Int,
    batteryTemp: Float,
    availableRamMb: Long,
    totalRamMb: Long = 0L,
    activeModelTitle: String = "",
    memoryCount: Int = 0,
    isMemoryEnabled: Boolean = true,
    isEngineReady: Boolean = false,
    isPersistentSidebar: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = SovereignTheme.colors

    var sessionToRename by remember { mutableStateOf<ConversationSession?>(null) }
    var renameText by remember { mutableStateOf("") }
    var sessionToDelete by remember { mutableStateOf<ConversationSession?>(null) }

    val activeRenameSession = sessionToRename
    if (activeRenameSession != null) {
        val submitRename = {
            val trimmed = renameText.trim()
            if (trimmed.isNotEmpty()) {
                onRenameConversation(activeRenameSession, trimmed)
                sessionToRename = null
            }
        }
        SovereignAlertDialog(
            title = "Rename chat",
            confirmText = "Save",
            confirmEnabled = renameText.isNotBlank(),
            onConfirm = submitRename,
            onDismissRequest = { sessionToRename = null },
            content = {
                SovereignTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = "Title",
                    placeholder = "Conversation title",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submitRename() }),
                )
            },
        )
    }

    val activeDeleteSession = sessionToDelete
    if (activeDeleteSession != null) {
        val sessionTitle = activeDeleteSession.title.ifBlank { "New chat" }
        SovereignAlertDialog(
            title = "Delete chat?",
            text = "This permanently removes \"$sessionTitle\" and all of its messages from this device.",
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
                onDeleteConversation(activeDeleteSession)
                sessionToDelete = null
            },
            onDismissRequest = { sessionToDelete = null },
        )
    }

    val bodyContent: @Composable (Modifier) -> Unit = { innerModifier ->
        DrawerBody(
            conversations = conversations,
            activeConversationId = activeConversationId,
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            onSelectConversation = onSelectConversation,
            onNewChat = onNewChat,
            onRequestRename = { session ->
                sessionToRename = session
                renameText = session.title
            },
            onRequestDelete = { session ->
                sessionToDelete = session
            },
            onTogglePin = onTogglePin,
            onOpenModelManager = onOpenModelManager,
            onOpenMemory = onOpenMemory,
            onOpenSettings = onOpenSettings,
            batteryPercent = batteryPercent,
            batteryTemp = batteryTemp,
            availableRamMb = availableRamMb,
            totalRamMb = totalRamMb,
            activeModelTitle = activeModelTitle,
            memoryCount = memoryCount,
            isMemoryEnabled = isMemoryEnabled,
            isEngineReady = isEngineReady,
            modifier = innerModifier,
        )
    }

    if (isPersistentSidebar) {
        Surface(
            modifier = modifier
                .fillMaxHeight()
                .widthIn(max = 296.dp),
            shape = RectangleShape,
            color = colors.surfaceLow,
            contentColor = colors.textPrimary,
            tonalElevation = SovereignElevation.none,
            shadowElevation = SovereignElevation.none,
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                bodyContent(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(SovereignElevation.hairlineWidth)
                        .background(colors.hairlineSubtle),
                )
            }
        }
    } else {
        ModalDrawerSheet(
            drawerShape = RoundedCornerShape(
                topStart = 0.dp,
                topEnd = 20.dp,
                bottomEnd = 20.dp,
                bottomStart = 0.dp,
            ),
            drawerContainerColor = colors.surfaceLow,
            drawerContentColor = colors.textPrimary,
            drawerTonalElevation = SovereignElevation.none,
            windowInsets = WindowInsets(0, 0, 0, 0),
            modifier = modifier.widthIn(max = 312.dp),
        ) {
            bodyContent(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun DrawerBody(
    conversations: List<ConversationSession>,
    activeConversationId: String?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelectConversation: (ConversationSession) -> Unit,
    onNewChat: () -> Unit,
    onRequestRename: (ConversationSession) -> Unit,
    onRequestDelete: (ConversationSession) -> Unit,
    onTogglePin: (ConversationSession) -> Unit,
    onOpenModelManager: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenSettings: () -> Unit,
    batteryPercent: Int,
    batteryTemp: Float,
    availableRamMb: Long,
    totalRamMb: Long,
    activeModelTitle: String,
    memoryCount: Int,
    isMemoryEnabled: Boolean,
    isEngineReady: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = SovereignTheme.colors
    val motion = SovereignTheme.motion
    val focusManager = LocalFocusManager.current
    val startSafePadding = WindowInsets.safeDrawing
        .only(WindowInsetsSides.Start)
        .asPaddingValues()

    val groupedSessions = remember(conversations) {
        groupConversationsByDate(conversations)
    }

    Column(
        modifier = modifier
            .padding(startSafePadding)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            DrawerBrandHeader()

            Spacer(Modifier.height(12.dp))

            SovereignPrimaryButton(
                text = "New chat",
                onClick = {
                    focusManager.clearFocus()
                    onNewChat()
                },
                leadingIcon = Icons.Rounded.Add,
                hapticFeedback = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(10.dp))

            SovereignSearchBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                placeholder = "Search conversations…",
                onSearch = { focusManager.clearFocus() },
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (groupedSessions.isEmpty()) {
                val isSearching = searchQuery.isNotBlank()
                SovereignEmptyState(
                    title = if (isSearching) "No matching chats" else "No conversations yet",
                    subtitle = if (isSearching) {
                        "No conversations match \"$searchQuery\"."
                    } else {
                        "Start a new chat to build your offline history."
                    },
                    icon = if (isSearching) Icons.Rounded.SearchOff else Icons.Rounded.ChatBubbleOutline,
                    action = if (isSearching) {
                        {
                            SovereignSecondaryButton(
                                text = "Clear search",
                                onClick = { onSearchQueryChange("") },
                            )
                        }
                    } else {
                        null
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .verticalScroll(rememberScrollState()),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    groupedSessions.forEach { (sectionTitle, sessions) ->
                        item(
                            key = "header_$sectionTitle",
                            contentType = "section_header",
                        ) {
                            SovereignSectionHeader(
                                title = sectionTitle,
                                uppercase = true,
                                modifier = Modifier
                                    .animateItem(
                                        fadeInSpec = motion.effectsQuick(),
                                        placementSpec = motion.spatialFast(),
                                        fadeOutSpec = motion.effectsQuick(),
                                    )
                                    .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 2.dp),
                            )
                        }

                        items(
                            items = sessions,
                            key = { it.id },
                            contentType = { "conversation_row" },
                        ) { session ->
                            ConversationHistoryRow(
                                session = session,
                                isSelected = session.id == activeConversationId,
                                onSelect = {
                                    focusManager.clearFocus()
                                    onSelectConversation(session)
                                },
                                onTogglePin = { onTogglePin(session) },
                                onRename = { onRequestRename(session) },
                                onDelete = { onRequestDelete(session) },
                                modifier = Modifier.animateItem(
                                    fadeInSpec = motion.effectsQuick(),
                                    placementSpec = motion.spatialFast(),
                                    fadeOutSpec = motion.effectsQuick(),
                                ),
                            )
                        }
                    }
                }
            }
        }

        SovereignDivider()

        DrawerUtilityDock(
            activeModelTitle = activeModelTitle,
            isEngineReady = isEngineReady,
            memoryCount = memoryCount,
            isMemoryEnabled = isMemoryEnabled,
            availableRamMb = availableRamMb,
            totalRamMb = totalRamMb,
            batteryTemp = batteryTemp,
            batteryPercent = batteryPercent,
            onOpenModelManager = {
                focusManager.clearFocus()
                onOpenModelManager()
            },
            onOpenMemory = {
                focusManager.clearFocus()
                onOpenMemory()
            },
            onOpenSettings = {
                focusManager.clearFocus()
                onOpenSettings()
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DrawerBrandHeader() {
    val colors = SovereignTheme.colors
    val monochromeFilter = remember {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SovereignSurface(
            shape = CircleShape,
            color = colors.surfaceHigh,
            border = BorderStroke(SovereignElevation.hairlineWidth, colors.hairlineStrong),
            modifier = Modifier.size(38.dp),
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = monochromeFilter,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
            )
        }

        Spacer(Modifier.width(12.dp))

        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.app_display_name),
                style = SovereignTypography.cardTitle,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            SovereignBadge(
                text = "OFFLINE",
                tone = SovereignStatusTone.NEUTRAL,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationHistoryRow(
    session: ConversationSession,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SovereignTheme.colors
    val motion = SovereignTheme.motion
    val shapes = SovereignTheme.shapes
    val haptics = LocalHapticFeedback.current
    val hapticsEnabled = SovereignTheme.hapticsEnabled

    var menuExpanded by remember { mutableStateOf(false) }

    val rowShape = shapes.sm
    val rowBackground by animateColorAsState(
        targetValue = if (isSelected) colors.surfaceHigh else Color.Transparent,
        animationSpec = motion.effectsQuick(),
        label = "DrawerRowBg",
    )
    val rowBorder = if (isSelected) {
        BorderStroke(SovereignElevation.hairlineWidth, colors.hairlineStrong)
    } else {
        null
    }

    val displayTitle = session.title.ifBlank { "New chat" }
    val accentColor = colors.accentPrimary

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(rowShape)
            .combinedClickable(
                onClick = onSelect,
                onLongClick = {
                    if (hapticsEnabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    menuExpanded = true
                },
                onLongClickLabel = stringResource(R.string.cd_more_options),
            )
            .semantics {
                role = Role.Button
                if (isSelected) {
                    selected = true
                }
            },
        shape = rowShape,
        color = rowBackground,
        contentColor = colors.textPrimary,
        border = rowBorder,
        tonalElevation = SovereignElevation.none,
        shadowElevation = SovereignElevation.none,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .drawBehind {
                    if (isSelected) {
                        val indicatorWidth = 3.dp.toPx()
                        val left = if (layoutDirection == LayoutDirection.Ltr) {
                            0f
                        } else {
                            size.width - indicatorWidth
                        }
                        drawRect(
                            color = accentColor,
                            topLeft = Offset(left, 0f),
                            size = Size(indicatorWidth, size.height),
                        )
                    }
                }
                .padding(start = 12.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (session.isPinned) {
                Icon(
                    imageVector = Icons.Rounded.PushPin,
                    contentDescription = "Pinned",
                    tint = if (isSelected) colors.accentPrimary else colors.textSecondary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
            }

            Text(
                text = displayTitle,
                style = SovereignTypography.pillTitle.copy(
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp),
            )

            Box {
                SovereignIconButton(
                    onClick = { menuExpanded = true },
                    contentDescription = "${stringResource(R.string.cd_more_options)}: $displayTitle",
                    visualSize = 36.dp,
                    contentColor = if (isSelected) colors.textPrimary else colors.textMuted,
                    hapticFeedback = true,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    shape = shapes.md,
                    containerColor = colors.surfaceHigh,
                    tonalElevation = SovereignElevation.none,
                    shadowElevation = SovereignElevation.floatingOverlay,
                    border = BorderStroke(SovereignElevation.hairlineWidth, colors.hairlineStrong),
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (session.isPinned) "Unpin" else "Pin",
                                style = SovereignTypography.bodyCompact,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.PushPin,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        colors = MenuDefaults.itemColors(
                            textColor = colors.textPrimary,
                            leadingIconColor = colors.textSecondary,
                        ),
                        onClick = {
                            menuExpanded = false
                            onTogglePin()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Rename",
                                style = SovereignTypography.bodyCompact,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        colors = MenuDefaults.itemColors(
                            textColor = colors.textPrimary,
                            leadingIconColor = colors.textSecondary,
                        ),
                        onClick = {
                            menuExpanded = false
                            onRename()
                        },
                    )
                    SovereignDivider(modifier = Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Delete",
                                style = SovereignTypography.bodyCompact,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        colors = MenuDefaults.itemColors(
                            textColor = colors.error,
                            leadingIconColor = colors.error,
                        ),
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerUtilityDock(
    activeModelTitle: String,
    isEngineReady: Boolean,
    memoryCount: Int,
    isMemoryEnabled: Boolean,
    availableRamMb: Long,
    totalRamMb: Long,
    batteryTemp: Float,
    batteryPercent: Int,
    onOpenModelManager: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = SovereignTheme.colors
    val shapes = SovereignTheme.shapes

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        val modelTone = when {
            isEngineReady -> SovereignStatusTone.SUCCESS
            activeModelTitle.isNotBlank() -> SovereignStatusTone.WARNING
            else -> SovereignStatusTone.NEUTRAL
        }
        val modelSummary = activeModelTitle.ifBlank { "Select model" }

        DrawerDockRow(
            icon = Icons.Rounded.Layers,
            title = stringResource(R.string.label_models),
            onClick = onOpenModelManager,
            accessibilityHint = if (isEngineReady) "Ready: $modelSummary" else modelSummary,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SovereignStatusDot(
                    tone = modelTone,
                    size = 7.dp,
                )
                Text(
                    text = modelSummary,
                    style = SovereignTypography.monoTag,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 136.dp),
                )
            }
        }

        DrawerDockRow(
            icon = Icons.Rounded.Psychology,
            title = stringResource(R.string.label_memory),
            onClick = onOpenMemory,
            accessibilityHint = if (isMemoryEnabled) "$memoryCount stored" else "Off",
        ) {
            SovereignBadge(
                text = if (isMemoryEnabled) memoryCount.toString() else "Off",
                tone = when {
                    !isMemoryEnabled -> SovereignStatusTone.NEUTRAL
                    memoryCount > 0 -> SovereignStatusTone.ACCENT
                    else -> SovereignStatusTone.NEUTRAL
                },
                leadingDot = isMemoryEnabled,
            )
        }

        DrawerDockRow(
            icon = Icons.Rounded.Settings,
            title = stringResource(R.string.label_settings),
            onClick = onOpenSettings,
        ) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(18.dp),
            )
        }

        Spacer(Modifier.height(6.dp))

        val telemetryText = remember(availableRamMb, totalRamMb, batteryTemp, batteryPercent) {
            formatDrawerTelemetry(
                availableRamMb = availableRamMb,
                totalRamMb = totalRamMb,
                batteryTemp = batteryTemp,
                batteryPercent = batteryPercent,
            )
        }

        SovereignSurface(
            shape = shapes.sm,
            color = colors.surfaceBase,
            border = BorderStroke(SovereignElevation.hairlineWidth, colors.hairlineSubtle),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            Text(
                text = telemetryText,
                style = SovereignTypography.monoTag,
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun DrawerDockRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    accessibilityHint: String? = null,
    trailing: @Composable () -> Unit,
) {
    val colors = SovereignTheme.colors
    val shapes = SovereignTheme.shapes
    val rowDescription = if (accessibilityHint.isNullOrBlank()) title else "$title, $accessibilityHint"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(shapes.sm)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = rowDescription
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            style = SovereignTypography.pillTitle,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

internal fun groupConversationsByDate(
    conversations: List<ConversationSession>,
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): Map<String, List<ConversationSession>> {
    if (conversations.isEmpty()) return emptyMap()

    val todayDate = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    val todayStart = todayDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
    val yesterdayStart = todayDate.minusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    val sevenDaysAgoStart = todayDate.minusDays(7).atStartOfDay(zoneId).toInstant().toEpochMilli()

    val pinned = mutableListOf<ConversationSession>()
    val today = mutableListOf<ConversationSession>()
    val yesterday = mutableListOf<ConversationSession>()
    val previousSevenDays = mutableListOf<ConversationSession>()
    val older = mutableListOf<ConversationSession>()

    for (session in conversations) {
        when {
            session.isPinned -> pinned.add(session)
            session.updatedAt >= todayStart -> today.add(session)
            session.updatedAt >= yesterdayStart -> yesterday.add(session)
            session.updatedAt >= sevenDaysAgoStart -> previousSevenDays.add(session)
            else -> older.add(session)
        }
    }

    val grouped = LinkedHashMap<String, List<ConversationSession>>(5)
    if (pinned.isNotEmpty()) {
        grouped[DrawerSectionLabels.PINNED] = pinned.sortedByDescending { it.updatedAt }
    }
    if (today.isNotEmpty()) {
        grouped[DrawerSectionLabels.TODAY] = today.sortedByDescending { it.updatedAt }
    }
    if (yesterday.isNotEmpty()) {
        grouped[DrawerSectionLabels.YESTERDAY] = yesterday.sortedByDescending { it.updatedAt }
    }
    if (previousSevenDays.isNotEmpty()) {
        grouped[DrawerSectionLabels.PREVIOUS_7_DAYS] = previousSevenDays.sortedByDescending { it.updatedAt }
    }
    if (older.isNotEmpty()) {
        grouped[DrawerSectionLabels.OLDER] = older.sortedByDescending { it.updatedAt }
    }
    return grouped
}

internal fun formatDrawerTelemetry(
    availableRamMb: Long,
    totalRamMb: Long,
    batteryTemp: Float,
    batteryPercent: Int,
): String {
    val safeAvailMb = availableRamMb.coerceAtLeast(0L)
    val safeTotalMb = totalRamMb.coerceAtLeast(0L)
    val ramPart = when {
        safeTotalMb >= 1024L -> {
            val availGb = String.format(Locale.US, "%.1f", safeAvailMb / 1024.0)
            val totalGb = String.format(Locale.US, "%.1f", safeTotalMb / 1024.0)
            "RAM $availGb/$totalGb GB"
        }
        safeTotalMb > 0L -> "RAM $safeAvailMb/$safeTotalMb MB"
        safeAvailMb >= 1024L -> {
            val availGb = String.format(Locale.US, "%.1f", safeAvailMb / 1024.0)
            "RAM $availGb GB free"
        }
        else -> "RAM $safeAvailMb MB free"
    }
    val tempPart = String.format(Locale.US, "%.1f°C", batteryTemp)
    val batteryPart = "${batteryPercent.coerceIn(0, 100)}%"
    return "$ramPart · $tempPart · $batteryPart"
}
