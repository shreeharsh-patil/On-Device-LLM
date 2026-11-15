package com.example.on_device_qwen.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuOpen
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.on_device_qwen.R
import com.example.on_device_qwen.model.BackendType
import com.example.on_device_qwen.model.ChatMessage
import com.example.on_device_qwen.model.MemoryItem
import com.example.on_device_qwen.model.ModelErrorCode
import com.example.on_device_qwen.model.PromptProfile
import com.example.on_device_qwen.model.UnrestrictedMode
import com.example.on_device_qwen.ui.components.BenchmarkBadge
import com.example.on_device_qwen.ui.components.ChatComposer
import com.example.on_device_qwen.ui.components.ChatNavigationDrawerContent
import com.example.on_device_qwen.ui.components.MarkdownText
import com.example.on_device_qwen.ui.components.MemoryManagerDialog
import com.example.on_device_qwen.ui.components.ModelManagerDialog
import com.example.on_device_qwen.ui.components.SettingsDialog
import com.example.on_device_qwen.ui.components.ThinkingSection
import com.example.on_device_qwen.ui.designsystem.DarkSovereignColors
import com.example.on_device_qwen.ui.designsystem.SovereignAlertDialog
import com.example.on_device_qwen.ui.designsystem.SovereignBadge
import com.example.on_device_qwen.ui.designsystem.SovereignBanner
import com.example.on_device_qwen.ui.designsystem.SovereignDivider
import com.example.on_device_qwen.ui.designsystem.SovereignElevation
import com.example.on_device_qwen.ui.designsystem.SovereignErrorCard
import com.example.on_device_qwen.ui.designsystem.SovereignHairlineCard
import com.example.on_device_qwen.ui.designsystem.SovereignIconButton
import com.example.on_device_qwen.ui.designsystem.SovereignModalSheet
import com.example.on_device_qwen.ui.designsystem.SovereignPrimaryButton
import com.example.on_device_qwen.ui.designsystem.SovereignSecondaryButton
import com.example.on_device_qwen.ui.designsystem.SovereignStatusDot
import com.example.on_device_qwen.ui.designsystem.SovereignStatusTone
import com.example.on_device_qwen.ui.designsystem.SovereignSurface
import com.example.on_device_qwen.ui.designsystem.SovereignTextField
import com.example.on_device_qwen.ui.designsystem.SovereignTheme
import com.example.on_device_qwen.ui.designsystem.SovereignTypingIndicator
import com.example.on_device_qwen.viewmodel.ChatViewModel
import com.example.on_device_qwen.viewmodel.EngineState
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val colors = SovereignTheme.colors
    val adaptive = SovereignTheme.adaptive
    val motion = SovereignTheme.motion
    val spacing = SovereignTheme.spacing

    val engineState by viewModel.engineState.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val isVisionModel by viewModel.isVisionModel.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val modelTitle by viewModel.modelTitle.collectAsStateWithLifecycle()
    val discoveredModels by viewModel.discoveredModels.collectAsStateWithLifecycle()
    val currentBackend by viewModel.currentBackend.collectAsStateWithLifecycle()
    val performanceMode by viewModel.performanceMode.collectAsStateWithLifecycle()
    val chatMode by viewModel.chatMode.collectAsStateWithLifecycle()
    val validationBanner by viewModel.validationBanner.collectAsStateWithLifecycle()
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val activeConversation by viewModel.activeConversation.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    // Persistent Memory
    val persistentMemoryEnabled by viewModel.persistentMemoryEnabled.collectAsStateWithLifecycle()
    val allMemories by viewModel.allMemories.collectAsStateWithLifecycle()
    val lastUsedMemories by viewModel.lastUsedMemories.collectAsStateWithLifecycle()

    // Hardware Telemetry
    val thermalState by viewModel.thermalState.collectAsStateWithLifecycle()
    val batteryTemp by viewModel.batteryTemp.collectAsStateWithLifecycle()
    val batteryPercent by viewModel.batteryPercent.collectAsStateWithLifecycle()
    val isCharging by viewModel.isCharging.collectAsStateWithLifecycle()
    val availRamMb by viewModel.availRamMb.collectAsStateWithLifecycle()
    val totalRamMb by viewModel.totalRamMb.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()

    // Preferences
    val temperature by viewModel.temperature.collectAsStateWithLifecycle()
    val topP by viewModel.topP.collectAsStateWithLifecycle()
    val maxTokens by viewModel.maxTokens.collectAsStateWithLifecycle()
    val systemPrompt by viewModel.systemPrompt.collectAsStateWithLifecycle()
    val threadCount by viewModel.threadCount.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var isPersistentDrawerOpen by rememberSaveable { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var showModelManager by remember { mutableStateOf(false) }
    var showMemoryManager by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showUsedMemoriesSheet by remember { mutableStateOf(false) }
    var previewImagePath by remember { mutableStateOf<String?>(null) }

    // Message editing state
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var editText by remember { mutableStateOf("") }

    val context = LocalContext.current
    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.onAppForegrounded()
    }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            viewModel.onAppForegrounded()
        }
    }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importModel(uri)
        }
    }
    val onRequestPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                manageStorageLauncher.launch(intent)
            } catch (_: Exception) {
                val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                manageStorageLauncher.launch(fallback)
            }
        } else {
            storagePermissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    if (editingMessage != null) {
        SovereignAlertDialog(
            title = "Edit Message",
            confirmText = "Send",
            confirmEnabled = editText.isNotBlank(),
            onConfirm = {
                val msg = editingMessage ?: return@SovereignAlertDialog
                val trimmed = editText.trim()
                editingMessage = null
                if (trimmed.isNotEmpty()) {
                    viewModel.editAndResendMessage(msg.id, trimmed)
                }
            },
            onDismissRequest = { editingMessage = null },
        ) {
            SovereignTextField(
                value = editText,
                onValueChange = { editText = it },
                placeholder = "Edit your message…",
                minLines = 2,
                maxLines = 6,
                shape = SovereignTheme.shapes.sm,
            )
        }
    }

    if (previewImagePath != null) {
        ImageLightboxDialog(
            imagePath = previewImagePath!!,
            onDismiss = { previewImagePath = null },
        )
    }

    if (showUsedMemoriesSheet && lastUsedMemories.isNotEmpty()) {
        UsedMemoriesSheet(
            memories = lastUsedMemories,
            onManageMemories = {
                showUsedMemoriesSheet = false
                showMemoryManager = true
            },
            onDismiss = { showUsedMemoriesSheet = false },
        )
    }

    if (showModelManager) {
        val activeMetadata = discoveredModels.find { it.name == modelTitle }
        ModelManagerDialog(
            models = discoveredModels,
            activeModel = activeMetadata,
            availRamMb = availRamMb,
            importProgress = importProgress,
            onSelectModel = { model ->
                showModelManager = false
                viewModel.switchModel(model)
            },
            onImportUri = { uri ->
                viewModel.importModel(uri)
            },
            onDeleteModel = { model -> viewModel.deleteModel(model) },
            onDismiss = { showModelManager = false },
        )
    }

    if (showMemoryManager) {
        MemoryManagerDialog(
            memories = allMemories,
            isMemoryEnabled = persistentMemoryEnabled,
            onToggleMemory = { viewModel.togglePersistentMemory(it) },
            onSaveMemory = { viewModel.saveMemory(it) },
            onUpdateMemory = { id, content, cat -> viewModel.updateMemory(id, content, cat) },
            onDeleteMemory = { viewModel.deleteMemory(it) },
            onClearAllMemories = { viewModel.clearAllMemories() },
            onSearchMemories = { viewModel.searchMemories(it) },
            onDismiss = { showMemoryManager = false },
        )
    }

    if (showSettings) {
        SettingsDialog(
            performanceMode = performanceMode,
            onPerformanceModeChange = { viewModel.setPerformanceMode(it) },
            backendType = currentBackend,
            onBackendTypeChange = { viewModel.switchBackend(it) },
            threadCount = threadCount,
            onThreadCountChange = { viewModel.setThreadCount(it) },
            temperature = temperature,
            onTemperatureChange = { viewModel.setTemperature(it) },
            topP = topP,
            onTopPChange = { viewModel.setTopP(it) },
            maxTokens = maxTokens,
            onMaxTokensChange = { viewModel.setMaxTokens(it) },
            systemPrompt = systemPrompt,
            onSystemPromptChange = { viewModel.setSystemPrompt(it) },
            chatMode = chatMode,
            onChatModeChange = { viewModel.setChatMode(it) },
            activeProfile = activeProfile,
            onActiveProfileChange = { viewModel.setActivePromptProfile(it) },
            profiles = profiles,
            onProfileSaved = { profile -> viewModel.saveActiveProfile(profile) },
            onProfileDeleted = { viewModel.deletePromptProfile(it) },
            onProfileReset = { viewModel.resetPromptProfile(it) },
            themeMode = themeMode,
            onThemeModeChange = { viewModel.setThemeMode(it) },
            thermalState = thermalState,
            batteryTemp = batteryTemp,
            batteryPercent = batteryPercent,
            isCharging = isCharging,
            availRamMb = availRamMb,
            totalRamMb = totalRamMb,
            isMemoryEnabled = persistentMemoryEnabled,
            onToggleMemory = { viewModel.togglePersistentMemory(it) },
            onManageMemories = {
                showSettings = false
                showMemoryManager = true
            },
            onDismiss = { showSettings = false },
        )
    }

    val drawerContent: @Composable (closeAfterAction: Boolean) -> Unit = { closeAfterAction ->
        ChatNavigationDrawerContent(
            conversations = conversations,
            activeConversationId = activeConversation?.id,
            searchQuery = searchQuery,
            onSearchQueryChange = { viewModel.searchConversations(it) },
            onSelectConversation = { session ->
                viewModel.selectConversation(session)
                if (closeAfterAction) coroutineScope.launch { drawerState.close() }
            },
            onNewChat = {
                viewModel.newChat()
                if (closeAfterAction) coroutineScope.launch { drawerState.close() }
            },
            onRenameConversation = { session, newTitle ->
                viewModel.renameConversation(session, newTitle)
            },
            onDeleteConversation = { session ->
                viewModel.deleteConversation(session)
            },
            onTogglePin = { session ->
                viewModel.togglePin(session)
            },
            onOpenModelManager = {
                if (closeAfterAction) coroutineScope.launch { drawerState.close() }
                showModelManager = true
            },
            onOpenMemory = {
                if (closeAfterAction) coroutineScope.launch { drawerState.close() }
                showMemoryManager = true
            },
            onOpenSettings = {
                if (closeAfterAction) coroutineScope.launch { drawerState.close() }
                showSettings = true
            },
            batteryPercent = batteryPercent,
            batteryTemp = batteryTemp,
            availableRamMb = availRamMb,
        )
    }

    val chatPaneContent: @Composable () -> Unit = {
        val showTopBarDivider by remember(engineState, messages.isEmpty()) {
            derivedStateOf {
                engineState is EngineState.Ready && messages.isNotEmpty() && listState.canScrollBackward
            }
        }

        Scaffold(
            topBar = {
                ChatTopBar(
                    modelTitle = modelTitle,
                    engineState = engineState,
                    chatMode = chatMode,
                    activeProfile = activeProfile,
                    hasScrolledContent = { showTopBarDivider },
                    isPersistentDrawerMode = adaptive.usePersistentDrawer,
                    isPersistentDrawerOpen = isPersistentDrawerOpen,
                    onOpenDrawer = {
                        if (adaptive.usePersistentDrawer) {
                            isPersistentDrawerOpen = !isPersistentDrawerOpen
                        } else {
                            coroutineScope.launch { drawerState.open() }
                        }
                    },
                    onOpenModelSelector = { showModelManager = true },
                    onOpenSettings = { showSettings = true },
                    onNewChat = { viewModel.newChat() },
                )
            },
            containerColor = colors.bgCanvas,
            contentColor = colors.textPrimary,
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .consumeWindowInsets(paddingValues),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    SovereignBanner(
                        message = validationBanner,
                        tone = SovereignStatusTone.WARNING,
                        actionLabel = "Open Settings",
                        onAction = { showSettings = true },
                        onDismiss = { viewModel.dismissValidationBanner() },
                        modifier = Modifier
                            .widthIn(max = adaptive.maxContentWidth)
                            .padding(horizontal = spacing.screenGutter(adaptive), vertical = spacing.xs),
                    )
                }

                AnimatedContent(
                    targetState = engineState is EngineState.Ready,
                    transitionSpec = {
                        fadeIn(motion.effectsQuick()) togetherWith fadeOut(motion.effectsQuick())
                    },
                    label = "EngineReadyContentTransition",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) { isReady ->
                    if (!isReady) {
                        EngineStatusOverlay(
                            state = engineState,
                            onRetry = { viewModel.initializeEngine() },
                            onOpenModelManager = { showModelManager = true },
                            onRequestPermission = onRequestPermission,
                            onSelectCandidate = { candidate -> viewModel.switchModel(candidate) },
                            onOpenFilePicker = { filePickerLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        val density = LocalDensity.current
                        var composerHeightPx by remember { mutableIntStateOf(0) }
                        val composerBottomPadding = if (composerHeightPx > 0) {
                            with(density) { composerHeightPx.toDp() }
                        } else {
                            84.dp
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            AnimatedContent(
                                targetState = messages.isEmpty(),
                                transitionSpec = {
                                    fadeIn(motion.effectsQuick()) togetherWith fadeOut(motion.effectsQuick())
                                },
                                label = "EmptyVsMessagesTransition",
                                modifier = Modifier.fillMaxSize(),
                            ) { isEmpty ->
                                if (isEmpty) {
                                    EmptyChatWelcomeView(
                                        modelName = modelTitle,
                                        currentBackend = currentBackend,
                                        isVisionModel = isVisionModel,
                                        persistentMemoryEnabled = persistentMemoryEnabled,
                                        memoryCount = allMemories.size,
                                        onSelectPrompt = { viewModel.sendMessage(it) },
                                        onOpenMemoryManager = { showMemoryManager = true },
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(bottom = composerBottomPadding),
                                    )
                                } else {
                                    MessageListView(
                                        listState = listState,
                                        conversationId = activeConversation?.id,
                                        messages = messages,
                                        modelTitle = modelTitle,
                                        isGenerating = isGenerating,
                                        isSpeaking = isSpeaking,
                                        lastUsedMemories = lastUsedMemories,
                                        bottomContentPadding = composerBottomPadding,
                                        onSpeak = { viewModel.speak(it) },
                                        onStopSpeaking = { viewModel.stopSpeaking() },
                                        onEditMessage = { msg ->
                                            editingMessage = msg
                                            editText = msg.text
                                        },
                                        onRegenerate = { viewModel.regenerateLastResponse() },
                                        onSelectSuggestion = { suggestion -> viewModel.sendMessage(suggestion) },
                                        getFollowUpSuggestions = { text -> viewModel.getFollowUpSuggestions(text) },
                                        onOpenUsedMemories = { showUsedMemoriesSheet = true },
                                        onPreviewImage = { path -> previewImagePath = path },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }

                            ChatComposer(
                                isGenerating = isGenerating,
                                isVisionModel = isVisionModel,
                                modelTitle = modelTitle,
                                onOpenModelManager = { showModelManager = true },
                                onSendMessage = { text, uri -> viewModel.sendMessage(text, uri) },
                                onStopGeneration = { viewModel.stopGeneration() },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .onSizeChanged { composerHeightPx = it.height },
                            )
                        }
                    }
                }
            }
        }
    }

    if (adaptive.usePersistentDrawer) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.bgCanvas),
        ) {
            AnimatedVisibility(visible = isPersistentDrawerOpen) {
                Row(modifier = Modifier.fillMaxHeight()) {
                    Box(
                        modifier = Modifier
                            .width(296.dp)
                            .fillMaxHeight(),
                    ) {
                        drawerContent(false)
                    }
                    Box(
                        modifier = Modifier
                            .width(SovereignElevation.hairlineWidth)
                            .fillMaxHeight()
                            .background(colors.hairlineSubtle),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                chatPaneContent()
            }
        }
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            scrimColor = colors.scrim,
            drawerContent = {
                drawerContent(true)
            },
        ) {
            chatPaneContent()
        }
    }
}

// ─── Sovereign Top Bar ───────────────────────────────────────────────────────

@Composable
private fun ChatTopBar(
    modelTitle: String,
    engineState: EngineState,
    chatMode: UnrestrictedMode,
    activeProfile: PromptProfile,
    hasScrolledContent: () -> Boolean,
    isPersistentDrawerMode: Boolean,
    isPersistentDrawerOpen: Boolean,
    onOpenDrawer: () -> Unit,
    onOpenModelSelector: () -> Unit,
    onOpenSettings: () -> Unit,
    onNewChat: () -> Unit,
) {
    val colors = SovereignTheme.colors
    val shapes = SovereignTheme.shapes
    val motion = SovereignTheme.motion
    val typography = SovereignTheme.typography
    val adaptive = SovereignTheme.adaptive

    val dividerAlpha by animateFloatAsState(
        targetValue = if (hasScrolledContent()) 1f else 0f,
        animationSpec = motion.effectsQuick(),
        label = "TopBarHairlineAlpha",
    )

    val (dotTone, dotPulsing, statusLabel) = when (engineState) {
        is EngineState.Ready -> Triple(SovereignStatusTone.SUCCESS, false, "Ready")
        is EngineState.Loading -> Triple(SovereignStatusTone.WARNING, true, "Loading")
        is EngineState.Error -> Triple(SovereignStatusTone.ERROR, false, "Error")
        is EngineState.Idle -> Triple(SovereignStatusTone.NEUTRAL, false, "Idle")
    }

    val isUnrestricted = chatMode == UnrestrictedMode.UNRESTRICTED
    val isCustomProfile = activeProfile.id != PromptProfile.DEFAULT_ID &&
        activeProfile.id != PromptProfile.UNRESTRICTED_ID

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgCanvas)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left Zone: Drawer toggle
            SovereignIconButton(
                onClick = onOpenDrawer,
                contentDescription = stringResource(R.string.cd_open_drawer),
                contentColor = colors.textPrimary,
            ) {
                Icon(
                    imageVector = if (isPersistentDrawerMode && isPersistentDrawerOpen) {
                        Icons.AutoMirrored.Rounded.MenuOpen
                    } else {
                        Icons.Rounded.Menu
                    },
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
            }

            // Center Zone: Model Switcher Pill
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    onClick = onOpenModelSelector,
                    shape = shapes.pill,
                    color = colors.surfaceLow,
                    contentColor = colors.textPrimary,
                    border = BorderStroke(SovereignElevation.hairlineWidth, colors.hairlineSubtle),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier.semantics {
                        role = Role.Button
                        contentDescription = "Model $modelTitle, status $statusLabel. Switch model"
                    },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        SovereignStatusDot(
                            tone = dotTone,
                            size = 8.dp,
                            pulsing = dotPulsing,
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = modelTitle,
                            style = typography.pillTitle,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            // Right Zone: Compact Mode/Profile badge + New Chat
            Row(verticalAlignment = Alignment.CenterVertically) {
                ModeAndProfileBadgeButton(
                    isUnrestricted = isUnrestricted,
                    profileName = activeProfile.name,
                    hasCustomProfile = isCustomProfile,
                    showLabel = !adaptive.isCompact,
                    onClick = onOpenSettings,
                )

                SovereignIconButton(
                    onClick = onNewChat,
                    contentDescription = stringResource(R.string.cd_new_chat),
                    contentColor = colors.textPrimary,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        SovereignDivider(modifier = Modifier.alpha(dividerAlpha))
    }
}

@Composable
private fun ModeAndProfileBadgeButton(
    isUnrestricted: Boolean,
    profileName: String,
    hasCustomProfile: Boolean,
    showLabel: Boolean,
    onClick: () -> Unit,
) {
    val colors = SovereignTheme.colors
    val shapes = SovereignTheme.shapes
    val typography = SovereignTheme.typography

    val containerColor = if (isUnrestricted) colors.amberContainer else colors.surfaceLow
    val contentColor = if (isUnrestricted) colors.onAmberContainer else colors.textSecondary
    val iconColor = if (isUnrestricted) colors.amber else colors.textSecondary
    val borderColor = if (isUnrestricted) {
        colors.amber.copy(alpha = 0.38f)
    } else {
        colors.hairlineSubtle
    }

    val modeLabel = if (isUnrestricted) {
        stringResource(R.string.label_unrestricted_mode)
    } else {
        stringResource(R.string.label_standard_mode)
    }

    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "Mode $modeLabel, profile $profileName. Open settings"
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            onClick = onClick,
            shape = shapes.pill,
            color = containerColor,
            contentColor = contentColor,
            border = BorderStroke(SovereignElevation.hairlineWidth, borderColor),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.defaultMinSize(minWidth = 36.dp, minHeight = 36.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(
                    horizontal = if (showLabel) 10.dp else 9.dp,
                    vertical = 7.dp,
                ),
            ) {
                Icon(
                    imageVector = if (isUnrestricted) Icons.Rounded.Bolt else Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(16.dp),
                )
                if (hasCustomProfile && !showLabel) {
                    Spacer(Modifier.width(3.dp))
                    SovereignStatusDot(
                        tone = SovereignStatusTone.ACCENT,
                        size = 5.dp,
                    )
                }
                if (showLabel && profileName.isNotBlank()) {
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = profileName,
                        style = typography.monoTag,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 96.dp),
                    )
                }
            }
        }
    }
}

// ─── Empty Welcome View ──────────────────────────────────────────────────────

private data class StarterPromptItem(
    val icon: ImageVector,
    val title: String,
    val prompt: String,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyChatWelcomeView(
    modelName: String,
    currentBackend: BackendType,
    isVisionModel: Boolean,
    persistentMemoryEnabled: Boolean,
    memoryCount: Int,
    onSelectPrompt: (String) -> Unit,
    onOpenMemoryManager: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SovereignTheme.colors
    val shapes = SovereignTheme.shapes
    val spacing = SovereignTheme.spacing
    val typography = SovereignTheme.typography
    val adaptive = SovereignTheme.adaptive

    val promptSuggestions = remember(isVisionModel) {
        if (isVisionModel) {
            listOf(
                StarterPromptItem(
                    icon = Icons.Rounded.ImageSearch,
                    title = "Analyze an image",
                    prompt = "Describe an uploaded photo in detail",
                ),
                StarterPromptItem(
                    icon = Icons.Rounded.DocumentScanner,
                    title = "Read visual text",
                    prompt = "Extract and summarize all text from a photo",
                ),
                StarterPromptItem(
                    icon = Icons.Rounded.Psychology,
                    title = "Quantum intuition",
                    prompt = "Explain quantum computing simply",
                ),
                StarterPromptItem(
                    icon = Icons.Rounded.Code,
                    title = "Python matrix ops",
                    prompt = "Write a Python script for matrix multiplication",
                ),
            )
        } else {
            listOf(
                StarterPromptItem(
                    icon = Icons.Rounded.Memory,
                    title = "CPU vs NPU",
                    prompt = "Explain the difference between CPU and NPU inference",
                ),
                StarterPromptItem(
                    icon = Icons.Rounded.Science,
                    title = "Thermodynamics",
                    prompt = "Summarize the key principles of thermodynamics",
                ),
                StarterPromptItem(
                    icon = Icons.Rounded.Code,
                    title = "Developer habits",
                    prompt = "Write 3 productive habits for software developers",
                ),
                StarterPromptItem(
                    icon = Icons.Rounded.Terminal,
                    title = "Kotlin concurrency",
                    prompt = "Explain structured concurrency in Kotlin with an example",
                ),
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val viewportHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .defaultMinSize(minHeight = viewportHeight)
                .padding(
                    horizontal = spacing.screenGutter(adaptive),
                    vertical = spacing.lg,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .widthIn(max = adaptive.maxContentWidth)
                    .fillMaxWidth(),
            ) {
            Surface(
                shape = CircleShape,
                color = colors.surfaceLow,
                border = BorderStroke(SovereignElevation.hairlineWidth, colors.hairlineStrong),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier.size(56.dp),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "On-Device AI emblem",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "What's on your mind?",
                style = typography.editorialGreeting,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(10.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SovereignBadge(
                    text = "$modelName · ${currentBackend.name} · 100% On-Device",
                    tone = SovereignStatusTone.NEUTRAL,
                    leadingDot = true,
                    shape = shapes.pill,
                )
                if (isVisionModel) {
                    SovereignBadge(
                        text = "Vision",
                        tone = SovereignStatusTone.ACCENT,
                        leadingIcon = Icons.Rounded.Image,
                        shape = shapes.pill,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val useTwoColumns = maxWidth >= 480.dp
                if (useTwoColumns) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        promptSuggestions.chunked(2).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                rowItems.forEach { item ->
                                    StarterPromptCard(
                                        item = item,
                                        onClick = { onSelectPrompt(item.prompt) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (rowItems.size == 1) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        promptSuggestions.forEach { item ->
                            StarterPromptCard(
                                item = item,
                                onClick = { onSelectPrompt(item.prompt) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            if (persistentMemoryEnabled && memoryCount > 0) {
                Spacer(Modifier.height(18.dp))
                val label = if (memoryCount == 1) "1 memory active" else "$memoryCount memories active"
                SovereignSurface(
                    shape = shapes.pill,
                    color = colors.accentContainer,
                    contentColor = colors.onAccentContainer,
                    border = BorderStroke(
                        SovereignElevation.hairlineWidth,
                        colors.accentPrimary.copy(alpha = 0.28f),
                    ),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(shapes.pill)
                        .clickable(onClick = onOpenMemoryManager)
                        .semantics(mergeDescendants = true) {
                            role = Role.Button
                            contentDescription = "$label. Tap to manage memories"
                        },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Psychology,
                            contentDescription = null,
                            tint = colors.onAccentContainer,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = label,
                            style = typography.actionLabel,
                            color = colors.onAccentContainer,
                        )
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun StarterPromptCard(
    item: StarterPromptItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SovereignTheme.colors
    val typography = SovereignTheme.typography

    SovereignHairlineCard(
        onClick = onClick,
        containerColor = colors.surfaceLow,
        borderColor = colors.hairlineSubtle,
        contentPadding = PaddingValues(14.dp),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = colors.accentPrimary,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = typography.pillTitle,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.prompt,
                    style = typography.bodyCompact,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ─── Messages List View ──────────────────────────────────────────────────────

@Composable
private fun MessageListView(
    listState: LazyListState,
    conversationId: String?,
    messages: List<ChatMessage>,
    modelTitle: String,
    isGenerating: Boolean,
    isSpeaking: Boolean,
    lastUsedMemories: List<MemoryItem>,
    bottomContentPadding: Dp,
    onSpeak: (String) -> Unit,
    onStopSpeaking: () -> Unit,
    onEditMessage: (ChatMessage) -> Unit,
    onRegenerate: () -> Unit,
    onSelectSuggestion: (String) -> Unit,
    getFollowUpSuggestions: (String?) -> List<String>,
    onOpenUsedMemories: () -> Unit,
    onPreviewImage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val adaptive = SovereignTheme.adaptive
    val spacing = SovereignTheme.spacing
    val reducedMotion = SovereignTheme.reducedMotion

    val scope = rememberCoroutineScope()
    val isUserDragging by listState.interactionSource.collectIsDraggedAsState()
    var userScrolledAway by remember { mutableStateOf(false) }
    var speakingMessageId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(isSpeaking) {
        if (!isSpeaking) {
            speakingMessageId = null
        }
    }

    // Observe scroll & drag state via snapshotFlow so scrolling never recomposes MessageListView
    LaunchedEffect(listState) {
        snapshotFlow { isUserDragging to listState.canScrollForward }
            .distinctUntilChanged()
            .collect { (dragging, canForward) ->
                if (dragging && canForward) {
                    userScrolledAway = true
                } else if (!canForward) {
                    userScrolledAway = false
                }
            }
    }

    LaunchedEffect(conversationId) {
        userScrolledAway = false
    }

    // Snap to bottom when new message arrives or conversation switches
    LaunchedEffect(conversationId, messages.size) {
        if (messages.isNotEmpty()) {
            userScrolledAway = false
            listState.scrollToItem(messages.lastIndex)
            scrollLastItemBottomIntoView(listState, messages.lastIndex)
        }
    }

    // Keep streaming response tail visible when following
    val lastMsg = messages.lastOrNull()
    LaunchedEffect(lastMsg?.text?.length, lastMsg?.thinkText?.length) {
        if (!userScrolledAway && !isUserDragging && messages.isNotEmpty() && isGenerating) {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            if (lastVisible >= messages.lastIndex - 1) {
                scrollLastItemBottomIntoView(listState, messages.lastIndex)
            }
        }
    }

    val showJumpToLatest by remember(messages.isNotEmpty()) {
        derivedStateOf { userScrolledAway && messages.isNotEmpty() }
    }

    val lastMessageId = messages.lastOrNull()?.id

    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = spacing.screenGutter(adaptive),
                end = spacing.screenGutter(adaptive),
                top = 12.dp,
                bottom = bottomContentPadding + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(messages, key = { it.id }) { message ->
                val isLastMessage = message.id == lastMessageId
                val suggestions = if (
                    isLastMessage && !message.isUser && !message.isStreaming && !message.isError && !isGenerating
                ) {
                    remember(message.text) { getFollowUpSuggestions(message.text) }
                } else {
                    emptyList()
                }
                val itemModifier = if (reducedMotion) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .animateItem()
                }

                Box(
                    modifier = itemModifier,
                    contentAlignment = Alignment.Center,
                ) {
                    MessageBubbleItem(
                        message = message,
                        fallbackModelTitle = modelTitle,
                        isLastMessage = isLastMessage,
                        isGenerating = isGenerating,
                        isThisMessageSpeaking = isSpeaking &&
                            (speakingMessageId == null || speakingMessageId == message.id),
                        usedMemoriesCount = if (isLastMessage && !message.isUser) lastUsedMemories.size else 0,
                        suggestions = suggestions,
                        onSpeak = {
                            speakingMessageId = message.id
                            onSpeak(message.text)
                        },
                        onStopSpeaking = {
                            speakingMessageId = null
                            onStopSpeaking()
                        },
                        onEditMessage = { onEditMessage(message) },
                        onRegenerate = onRegenerate,
                        onSelectSuggestion = onSelectSuggestion,
                        onOpenUsedMemories = onOpenUsedMemories,
                        onPreviewImage = onPreviewImage,
                        modifier = Modifier
                            .widthIn(max = adaptive.maxContentWidth)
                            .fillMaxWidth(),
                    )
                }
            }
        }

        JumpToLatestOverlay(
            visible = showJumpToLatest,
            isGenerating = isGenerating,
            onClick = {
                userScrolledAway = false
                scope.launch {
                    if (messages.isNotEmpty()) {
                        listState.animateScrollToItem(messages.lastIndex)
                        scrollLastItemBottomIntoView(listState, messages.lastIndex)
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomContentPadding + 10.dp),
        )
    }
}

private suspend fun scrollLastItemBottomIntoView(listState: LazyListState, lastIndex: Int) {
    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()
    if (lastVisible != null && lastVisible.index == lastIndex) {
        val viewportBottom = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.afterContentPadding
        val itemBottom = lastVisible.offset + lastVisible.size
        val delta = (itemBottom - viewportBottom).toFloat()
        if (delta > 0f) {
            listState.scrollBy(delta)
        }
    } else {
        listState.scrollToItem(lastIndex)
    }
}

@Composable
private fun JumpToLatestOverlay(
    visible: Boolean,
    isGenerating: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SovereignTheme.colors
    val shapes = SovereignTheme.shapes
    val motion = SovereignTheme.motion
    val typography = SovereignTheme.typography

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(motion.effectsQuick()) + slideInVertically(
            animationSpec = motion.spatialExpressive(),
            initialOffsetY = { it / 2 },
        ),
        exit = fadeOut(motion.effectsQuick()) + slideOutVertically(
            animationSpec = motion.spatialFast(),
            targetOffsetY = { it / 2 },
        ),
        modifier = modifier,
    ) {
        Surface(
            onClick = onClick,
            shape = shapes.pill,
            color = colors.surfaceHigh,
            contentColor = colors.textPrimary,
            border = BorderStroke(SovereignElevation.hairlineWidth, colors.hairlineStrong),
            tonalElevation = 0.dp,
            shadowElevation = SovereignElevation.floatingOverlay,
            modifier = Modifier
                .defaultMinSize(minHeight = 40.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = if (isGenerating) {
                        "Generating response. Jump to latest message"
                    } else {
                        "Jump to latest message"
                    }
                },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                if (isGenerating) {
                    SovereignStatusDot(
                        tone = SovereignStatusTone.ACCENT,
                        size = 7.dp,
                        pulsing = true,
                    )
                    Spacer(Modifier.width(7.dp))
                }
                Text(
                    text = if (isGenerating) "Generating…" else "Jump to latest",
                    style = typography.actionLabel,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun MessageBubbleItem(
    message: ChatMessage,
    fallbackModelTitle: String,
    isLastMessage: Boolean,
    isGenerating: Boolean,
    isThisMessageSpeaking: Boolean,
    usedMemoriesCount: Int,
    suggestions: List<String>,
    onSpeak: () -> Unit,
    onStopSpeaking: () -> Unit,
    onEditMessage: () -> Unit,
    onRegenerate: () -> Unit,
    onSelectSuggestion: (String) -> Unit,
    onOpenUsedMemories: () -> Unit,
    onPreviewImage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SovereignTheme.colors
    val shapes = SovereignTheme.shapes
    val typography = SovereignTheme.typography
    val adaptive = SovereignTheme.adaptive
    val hapticsEnabled = SovereignTheme.hapticsEnabled
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    if (message.isUser) {
        val maxBubbleWidth = (adaptive.windowWidthDp * 0.78f).coerceAtMost(460.dp)
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.End,
            modifier = modifier,
        ) {
            SovereignIconButton(
                onClick = onEditMessage,
                contentDescription = stringResource(R.string.cd_edit_message),
                visualSize = 34.dp,
                contentColor = colors.textMuted,
                hapticFeedback = true,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                )
            }

            Spacer(Modifier.width(2.dp))

            SovereignSurface(
                shape = shapes.userBubble,
                color = colors.userBubble,
                contentColor = colors.textPrimary,
                border = BorderStroke(SovereignElevation.hairlineWidth, colors.hairlineSubtle),
                modifier = Modifier.widthIn(max = maxBubbleWidth),
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    message.imagePath?.let { path ->
                        val imageFile = remember(path) { File(path) }
                        AsyncImage(
                            model = imageFile,
                            contentDescription = "Attached photo. Tap to view full screen",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .clip(shapes.sm)
                                .background(colors.surfaceBase)
                                .clickable(
                                    role = Role.Image,
                                    onClickLabel = "View full screen image",
                                    onClick = { onPreviewImage(path) },
                                ),
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        text = message.text,
                        style = typography.chatProse,
                        color = colors.textPrimary,
                    )
                }
            }
        }
    } else {
        Column(modifier = modifier) {
            val turnModelLabel = message.modelName?.takeIf { it.isNotBlank() } ?: fallbackModelTitle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.padding(bottom = 6.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = colors.surfaceLow,
                    border = BorderStroke(SovereignElevation.hairlineWidth, colors.hairlineSubtle),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier.size(18.dp),
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_logo),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                    )
                }
                Text(
                    text = turnModelLabel,
                    style = typography.monoTag,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (message.isCancelled) {
                    SovereignBadge(
                        text = "Stopped",
                        tone = SovereignStatusTone.NEUTRAL,
                    )
                }
            }

            if (!message.thinkText.isNullOrBlank() || (message.isStreaming && message.thinkText != null && message.text.isBlank())) {
                ThinkingSection(
                    thinkText = message.thinkText.orEmpty(),
                    isThinking = message.isStreaming && message.text.isBlank(),
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }

            if (message.text.isNotEmpty()) {
                if (message.isError) {
                    SovereignSurface(
                        shape = shapes.md,
                        color = colors.errorContainer,
                        contentColor = colors.onErrorContainer,
                        border = BorderStroke(
                            SovereignElevation.hairlineWidth,
                            colors.error.copy(alpha = 0.35f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        MarkdownText(
                            text = message.text,
                            color = colors.onErrorContainer,
                            isStreaming = message.isStreaming,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        )
                    }
                } else {
                    MarkdownText(
                        text = message.text,
                        color = colors.textPrimary,
                        isStreaming = message.isStreaming,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else if (message.isStreaming && message.thinkText == null) {
                SovereignTypingIndicator(
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }

            if (message.benchmark != null && (message.text.isNotEmpty() || message.isStreaming)) {
                Spacer(Modifier.height(10.dp))
                BenchmarkBadge(
                    benchmark = message.benchmark,
                    isStreaming = message.isStreaming,
                )
            }

            if (!message.isStreaming && message.text.isNotEmpty()) {
                var copiedJustNow by remember(message.id) { mutableStateOf(false) }
                LaunchedEffect(copiedJustNow) {
                    if (copiedJustNow) {
                        delay(1600L)
                        copiedJustNow = false
                    }
                }

                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    SovereignIconButton(
                        onClick = {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Response", message.text)
                            clipboard.setPrimaryClip(clip)
                            copiedJustNow = true
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        },
                        contentDescription = if (copiedJustNow) {
                            "Copied"
                        } else {
                            stringResource(R.string.cd_copy_message)
                        },
                        visualSize = 36.dp,
                        contentColor = if (copiedJustNow) colors.success else colors.textSecondary,
                        containerColor = if (copiedJustNow) colors.successContainer else Color.Transparent,
                        hapticFeedback = true,
                    ) {
                        Icon(
                            imageVector = if (copiedJustNow) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }

                    SovereignIconButton(
                        onClick = {
                            if (isThisMessageSpeaking) onStopSpeaking() else onSpeak()
                        },
                        contentDescription = if (isThisMessageSpeaking) {
                            stringResource(R.string.cd_stop_speaking)
                        } else {
                            stringResource(R.string.cd_read_aloud)
                        },
                        selected = isThisMessageSpeaking,
                        visualSize = 36.dp,
                        contentColor = if (isThisMessageSpeaking) colors.accentPrimary else colors.textSecondary,
                        containerColor = if (isThisMessageSpeaking) colors.accentContainer else Color.Transparent,
                        hapticFeedback = true,
                    ) {
                        Icon(
                            imageVector = if (isThisMessageSpeaking) {
                                Icons.Rounded.Stop
                            } else {
                                Icons.AutoMirrored.Rounded.VolumeUp
                            },
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                        )
                    }

                    SovereignIconButton(
                        onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, message.text)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share response"))
                        },
                        contentDescription = stringResource(R.string.cd_share_message),
                        visualSize = 36.dp,
                        contentColor = colors.textSecondary,
                        hapticFeedback = true,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }

                    if (isLastMessage) {
                        SovereignIconButton(
                            onClick = onRegenerate,
                            enabled = !isGenerating,
                            contentDescription = stringResource(R.string.cd_regenerate),
                            visualSize = 36.dp,
                            contentColor = colors.textSecondary,
                            hapticFeedback = true,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }

                    if (usedMemoriesCount > 0) {
                        Spacer(Modifier.width(4.dp))
                        val memLabel = if (usedMemoriesCount == 1) {
                            "1 memory used"
                        } else {
                            "$usedMemoriesCount memories used"
                        }
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .semantics(mergeDescendants = true) {
                                    role = Role.Button
                                    contentDescription = "$memLabel. Tap to inspect"
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                onClick = {
                                    if (hapticsEnabled) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    onOpenUsedMemories()
                                },
                                shape = shapes.pill,
                                color = colors.accentContainer,
                                contentColor = colors.onAccentContainer,
                                border = BorderStroke(
                                    SovereignElevation.hairlineWidth,
                                    colors.accentPrimary.copy(alpha = 0.28f),
                                ),
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Psychology,
                                        contentDescription = null,
                                        tint = colors.onAccentContainer,
                                        modifier = Modifier.size(13.dp),
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        text = memLabel,
                                        style = typography.monoTag,
                                        color = colors.onAccentContainer,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    suggestions.forEach { suggestion ->
                        Surface(
                            onClick = {
                                if (hapticsEnabled) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                onSelectSuggestion(suggestion)
                            },
                            shape = shapes.pill,
                            color = colors.surfaceLow,
                            contentColor = colors.textPrimary,
                            border = BorderStroke(SovereignElevation.hairlineWidth, colors.hairlineSubtle),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 40.dp)
                                .semantics { role = Role.Button },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = colors.accentPrimary,
                                    modifier = Modifier.size(13.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = suggestion,
                                    style = typography.actionLabel,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Engine Status Overlay ───────────────────────────────────────────────────

@Composable
private fun EngineStatusOverlay(
    state: EngineState,
    onRetry: () -> Unit,
    onOpenModelManager: () -> Unit,
    onRequestPermission: () -> Unit,
    onSelectCandidate: (File) -> Unit,
    onOpenFilePicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SovereignTheme.colors
    val typography = SovereignTheme.typography
    val spacing = SovereignTheme.spacing
    val adaptive = SovereignTheme.adaptive

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val viewportHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .defaultMinSize(minHeight = viewportHeight)
                .padding(horizontal = spacing.screenGutter(adaptive), vertical = spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Crossfade(
                targetState = state,
                label = "EngineOverlayStateCrossfade",
            ) { currentState ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                when (currentState) {
                    is EngineState.Idle -> {
                        SovereignHairlineCard(
                            modifier = Modifier
                                .widthIn(max = 440.dp)
                                .fillMaxWidth(),
                            containerColor = colors.surfaceLow,
                            contentPadding = PaddingValues(24.dp),
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = colors.surfaceBase,
                                    border = BorderStroke(
                                        SovereignElevation.hairlineWidth,
                                        colors.hairlineStrong,
                                    ),
                                    tonalElevation = 0.dp,
                                    shadowElevation = 0.dp,
                                    modifier = Modifier.size(56.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.Memory,
                                            contentDescription = null,
                                            tint = colors.accentPrimary,
                                            modifier = Modifier.size(26.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = "On-Device AI Assistant",
                                    style = typography.sheetHeader,
                                    color = colors.textPrimary,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "Offline inference with LiteRT-LM & llama.cpp",
                                    style = typography.bodyCompact,
                                    color = colors.textSecondary,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(22.dp))
                                SovereignPrimaryButton(
                                    text = "Initialize Engine",
                                    leadingIcon = Icons.Rounded.PlayArrow,
                                    onClick = onRetry,
                                    hapticFeedback = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    is EngineState.Loading -> {
                        SovereignHairlineCard(
                            modifier = Modifier
                                .widthIn(max = 440.dp)
                                .fillMaxWidth()
                                .semantics { liveRegion = LiveRegionMode.Polite },
                            containerColor = colors.surfaceLow,
                            contentPadding = PaddingValues(24.dp),
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(36.dp),
                                    strokeWidth = 3.dp,
                                    color = colors.accentPrimary,
                                    trackColor = colors.surfaceHighest,
                                )
                                Spacer(Modifier.height(16.dp))
                                SovereignBadge(
                                    text = "Loading model runtime",
                                    tone = SovereignStatusTone.ACCENT,
                                    leadingDot = true,
                                    pulsingDot = true,
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = currentState.progress,
                                    style = typography.bodyCompact,
                                    color = colors.textSecondary,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }

                    is EngineState.Error -> {
                        val iconVector = when (currentState.errorCode) {
                            ModelErrorCode.FILE_UNREADABLE_OR_PERMISSION_DENIED -> Icons.Rounded.Lock
                            ModelErrorCode.DOWNLOADED_MODEL_NOT_REGISTERED -> Icons.Rounded.CheckCircleOutline
                            ModelErrorCode.INSUFFICIENT_RAM -> Icons.Rounded.Memory
                            else -> Icons.Rounded.WarningAmber
                        }
                        val tone = when (currentState.errorCode) {
                            ModelErrorCode.DOWNLOADED_MODEL_NOT_REGISTERED -> SovereignStatusTone.ACCENT
                            else -> SovereignStatusTone.ERROR
                        }

                        SovereignErrorCard(
                            title = currentState.title,
                            message = currentState.message,
                            icon = iconVector,
                            tone = tone,
                        ) {
                            if (currentState.candidateFile != null) {
                                SovereignHairlineCard(
                                    containerColor = colors.accentContainer,
                                    borderColor = colors.accentPrimary.copy(alpha = 0.35f),
                                    contentPadding = PaddingValues(12.dp),
                                ) {
                                    Text(
                                        text = "Discovered: ${currentState.candidateFile.name}",
                                        style = typography.monoTag,
                                        color = colors.onAccentContainer,
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    SovereignPrimaryButton(
                                        text = "Load Discovered Model",
                                        useAccent = true,
                                        leadingIcon = Icons.Rounded.PlayArrow,
                                        onClick = { onSelectCandidate(currentState.candidateFile) },
                                        hapticFeedback = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }

                            if (currentState.canRequestPermission) {
                                SovereignPrimaryButton(
                                    text = "Grant Storage Permission",
                                    leadingIcon = Icons.Rounded.LockOpen,
                                    onClick = onRequestPermission,
                                    hapticFeedback = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            if (currentState.canOpenFilePicker && currentState.candidateFile == null) {
                                SovereignSecondaryButton(
                                    text = "Locate Model File (SAF)",
                                    leadingIcon = Icons.Rounded.FolderOpen,
                                    onClick = onOpenFilePicker,
                                    hapticFeedback = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SovereignSecondaryButton(
                                    text = "Scan Again",
                                    leadingIcon = Icons.Rounded.Refresh,
                                    onClick = onRetry,
                                    modifier = Modifier.weight(1f),
                                )
                                SovereignPrimaryButton(
                                    text = "Catalog",
                                    leadingIcon = Icons.Rounded.Layers,
                                    onClick = onOpenModelManager,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    is EngineState.Ready -> Unit
                }
                }
            }
        }
    }
}

// ─── Used Memories Inspection Sheet ──────────────────────────────────────────

@Composable
private fun UsedMemoriesSheet(
    memories: List<MemoryItem>,
    onManageMemories: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = SovereignTheme.colors
    val typography = SovereignTheme.typography

    SovereignModalSheet(
        title = "Memories Used in Context",
        subtitle = "Injected locally from SQLite for this response",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(memories, key = { it.id }) { item ->
                    SovereignHairlineCard(
                        containerColor = colors.surfaceLow,
                        contentPadding = PaddingValues(14.dp),
                    ) {
                        SovereignBadge(
                            text = item.category.displayName,
                            tone = SovereignStatusTone.ACCENT,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = item.content,
                            style = typography.bodyCompact,
                            color = colors.textPrimary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            SovereignPrimaryButton(
                text = "Manage Memories",
                leadingIcon = Icons.Rounded.Psychology,
                onClick = onManageMemories,
                hapticFeedback = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ─── Full-Screen Dismissible Image Lightbox ──────────────────────────────────

@Composable
private fun ImageLightboxDialog(
    imagePath: String,
    onDismiss: () -> Unit,
) {
    val obsidian = DarkSovereignColors
    val imageFile = remember(imagePath) { File(imagePath) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { _, zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 4f)
        scale = nextScale
        offset = if (nextScale > 1f) {
            offset + panChange
        } else {
            Offset.Zero
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val view = LocalView.current
        val dialogWindow = (view.parent as? DialogWindowProvider)?.window
        if (dialogWindow != null) {
            SideEffect {
                WindowCompat.setDecorFitsSystemWindows(dialogWindow, false)
                val controller = WindowCompat.getInsetsController(dialogWindow, view)
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
            }
        }
        BackHandler(onBack = onDismiss)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(obsidian.bgCanvas)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            AsyncImage(
                model = imageFile,
                contentDescription = "Full-screen attached image preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1.05f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.25f
                                }
                            },
                            onTap = { onDismiss() },
                        )
                    }
                    .transformable(state = transformState)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .safeDrawingPadding()
                    .padding(12.dp),
            ) {
                SovereignIconButton(
                    onClick = onDismiss,
                    contentDescription = stringResource(R.string.cd_close),
                    containerColor = obsidian.surfaceHigh.copy(alpha = 0.9f),
                    contentColor = obsidian.textPrimary,
                    border = BorderStroke(SovereignElevation.hairlineWidth, obsidian.hairlineStrong),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
