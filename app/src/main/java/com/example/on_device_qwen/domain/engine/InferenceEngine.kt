package com.example.on_device_qwen.domain.engine

import com.example.on_device_qwen.model.*
import kotlinx.coroutines.flow.Flow
import java.io.File

data class GenerationChunk(
    val text: String = "",
    val isComplete: Boolean = false,
    val metrics: BenchmarkMetrics? = null,
    val isDelta: Boolean = false,
    val generationId: String = "",
    val deltaText: String = if (isDelta) text else "",
    val deltaThinking: String = "",
    val thinking: String? = null,
    val error: String? = null,
    val isCancelled: Boolean = false,
) {
    val hasError: Boolean get() = !error.isNullOrBlank()
}

data class EngineCapabilities(
    val engineType: EngineType,
    val supportedFormats: List<ModelFormat>,
    val supportsVision: Boolean,
    val supportsGpu: Boolean,
    val supportsNpu: Boolean,
    val supportsSystemPrompt: Boolean = true,
    val supportsReasoningTags: Boolean = true,
    val supportsKvReuse: Boolean = false,
    val supportsNativeTokenCount: Boolean = false,
    val maxContextLength: Int = 4096,
    val maxOutputTokens: Int = 2048,
    val maxThreads: Int = 8,
    val unsupportedVisionReason: String? = null,
)

data class EngineLoadResult(
    val success: Boolean,
    val backendInfo: ExecutionBackendInfo,
    val capabilities: EngineCapabilities,
    val errorCode: ModelErrorCode? = null,
    val errorMessage: String? = null,
)

interface InferenceEngine {
    val activeModel: ModelMetadata?
    val isLoaded: Boolean
    val isGenerating: Boolean

    suspend fun loadModel(modelFile: File, config: GenerationConfig): Result<Unit>
    suspend fun unloadModel()
    fun sendMessage(
        prompt: String,
        imagePath: String? = null,
        config: GenerationConfig = GenerationConfig()
    ): Flow<GenerationChunk>
    fun cancelGeneration()
    fun resetConversation() {}
    fun getCapabilities(): EngineCapabilities
    fun getBenchmarkMetrics(): BenchmarkMetrics?

    fun getExecutionBackendInfo(): ExecutionBackendInfo {
        val caps = getCapabilities()
        val effectiveCtx = activeModel?.contextLength?.coerceAtMost(caps.maxContextLength)
            ?: caps.maxContextLength
        return ExecutionBackendInfo(
            requested = BackendType.CPU,
            active = BackendType.CPU,
            effectiveContextLength = effectiveCtx,
        )
    }

    suspend fun loadModelWithResult(
        modelPath: String,
        backend: BackendType,
        config: GenerationConfig = GenerationConfig(),
    ): EngineLoadResult {
        val effectiveConfig = if (config.backend == backend) config else config.copy(backend = backend)
        val result = loadModel(File(modelPath), effectiveConfig)
        val caps = getCapabilities()
        val backendInfo = getExecutionBackendInfo()
        return if (result.isSuccess && isLoaded) {
            EngineLoadResult(
                success = true,
                backendInfo = backendInfo,
                capabilities = caps,
            )
        } else {
            val ex = result.exceptionOrNull()
            val errorCode = when (ex) {
                is UnsupportedOperationException -> ModelErrorCode.MISSING_RUNTIME_OR_INCOMPATIBLE_ABI
                is SecurityException -> ModelErrorCode.FILE_UNREADABLE_OR_PERMISSION_DENIED
                is java.io.FileNotFoundException -> ModelErrorCode.MODEL_FILE_NOT_FOUND
                else -> ModelErrorCode.MODEL_INITIALIZATION_FAILED
            }
            EngineLoadResult(
                success = false,
                backendInfo = backendInfo,
                capabilities = caps,
                errorCode = errorCode,
                errorMessage = ex?.localizedMessage ?: ex?.message ?: "Model initialization failed",
            )
        }
    }

    suspend fun countTokens(text: String): Int? = null

    suspend fun resetSession(
        systemPrompt: String?,
        history: List<ChatMessage> = emptyList(),
    ): Boolean {
        resetConversation()
        return true
    }
}
