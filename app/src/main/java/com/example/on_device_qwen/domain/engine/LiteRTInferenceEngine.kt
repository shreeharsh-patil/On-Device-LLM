package com.example.on_device_qwen.domain.engine

import android.content.Context
import com.example.on_device_qwen.domain.context.PromptContextBuilder
import com.example.on_device_qwen.model.BackendType
import com.example.on_device_qwen.model.BenchmarkMetrics
import com.example.on_device_qwen.model.ChatMessage
import com.example.on_device_qwen.model.ChatTemplateFamily
import com.example.on_device_qwen.model.ExecutionBackendInfo
import com.example.on_device_qwen.model.GenerationConfig
import com.example.on_device_qwen.model.Modality
import com.example.on_device_qwen.model.ModelCompatibilityStatus
import com.example.on_device_qwen.model.ModelErrorCode
import com.example.on_device_qwen.model.ModelFormat
import com.example.on_device_qwen.model.ModelMetadata
import com.example.on_device_qwen.model.EngineType
import com.example.on_device_qwen.model.sanitizeSystemPrompt
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.RepetitionPenaltyConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import com.google.ai.edge.litertlm.BackendType as LiteRtBackendType
import com.google.ai.edge.litertlm.Message as LiteRtMessage
import com.google.ai.edge.litertlm.Modality as LiteRtModality
import com.google.ai.edge.litertlm.ModelInfo as LiteRtModelInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

@OptIn(ExperimentalApi::class)
class LiteRTInferenceEngine(private val context: Context) : InferenceEngine {

    private val lifecycleMutex = Mutex()
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var engine: Engine? = null
    @Volatile private var conversation: Conversation? = null
    @Volatile private var _activeModel: ModelMetadata? = null
    @Volatile private var currentBackendType: BackendType = BackendType.CPU
    @Volatile private var requestedBackendType: BackendType = BackendType.CPU
    @Volatile private var lastFallbackReason: String? = null
    @Volatile private var effectiveThreads: Int = 4
    @Volatile private var effectiveContextLength: Int = 4096
    @Volatile private var lastConfig: GenerationConfig = GenerationConfig()
    @Volatile private var activeSupportsThinking: Boolean = false
    @Volatile private var activeSupportedTextBackends: List<LiteRtBackendType> = emptyList()
    @Volatile private var lastBenchmark: BenchmarkMetrics? = null

    @Volatile private var currentSystemPrompt: String = GenerationConfig.DEFAULT_SYSTEM_PROMPT
    private val sessionHistory = mutableListOf<ChatMessage>()
    @Volatile private var pendingResetSystemPrompt: String? = null
    @Volatile private var hasPendingReset: Boolean = false

    private val _isGenerating = AtomicBoolean(false)
    private val _isCancelled = AtomicBoolean(false)

    override val activeModel: ModelMetadata? get() = _activeModel
    override val isLoaded: Boolean get() = engine != null && conversation != null
    override val isGenerating: Boolean get() = _isGenerating.get()

    private data class ProbedLiteRtMetadata(
        val supportsVision: Boolean = false,
        val supportsThinking: Boolean = false,
        val maxContextTokens: Int? = null,
        val textBackends: List<LiteRtBackendType> = emptyList(),
        val visionBackends: List<LiteRtBackendType> = emptyList(),
        val unsupportedVisionReason: String? = null,
    )

    private fun probeModelMetadata(modelFile: File): ProbedLiteRtMetadata {
        return try {
            LiteRtModelInfo.from(modelFile.absolutePath).use { info ->
                val modalities = info.inputModalities()
                val textBackends = runCatching { info.supportedBackends(LiteRtModality.TEXT) }.getOrDefault(emptyList())
                val visionBackends = if (modalities.vision) {
                    runCatching { info.supportedBackends(LiteRtModality.VISION) }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }
                val thinkingSupported = (info as? LiteRtModelInfo.Llm)
                    ?.let { runCatching { it.supportsThinking() }.getOrNull() }
                    ?: false
                val maxCtx = runCatching { info.maxContextTokens() }.getOrNull()?.takeIf { it > 0 }
                val reason = if (!modalities.vision) {
                    "This LiteRT model does not declare vision input modality in its bundle metadata."
                } else null
                ProbedLiteRtMetadata(
                    supportsVision = modalities.vision,
                    supportsThinking = thinkingSupported,
                    maxContextTokens = maxCtx,
                    textBackends = textBackends,
                    visionBackends = visionBackends,
                    unsupportedVisionReason = reason,
                )
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            ProbedLiteRtMetadata(
                supportsVision = false,
                unsupportedVisionReason = "LiteRT model metadata could not confirm vision support (${t.message ?: t.javaClass.simpleName}).",
            )
        }
    }

    private fun toLiteRtBackend(backend: BackendType, threads: Int): Backend = when (backend) {
        BackendType.CPU -> Backend.CPU(threadCount = threads)
        BackendType.GPU -> Backend.GPU()
        BackendType.NPU -> Backend.NPU(context.applicationInfo.nativeLibraryDir)
    }

    private fun toLiteRtBackendType(backend: BackendType): LiteRtBackendType = when (backend) {
        BackendType.CPU -> LiteRtBackendType.CPU
        BackendType.GPU -> LiteRtBackendType.GPU
        BackendType.NPU -> LiteRtBackendType.NPU
    }

    private fun buildEngineConfig(
        modelPath: String,
        backend: Backend,
        visionBackend: Backend?,
        supportsVision: Boolean,
        maxTokens: Int,
        cacheDir: String,
    ): EngineConfig {
        return EngineConfig(
            modelPath = modelPath,
            backend = backend,
            visionBackend = if (supportsVision) visionBackend else null,
            maxNumTokens = maxTokens,
            maxNumImages = if (supportsVision) 1 else null,
            cacheDir = cacheDir,
        )
    }

    private fun buildConversationConfig(
        systemPrompt: String,
        history: List<ChatMessage>,
        config: GenerationConfig,
        contextLimit: Int,
        thinkingSupported: Boolean,
    ): ConversationConfig {
        val resolvedPrompt = sanitizeSystemPrompt(systemPrompt)
        val reservedOutput = config.maxOutputTokens.coerceIn(
            GenerationConfig.MIN_MAX_TOKENS,
            (contextLimit / 2).coerceAtLeast(GenerationConfig.MIN_MAX_TOKENS),
        )
        val initialMsgs = toLiteRtInitialMessages(
            history = history,
            maxHistoryTokens = (contextLimit - PromptContextBuilder.estimateTokens(resolvedPrompt) - reservedOutput - 128)
                .coerceAtLeast(0),
        )
        val clampedTopK = config.topK.coerceIn(GenerationConfig.MIN_TOP_K, GenerationConfig.MAX_TOP_K).let {
            if (it <= 0) 1 else it
        }
        val clampedTemp = config.temperature.toDouble().coerceIn(
            GenerationConfig.MIN_TEMPERATURE.toDouble(),
            GenerationConfig.MAX_TEMPERATURE.toDouble(),
        )
        val clampedTopP = config.topP.toDouble().coerceIn(
            GenerationConfig.MIN_TOP_P.toDouble(),
            GenerationConfig.MAX_TOP_P.toDouble(),
        )
        val seed = if (config.seed >= 0) config.seed else 0
        return ConversationConfig(
            systemInstruction = Contents.of(resolvedPrompt),
            initialMessages = initialMsgs,
            samplerConfig = SamplerConfig(
                topK = clampedTopK,
                topP = clampedTopP,
                temperature = clampedTemp,
                seed = seed,
            ),
            maxOutputToken = reservedOutput,
            thinkingConfig = if (thinkingSupported) {
                ThinkingConfig(enableThinking = true)
            } else {
                null
            },
        )
    }

    private fun closeNativeObjectsUnchecked() {
        try {
            conversation?.close()
        } catch (_: Throwable) {
        }
        try {
            engine?.close()
        } catch (_: Throwable) {
        }
        conversation = null
        engine = null
        _activeModel = null
        activeSupportedTextBackends = emptyList()
        activeSupportsThinking = false
        _isGenerating.set(false)
        _isCancelled.set(false)
    }

    private fun recreateConversationLocked(
        systemPrompt: String = currentSystemPrompt,
        history: List<ChatMessage> = sessionHistory.toList(),
    ): Boolean {
        val initializedEngine = engine ?: return false
        try {
            conversation?.close()
        } catch (_: Throwable) {
        }
        conversation = null
        val resolvedSystem = sanitizeSystemPrompt(systemPrompt)
        currentSystemPrompt = resolvedSystem
        sessionHistory.clear()
        sessionHistory.addAll(history)
        val convConfig = buildConversationConfig(
            systemPrompt = resolvedSystem,
            history = history,
            config = lastConfig,
            contextLimit = effectiveContextLength,
            thinkingSupported = activeSupportsThinking,
        )
        conversation = initializedEngine.createConversation(convConfig)
        hasPendingReset = false
        pendingResetSystemPrompt = null
        return true
    }

    override suspend fun loadModel(modelFile: File, config: GenerationConfig): Result<Unit> {
        val result = loadModelWithResult(modelFile.absolutePath, config.backend, config)
        return if (result.success) {
            Result.success(Unit)
        } else {
            val ex = when (result.errorCode) {
                ModelErrorCode.MODEL_FILE_NOT_FOUND -> FileNotFoundException(result.errorMessage ?: "Model not found")
                ModelErrorCode.FILE_UNREADABLE_OR_PERMISSION_DENIED -> SecurityException(result.errorMessage ?: "Model unreadable")
                ModelErrorCode.MISSING_RUNTIME_OR_INCOMPATIBLE_ABI -> UnsupportedOperationException(result.errorMessage ?: "Unsupported ABI")
                else -> IllegalStateException(result.errorMessage ?: "LiteRT initialization failed")
            }
            Result.failure(ex)
        }
    }

    override suspend fun loadModelWithResult(
        modelPath: String,
        backend: BackendType,
        config: GenerationConfig,
    ): EngineLoadResult = withContext(Dispatchers.IO) {
        cancelGeneration()
        lifecycleMutex.withLock {
            val modelFile = File(modelPath)
            val resolvedThreads = config.threadCount.coerceIn(
                GenerationConfig.MIN_THREAD_COUNT,
                GenerationConfig.MAX_THREAD_COUNT,
            )
            requestedBackendType = backend
            effectiveThreads = resolvedThreads
            lastConfig = config.copy(backend = backend, threadCount = resolvedThreads)

            try {
                closeNativeObjectsUnchecked()

                if (!modelFile.exists()) {
                    return@withLock failureLoadResult(
                        requested = backend,
                        code = ModelErrorCode.MODEL_FILE_NOT_FOUND,
                        message = "LiteRT model file does not exist: ${modelFile.absolutePath}",
                    )
                }
                if (!modelFile.canRead()) {
                    return@withLock failureLoadResult(
                        requested = backend,
                        code = ModelErrorCode.FILE_UNREADABLE_OR_PERMISSION_DENIED,
                        message = "LiteRT model file is unreadable (permission denied): ${modelFile.absolutePath}",
                    )
                }
                if (modelFile.length() < 512L * 1024L) {
                    return@withLock failureLoadResult(
                        requested = backend,
                        code = ModelErrorCode.MODEL_INITIALIZATION_FAILED,
                        message = "LiteRT model file is too small or incomplete (${modelFile.length()} bytes): ${modelFile.name}",
                    )
                }

                val probe = probeModelMetadata(modelFile)
                activeSupportsThinking = probe.supportsThinking
                activeSupportedTextBackends = probe.textBackends

                val desiredContext = config.contextLength.coerceAtLeast(GenerationConfig.MIN_CONTEXT_LENGTH)
                val maxSupportedCtx = probe.maxContextTokens ?: GenerationConfig.MAX_CONTEXT_LENGTH
                val clampedCtx = desiredContext.coerceAtMost(maxSupportedCtx).coerceAtLeast(256)
                effectiveContextLength = clampedCtx

                val cacheDir = File(context.cacheDir, "litert_lm_cache").apply { mkdirs() }.absolutePath
                ExperimentalFlags.enableBenchmark = true

                var chosenBackend = backend
                var fallbackReason: String? = null
                val requestedLiteRtBackend = toLiteRtBackendType(backend)
                val primarySupportedByMetadata = probe.textBackends.isEmpty() ||
                    requestedLiteRtBackend in probe.textBackends

                val initializedEngine: Engine = if (backend != BackendType.CPU && !primarySupportedByMetadata) {
                    chosenBackend = BackendType.CPU
                    fallbackReason = "${backend.displayLabel(EngineType.LITERT)} is not declared in this model's supported backends (${probe.textBackends.joinToString()}); using CPU ($resolvedThreads threads)."
                    val cpuConfig = buildEngineConfig(
                        modelPath = modelFile.absolutePath,
                        backend = Backend.CPU(threadCount = resolvedThreads),
                        visionBackend = if (probe.supportsVision) Backend.CPU(threadCount = resolvedThreads) else null,
                        supportsVision = probe.supportsVision,
                        maxTokens = clampedCtx,
                        cacheDir = cacheDir,
                    )
                    Engine(cpuConfig).also { it.initialize() }
                } else {
                    try {
                        val primaryVisionBackend = when {
                            !probe.supportsVision -> null
                            backend == BackendType.GPU && LiteRtBackendType.GPU in probe.visionBackends -> Backend.GPU()
                            else -> Backend.CPU(threadCount = resolvedThreads)
                        }
                        val primaryConfig = buildEngineConfig(
                            modelPath = modelFile.absolutePath,
                            backend = toLiteRtBackend(backend, resolvedThreads),
                            visionBackend = primaryVisionBackend,
                            supportsVision = probe.supportsVision,
                            maxTokens = clampedCtx,
                            cacheDir = cacheDir,
                        )
                        Engine(primaryConfig).also { it.initialize() }
                    } catch (primaryErr: Throwable) {
                        if (primaryErr is CancellationException) throw primaryErr
                        if (backend == BackendType.CPU) throw primaryErr
                        chosenBackend = BackendType.CPU
                        fallbackReason = "Requested ${backend.displayLabel(EngineType.LITERT)} backend unavailable (${primaryErr.message ?: primaryErr.javaClass.simpleName}); fell back to CPU ($resolvedThreads threads)."
                        val cpuConfig = buildEngineConfig(
                            modelPath = modelFile.absolutePath,
                            backend = Backend.CPU(threadCount = resolvedThreads),
                            visionBackend = if (probe.supportsVision) Backend.CPU(threadCount = resolvedThreads) else null,
                            supportsVision = probe.supportsVision,
                            maxTokens = clampedCtx,
                            cacheDir = cacheDir,
                        )
                        Engine(cpuConfig).also { it.initialize() }
                    }
                }

                engine = initializedEngine
                currentBackendType = chosenBackend
                lastFallbackReason = fallbackReason

                val sanitizedSystem = sanitizeSystemPrompt(config.systemPrompt)
                currentSystemPrompt = sanitizedSystem
                sessionHistory.clear()
                hasPendingReset = false
                pendingResetSystemPrompt = null

                conversation = initializedEngine.createConversation(
                    buildConversationConfig(
                        systemPrompt = sanitizedSystem,
                        history = emptyList(),
                        config = lastConfig,
                        contextLimit = clampedCtx,
                        thinkingSupported = activeSupportsThinking,
                    ),
                )

                val fileBytes = modelFile.length()
                val estRamBytes = (fileBytes * 1.2).toLong().coerceAtLeast(fileBytes)
                val estKvMb = ((clampedCtx.toLong() * 256L * 1024L) / (1024L * 1024L)).coerceAtLeast(32L)
                _activeModel = ModelMetadata(
                    file = modelFile,
                    name = modelFile.nameWithoutExtension,
                    format = ModelFormat.LITERT,
                    engineType = EngineType.LITERT,
                    architecture = if (probe.supportsVision) "litert-vlm" else "litert-llm",
                    quantization = "LiteRT Optimized",
                    contextLength = clampedCtx,
                    fileSizeBytes = fileBytes,
                    estimatedRamBytes = estRamBytes,
                    hasVision = probe.supportsVision,
                    templateFamily = ChatTemplateFamily.GEMMA,
                    supportedModalities = if (probe.supportsVision) {
                        setOf(Modality.TEXT, Modality.VISION)
                    } else {
                        setOf(Modality.TEXT)
                    },
                    visionUnsupportedReason = probe.unsupportedVisionReason,
                    recommendedRamMb = ((estRamBytes / (1024L * 1024L)) + estKvMb + 256L),
                    minRamMb = ((fileBytes / (1024L * 1024L)) + estKvMb),
                    estimatedKvRamMb = estKvMb,
                    compatibilityStatus = ModelCompatibilityStatus.COMPATIBLE,
                )

                EngineLoadResult(
                    success = true,
                    backendInfo = getExecutionBackendInfo(),
                    capabilities = getCapabilities(),
                )
            } catch (ce: CancellationException) {
                withContext(NonCancellable) {
                    closeNativeObjectsUnchecked()
                }
                throw ce
            } catch (t: Throwable) {
                withContext(NonCancellable) {
                    closeNativeObjectsUnchecked()
                }
                val code = when (t) {
                    is UnsatisfiedLinkError, is UnsupportedOperationException -> ModelErrorCode.MISSING_RUNTIME_OR_INCOMPATIBLE_ABI
                    is SecurityException -> ModelErrorCode.FILE_UNREADABLE_OR_PERMISSION_DENIED
                    is FileNotFoundException -> ModelErrorCode.MODEL_FILE_NOT_FOUND
                    else -> ModelErrorCode.MODEL_INITIALIZATION_FAILED
                }
                failureLoadResult(
                    requested = backend,
                    code = code,
                    message = t.localizedMessage ?: t.message ?: "LiteRT initialization failed",
                )
            }
        }
    }

    private fun failureLoadResult(
        requested: BackendType,
        code: ModelErrorCode,
        message: String,
    ): EngineLoadResult {
        currentBackendType = BackendType.CPU
        lastFallbackReason = message
        return EngineLoadResult(
            success = false,
            backendInfo = ExecutionBackendInfo(
                requested = requested,
                active = BackendType.CPU,
                fallbackReason = message,
                effectiveThreads = effectiveThreads,
                effectiveContextLength = effectiveContextLength,
            ),
            capabilities = getCapabilities(),
            errorCode = code,
            errorMessage = message,
        )
    }

    override suspend fun unloadModel() {
        cancelGeneration()
        withContext(Dispatchers.IO) {
            lifecycleMutex.withLock {
                withContext(NonCancellable) {
                    closeNativeObjectsUnchecked()
                    sessionHistory.clear()
                    hasPendingReset = false
                    pendingResetSystemPrompt = null
                }
            }
        }
    }

    override fun sendMessage(
        prompt: String,
        imagePath: String?,
        config: GenerationConfig,
    ): Flow<GenerationChunk> = channelFlow {
        lifecycleMutex.withLock {
            if (hasPendingReset) {
                recreateConversationLocked(pendingResetSystemPrompt ?: currentSystemPrompt, emptyList())
            }
            val activeConv = conversation ?: throw IllegalStateException("LiteRT model is not loaded")
            if (!_isGenerating.compareAndSet(false, true)) {
                throw IllegalStateException("LiteRT generation is already in progress")
            }
            _isCancelled.set(false)

            val cleanUserPrompt = extractPlainUserTurn(prompt)
            val startMs = System.currentTimeMillis()
            var firstTokenMs: Long? = null
            var chunkCount = 0
            val textBuffer = StringBuilder()
            val thinkingBuffer = StringBuilder()
            var emittedTextLength = 0
            var emittedThinkingLength = 0
            var lastEmitMs = 0L
            val batchIntervalMs = config.streamBatchIntervalMs.coerceIn(
                GenerationConfig.MIN_STREAM_BATCH_INTERVAL_MS,
                GenerationConfig.MAX_STREAM_BATCH_INTERVAL_MS,
            )
            val backendLabel = currentBackendType.chipText
            val promptTokens = PromptContextBuilder.estimateTokens(cleanUserPrompt)

            val canAttachImage = !imagePath.isNullOrBlank() &&
                _activeModel?.hasVision == true &&
                File(imagePath).exists()

            val repConfig = if (config.repetitionPenalty != 1.0f) {
                RepetitionPenaltyConfig(
                    repetitionPenalty = config.repetitionPenalty.coerceIn(
                        GenerationConfig.MIN_REPETITION_PENALTY,
                        GenerationConfig.MAX_REPETITION_PENALTY,
                    ),
                )
            } else {
                null
            }
            val maxOut = config.maxOutputTokens.coerceIn(
                GenerationConfig.MIN_MAX_TOKENS,
                effectiveContextLength.coerceAtLeast(GenerationConfig.MIN_MAX_TOKENS),
            )
            val turnThinkingConfig = if (activeSupportsThinking) {
                ThinkingConfig(enableThinking = true)
            } else {
                null
            }

            fun buildLiveMetrics(now: Long, currentText: String): BenchmarkMetrics {
                val first = firstTokenMs ?: now
                val ttft = (first - startMs).coerceAtLeast(0L)
                val decodeDurationSec = (now - first) / 1000.0
                val decodeTokens = maxOf(chunkCount, PromptContextBuilder.estimateTokens(currentText))
                val tps = if (decodeDurationSec > 0.05) decodeTokens / decodeDurationSec else 0.0
                return BenchmarkMetrics(
                    ttftMs = ttft,
                    decodeTps = tps,
                    prefillTokens = promptTokens,
                    decodeTokens = decodeTokens,
                    totalDurationMs = (now - startMs).coerceAtLeast(0L),
                    backend = backendLabel,
                )
            }

            try {
                val messageFlow: Flow<LiteRtMessage> = if (canAttachImage) {
                    val contents = Contents.of(
                        Content.ImageFile(imagePath!!),
                        Content.Text(cleanUserPrompt),
                    )
                    activeConv.sendMessageAsync(
                        contents,
                        emptyMap(),
                        repConfig,
                        null,
                        null,
                        maxOut,
                        turnThinkingConfig,
                    )
                } else {
                    activeConv.sendMessageAsync(
                        cleanUserPrompt,
                        emptyMap(),
                        repConfig,
                        null,
                        null,
                        maxOut,
                        turnThinkingConfig,
                    )
                }

                messageFlow.collect { msg ->
                    if (!currentCoroutineContext().isActive) {
                        throw CancellationException("Collector coroutine cancelled")
                    }
                    if (_isCancelled.get()) {
                        throw LocalStopCancellationException()
                    }

                    val (textPiece, thoughtPiece) = extractMessageParts(msg)
                    if (textPiece.isEmpty() && thoughtPiece.isEmpty()) return@collect

                    val now = System.currentTimeMillis()
                    val isFirstEmission = firstTokenMs == null
                    if (isFirstEmission) firstTokenMs = now
                    chunkCount++

                    if (textPiece.isNotEmpty()) textBuffer.append(textPiece)
                    if (thoughtPiece.isNotEmpty()) thinkingBuffer.append(thoughtPiece)

                    if (isFirstEmission || (now - lastEmitMs) >= batchIntervalMs) {
                        lastEmitMs = now
                        val deltaText = textBuffer.substring(emittedTextLength)
                        val deltaThinking = thinkingBuffer.substring(emittedThinkingLength)
                        emittedTextLength = textBuffer.length
                        emittedThinkingLength = thinkingBuffer.length
                        val fullText = textBuffer.toString()
                        val fullThinking = thinkingBuffer.toString().ifEmpty { null }

                        trySend(
                            GenerationChunk(
                                text = fullText,
                                isComplete = false,
                                metrics = buildLiveMetrics(now, fullText),
                                isDelta = false,
                                generationId = config.generationId,
                                deltaText = deltaText,
                                deltaThinking = deltaThinking,
                                thinking = fullThinking,
                            ),
                        )
                    }
                }

                val now = System.currentTimeMillis()
                val totalMs = (now - startMs).coerceAtLeast(0L)
                val benchInfo = runCatching { activeConv.getBenchmarkInfo() }.getOrNull()
                val finalText = textBuffer.toString()
                val finalThinking = thinkingBuffer.toString().ifEmpty { null }
                val deltaText = textBuffer.substring(emittedTextLength)
                val deltaThinking = thinkingBuffer.substring(emittedThinkingLength)

                val fallbackFirst = firstTokenMs ?: now
                val ttftMs = (fallbackFirst - startMs).coerceAtLeast(0L)
                val decodeSec = (totalMs - ttftMs) / 1000.0
                val estDecode = maxOf(chunkCount, PromptContextBuilder.estimateTokens(finalText))
                val actualDecode = benchInfo?.lastDecodeTokenCount?.takeIf { it > 0 } ?: estDecode
                val actualPrefill = benchInfo?.lastPrefillTokenCount?.takeIf { it > 0 } ?: promptTokens

                val finalMetrics = if (benchInfo != null && benchInfo.lastDecodeTokensPerSecond > 0) {
                    val benchTtft = (benchInfo.timeToFirstTokenInSecond * 1000.0).toLong().coerceAtLeast(0L)
                    BenchmarkMetrics(
                        ttftMs = if (benchTtft > 0L) benchTtft else ttftMs,
                        decodeTps = benchInfo.lastDecodeTokensPerSecond,
                        prefillTps = benchInfo.lastPrefillTokensPerSecond.takeIf { it > 0.0 },
                        prefillTokens = actualPrefill,
                        decodeTokens = actualDecode,
                        totalDurationMs = totalMs,
                        backend = backendLabel,
                    )
                } else {
                    val tps = if (decodeSec > 0.05) actualDecode / decodeSec else 0.0
                    BenchmarkMetrics(
                        ttftMs = ttftMs,
                        decodeTps = tps,
                        prefillTokens = actualPrefill,
                        decodeTokens = actualDecode,
                        totalDurationMs = totalMs,
                        backend = backendLabel,
                    )
                }

                lastBenchmark = finalMetrics
                if (cleanUserPrompt.isNotBlank() && finalText.isNotBlank()) {
                    val convId = sessionHistory.lastOrNull()?.conversationId ?: "litert_active"
                    sessionHistory.add(
                        ChatMessage(
                            id = System.nanoTime(),
                            conversationId = convId,
                            text = cleanUserPrompt,
                            isUser = true,
                        ),
                    )
                    sessionHistory.add(
                        ChatMessage(
                            id = System.nanoTime() + 1L,
                            conversationId = convId,
                            text = finalText,
                            isUser = false,
                            thinkText = finalThinking,
                        ),
                    )
                }

                trySend(
                    GenerationChunk(
                        text = finalText,
                        isComplete = true,
                        metrics = finalMetrics,
                        isDelta = false,
                        generationId = config.generationId,
                        deltaText = deltaText,
                        deltaThinking = deltaThinking,
                        thinking = finalThinking,
                    ),
                )
            } catch (_: LocalStopCancellationException) {
                emitCancelledAndRecover(
                    textBuffer = textBuffer,
                    thinkingBuffer = thinkingBuffer,
                    emittedTextLength = emittedTextLength,
                    emittedThinkingLength = emittedThinkingLength,
                    startMs = startMs,
                    firstTokenMs = firstTokenMs,
                    chunkCount = chunkCount,
                    promptTokens = promptTokens,
                    backendLabel = backendLabel,
                    generationId = config.generationId,
                    emitChunk = true,
                )
            } catch (ce: CancellationException) {
                val stillActive = coroutineContext.isActive
                emitCancelledAndRecover(
                    textBuffer = textBuffer,
                    thinkingBuffer = thinkingBuffer,
                    emittedTextLength = emittedTextLength,
                    emittedThinkingLength = emittedThinkingLength,
                    startMs = startMs,
                    firstTokenMs = firstTokenMs,
                    chunkCount = chunkCount,
                    promptTokens = promptTokens,
                    backendLabel = backendLabel,
                    generationId = config.generationId,
                    emitChunk = _isCancelled.get() && stillActive,
                )
                throw ce
            } catch (t: Throwable) {
                withContext(NonCancellable) {
                    recreateConversationLocked(currentSystemPrompt, sessionHistory.toList())
                }
                val message = t.localizedMessage ?: t.message ?: "LiteRT generation failed"
                trySend(
                    GenerationChunk(
                        text = textBuffer.toString(),
                        isComplete = true,
                        metrics = buildLiveMetrics(System.currentTimeMillis(), textBuffer.toString()),
                        isDelta = false,
                        generationId = config.generationId,
                        deltaText = textBuffer.substring(emittedTextLength),
                        deltaThinking = thinkingBuffer.substring(emittedThinkingLength),
                        thinking = thinkingBuffer.toString().ifEmpty { null },
                        error = message,
                    ),
                )
            } finally {
                _isGenerating.set(false)
                _isCancelled.set(false)
            }
        }
    }.flowOn(Dispatchers.IO)

    private class LocalStopCancellationException : RuntimeException("Generation cancelled by caller")

    private suspend fun kotlinx.coroutines.channels.ProducerScope<GenerationChunk>.emitCancelledAndRecover(
        textBuffer: StringBuilder,
        thinkingBuffer: StringBuilder,
        emittedTextLength: Int,
        emittedThinkingLength: Int,
        startMs: Long,
        firstTokenMs: Long?,
        chunkCount: Int,
        promptTokens: Int,
        backendLabel: String,
        generationId: String,
        emitChunk: Boolean,
    ) {
        withContext(NonCancellable) {
            try {
                conversation?.cancelProcess()
            } catch (_: Throwable) {
            }
            recreateConversationLocked(currentSystemPrompt, sessionHistory.toList())
        }
        if (emitChunk) {
            val now = System.currentTimeMillis()
            val cancelledText = textBuffer.toString()
            val cancelledThinking = thinkingBuffer.toString().ifEmpty { null }
            val metrics = BenchmarkMetrics(
                ttftMs = ((firstTokenMs ?: now) - startMs).coerceAtLeast(0L),
                decodeTps = 0.0,
                prefillTokens = promptTokens,
                decodeTokens = maxOf(chunkCount, PromptContextBuilder.estimateTokens(cancelledText)),
                totalDurationMs = (now - startMs).coerceAtLeast(0L),
                backend = backendLabel,
            )
            trySend(
                GenerationChunk(
                    text = cancelledText,
                    isComplete = true,
                    metrics = metrics,
                    isDelta = false,
                    generationId = generationId,
                    deltaText = textBuffer.substring(emittedTextLength),
                    deltaThinking = thinkingBuffer.substring(emittedThinkingLength),
                    thinking = cancelledThinking,
                    isCancelled = true,
                ),
            )
        }
    }

    override fun cancelGeneration() {
        if (_isGenerating.get()) {
            _isCancelled.set(true)
            try {
                conversation?.cancelProcess()
            } catch (_: Throwable) {
            }
        }
    }

    override fun resetConversation() {
        resetConversationWithSystemPrompt(null)
    }

    /** Mode-aware synchronous fallback for callers outside coroutines; prefers [resetSession]. */
    fun resetConversationWithSystemPrompt(systemPrompt: String?) {
        cancelGeneration()
        val resolved = systemPrompt?.let { sanitizeSystemPrompt(it) } ?: GenerationConfig.DEFAULT_SYSTEM_PROMPT
        pendingResetSystemPrompt = resolved
        hasPendingReset = true
        if (lifecycleMutex.tryLock()) {
            try {
                recreateConversationLocked(resolved, emptyList())
            } catch (_: Throwable) {
            } finally {
                lifecycleMutex.unlock()
            }
        } else {
            backgroundScope.launch {
                lifecycleMutex.withLock {
                    if (hasPendingReset) {
                        recreateConversationLocked(pendingResetSystemPrompt ?: resolved, emptyList())
                    }
                }
            }
        }
    }

    override suspend fun resetSession(
        systemPrompt: String?,
        history: List<ChatMessage>,
    ): Boolean = withContext(Dispatchers.IO) {
        cancelGeneration()
        lifecycleMutex.withLock {
            try {
                val resolved = systemPrompt?.let { sanitizeSystemPrompt(it) }
                    ?: GenerationConfig.DEFAULT_SYSTEM_PROMPT
                recreateConversationLocked(resolved, history)
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                false
            }
        }
    }

    /**
     * LiteRT-LM exposes only total conversation token count, not raw text tokenization.
     * Keep [countTokens] returning null (`supportsNativeTokenCount = false`) and expose
     * session total via [getSessionTokenCount].
     */
    override suspend fun countTokens(text: String): Int? = null

    fun getSessionTokenCount(): Int? {
        val conv = conversation ?: return null
        return runCatching {
            if (conv.isAlive) conv.getTokenCount() else null
        }.getOrNull()
    }

    override fun getExecutionBackendInfo(): ExecutionBackendInfo {
        return ExecutionBackendInfo(
            requested = requestedBackendType,
            active = currentBackendType,
            fallbackReason = lastFallbackReason,
            effectiveThreads = effectiveThreads,
            effectiveContextLength = effectiveContextLength,
        )
    }

    override fun getCapabilities(): EngineCapabilities {
        val model = _activeModel
        return EngineCapabilities(
            engineType = EngineType.LITERT,
            supportedFormats = listOf(ModelFormat.LITERT),
            supportsVision = model?.hasVision ?: false,
            supportsGpu = if (model != null) {
                activeSupportedTextBackends.isEmpty() || LiteRtBackendType.GPU in activeSupportedTextBackends
            } else {
                true
            },
            supportsNpu = if (model != null) {
                activeSupportedTextBackends.isEmpty() || LiteRtBackendType.NPU in activeSupportedTextBackends
            } else {
                true
            },
            supportsSystemPrompt = true,
            supportsReasoningTags = activeSupportsThinking || model == null,
            supportsKvReuse = true,
            supportsNativeTokenCount = false,
            maxContextLength = effectiveContextLength,
            maxOutputTokens = effectiveContextLength.coerceAtMost(GenerationConfig.MAX_MAX_TOKENS),
            maxThreads = GenerationConfig.MAX_THREAD_COUNT,
            unsupportedVisionReason = model?.visionUnsupportedReason,
        )
    }

    override fun getBenchmarkMetrics(): BenchmarkMetrics? = lastBenchmark

    companion object {
        internal fun extractMessageParts(message: LiteRtMessage): Pair<String, String> {
            val text = message.contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString("") { it.text }
            val thought = message.channels["thought"]
                ?: message.channels["thinking"]
                ?: message.channels["reasoning"]
                ?: ""
            return text to thought
        }

        /**
         * Defensively extracts only the latest raw user utterance if a caller passes a full
         * ChatML-formatted prompt block, preventing duplicated system/history prompts in LiteRT.
         */
        internal fun extractPlainUserTurn(prompt: String): String {
            if (!prompt.contains("<|im_start|>")) return prompt
            val userMarker = "<|im_start|>user\n"
            val lastUserIdx = prompt.lastIndexOf(userMarker)
            if (lastUserIdx == -1) {
                return prompt
                    .replace(Regex("""<\|im_start\|>system[\s\S]*?<\|im_end\|>\s*"""), "")
                    .replace("<|im_start|>assistant\n", "")
                    .replace("<|im_end|>", "")
                    .trim()
            }
            var tail = prompt.substring(lastUserIdx + userMarker.length)
            val endIdx = tail.indexOf("<|im_end|>")
            if (endIdx != -1) {
                tail = tail.substring(0, endIdx)
            } else {
                val assistantIdx = tail.indexOf("<|im_start|>assistant")
                if (assistantIdx != -1) {
                    tail = tail.substring(0, assistantIdx)
                }
            }
            return tail.trim()
        }

        internal fun toLiteRtInitialMessages(
            history: List<ChatMessage>,
            maxHistoryTokens: Int,
        ): List<LiteRtMessage> {
            if (history.isEmpty() || maxHistoryTokens <= 0) return emptyList()
            val usable = history.filter { !it.isError && !it.isStreaming && it.text.isNotBlank() }
            if (usable.isEmpty()) return emptyList()

            val selected = ArrayDeque<LiteRtMessage>()
            var usedTokens = 0
            for (msg in usable.asReversed()) {
                val cleanedText = if (msg.isUser) {
                    msg.text.trim()
                } else {
                    PromptContextBuilder.stripThinkingBlock(msg.text).trim()
                }
                if (cleanedText.isBlank()) continue
                val tokens = PromptContextBuilder.estimateTokens(cleanedText) + 8
                if (usedTokens + tokens > maxHistoryTokens && selected.isNotEmpty()) break
                usedTokens += tokens
                val liteRtMsg = if (msg.isUser) {
                    LiteRtMessage.user(cleanedText)
                } else {
                    LiteRtMessage.model(cleanedText)
                }
                selected.addFirst(liteRtMsg)
            }
            return selected.toList()
        }
    }
}
