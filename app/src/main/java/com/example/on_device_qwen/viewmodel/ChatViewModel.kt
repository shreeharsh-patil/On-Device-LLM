package com.example.on_device_qwen.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.on_device_qwen.data.preferences.AppPreferences
import com.example.on_device_qwen.data.preferences.PerformanceMode
import com.example.on_device_qwen.data.preferences.ThemeMode
import com.example.on_device_qwen.data.repository.ConversationRepository
import com.example.on_device_qwen.domain.context.PromptContextBuilder
import com.example.on_device_qwen.domain.context.PromptValidation
import com.example.on_device_qwen.domain.context.PromptValidationError
import com.example.on_device_qwen.domain.engine.EngineCapabilities
import com.example.on_device_qwen.domain.engine.InferenceEngine
import com.example.on_device_qwen.domain.engine.InferenceEngineFactory
import com.example.on_device_qwen.domain.memory.MemoryManager
import com.example.on_device_qwen.domain.modelmanager.ImportProgress
import com.example.on_device_qwen.domain.modelmanager.ModelImportState
import com.example.on_device_qwen.domain.modelmanager.ModelRepository
import com.example.on_device_qwen.domain.performance.DeviceThermalState
import com.example.on_device_qwen.domain.performance.MemoryMonitor
import com.example.on_device_qwen.domain.performance.ThermalPerformanceManager
import com.example.on_device_qwen.domain.tts.TtsManager
import com.example.on_device_qwen.model.BackendType
import com.example.on_device_qwen.model.BenchmarkMetrics
import com.example.on_device_qwen.model.ChatMessage
import com.example.on_device_qwen.model.ConversationSession
import com.example.on_device_qwen.model.EngineType
import com.example.on_device_qwen.model.ExecutionBackendInfo
import com.example.on_device_qwen.model.GenerationConfig
import com.example.on_device_qwen.model.MemoryCategory
import com.example.on_device_qwen.model.MemoryItem
import com.example.on_device_qwen.model.MessageIdGenerator
import com.example.on_device_qwen.model.ModelCompatibilityStatus
import com.example.on_device_qwen.model.ModelErrorCode
import com.example.on_device_qwen.model.ModelMetadata
import com.example.on_device_qwen.model.PromptProfile
import com.example.on_device_qwen.model.UnrestrictedMode
import com.example.on_device_qwen.model.sanitizeSystemPrompt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal data class ChatViewModelDependencies(
    val repository: ConversationRepository,
    val modelHub: ModelHubCoordinator,
    val prefs: AppPreferences,
    val memoryManager: MemoryManager,
    val engineProvider: (File) -> InferenceEngine,
    val thermalStateFlow: StateFlow<DeviceThermalState>,
    val batteryTempFlow: StateFlow<Float>,
    val batteryPercentFlow: StateFlow<Int>,
    val isChargingFlow: StateFlow<Boolean>,
    val adaptiveThreadCountFn: (PerformanceMode, Int) -> Int,
    val cleanupThermalFn: () -> Unit,
    val speakFn: (String) -> Unit,
    val stopSpeakFn: () -> Unit,
    val shutdownTtsFn: () -> Unit,
    val isSpeakingFlow: StateFlow<Boolean>,
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    val autoInitialize: Boolean = true,
    val clockMs: () -> Long = { SystemClock.elapsedRealtime() },
    val streamThrottleMs: Long = 45L,
    val uriCacheCopier: (Uri) -> File? = { null },
) {
    companion object {
        fun production(application: Application): ChatViewModelDependencies {
            val repository = ConversationRepository(application)
            val prefs = AppPreferences(application)
            val modelRepository = ModelRepository(application)
            val memoryMonitor = MemoryMonitor(application)
            val thermalManager = ThermalPerformanceManager(application)
            val ttsManager = TtsManager(application)
            val engineFactory = InferenceEngineFactory(application)
            return ChatViewModelDependencies(
                repository = repository,
                modelHub = ModelHubCoordinator(application, modelRepository, memoryMonitor),
                prefs = prefs,
                memoryManager = MemoryManager(repository, prefs),
                engineProvider = { file -> engineFactory.getEngineForModel(file) },
                thermalStateFlow = thermalManager.thermalState,
                batteryTempFlow = thermalManager.batteryTempCelsius,
                batteryPercentFlow = thermalManager.batteryLevelPercent,
                isChargingFlow = thermalManager.isCharging,
                adaptiveThreadCountFn = { mode, threads ->
                    thermalManager.getAdaptiveThreadCount(mode, threads)
                },
                cleanupThermalFn = { thermalManager.cleanup() },
                speakFn = { text -> ttsManager.speak(text) },
                stopSpeakFn = { ttsManager.stop() },
                shutdownTtsFn = { ttsManager.shutdown() },
                isSpeakingFlow = ttsManager.isSpeaking,
                uriCacheCopier = { uri -> copyImageUriToCache(application, uri) },
            )
        }

        private fun copyImageUriToCache(app: Application, uri: Uri): File? = try {
            val targetFile = File(app.cacheDir, "vlm_input_${MessageIdGenerator.nextId()}.jpg")
            app.contentResolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output -> input.copyTo(output) }
            }
            if (targetFile.exists() && targetFile.length() > 0L) targetFile else null
        } catch (_: Exception) {
            null
        }
    }
}

class ChatViewModel private constructor(
    application: Application,
    private val deps: ChatViewModelDependencies,
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application,
        ChatViewModelDependencies.production(application),
    )

    companion object {
        private const val UI_PREFS_NAME = "on_device_qwen_prefs"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val DEFAULT_MODEL_PLACEHOLDER = "Select Model"
        private const val SEARCH_DEBOUNCE_MS = 180L

        @VisibleForTesting
        internal fun createForTest(
            application: Application,
            dependencies: ChatViewModelDependencies,
        ): ChatViewModel = ChatViewModel(application, dependencies)
    }

    private val uiPrefs by lazy {
        application.getSharedPreferences(UI_PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Serialization & concurrency primitives
    private val engineMutex = Mutex()
    private val sendSlotClaimed = AtomicBoolean(false)
    private val loadRequestSeq = AtomicLong(0L)
    private val stateLock = Any()

    private var activeEngine: InferenceEngine? = null
    private var generationJob: Job? = null
    private var conversationLoadJob: Job? = null
    private var searchConversationsJob: Job? = null
    private var searchMemoriesJob: Job? = null
    private var activeGenerationId: String? = null

    // Backing domain state fields (protected for consistent publication via stateLock)
    private var engineStateValue: EngineState = EngineState.Idle
    private var modelTitleValue: String = DEFAULT_MODEL_PLACEHOLDER
    private var activeModelMetadataValue: ModelMetadata? = null
    private var executionBackendInfoValue: ExecutionBackendInfo? = null
    private var engineCapabilitiesValue: EngineCapabilities? = null
    private var isVisionSupportedValue: Boolean = false
    private var visionUnsupportedReasonValue: String? = null
    private var isGeneratingValue: Boolean = false
    private var isSpeakingValue: Boolean = deps.isSpeakingFlow.value

    private var discoveredModelsValue: List<ModelMetadata> = emptyList()
    private var importStateValue: ModelImportState = ModelImportState()
    private var importErrorValue: String? = null
    private var isScanningValue: Boolean = false

    private var conversationsValue: List<ConversationSession> = emptyList()
    private var activeConversationValue: ConversationSession? = null
    private var messagesValue: List<ChatMessage> = emptyList()
    private var conversationSearchQuery: String = ""

    private var persistentMemoryEnabledValue: Boolean = deps.prefs.persistentMemoryEnabled
    private var allMemoriesMasterValue: List<MemoryItem> = emptyList()
    private var displayedMemoriesValue: List<MemoryItem> = emptyList()
    private var lastUsedMemoriesValue: List<MemoryItem> = emptyList()
    private var memorySearchQuery: String = ""
    private var selectedMemoryCategory: MemoryCategory? = null

    private var currentBackendValue: BackendType = deps.prefs.backendType
    private var performanceModeValue: PerformanceMode = deps.prefs.performanceMode
    private var temperatureValue: Float = deps.prefs.temperature
    private var topPValue: Float = deps.prefs.topP
    private var repetitionPenaltyValue: Float = deps.prefs.repetitionPenalty
    private var maxTokensValue: Int = deps.prefs.maxOutputTokens
    private var contextLengthValue: Int = deps.prefs.contextLength
    private var systemPromptValue: String = deps.prefs.systemPrompt
    private var threadCountValue: Int = deps.prefs.threadCount
    private var themeModeValue: ThemeMode = deps.prefs.themeMode
    private var hapticsEnabledValue: Boolean = deps.prefs.hapticsEnabled
    private var chatModeValue: UnrestrictedMode = deps.prefs.unrestrictedMode
    private var activeProfileValue: PromptProfile = deps.prefs.activePromptProfile
    private var profilesValue: List<PromptProfile> = deps.prefs.promptProfiles

    private var validationErrorsValue: List<PromptValidationError> = emptyList()
    private var validationBannerValue: String? = null
    private var backendFallbackBannerValue: String? = null
    private var transientErrorBannerValue: String? = null
    private var onboardingCompletedValue: Boolean = uiPrefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    private var onboardingExplicitlyShown: Boolean = false
    private var hardwareInfoValue: DeviceHardwareInfo = sampleHardwareInfo()

    // Consolidated @Immutable UI StateFlows
    private val _themeMode = MutableStateFlow(themeModeValue)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _hapticsEnabled = MutableStateFlow(hapticsEnabledValue)
    val hapticsEnabled: StateFlow<Boolean> = _hapticsEnabled.asStateFlow()

    private val _chatUiState = MutableStateFlow(buildChatUiState())
    val chatUiState: StateFlow<ChatScreenUiState> = _chatUiState.asStateFlow()

    private val _modelHubUiState = MutableStateFlow(buildModelHubUiState())
    val modelHubUiState: StateFlow<ModelHubUiState> = _modelHubUiState.asStateFlow()

    private val _settingsUiState = MutableStateFlow(buildSettingsUiState())
    val settingsUiState: StateFlow<SettingsUiState> = _settingsUiState.asStateFlow()

    private val _memoryUiState = MutableStateFlow(buildMemoryUiState())
    val memoryUiState: StateFlow<MemoryUiState> = _memoryUiState.asStateFlow()

    private val _navigationUiState = MutableStateFlow(buildNavigationUiState())
    val navigationUiState: StateFlow<NavigationUiState> = _navigationUiState.asStateFlow()

    init {
        observeHardwareTelemetry()
        validateCurrentConfig()
        loadConversations()
        loadMemories()
        if (deps.autoInitialize) {
            initializeEngine()
        }
    }

    private fun observeHardwareTelemetry() {
        viewModelScope.launch {
            deps.isSpeakingFlow.collect { speaking ->
                synchronized(stateLock) { isSpeakingValue = speaking }
                publishUiStates()
            }
        }
        viewModelScope.launch {
            combine(
                deps.thermalStateFlow,
                deps.batteryTempFlow,
                deps.batteryPercentFlow,
                deps.isChargingFlow,
            ) { _, _, _, _ -> Unit }.collect {
                refreshDeviceStatusInternal()
            }
        }
    }

    private fun sampleHardwareInfo(): DeviceHardwareInfo {
        val caps = engineCapabilitiesValue
        return deps.modelHub.readHardwareInfo(
            thermalState = deps.thermalStateFlow.value,
            batteryTempCelsius = deps.batteryTempFlow.value,
            batteryPercent = deps.batteryPercentFlow.value,
            isCharging = deps.isChargingFlow.value,
            gpuSupported = caps?.supportsGpu ?: false,
            npuSupported = caps?.supportsNpu ?: false,
        )
    }

    fun refreshDeviceStatus() {
        refreshDeviceStatusInternal()
    }

    private fun refreshDeviceStatusInternal() {
        val hw = sampleHardwareInfo()
        synchronized(stateLock) {
            hardwareInfoValue = hw
            discoveredModelsValue = discoveredModelsValue.map {
                deps.modelHub.assessCompatibility(it, hw.availRamMb, hw.totalRamMb)
            }
        }
        publishUiStates()
    }

    // ── Onboarding ────────────────────────────────────────────────────────────

    fun completeOnboarding() {
        uiPrefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
        synchronized(stateLock) {
            onboardingCompletedValue = true
            onboardingExplicitlyShown = false
        }
        publishUiStates()
    }

    fun openOnboarding() {
        refreshDeviceStatusInternal()
        synchronized(stateLock) {
            onboardingExplicitlyShown = true
        }
        publishUiStates()
    }

    // ── Engine & Model Lifecycle (Strictly Serialized) ────────────────────────

    fun initializeEngine(
        explicitModelFile: File? = null,
        requestedBackend: BackendType? = null,
    ) {
        val mySeq = loadRequestSeq.incrementAndGet()
        requestedBackend?.let { backend ->
            synchronized(stateLock) { currentBackendValue = backend }
            deps.prefs.backendType = backend
        }

        val initialProgress = if (explicitModelFile != null) {
            "Preparing ${explicitModelFile.nameWithoutExtension}…"
        } else {
            "Discovering local models…"
        }
        synchronized(stateLock) {
            engineStateValue = EngineState.Loading(initialProgress)
        }
        publishUiStates()

        viewModelScope.launch {
            stopGenerationAndJoin()

            engineMutex.withLock {
                if (mySeq != loadRequestSeq.get()) return@withLock

                try {
                    val hw = sampleHardwareInfo()
                    synchronized(stateLock) {
                        hardwareInfoValue = hw
                        isScanningValue = true
                    }
                    publishUiStates()

                    val allModels = deps.modelHub.scanAndAssessModels(hw.availRamMb, hw.totalRamMb)
                    synchronized(stateLock) {
                        discoveredModelsValue = allModels
                        isScanningValue = false
                    }

                    var targetFile = explicitModelFile?.takeIf { it.exists() && it.canRead() }
                        ?: deps.prefs.activeModelPath?.let(::File)?.takeIf { it.exists() && it.canRead() }
                        ?: allModels.firstOrNull()?.file

                    if (targetFile == null) {
                        val diag = deps.modelHub.diagnoseMissingModels()
                        val recovered = diag.candidateFiles.firstOrNull { it.exists() && it.canRead() }
                        if (recovered != null) {
                            deps.prefs.registerModelPath(recovered.absolutePath)
                            targetFile = recovered
                        } else {
                            // Unload any previously active handle safely before entering Idle/Error
                            unloadActiveEngineLocked()
                            synchronized(stateLock) {
                                engineStateValue = if (diag.errorCode == ModelErrorCode.MODEL_FILE_NOT_FOUND) {
                                    EngineState.Idle
                                } else {
                                    EngineState.Error(
                                        title = diag.title,
                                        message = "This model file cannot be read directly due to Android scoped storage. Use 'Import Model' via the system file picker instead.",
                                        errorCode = diag.errorCode,
                                        canRequestPermission = false,
                                        canOpenFilePicker = true,
                                        candidateFile = diag.candidateFiles.firstOrNull(),
                                    )
                                }
                            }
                            publishUiStates()
                            return@withLock
                        }
                    }

                    if (!targetFile.canRead()) {
                        unloadActiveEngineLocked()
                        synchronized(stateLock) {
                            engineStateValue = EngineState.Error(
                                title = "File Access Unavailable",
                                message = "Cannot read '${targetFile.name}' from its current folder. Import it with the system file picker so the app receives persistent scoped access.",
                                errorCode = ModelErrorCode.FILE_UNREADABLE_OR_PERMISSION_DENIED,
                                canRequestPermission = false,
                                canOpenFilePicker = true,
                                candidateFile = targetFile,
                            )
                        }
                        publishUiStates()
                        return@withLock
                    }

                    val targetMetadata = allModels.find { canonicalPathOf(it.file) == canonicalPathOf(targetFile) }
                        ?: deps.modelHub.assessCompatibility(
                            deps.modelHub.parseOrFallbackMetadata(targetFile),
                            hw.availRamMb,
                            hw.totalRamMb,
                        )

                    if (targetMetadata.isCorrupted ||
                        targetMetadata.compatibilityStatus == ModelCompatibilityStatus.CORRUPTED
                    ) {
                        unloadActiveEngineLocked()
                        synchronized(stateLock) {
                            engineStateValue = EngineState.Error(
                                title = "Model File Corrupted",
                                message = targetMetadata.compatibilityMessage
                                    ?: "'${targetMetadata.name}' failed format validation and cannot be loaded.",
                                errorCode = ModelErrorCode.MODEL_INITIALIZATION_FAILED,
                                canOpenFilePicker = true,
                            )
                        }
                        publishUiStates()
                        return@withLock
                    }

                    // Unload old native engine BEFORE measuring available RAM and loading new model
                    unloadActiveEngineLocked()
                    val refreshedHw = sampleHardwareInfo()
                    synchronized(stateLock) { hardwareInfoValue = refreshedHw }

                    val requiredRamMb = targetMetadata.estimatedRamMb.coerceAtLeast(targetMetadata.minRamMb)
                    if (requiredRamMb > 0L && refreshedHw.availRamMb > 0L &&
                        refreshedHw.availRamMb < requiredRamMb / 2L
                    ) {
                        synchronized(stateLock) {
                            engineStateValue = EngineState.Error(
                                title = "Insufficient RAM",
                                message = "Model '${targetMetadata.name}' needs ~${requiredRamMb} MB RAM, while ${refreshedHw.availRamMb} MB is currently available. Close background apps or choose a smaller model.",
                                errorCode = ModelErrorCode.INSUFFICIENT_RAM,
                                canOpenFilePicker = true,
                            )
                        }
                        publishUiStates()
                        return@withLock
                    }

                    val requestedBackendSnapshot = synchronized(stateLock) { currentBackendValue }
                    val resolvedBackend = requestedBackendSnapshot.resolveForEngine(targetMetadata.engineType)
                    val config = currentGenerationConfig().copy(backend = resolvedBackend)
                    val threads = deps.adaptiveThreadCountFn(performanceModeValue, threadCountValue)

                    synchronized(stateLock) {
                        engineStateValue = EngineState.Loading(
                            "Loading ${targetMetadata.name} (${targetMetadata.fileSizeMb} MB) · ${resolvedBackend.chipText} · $threads threads…",
                        )
                    }
                    publishUiStates()

                    val engine = deps.engineProvider(targetFile)
                    val loadResult = withContext(deps.ioDispatcher) {
                        engine.loadModelWithResult(targetFile.absolutePath, resolvedBackend, config)
                    }

                    // If a newer switchModel/switchBackend arrived while loading, discard this engine cleanly.
                    if (mySeq != loadRequestSeq.get()) {
                        withContext(NonCancellable + deps.ioDispatcher) {
                            try { engine.unloadModel() } catch (_: Exception) {}
                        }
                        return@withLock
                    }

                    if (loadResult.success && engine.isLoaded) {
                        activeEngine = engine
                        val backendInfo = loadResult.backendInfo
                        val caps = loadResult.capabilities
                        deps.prefs.activeModelPath = targetFile.absolutePath
                        deps.prefs.registerModelPath(targetFile.absolutePath)
                        if (!onboardingCompletedValue) {
                            uiPrefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
                        }

                        val fallbackMsg = if (backendInfo.isFallback) {
                            buildString {
                                append("Requested ${backendInfo.requested.chipText}; running on ${backendInfo.active.chipText}")
                                backendInfo.fallbackReason?.takeIf { it.isNotBlank() }?.let { append(" ($it)") }
                            }
                        } else null

                        synchronized(stateLock) {
                            activeModelMetadataValue = targetMetadata
                            modelTitleValue = targetMetadata.name
                            engineCapabilitiesValue = caps
                            executionBackendInfoValue = backendInfo
                            currentBackendValue = backendInfo.active
                            // Vision support derives strictly from verified engine capabilities, never filename
                            isVisionSupportedValue = caps.supportsVision
                            visionUnsupportedReasonValue = caps.unsupportedVisionReason
                            backendFallbackBannerValue = fallbackMsg
                            onboardingCompletedValue = true
                            onboardingExplicitlyShown = false
                            hardwareInfoValue = sampleHardwareInfo()
                            engineStateValue = EngineState.Ready
                        }
                        validateCurrentConfig()
                    } else {
                        withContext(NonCancellable + deps.ioDispatcher) {
                            try { engine.unloadModel() } catch (_: Exception) {}
                        }
                        val code = loadResult.errorCode ?: ModelErrorCode.MODEL_INITIALIZATION_FAILED
                        val title = when (code) {
                            ModelErrorCode.MISSING_RUNTIME_OR_INCOMPATIBLE_ABI -> "Runtime Architecture Incompatible"
                            ModelErrorCode.FILE_UNREADABLE_OR_PERMISSION_DENIED -> "File Access Unavailable"
                            ModelErrorCode.MODEL_FILE_NOT_FOUND -> "Model File Missing"
                            ModelErrorCode.INSUFFICIENT_RAM -> "Insufficient RAM"
                            else -> "Model Initialization Failed"
                        }
                        synchronized(stateLock) {
                            engineStateValue = EngineState.Error(
                                title = title,
                                message = "Could not initialize ${targetMetadata.name}: ${loadResult.errorMessage ?: "Unknown error"}",
                                errorCode = code,
                                canRequestPermission = false,
                                canOpenFilePicker = true,
                            )
                        }
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    unloadActiveEngineLocked()
                    synchronized(stateLock) {
                        engineStateValue = EngineState.Error(
                            title = "Engine Error",
                            message = "Unexpected error while setting up engine: ${e.localizedMessage ?: e.toString()}",
                            errorCode = ModelErrorCode.UNKNOWN,
                            canOpenFilePicker = true,
                        )
                    }
                } finally {
                    synchronized(stateLock) { isScanningValue = false }
                    publishUiStates()
                }
            }
        }
    }

    private suspend fun unloadActiveEngineLocked() {
        val current = activeEngine
        activeEngine = null
        if (current != null) {
            withContext(NonCancellable + deps.ioDispatcher) {
                try { current.unloadModel() } catch (_: Exception) {}
            }
        }
        synchronized(stateLock) {
            activeModelMetadataValue = null
            executionBackendInfoValue = null
            engineCapabilitiesValue = null
            isVisionSupportedValue = false
            visionUnsupportedReasonValue = null
            backendFallbackBannerValue = null
            modelTitleValue = DEFAULT_MODEL_PLACEHOLDER
        }
    }

    fun onAppForegrounded() {
        refreshDeviceStatusInternal()
        val state = synchronized(stateLock) { engineStateValue }
        if (state is EngineState.Error || state is EngineState.Idle) {
            initializeEngine()
        }
    }

    fun switchModel(model: ModelMetadata) {
        initializeEngine(explicitModelFile = model.file)
    }

    fun switchModel(file: File) {
        initializeEngine(explicitModelFile = file)
    }

    fun switchBackend(backend: BackendType) {
        initializeEngine(requestedBackend = backend)
    }

    fun deleteModel(model: ModelMetadata): Boolean {
        val targetPath = model.file.absolutePath
        val targetCanon = canonicalPathOf(model.file)

        viewModelScope.launch {
            stopGenerationAndJoin()

            var fallbackFile: File? = null
            engineMutex.withLock {
                val activePrefCanon = deps.prefs.activeModelPath?.let { canonicalPathOf(File(it)) }
                val activeLoadedCanon = activeEngine?.activeModel?.file?.let { canonicalPathOf(it) }
                val isDeletingActive = targetCanon == activePrefCanon || targetCanon == activeLoadedCanon

                if (isDeletingActive) {
                    deps.prefs.activeModelPath = null
                    unloadActiveEngineLocked()
                }
                deps.prefs.unregisterModelPath(targetPath)

                val deleted = deps.modelHub.deleteModelFile(model.file)
                val hw = sampleHardwareInfo()
                val remaining = deps.modelHub.scanAndAssessModels(hw.availRamMb, hw.totalRamMb)

                synchronized(stateLock) {
                    hardwareInfoValue = hw
                    discoveredModelsValue = remaining
                    if (!deleted) {
                        transientErrorBannerValue = "Could not delete ${model.name}."
                    }
                    if (isDeletingActive && remaining.none { canonicalPathOf(it.file) != targetCanon }) {
                        engineStateValue = EngineState.Idle
                    }
                }
                publishUiStates()

                if (isDeletingActive) {
                    fallbackFile = remaining.firstOrNull { canonicalPathOf(it.file) != targetCanon }?.file
                }
            }
            fallbackFile?.let { initializeEngine(explicitModelFile = it) }
        }
        return true
    }

    // ── Model Importing (Full Progress & Actionable Errors) ───────────────────

    fun importModel(uri: Uri) {
        viewModelScope.launch {
            synchronized(stateLock) {
                importErrorValue = null
                importStateValue = ModelImportState(progress = ImportProgress.COPYING)
            }
            publishUiStates()

            val result = deps.modelHub.importModelSafely(uri) { state ->
                synchronized(stateLock) { importStateValue = state }
                publishUiStates()
            }

            if (result.isSuccess) {
                val file = result.getOrElse { return@launch }
                val hw = sampleHardwareInfo()
                val scanned = deps.modelHub.scanAndAssessModels(hw.availRamMb, hw.totalRamMb)
                synchronized(stateLock) {
                    hardwareInfoValue = hw
                    discoveredModelsValue = scanned
                    importErrorValue = null
                    importStateValue = ModelImportState(progress = ImportProgress.IDLE)
                }
                publishUiStates()
                initializeEngine(explicitModelFile = file)
            } else {
                val message = result.exceptionOrNull()?.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: "Failed to import model. Verify the file is a valid .gguf or .litertlm model and enough storage is available."
                synchronized(stateLock) {
                    importErrorValue = message
                    transientErrorBannerValue = message
                }
                publishUiStates()
            }
        }
    }

    fun dismissImportError() {
        synchronized(stateLock) {
            importErrorValue = null
            if (importStateValue.progress == ImportProgress.FAILED) {
                importStateValue = ModelImportState(progress = ImportProgress.IDLE)
            }
        }
        publishUiStates()
    }

    fun dismissTransientError() {
        synchronized(stateLock) { transientErrorBannerValue = null }
        publishUiStates()
    }

    fun dismissBackendFallbackBanner() {
        synchronized(stateLock) { backendFallbackBannerValue = null }
        publishUiStates()
    }

    // ── Messaging, Streaming, Stop, Regenerate & Edit ─────────────────────────

    fun sendMessage(userText: String, imageUri: Uri? = null, isRegeneration: Boolean = false) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty() && imageUri == null) return

        val visionSupported = synchronized(stateLock) { isVisionSupportedValue }
        if (imageUri != null && !visionSupported) {
            val reason = synchronized(stateLock) {
                visionUnsupportedReasonValue ?: "Active model is text-only and does not support images."
            }
            synchronized(stateLock) { transientErrorBannerValue = reason }
            publishUiStates()
            return
        }

        val engine = activeEngine ?: return
        if (!engine.isLoaded) return

        // Atomic check-and-claim BEFORE launching coroutine or touching DB/cache
        if (!sendSlotClaimed.compareAndSet(false, true)) return
        synchronized(stateLock) { isGeneratingValue = true }
        publishUiStates()

        generationJob = viewModelScope.launch {
            runGenerationTurn(
                engine = engine,
                promptRawText = trimmed,
                newImageUri = imageUri,
                reusedImagePath = null,
                prepareHistory = if (isRegeneration) {
                    suspend { sessionId ->
                        val current = synchronized(stateLock) { messagesValue }
                        val lastBotIdx = current.indexOfLast { !it.isUser }
                        if (lastBotIdx >= 0) {
                            val toRemove = current.subList(lastBotIdx, current.size)
                            withContext(deps.ioDispatcher) {
                                toRemove.forEach { deps.repository.deleteMessage(it.id) }
                            }
                            synchronized(stateLock) {
                                messagesValue = current.take(lastBotIdx)
                            }
                        }
                        resetEngineForCurrentMode(synchronized(stateLock) { messagesValue })
                    }
                } else null,
                insertUserTurn = !isRegeneration,
            )
        }
    }

    fun regenerateLastResponse() {
        val current = synchronized(stateLock) { messagesValue }
        val lastAssistant = current.lastOrNull { !it.isUser } ?: return
        regenerateMessage(lastAssistant.id)
    }

    fun regenerateMessage(assistantMessageId: Long) {
        val engine = activeEngine ?: return
        if (!engine.isLoaded) return

        val snapshot = synchronized(stateLock) { messagesValue }
        val targetIdx = snapshot.indexOfFirst { it.id == assistantMessageId && !it.isUser }
        if (targetIdx < 0) return
        val precedingUser = snapshot.subList(0, targetIdx).lastOrNull { it.isUser } ?: return

        if (!sendSlotClaimed.compareAndSet(false, true)) return
        synchronized(stateLock) { isGeneratingValue = true }
        publishUiStates()

        generationJob = viewModelScope.launch {
            runGenerationTurn(
                engine = engine,
                promptRawText = precedingUser.text,
                newImageUri = null,
                reusedImagePath = precedingUser.imagePath,
                prepareHistory = { _ ->
                    val current = synchronized(stateLock) { messagesValue }
                    val idx = current.indexOfFirst { it.id == assistantMessageId }
                    if (idx >= 0) {
                        val dropped = current.subList(idx, current.size)
                        withContext(deps.ioDispatcher) {
                            dropped.forEach { deps.repository.deleteMessage(it.id) }
                        }
                        synchronized(stateLock) {
                            messagesValue = current.take(idx)
                        }
                    }
                    resetEngineForCurrentMode(synchronized(stateLock) { messagesValue })
                },
                insertUserTurn = false,
            )
        }
    }

    fun editAndResendMessage(messageId: Long, newText: String) {
        val engine = activeEngine ?: return
        if (!engine.isLoaded) return

        val snapshot = synchronized(stateLock) { messagesValue }
        val targetIdx = snapshot.indexOfFirst { it.id == messageId && it.isUser }
        if (targetIdx < 0) return
        val targetMsg = snapshot[targetIdx]

        val cleanText = newText.trim()
        if (cleanText.isEmpty() && targetMsg.imagePath == null) return

        if (!sendSlotClaimed.compareAndSet(false, true)) return
        synchronized(stateLock) { isGeneratingValue = true }
        publishUiStates()

        generationJob = viewModelScope.launch {
            runGenerationTurn(
                engine = engine,
                promptRawText = cleanText,
                newImageUri = null,
                reusedImagePath = targetMsg.imagePath,
                prepareHistory = { _ ->
                    val current = synchronized(stateLock) { messagesValue }
                    val idx = current.indexOfFirst { it.id == messageId }
                    if (idx >= 0) {
                        val dropped = current.subList(idx, current.size)
                        withContext(deps.ioDispatcher) {
                            dropped.forEach { deps.repository.deleteMessage(it.id) }
                        }
                        synchronized(stateLock) {
                            messagesValue = current.take(idx)
                        }
                    }
                    resetEngineForCurrentMode(synchronized(stateLock) { messagesValue })
                },
                insertUserTurn = true,
            )
        }
    }

    private suspend fun runGenerationTurn(
        engine: InferenceEngine,
        promptRawText: String,
        newImageUri: Uri?,
        reusedImagePath: String?,
        prepareHistory: (suspend (String) -> Unit)?,
        insertUserTurn: Boolean,
    ) {
        val generationId = UUID.randomUUID().toString()
        activeGenerationId = generationId
        val botMsgId = MessageIdGenerator.nextId()
        var session: ConversationSession? = null
        var accumulatedRaw = ""
        var latestDirectThink: String? = null
        var latestMetrics: BenchmarkMetrics? = null
        val deltaTextBuffer = StringBuilder()
        val deltaThinkBuffer = StringBuilder()

        try {
            val baseConfig = currentGenerationConfig()
            val errors = PromptValidation.validate(baseConfig)
            if (errors.isNotEmpty()) {
                val detail = errors.joinToString("\n") { "• ${it.message}" }
                val convId = synchronized(stateLock) { activeConversationValue?.id ?: "" }
                val validationErrorMsg = ChatMessage(
                    id = MessageIdGenerator.nextId(),
                    conversationId = convId,
                    text = "Cannot generate — fix the highlighted inference settings first:\n$detail",
                    isUser = false,
                    isStreaming = false,
                    isError = true,
                )
                synchronized(stateLock) {
                    validationErrorsValue = errors
                    validationBannerValue = errors.first().message
                    messagesValue = messagesValue + validationErrorMsg
                }
                publishUiStates()
                return
            }

            synchronized(stateLock) {
                validationErrorsValue = emptyList()
                validationBannerValue = null
            }

            // Ensure an active conversation session exists in SQLite
            session = synchronized(stateLock) { activeConversationValue } ?: run {
                val created = ConversationSession(
                    id = UUID.randomUUID().toString(),
                    title = promptRawText.take(36).ifEmpty { "Image Query" },
                    modelName = synchronized(stateLock) { modelTitleValue },
                )
                withContext(deps.ioDispatcher) {
                    deps.repository.saveConversation(created)
                }
                synchronized(stateLock) {
                    activeConversationValue = created
                    conversationsValue = listOf(created) + conversationsValue
                }
                created
            }

            prepareHistory?.invoke(session.id)

            val promptText = promptRawText.ifEmpty { "Describe this image in detail." }
            val effectiveImagePath = reusedImagePath ?: newImageUri?.let { uri ->
                withContext(deps.ioDispatcher) { deps.uriCacheCopier(uri)?.absolutePath }
            }

            if (insertUserTurn) {
                val userMsg = ChatMessage(
                    id = MessageIdGenerator.nextId(),
                    conversationId = session.id,
                    text = promptText,
                    imagePath = effectiveImagePath,
                    isUser = true,
                )
                synchronized(stateLock) { messagesValue = messagesValue + userMsg }
                publishUiStates()
                withContext(deps.ioDispatcher) {
                    deps.repository.saveMessage(userMsg)
                }
            }

            val memoryContext = withContext(deps.ioDispatcher) {
                deps.memoryManager.retrieveMemoryContext(promptText, session.id)
            }
            val config = currentGenerationConfig().copy(generationId = generationId)
            val backendLabel = currentBackendDisplayLabel()
            val modelName = synchronized(stateLock) { modelTitleValue }

            // Filter out error & streaming artifacts so failed turns never poison future context
            val historyForPrompt = synchronized(stateLock) {
                messagesValue.filterNot { it.isError || it.isStreaming }
            }
            val formattedPrompt = PromptContextBuilder.buildChatMl(
                historyMessages = historyForPrompt,
                latestUserPrompt = promptText,
                memoryContext = memoryContext,
                config = config,
                profiles = synchronized(stateLock) { profilesValue },
                promptProfile = synchronized(stateLock) { activeProfileValue },
            )
            val estimatedPromptTokens = PromptContextBuilder.estimateTokens(formattedPrompt)
            latestMetrics = BenchmarkMetrics(
                backend = backendLabel,
                prefillTokens = estimatedPromptTokens,
            )

            val placeholder = ChatMessage(
                id = botMsgId,
                conversationId = session.id,
                text = "",
                isUser = false,
                isStreaming = true,
                modelName = modelName,
                backendLabel = backendLabel,
                benchmark = latestMetrics,
            )
            synchronized(stateLock) {
                lastUsedMemoriesValue = memoryContext.memories
                messagesValue = messagesValue + placeholder
            }
            publishUiStates()

            var lastUiUpdateMs = 0L
            engine.sendMessage(formattedPrompt, effectiveImagePath, config).collect { chunk ->
                if (activeGenerationId != generationId ||
                    synchronized(stateLock) { activeConversationValue?.id != session.id }
                ) {
                    return@collect
                }

                if (chunk.hasError) {
                    val errMsg = ChatMessage(
                        id = botMsgId,
                        conversationId = session.id,
                        text = chunk.error ?: "Generation error occurred.",
                        isUser = false,
                        isStreaming = false,
                        isError = true,
                        modelName = modelName,
                        backendLabel = backendLabel,
                    )
                    updateMessageInState(botMsgId) { errMsg }
                    withContext(NonCancellable + deps.ioDispatcher) {
                        deps.repository.saveMessage(errMsg)
                    }
                    return@collect
                }

                // Support both delta-streaming (Wave 2 engines) and cumulative chunks (bff90bd engines)
                accumulatedRaw = if (chunk.isDelta || (chunk.deltaText.isNotEmpty() && chunk.text.isEmpty())) {
                    deltaTextBuffer.append(chunk.deltaText)
                    deltaTextBuffer.toString()
                } else if (chunk.text.isNotEmpty() || chunk.isComplete || chunk.isCancelled) {
                    chunk.text
                } else {
                    accumulatedRaw
                }

                if (chunk.deltaThinking.isNotEmpty()) {
                    deltaThinkBuffer.append(chunk.deltaThinking)
                    latestDirectThink = deltaThinkBuffer.toString()
                } else if (!chunk.thinking.isNullOrBlank()) {
                    latestDirectThink = chunk.thinking
                }

                chunk.metrics?.let { metrics ->
                    latestMetrics = metrics.copy(
                        backend = backendLabel.ifBlank { metrics.backend },
                        prefillTokens = metrics.prefillTokens ?: estimatedPromptTokens,
                    )
                }

                val now = deps.clockMs()
                val shouldPublish = chunk.isComplete ||
                    chunk.isCancelled ||
                    (now - lastUiUpdateMs >= deps.streamThrottleMs)

                if (shouldPublish) {
                    lastUiUpdateMs = now
                    val parsed = parseReasoningTags(accumulatedRaw, latestDirectThink)
                    val finalized = chunk.isComplete || chunk.isCancelled
                    updateMessageInState(botMsgId) { existing ->
                        existing.copy(
                            text = parsed.answer,
                            thinkText = parsed.think,
                            isStreaming = !finalized,
                            isCancelled = chunk.isCancelled,
                            benchmark = latestMetrics,
                            modelName = modelName,
                            backendLabel = backendLabel,
                        )
                    }
                }

                if (chunk.isComplete) {
                    val parsed = parseReasoningTags(accumulatedRaw, latestDirectThink)
                    val finalMsg = synchronized(stateLock) {
                        messagesValue.find { it.id == botMsgId }
                    }?.copy(
                        text = parsed.answer,
                        thinkText = parsed.think,
                        isStreaming = false,
                        isCancelled = chunk.isCancelled,
                        benchmark = latestMetrics,
                        modelName = modelName,
                        backendLabel = backendLabel,
                    )
                    if (finalMsg != null &&
                        (finalMsg.text.isNotBlank() || !finalMsg.thinkText.isNullOrBlank())
                    ) {
                        withContext(NonCancellable + deps.ioDispatcher) {
                            deps.repository.saveMessage(finalMsg)
                            if (!chunk.isCancelled && finalMsg.text.isNotBlank()) {
                                deps.memoryManager.extractAndConsolidate(
                                    session.id,
                                    promptText,
                                    finalMsg.text,
                                )
                            }
                        }
                        if (!chunk.isCancelled && finalMsg.text.isNotBlank()) {
                            loadMemories()
                        }
                    } else if (finalMsg != null && chunk.isCancelled) {
                        removeMessageFromState(botMsgId)
                    }
                }
            }
        } catch (ce: CancellationException) {
            val activeConvId = session?.id
            if (activeConvId != null && activeGenerationId == generationId) {
                val parsed = parseReasoningTags(accumulatedRaw, latestDirectThink)
                if (parsed.answer.isNotBlank() || !parsed.think.isNullOrBlank()) {
                    val partialMsg = ChatMessage(
                        id = botMsgId,
                        conversationId = activeConvId,
                        text = parsed.answer,
                        thinkText = parsed.think,
                        isUser = false,
                        isStreaming = false,
                        isCancelled = true,
                        benchmark = latestMetrics,
                        modelName = synchronized(stateLock) { modelTitleValue },
                        backendLabel = currentBackendDisplayLabel(),
                    )
                    updateMessageInState(botMsgId) { partialMsg }
                    withContext(NonCancellable + deps.ioDispatcher) {
                        deps.repository.saveMessage(partialMsg)
                    }
                } else {
                    removeMessageFromState(botMsgId)
                }
            }
            throw ce
        } catch (e: Exception) {
            val activeConvId = session?.id
            if (activeConvId != null && activeGenerationId == generationId) {
                val errorMsg = ChatMessage(
                    id = botMsgId,
                    conversationId = activeConvId,
                    text = "Error during generation: ${e.localizedMessage ?: e.toString()}",
                    isUser = false,
                    isStreaming = false,
                    isError = true,
                    modelName = synchronized(stateLock) { modelTitleValue },
                    backendLabel = currentBackendDisplayLabel(),
                )
                updateMessageInState(botMsgId) { errorMsg }
                withContext(NonCancellable + deps.ioDispatcher) {
                    deps.repository.saveMessage(errorMsg)
                }
            }
        } finally {
            withContext(NonCancellable) {
                if (activeGenerationId == generationId) {
                    activeGenerationId = null
                    synchronized(stateLock) { isGeneratingValue = false }
                    sendSlotClaimed.set(false)
                    publishUiStates()
                }
                refreshConversationsList()
            }
        }
    }

    private fun updateMessageInState(messageId: Long, transform: (ChatMessage) -> ChatMessage) {
        synchronized(stateLock) {
            val idx = messagesValue.indexOfLast { it.id == messageId }
            if (idx >= 0) {
                val mutable = messagesValue.toMutableList()
                mutable[idx] = transform(mutable[idx])
                messagesValue = mutable
            }
        }
        publishUiStates()
    }

    private fun removeMessageFromState(messageId: Long) {
        synchronized(stateLock) {
            messagesValue = messagesValue.filterNot { it.id == messageId }
        }
        publishUiStates()
    }

    private fun currentBackendDisplayLabel(): String {
        val info = synchronized(stateLock) { executionBackendInfoValue }
        val activeBackend = info?.active ?: synchronized(stateLock) { currentBackendValue }
        return if (info?.isFallback == true) {
            "${activeBackend.chipText} (${info.requested.chipText} fallback)"
        } else {
            activeBackend.chipText
        }
    }

    fun stopGeneration() {
        val job = generationJob
        activeEngine?.cancelGeneration()
        job?.cancel()
    }

    private suspend fun stopGenerationAndJoin() {
        val job = generationJob
        activeEngine?.cancelGeneration()
        if (job != null && job.isActive) {
            job.cancelAndJoin()
        }
        generationJob = null
        sendSlotClaimed.set(false)
        synchronized(stateLock) { isGeneratingValue = false }
        publishUiStates()
    }

    // ── Conversation Lifecycle ────────────────────────────────────────────────

    private fun loadConversations() {
        viewModelScope.launch {
            val query = synchronized(stateLock) { conversationSearchQuery }
            val list = withContext(deps.ioDispatcher) {
                if (query.isBlank()) deps.repository.getAllConversations()
                else deps.repository.searchConversations(query)
            }
            var initialToSelect: ConversationSession? = null
            synchronized(stateLock) {
                conversationsValue = list
                if (list.isNotEmpty() && activeConversationValue == null) {
                    initialToSelect = list.first()
                }
            }
            publishUiStates()
            initialToSelect?.let { selectConversation(it) }
        }
    }

    private suspend fun refreshConversationsList() {
        val query = synchronized(stateLock) { conversationSearchQuery }
        val list = withContext(deps.ioDispatcher) {
            if (query.isBlank()) deps.repository.getAllConversations()
            else deps.repository.searchConversations(query)
        }
        synchronized(stateLock) { conversationsValue = list }
        publishUiStates()
    }

    fun newChat() {
        conversationLoadJob?.cancel()
        viewModelScope.launch {
            stopGenerationAndJoin()
            engineMutex.withLock {
                resetEngineForCurrentMode(emptyList())
            }
            synchronized(stateLock) {
                activeConversationValue = null
                messagesValue = emptyList()
                lastUsedMemoriesValue = emptyList()
            }
            publishUiStates()
        }
    }

    fun selectConversation(session: ConversationSession) {
        conversationLoadJob?.cancel()
        conversationLoadJob = viewModelScope.launch {
            stopGenerationAndJoin()
            synchronized(stateLock) {
                activeConversationValue = session
                messagesValue = emptyList()
                lastUsedMemoriesValue = emptyList()
            }
            publishUiStates()

            val loadedMessages = withContext(deps.ioDispatcher) {
                deps.repository.getMessages(session.id)
            }
            if (synchronized(stateLock) { activeConversationValue?.id != session.id }) return@launch

            engineMutex.withLock {
                resetEngineForCurrentMode(loadedMessages)
            }
            if (synchronized(stateLock) { activeConversationValue?.id == session.id }) {
                synchronized(stateLock) { messagesValue = loadedMessages }
                publishUiStates()
            }
        }
    }

    fun renameConversation(session: ConversationSession, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            withContext(deps.ioDispatcher) {
                deps.repository.updateConversationTitle(session.id, trimmed)
            }
            synchronized(stateLock) {
                if (activeConversationValue?.id == session.id) {
                    activeConversationValue = activeConversationValue?.copy(title = trimmed)
                }
            }
            refreshConversationsList()
        }
    }

    fun deleteConversation(session: ConversationSession) {
        viewModelScope.launch {
            val isDeletingActive = synchronized(stateLock) { activeConversationValue?.id == session.id }
            if (isDeletingActive) {
                stopGenerationAndJoin()
                engineMutex.withLock { resetEngineForCurrentMode(emptyList()) }
                synchronized(stateLock) {
                    activeConversationValue = null
                    messagesValue = emptyList()
                    lastUsedMemoriesValue = emptyList()
                }
            }
            withContext(deps.ioDispatcher) {
                deps.repository.deleteConversation(session.id)
            }
            refreshConversationsList()
        }
    }

    fun togglePin(session: ConversationSession) {
        viewModelScope.launch {
            withContext(deps.ioDispatcher) {
                deps.repository.togglePinConversation(session.id, !session.isPinned)
            }
            refreshConversationsList()
        }
    }

    fun searchConversations(query: String) {
        synchronized(stateLock) { conversationSearchQuery = query }
        publishUiStates()
        searchConversationsJob?.cancel()
        searchConversationsJob = viewModelScope.launch {
            if (query.isNotBlank()) delay(SEARCH_DEBOUNCE_MS)
            refreshConversationsList()
        }
    }

    fun formatConversationAsMarkdown(session: ConversationSession): suspend () -> String = {
        val msgs = if (synchronized(stateLock) { activeConversationValue?.id == session.id }) {
            synchronized(stateLock) { messagesValue }
        } else {
            withContext(deps.ioDispatcher) { deps.repository.getMessages(session.id) }
        }
        buildString {
            append("# ${session.title}\n\n")
            for (m in msgs) {
                append(if (m.isUser) "## User\n" else "## Assistant\n")
                if (!m.thinkText.isNullOrBlank()) {
                    append("<details><summary>Reasoning</summary>\n\n")
                    append(m.thinkText.trim())
                    append("\n</details>\n\n")
                }
                append(m.text.trim())
                append("\n\n---\n\n")
            }
        }
    }

    // ── Persistent Cross-Chat Memory (Non-destructive Search) ─────────────────

    fun loadMemories() {
        viewModelScope.launch {
            val list = withContext(deps.ioDispatcher) { deps.repository.getAllMemories() }
            synchronized(stateLock) {
                allMemoriesMasterValue = list
                displayedMemoriesValue = applyMemoryFiltersLocked(list)
            }
            publishUiStates()
        }
    }

    private fun applyMemoryFiltersLocked(source: List<MemoryItem>): List<MemoryItem> {
        val query = memorySearchQuery.trim()
        val category = selectedMemoryCategory
        return source.filter { item ->
            val matchesCat = category == null || item.category == category
            val matchesQuery = query.isEmpty() ||
                item.content.contains(query, ignoreCase = true) ||
                item.keywords.contains(query, ignoreCase = true) ||
                item.category.displayName.contains(query, ignoreCase = true)
            matchesCat && matchesQuery
        }
    }

    fun togglePersistentMemory(enabled: Boolean) {
        deps.prefs.persistentMemoryEnabled = enabled
        synchronized(stateLock) { persistentMemoryEnabledValue = enabled }
        publishUiStates()
    }

    fun saveMemory(item: MemoryItem) {
        viewModelScope.launch {
            withContext(deps.ioDispatcher) { deps.repository.saveMemory(item) }
            loadMemories()
        }
    }

    fun updateMemory(id: String, content: String, category: MemoryCategory? = null) {
        viewModelScope.launch {
            withContext(deps.ioDispatcher) { deps.repository.updateMemory(id, content, category) }
            loadMemories()
        }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch {
            withContext(deps.ioDispatcher) { deps.repository.deleteMemory(id) }
            loadMemories()
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            withContext(deps.ioDispatcher) { deps.repository.clearAllMemories() }
            loadMemories()
        }
    }

    fun searchMemories(query: String) {
        synchronized(stateLock) {
            memorySearchQuery = query
            displayedMemoriesValue = applyMemoryFiltersLocked(allMemoriesMasterValue)
        }
        publishUiStates()
        searchMemoriesJob?.cancel()
        searchMemoriesJob = viewModelScope.launch {
            if (query.isNotBlank()) delay(SEARCH_DEBOUNCE_MS)
            val all = withContext(deps.ioDispatcher) { deps.repository.getAllMemories() }
            synchronized(stateLock) {
                allMemoriesMasterValue = all
                displayedMemoriesValue = applyMemoryFiltersLocked(all)
            }
            publishUiStates()
        }
    }

    fun selectMemoryCategory(category: MemoryCategory?) {
        synchronized(stateLock) {
            selectedMemoryCategory = category
            displayedMemoriesValue = applyMemoryFiltersLocked(allMemoriesMasterValue)
        }
        publishUiStates()
    }

    // ── Inference Settings, Validation & Prompt Profiles ──────────────────────

    fun validateCurrentConfig(): List<PromptValidationError> {
        val config = currentGenerationConfig()
        val errors = PromptValidation.validate(config)
        synchronized(stateLock) {
            validationErrorsValue = errors
            validationBannerValue = errors.firstOrNull()?.message
        }
        publishUiStates()
        return errors
    }

    fun dismissValidationBanner() {
        synchronized(stateLock) { validationBannerValue = null }
        publishUiStates()
    }

    fun runtimeSupportNotices(): List<String> {
        val config = currentGenerationConfig()
        val errors = PromptValidation.validate(config).map { it.message }
        val runtimeNotes = PromptValidation.auditRuntimeSupport(config).map { it.message }
        return (errors + runtimeNotes).distinct()
    }

    fun currentGenerationConfig(): GenerationConfig {
        val threads = deps.adaptiveThreadCountFn(performanceModeValue, threadCountValue)
            .coerceIn(GenerationConfig.MIN_THREAD_COUNT, GenerationConfig.MAX_THREAD_COUNT)
        val profile = synchronized(stateLock) { activeProfileValue }
        val resolvedPrompt = profile.effectiveSystemPrompt
            .ifBlank { profile.instructionText }
            .ifBlank { synchronized(stateLock) { systemPromptValue } }
        return synchronized(stateLock) {
            GenerationConfig(
                temperature = temperatureValue.coerceIn(
                    GenerationConfig.MIN_TEMPERATURE, GenerationConfig.MAX_TEMPERATURE,
                ),
                topP = topPValue.coerceIn(GenerationConfig.MIN_TOP_P, GenerationConfig.MAX_TOP_P),
                repetitionPenalty = repetitionPenaltyValue.coerceIn(
                    GenerationConfig.MIN_REPETITION_PENALTY, GenerationConfig.MAX_REPETITION_PENALTY,
                ),
                maxOutputTokens = maxTokensValue.coerceIn(
                    GenerationConfig.MIN_MAX_TOKENS, GenerationConfig.MAX_MAX_TOKENS,
                ),
                contextLength = contextLengthValue.coerceIn(
                    GenerationConfig.MIN_CONTEXT_LENGTH, GenerationConfig.MAX_CONTEXT_LENGTH,
                ),
                systemPrompt = sanitizeSystemPrompt(resolvedPrompt),
                threadCount = threads,
                backend = currentBackendValue,
                stopSequences = GenerationConfig.DEFAULT_STOP_SEQUENCES,
                unrestrictedMode = chatModeValue,
                activePromptProfileId = profile.id,
            )
        }
    }

    fun setChatMode(mode: UnrestrictedMode) {
        deps.prefs.unrestrictedMode = mode
        synchronized(stateLock) { chatModeValue = mode }
        val targetId = if (mode == UnrestrictedMode.UNRESTRICTED) PromptProfile.UNRESTRICTED_ID
        else PromptProfile.DEFAULT_ID
        val profiles = synchronized(stateLock) { profilesValue }.ifEmpty { deps.prefs.promptProfiles }
        profiles.firstOrNull { it.id == targetId }?.let { setActiveProfile(it.id) }
            ?: validateCurrentConfig()
    }

    fun setActiveProfile(profileId: String) {
        val profiles = synchronized(stateLock) { profilesValue }.ifEmpty { deps.prefs.promptProfiles }
        val match = profiles.firstOrNull { it.id == profileId } ?: return
        deps.prefs.setActivePromptProfile(match.id)
        val resolvedPrompt = match.effectiveSystemPrompt.ifBlank { match.instructionText }
        deps.prefs.systemPrompt = resolvedPrompt
        synchronized(stateLock) {
            activeProfileValue = match
            systemPromptValue = resolvedPrompt
        }
        validateCurrentConfig()
    }

    fun setActivePromptProfile(profile: PromptProfile) = setActiveProfile(profile.id)

    fun savePromptProfile(profile: PromptProfile): String? {
        val error = profile.validate()
        if (error != null) return error
        deps.prefs.addOrUpdateProfile(profile)
        val updatedList = deps.prefs.promptProfiles
        synchronized(stateLock) {
            profilesValue = updatedList
            if (profile.id == activeProfileValue.id) {
                val resolved = sanitizeSystemPrompt(
                    profile.effectiveSystemPrompt.ifBlank { profile.instructionText },
                )
                activeProfileValue = profile.copy(effectiveSystemPrompt = resolved)
                systemPromptValue = resolved
                deps.prefs.systemPrompt = resolved
            }
        }
        validateCurrentConfig()
        return null
    }

    fun saveActiveProfile(profile: PromptProfile): String? = savePromptProfile(profile)

    fun deletePromptProfile(profileId: String) {
        deps.prefs.removeProfile(profileId)
        val updatedList = deps.prefs.promptProfiles
        synchronized(stateLock) {
            profilesValue = updatedList
            if (activeProfileValue.id == profileId) {
                val fallback = updatedList.firstOrNull() ?: PromptProfile.standardDefault()
                activeProfileValue = fallback
                deps.prefs.setActivePromptProfile(fallback.id)
                systemPromptValue = fallback.effectiveSystemPrompt.ifBlank { fallback.instructionText }
                deps.prefs.systemPrompt = systemPromptValue
            }
        }
        validateCurrentConfig()
    }

    fun resetPromptProfile(profileId: String) {
        deps.prefs.resetProfileToDefault(profileId)
        val updatedList = deps.prefs.promptProfiles
        synchronized(stateLock) {
            profilesValue = updatedList
            updatedList.firstOrNull { it.id == profileId }?.let { restored ->
                if (restored.id == activeProfileValue.id) {
                    activeProfileValue = restored
                    systemPromptValue = restored.effectiveSystemPrompt
                    deps.prefs.systemPrompt = restored.effectiveSystemPrompt
                }
            }
        }
        validateCurrentConfig()
    }

    fun setPerformanceMode(mode: PerformanceMode) {
        deps.prefs.performanceMode = mode
        synchronized(stateLock) { performanceModeValue = mode }
        validateCurrentConfig()
    }

    fun setThreadCount(count: Int) {
        val clamped = count.coerceIn(GenerationConfig.MIN_THREAD_COUNT, GenerationConfig.MAX_THREAD_COUNT)
        deps.prefs.threadCount = clamped
        synchronized(stateLock) { threadCountValue = clamped }
        validateCurrentConfig()
    }

    fun setTemperature(temp: Float) {
        val clamped = temp.coerceIn(GenerationConfig.MIN_TEMPERATURE, GenerationConfig.MAX_TEMPERATURE)
        deps.prefs.temperature = clamped
        synchronized(stateLock) { temperatureValue = clamped }
        validateCurrentConfig()
    }

    fun setTopP(p: Float) {
        val clamped = p.coerceIn(GenerationConfig.MIN_TOP_P, GenerationConfig.MAX_TOP_P)
        deps.prefs.topP = clamped
        synchronized(stateLock) { topPValue = clamped }
        validateCurrentConfig()
    }

    fun setRepetitionPenalty(value: Float) {
        val clamped = value.coerceIn(
            GenerationConfig.MIN_REPETITION_PENALTY,
            GenerationConfig.MAX_REPETITION_PENALTY,
        )
        deps.prefs.repetitionPenalty = clamped
        synchronized(stateLock) { repetitionPenaltyValue = clamped }
        validateCurrentConfig()
    }

    fun setContextLength(value: Int) {
        val clamped = value.coerceIn(
            GenerationConfig.MIN_CONTEXT_LENGTH,
            GenerationConfig.MAX_CONTEXT_LENGTH,
        )
        deps.prefs.contextLength = clamped
        synchronized(stateLock) { contextLengthValue = clamped }
        validateCurrentConfig()
    }

    fun setMaxTokens(tokens: Int) {
        val clamped = tokens.coerceIn(GenerationConfig.MIN_MAX_TOKENS, GenerationConfig.MAX_MAX_TOKENS)
        deps.prefs.maxOutputTokens = clamped
        synchronized(stateLock) { maxTokensValue = clamped }
        validateCurrentConfig()
    }

    fun setSystemPrompt(prompt: String) {
        val clean = sanitizeSystemPrompt(prompt)
        deps.prefs.systemPrompt = clean
        val updated = synchronized(stateLock) {
            activeProfileValue.copy(instructionText = clean, effectiveSystemPrompt = clean)
        }
        deps.prefs.addOrUpdateProfile(updated)
        val refreshed = deps.prefs.promptProfiles
        synchronized(stateLock) {
            systemPromptValue = clean
            activeProfileValue = updated
            profilesValue = refreshed
        }
        validateCurrentConfig()
    }

    fun setThemeMode(mode: ThemeMode) {
        deps.prefs.themeMode = mode
        synchronized(stateLock) { themeModeValue = mode }
        _themeMode.value = mode
        publishUiStates()
    }

    fun setHapticsEnabled(enabled: Boolean) {
        deps.prefs.hapticsEnabled = enabled
        synchronized(stateLock) { hapticsEnabledValue = enabled }
        _hapticsEnabled.value = enabled
        publishUiStates()
    }

    fun regenerationConfig(): GenerationConfig = currentGenerationConfig()

    // ── TTS & Suggestions ─────────────────────────────────────────────────────

    fun speak(text: String) = deps.speakFn(text)

    fun stopSpeaking() = deps.stopSpeakFn()

    fun getFollowUpSuggestions(lastAssistantMessage: String?): List<String> {
        if (lastAssistantMessage.isNullOrBlank()) return emptyList()
        val lower = lastAssistantMessage.lowercase()
        val suggestions = mutableListOf<String>()

        when {
            lower.contains("```") || lower.contains("fun ") || lower.contains("def ") || lower.contains("class ") -> {
                suggestions.add("Explain how this code works")
                suggestions.add("Write unit tests for this")
                suggestions.add("Optimize this implementation")
            }
            lower.contains("diagram") || lower.contains("graph") || lower.contains("flowchart") || lower.contains("architecture") -> {
                suggestions.add("Show as a Mermaid flowchart")
                suggestions.add("Break down the components")
            }
            lower.contains("table") || lower.contains("|") -> {
                suggestions.add("Add more comparison criteria")
                suggestions.add("Summarize key trade-offs")
            }
            else -> {
                suggestions.add("Explain in simpler terms")
                suggestions.add("Give a practical example")
                suggestions.add("Summarize key takeaways")
            }
        }
        return suggestions.take(3)
    }

    // ── Helpers & Cleanup ─────────────────────────────────────────────────────

    private suspend fun resetEngineForCurrentMode(history: List<ChatMessage>) {
        val engine = activeEngine ?: return
        val prompt = currentGenerationConfig().systemPrompt
        if (engine is com.example.on_device_qwen.domain.engine.LiteRTInferenceEngine) {
            engine.resetConversationWithSystemPrompt(prompt)
        } else {
            engine.resetSession(prompt, history)
        }
    }

    private data class ParsedResponse(val think: String?, val answer: String)

    private fun parseReasoningTags(raw: String, explicitThink: String? = null): ParsedResponse {
        val openTag = "<think>"
        val closeTag = "</think>"
        val openIdx = raw.indexOf(openTag)
        if (openIdx == -1) {
            return ParsedResponse(explicitThink?.trim()?.ifEmpty { null }, raw)
        }

        val afterOpen = raw.substring(openIdx + openTag.length)
        val closeIdx = afterOpen.indexOf(closeTag)
        return if (closeIdx == -1) {
            val combinedThink = afterOpen.trim().ifEmpty { explicitThink?.trim().orEmpty() }
            ParsedResponse(combinedThink.ifEmpty { null }, "")
        } else {
            val tagThink = afterOpen.substring(0, closeIdx).trim()
            val answer = afterOpen.substring(closeIdx + closeTag.length).trimStart('\n', '\r', ' ')
            val combinedThink = tagThink.ifEmpty { explicitThink?.trim().orEmpty() }
            ParsedResponse(combinedThink.ifEmpty { null }, answer)
        }
    }

    private fun canonicalPathOf(file: File): String =
        try { file.canonicalPath } catch (_: Exception) { file.absolutePath }

    private fun publishUiStates() {
        _chatUiState.value = buildChatUiState()
        _modelHubUiState.value = buildModelHubUiState()
        _settingsUiState.value = buildSettingsUiState()
        _memoryUiState.value = buildMemoryUiState()
        _navigationUiState.value = buildNavigationUiState()
    }

    private fun buildChatUiState(): ChatScreenUiState = synchronized(stateLock) {
        val showGuide = onboardingExplicitlyShown ||
            (!onboardingCompletedValue && engineStateValue !is EngineState.Ready)
        ChatScreenUiState(
            engineState = engineStateValue,
            modelTitle = modelTitleValue,
            activeModelMetadata = activeModelMetadataValue,
            executionBackendInfo = executionBackendInfoValue,
            engineCapabilities = engineCapabilitiesValue,
            messages = messagesValue,
            isGenerating = isGeneratingValue,
            isVisionSupported = isVisionSupportedValue,
            visionUnsupportedReason = visionUnsupportedReasonValue,
            isSpeaking = isSpeakingValue,
            chatMode = chatModeValue,
            activeProfile = activeProfileValue,
            lastUsedMemories = lastUsedMemoriesValue,
            validationBanner = validationBannerValue,
            backendFallbackBanner = backendFallbackBannerValue,
            transientErrorBanner = transientErrorBannerValue,
            activeConversationId = activeConversationValue?.id,
            activeConversationTitle = activeConversationValue?.title,
            showOnboarding = showGuide,
            onboardingCompleted = onboardingCompletedValue,
            hardwareInfo = hardwareInfoValue,
        )
    }

    private fun buildModelHubUiState(): ModelHubUiState = synchronized(stateLock) {
        val hw = hardwareInfoValue
        ModelHubUiState(
            discoveredModels = discoveredModelsValue,
            activeModel = activeModelMetadataValue,
            availRamMb = hw.availRamMb,
            totalRamMb = hw.totalRamMb,
            importState = importStateValue,
            importError = importErrorValue,
            isScanning = isScanningValue,
            currentBackend = currentBackendValue,
            executionBackendInfo = executionBackendInfoValue,
            catalogRecommendations = deps.modelHub.recommendCatalogModels(hw.totalRamMb, hw.availRamMb),
        )
    }

    private fun buildSettingsUiState(): SettingsUiState = synchronized(stateLock) {
        val engineType = activeModelMetadataValue?.engineType
            ?: when (currentBackendValue) {
                BackendType.CPU -> EngineType.LLAMA_CPP
                BackendType.GPU, BackendType.NPU -> EngineType.LITERT
            }
        val config = GenerationConfig(
            temperature = temperatureValue,
            topP = topPValue,
            repetitionPenalty = repetitionPenaltyValue,
            maxOutputTokens = maxTokensValue,
            contextLength = contextLengthValue,
            systemPrompt = systemPromptValue,
            threadCount = threadCountValue,
            backend = currentBackendValue,
            unrestrictedMode = chatModeValue,
            activePromptProfileId = activeProfileValue.id,
        )
        val runtimeNotes = PromptValidation.auditRuntimeSupport(config).map { it.message }
        SettingsUiState(
            performanceMode = performanceModeValue,
            backendType = currentBackendValue,
            activeEngineType = engineType,
            executionBackendInfo = executionBackendInfoValue,
            engineCapabilities = engineCapabilitiesValue,
            threadCount = threadCountValue,
            temperature = temperatureValue,
            topP = topPValue,
            repetitionPenalty = repetitionPenaltyValue,
            maxTokens = maxTokensValue,
            contextLength = contextLengthValue,
            systemPrompt = systemPromptValue,
            chatMode = chatModeValue,
            activeProfile = activeProfileValue,
            profiles = profilesValue,
            themeMode = themeModeValue,
            hapticsEnabled = hapticsEnabledValue,
            persistentMemoryEnabled = persistentMemoryEnabledValue,
            validationErrors = validationErrorsValue.map { it.message },
            runtimeSupportNotices = runtimeNotes,
            supportedParameters = GenerationConfig.runtimeParameterSupport(engineType),
            hardwareInfo = hardwareInfoValue,
        )
    }

    private fun buildMemoryUiState(): MemoryUiState = synchronized(stateLock) {
        MemoryUiState(
            memories = displayedMemoriesValue,
            totalMemoryCount = allMemoriesMasterValue.size,
            searchQuery = memorySearchQuery,
            isMemoryEnabled = persistentMemoryEnabledValue,
            selectedCategory = selectedMemoryCategory,
        )
    }

    private fun buildNavigationUiState(): NavigationUiState = synchronized(stateLock) {
        val hw = hardwareInfoValue
        NavigationUiState(
            conversations = conversationsValue,
            activeConversationId = activeConversationValue?.id,
            searchQuery = conversationSearchQuery,
            batteryPercent = hw.batteryPercent,
            batteryTemp = hw.batteryTempCelsius,
            availableRamMb = hw.availRamMb,
        )
    }

    override fun onCleared() {
        super.onCleared()
        deps.cleanupThermalFn()
        deps.shutdownTtsFn()
        try {
            runBlocking(deps.ioDispatcher) {
                generationJob?.cancelAndJoin()
                engineMutex.withLock {
                    activeEngine?.unloadModel()
                    activeEngine = null
                }
            }
        } catch (_: Exception) {
        }
    }
}
