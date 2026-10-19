package com.example.on_device_qwen.domain.engine

import android.content.Context
import com.example.on_device_qwen.inference.ChunkListener
import com.example.on_device_qwen.inference.DolphinNativeEngine
import com.example.on_device_qwen.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-performance inference backend for GGUF quantized models (including Dolphin 3.0 Qwen2.5 3B,
 * Huihui Qwen3.5 Abliterated 0.8B/2B/4B, and user-imported GGUFs).
 * Integrates directly with native llama.cpp JNI bindings with cooperative cancellation and UTF-8 piece decoding.
 */
class LlamaCppInferenceEngine(private val context: Context) : InferenceEngine {

    private val nativeEngine = DolphinNativeEngine()
    private var _activeModel: ModelMetadata? = null
    private val _isGenerating = AtomicBoolean(false)
    private val _isCancelled = AtomicBoolean(false)
    private var lastBenchmark: BenchmarkMetrics? = null

    override val activeModel: ModelMetadata? get() = _activeModel
    override val isLoaded: Boolean get() = _activeModel != null
    override val isGenerating: Boolean get() = _isGenerating.get()

    override suspend fun loadModel(modelFile: File, config: GenerationConfig): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            unloadModel()

            if (!modelFile.exists() || !modelFile.canRead()) {
                return@withContext Result.failure(IllegalStateException("Cannot read GGUF model file: ${modelFile.absolutePath}"))
            }

            if (!DolphinNativeEngine.isAvailable()) {
                return@withContext Result.failure(
                    UnsupportedOperationException(
                        "Native llama.cpp engine (libdolphin_gguf.so) is not available for this device architecture. " +
                        "Please use a LiteRT-LM (.litertlm) model instead."
                    )
                )
            }

            // Parse GGUF metadata from binary header
            val metadata = GgufMetadataParser.parse(modelFile) ?: ModelMetadata(
                file = modelFile,
                name = modelFile.nameWithoutExtension,
                format = ModelFormat.GGUF,
                engineType = EngineType.LLAMA_CPP,
                architecture = "qwen2",
                quantization = "Q4_K_M",
                contextLength = config.contextLength,
                fileSizeBytes = modelFile.length(),
                estimatedRamBytes = (modelFile.length() * 1.3).toLong(),
                isAbliterated = modelFile.name.contains("abliterat", ignoreCase = true) ||
                        modelFile.name.contains("dolphin", ignoreCase = true)
            )

            // Initialize native llama.cpp session
            nativeEngine.load(modelFile, config.contextLength, config.threadCount)

            _activeModel = metadata
            Result.success(Unit)
        } catch (e: Exception) {
            unloadModel()
            Result.failure(e)
        }
    }

    override suspend fun unloadModel() = withContext(Dispatchers.IO) {
        try {
            nativeEngine.close()
        } catch (_: Exception) {}
        _activeModel = null
        _isGenerating.set(false)
        _isCancelled.set(false)
    }

    override fun resetConversation() {
        // llama.cpp kv cache is cleared at each prompt submission; reset any ongoing flag
        _isCancelled.set(false)
    }

    override fun sendMessage(
        prompt: String,
        imagePath: String?,
        config: GenerationConfig
    ): Flow<GenerationChunk> = channelFlow {
        val model = _activeModel ?: throw IllegalStateException("GGUF Model is not loaded")
        _isGenerating.set(true)
        _isCancelled.set(false)

        val startMs = System.currentTimeMillis()
        var firstTokenMs: Long? = null
        var chunkCount = 0
        // Fresh local buffer allocated per request to eliminate cross-request concurrency contamination
        val buffer = ByteArrayOutputStream(4096)
        val backendLabel = "llama.cpp • CPU (${config.threadCount} threads)"

        // If prompt is already structured with ChatML tags (from PromptContextBuilder), use directly.
        // The builder guarantees exactly one sanitized system block, so engines must NOT
        // prepend a second system prompt here — that is what caused duplication bugs.
        val formattedPrompt = if (prompt.contains("<|im_start|>")) {
            prompt
        } else {
            buildChatMlPrompt(config.systemPrompt, prompt)
        }

        var lastEmitMs = 0L
        val promptTokens = com.example.on_device_qwen.domain.context.PromptContextBuilder.estimateTokens(formattedPrompt)
        try {
            val totalTokens = nativeEngine.generate(
                prompt = formattedPrompt,
                maxTokens = config.maxOutputTokens,
                temperature = config.temperature,
                topP = config.topP,
                repetitionPenalty = config.repetitionPenalty,
                listener = ChunkListener { bytes ->
                    if (_isCancelled.get()) return@ChunkListener false

                    val now = System.currentTimeMillis()
                    val currentFirstToken = firstTokenMs ?: now.also { firstTokenMs = it }
                    chunkCount++
                    buffer.write(bytes)

                    // Throttle String conversions & channel emissions to ~45ms to eliminate GC & UI pressure
                    if (now - lastEmitMs >= 45L) {
                        lastEmitMs = now
                        val rawText = buffer.toString("UTF-8")
                        val cleanText = sanitizeResponseText(rawText)
                        val currentDecodeTokens = maxOf(chunkCount, com.example.on_device_qwen.domain.context.PromptContextBuilder.estimateTokens(cleanText))
                        val ttft = (currentFirstToken - startMs).coerceAtLeast(0)
                        val decodeDurationSec = (now - currentFirstToken) / 1000.0
                        val liveTps = if (decodeDurationSec > 0.05) currentDecodeTokens / decodeDurationSec else 0.0

                        val liveMetrics = BenchmarkMetrics(
                            ttftMs = ttft,
                            decodeTps = liveTps,
                            prefillTokens = promptTokens,
                            decodeTokens = currentDecodeTokens,
                            totalDurationMs = now - startMs,
                            backend = backendLabel
                        )

                        trySend(GenerationChunk(
                            text = cleanText,
                            isComplete = false,
                            metrics = liveMetrics,
                            generationId = config.generationId
                        ))
                    }
                    true
                }
            )

            val finalTotalDurationMs = System.currentTimeMillis() - startMs
            val currentFirstToken = firstTokenMs ?: startMs
            val ttft = (currentFirstToken - startMs).coerceAtLeast(0)
            val decodeDurationSec = (finalTotalDurationMs - ttft) / 1000.0
            val cleanFinal = sanitizeResponseText(buffer.toString("UTF-8"))
            val estimatedFinal = maxOf(chunkCount, com.example.on_device_qwen.domain.context.PromptContextBuilder.estimateTokens(cleanFinal))
            val finalDecodeTokens = if (totalTokens > 0) totalTokens else estimatedFinal
            val finalTps = if (decodeDurationSec > 0.05) finalDecodeTokens / decodeDurationSec else 0.0

            val finalMetrics = BenchmarkMetrics(
                ttftMs = ttft,
                decodeTps = finalTps,
                prefillTokens = promptTokens,
                decodeTokens = finalDecodeTokens,
                totalDurationMs = finalTotalDurationMs,
                backend = backendLabel
            )

            lastBenchmark = finalMetrics
            trySend(GenerationChunk(
                text = cleanFinal,
                isComplete = true,
                metrics = finalMetrics,
                generationId = config.generationId
            ))

        } catch (e: CancellationException) {
            val totalMs = System.currentTimeMillis() - startMs
            val cleanCancelled = sanitizeResponseText(buffer.toString("UTF-8"))
            val cancelledTokens = maxOf(chunkCount, com.example.on_device_qwen.domain.context.PromptContextBuilder.estimateTokens(cleanCancelled))
            val cancelledMetrics = BenchmarkMetrics(
                ttftMs = ((firstTokenMs ?: System.currentTimeMillis()) - startMs).coerceAtLeast(0),
                decodeTps = 0.0,
                prefillTokens = promptTokens,
                decodeTokens = cancelledTokens,
                totalDurationMs = totalMs,
                backend = backendLabel
            )
            trySend(GenerationChunk(
                text = cleanCancelled,
                isComplete = true,
                metrics = cancelledMetrics,
                generationId = config.generationId
            ))
        } finally {
            _isGenerating.set(false)
        }
    }.flowOn(Dispatchers.IO)

    private fun sanitizeResponseText(raw: String): String {
        return raw.replace("<|im_end|>", "")
            .replace("<|endoftext|>", "")
            .replace("<|im_start|>", "")
            .trimEnd()
    }

    override fun cancelGeneration() {
        if (_isGenerating.get()) {
            _isCancelled.set(true)
        }
    }

    override fun getCapabilities(): EngineCapabilities {
        return EngineCapabilities(
            engineType = EngineType.LLAMA_CPP,
            supportedFormats = listOf(ModelFormat.GGUF),
            supportsVision = false,
            supportsGpu = false,
            supportsNpu = false,
            supportsSystemPrompt = true,
            supportsReasoningTags = true
        )
    }

    override fun getBenchmarkMetrics(): BenchmarkMetrics? = lastBenchmark

    private fun buildChatMlPrompt(systemPrompt: String, userPrompt: String): String {
        val cleanSystem = com.example.on_device_qwen.model.sanitizeSystemPrompt(systemPrompt)
        return buildString {
            append("<|im_start|>system\n")
            append(cleanSystem.ifBlank { "You are Dolphin, a helpful, precise, and thoughtful AI assistant running completely offline." })
            append("<|im_end|>\n")
            append("<|im_start|>user\n")
            append(userPrompt)
            append("<|im_end|>\n")
            append("<|im_start|>assistant\n")
        }
    }
}
