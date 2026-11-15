package com.example.on_device_qwen.domain.engine

import android.content.Context
import com.example.on_device_qwen.data.preferences.PerformanceMode
import com.example.on_device_qwen.domain.context.PromptContextBuilder
import com.example.on_device_qwen.domain.performance.DeviceThermalState
import com.example.on_device_qwen.domain.performance.ThermalPerformanceManager
import com.example.on_device_qwen.inference.ChunkListener
import com.example.on_device_qwen.inference.DolphinNativeEngine
import com.example.on_device_qwen.model.BackendType
import com.example.on_device_qwen.model.BenchmarkMetrics
import com.example.on_device_qwen.model.ChatMessage
import com.example.on_device_qwen.model.EngineType
import com.example.on_device_qwen.model.ExecutionBackendInfo
import com.example.on_device_qwen.model.GenerationConfig
import com.example.on_device_qwen.model.ModelCompatibilityStatus
import com.example.on_device_qwen.model.ModelErrorCode
import com.example.on_device_qwen.model.ModelFormat
import com.example.on_device_qwen.model.ModelMetadata
import com.example.on_device_qwen.model.RetrievedMemoryContext
import com.example.on_device_qwen.model.sanitizeSystemPrompt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

/**
 * High-performance CPU llama.cpp inference engine for GGUF models.
 * Enforces serialized lifecycle locks, honest GGUF header validation, non-blocking native aborts,
 * KV-prefix reuse, incomplete UTF-8 byte buffering, stop-sequence holdback, and thermal throttling.
 */
class LlamaCppInferenceEngine(
    private val context: Context,
    thermalManager: ThermalPerformanceManager? = null,
) : InferenceEngine {

    private val lifecycleMutex = Mutex()
    private val nativeEngine = DolphinNativeEngine()

    @Volatile private var attachedThermalManager: ThermalPerformanceManager? = thermalManager
    @Volatile private var _activeModel: ModelMetadata? = null
    @Volatile private var lastExecutionBackendInfo = ExecutionBackendInfo(
        requested = BackendType.CPU,
        active = BackendType.CPU,
        effectiveThreads = 4,
        effectiveContextLength = 4096,
    )
    @Volatile private var lastBenchmark: BenchmarkMetrics? = null

    private val _isGenerating = AtomicBoolean(false)
    private val _isCancelled = AtomicBoolean(false)

    override val activeModel: ModelMetadata? get() = _activeModel
    override val isLoaded: Boolean get() = _activeModel != null
    override val isGenerating: Boolean get() = _isGenerating.get()

    fun setThermalManager(manager: ThermalPerformanceManager?) {
        attachedThermalManager = manager
    }

    override suspend fun loadModel(modelFile: File, config: GenerationConfig): Result<Unit> {
        val loadResult = loadModelWithResult(modelFile.absolutePath, config.backend, config)
        return if (loadResult.success) {
            Result.success(Unit)
        } else {
            val ex = when (loadResult.errorCode) {
                ModelErrorCode.MODEL_FILE_NOT_FOUND -> FileNotFoundException(loadResult.errorMessage ?: "Model not found")
                ModelErrorCode.FILE_UNREADABLE_OR_PERMISSION_DENIED -> SecurityException(loadResult.errorMessage ?: "Model unreadable")
                ModelErrorCode.MISSING_RUNTIME_OR_INCOMPATIBLE_ABI -> UnsupportedOperationException(loadResult.errorMessage ?: "Native ABI unavailable")
                else -> IllegalStateException(loadResult.errorMessage ?: "GGUF model load failed")
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
            val baseThreads = config.threadCount.coerceIn(
                GenerationConfig.MIN_THREAD_COUNT,
                GenerationConfig.MAX_THREAD_COUNT,
            )
            val fallbackReason = if (backend != BackendType.CPU) {
                "${backend.displayLabel(EngineType.LLAMA_CPP)} is not available in this CPU-only llama.cpp build (n_gpu_layers = 0); running on CPU."
            } else null

            try {
                closeUnchecked()

                if (!modelFile.exists()) {
                    return@withLock failureResult(
                        requested = backend,
                        code = ModelErrorCode.MODEL_FILE_NOT_FOUND,
                        message = "GGUF model file does not exist: ${modelFile.absolutePath}",
                    )
                }
                if (!modelFile.canRead()) {
                    return@withLock failureResult(
                        requested = backend,
                        code = ModelErrorCode.FILE_UNREADABLE_OR_PERMISSION_DENIED,
                        message = "Cannot read GGUF model file: ${modelFile.absolutePath}",
                    )
                }
                if (!DolphinNativeEngine.isAvailable()) {
                    return@withLock failureResult(
                        requested = backend,
                        code = ModelErrorCode.MISSING_RUNTIME_OR_INCOMPATIBLE_ABI,
                        message = "Native llama.cpp library (libdolphin_gguf.so) is not available on this device ABI.",
                    )
                }

                // Reject invalid/corrupt GGUFs instead of silently fabricating fallback metadata.
                val metadata = GgufMetadataParser.parse(modelFile)
                    ?: return@withLock failureResult(
                        requested = backend,
                        code = ModelErrorCode.MODEL_INITIALIZATION_FAILED,
                        message = "Invalid or corrupted GGUF header in ${modelFile.name}; verify the file finished downloading.",
                    )

                val thermallyAdjustedThreads = resolveThermalThreadCap(baseThreads)
                val maxModelCtx = metadata.contextLength.takeIf { it > 0 } ?: GenerationConfig.MAX_CONTEXT_LENGTH
                val effectiveCtx = config.contextLength
                    .coerceIn(GenerationConfig.MIN_CONTEXT_LENGTH, GenerationConfig.MAX_CONTEXT_LENGTH)
                    .coerceAtMost(maxModelCtx.coerceAtLeast(GenerationConfig.MIN_CONTEXT_LENGTH))

                nativeEngine.load(modelFile, effectiveCtx, thermallyAdjustedThreads)

                _activeModel = metadata.copy(
                    compatibilityStatus = ModelCompatibilityStatus.COMPATIBLE,
                )
                lastExecutionBackendInfo = ExecutionBackendInfo(
                    requested = backend,
                    active = BackendType.CPU,
                    fallbackReason = fallbackReason,
                    effectiveThreads = nativeEngine.effectiveThreadCount.coerceAtLeast(1),
                    effectiveContextLength = nativeEngine.effectiveContextSize.coerceAtLeast(effectiveCtx),
                )

                EngineLoadResult(
                    success = true,
                    backendInfo = lastExecutionBackendInfo,
                    capabilities = getCapabilities(),
                )
            } catch (ce: CancellationException) {
                withContext(NonCancellable) {
                    closeUnchecked()
                }
                throw ce
            } catch (t: Throwable) {
                withContext(NonCancellable) {
                    closeUnchecked()
                }
                val code = when (t) {
                    is UnsupportedOperationException, is UnsatisfiedLinkError -> ModelErrorCode.MISSING_RUNTIME_OR_INCOMPATIBLE_ABI
                    is SecurityException -> ModelErrorCode.FILE_UNREADABLE_OR_PERMISSION_DENIED
                    is FileNotFoundException -> ModelErrorCode.MODEL_FILE_NOT_FOUND
                    is IllegalArgumentException -> ModelErrorCode.MODEL_INITIALIZATION_FAILED
                    else -> ModelErrorCode.MODEL_INITIALIZATION_FAILED
                }
                failureResult(
                    requested = backend,
                    code = code,
                    message = t.localizedMessage ?: t.message ?: "GGUF initialization failed",
                )
            }
        }
    }

    private fun resolveThermalThreadCap(requestedThreads: Int): Int {
        val manager = attachedThermalManager ?: return requestedThreads
        return when (manager.thermalState.value) {
            DeviceThermalState.NOMINAL, DeviceThermalState.LIGHT -> requestedThreads
            DeviceThermalState.MODERATE -> requestedThreads.coerceAtMost(4).coerceAtLeast(1)
            DeviceThermalState.SEVERE -> requestedThreads.coerceAtMost(2).coerceAtLeast(1)
            DeviceThermalState.CRITICAL -> 1
        }
    }

    private fun failureResult(
        requested: BackendType,
        code: ModelErrorCode,
        message: String,
    ): EngineLoadResult {
        lastExecutionBackendInfo = ExecutionBackendInfo(
            requested = requested,
            active = BackendType.CPU,
            fallbackReason = message,
            effectiveThreads = 0,
            effectiveContextLength = 0,
        )
        return EngineLoadResult(
            success = false,
            backendInfo = lastExecutionBackendInfo,
            capabilities = getCapabilities(),
            errorCode = code,
            errorMessage = message,
        )
    }

    private fun closeUnchecked() {
        try {
            nativeEngine.close()
        } catch (_: Throwable) {
        }
        _activeModel = null
        _isGenerating.set(false)
        _isCancelled.set(false)
    }

    override suspend fun unloadModel() {
        cancelGeneration()
        withContext(Dispatchers.IO) {
            lifecycleMutex.withLock {
                withContext(NonCancellable) {
                    closeUnchecked()
                }
            }
        }
    }

    override fun resetConversation() {
        _isCancelled.set(false)
        if (isLoaded && !_isGenerating.get()) {
            nativeEngine.resetContext()
        }
    }

    override suspend fun resetSession(
        systemPrompt: String?,
        history: List<ChatMessage>,
    ): Boolean = withContext(Dispatchers.IO) {
        cancelGeneration()
        lifecycleMutex.withLock {
            try {
                if (!isLoaded) return@withContext false
                _isCancelled.set(false)
                nativeEngine.resetContext()
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                false
            }
        }
    }

    override suspend fun countTokens(text: String): Int? = withContext(Dispatchers.IO) {
        if (!isLoaded) return@withContext null
        nativeEngine.countTokens(text)
    }

    override fun getExecutionBackendInfo(): ExecutionBackendInfo = lastExecutionBackendInfo

    override fun sendMessage(
        prompt: String,
        imagePath: String?,
        config: GenerationConfig,
    ): Flow<GenerationChunk> = channelFlow {
        lifecycleMutex.withLock {
            val model = _activeModel ?: throw IllegalStateException("GGUF model is not loaded")
            if (!_isGenerating.compareAndSet(false, true)) {
                throw IllegalStateException("GGUF generation is already in progress")
            }
            _isCancelled.set(false)

            if (attachedThermalManager?.shouldPauseGeneration?.value == true) {
                _isGenerating.set(false)
                trySend(
                    GenerationChunk(
                        text = "",
                        isComplete = true,
                        generationId = config.generationId,
                        error = "Device thermal state is critical; generation paused until device cools down.",
                        isCancelled = true,
                    ),
                )
                return@withLock
            }

            val startMs = System.currentTimeMillis()
            var firstTokenMs: Long? = null
            var chunkCount = 0
            val activeThreads = lastExecutionBackendInfo.effectiveThreads.coerceAtLeast(1)
            val backendLabel = "llama.cpp • CPU ($activeThreads threads)"

            val formattedPrompt = resolveFormattedPrompt(prompt, model, config)
            val activeStops = (config.stopSequences + model.templateFamily.defaultStopSequences)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()

            val streamFilter = Utf8AndStopStreamFilter(activeStops)
            val emittedBuffer = StringBuilder()
            var lastEmittedLength = 0
            var lastEmitMs = 0L
            val batchIntervalMs = config.streamBatchIntervalMs.coerceIn(
                GenerationConfig.MIN_STREAM_BATCH_INTERVAL_MS,
                GenerationConfig.MAX_STREAM_BATCH_INTERVAL_MS,
            )

            val promptTokens = nativeEngine.countTokens(formattedPrompt)
                ?: PromptContextBuilder.estimateTokens(formattedPrompt)

            fun buildMetrics(now: Long, text: String, totalDecodedTokens: Int? = null): BenchmarkMetrics {
                val first = firstTokenMs ?: now
                val ttft = (first - startMs).coerceAtLeast(0L)
                val decodeDurationSec = (now - first) / 1000.0
                val estTokens = maxOf(chunkCount, PromptContextBuilder.estimateTokens(text))
                val decodeCount = totalDecodedTokens?.takeIf { it > 0 } ?: estTokens
                val tps = if (decodeDurationSec > 0.05) decodeCount / decodeDurationSec else 0.0
                return BenchmarkMetrics(
                    ttftMs = ttft,
                    decodeTps = tps,
                    prefillTokens = promptTokens,
                    decodeTokens = decodeCount,
                    totalDurationMs = (now - startMs).coerceAtLeast(0L),
                    backend = backendLabel,
                )
            }

            try {
                var thermalPaused = false
                val totalTokens = nativeEngine.generate(
                    prompt = formattedPrompt,
                    maxTokens = config.maxOutputTokens.coerceIn(GenerationConfig.MIN_MAX_TOKENS, 4096),
                    temperature = config.temperature,
                    topP = config.topP,
                    topK = config.topK,
                    minP = config.minP,
                    repetitionPenalty = config.repetitionPenalty,
                    seed = config.seed,
                    stopSequences = activeStops,
                    listener = ChunkListener { bytes ->
                        if (_isCancelled.get() || !this@channelFlow.isActive) {
                            return@ChunkListener false
                        }
                        if (attachedThermalManager?.shouldPauseGeneration?.value == true) {
                            thermalPaused = true
                            _isCancelled.set(true)
                            return@ChunkListener false
                        }

                        val now = System.currentTimeMillis()
                        val isFirst = firstTokenMs == null
                        if (isFirst) firstTokenMs = now
                        chunkCount++

                        val filtered = streamFilter.appendBytes(bytes)
                        if (filtered.emittedText.isNotEmpty()) {
                            emittedBuffer.append(filtered.emittedText)
                        }

                        if (filtered.hitStop || isFirst || (now - lastEmitMs) >= batchIntervalMs) {
                            val currentDelta = emittedBuffer.substring(lastEmittedLength)
                            if (currentDelta.isNotEmpty() || isFirst) {
                                lastEmitMs = now
                                lastEmittedLength = emittedBuffer.length
                                val fullSoFar = emittedBuffer.toString()
                                trySend(
                                    GenerationChunk(
                                        text = fullSoFar,
                                        isComplete = false,
                                        metrics = buildMetrics(now, fullSoFar),
                                        isDelta = false,
                                        generationId = config.generationId,
                                        deltaText = currentDelta,
                                    ),
                                )
                            }
                        }

                        val throttleMs = attachedThermalManager?.recommendedThrottleDelayMs() ?: 0L
                        if (throttleMs > 0L) {
                            try {
                                Thread.sleep(throttleMs)
                            } catch (_: InterruptedException) {
                                return@ChunkListener false
                            }
                        }

                        !filtered.hitStop
                    },
                )

                val tail = streamFilter.flush(stoppedByStopSequence = _isCancelled.get())
                if (tail.isNotEmpty()) {
                    emittedBuffer.append(tail)
                }

                val now = System.currentTimeMillis()
                val finalText = emittedBuffer.toString().trimEnd()
                val remainingDelta = if (emittedBuffer.length > lastEmittedLength) {
                    emittedBuffer.substring(lastEmittedLength).trimEnd()
                } else ""

                val finalMetrics = buildMetrics(now, finalText, totalTokens)
                lastBenchmark = finalMetrics

                if (!coroutineContext.isActive && !_isCancelled.get()) {
                    throw CancellationException("Collector coroutine cancelled")
                }

                trySend(
                    GenerationChunk(
                        text = finalText,
                        isComplete = true,
                        metrics = finalMetrics,
                        isDelta = false,
                        generationId = config.generationId,
                        deltaText = remainingDelta,
                        error = if (thermalPaused) "Generation paused because device thermal limits reached critical." else null,
                        isCancelled = _isCancelled.get() && !thermalPaused,
                    ),
                )
            } catch (ce: CancellationException) {
                val stillActive = coroutineContext.isActive
                withContext(NonCancellable) {
                    nativeEngine.requestAbort()
                }
                if (_isCancelled.get() && stillActive) {
                    val cancelledTail = streamFilter.flush(stoppedByStopSequence = true)
                    if (cancelledTail.isNotEmpty()) emittedBuffer.append(cancelledTail)
                    val cancelledText = emittedBuffer.toString().trimEnd()
                    val delta = if (emittedBuffer.length > lastEmittedLength) {
                        emittedBuffer.substring(lastEmittedLength).trimEnd()
                    } else ""
                    val now = System.currentTimeMillis()
                    trySend(
                        GenerationChunk(
                            text = cancelledText,
                            isComplete = true,
                            metrics = buildMetrics(now, cancelledText),
                            isDelta = false,
                            generationId = config.generationId,
                            deltaText = delta,
                            isCancelled = true,
                        ),
                    )
                }
                throw ce
            } catch (t: Throwable) {
                val now = System.currentTimeMillis()
                val currentText = emittedBuffer.toString().trimEnd()
                val delta = if (emittedBuffer.length > lastEmittedLength) {
                    emittedBuffer.substring(lastEmittedLength).trimEnd()
                } else ""
                trySend(
                    GenerationChunk(
                        text = currentText,
                        isComplete = true,
                        metrics = buildMetrics(now, currentText),
                        isDelta = false,
                        generationId = config.generationId,
                        deltaText = delta,
                        error = t.localizedMessage ?: t.message ?: "GGUF generation failed",
                    ),
                )
            } finally {
                _isGenerating.set(false)
                _isCancelled.set(false)
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun resolveFormattedPrompt(
        prompt: String,
        model: ModelMetadata,
        config: GenerationConfig,
    ): String {
        if (hasStructuredTemplateMarkers(prompt)) {
            return prompt
        }
        val cleanSystem = sanitizeSystemPrompt(config.systemPrompt)
        val nativeFormatted = nativeEngine.applyChatTemplate(
            messages = listOf("system" to cleanSystem, "user" to prompt),
            addAssistant = true,
        )
        if (!nativeFormatted.isNullOrBlank()) {
            return nativeFormatted
        }
        return PromptContextBuilder.buildPromptForModel(
            historyMessages = emptyList(),
            latestUserPrompt = prompt,
            memoryContext = RetrievedMemoryContext(),
            config = config,
            metadata = model,
        ).formattedPrompt
    }

    override fun cancelGeneration() {
        if (_isGenerating.get()) {
            _isCancelled.set(true)
            nativeEngine.requestAbort()
        }
    }

    override fun getCapabilities(): EngineCapabilities {
        val model = _activeModel
        return EngineCapabilities(
            engineType = EngineType.LLAMA_CPP,
            supportedFormats = listOf(ModelFormat.GGUF),
            supportsVision = false,
            supportsGpu = false,
            supportsNpu = false,
            supportsSystemPrompt = true,
            supportsReasoningTags = true,
            supportsKvReuse = true,
            supportsNativeTokenCount = isLoaded,
            maxContextLength = GenerationConfig.MAX_CONTEXT_LENGTH,
            maxOutputTokens = 4096,
            maxThreads = GenerationConfig.MAX_THREAD_COUNT,
            unsupportedVisionReason = model?.visionUnsupportedReason
                ?: "Standalone GGUF runtime supports text only (no external vision projector).",
        )
    }

    override fun getBenchmarkMetrics(): BenchmarkMetrics? = lastBenchmark

    companion object {
        private val TEMPLATE_MARKERS = listOf(
            "<|im_start|>",
            "<|start_header_id|>",
            "<start_of_turn>",
            "<|system|>",
            "<|user|>",
            "<｜User｜>",
        )

        internal fun hasStructuredTemplateMarkers(prompt: String): Boolean {
            return TEMPLATE_MARKERS.any { prompt.contains(it) }
        }
    }

    /**
     * Stateful streaming filter that:
     * 1. Buffers incomplete trailing multi-byte UTF-8 sequences so characters are never torn into U+FFFD.
     * 2. Holds back trailing prefixes of stop sequences (`maxStopLen - 1`) and truncates cleanly on match.
     */
    internal class Utf8AndStopStreamFilter(stopSequences: List<String>) {
        private val activeStops = stopSequences.filter { it.isNotEmpty() }.distinct()
        private val maxHoldbackChars = (activeStops.maxOfOrNull { it.length } ?: 1) - 1
        private val rawPendingBytes = ByteArrayOutputStream(256)
        private val pendingText = StringBuilder()
        private var stopMatched = false

        data class FilterOutput(
            val emittedText: String,
            val hitStop: Boolean,
        )

        fun appendBytes(chunk: ByteArray): FilterOutput {
            if (stopMatched || chunk.isEmpty()) return FilterOutput("", stopMatched)

            rawPendingBytes.write(chunk)
            val allBytes = rawPendingBytes.toByteArray()
            val incompleteTail = trailingIncompleteUtf8Count(allBytes)
            val safeByteCount = allBytes.size - incompleteTail

            if (safeByteCount > 0) {
                val decoded = String(allBytes, 0, safeByteCount, Charsets.UTF_8)
                rawPendingBytes.reset()
                if (incompleteTail > 0) {
                    rawPendingBytes.write(allBytes, safeByteCount, incompleteTail)
                }
                pendingText.append(decoded)
            }

            if (pendingText.isEmpty()) {
                return FilterOutput("", false)
            }

            val buffered = pendingText.toString()
            var earliestStopPos = -1
            for (stop in activeStops) {
                val idx = buffered.indexOf(stop)
                if (idx != -1 && (earliestStopPos == -1 || idx < earliestStopPos)) {
                    earliestStopPos = idx
                }
            }

            if (earliestStopPos != -1) {
                stopMatched = true
                pendingText.setLength(0)
                rawPendingBytes.reset()
                return FilterOutput(buffered.substring(0, earliestStopPos), hitStop = true)
            }

            var holdback = 0
            if (maxHoldbackChars > 0) {
                for (stop in activeStops) {
                    val limit = minOf(buffered.length, stop.length - 1)
                    for (len in limit downTo holdback + 1) {
                        if (buffered.endsWith(stop.substring(0, len))) {
                            holdback = len
                            break
                        }
                    }
                }
            }

            val emitLen = (buffered.length - holdback).coerceAtLeast(0)
            if (emitLen == 0) {
                return FilterOutput("", false)
            }
            val emitted = buffered.substring(0, emitLen)
            pendingText.delete(0, emitLen)
            return FilterOutput(emitted, hitStop = false)
        }

        fun flush(stoppedByStopSequence: Boolean = false): String {
            if (stopMatched || stoppedByStopSequence) {
                pendingText.setLength(0)
                rawPendingBytes.reset()
                return ""
            }
            val leftover = pendingText.toString()
            pendingText.setLength(0)
            rawPendingBytes.reset()
            return leftover
        }

        companion object {
            fun trailingIncompleteUtf8Count(bytes: ByteArray): Int {
                val len = bytes.size
                if (len == 0) return 0
                val last = bytes[len - 1].toInt() and 0xFF
                if (last < 0x80) return 0

                var scan = 0
                while (scan < 3 && scan + 1 < len && (bytes[len - 1 - scan].toInt() and 0xC0) == 0x80) {
                    scan++
                }
                val leadIdx = len - 1 - scan
                val lead = bytes[leadIdx].toInt() and 0xFF
                val have = len - leadIdx

                val expected = when {
                    (lead and 0xE0) == 0xC0 -> 2
                    (lead and 0xF0) == 0xE0 -> 3
                    (lead and 0xF8) == 0xF0 -> 4
                    else -> return 0
                }
                return if (have < expected) have else 0
            }
        }
    }
}
