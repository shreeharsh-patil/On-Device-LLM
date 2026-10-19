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
    data class Error(
        val message: String,
        val title: String = "Engine Error",
        val errorCode: ModelErrorCode = ModelErrorCode.UNKNOWN,
        val canRequestPermission: Boolean = false,
        val canOpenFilePicker: Boolean = true,
        val candidateFile: File? = null
    ) : EngineState()
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ConversationRepository(application)
    private val modelRepository = ModelRepository(application)
    private val prefs = AppPreferences(application)
    private val memoryManager = com.example.on_device_qwen.domain.memory.MemoryManager(repository, prefs)
    private val engineFactory = InferenceEngineFactory(application)
    private val thermalManager = ThermalPerformanceManager(application)
    private val memoryMonitor = MemoryMonitor(application)
    private val ttsManager = com.example.on_device_qwen.domain.tts.TtsManager(application)

    private var activeEngine: InferenceEngine? = null
    private var generationJob: Job? = null
    private var activeGenerationId: String? = null

    val isSpeaking: StateFlow<Boolean> = ttsManager.isSpeaking

    // ── Memory State Flows ─────────────────────────────────────────────────────
    private val _persistentMemoryEnabled = MutableStateFlow(prefs.persistentMemoryEnabled)
    val persistentMemoryEnabled: StateFlow<Boolean> = _persistentMemoryEnabled.asStateFlow()

    private val _allMemories = MutableStateFlow<List<com.example.on_device_qwen.model.MemoryItem>>(emptyList())
    val allMemories: StateFlow<List<com.example.on_device_qwen.model.MemoryItem>> = _allMemories.asStateFlow()

    private val _lastUsedMemories = MutableStateFlow<List<com.example.on_device_qwen.model.MemoryItem>>(emptyList())
    val lastUsedMemories: StateFlow<List<com.example.on_device_qwen.model.MemoryItem>> = _lastUsedMemories.asStateFlow()

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

    private val _chatMode = MutableStateFlow(prefs.unrestrictedMode)
    val chatMode: StateFlow<UnrestrictedMode> = _chatMode.asStateFlow()

    private val _activeProfile = MutableStateFlow(prefs.activePromptProfile)
    val activeProfile: StateFlow<PromptProfile> = _activeProfile.asStateFlow()

    private val _profiles = MutableStateFlow(prefs.promptProfiles)
    val profiles: StateFlow<List<PromptProfile>> = _profiles.asStateFlow()

    private var lastValidationResult: List<com.example.on_device_qwen.domain.context.PromptValidationError> = emptyList()

    /** First blocking inference-parameter error, surfaced as a UI banner. */
    private val _validationBanner = MutableStateFlow<String?>(null)
    val validationBanner: StateFlow<String?> = _validationBanner.asStateFlow()

    init {
        loadConversations()
        loadMemories()
        initializeEngine()
        validateCurrentConfig()
    }

    fun validateCurrentConfig(): List<com.example.on_device_qwen.domain.context.PromptValidationError> {
        val config = currentGenerationConfig()
        lastValidationResult = com.example.on_device_qwen.domain.context.PromptValidation.validate(config)
        return lastValidationResult
    }

    fun currentGenerationConfig(): GenerationConfig {
        val threads = thermalManager.getAdaptiveThreadCount(_performanceMode.value, _threadCount.value)
            .coerceIn(GenerationConfig.MIN_THREAD_COUNT, GenerationConfig.MAX_THREAD_COUNT)
        val activeProfile = _activeProfile.value
        // Single source of truth: resolved sanitized prompt, never raw duplicated text.
        val resolvedPrompt = activeProfile.effectiveSystemPrompt
            .ifBlank { activeProfile.instructionText }
            .ifBlank { _systemPrompt.value }
        return GenerationConfig(
            temperature = _temperature.value.coerceIn(
                GenerationConfig.MIN_TEMPERATURE, GenerationConfig.MAX_TEMPERATURE,
            ),
            topP = _topP.value.coerceIn(GenerationConfig.MIN_TOP_P, GenerationConfig.MAX_TOP_P),
            repetitionPenalty = prefs.repetitionPenalty,
            maxOutputTokens = _maxTokens.value.coerceIn(
                GenerationConfig.MIN_MAX_TOKENS, GenerationConfig.MAX_MAX_TOKENS,
            ),
            contextLength = prefs.contextLength.coerceIn(
                GenerationConfig.MIN_CONTEXT_LENGTH, GenerationConfig.MAX_CONTEXT_LENGTH,
            ),
            systemPrompt = com.example.on_device_qwen.model.sanitizeSystemPrompt(resolvedPrompt),
            threadCount = threads,
            backend = _currentBackend.value,
            stopSequences = GenerationConfig.DEFAULT_STOP_SEQUENCES,
            unrestrictedMode = _chatMode.value,
            activePromptProfileId = activeProfile.id,
        )
    }

    /** Surfaced to Settings UI: current validation state for unsupported params. */
    fun validationErrors(): List<com.example.on_device_qwen.domain.context.PromptValidationError> =
        lastValidationResult.toList()

    fun setChatMode(mode: UnrestrictedMode) {
        _chatMode.value = mode
        prefs.unrestrictedMode = mode
        // Switching modes swaps the ACTIVE profile so the actual inference prompt
        // changes immediately for new/continued/regenerated responses.
        val targetId = if (mode == UnrestrictedMode.UNRESTRICTED) PromptProfile.UNRESTRICTED_ID
        else PromptProfile.DEFAULT_ID
        val profiles = _profiles.value.ifEmpty { prefs.promptProfiles }
        profiles.firstOrNull { it.id == targetId }?.let { setActiveProfile(it.id) }
        validateCurrentConfig().also { lastValidationResult = it }
    }

    fun setActiveProfile(profileId: String) {
        val profiles = _profiles.value.ifEmpty { prefs.promptProfiles }
        val match = profiles.firstOrNull { it.id == profileId } ?: return
        _activeProfile.value = match
        prefs.setActivePromptProfile(match.id)
        _systemPrompt.value = match.effectiveSystemPrompt.ifBlank { match.instructionText }
        prefs.systemPrompt = _systemPrompt.value
        validateCurrentConfig().also { lastValidationResult = it }
    }

    fun savePromptProfile(profile: PromptProfile): String? {
        val error = profile.validate()
        if (error != null) return error
        prefs.addOrUpdateProfile(profile)
        _profiles.value = prefs.promptProfiles
        if (profile.id == _activeProfile.value.id) {
            _activeProfile.value = profile.copy(
                effectiveSystemPrompt = com.example.on_device_qwen.model.sanitizeSystemPrompt(
                    profile.effectiveSystemPrompt.ifBlank { profile.instructionText }
                ),
            )
            _systemPrompt.value = _activeProfile.value.effectiveSystemPrompt
            prefs.systemPrompt = _systemPrompt.value
        }
        validateCurrentConfig().also { lastValidationResult = it }
        return null
    }

    fun deletePromptProfile(profileId: String) {
        prefs.removeProfile(profileId)
        _profiles.value = prefs.promptProfiles
        val current = _activeProfile.value
        if (current.id == profileId) {
            val fallback = _profiles.value.firstOrNull() ?: PromptProfile.standardDefault()
            _activeProfile.value = fallback
            prefs.setActivePromptProfile(fallback.id)
            _systemPrompt.value = fallback.effectiveSystemPrompt.ifBlank { fallback.instructionText }
        }
        validateCurrentConfig().also { lastValidationResult = it }
    }

    fun resetPromptProfile(profileId: String) {
        prefs.resetProfileToDefault(profileId)
        _profiles.value = prefs.promptProfiles
        _profiles.value.firstOrNull { it.id == profileId }?.let { restored ->
            if (restored.id == _activeProfile.value.id) {
                _activeProfile.value = restored
                _systemPrompt.value = restored.effectiveSystemPrompt
                prefs.systemPrompt = _systemPrompt.value
            }
        }
        validateCurrentConfig().also { lastValidationResult = it }
    }

    fun setRepetitionPenalty(value: Float) {
        prefs.repetitionPenalty = value.coerceIn(
            GenerationConfig.MIN_REPETITION_PENALTY, GenerationConfig.MAX_REPETITION_PENALTY,
        )
        validateCurrentConfig().also { lastValidationResult = it }
    }

    fun setContextLength(value: Int) {
        prefs.contextLength = value.coerceIn(
            GenerationConfig.MIN_CONTEXT_LENGTH, GenerationConfig.MAX_CONTEXT_LENGTH,
        )
        validateCurrentConfig().also { lastValidationResult = it }
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

        // BUG-015 FIX: Set Loading state synchronously BEFORE launching coroutine to
        // close the TOCTOU race window where two concurrent initializeEngine() calls
        // could both pass the guard check before either sets the state.
        _engineState.value = EngineState.Loading("Discovering models on device…")

        viewModelScope.launch {
            try {
                // Refresh memory stats
                val mem = memoryMonitor.getMemoryStatus()
                _availRamMb.value = mem.availRamMb
                _totalRamMb.value = mem.totalRamMb

                // 1. Scan available models across registered paths, app directories, Downloads, and Documents
                val allModels = modelRepository.scanModels()
                _discoveredModels.value = allModels
                _availableModels.value = allModels.map { it.file }

                // 2. Select target model: explicit request > active preference > first discovered model
                var targetFile = explicitModelFile?.takeIf { it.exists() && it.canRead() }
                    ?: prefs.activeModelPath?.let { File(it) }?.takeIf { it.exists() && it.canRead() }
                    ?: allModels.firstOrNull()?.file

                // 3. If still no target, run deep diagnosis (permission check, incomplete downloads, unregistered files)
                if (targetFile == null) {
                    val diag = modelRepository.diagnoseModelStatus()

                    // Auto-recovery: if diagnostic found candidate models, automatically register and select the first one!
                    if (diag.candidateFiles.isNotEmpty()) {
                        val recovered = diag.candidateFiles.firstOrNull { it.exists() && it.canRead() }
                        if (recovered != null) {
                            prefs.registerModelPath(recovered.absolutePath)
                            targetFile = recovered
                        }
                    }

                    if (targetFile == null) {
                        _engineState.value = EngineState.Error(
                            title = diag.title,
                            message = diag.message,
                            errorCode = diag.errorCode,
                            canRequestPermission = diag.canRequestPermission,
                            canOpenFilePicker = diag.canOpenFilePicker,
                            candidateFile = diag.candidateFiles.firstOrNull()
                        )
                        return@launch
                    }
                }

                // 4. Validate target file readability
                if (!targetFile.canRead()) {
                    _engineState.value = EngineState.Error(
                        title = "Storage Permission Required",
                        message = "The model file '${targetFile.name}' is unreadable. Android requires 'All files access' permission or file picker import to access it.",
                        errorCode = ModelErrorCode.FILE_UNREADABLE_OR_PERMISSION_DENIED,
                        canRequestPermission = true,
                        canOpenFilePicker = true,
                        candidateFile = targetFile
                    )
                    return@launch
                }

                // 5. Parse or retrieve metadata
                val targetMetadata = allModels.find { it.file.absolutePath == targetFile.absolutePath }
                    ?: modelRepository.parseMetadata(targetFile)
                    ?: ModelMetadata(
                        file = targetFile,
                        name = targetFile.nameWithoutExtension,
                        format = if (targetFile.extension.equals("gguf", true) || modelRepository.isGgufBinary(targetFile)) ModelFormat.GGUF else ModelFormat.LITERT,
                        engineType = if (targetFile.extension.equals("gguf", true) || modelRepository.isGgufBinary(targetFile)) EngineType.LLAMA_CPP else EngineType.LITERT,
                        fileSizeBytes = targetFile.length()
                    )

                // 6. Check RAM requirements before loading
                val requiredRamMb = targetMetadata.estimatedRamMb
                if (requiredRamMb > 0 && mem.availRamMb > 0 && mem.availRamMb < requiredRamMb / 2) {
                    _engineState.value = EngineState.Error(
                        title = "Insufficient RAM",
                        message = "Model '${targetMetadata.name}' requires approx ${requiredRamMb}MB RAM, but this device only has ${mem.availRamMb}MB available. Please close background apps or select a smaller model.",
                        errorCode = ModelErrorCode.INSUFFICIENT_RAM,
                        canOpenFilePicker = true
                    )
                    return@launch
                }

                _modelTitle.value = targetMetadata.name
                _isVisionModel.value = targetMetadata.hasVision
                prefs.activeModelPath = targetFile.absolutePath
                prefs.registerModelPath(targetFile.absolutePath)

                val backend = _currentBackend.value
                val threads = thermalManager.getAdaptiveThreadCount(_performanceMode.value, _threadCount.value)

                val config = currentGenerationConfig()

                _engineState.value = EngineState.Loading(
                    "Loading ${targetMetadata.name} (${targetMetadata.fileSizeMb} MB) on ${backend.chipText}…\nUsing $threads threads."
                )

                // 7. Unload previous engine before replacing it
                activeEngine?.unloadModel()
                val engine = engineFactory.getEngineForModel(targetFile)
                activeEngine = engine

                // 8. Initialize inference engine
                val loadResult = engine.loadModel(targetFile, config)
                if (loadResult.isSuccess && engine.isLoaded) {
                    // 9. Successfully verified! Transition to Ready
                    _engineState.value = EngineState.Ready
                } else {
                    val ex = loadResult.exceptionOrNull()
                    val errorCode = when (ex) {
                        is UnsupportedOperationException -> ModelErrorCode.MISSING_RUNTIME_OR_INCOMPATIBLE_ABI
                        is SecurityException -> ModelErrorCode.FILE_UNREADABLE_OR_PERMISSION_DENIED
                        is java.io.FileNotFoundException -> ModelErrorCode.MODEL_FILE_NOT_FOUND
                        else -> ModelErrorCode.MODEL_INITIALIZATION_FAILED
                    }
                    val title = when (errorCode) {
                        ModelErrorCode.MISSING_RUNTIME_OR_INCOMPATIBLE_ABI -> "Runtime Architecture Incompatible"
                        ModelErrorCode.FILE_UNREADABLE_OR_PERMISSION_DENIED -> "Storage Permission Denied"
                        ModelErrorCode.MODEL_FILE_NOT_FOUND -> "Model File Missing"
                        else -> "Model Initialization Failed"
                    }
                    _engineState.value = EngineState.Error(
                        title = title,
                        message = "Could not initialize ${targetMetadata.name}:\n${ex?.localizedMessage ?: "Unknown error"}",
                        errorCode = errorCode,
                        canRequestPermission = errorCode == ModelErrorCode.FILE_UNREADABLE_OR_PERMISSION_DENIED,
                        canOpenFilePicker = true
                    )
                }

            } catch (e: Exception) {
                _engineState.value = EngineState.Error(
                    title = "Engine Error",
                    message = "Unexpected error while setting up AI engine:\n${e.localizedMessage ?: e.toString()}",
                    errorCode = ModelErrorCode.UNKNOWN,
                    canOpenFilePicker = true
                )
            }
        }
    }

    fun onAppForegrounded() {
        if (_engineState.value is EngineState.Error || _engineState.value is EngineState.Idle) {
            initializeEngine()
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
     * Send user message and stream token chunks to UI with real-time telemetry,
     * conversation context, and persistent cross-chat memory retrieval.
     */
    fun sendMessage(userText: String, imageUri: Uri? = null, isRegeneration: Boolean = false) {
        val trimmed = userText.trim()
        if ((trimmed.isEmpty() && imageUri == null) || _isGenerating.value) return
        val engine = activeEngine ?: return
        if (!engine.isLoaded) return

        viewModelScope.launch {
            // Validate inference parameters BEFORE mutating chat state. Unsupported
            // or out-of-range values produce a clear, readable error instead of
            // silently reaching the native engine.
            val baseConfig = currentGenerationConfig()
            val validationErrors =
                com.example.on_device_qwen.domain.context.PromptValidation.validate(baseConfig)
            if (validationErrors.isNotEmpty()) {
                val detail = validationErrors.joinToString("\n") { "- ${it.message}" }
                val errorId = System.nanoTime()
                val userVisibleError = ChatMessage(
                    id = errorId,
                    conversationId = _activeConversation.value?.id ?: "",
                    text = "⚠️ Cannot generate — fix the highlighted inference settings first:\n$detail",
                    isUser = false,
                    isStreaming = false,
                    isError = true,
                )
                _messages.value = _messages.value + userVisibleError
                _validationBanner.value = validationErrors.first().message
                return@launch
            }
            _validationBanner.value = null
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
                _conversations.value = listOf(newSession) + _conversations.value
            }

            val session = currentSession
            val promptText = if (trimmed.isNotEmpty()) trimmed else "Describe this image in detail."
            val localImagePath = imageUri?.let {
                withContext(Dispatchers.IO) { saveUriToCache(it)?.absolutePath }
            }

            if (isRegeneration) {
                // BUG FIX: On regeneration, delete previous assistant message from DB and UI.
                // Do NOT insert a duplicate user message!
                repository.deleteLastAssistantMessage(session.id)
                val lastBot = _messages.value.lastOrNull { !it.isUser }
                if (lastBot != null) {
                    _messages.value = _messages.value.filterNot { it.id == lastBot.id }
                }
            } else {
                // Add new user message to UI and database
                val userMsg = ChatMessage(
                    conversationId = session.id,
                    text = promptText,
                    imagePath = localImagePath,
                    isUser = true
                )
                _messages.value = _messages.value + userMsg
                repository.saveMessage(userMsg)
            }

            // Assign unique generation ID to guarantee single authoritative stream reader
            val generationId = UUID.randomUUID().toString()
            activeGenerationId = generationId

            // Retrieve relevant cross-chat persistent memories (works in BOTH modes;
            // memories are delimited context, never trusted instructions).
            val memoryContext = memoryManager.retrieveMemoryContext(promptText, session.id)
            _lastUsedMemories.value = memoryContext.memories

            val config = currentGenerationConfig().copy(generationId = generationId)
            lastValidationResult =
                com.example.on_device_qwen.domain.context.PromptValidation.validate(config)

            // Build bounded, multi-turn conversation context. The active profile is
            // passed through so mode switches + custom prompts apply to new,
            // continued, AND regenerated responses. Switching chats passes a
            // different history list, so prompts/history can't leak across chats.
            val historyForPrompt = _messages.value
            val formattedPrompt = com.example.on_device_qwen.domain.context.PromptContextBuilder.buildChatMl(
                historyMessages = historyForPrompt,
                latestUserPrompt = promptText,
                memoryContext = memoryContext,
                config = config,
                promptProfile = _activeProfile.value,
                profiles = _profiles.value,
            )
            val estimatedPromptTokens = com.example.on_device_qwen.domain.context.PromptContextBuilder.estimateTokens(formattedPrompt)

            val botMsgId = System.nanoTime()
            val initialBotMsg = ChatMessage(
                id = botMsgId,
                conversationId = session.id,
                text = "",
                isUser = false,
                isStreaming = true,
                benchmark = BenchmarkMetrics(
                    backend = _currentBackend.value.chipText,
                    prefillTokens = estimatedPromptTokens
                )
            )
            _messages.value = _messages.value + initialBotMsg
            _isGenerating.value = true

            generationJob = viewModelScope.launch {
                try {
                    var lastUiUpdateMs = 0L
                    engine.sendMessage(formattedPrompt, localImagePath, config).collect { chunk ->
                        // Discard stale chunks from prior or cancelled generations
                        if (activeGenerationId != generationId || _activeConversation.value?.id != session.id) {
                            return@collect
                        }

                        val now = android.os.SystemClock.elapsedRealtime()
                        val isComplete = chunk.isComplete
                        if (isComplete || now - lastUiUpdateMs >= 50L) {
                            lastUiUpdateMs = now
                            val parsed = parseReasoningTags(chunk.text)

                            withContext(Dispatchers.Main) {
                                if (activeGenerationId != generationId) return@withContext
                                val current = _messages.value
                                val targetIdx = current.indexOfLast { it.id == botMsgId }
                                if (targetIdx >= 0) {
                                    val updated = current.toMutableList()
                                    updated[targetIdx] = updated[targetIdx].copy(
                                        text = parsed.answer,
                                        thinkText = parsed.think,
                                        isStreaming = !chunk.isComplete,
                                        benchmark = chunk.metrics
                                    )
                                    _messages.value = updated
                                }
                            }
                        }

                        if (chunk.isComplete) {
                            val finalMsg = _messages.value.find { it.id == botMsgId }
                            if (finalMsg != null) {
                                repository.saveMessage(finalMsg.copy(isStreaming = false))
                                // Asynchronously extract and consolidate persistent facts
                                viewModelScope.launch(Dispatchers.IO) {
                                    memoryManager.extractAndConsolidate(session.id, promptText, finalMsg.text)
                                    loadMemories()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (activeGenerationId == generationId) {
                        val errorMsg = ChatMessage(
                            id = botMsgId,
                            conversationId = session.id,
                            text = "⚠️ Error during generation: ${e.localizedMessage ?: e.toString()}",
                            isUser = false,
                            isStreaming = false,
                            isError = true
                        )
                        _messages.value = _messages.value.map { if (it.id == botMsgId) errorMsg else it }
                        repository.saveMessage(errorMsg)
                    }
                } finally {
                    if (activeGenerationId == generationId) {
                        _isGenerating.value = false
                        activeGenerationId = null
                    }
                    loadConversations()
                }
            }
        }
    }

    /**
     * Instantly halts token generation and releases native stream readers.
     */
    fun stopGeneration() {
        if (_isGenerating.value) {
            activeGenerationId = null
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
     * Regenerates the last assistant response without duplicating the user's prompt.
     */
    fun regenerateLastResponse() {
        if (_isGenerating.value) return
        val currentMsgs = _messages.value
        val lastUserMsg = currentMsgs.lastOrNull { it.isUser } ?: return

        sendMessage(
            userText = lastUserMsg.text,
            imageUri = lastUserMsg.imagePath?.let { Uri.fromFile(File(it)) },
            isRegeneration = true
        )
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
            _messages.value = repository.getMessages(currentSession.id).filterNot { it.id == messageId }
            sendMessage(newText, targetMsg.imagePath?.let { Uri.fromFile(File(it)) })
        }
    }

    // ── Persistent Memory Operations ──────────────────────────────────────────

    fun loadMemories() {
        viewModelScope.launch {
            _allMemories.value = repository.getAllMemories()
        }
    }

    fun togglePersistentMemory(enabled: Boolean) {
        _persistentMemoryEnabled.value = enabled
        prefs.persistentMemoryEnabled = enabled
    }

    fun saveMemory(item: com.example.on_device_qwen.model.MemoryItem) {
        viewModelScope.launch {
            repository.saveMemory(item)
            loadMemories()
        }
    }

    fun updateMemory(id: String, content: String, category: com.example.on_device_qwen.model.MemoryCategory? = null) {
        viewModelScope.launch {
            repository.updateMemory(id, content, category)
            loadMemories()
        }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch {
            repository.deleteMemory(id)
            loadMemories()
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            repository.clearAllMemories()
            loadMemories()
        }
    }

    fun searchMemories(query: String) {
        viewModelScope.launch {
            if (query.isBlank()) {
                _allMemories.value = repository.getAllMemories()
            } else {
                _allMemories.value = repository.searchMemories(query)
            }
        }
    }

    // ── Conversation Lifecycle ────────────────────────────────────────────────
    /** Resets engine state with the CURRENT mode prompt so chats never leak prompts. */
    private fun resetEngineForCurrentMode() {
        val engine = activeEngine
        val prompt = currentGenerationConfig().systemPrompt
        if (engine is com.example.on_device_qwen.domain.engine.LiteRTInferenceEngine) {
            engine.resetConversationWithSystemPrompt(prompt)
        } else {
            engine?.resetConversation()
        }
    }

    fun newChat() {
        if (_isGenerating.value) stopGeneration()
        resetEngineForCurrentMode()
        _activeConversation.value = null
        _messages.value = emptyList()
        _lastUsedMemories.value = emptyList()
    }

    fun selectConversation(session: ConversationSession) {
        if (_isGenerating.value) stopGeneration()
        resetEngineForCurrentMode()
        _activeConversation.value = session
        _lastUsedMemories.value = emptyList()
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
                // BUG-027 FIX: getOrNull()!! is contradictory — getOrNull avoids throws
                // but !! immediately re-introduces NPE. Use getOrElse instead.
                val file = result.getOrElse { return@launch }
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
        val clamped = count.coerceIn(GenerationConfig.MIN_THREAD_COUNT, GenerationConfig.MAX_THREAD_COUNT)
        _threadCount.value = clamped
        prefs.threadCount = clamped
        validateCurrentConfig().also { lastValidationResult = it }
    }

    fun setTemperature(temp: Float) {
        val clamped = temp.coerceIn(GenerationConfig.MIN_TEMPERATURE, GenerationConfig.MAX_TEMPERATURE)
        _temperature.value = clamped
        prefs.temperature = clamped
        validateCurrentConfig().also { lastValidationResult = it }
    }

    fun setTopP(p: Float) {
        val clamped = p.coerceIn(GenerationConfig.MIN_TOP_P, GenerationConfig.MAX_TOP_P)
        _topP.value = clamped
        prefs.topP = clamped
        validateCurrentConfig().also { lastValidationResult = it }
    }

    fun setMaxTokens(tokens: Int) {
        val clamped = tokens.coerceIn(GenerationConfig.MIN_MAX_TOKENS, GenerationConfig.MAX_MAX_TOKENS)
        _maxTokens.value = clamped
        prefs.maxOutputTokens = clamped
        validateCurrentConfig().also { lastValidationResult = it }
    }

    fun setActivePromptProfile(profile: PromptProfile) {
        setActiveProfile(profile.id)
    }

    fun updatePromptProfiles(profiles: List<PromptProfile>) {
        profiles.forEach { prefs.addOrUpdateProfile(it) }
        _profiles.value = prefs.promptProfiles
        validateCurrentConfig().also { lastValidationResult = it }
    }

    fun saveActiveProfile(profile: PromptProfile): String? = savePromptProfile(profile)

    fun setSystemPrompt(prompt: String) {
        val clean = com.example.on_device_qwen.model.sanitizeSystemPrompt(prompt)
        _systemPrompt.value = clean
        prefs.systemPrompt = clean
        // Keep the active profile in sync so the editor visibly changes inference.
        val current = _activeProfile.value
        val updated = current.copy(instructionText = clean, effectiveSystemPrompt = clean)
        prefs.addOrUpdateProfile(updated)
        _profiles.value = prefs.promptProfiles
        _activeProfile.value = updated
        validateCurrentConfig().also { lastValidationResult = it }
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.themeMode = mode
    }

    fun regenerationConfig(): GenerationConfig {
        // Regeneration must reuse the same chat mode / prompt profile as the original turn.
        return currentGenerationConfig()
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

    fun speak(text: String) {
        ttsManager.speak(text)
    }

    fun stopSpeaking() {
        ttsManager.stop()
    }

    fun getFollowUpSuggestions(lastAssistantMessage: String?): List<String> {
        if (lastAssistantMessage.isNullOrBlank()) return emptyList()
        val lower = lastAssistantMessage.lowercase()
        val suggestions = mutableListOf<String>()

        if (lower.contains("```") || lower.contains("fun ") || lower.contains("def ") || lower.contains("class ")) {
            suggestions.add("Explain how this code works")
            suggestions.add("Write unit tests for this")
            suggestions.add("Can you optimize this implementation?")
        } else if (lower.contains("diagram") || lower.contains("graph") || lower.contains("flowchart") || lower.contains("architecture")) {
            suggestions.add("Show this as a Mermaid flowchart")
            suggestions.add("Explain the system components step-by-step")
        } else if (lower.contains("table") || lower.contains("|")) {
            suggestions.add("Add more details to the comparison table")
            suggestions.add("What are the key trade-offs?")
        } else {
            suggestions.add("Explain in simpler terms")
            suggestions.add("Give a practical real-world example")
            suggestions.add("Summarize into 3 key takeaways")
        }
        return suggestions.take(3)
    }

    override fun onCleared() {
        super.onCleared()
        thermalManager.cleanup()
        ttsManager.shutdown()
        // BUG-001 FIX: viewModelScope is cancelled by super.onCleared() before any
        // new coroutine can execute inside it. Use runBlocking to synchronously unload
        // native engine handles, preventing JNI memory / file-lock leaks.
        try {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                activeEngine?.unloadModel()
            }
        } catch (_: Exception) {}
    }
}
