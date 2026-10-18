package com.example.on_device_qwen.model

import java.io.File

enum class EngineType(val displayName: String) {
    LITERT("LiteRT-LM"),
    LLAMA_CPP("llama.cpp")
}

enum class ModelFormat(val extension: String) {
    LITERT(".litertlm"),
    GGUF(".gguf")
}

enum class BackendType(val label: String, val chipText: String) {
    CPU("CPU (XNNPACK / NEON)", "CPU"),
    GPU("GPU (OpenCL / Adreno)", "GPU"),
    NPU("NPU (Qualcomm HTP)", "NPU")
}

data class ModelMetadata(
    val file: File,
    val name: String,
    val format: ModelFormat,
    val engineType: EngineType,
    val architecture: String = "qwen2",
    val quantization: String = "Unknown",
    val contextLength: Int = 4096,
    val fileSizeBytes: Long = 0L,
    val estimatedRamBytes: Long = 0L,
    val hasVision: Boolean = false,
    val isAbliterated: Boolean = false,
    val parameterCount: String = "",
    val isReadable: Boolean = true,
    val isCorrupted: Boolean = false,
) {
    val fileSizeMb: Long get() = fileSizeBytes / (1024 * 1024)
    val estimatedRamMb: Long get() = estimatedRamBytes / (1024 * 1024)
}

enum class ModelErrorCode {
    MODEL_FILE_NOT_FOUND,
    DOWNLOADED_MODEL_NOT_REGISTERED,
    FILE_UNREADABLE_OR_PERMISSION_DENIED,
    UNSUPPORTED_MODEL_FORMAT,
    MISSING_RUNTIME_OR_INCOMPATIBLE_ABI,
    INSUFFICIENT_RAM,
    MODEL_INITIALIZATION_FAILED,
    INFERENCE_FAILED,
    UNKNOWN
}

data class ModelDiagnostic(
    val errorCode: ModelErrorCode,
    val title: String,
    val message: String,
    val canRequestPermission: Boolean = false,
    val canOpenFilePicker: Boolean = true,
    val candidateFiles: List<File> = emptyList()
)

data class GenerationConfig(
    val temperature: Float = 0.7f,
    val topP: Float = 0.8f,
    val repetitionPenalty: Float = 1.15f,
    val maxOutputTokens: Int = 2048,
    val contextLength: Int = 4096,
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val threadCount: Int = 4,
    val backend: BackendType = BackendType.CPU,
    val stopSequences: List<String> = listOf("<|im_end|>", "<|endoftext|>", "<|im_start|>"),
    val generationId: String = "",
) {
    companion object {
        const val DEFAULT_SYSTEM_PROMPT =
            "You are Qwen, a helpful, precise, and thoughtful AI assistant. " +
            "You run completely offline and private on this Android device. " +
            "Provide clear, accurate, and concise answers."
    }
}
