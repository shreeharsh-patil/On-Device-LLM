package com.example.on_device_qwen

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.on_device_qwen.data.preferences.AppPreferences
import com.example.on_device_qwen.data.preferences.PerformanceMode
import com.example.on_device_qwen.data.repository.ConversationRepository
import com.example.on_device_qwen.domain.engine.EngineCapabilities
import com.example.on_device_qwen.domain.engine.EngineLoadResult
import com.example.on_device_qwen.domain.engine.GenerationChunk
import com.example.on_device_qwen.domain.engine.InferenceEngine
import com.example.on_device_qwen.domain.memory.MemoryManager
import com.example.on_device_qwen.domain.modelmanager.ImportProgress
import com.example.on_device_qwen.domain.performance.DeviceMemoryStatus
import com.example.on_device_qwen.domain.performance.DeviceThermalState
import com.example.on_device_qwen.model.BackendType
import com.example.on_device_qwen.model.BenchmarkMetrics
import com.example.on_device_qwen.model.ChatMessage
import com.example.on_device_qwen.model.EngineType
import com.example.on_device_qwen.model.ExecutionBackendInfo
import com.example.on_device_qwen.model.GenerationConfig
import com.example.on_device_qwen.model.MemoryCategory
import com.example.on_device_qwen.model.MemoryItem
import com.example.on_device_qwen.model.ModelCompatibilityStatus
import com.example.on_device_qwen.model.ModelDiagnostic
import com.example.on_device_qwen.model.ModelErrorCode
import com.example.on_device_qwen.model.ModelFormat
import com.example.on_device_qwen.model.ModelMetadata
import com.example.on_device_qwen.viewmodel.ChatViewModel
import com.example.on_device_qwen.viewmodel.ChatViewModelDependencies
import com.example.on_device_qwen.viewmodel.EngineState
import com.example.on_device_qwen.viewmodel.ModelHubCoordinator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.Collections

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ChatViewModelBehaviorTests {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var app: Application
    private lateinit var repository: ConversationRepository
    private lateinit var prefs: AppPreferences
    private lateinit var memoryManager: MemoryManager
    private val testDispatcher = StandardTestDispatcher()
    private val createdViewModels = mutableListOf<ChatViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("on_device_qwen_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        repository = ConversationRepository(app)
        kotlinx.coroutines.runBlocking { repository.clearAllConversations() }
        kotlinx.coroutines.runBlocking { repository.clearAllMemories() }
        prefs = AppPreferences(app)
        memoryManager = MemoryManager(repository, prefs)
    }

    @After
    fun tearDown() {
        val store = ViewModelStore()
        createdViewModels.forEachIndexed { idx, vm -> store.put("vm_$idx", vm) }
        store.clear()
        createdViewModels.clear()
        Dispatchers.resetMain()
    }

    private fun makeModelFile(name: String): File {
        val file = File(tempFolder.root, name)
        file.writeBytes(byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte(), 3, 0, 0, 0))
        return file
    }

    private fun makeMetadata(file: File, name: String, hasVision: Boolean = false): ModelMetadata =
        ModelMetadata(
            file = file,
            name = name,
            format = ModelFormat.GGUF,
            engineType = EngineType.LLAMA_CPP,
            architecture = "qwen2",
            quantization = "Q4_K_M",
            contextLength = 4096,
            fileSizeBytes = 640L * 1024L * 1024L,
            estimatedRamBytes = 900L * 1024L * 1024L,
            hasVision = hasVision,
            compatibilityStatus = ModelCompatibilityStatus.COMPATIBLE,
        )

    private fun buildVm(
        models: MutableList<ModelMetadata>,
        engineProvider: (File) -> InferenceEngine,
        importFn: suspend (Uri, (Float) -> Unit) -> Result<File> = { _, _ -> Result.failure(IllegalStateException("unused")) },
        deleteFn: suspend (File) -> Boolean = { f -> f.delete() || !f.exists() },
        uriCacheCopier: (Uri) -> File? = { null },
        streamThrottleMs: Long = 0L,
    ): ChatViewModel {
        val modelHub = ModelHubCoordinator(
            context = app,
            scanModelsFn = { models.filter { it.file.exists() } },
            diagnoseStatusFn = {
                ModelDiagnostic(
                    errorCode = ModelErrorCode.MODEL_FILE_NOT_FOUND,
                    title = "No Models",
                    message = "Import a model",
                )
            },
            parseMetadataFn = { file -> models.firstOrNull { it.file.absolutePath == file.absolutePath } },
            isGgufBinaryFn = { true },
            importFromUriFn = importFn,
            deleteModelFn = deleteFn,
            memoryStatusFn = {
                DeviceMemoryStatus(
                    totalRamMb = 8192L,
                    availRamMb = 4096L,
                    usedRamMb = 4096L,
                    isLowMemory = false,
                    thresholdMb = 512L,
                )
            },
            supportedAbisProvider = { listOf("arm64-v8a") },
            cpuCoresProvider = { 8 },
        )
        var nowMs = 1000L
        val deps = ChatViewModelDependencies(
            repository = repository,
            modelHub = modelHub,
            prefs = prefs,
            memoryManager = memoryManager,
            engineProvider = engineProvider,
            thermalStateFlow = MutableStateFlow(DeviceThermalState.NOMINAL),
            batteryTempFlow = MutableStateFlow(29.0f),
            batteryPercentFlow = MutableStateFlow(90),
            isChargingFlow = MutableStateFlow(false),
            adaptiveThreadCountFn = { _, count -> count },
            cleanupThermalFn = {},
            speakFn = {},
            stopSpeakFn = {},
            shutdownTtsFn = {},
            isSpeakingFlow = MutableStateFlow(false),
            ioDispatcher = testDispatcher,
            autoInitialize = false,
            clockMs = {
                nowMs += 100L
                nowMs
            },
            streamThrottleMs = streamThrottleMs,
            uriCacheCopier = uriCacheCopier,
        )
        return ChatViewModel.createForTest(app, deps).also { createdViewModels.add(it) }
    }

    private fun TestScope.initReadyEngine(
        models: MutableList<ModelMetadata>,
        file: File,
        engine: InferenceEngine,
    ): ChatViewModel {
        val vm = buildVm(models = models, engineProvider = { engine })
        vm.initializeEngine(explicitModelFile = file)
        advanceUntilIdle()
        assertEquals(EngineState.Ready, vm.chatUiState.value.engineState)
        return vm
    }

    @Test
    fun atomicSendGuardPreventsConcurrentDoubleGeneration() = runTest(testDispatcher) {
        val file = makeModelFile("qwen2.5-1.5b.gguf")
        val meta = makeMetadata(file, "Qwen 2.5 1.5B")
        val streamGate = CompletableDeferred<Unit>()
        val engine = FakeEngine(
            metadata = meta,
            streamGate = streamGate,
            chunksToEmit = listOf(
                GenerationChunk(deltaText = "Only once", isDelta = true, isComplete = false),
                GenerationChunk(
                    isComplete = true,
                    metrics = BenchmarkMetrics(ttftMs = 42, decodeTps = 22.5, decodeTokens = 2),
                ),
            ),
        )
        val vm = initReadyEngine(mutableListOf(meta), file, engine)

        vm.sendMessage("First prompt")
        // Second call immediately while slot is already claimed must be dropped cleanly
        vm.sendMessage("Second prompt")
        advanceUntilIdle()

        assertEquals(1, engine.sendCalls)
        assertTrue(vm.chatUiState.value.isGenerating)

        streamGate.complete(Unit)
        advanceUntilIdle()

        assertFalse(vm.chatUiState.value.isGenerating)
        assertEquals(1, engine.sendCalls)
        val userMessages = vm.chatUiState.value.messages.filter { it.isUser }
        assertEquals(1, userMessages.size)
        assertEquals("First prompt", userMessages.first().text)
    }

    @Test
    fun visionCapabilityDerivedFromEngineNotFilenameAndBlocksImageOnTextModel() = runTest(testDispatcher) {
        val file = makeModelFile("qwen2.5-vision-vl-fake.gguf") // misleading filename
        val meta = makeMetadata(file, "Misleading VL Name", hasVision = false)
        var copyCount = 0
        val engine = FakeEngine(
            metadata = meta,
            capabilities = defaultCapabilities(supportsVision = false, unsupportedVisionReason = "No mmproj loaded"),
        )
        val vm = buildVm(
            models = mutableListOf(meta),
            engineProvider = { engine },
            uriCacheCopier = {
                copyCount++
                File(tempFolder.root, "copied.jpg").apply { writeText("img") }
            },
        )
        vm.initializeEngine(explicitModelFile = file)
        advanceUntilIdle()

        assertFalse(vm.chatUiState.value.isVisionSupported)
        assertEquals("No mmproj loaded", vm.chatUiState.value.visionUnsupportedReason)

        vm.sendMessage("Describe this", imageUri = Uri.parse("content://media/external/images/1"))
        advanceUntilIdle()

        assertEquals(0, engine.sendCalls)
        assertEquals(0, copyCount)
        assertNotNull(vm.chatUiState.value.transientErrorBanner)
    }

    @Test
    fun visionAttachmentAcceptedWhenEngineSupportsVision() = runTest(testDispatcher) {
        val file = makeModelFile("qwen2.5-vl-3b.gguf")
        val meta = makeMetadata(file, "Qwen VL 3B", hasVision = true)
        val copied = File(tempFolder.root, "cached_vlm.jpg").apply { writeText("jpeg") }
        val engine = FakeEngine(
            metadata = meta,
            capabilities = defaultCapabilities(supportsVision = true),
            chunksToEmit = listOf(GenerationChunk(deltaText = "Saw an image", isDelta = true, isComplete = true)),
        )
        val vm = buildVm(
            models = mutableListOf(meta),
            engineProvider = { engine },
            uriCacheCopier = { copied },
        )
        vm.initializeEngine(explicitModelFile = file)
        advanceUntilIdle()

        assertTrue(vm.chatUiState.value.isVisionSupported)
        vm.sendMessage("What is in here?", imageUri = Uri.parse("content://media/external/images/2"))
        advanceUntilIdle()

        assertEquals(listOf(copied.absolutePath), engine.imagePathsSeen)
    }

    @Test
    fun rapidModelSwitchCancelsGenerationUnloadsBeforeLoadingAndConvergesOnLatest() = runTest(testDispatcher) {
        val fileA = makeModelFile("model-a.gguf")
        val fileB = makeModelFile("model-b.gguf")
        val fileC = makeModelFile("model-c.gguf")
        val metaA = makeMetadata(fileA, "Model A")
        val metaB = makeMetadata(fileB, "Model B")
        val metaC = makeMetadata(fileC, "Model C")

        val events = Collections.synchronizedList(mutableListOf<String>())
        val loadBGate = CompletableDeferred<Unit>()
        val streamGateA = CompletableDeferred<Unit>()

        val engineA = FakeEngine(metaA, streamGate = streamGateA, eventLog = events, chunksToEmit = listOf(
            GenerationChunk(deltaText = "Partial A", isDelta = true, isComplete = false),
        ))
        val engineB = FakeEngine(metaB, loadGate = loadBGate, eventLog = events)
        val engineC = FakeEngine(metaC, eventLog = events)

        val vm = buildVm(
            models = mutableListOf(metaA, metaB, metaC),
            engineProvider = { f ->
                when (f.name) {
                    "model-a.gguf" -> engineA
                    "model-b.gguf" -> engineB
                    else -> engineC
                }
            },
        )
        vm.initializeEngine(explicitModelFile = fileA)
        advanceUntilIdle()

        // Start streaming on A, then immediately switch B -> C while A streams & B load suspends
        vm.sendMessage("Hello A")
        runCurrent()
        vm.switchModel(metaB)
        runCurrent()
        vm.switchModel(metaC)

        // Release gates cleanly
        streamGateA.complete(Unit)
        loadBGate.complete(Unit)
        advanceUntilIdle()

        // Must converge cleanly on Model C with Model A cancelled & unloaded before loading B/C
        assertEquals("Model C", vm.chatUiState.value.modelTitle)
        assertEquals(fileC.absolutePath, vm.chatUiState.value.activeModelMetadata?.file?.absolutePath)
        assertEquals(EngineState.Ready, vm.chatUiState.value.engineState)
        assertTrue(engineA.unloadCalls >= 1)
        val cancelIdx = events.indexOfFirst { it.startsWith("cancel:") }
        val unloadAIdx = events.indexOfFirst { it == "unload:Model A" }
        assertTrue("Cancel must happen before unload of Model A: $events", cancelIdx in 0 until unloadAIdx)
    }

    @Test
    fun failedModelLoadDoesNotCorruptExistingModelTitleOrVisionFlag() = runTest(testDispatcher) {
        val goodFile = makeModelFile("good-vision.gguf")
        val badFile = makeModelFile("bad-model.gguf")
        val goodMeta = makeMetadata(goodFile, "Good Vision", hasVision = true)
        val badMeta = makeMetadata(badFile, "Corrupt Model", hasVision = false)

        val goodEngine = FakeEngine(goodMeta, capabilities = defaultCapabilities(supportsVision = true))
        val badEngine = FakeEngine(
            metadata = badMeta,
            shouldFailLoad = true,
            loadErrorCode = ModelErrorCode.MISSING_RUNTIME_OR_INCOMPATIBLE_ABI,
            loadErrorMessage = "Unsupported ABI",
        )
        val vm = buildVm(
            models = mutableListOf(goodMeta, badMeta),
            engineProvider = { f -> if (f.name == goodFile.name) goodEngine else badEngine },
        )
        vm.initializeEngine(explicitModelFile = goodFile)
        advanceUntilIdle()
        assertEquals("Good Vision", vm.chatUiState.value.modelTitle)
        assertTrue(vm.chatUiState.value.isVisionSupported)

        vm.switchModel(badMeta)
        advanceUntilIdle()

        val state = vm.chatUiState.value
        assertTrue(state.engineState is EngineState.Error)
        assertEquals("Good Vision", state.modelTitle)
        assertTrue(state.isVisionSupported)
        assertEquals(goodFile.absolutePath, prefs.activeModelPath)
    }

    @Test
    fun backendFallbackSurfacesHonestFallbackBannerAndUpdatesActiveBackend() = runTest(testDispatcher) {
        val file = makeModelFile("model.gguf")
        val meta = makeMetadata(file, "Qwen Fallback")
        val engine = FakeEngine(
            metadata = meta,
            backendInfo = ExecutionBackendInfo(
                requested = BackendType.GPU,
                active = BackendType.CPU,
                fallbackReason = "OpenCL driver unavailable on device",
                effectiveThreads = 4,
                effectiveContextLength = 4096,
            ),
        )
        prefs.backendType = BackendType.GPU
        val vm = initReadyEngine(mutableListOf(meta), file, engine)

        val state = vm.chatUiState.value
        val banner = state.backendFallbackBanner.orEmpty()
        assertTrue(banner.contains("GPU"))
        assertTrue(banner.contains("CPU"))
        assertTrue(banner.contains("OpenCL driver unavailable"))
        assertEquals(BackendType.CPU, vm.settingsUiState.value.backendType)
        assertTrue(vm.settingsUiState.value.executionBackendInfo?.isFallback == true)
    }

    @Test
    fun stopGenerationPreservesAndPersistsPartialStreamedOutputToSqlite() = runTest(testDispatcher) {
        val file = makeModelFile("model.gguf")
        val meta = makeMetadata(file, "Qwen Stop")
        val streamGate = CompletableDeferred<Unit>()
        val engine = FakeEngine(
            metadata = meta,
            streamGate = streamGate,
            emitBeforeGate = GenerationChunk(deltaText = "Partial answer kept ", isDelta = true),
            chunksToEmit = listOf(GenerationChunk(deltaText = "remaining", isDelta = true, isComplete = true)),
        )
        val vm = initReadyEngine(mutableListOf(meta), file, engine)

        vm.sendMessage("Tell me a story")
        runCurrent()
        // Advance clock once so chunk before gate publishes
        advanceUntilIdle()

        vm.stopGeneration()
        advanceUntilIdle()

        assertFalse(vm.chatUiState.value.isGenerating)
        val bot = vm.chatUiState.value.messages.lastOrNull { !it.isUser }
        assertNotNull(bot)
        assertEquals("Partial answer kept", bot?.text?.trim())
        assertTrue(bot?.isCancelled == true)
        assertFalse(bot?.isStreaming == true)

        val convId = vm.chatUiState.value.activeConversationId!!
        val persisted = repository.getMessages(convId)
        assertEquals(2, persisted.size)
        assertTrue(persisted.last().isCancelled)
        assertEquals("Partial answer kept", persisted.last().text.trim())
    }

    @Test
    fun editAndResendRejectsBlankTextAndReusesExistingImagePathWithoutCopying() = runTest(testDispatcher) {
        val file = makeModelFile("vision.gguf")
        val meta = makeMetadata(file, "Vision", hasVision = true)
        val copied = File(tempFolder.root, "cached_once.jpg").apply { writeText("jpg") }
        var copyCount = 0
        val engine = FakeEngine(
            metadata = meta,
            capabilities = defaultCapabilities(supportsVision = true),
            chunksToEmit = listOf(GenerationChunk(deltaText = "Reply", isDelta = true, isComplete = true)),
        )
        val vm = buildVm(
            models = mutableListOf(meta),
            engineProvider = { engine },
            uriCacheCopier = {
                copyCount++
                copied
            },
        )
        vm.initializeEngine(explicitModelFile = file)
        advanceUntilIdle()

        // First send text-only message
        vm.sendMessage("Original text")
        advanceUntilIdle()
        val firstUser = vm.chatUiState.value.messages.first { it.isUser }

        // Blank edit on text-only turn must be rejected without mutating history
        vm.editAndResendMessage(firstUser.id, "   ")
        advanceUntilIdle()
        assertEquals(2, vm.chatUiState.value.messages.size)
        assertEquals("Original text", vm.chatUiState.value.messages.first { it.isUser }.text)

        // Now send an image turn
        vm.newChat()
        advanceUntilIdle()
        vm.sendMessage("Describe chart", imageUri = Uri.parse("content://img/9"))
        advanceUntilIdle()
        assertEquals(1, copyCount)

        val imageUser = vm.chatUiState.value.messages.first { it.isUser }
        vm.editAndResendMessage(imageUser.id, "List values in chart")
        advanceUntilIdle()

        assertEquals("Should reuse imagePath without extra cache copies", 1, copyCount)
        val updatedUser = vm.chatUiState.value.messages.first { it.isUser }
        assertEquals("List values in chart", updatedUser.text)
        assertEquals(copied.absolutePath, updatedUser.imagePath)
        assertEquals(copied.absolutePath, engine.imagePathsSeen.last())
    }

    @Test
    fun errorMessagesAreExcludedFromPromptHistoryAndRegenerateTargetsTurn() = runTest(testDispatcher) {
        val file = makeModelFile("model.gguf")
        val meta = makeMetadata(file, "Qwen Prompt")
        val engine = FakeEngine(
            metadata = meta,
            chunksToEmit = listOf(GenerationChunk(deltaText = "Clean answer", isDelta = true, isComplete = true)),
        )
        val vm = initReadyEngine(mutableListOf(meta), file, engine)

        // Turn 1: fails inside engine chunk error -> stored with isError = true
        engine.chunksToEmit = listOf(GenerationChunk(error = "OOM mid-decode"))
        vm.sendMessage("First query")
        advanceUntilIdle()
        val errBot = vm.chatUiState.value.messages.last { !it.isUser }
        assertTrue(errBot.isError)

        // Turn 2: valid reply -> history sent to engine MUST NOT include "OOM mid-decode"
        engine.chunksToEmit = listOf(GenerationChunk(deltaText = "Second answer", isDelta = true, isComplete = true))
        vm.sendMessage("Second query")
        advanceUntilIdle()
        assertFalse(engine.promptsSeen.last().contains("OOM mid-decode"))

        // Targeted regenerate on Turn 1 drops Turn 1 bot + Turn 2 and replays Turn 1 query
        engine.chunksToEmit = listOf(GenerationChunk(deltaText = "Recovered turn 1", isDelta = true, isComplete = true))
        vm.regenerateMessage(errBot.id)
        advanceUntilIdle()

        val msgs = vm.chatUiState.value.messages
        assertEquals(2, msgs.size)
        assertEquals("Recovered turn 1", msgs.last().text)
        assertFalse(msgs.last().isError)
    }

    @Test
    fun conversationSwitchIsolatesChatsAndResetsEngineSession() = runTest(testDispatcher) {
        val file = makeModelFile("model.gguf")
        val meta = makeMetadata(file, "Qwen")
        val engine = FakeEngine(
            metadata = meta,
            chunksToEmit = listOf(GenerationChunk(deltaText = "Reply", isDelta = true, isComplete = true)),
        )
        val vm = initReadyEngine(mutableListOf(meta), file, engine)

        vm.sendMessage("Chat Alpha question")
        advanceUntilIdle()
        val convAlpha = vm.navigationUiState.value.conversations.first()

        vm.newChat()
        advanceUntilIdle()
        assertTrue(vm.chatUiState.value.messages.isEmpty())

        vm.sendMessage("Chat Beta question")
        advanceUntilIdle()
        assertEquals(2, vm.navigationUiState.value.conversations.size)

        val resetsBefore = engine.resetSessionCalls
        vm.selectConversation(convAlpha)
        advanceUntilIdle()

        assertTrue(engine.resetSessionCalls > resetsBefore)
        assertEquals(convAlpha.id, vm.chatUiState.value.activeConversationId)
        assertTrue(vm.chatUiState.value.messages.any { it.text == "Chat Alpha question" })
        assertFalse(vm.chatUiState.value.messages.any { it.text == "Chat Beta question" })
    }

    @Test
    fun deleteActiveModelUnloadsEngineFirstAndFallsBackCleanly() = runTest(testDispatcher) {
        val fileA = makeModelFile("model-a.gguf")
        val fileB = makeModelFile("model-b.gguf")
        val metaA = makeMetadata(fileA, "Model A")
        val metaB = makeMetadata(fileB, "Model B")
        val models = mutableListOf(metaA, metaB)
        val events = Collections.synchronizedList(mutableListOf<String>())
        val engineA = FakeEngine(metaA, eventLog = events)
        val engineB = FakeEngine(metaB, eventLog = events)

        val vm = buildVm(
            models = models,
            engineProvider = { f -> if (f.name == fileA.name) engineA else engineB },
            deleteFn = { f ->
                events.add("delete:${f.name}")
                models.removeAll { it.file.name == f.name }
                f.delete()
            },
        )
        vm.initializeEngine(explicitModelFile = fileA)
        advanceUntilIdle()

        vm.deleteModel(metaA)
        advanceUntilIdle()

        val unloadIdx = events.indexOf("unload:Model A")
        val deleteIdx = events.indexOf("delete:model-a.gguf")
        assertTrue("Engine must unload before deleting file: $events", unloadIdx in 0 until deleteIdx)
        assertEquals("Model B", vm.chatUiState.value.modelTitle)
        assertEquals(EngineState.Ready, vm.chatUiState.value.engineState)

        // Delete remaining last model -> Idle cleanly
        vm.deleteModel(metaB)
        advanceUntilIdle()
        assertEquals(EngineState.Idle, vm.chatUiState.value.engineState)
        assertTrue(vm.modelHubUiState.value.discoveredModels.isEmpty())
    }

    @Test
    fun modelImportFailureSurfacesFailedStateAndErrorBanner() = runTest(testDispatcher) {
        val vm = buildVm(
            models = mutableListOf(),
            engineProvider = { FakeEngine(makeMetadata(it, "Dummy")) },
            importFn = { _, _ -> Result.failure(IllegalArgumentException("Invalid GGUF header magic")) },
        )

        vm.importModel(Uri.parse("content://downloads/broken.bin"))
        advanceUntilIdle()

        assertEquals(ImportProgress.FAILED, vm.modelHubUiState.value.importState.progress)
        assertTrue(vm.modelHubUiState.value.importError?.contains("Invalid GGUF") == true)
        assertNotNull(vm.chatUiState.value.transientErrorBanner)

        vm.dismissImportError()
        assertNull(vm.modelHubUiState.value.importError)
        assertEquals(ImportProgress.IDLE, vm.modelHubUiState.value.importState.progress)
    }

    @Test
    fun settingsPersistenceAndNonDestructiveMemoryAndDebouncedConversationSearch() = runTest(testDispatcher) {
        val vm = buildVm(
            models = mutableListOf(),
            engineProvider = { FakeEngine(makeMetadata(it, "Dummy")) },
        )
        advanceUntilIdle()

        vm.setRepetitionPenalty(1.42f)
        vm.setContextLength(2048)
        vm.setHapticsEnabled(false)

        assertEquals(1.42f, prefs.repetitionPenalty, 0.01f)
        assertEquals(2048, prefs.contextLength)
        assertFalse(prefs.hapticsEnabled)
        assertFalse(vm.hapticsEnabled.value)
        assertEquals(2048, vm.settingsUiState.value.contextLength)

        vm.saveMemory(MemoryItem(category = MemoryCategory.USER, content = "Prefers concise Kotlin code"))
        vm.saveMemory(MemoryItem(category = MemoryCategory.PROJECT, content = "Uses SQLite FTS5"))
        advanceUntilIdle()

        assertEquals(2, vm.memoryUiState.value.totalMemoryCount)
        vm.searchMemories("SQLite")
        assertEquals(1, vm.memoryUiState.value.memories.size)
        // Non-destructive: totalMemoryCount and DB stay intact
        assertEquals(2, vm.memoryUiState.value.totalMemoryCount)
        assertEquals(2, repository.getAllMemories().size)
    }

    private fun TestScope.runCurrent() = testScheduler.runCurrent()

    private fun defaultCapabilities(
        supportsVision: Boolean = false,
        unsupportedVisionReason: String? = null,
    ) = EngineCapabilities(
        engineType = EngineType.LLAMA_CPP,
        supportedFormats = listOf(ModelFormat.GGUF),
        supportsVision = supportsVision,
        supportsGpu = true,
        supportsNpu = false,
        maxContextLength = 4096,
        maxOutputTokens = 2048,
        unsupportedVisionReason = unsupportedVisionReason,
    )

    private class FakeEngine(
        private val metadata: ModelMetadata,
        private val capabilities: EngineCapabilities = EngineCapabilities(
            engineType = EngineType.LLAMA_CPP,
            supportedFormats = listOf(ModelFormat.GGUF),
            supportsVision = metadata.hasVision,
            supportsGpu = false,
            supportsNpu = false,
        ),
        private val backendInfo: ExecutionBackendInfo = ExecutionBackendInfo(
            requested = BackendType.CPU,
            active = BackendType.CPU,
            effectiveThreads = 4,
            effectiveContextLength = 4096,
        ),
        private val shouldFailLoad: Boolean = false,
        private val loadErrorCode: ModelErrorCode = ModelErrorCode.MODEL_INITIALIZATION_FAILED,
        private val loadErrorMessage: String? = null,
        private val loadGate: CompletableDeferred<Unit>? = null,
        private val streamGate: CompletableDeferred<Unit>? = null,
        private val emitBeforeGate: GenerationChunk? = null,
        var chunksToEmit: List<GenerationChunk> = emptyList(),
        private val eventLog: MutableList<String>? = null,
    ) : InferenceEngine {

        override var activeModel: ModelMetadata? = null
        override var isLoaded: Boolean = false
        override var isGenerating: Boolean = false

        var sendCalls = 0
        var unloadCalls = 0
        var cancelCalls = 0
        var resetSessionCalls = 0
        val promptsSeen = mutableListOf<String>()
        val imagePathsSeen = mutableListOf<String?>()

        override suspend fun loadModel(modelFile: File, config: GenerationConfig): Result<Unit> =
            if (shouldFailLoad) Result.failure(IllegalStateException(loadErrorMessage ?: "fail"))
            else {
                activeModel = metadata
                isLoaded = true
                Result.success(Unit)
            }

        override suspend fun loadModelWithResult(
            modelPath: String,
            backend: BackendType,
            config: GenerationConfig,
        ): EngineLoadResult {
            eventLog?.add("load:${metadata.name}")
            loadGate?.await()
            return if (shouldFailLoad) {
                EngineLoadResult(
                    success = false,
                    backendInfo = backendInfo,
                    capabilities = capabilities,
                    errorCode = loadErrorCode,
                    errorMessage = loadErrorMessage ?: "load failed",
                )
            } else {
                activeModel = metadata
                isLoaded = true
                EngineLoadResult(
                    success = true,
                    backendInfo = backendInfo,
                    capabilities = capabilities,
                )
            }
        }

        override suspend fun unloadModel() {
            unloadCalls++
            eventLog?.add("unload:${metadata.name}")
            isLoaded = false
            activeModel = null
        }

        override fun sendMessage(
            prompt: String,
            imagePath: String?,
            config: GenerationConfig,
        ): Flow<GenerationChunk> = flow {
            sendCalls++
            promptsSeen.add(prompt)
            imagePathsSeen.add(imagePath)
            isGenerating = true
            try {
                emitBeforeGate?.let { emit(it) }
                streamGate?.await()
                for (chunk in chunksToEmit) {
                    emit(chunk)
                }
            } finally {
                isGenerating = false
            }
        }

        override fun cancelGeneration() {
            cancelCalls++
            eventLog?.add("cancel:${metadata.name}")
            isGenerating = false
        }

        override suspend fun resetSession(systemPrompt: String?, history: List<ChatMessage>): Boolean {
            resetSessionCalls++
            return true
        }

        override fun getCapabilities(): EngineCapabilities = capabilities
        override fun getBenchmarkMetrics(): BenchmarkMetrics? = null
        override fun getExecutionBackendInfo(): ExecutionBackendInfo = backendInfo
    }
}
