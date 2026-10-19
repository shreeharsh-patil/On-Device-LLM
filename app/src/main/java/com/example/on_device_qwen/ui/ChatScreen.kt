package com.example.on_device_qwen.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.lazy.rememberLazyListState
import java.io.File
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.on_device_qwen.R
import com.example.on_device_qwen.model.ChatMessage
import com.example.on_device_qwen.model.ModelErrorCode
import com.example.on_device_qwen.model.UnrestrictedMode
import com.example.on_device_qwen.ui.components.*
import com.example.on_device_qwen.viewmodel.ChatViewModel
import com.example.on_device_qwen.viewmodel.EngineState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val engineState by viewModel.engineState.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val isVisionModel by viewModel.isVisionModel.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val modelTitle by viewModel.modelTitle.collectAsState()
    val discoveredModels by viewModel.discoveredModels.collectAsState()
    val currentBackend by viewModel.currentBackend.collectAsState()
    val performanceMode by viewModel.performanceMode.collectAsState()
    val chatMode by viewModel.chatMode.collectAsState()
    val validationBanner by viewModel.validationBanner.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val activeConversation by viewModel.activeConversation.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    // Persistent Memory
    val persistentMemoryEnabled by viewModel.persistentMemoryEnabled.collectAsState()
    val allMemories by viewModel.allMemories.collectAsState()
    val lastUsedMemories by viewModel.lastUsedMemories.collectAsState()

    // Hardware Telemetry
    val thermalState by viewModel.thermalState.collectAsState()
    val batteryTemp by viewModel.batteryTemp.collectAsState()
    val batteryPercent by viewModel.batteryPercent.collectAsState()
    val isCharging by viewModel.isCharging.collectAsState()
    val availRamMb by viewModel.availRamMb.collectAsState()
    val totalRamMb by viewModel.totalRamMb.collectAsState()
    val importProgress by viewModel.importProgress.collectAsState()

    // Preferences
    val temperature by viewModel.temperature.collectAsState()
    val topP by viewModel.topP.collectAsState()
    val maxTokens by viewModel.maxTokens.collectAsState()
    val systemPrompt by viewModel.systemPrompt.collectAsState()
    val threadCount by viewModel.threadCount.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    var showModelManager by remember { mutableStateOf(false) }
    var showMemoryManager by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onAppForegrounded()
    }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.onAppForegrounded()
        }
    }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
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

    // Message editing state
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var editText by remember { mutableStateOf("") }

    if (editingMessage != null) {
        AlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text("Edit Message", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val msg = editingMessage!!
                        editingMessage = null
                        viewModel.editAndResendMessage(msg.id, editText.trim())
                    }
                ) { Text("Send") }
            },
            dismissButton = {
                TextButton(onClick = { editingMessage = null }) { Text("Cancel") }
            }
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
            onDeleteModel = {},
            onDismiss = { showModelManager = false }
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
            onDismiss = { showMemoryManager = false }
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
            onDismiss = { showSettings = false }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ChatNavigationDrawerContent(
                conversations = conversations,
                activeConversationId = activeConversation?.id,
                searchQuery = searchQuery,
                onSearchQueryChange = { viewModel.searchConversations(it) },
                onSelectConversation = { session ->
                    viewModel.selectConversation(session)
                    coroutineScope.launch { drawerState.close() }
                },
                onNewChat = {
                    viewModel.newChat()
                    coroutineScope.launch { drawerState.close() }
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
                    coroutineScope.launch { drawerState.close() }
                    showModelManager = true
                },
                onOpenMemory = {
                    coroutineScope.launch { drawerState.close() }
                    showMemoryManager = true
                },
                onOpenSettings = {
                    coroutineScope.launch { drawerState.close() }
                    showSettings = true
                },
                batteryPercent = batteryPercent,
                batteryTemp = batteryTemp,
                availableRamMb = availRamMb
            )
        }
    ) {
        Scaffold(
            topBar = {
                ChatTopBar(
                    modelTitle = modelTitle,
                    engineState = engineState,
                    chatMode = chatMode,
                    activeProfileName = activeProfile.name,
                    onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                    onOpenModelSelector = { showModelManager = true },
                    onOpenSettings = { showSettings = true },
                    onNewChat = { viewModel.newChat() }
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (engineState) {
                    is EngineState.Idle,
                    is EngineState.Loading,
                    is EngineState.Error -> {
                        EngineStatusOverlay(
                            state = engineState,
                            onRetry = { viewModel.initializeEngine() },
                            onOpenModelManager = { showModelManager = true },
                            onRequestPermission = onRequestPermission,
                            onSelectCandidate = { candidate -> viewModel.switchModel(candidate) },
                            onOpenFilePicker = { filePickerLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }

                    is EngineState.Ready -> {
                        if (messages.isEmpty()) {
                            EmptyChatWelcomeView(
                                modelName = modelTitle,
                                isVisionModel = isVisionModel,
                                onSelectPrompt = { viewModel.sendMessage(it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            )
                        } else {
                            MessageListView(
                                messages = messages,
                                isGenerating = isGenerating,
                                isSpeaking = isSpeaking,
                                lastUsedMemories = lastUsedMemories,
                                onSpeak = { viewModel.speak(it) },
                                onStopSpeaking = { viewModel.stopSpeaking() },
                                onEditMessage = { msg ->
                                    editingMessage = msg
                                    editText = msg.text
                                },
                                onRegenerate = { viewModel.regenerateLastResponse() },
                                onSelectSuggestion = { suggestion -> viewModel.sendMessage(suggestion) },
                                getFollowUpSuggestions = { text -> viewModel.getFollowUpSuggestions(text) },
                                onOpenMemoryManager = { showMemoryManager = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            )
                        }

                        ChatComposer(
                            isGenerating = isGenerating,
                            isVisionModel = isVisionModel,
                            onSendMessage = { text, uri -> viewModel.sendMessage(text, uri) },
                            onStopGeneration = { viewModel.stopGeneration() }
                        )
                    }
                }
            }
        }
    }
}

// ─── ChatGPT Top App Bar ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    modelTitle: String,
    engineState: EngineState,
    chatMode: UnrestrictedMode,
    activeProfileName: String,
    onOpenDrawer: () -> Unit,
    onOpenModelSelector: () -> Unit,
    onOpenSettings: () -> Unit,
    onNewChat: () -> Unit
) {
    TopAppBar(
        title = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                // Centered pill model selector like ChatGPT
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onOpenModelSelector() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = modelTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Switch model",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Default.Menu, contentDescription = "Sidebar")
            }
        },
        actions = {
            ActiveModeChip(
                chatMode = chatMode,
                profileName = activeProfileName,
                onClick = onOpenSettings
            )
            IconButton(onClick = onNewChat) {
                Icon(Icons.Outlined.Edit, contentDescription = "New chat")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

// ─── Active Inference Mode Chip ─────────────────────────────────────────────

/**
 * Always-visible indicator of the active Standard/Unrestricted mode plus the
 * selected prompt profile, so the user can confirm what the model was told
 * before sending a message. Tapping it opens Settings.
 */
@Composable
private fun ActiveModeChip(
    chatMode: UnrestrictedMode,
    profileName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isUnrestricted = chatMode == UnrestrictedMode.UNRESTRICTED
    val containerColor = if (isUnrestricted) {
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val contentColor = if (isUnrestricted) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.25f)),
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Icon(
                imageVector = if (isUnrestricted) Icons.Default.FlashOn else Icons.Default.Shield,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = contentColor
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (isUnrestricted) "Unrestricted" else "Standard",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1
            )
            if (profileName.isNotBlank()) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "• $profileName",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ─── ChatGPT Empty State (Welcome View) ────────────────────────────────────

@Composable
private fun EmptyChatWelcomeView(
    modelName: String,
    isVisionModel: Boolean,
    onSelectPrompt: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Futuristic Monochrome AI Robot Emblem
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = R.drawable.app_logo),
                        contentDescription = "AI Robot Emblem",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "What's on your mind?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "$modelName • 100% On-Device & Private",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(36.dp))

            // Clean prompt cards
            val promptSuggestions = if (isVisionModel) {
                listOf(
                    "Describe an uploaded photo in detail",
                    "Explain quantum computing simply",
                    "Write a Python script for matrix multiplication"
                )
            } else {
                listOf(
                    "Explain the difference between CPU and NPU inference",
                    "Summarize the key principles of thermodynamics",
                    "Write 3 productive habits for software developers"
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (prompt in promptSuggestions) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectPrompt(prompt) }
                    ) {
                        Text(
                            text = prompt,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─── Responsive Messages List View ──────────────────────────────────────────

@Composable
private fun MessageListView(
    messages: List<ChatMessage>,
    isGenerating: Boolean,
    isSpeaking: Boolean,
    lastUsedMemories: List<com.example.on_device_qwen.model.MemoryItem> = emptyList(),
    onSpeak: (String) -> Unit,
    onStopSpeaking: () -> Unit,
    onEditMessage: (ChatMessage) -> Unit,
    onRegenerate: () -> Unit,
    onSelectSuggestion: (String) -> Unit,
    getFollowUpSuggestions: (String?) -> List<String>,
    onOpenMemoryManager: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val isUserDragging by listState.interactionSource.collectIsDraggedAsState()

    // Follow mode state: user starts in follow mode; manual scroll upward detachment pauses auto-scroll
    var userScrolledAway by remember { mutableStateOf(false) }

    // When user drags and there is content below, they detached upward
    LaunchedEffect(isUserDragging) {
        if (isUserDragging && listState.canScrollForward) {
            userScrolledAway = true
        }
    }

    // When the list reaches the very bottom, re-engage follow mode automatically
    LaunchedEffect(listState.canScrollForward) {
        if (!listState.canScrollForward) {
            userScrolledAway = false
        }
    }

    // When a new message is posted, snap to the bottom and engage follow mode
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            userScrolledAway = false
            listState.scrollToItem(messages.lastIndex)
        }
    }

    // When generating: auto-scroll ONLY if user is following (not dragging or scrolled away)
    LaunchedEffect(messages.lastOrNull()?.text?.length) {
        if (!userScrolledAway && !isUserDragging && messages.isNotEmpty() && isGenerating) {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            if (lastVisible >= messages.lastIndex - 1) {
                listState.scrollToItem(messages.lastIndex)
            }
        }
    }

    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Memory context badge at top of conversation if persistent memories were retrieved
            if (lastUsedMemories.isNotEmpty()) {
                item(key = "memory_active_badge") {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onOpenMemoryManager() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "🧠 ${lastUsedMemories.size} persistent memories retrieved for this context",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            items(messages, key = { it.id }) { message ->
                val isLastMessage = message.id == messages.lastOrNull()?.id
                val suggestions = if (isLastMessage && !message.isUser && !message.isStreaming && !isGenerating) {
                    remember(message.text) { getFollowUpSuggestions(message.text) }
                } else {
                    emptyList()
                }

                MessageBubbleItem(
                    message = message,
                    isGenerating = isGenerating,
                    isSpeaking = isSpeaking,
                    suggestions = suggestions,
                    onSpeak = onSpeak,
                    onStopSpeaking = onStopSpeaking,
                    onEditMessage = { onEditMessage(message) },
                    onRegenerate = onRegenerate,
                    onSelectSuggestion = onSelectSuggestion
                )
            }
        }

        // Subtle floating "Jump to latest" button when scrolled upward during or after generation
        AnimatedVisibility(
            visible = userScrolledAway && messages.isNotEmpty(),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable {
                        userScrolledAway = false
                        scope.launch {
                            listState.animateScrollToItem(messages.lastIndex)
                        }
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    if (isGenerating) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = if (isGenerating) "Generating… Jump to latest" else "Jump to latest",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Scroll to bottom",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubbleItem(
    message: ChatMessage,
    isGenerating: Boolean,
    isSpeaking: Boolean,
    suggestions: List<String>,
    onSpeak: (String) -> Unit,
    onStopSpeaking: () -> Unit,
    onEditMessage: () -> Unit,
    onRegenerate: () -> Unit,
    onSelectSuggestion: (String) -> Unit
) {
    val isUser = message.isUser
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isUser) {
            // ChatGPT-style User row with subtle inline Edit icon
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onEditMessage,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Edit message",
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }

                Spacer(Modifier.width(4.dp))

                Surface(
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp))
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        message.imagePath?.let { path ->
                            val bitmap = remember(path) {
                                try { BitmapFactory.decodeFile(path) } catch (_: Exception) { null }
                            }
                            bitmap?.let { bmp ->
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Attached photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 200.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        } else {
            // ChatGPT-style Assistant response (directly on canvas)
            Column(modifier = Modifier.fillMaxWidth()) {
                // Expandable Reasoning Drawer (<think>...</think>)
                if (!message.thinkText.isNullOrBlank() || (message.isStreaming && message.text.isBlank())) {
                    ThinkingSection(
                        thinkText = message.thinkText ?: "",
                        isThinking = message.isStreaming && message.text.isBlank(),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Main Markdown content with streaming cursor support
                if (message.text.isNotEmpty()) {
                    MarkdownText(
                        text = message.text,
                        isStreaming = message.isStreaming,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (message.isStreaming && message.thinkText.isNullOrBlank()) {
                    TypingDotsIndicator()
                }

                // Telemetry Benchmark Badge
                if (message.benchmark != null && (message.text.isNotEmpty() || message.isStreaming)) {
                    Spacer(Modifier.height(8.dp))
                    BenchmarkBadge(
                        benchmark = message.benchmark,
                        isStreaming = message.isStreaming
                    )
                }

                // ChatGPT-style Action Row: Copy, Share, Read Aloud, Regenerate
                if (!message.isStreaming && message.text.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Response", message.text)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (isSpeaking) {
                                    onStopSpeaking()
                                } else {
                                    onSpeak(message.text)
                                }
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = if (isSpeaking) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = "Read aloud",
                                modifier = Modifier.size(16.dp),
                                tint = if (isSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }

                        IconButton(
                            onClick = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, message.text)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share response"))
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Share,
                                contentDescription = "Share",
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }

                        IconButton(
                            onClick = onRegenerate,
                            enabled = !isGenerating,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Refresh,
                                contentDescription = "Regenerate",
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                // ChatGPT-style Follow-up Suggestion Chips
                if (suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (suggestion in suggestions) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { onSelectSuggestion(suggestion) }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = suggestion,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Engine Status Overlay ──────────────────────────────────────────────────

@Composable
private fun EngineStatusOverlay(
    state: EngineState,
    onRetry: () -> Unit,
    onOpenModelManager: () -> Unit,
    onRequestPermission: () -> Unit,
    onSelectCandidate: (File) -> Unit,
    onOpenFilePicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            when (state) {
                is EngineState.Idle -> {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier.size(60.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(30.dp))
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "On-Device AI Assistant",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Offline inference with LiteRT-LM & llama.cpp",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = onRetry,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(46.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Initialize Engine")
                    }
                }

                is EngineState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = state.progress,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                is EngineState.Error -> {
                    val iconVector = when (state.errorCode) {
                        ModelErrorCode.FILE_UNREADABLE_OR_PERMISSION_DENIED -> Icons.Default.Lock
                        ModelErrorCode.DOWNLOADED_MODEL_NOT_REGISTERED -> Icons.Default.Done
                        ModelErrorCode.INSUFFICIENT_RAM -> Icons.Default.Memory
                        else -> Icons.Default.Warning
                    }
                    val iconTint = when (state.errorCode) {
                        ModelErrorCode.DOWNLOADED_MODEL_NOT_REGISTERED -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.error
                    }

                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(Modifier.height(14.dp))

                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(8.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(14.dp),
                            textAlign = TextAlign.Center
                        )
                    }

                    // Candidate recovered model ready for one-click load
                    if (state.candidateFile != null) {
                        Spacer(Modifier.height(14.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Discovered: ${state.candidateFile.name}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = { onSelectCandidate(state.candidateFile) },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Load Discovered Model")
                                }
                            }
                        }
                    }

                    // Storage permission button
                    if (state.canRequestPermission) {
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = onRequestPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Grant Storage Permission")
                        }
                    }

                    // File picker button for SAF selection
                    if (state.canOpenFilePicker && state.candidateFile == null) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onOpenFilePicker,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Locate Model File (SAF)")
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onRetry,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Scan Again")
                        }
                        Button(
                            onClick = onOpenModelManager,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Catalog")
                        }
                    }
                }

                else -> {}
            }
        }
    }
}

@Composable
private fun TypingDotsIndicator() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        repeat(3) { index ->
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(7.dp)
            ) {}
        }
    }
}
