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
        val buffer = ByteArrayOutputStream()
        val backendLabel = "llama.cpp • CPU (${config.threadCount} threads)"

        // Format prompt using Dolphin / Qwen ChatML standard template
        val formattedPrompt = buildChatMlPrompt(config.systemPrompt, prompt)

        try {
            val totalTokens = nativeEngine.generate(
                prompt = formattedPrompt,
                maxTokens = config.maxOutputTokens,
                listener = ChunkListener { bytes ->
                    if (_isCancelled.get()) return@ChunkListener false

                    val now = System.currentTimeMillis()
                    val currentFirstToken = firstTokenMs ?: now.also { firstTokenMs = it }
                    chunkCount++
                    buffer.write(bytes)

                    val ttft = (currentFirstToken - startMs).coerceAtLeast(0)
                    val decodeDurationSec = (now - currentFirstToken) / 1000.0
                    val liveTps = if (decodeDurationSec > 0.05) chunkCount / decodeDurationSec else 0.0

                    val liveMetrics = BenchmarkMetrics(
                        ttftMs = ttft,
                        decodeTps = liveTps,
                        decodeTokens = chunkCount,
                        totalDurationMs = now - startMs,
                        backend = backendLabel
                    )

                    val textPreview = buffer.toString("UTF-8")
                    trySend(GenerationChunk(text = textPreview, isComplete = false, metrics = liveMetrics))
                    true
                }
            )

            val finalTotalDurationMs = System.currentTimeMillis() - startMs
            val currentFirstToken = firstTokenMs ?: startMs
            val ttft = (currentFirstToken - startMs).coerceAtLeast(0)
            val decodeDurationSec = (finalTotalDurationMs - ttft) / 1000.0
            val finalTps = if (decodeDurationSec > 0.05) chunkCount / decodeDurationSec else 0.0

            val finalMetrics = BenchmarkMetrics(
                ttftMs = ttft,
                decodeTps = finalTps,
                decodeTokens = if (totalTokens > 0) totalTokens else chunkCount,
                totalDurationMs = finalTotalDurationMs,
                backend = backendLabel
            )

            lastBenchmark = finalMetrics
            trySend(GenerationChunk(text = buffer.toString("UTF-8"), isComplete = true, metrics = finalMetrics))

        } catch (e: CancellationException) {
            val totalMs = System.currentTimeMillis() - startMs
            val cancelledMetrics = BenchmarkMetrics(
                ttftMs = ((firstTokenMs ?: System.currentTimeMillis()) - startMs).coerceAtLeast(0),
                decodeTps = 0.0,
                decodeTokens = chunkCount,
                totalDurationMs = totalMs,
                backend = backendLabel
            )
            trySend(GenerationChunk(text = buffer.toString("UTF-8"), isComplete = true, metrics = cancelledMetrics))
        } finally {
            _isGenerating.set(false)
        }
    }.flowOn(Dispatchers.IO)

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
        return buildString {
            append("<|im_start|>system\n")
            append(systemPrompt.ifBlank { "You are Dolphin, a helpful, precise, and thoughtful AI assistant running completely offline." })
            append("<|im_end|>\n")
            append("<|im_start|>user\n")
            append(userPrompt)
            append("<|im_end|>\n")
            append("<|im_start|>assistant\n")
        }
    }
}
