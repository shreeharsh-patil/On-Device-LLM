package com.example.on_device_qwen.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.on_device_qwen.data.preferences.AppPreferences
import com.example.on_device_qwen.data.preferences.PerformanceMode
import com.example.on_device_qwen.data.preferences.ThemeMode
import com.example.on_device_qwen.data.repository.ConversationRepository
import com.example.on_device_qwen.domain.engine.GgufMetadataParser
import com.example.on_device_qwen.domain.engine.InferenceEngine
import com.example.on_device_qwen.domain.engine.InferenceEngineFactory
import com.example.on_device_qwen.domain.modelmanager.ModelRepository
import com.example.on_device_qwen.domain.performance.DeviceThermalState
import com.example.on_device_qwen.domain.performance.MemoryMonitor
import com.example.on_device_qwen.domain.performance.ThermalPerformanceManager
import com.example.on_device_qwen.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

sealed class EngineState {
    data object Idle : EngineState()
    data class Loading(val progress: String) : EngineState()
    data object Ready : EngineState()
    data class Error(val message: String) : EngineState()
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ConversationRepository(application)
    private val modelRepository = ModelRepository(application)
    private val prefs = AppPreferences(application)
    private val engineFactory = InferenceEngineFactory(application)
    private val thermalManager = ThermalPerformanceManager(application)
    private val memoryMonitor = MemoryMonitor(application)

    private var activeEngine: InferenceEngine? = null
    private var generationJob: Job? = null

    // ── State Flows ───────────────────────────────────────────────────────────
    private val _engineState = MutableStateFlow<EngineState>(EngineState.Idle)
    val engineState: StateFlow<EngineState> = _engineState.asStateFlow()

    private val _conversations = MutableStateFlow<List<ConversationSession>>(emptyList())
    val conversations: StateFlow<List<ConversationSession>> = _conversations.asStateFlow()

    private val _activeConversation = MutableStateFlow<ConversationSession?>(null)
    val activeConversation: StateFlow<ConversationSession?> = _activeConversation.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _modelTitle = MutableStateFlow("Qwen")
    val modelTitle: StateFlow<String> = _modelTitle.asStateFlow()

    private val _discoveredModels = MutableStateFlow<List<ModelMetadata>>(emptyList())
    val discoveredModels: StateFlow<List<ModelMetadata>> = _discoveredModels.asStateFlow()

    private val _availableModels = MutableStateFlow<List<File>>(emptyList())
    val availableModels: StateFlow<List<File>> = _availableModels.asStateFlow()

    private val _currentBackend = MutableStateFlow(prefs.backendType)
    val currentBackend: StateFlow<BackendType> = _currentBackend.asStateFlow()

    private val _performanceMode = MutableStateFlow(prefs.performanceMode)
    val performanceMode: StateFlow<PerformanceMode> = _performanceMode.asStateFlow()

    private val _isVisionModel = MutableStateFlow(false)
    val isVisionModel: StateFlow<Boolean> = _isVisionModel.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _importProgress = MutableStateFlow<Float?>(null)
    val importProgress: StateFlow<Float?> = _importProgress.asStateFlow()

    // Telemetry & Hardware State
    val thermalState: StateFlow<DeviceThermalState> = thermalManager.thermalState
    val batteryTemp: StateFlow<Float> = thermalManager.batteryTempCelsius
    val batteryPercent: StateFlow<Int> = thermalManager.batteryLevelPercent
    val isCharging: StateFlow<Boolean> = thermalManager.isCharging

    private val _availRamMb = MutableStateFlow(memoryMonitor.getMemoryStatus().availRamMb)
    val availRamMb: StateFlow<Long> = _availRamMb.asStateFlow()

    private val _totalRamMb = MutableStateFlow(memoryMonitor.getMemoryStatus().totalRamMb)
    val totalRamMb: StateFlow<Long> = _totalRamMb.asStateFlow()

    // Preferences & Config State
    private val _temperature = MutableStateFlow(prefs.temperature)
    val temperature: StateFlow<Float> = _temperature.asStateFlow()

    private val _topP = MutableStateFlow(prefs.topP)
    val topP: StateFlow<Float> = _topP.asStateFlow()

    private val _maxTokens = MutableStateFlow(prefs.maxOutputTokens)
    val maxTokens: StateFlow<Int> = _maxTokens.asStateFlow()

    private val _systemPrompt = MutableStateFlow(prefs.systemPrompt)
    val systemPrompt: StateFlow<String> = _systemPrompt.asStateFlow()

    private val _threadCount = MutableStateFlow(prefs.threadCount)
    val threadCount: StateFlow<Int> = _threadCount.asStateFlow()

    private val _themeMode = MutableStateFlow(prefs.themeMode)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    init {
        loadConversations()
        initializeEngine()
    }

    private fun loadConversations() {
        viewModelScope.launch {
            val list = repository.getAllConversations()
            _conversations.value = list
            if (list.isNotEmpty() && _activeConversation.value == null) {
                selectConversation(list.first())
            }
        }
    }

    /**
     * Initializes the LLM inference engine with the active or discovered model.
     */
    fun initializeEngine(
        explicitModelFile: File? = null,
        requestedBackend: BackendType? = null
    ) {
        if (_engineState.value is EngineState.Loading) return

        requestedBackend?.let {
            _currentBackend.value = it
            prefs.backendType = it
        }

        viewModelScope.launch {
            _engineState.value = EngineState.Loading("Discovering models on device…")

            try {
                // Refresh memory stats
                val mem = memoryMonitor.getMemoryStatus()
                _availRamMb.value = mem.availRamMb
                _totalRamMb.value = mem.totalRamMb

                // Scan available models
                val allModels = modelRepository.scanModels()
                _discoveredModels.value = allModels
                _availableModels.value = allModels.map { it.file }

                val targetFile = explicitModelFile ?: prefs.activeModelPath?.let { File(it) }?.takeIf { it.exists() }
                    ?: allModels.firstOrNull()?.file

                if (targetFile == null) {
                    _engineState.value = EngineState.Error(
                        buildString {
                            appendLine("No compatible model file found!")
                            appendLine()
                            appendLine("Please push or import a .litertlm or .gguf model.")
                            appendLine("Supported:")
                            appendLine("  • Qwen 2.5 (0.5B, 1.5B, 3B, 7B)")
                            appendLine("  • Qwen 3 (0.6B, 1.7B, 4B)")
                            appendLine("  • Qwen 2-VL (Multimodal Vision)")
                            appendLine("  • Qwen 3 Abliterated GGUF")
                            appendLine()
                            appendLine("Use 'Import Model' in the Model Manager or push via ADB:")
                            appendLine("  adb push <model>.litertlm /sdcard/Download/")
                        }
                    )
                    return@launch
                }

                val targetMetadata = allModels.find { it.file.absolutePath == targetFile.absolutePath }
                    ?: GgufMetadataParser.parse(targetFile)
                    ?: ModelMetadata(
                        file = targetFile,
                        name = targetFile.nameWithoutExtension,
                        format = if (targetFile.extension.equals("gguf", true)) ModelFormat.GGUF else ModelFormat.LITERT,
                        engineType = if (targetFile.extension.equals("gguf", true)) EngineType.LLAMA_CPP else EngineType.LITERT,
                        fileSizeBytes = targetFile.length()
                    )

                _modelTitle.value = targetMetadata.name
                _isVisionModel.value = targetMetadata.hasVision
                prefs.activeModelPath = targetFile.absolutePath

                val backend = _currentBackend.value
                val threads = thermalManager.getAdaptiveThreadCount(_performanceMode.value, _threadCount.value)

                val config = GenerationConfig(
                    temperature = _temperature.value,
                    topP = _topP.value,
                    maxOutputTokens = _maxTokens.value,
                    contextLength = targetMetadata.contextLength,
                    systemPrompt = _systemPrompt.value,
                    threadCount = threads,
                    backend = backend
                )

                _engineState.value = EngineState.Loading(
                    "Loading ${targetMetadata.name} (${targetMetadata.fileSizeMb} MB) on ${backend.chipText}…\nUsing $threads threads."
                )

                // Select engine from factory
                val engine = engineFactory.getEngineForModel(targetFile)
                activeEngine = engine

                val loadResult = engine.loadModel(targetFile, config)
                if (loadResult.isSuccess) {
                    _engineState.value = EngineState.Ready
                } else {
                    _engineState.value = EngineState.Error(
                        "Failed to initialize engine:\n${loadResult.exceptionOrNull()?.localizedMessage ?: "Unknown error"}"
                    )
                }

            } catch (e: Exception) {
                _engineState.value = EngineState.Error(
                    "Engine error: ${e.localizedMessage ?: e.toString()}"
                )
            }
        }
    }

    fun switchModel(model: ModelMetadata) {
        if (_isGenerating.value) stopGeneration()
        initializeEngine(explicitModelFile = model.file)
    }

    fun switchModel(file: File) {
        if (_isGenerating.value) stopGeneration()
        initializeEngine(explicitModelFile = file)
    }

    fun switchBackend(backend: BackendType) {
        if (_isGenerating.value) return
        initializeEngine(requestedBackend = backend)
    }

    /**
     * Send user message and stream token chunks to UI with real-time telemetry.
     */
    fun sendMessage(userText: String, imageUri: Uri? = null) {
        val trimmed = userText.trim()
        if ((trimmed.isEmpty() && imageUri == null) || _isGenerating.value) return
        val engine = activeEngine ?: return
        if (!engine.isLoaded) return

        viewModelScope.launch {
            // Ensure an active conversation session exists in SQLite
            var currentSession = _activeConversation.value
            if (currentSession == null) {
                val newSession = ConversationSession(
                    id = UUID.randomUUID().toString(),
                    title = trimmed.take(30).ifEmpty { "Image Query" },
                    modelName = _modelTitle.value
                )
                repository.saveConversation(newSession)
                _activeConversation.value = newSession
                currentSession = newSession
                loadConversations()
            }

            // Save local cache image for VLM if attached
            val localImagePath = imageUri?.let { saveUriToCache(it)?.absolutePath }
            val promptText = if (trimmed.isNotEmpty()) trimmed else "Describe this image in detail."

            // Add user message to UI and database
            val userMsg = ChatMessage(
                conversationId = currentSession.id,
                text = promptText,
                imagePath = localImagePath,
                isUser = true
            )
            _messages.value = _messages.value + userMsg
            repository.saveMessage(userMsg)

            // Placeholder streaming assistant message
            val botMsgId = System.nanoTime()
            val initialBotMsg = ChatMessage(
                id = botMsgId,
                conversationId = currentSession.id,
                text = "",
                isUser = false,
                isStreaming = true,
                benchmark = BenchmarkMetrics(backend = _currentBackend.value.chipText)
            )
            _messages.value = _messages.value + initialBotMsg
            _isGenerating.value = true

            val threads = thermalManager.getAdaptiveThreadCount(_performanceMode.value, _threadCount.value)
            val config = GenerationConfig(
                temperature = _temperature.value,
                topP = _topP.value,
                maxOutputTokens = _maxTokens.value,
                systemPrompt = _systemPrompt.value,
                threadCount = threads,
                backend = _currentBackend.value
            )

            generationJob = launch {
                try {
                    var lastUiUpdateMs = 0L
                    engine.sendMessage(promptText, localImagePath, config).collect { chunk ->
                        val now = android.os.SystemClock.elapsedRealtime()
                        val isComplete = chunk.isComplete
                        if (isComplete || now - lastUiUpdateMs >= 40L) {
                            lastUiUpdateMs = now
                            val parsed = parseReasoningTags(chunk.text)

                            withContext(Dispatchers.Main) {
                                _messages.value = _messages.value.map { msg ->
                                    if (msg.id == botMsgId) {
                                        msg.copy(
                                            text = parsed.answer,
                                            thinkText = parsed.think,
                                            isStreaming = !chunk.isComplete,
                                            benchmark = chunk.metrics
                                        )
                                    } else msg
                                }
                            }
                        }

                        if (chunk.isComplete) {
                            val finalMsg = _messages.value.find { it.id == botMsgId }
                            if (finalMsg != null) {
                                repository.saveMessage(finalMsg.copy(isStreaming = false))
                            }
                        }
                    }
                } catch (e: Exception) {
                    val errorMsg = ChatMessage(
                        id = botMsgId,
                        conversationId = currentSession.id,
                        text = "⚠️ Error during generation: ${e.localizedMessage ?: e.toString()}",
                        isUser = false,
                        isStreaming = false,
                        isError = true
                    )
                    _messages.value = _messages.value.map { if (it.id == botMsgId) errorMsg else it }
                    repository.saveMessage(errorMsg)
                } finally {
                    _isGenerating.value = false
                    loadConversations()
                }
            }
        }
    }

    /**
     * Instantly halts token generation.
     */
    fun stopGeneration() {
        if (_isGenerating.value) {
            generationJob?.cancel()
            activeEngine?.cancelGeneration()
            _isGenerating.value = false

            // Mark any streaming message as complete
            _messages.value = _messages.value.map {
                if (it.isStreaming) it.copy(isStreaming = false) else it
            }
        }
    }

    /**
     * Regenerates the last assistant response.
     */
    fun regenerateLastResponse() {
        if (_isGenerating.value) return
        val currentMsgs = _messages.value
        val lastUserMsg = currentMsgs.lastOrNull { it.isUser } ?: return

        // Remove the last assistant message
        val updatedMsgs = currentMsgs.filterNot { it.id == currentMsgs.lastOrNull()?.id && !it.isUser }
        _messages.value = updatedMsgs

        sendMessage(lastUserMsg.text, lastUserMsg.imagePath?.let { Uri.fromFile(File(it)) })
    }

    /**
     * Edit a previous user message and re-run conversation from that point.
     */
    fun editAndResendMessage(messageId: Long, newText: String) {
        if (_isGenerating.value) return
        val currentSession = _activeConversation.value ?: return

        viewModelScope.launch {
            val targetMsg = _messages.value.find { it.id == messageId } ?: return@launch
            repository.deleteMessagesAfter(currentSession.id, targetMsg.timestamp)
            _messages.value = repository.getMessages(currentSession.id)
            sendMessage(newText)
        }
    }

    // ── Conversation Lifecycle ────────────────────────────────────────────────
    fun newChat() {
        if (_isGenerating.value) stopGeneration()
        _activeConversation.value = null
        _messages.value = emptyList()
    }

    fun selectConversation(session: ConversationSession) {
        if (_isGenerating.value) stopGeneration()
        _activeConversation.value = session
        viewModelScope.launch {
            _messages.value = repository.getMessages(session.id)
        }
    }

    fun renameConversation(session: ConversationSession, newTitle: String) {
        viewModelScope.launch {
            repository.updateConversationTitle(session.id, newTitle)
            loadConversations()
            if (_activeConversation.value?.id == session.id) {
                _activeConversation.value = _activeConversation.value?.copy(title = newTitle)
            }
        }
    }

    fun deleteConversation(session: ConversationSession) {
        viewModelScope.launch {
            repository.deleteConversation(session.id)
            if (_activeConversation.value?.id == session.id) {
                newChat()
            }
            loadConversations()
        }
    }

    fun togglePin(session: ConversationSession) {
        viewModelScope.launch {
            repository.togglePinConversation(session.id, !session.isPinned)
            loadConversations()
        }
    }

    fun searchConversations(query: String) {
        _searchQuery.value = query
        viewModelScope.launch {
            _conversations.value = repository.searchConversations(query)
        }
    }

    fun clearChat() {
        val currentSession = _activeConversation.value
        if (currentSession != null) {
            deleteConversation(currentSession)
        } else {
            newChat()
        }
    }

    // ── Model Importing ───────────────────────────────────────────────────────
    fun importModel(uri: Uri) {
        viewModelScope.launch {
            _importProgress.value = 0f
            val result = modelRepository.importModelFromUri(uri) { prog ->
                _importProgress.value = prog
            }
            _importProgress.value = null

            if (result.isSuccess) {
                val file = result.getOrNull()!!
                initializeEngine(explicitModelFile = file)
            }
        }
    }

    // ── Settings Updates ──────────────────────────────────────────────────────
    fun setPerformanceMode(mode: PerformanceMode) {
        _performanceMode.value = mode
        prefs.performanceMode = mode
    }

    fun setThreadCount(count: Int) {
        _threadCount.value = count
        prefs.threadCount = count
    }

    fun setTemperature(temp: Float) {
        _temperature.value = temp
        prefs.temperature = temp
    }

    fun setTopP(p: Float) {
        _topP.value = p
        prefs.topP = p
    }

    fun setMaxTokens(tokens: Int) {
        _maxTokens.value = tokens
        prefs.maxOutputTokens = tokens
    }

    fun setSystemPrompt(prompt: String) {
        _systemPrompt.value = prompt
        prefs.systemPrompt = prompt
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.themeMode = mode
    }

    private fun saveUriToCache(uri: Uri): File? {
        return try {
            val app = getApplication<Application>()
            val targetFile = File(app.cacheDir, "vlm_input_${System.currentTimeMillis()}.jpg")
            app.contentResolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            targetFile
        } catch (_: Exception) {
            null
        }
    }

    private data class ParsedResponse(val think: String?, val answer: String)

    private fun parseReasoningTags(raw: String): ParsedResponse {
        val openTag = "<think>"
        val closeTag = "</think>"
        val openIdx = raw.indexOf(openTag)
        if (openIdx == -1) return ParsedResponse(null, raw)

        val afterOpen = raw.substring(openIdx + openTag.length)
        val closeIdx = afterOpen.indexOf(closeTag)
        return if (closeIdx == -1) {
            ParsedResponse(afterOpen.trim(), "")
        } else {
            val think = afterOpen.substring(0, closeIdx).trim()
            val answer = afterOpen.substring(closeIdx + closeTag.length).trimStart('\n', '\r', ' ')
            ParsedResponse(think.ifEmpty { null }, answer)
        }
    }

    override fun onCleared() {
        super.onCleared()
        thermalManager.cleanup()
        viewModelScope.launch {
            activeEngine?.unloadModel()
        }
    }
}
