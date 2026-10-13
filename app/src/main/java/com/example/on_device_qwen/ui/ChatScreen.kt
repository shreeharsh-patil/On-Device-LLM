package com.example.on_device_qwen.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.on_device_qwen.model.ChatMessage
import com.example.on_device_qwen.model.ModelMetadata
import com.example.on_device_qwen.ui.components.*
import com.example.on_device_qwen.ui.theme.*
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
    val modelTitle by viewModel.modelTitle.collectAsState()
    val discoveredModels by viewModel.discoveredModels.collectAsState()
    val currentBackend by viewModel.currentBackend.collectAsState()
    val performanceMode by viewModel.performanceMode.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val activeConversation by viewModel.activeConversation.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

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
    var showSettings by remember { mutableStateOf(false) }

    // Message editing state
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var editText by remember { mutableStateOf("") }

    if (editingMessage != null) {
        AlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text("Edit Message") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5
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
            themeMode = themeMode,
            onThemeModeChange = { viewModel.setThemeMode(it) },
            thermalState = thermalState,
            batteryTemp = batteryTemp,
            batteryPercent = batteryPercent,
            isCharging = isCharging,
            availRamMb = availRamMb,
            totalRamMb = totalRamMb,
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
                    currentBackend = currentBackend,
                    modelsCount = discoveredModels.size,
                    onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                    onOpenModelSelector = { showModelManager = true },
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
                                onEditMessage = { msg ->
                                    editingMessage = msg
                                    editText = msg.text
                                },
                                onRegenerate = { viewModel.regenerateLastResponse() },
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

// ─── Top App Bar ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    modelTitle: String,
    engineState: EngineState,
    currentBackend: com.example.on_device_qwen.model.BackendType,
    modelsCount: Int,
    onOpenDrawer: () -> Unit,
    onOpenModelSelector: () -> Unit,
    onNewChat: () -> Unit
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOpenModelSelector() }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Text(
                    text = modelTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Switch model",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Default.Menu, contentDescription = "Menu")
            }
        },
        actions = {
            // New chat icon
            IconButton(onClick = onNewChat) {
                Icon(Icons.Default.AddComment, contentDescription = "New chat")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

// ─── Empty State (Welcome View) ─────────────────────────────────────────────

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
            // Elegant Qwen Assistant Logo
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(QwenPrimary, QwenSecondary))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Q",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp
                )
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = "What's on your mind?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "$modelName • 100% On-Device & Offline",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(32.dp))

            // Suggested prompt chips
            val promptSuggestions = if (isVisionModel) {
                listOf(
                    "Describe an uploaded photo in detail",
                    "Explain quantum computing in simple terms",
                    "Write a Python script for fast matrix multiplication"
                )
            } else {
                listOf(
                    "Explain the difference between CPU and NPU inference",
                    "Write a concise summary of the theory of relativity",
                    "Give me 5 productive morning routine habits"
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (prompt in promptSuggestions) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectPrompt(prompt) }
                    ) {
                        Text(
                            text = prompt,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─── Messages List View ─────────────────────────────────────────────────────

@Composable
private fun MessageListView(
    messages: List<ChatMessage>,
    isGenerating: Boolean,
    onEditMessage: (ChatMessage) -> Unit,
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
        if (messages.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(messages.lastIndex)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.padding(horizontal = 14.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(messages, key = { it.id }) { message ->
            MessageBubbleItem(
                message = message,
                isGenerating = isGenerating,
                onEditMessage = { onEditMessage(message) },
                onRegenerate = onRegenerate
            )
        }
    }
}

@Composable
private fun MessageBubbleItem(
    message: ChatMessage,
    isGenerating: Boolean,
    onEditMessage: () -> Unit,
    onRegenerate: () -> Unit
) {
    val isUser = message.isUser
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isUser) {
            // User message bubble
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = QwenUserBubble,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .clip(RoundedCornerShape(20.dp))
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
                        color = Color.White
                    )
                }
            }
        } else {
            // Assistant response
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header row with Qwen icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(QwenPrimary, QwenSecondary))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Q", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Qwen",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Expandable Reasoning Drawer (<think>...</think>)
                if (!message.thinkText.isNullOrBlank() || (message.isStreaming && message.text.isBlank())) {
                    ThinkingSection(
                        thinkText = message.thinkText ?: "",
                        isThinking = message.isStreaming && message.text.isBlank(),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Main Markdown content
                if (message.text.isNotEmpty()) {
                    MarkdownText(
                        text = message.text,
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

                // Message Actions Toolbar (Copy, Share, Regenerate)
                if (!message.isStreaming && message.text.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Qwen Response", message.text)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
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
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(16.dp))
                        }

                        IconButton(
                            onClick = onRegenerate,
                            enabled = !isGenerating,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Regenerate", modifier = Modifier.size(16.dp))
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
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            when (state) {
                is EngineState.Idle -> {
                    GlowingQLogo()
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "On-Device Qwen 2.0",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Offline on-device inference with LiteRT and llama.cpp",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(28.dp))
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = QwenPrimary),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(50.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Initialize Engine")
                    }
                }

                is EngineState.Loading -> {
                    PulsingQLogo()
                    Spacer(Modifier.height(24.dp))
                    CircularProgressIndicator(color = QwenPrimary)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = state.progress,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                is EngineState.Error -> {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = QwenError,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onRetry,
                            colors = ButtonDefaults.buttonColors(containerColor = QwenPrimary),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Retry")
                        }
                        OutlinedButton(
                            onClick = onOpenModelManager,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Layers, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Model Manager")
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
            val infiniteTransition = rememberInfiniteTransition(label = "dot_$index")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500, delayMillis = index * 150),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "alpha_$index"
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(QwenPrimary.copy(alpha = alpha))
            )
        }
    }
}

@Composable
private fun GlowingQLogo() {
    Box(
        modifier = Modifier
            .size(76.dp)
            .shadow(12.dp, CircleShape, ambientColor = QwenPrimary, spotColor = QwenPrimary)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(QwenPrimary, QwenSecondary))),
        contentAlignment = Alignment.Center
    ) {
        Text("Q", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PulsingQLogo() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_q")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_q_scale"
    )
    Box(
        modifier = Modifier
            .size((76 * scale).dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(QwenPrimary, QwenSecondary))),
        contentAlignment = Alignment.Center
    ) {
        Text("Q", color = Color.White, fontSize = (34 * scale).sp, fontWeight = FontWeight.Bold)
    }
}
