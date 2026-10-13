package com.example.on_device_qwen.domain.engine

import com.example.on_device_qwen.model.*
import kotlinx.coroutines.flow.Flow
import java.io.File

data class GenerationChunk(
    val text: String,
    val isComplete: Boolean = false,
    val metrics: BenchmarkMetrics? = null,
)

data class EngineCapabilities(
    val engineType: EngineType,
    val supportedFormats: List<ModelFormat>,
    val supportsVision: Boolean,
    val supportsGpu: Boolean,
    val supportsNpu: Boolean,
    val supportsSystemPrompt: Boolean = true,
    val supportsReasoningTags: Boolean = true,
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
    fun getCapabilities(): EngineCapabilities
    fun getBenchmarkMetrics(): BenchmarkMetrics?
}
