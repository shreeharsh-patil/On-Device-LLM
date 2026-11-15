package com.example.on_device_qwen.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.on_device_qwen.R
import com.example.on_device_qwen.model.ChatMessage
import com.example.on_device_qwen.model.ExecutionBackendInfo
import com.example.on_device_qwen.model.UnrestrictedMode
import com.example.on_device_qwen.ui.components.ChatComposer
import com.example.on_device_qwen.ui.components.ChatNavigationDrawerContent
import com.example.on_device_qwen.ui.components.MemoryManagerDialog
import com.example.on_device_qwen.ui.components.ModelManagerDialog
import com.example.on_device_qwen.ui.components.SettingsDialog
import com.example.on_device_qwen.ui.designsystem.SovereignAlertDialog
import com.example.on_device_qwen.ui.designsystem.SovereignBanner
import com.example.on_device_qwen.ui.designsystem.SovereignElevation
import com.example.on_device_qwen.ui.designsystem.SovereignIconButton
import com.example.on_device_qwen.ui.designsystem.SovereignStatusDot
import com.example.on_device_qwen.ui.designsystem.SovereignStatusTone
import com.example.on_device_qwen.ui.designsystem.SovereignSurface
import com.example.on_device_qwen.ui.designsystem.SovereignTextField
import com.example.on_device_qwen.ui.designsystem.SovereignTheme
import com.example.on_device_qwen.ui.screens.DiagnosticsSheet
import com.example.on_device_qwen.ui.screens.EmptyChatState
import com.example.on_device_qwen.ui.screens.MessageList
import com.example.on_device_qwen.ui.screens.OnboardingScreen
import com.example.on_device_qwen.viewmodel.ChatScreenUiState
import com.example.on_device_qwen.viewmodel.ChatViewModel
import com.example.on_device_qwen.viewmodel.EngineState
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val chatState by viewModel.chatUiState.collectAsStateWithLifecycle()
    val modelHubState by viewModel.modelHubUiState.collectAsStateWithLifecycle()
    val settingsState by viewModel.settingsUiState.collectAsStateWithLifecycle()
    val memoryState by viewModel.memoryUiState.collectAsStateWithLifecycle()
    val navState by viewModel.navigationUiState.collectAsStateWithLifecycle()

    val colors = SovereignTheme.colors
    val adaptive = SovereignTheme.adaptive
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var showModelManager by remember { mutableStateOf(false) }
    var showMemoryManager by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }

    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var editText by remember { mutableStateOf("") }

    // Strictly uses Android Storage Access Framework — zero All-Files-Access prompts
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importModel(uri)
        }
    }

    editingMessage?.let { target ->
        SovereignAlertDialog(
            title = stringResource(R.string.msg_edit_dialog_title),
            confirmText = stringResource(R.string.label_send),
            confirmEnabled = editText.isNotBlank() || target.imagePath != null,
            onConfirm = {
                editingMessage = null
                viewModel.editAndResendMessage(target.id, editText.trim())
            },
            onDismissRequest = { editingMessage = null },
            content = {
                SovereignTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    minLines = 2,
                    maxLines = 6,
                )
            },
        )
    }

    if (showModelManager) {
        ModelManagerDialog(
            state = modelHubState,
            onSelectModel = { model ->
                showModelManager = false
                viewModel.switchModel(model)
            },
            onImportUri = { uri -> viewModel.importModel(uri) },
            onDeleteModel = { model -> viewModel.deleteModel(model) },
            onDismissImportError = { viewModel.dismissImportError() },
            onRefreshModels = { viewModel.refreshDeviceStatus() },
            onDismiss = { showModelManager = false },
        )
    }

    if (showMemoryManager) {
        MemoryManagerDialog(
            state = memoryState,
            onToggleMemory = { viewModel.togglePersistentMemory(it) },
            onSaveMemory = { viewModel.saveMemory(it) },
            onUpdateMemory = { id, content, cat -> viewModel.updateMemory(id, content, cat) },
            onDeleteMemory = { viewModel.deleteMemory(it) },
            onClearAllMemories = { viewModel.clearAllMemories() },
            onSearchMemories = { viewModel.searchMemories(it) },
            onSelectCategory = { viewModel.selectMemoryCategory(it) },
            onDismiss = { showMemoryManager = false },
        )
    }

    if (showSettings) {
        SettingsDialog(
            state = settingsState,
            onThemeModeChange = { viewModel.setThemeMode(it) },
            onHapticsEnabledChange = { viewModel.setHapticsEnabled(it) },
            onPerformanceModeChange = { viewModel.setPerformanceMode(it) },
            onBackendTypeChange = { viewModel.switchBackend(it) },
            onThreadCountChange = { viewModel.setThreadCount(it) },
            onTemperatureChange = { viewModel.setTemperature(it) },
            onTopPChange = { viewModel.setTopP(it) },
            onRepetitionPenaltyChange = { viewModel.setRepetitionPenalty(it) },
            onMaxTokensChange = { viewModel.setMaxTokens(it) },
            onContextLengthChange = { viewModel.setContextLength(it) },
            onSystemPromptChange = { viewModel.setSystemPrompt(it) },
            onChatModeChange = { viewModel.setChatMode(it) },
            onActiveProfileChange = { viewModel.setActivePromptProfile(it) },
            onProfileSaved = { profile -> viewModel.savePromptProfile(profile) },
            onProfileDeleted = { viewModel.deletePromptProfile(it) },
            onProfileReset = { viewModel.resetPromptProfile(it) },
            onToggleMemory = { viewModel.togglePersistentMemory(it) },
            onManageMemories = {
                showSettings = false
                viewModel.refreshDeviceStatus()
                showMemoryManager = true
            },
            onOpenDiagnostics = {
                showSettings = false
                viewModel.refreshDeviceStatus()
                showDiagnostics = true
            },
            onDismiss = { showSettings = false },
        )
    }

    if (showDiagnostics) {
        DiagnosticsSheet(
            modelTitle = chatState.modelTitle,
            activeModel = chatState.activeModelMetadata,
            activeEngineType = settingsState.activeEngineType,
            backendInfo = chatState.executionBackendInfo,
            capabilities = chatState.engineCapabilities,
            hardwareInfo = chatState.hardwareInfo,
            supportedParameters = settingsState.supportedParameters,
            notices = viewModel.runtimeSupportNotices(),
            onDismiss = { showDiagnostics = false },
        )
    }

    val shareTitle = stringResource(R.string.msg_share_chat_title)
    val noShareApp = stringResource(R.string.msg_no_share_app)

    val drawerContent: @Composable () -> Unit = {
        ChatNavigationDrawerContent(
            conversations = navState.conversations,
            activeConversationId = navState.activeConversationId,
            searchQuery = navState.searchQuery,
            onSearchQueryChange = { viewModel.searchConversations(it) },
            onSelectConversation = { session ->
                viewModel.selectConversation(session)
                if (!adaptive.usePersistentDrawer) coroutineScope.launch { drawerState.close() }
            },
            onNewChat = {
                viewModel.newChat()
                if (!adaptive.usePersistentDrawer) coroutineScope.launch { drawerState.close() }
            },
            onRenameConversation = { session, title -> viewModel.renameConversation(session, title) },
            onDeleteConversation = { session -> viewModel.deleteConversation(session) },
            onTogglePin = { session -> viewModel.togglePin(session) },
            onShareConversation = { session ->
                coroutineScope.launch {
                    val markdown = viewModel.formatConversationAsMarkdown(session).invoke()
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        putExtra(Intent.EXTRA_TEXT, markdown)
                        putExtra(Intent.EXTRA_SUBJECT, session.title)
                        type = "text/plain"
                    }
                    try {
                        context.startActivity(Intent.createChooser(shareIntent, shareTitle))
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, noShareApp, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onOpenModelManager = {
                if (!adaptive.usePersistentDrawer) coroutineScope.launch { drawerState.close() }
                viewModel.refreshDeviceStatus()
                showModelManager = true
            },
            onOpenMemory = {
                if (!adaptive.usePersistentDrawer) coroutineScope.launch { drawerState.close() }
                showMemoryManager = true
            },
            onOpenSettings = {
                if (!adaptive.usePersistentDrawer) coroutineScope.launch { drawerState.close() }
                viewModel.refreshDeviceStatus()
                showSettings = true
            },
            onOpenDiagnostics = {
                if (!adaptive.usePersistentDrawer) coroutineScope.launch { drawerState.close() }
                viewModel.refreshDeviceStatus()
                showDiagnostics = true
            },
            batteryPercent = navState.batteryPercent,
            batteryTemp = navState.batteryTemp,
            availableRamMb = navState.availableRamMb,
            showCloseButton = !adaptive.usePersistentDrawer,
            onCloseDrawer = { coroutineScope.launch { drawerState.close() } },
        )
    }

    val scaffoldContent: @Composable () -> Unit = {
        Scaffold(
            containerColor = colors.bgCanvas,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                ChatTopBar(
                    chatState = chatState,
                    showDrawerToggle = !adaptive.usePersistentDrawer,
                    onOpenDrawer = {
                        viewModel.refreshDeviceStatus()
                        coroutineScope.launch { drawerState.open() }
                    },
                    onOpenModelSelector = {
                        viewModel.refreshDeviceStatus()
                        showModelManager = true
                    },
                    onOpenSettings = {
                        viewModel.refreshDeviceStatus()
                        showSettings = true
                    },
                    onOpenDiagnostics = {
                        viewModel.refreshDeviceStatus()
                        showDiagnostics = true
                    },
                    onNewChat = { viewModel.newChat() },
                )
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .consumeWindowInsets(paddingValues),
            ) {
                SovereignBanner(
                    message = chatState.backendFallbackBanner,
                    tone = SovereignStatusTone.WARNING,
                    onDismiss = { viewModel.dismissBackendFallbackBanner() },
                    modifier = Modifier.padding(horizontal = adaptive.horizontalGutter, vertical = 4.dp),
                )
                SovereignBanner(
                    message = chatState.validationBanner ?: chatState.transientErrorBanner,
                    tone = SovereignStatusTone.ERROR,
                    actionLabel = if (chatState.validationBanner != null) stringResource(R.string.label_settings) else null,
                    onAction = if (chatState.validationBanner != null) {
                        { showSettings = true }
                    } else null,
                    onDismiss = {
                        viewModel.dismissValidationBanner()
                        viewModel.dismissTransientError()
                    },
                    modifier = Modifier.padding(horizontal = adaptive.horizontalGutter, vertical = 4.dp),
                )

                val showSetupOrOnboarding = chatState.showOnboarding ||
                    chatState.engineState !is EngineState.Ready

                if (showSetupOrOnboarding) {
                    OnboardingScreen(
                        engineState = chatState.engineState,
                        hardwareInfo = chatState.hardwareInfo,
                        recommendations = modelHubState.catalogRecommendations,
                        showFirstRunGuide = chatState.showOnboarding || !chatState.onboardingCompleted,
                        onImportModelClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                        onOpenModelHub = {
                            viewModel.refreshDeviceStatus()
                            showModelManager = true
                        },
                        onRetryEngine = { viewModel.initializeEngine() },
                        onSelectCandidate = { candidate -> viewModel.switchModel(candidate) },
                        onContinueDismissGuide = {
                            viewModel.completeOnboarding()
                            viewModel.refreshDeviceStatus()
                            showModelManager = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                } else {
                    if (chatState.messages.isEmpty()) {
                        EmptyChatState(
                            modelName = chatState.modelTitle,
                            executionBackendInfo = chatState.executionBackendInfo,
                            isVisionSupported = chatState.isVisionSupported,
                            onSelectPrompt = { viewModel.sendMessage(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                    } else {
                        MessageList(
                            conversationId = chatState.activeConversationId,
                            messages = chatState.messages,
                            isGenerating = chatState.isGenerating,
                            isSpeaking = chatState.isSpeaking,
                            lastUsedMemories = chatState.lastUsedMemories,
                            onSpeak = { viewModel.speak(it) },
                            onStopSpeaking = { viewModel.stopSpeaking() },
                            onEditMessage = { msg ->
                                editingMessage = msg
                                editText = msg.text
                            },
                            onRegenerateMessage = { id -> viewModel.regenerateMessage(id) },
                            onSelectSuggestion = { suggestion -> viewModel.sendMessage(suggestion) },
                            getFollowUpSuggestions = { text -> viewModel.getFollowUpSuggestions(text) },
                            onOpenMemoryManager = { showMemoryManager = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                    }

                    ChatComposer(
                        isGenerating = chatState.isGenerating,
                        isVisionModel = chatState.isVisionSupported,
                        onSendMessage = { text, uri -> viewModel.sendMessage(text, uri) },
                        onStopGeneration = { viewModel.stopGeneration() },
                    )
                }
            }
        }
    }

    if (adaptive.usePersistentDrawer) {
        PermanentNavigationDrawer(drawerContent = drawerContent) {
            scaffoldContent()
        }
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = drawerContent,
        ) {
            scaffoldContent()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    chatState: ChatScreenUiState,
    showDrawerToggle: Boolean,
    onOpenDrawer: () -> Unit,
    onOpenModelSelector: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onNewChat: () -> Unit,
) {
    val colors = SovereignTheme.colors
    val typography = SovereignTheme.typography
    val spacing = SovereignTheme.spacing

    val switchModelCd = stringResource(R.string.cd_switch_model)
    val isReady = chatState.engineState is EngineState.Ready
    val backendInfo: ExecutionBackendInfo? = chatState.executionBackendInfo

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colors.bgCanvas,
            titleContentColor = colors.textPrimary,
        ),
        navigationIcon = {
            if (showDrawerToggle) {
                SovereignIconButton(
                    onClick = onOpenDrawer,
                    contentDescription = stringResource(R.string.cd_open_drawer),
                    contentColor = colors.textPrimary,
                ) {
                    Icon(Icons.Rounded.Menu, contentDescription = null, modifier = Modifier.size(22.dp))
                }
            }
        },
        title = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart,
            ) {
                SovereignSurface(
                    shape = SovereignTheme.shapes.pill,
                    color = colors.surfaceLow,
                    border = BorderStroke(SovereignElevation.hairlineWidth, colors.hairlineStrong),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 40.dp)
                        .clip(SovereignTheme.shapes.pill)
                        .clickable(role = Role.Button, onClick = onOpenModelSelector)
                        .semantics { contentDescription = switchModelCd },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        SovereignStatusDot(
                            tone = when {
                                !isReady && chatState.engineState is EngineState.Error -> SovereignStatusTone.ERROR
                                !isReady && chatState.engineState is EngineState.Loading -> SovereignStatusTone.WARNING
                                backendInfo?.isFallback == true -> SovereignStatusTone.WARNING
                                isReady -> SovereignStatusTone.SUCCESS
                                else -> SovereignStatusTone.NEUTRAL
                            },
                            pulsing = chatState.engineState is EngineState.Loading,
                            size = 7.dp,
                        )
                        Text(
                            text = chatState.modelTitle,
                            style = typography.pillTitle,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = colors.textMuted,
                        )
                    }
                }
            }
        },
        actions = {
            ActiveModeChip(
                chatMode = chatState.chatMode,
                onClick = onOpenSettings,
            )
            Spacer(Modifier.width(spacing.xxs))
            SovereignIconButton(
                onClick = onOpenDiagnostics,
                contentDescription = stringResource(R.string.cd_open_diagnostics),
                contentColor = colors.textSecondary,
            ) {
                Icon(Icons.Rounded.MonitorHeart, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            SovereignIconButton(
                onClick = onNewChat,
                contentDescription = stringResource(R.string.cd_new_chat),
                contentColor = colors.textPrimary,
            ) {
                Icon(Icons.Rounded.EditNote, contentDescription = null, modifier = Modifier.size(22.dp))
            }
            SovereignIconButton(
                onClick = onOpenSettings,
                contentDescription = stringResource(R.string.label_settings),
                contentColor = colors.textSecondary,
            ) {
                Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        },
    )
}

@Composable
private fun ActiveModeChip(
    chatMode: UnrestrictedMode,
    onClick: () -> Unit,
) {
    val colors = SovereignTheme.colors
    val typography = SovereignTheme.typography
    val isUnrestricted = chatMode == UnrestrictedMode.UNRESTRICTED

    SovereignSurface(
        shape = SovereignTheme.shapes.pill,
        color = if (isUnrestricted) colors.amberContainer else colors.surfaceLow,
        border = BorderStroke(
            SovereignElevation.hairlineWidth,
            if (isUnrestricted) colors.amber.copy(alpha = 0.4f) else colors.hairlineSubtle,
        ),
        modifier = Modifier
            .defaultMinSize(minHeight = 40.dp)
            .clip(SovereignTheme.shapes.pill)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector = if (isUnrestricted) Icons.Rounded.FlashOn else Icons.Rounded.Shield,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (isUnrestricted) colors.onAmberContainer else colors.textSecondary,
            )
            Text(
                text = stringResource(
                    if (isUnrestricted) R.string.label_unrestricted_mode
                    else R.string.label_standard_mode,
                ),
                style = typography.actionLabel,
                color = if (isUnrestricted) colors.onAmberContainer else colors.textSecondary,
                maxLines = 1,
            )
        }
    }
}
