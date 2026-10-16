package com.example.on_device_qwen.domain.engine

import android.content.Context
import com.example.on_device_qwen.model.*
import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalApi::class)
class LiteRTInferenceEngine(private val context: Context) : InferenceEngine {

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var _activeModel: ModelMetadata? = null
    private var currentBackendType: BackendType = BackendType.CPU
    private val _isGenerating = AtomicBoolean(false)
    private val _isCancelled = AtomicBoolean(false)
    private var lastBenchmark: BenchmarkMetrics? = null

    override val activeModel: ModelMetadata? get() = _activeModel
    override val isLoaded: Boolean get() = engine != null && conversation != null
    override val isGenerating: Boolean get() = _isGenerating.get()

    override suspend fun loadModel(modelFile: File, config: GenerationConfig): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            unloadModel()

            if (!modelFile.exists()) {
                return@withContext Result.failure(java.io.FileNotFoundException("LiteRT model file does not exist: ${modelFile.absolutePath}"))
            }
            if (!modelFile.canRead()) {
                return@withContext Result.failure(SecurityException("LiteRT model file is unreadable (permission denied): ${modelFile.absolutePath}"))
            }
            if (modelFile.length() < 512 * 1024) {
                return@withContext Result.failure(IllegalStateException("LiteRT model file is too small or incomplete (${modelFile.length()} bytes): ${modelFile.name}"))
            }

            val isVlm = modelFile.nameWithoutExtension.lowercase().contains("vl")
            currentBackendType = config.backend

            val backendObj: Backend = when (config.backend) {
                BackendType.CPU -> Backend.CPU()
                BackendType.GPU -> Backend.GPU()
                BackendType.NPU -> Backend.NPU(context.applicationInfo.nativeLibraryDir)
            }

            try {
                val engineConfig = if (isVlm) {
                    EngineConfig(
                        modelPath = modelFile.absolutePath,
                        backend = backendObj,
                        maxNumImages = 1,
                        maxNumTokens = config.contextLength
                    )
                } else {
                    EngineConfig(
                        modelPath = modelFile.absolutePath,
                        backend = backendObj
                    )
                }
                engine = Engine(engineConfig).also { it.initialize() }
            } catch (err: Exception) {
                if (config.backend != BackendType.CPU) {
                    currentBackendType = BackendType.CPU
                    val fallbackConfig = if (isVlm) {
                        EngineConfig(
                            modelPath = modelFile.absolutePath,
                            backend = Backend.CPU(),
                            maxNumImages = 1,
                            maxNumTokens = config.contextLength
                        )
                    } else {
                        EngineConfig(
                            modelPath = modelFile.absolutePath,
                            backend = Backend.CPU()
                        )
                    }
                    engine = Engine(fallbackConfig).also { it.initialize() }
                } else {
                    throw err
                }
            }

            val convConfig = ConversationConfig(
                systemInstruction = Contents.of(config.systemPrompt)
            )
            // BUG-028 FIX: Use safe null check instead of engine!! to produce a clear
            // error if both primary and CPU fallback engine construction fail.
            val initializedEngine = engine
                ?: throw IllegalStateException("LiteRT Engine failed to initialize (both GPU/NPU and CPU fallback)")
            conversation = initializedEngine.createConversation(convConfig)

            _activeModel = ModelMetadata(
                file = modelFile,
                name = modelFile.nameWithoutExtension,
                format = ModelFormat.LITERT,
                engineType = EngineType.LITERT,
                architecture = if (isVlm) "qwen2-vl" else "qwen2.5",
                quantization = "LiteRT 4-bit / 8-bit",
                contextLength = config.contextLength,
                fileSizeBytes = modelFile.length(),
                estimatedRamBytes = (modelFile.length() * 1.2).toLong(),
                hasVision = isVlm
            )

            Result.success(Unit)
        } catch (e: Exception) {
            unloadModel()
            Result.failure(e)
        }
    }

    override suspend fun unloadModel() = withContext(Dispatchers.IO) {
        try {
            conversation?.close()
        } catch (_: Exception) {}
        try {
            engine?.close()
        } catch (_: Exception) {}
        conversation = null
        engine = null
        _activeModel = null
        _isGenerating.set(false)
        _isCancelled.set(false)
    }

    override fun sendMessage(
        prompt: String,
        imagePath: String?,
        config: GenerationConfig
    // BUG-008 FIX: LiteRTInferenceEngine was using flow{} but calling emit() from inside
    // sendMessageAsync().collect{} — a cross-coroutine emission that violates flow invariants
    // and can throw IllegalStateException. channelFlow{} with trySend() is the correct builder
    // for async, cross-coroutine streaming.
    ): Flow<GenerationChunk> = channelFlow {
        val activeConv = conversation ?: throw IllegalStateException("Model is not loaded")
        _isGenerating.set(true)
        _isCancelled.set(false)

        val startMs = System.currentTimeMillis()
        var firstTokenMs: Long? = null
        var chunkCount = 0
        val buffer = StringBuilder()
        val backendLabel = currentBackendType.chipText

        try {
            if (imagePath != null && File(imagePath).exists()) {
                val contents = Contents.of(
                    Content.ImageFile(imagePath),
                    Content.Text(prompt)
                )
                activeConv.sendMessageAsync(contents).collect { msg ->
                    if (_isCancelled.get()) throw CancellationException("Generation cancelled by user")
                    val now = System.currentTimeMillis()
                    val currentFirstToken = firstTokenMs ?: now.also { firstTokenMs = it }
                    chunkCount++

                    val textPiece = msg.contents.contents
                        .filterIsInstance<Content.Text>()
                        .joinToString("") { it.text }
                    buffer.append(textPiece)

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
                    trySend(GenerationChunk(text = buffer.toString(), isComplete = false, metrics = liveMetrics))
                }
            } else {
                activeConv.sendMessageAsync(prompt).collect { chunk ->
                    if (_isCancelled.get()) throw CancellationException("Generation cancelled by user")
                    val now = System.currentTimeMillis()
                    val currentFirstToken = firstTokenMs ?: now.also { firstTokenMs = it }
                    chunkCount++

                    buffer.append(chunk)

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
                    trySend(GenerationChunk(text = buffer.toString(), isComplete = false, metrics = liveMetrics))
                }
            }

            // Extract engine-level benchmark if available
            val finalTotalDurationMs = System.currentTimeMillis() - startMs
            val benchInfo = try { activeConv.getBenchmarkInfo() } catch (_: Exception) { null }
            val finalMetrics = if (benchInfo != null && benchInfo.lastDecodeTokensPerSecond > 0) {
                val ttft = (benchInfo.timeToFirstTokenInSecond * 1000).toLong().coerceAtLeast(0)
                BenchmarkMetrics(
                    ttftMs = if (ttft > 0) ttft else ((firstTokenMs ?: System.currentTimeMillis()) - startMs).coerceAtLeast(0),
                    decodeTps = benchInfo.lastDecodeTokensPerSecond,
                    prefillTps = if (benchInfo.lastPrefillTokensPerSecond > 0) benchInfo.lastPrefillTokensPerSecond else null,
                    prefillTokens = if (benchInfo.lastPrefillTokenCount > 0) benchInfo.lastPrefillTokenCount else null,
                    decodeTokens = if (benchInfo.lastDecodeTokenCount > 0) benchInfo.lastDecodeTokenCount else chunkCount,
                    totalDurationMs = finalTotalDurationMs,
                    backend = backendLabel
                )
            } else {
                val ttft = ((firstTokenMs ?: System.currentTimeMillis()) - startMs).coerceAtLeast(0)
                val decodeDurationSec = (finalTotalDurationMs - ttft) / 1000.0
                val fallbackTps = if (decodeDurationSec > 0.05) chunkCount / decodeDurationSec else 0.0
                BenchmarkMetrics(
                    ttftMs = ttft,
                    decodeTps = fallbackTps,
                    decodeTokens = chunkCount,
                    totalDurationMs = finalTotalDurationMs,
                    backend = backendLabel
                )
            }

            lastBenchmark = finalMetrics
            trySend(GenerationChunk(text = buffer.toString(), isComplete = true, metrics = finalMetrics))

        } catch (e: CancellationException) {
            val totalMs = System.currentTimeMillis() - startMs
            val cancelledMetrics = BenchmarkMetrics(
                ttftMs = ((firstTokenMs ?: System.currentTimeMillis()) - startMs).coerceAtLeast(0),
                decodeTps = 0.0,
                decodeTokens = chunkCount,
                totalDurationMs = totalMs,
                backend = backendLabel
            )
            trySend(GenerationChunk(text = buffer.toString(), isComplete = true, metrics = cancelledMetrics))
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
            engineType = EngineType.LITERT,
            supportedFormats = listOf(ModelFormat.LITERT),
            supportsVision = _activeModel?.hasVision ?: true,
            supportsGpu = true,
            supportsNpu = true,
            supportsSystemPrompt = true,
            supportsReasoningTags = true
        )
    }

    override fun getBenchmarkMetrics(): BenchmarkMetrics? = lastBenchmark
}
