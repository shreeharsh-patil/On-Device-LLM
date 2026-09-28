package com.example.on_device_qwen.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.on_device_qwen.model.ChatMessage
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

    Scaffold(
        topBar = { ChatTopBar(viewModel, engineState) },
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }

                is EngineState.Ready -> {
                    if (messages.isEmpty()) {
                        WelcomeView(
                            onSelectSuggestion = { viewModel.sendMessage(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    } else {
                        MessageList(
                            messages = messages,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                    ChatInputBar(
                        isGenerating = isGenerating,
                        isVisionModel = isVisionModel,
                        onSend = { text, uri -> viewModel.sendMessage(text, uri) },
                    )
                }
            }
        }
    }
}

// ─── Top App Bar ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(viewModel: ChatViewModel, engineState: EngineState) {
    val modelTitle by viewModel.modelTitle.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val currentBackend by viewModel.currentBackend.collectAsState()
    var showModelMenu by remember { mutableStateOf(false) }
    var showBackendMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = if (availableModels.size > 1) {
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showModelMenu = true }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                } else Modifier
            ) {
                // Qwen logo indicator
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(QwenPrimary, QwenSecondary)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Q", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            modelTitle,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                        if (availableModels.size > 1) {
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = "Switch model",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = when (engineState) {
                            is EngineState.Idle -> "Tap to initialize"
                            is EngineState.Loading -> "Loading…"
                            is EngineState.Ready -> "On-device • ${currentBackend.chipText}"
                            is EngineState.Error -> "Error"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when (engineState) {
                            is EngineState.Ready -> QwenSecondary
                            is EngineState.Error -> QwenError
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        },
        actions = {
            // ── Backend Selector Chip (CPU / GPU / NPU) ──────────────────
            Box {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(
                        1.dp,
                        when (currentBackend) {
                            com.example.on_device_qwen.viewmodel.BackendType.NPU -> Color(0xFF4CAF50).copy(alpha = 0.5f)
                            com.example.on_device_qwen.viewmodel.BackendType.GPU -> Color(0xFFFF9800).copy(alpha = 0.5f)
                            com.example.on_device_qwen.viewmodel.BackendType.CPU -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        }
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showBackendMenu = true }
                        .padding(horizontal = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            Icons.Default.Memory,
                            contentDescription = "Backend",
                            tint = when (currentBackend) {
                                com.example.on_device_qwen.viewmodel.BackendType.NPU -> Color(0xFF4CAF50)
                                com.example.on_device_qwen.viewmodel.BackendType.GPU -> Color(0xFFFF9800)
                                com.example.on_device_qwen.viewmodel.BackendType.CPU -> MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            currentBackend.chipText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = when (currentBackend) {
                                com.example.on_device_qwen.viewmodel.BackendType.NPU -> Color(0xFF4CAF50)
                                com.example.on_device_qwen.viewmodel.BackendType.GPU -> Color(0xFFFF9800)
                                com.example.on_device_qwen.viewmodel.BackendType.CPU -> MaterialTheme.colorScheme.primary
                            }
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                DropdownMenu(
                    expanded = showBackendMenu,
                    onDismissRequest = { showBackendMenu = false }
                ) {
                    Text(
                        "Inference Backend",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    HorizontalDivider()
                    com.example.on_device_qwen.viewmodel.BackendType.entries.forEach { b ->
                        val isSelected = b == currentBackend
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        b.chipText,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Text(
                                        b.label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            trailingIcon = {
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = "Active", tint = QwenPrimary)
                                }
                            },
                            onClick = {
                                showBackendMenu = false
                                if (!isSelected) {
                                    viewModel.switchBackend(b)
                                }
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.width(4.dp))

            // ── Model Selector Menu ──────────────────────────────────────
            if (availableModels.size > 1) {
                Box {
                    IconButton(onClick = { showModelMenu = true }) {
                        Icon(
                            Icons.Default.Layers,
                            contentDescription = "Switch model",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(
                        expanded = showModelMenu,
                        onDismissRequest = { showModelMenu = false }
                    ) {
                        Text(
                            "Select Local Model",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        HorizontalDivider()
                        availableModels.forEach { file ->
                            val isSelected = file.nameWithoutExtension == modelTitle
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            file.nameWithoutExtension,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                        Text(
                                            "${file.length() / 1_048_576} MB",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                trailingIcon = {
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = "Active", tint = QwenPrimary)
                                    }
                                },
                                onClick = {
                                    showModelMenu = false
                                    if (!isSelected) {
                                        viewModel.switchModel(file)
                                    }
                                }
                            )
                        }
                    }
                }
            }
            if (engineState is EngineState.Ready) {
                IconButton(onClick = { viewModel.clearChat() }) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "New chat",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

// ─── Engine Status Overlay (Loading / Error / Idle) ─────────────────────────

@Composable
private fun EngineStatusOverlay(
    state: EngineState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            when (state) {
                is EngineState.Idle -> {
                    GlowingQwenLogo()
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "On-Device Qwen",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Run Qwen 2.5-3B completely offline on your device",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(32.dp))
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = QwenPrimary),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.height(52.dp),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Initialize Engine", fontWeight = FontWeight.SemiBold)
                    }
                }

                is EngineState.Loading -> {
                    PulsingQwenLogo()
                    Spacer(Modifier.height(24.dp))
                    CircularProgressIndicator(color = QwenPrimary)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        state.progress,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                is EngineState.Error -> {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = QwenError,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            state.message,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = QwenPrimary),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Retry")
                    }
                }

                else -> {}
            }
        }
    }
}

// ─── Welcome View (engine ready, no messages yet) ───────────────────────────

@Composable
private fun WelcomeView(
    onSelectSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            GlowingQwenLogo()
            Spacer(Modifier.height(24.dp))
            Text(
                "Ready to chat!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Your Qwen model is loaded and running entirely on-device.\nNo internet connection required.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))

            // Suggestion chips
            val suggestions = listOf(
                "Explain quantum computing simply",
                "Write a haiku about Android",
                "What is the Pythagorean theorem?",
            )
            suggestions.forEach { suggestion ->
                SuggestionChipRow(
                    text = suggestion,
                    onClick = { onSelectSuggestion(suggestion) }
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SuggestionChipRow(
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Lightbulb,
                contentDescription = null,
                tint = QwenPrimary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ─── Message List ───────────────────────────────────────────────────────────

@Composable
private fun MessageList(messages: List<ChatMessage>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size, messages.lastOrNull()?.text) {
        if (messages.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(messages.lastIndex)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.padding(horizontal = 12.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(messages, key = { it.id }) { message ->
            ChatBubble(message = message)
        }
    }
}

// ─── Chat Bubble ────────────────────────────────────────────────────────────

// ─── Chat Bubble ────────────────────────────────────────────────────────────

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.isUser

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        // Avatar + Label row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            if (!isUser) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(QwenPrimary, QwenSecondary))
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Q", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    "Qwen",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "You",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Bubble
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp,
            ),
            color = if (isUser) QwenUserBubble else MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 1.dp,
            modifier = Modifier
                .widthIn(max = 340.dp)
                .animateContentSize(animationSpec = spring(dampingRatio = 0.8f)),
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (isUser) {
                    message.imagePath?.let { path ->
                        val bitmap = remember(path) {
                            try {
                                BitmapFactory.decodeFile(path)
                            } catch (_: Exception) {
                                null
                            }
                        }
                        bitmap?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Attached photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                    )
                } else {
                    BotMessageContent(
                        rawText = message.text,
                        isStreaming = message.isStreaming
                    )
                    // Benchmarking notations (TPS, TTFT, Backend NPU/GPU/CPU)
                    if (message.benchmark != null && (message.text.isNotEmpty() || message.isStreaming)) {
                        Spacer(Modifier.height(8.dp))
                        BenchmarkNotationBar(
                            benchmark = message.benchmark,
                            isStreaming = message.isStreaming
                        )
                    }
                }
            }
        }
    }
}

/**
 * Sleek, high-tech benchmarking notation chip bar displaying:
 * - TPS (Tokens Per Second)
 * - TTFT (Time To First Token)
 * - Backend in use: NPU / GPU / CPU
 * - Total tokens and elapsed time
 */
@Composable
private fun BenchmarkNotationBar(
    benchmark: com.example.on_device_qwen.model.BenchmarkMetrics,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            0.5.dp,
            if (isStreaming) QwenSecondary.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // ── 1. TPS Notation ──────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Speed,
                    contentDescription = "TPS",
                    tint = QwenPrimary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = if (benchmark.decodeTps > 0) {
                        String.format(java.util.Locale.US, "%.1f TPS", benchmark.decodeTps)
                    } else if (isStreaming) {
                        "measuring…"
                    } else {
                        "— TPS"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = QwenPrimary
                )
            }

            // Divider dot
            Text("•", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outlineVariant)

            // ── 2. TTFT Notation ─────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Timer,
                    contentDescription = "TTFT",
                    tint = QwenSecondary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = if (benchmark.ttftMs > 0) {
                        "TTFT ${benchmark.ttftMs}ms"
                    } else if (isStreaming) {
                        "TTFT…"
                    } else {
                        "TTFT —"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Divider dot
            Text("•", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outlineVariant)

            // ── 3. Backend Notation (NPU / GPU / CPU) ────
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = when (benchmark.backend.uppercase()) {
                    "NPU" -> Color(0xFF1B5E20).copy(alpha = 0.2f)
                    "GPU" -> Color(0xFFE65100).copy(alpha = 0.2f)
                    else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Memory,
                        contentDescription = "Backend",
                        tint = when (benchmark.backend.uppercase()) {
                            "NPU" -> Color(0xFF4CAF50)
                            "GPU" -> Color(0xFFFF9800)
                            else -> MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = benchmark.backend,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        fontWeight = FontWeight.Bold,
                        color = when (benchmark.backend.uppercase()) {
                            "NPU" -> Color(0xFF4CAF50)
                            "GPU" -> Color(0xFFFF9800)
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }

            // ── 4. Token summary ─────────────────────────
            if (!isStreaming && benchmark.decodeTokens > 0) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${benchmark.decodeTokens} tok" + (if (benchmark.totalDurationMs > 0) " (${String.format(java.util.Locale.US, "%.1fs", benchmark.totalDurationMs / 1000.0)})" else ""),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Renders the AI response with explicit separation between:
 * 1. The reasoning/thought process (<think> ... </think>)
 * 2. The main answer response
 */
@Composable
private fun BotMessageContent(
    rawText: String,
    isStreaming: Boolean
) {
    val parsed = remember(rawText, isStreaming) {
        parseQwenMessage(rawText, isStreaming)
    }

    if (rawText.isEmpty() && isStreaming) {
        TypingIndicator()
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // ── 1. Think Section (if present or actively thinking) ──────────────────
        if (parsed.thinkText != null || parsed.isThinking) {
            ThinkingBox(
                thinkText = parsed.thinkText ?: "",
                isThinking = parsed.isThinking,
            )
        }

        // ── 2. Answer Section ──────────────────────────────────────────────────
        if (parsed.answerText.isNotEmpty()) {
            Text(
                text = parsed.answerText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else if (!parsed.isThinking && parsed.thinkText == null) {
            Text(
                text = rawText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // Streaming indicator
        if (isStreaming && !parsed.isThinking && parsed.answerText.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            StreamingDot()
        }
    }
}

/**
 * Collapsible, styled container specifically for model reasoning / thoughts.
 */
@Composable
private fun ThinkingBox(
    thinkText: String,
    isThinking: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(true) }

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (isThinking) QwenSecondary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header row with toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
            ) {
                Icon(
                    imageVector = if (isThinking) Icons.Default.AutoAwesome else Icons.Default.Lightbulb,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (isThinking) QwenSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isThinking) "Thinking…" else "Thought process",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isThinking) QwenSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Expanded body content
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Row {
                        // Left accent bar
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .fillMaxHeight()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(QwenSecondary.copy(alpha = 0.7f), QwenPrimary.copy(alpha = 0.3f))
                                    )
                                )
                        )
                        Spacer(Modifier.width(8.dp))
                        if (thinkText.isNotEmpty()) {
                            Text(
                                text = thinkText,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    lineHeight = 18.sp,
                                    fontStyle = FontStyle.Italic
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                            )
                        } else if (isThinking) {
                            TypingIndicator()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Data structure holding separated thought and answer components.
 */
private data class ParsedQwenMessage(
    val thinkText: String?,
    val isThinking: Boolean,
    val answerText: String
)

/**
 * Parses <think> and </think> tags from model output.
 */
private fun parseQwenMessage(rawText: String, isStreaming: Boolean): ParsedQwenMessage {
    val thinkOpenTag = "<think>"
    val thinkCloseTag = "</think>"
    val openIdx = rawText.indexOf(thinkOpenTag)

    if (openIdx == -1) {
        return ParsedQwenMessage(thinkText = null, isThinking = false, answerText = rawText)
    }

    val afterOpen = rawText.substring(openIdx + thinkOpenTag.length)
    val closeIdx = afterOpen.indexOf(thinkCloseTag)

    if (closeIdx == -1) {
        // Still thinking / closing tag not yet received
        return ParsedQwenMessage(
            thinkText = afterOpen.trimStart('\n', '\r', ' '),
            isThinking = isStreaming,
            answerText = ""
        )
    }

    val think = afterOpen.substring(0, closeIdx).trim()
    val answer = afterOpen.substring(closeIdx + thinkCloseTag.length).trimStart('\n', '\r', ' ')
    return ParsedQwenMessage(
        thinkText = think.ifEmpty { null },
        isThinking = false,
        answerText = answer
    )
}

// ─── Typing / Streaming indicators ─────────────────────────────────────────

@Composable
private fun TypingIndicator() {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { index ->
            val infiniteTransition = rememberInfiniteTransition(label = "dot_$index")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500, delayMillis = index * 150),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "alpha_$index",
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
private fun StreamingDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "stream")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "stream_alpha",
    )
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(QwenSecondary.copy(alpha = alpha))
    )
}

// ─── Animated Logo Components ───────────────────────────────────────────────

@Composable
private fun GlowingQwenLogo() {
    Box(
        modifier = Modifier
            .size(80.dp)
            .shadow(12.dp, CircleShape, ambientColor = QwenPrimary, spotColor = QwenPrimary)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(listOf(QwenPrimary, QwenSecondary))
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text("Q", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PulsingQwenLogo() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_scale",
    )
    Box(
        modifier = Modifier
            .size((80 * scale).dp)
            .shadow(
                (12 * scale).dp, CircleShape,
                ambientColor = QwenPrimary, spotColor = QwenPrimary
            )
            .clip(CircleShape)
            .background(
                Brush.linearGradient(listOf(QwenPrimary, QwenSecondary))
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text("Q", color = Color.White, fontSize = (36 * scale).sp, fontWeight = FontWeight.Bold)
    }
}

// ─── Input Bar ──────────────────────────────────────────────────────────────

@Composable
private fun ChatInputBar(
    isGenerating: Boolean,
    isVisionModel: Boolean,
    onSend: (String, Uri?) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
        }
    }

    val canSend = (text.isNotBlank() || selectedImageUri != null) && !isGenerating

    val handleSend = {
        if (canSend) {
            val prompt = if (text.isBlank() && selectedImageUri != null) "Describe this image in detail." else text
            onSend(prompt, selectedImageUri)
            text = ""
            selectedImageUri = null
            keyboardController?.hide()
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
        ) {
            // Selected Image Preview Thumbnail Chip
            if (selectedImageUri != null) {
                val previewBitmap = remember(selectedImageUri) {
                    try {
                        context.contentResolver.openInputStream(selectedImageUri!!)?.use {
                            BitmapFactory.decodeStream(it)
                        }
                    } catch (_: Exception) {
                        null
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(6.dp)
                    ) {
                        if (previewBitmap != null) {
                            Image(
                                bitmap = previewBitmap.asImageBitmap(),
                                contentDescription = "Attached photo preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                "Photo attached for VLM",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = QwenPrimary
                            )
                            Text(
                                "Ready for multimodal analysis",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = { selectedImageUri = null },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove photo",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Photo Picker Button (enabled for vision models)
                if (isVisionModel) {
                    IconButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = !isGenerating,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            Icons.Default.AddPhotoAlternate,
                            contentDescription = "Attach photo",
                            tint = if (selectedImageUri != null) QwenPrimary else MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            when {
                                isGenerating -> "Generating…"
                                isVisionModel && selectedImageUri != null -> "Ask a question about this photo…"
                                isVisionModel -> "Ask Qwen or attach a photo…"
                                else -> "Ask Qwen anything…"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    },
                    enabled = !isGenerating,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = QwenPrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { handleSend() }),
                    maxLines = 4,
                )

                Spacer(Modifier.width(8.dp))

                FilledIconButton(
                    onClick = { handleSend() },
                    enabled = canSend,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = QwenPrimary,
                        contentColor = Color.White,
                        disabledContainerColor = QwenPrimary.copy(alpha = 0.3f),
                    ),
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp),
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}

