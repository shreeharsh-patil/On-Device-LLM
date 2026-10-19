package com.example.on_device_qwen.model

import java.io.File
import java.util.UUID

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
    UNKNOWN,
    INFERENCE_PARAMETER_INVALID
}

data class ModelDiagnostic(
    val errorCode: ModelErrorCode,
    val title: String,
    val message: String,
    val canRequestPermission: Boolean = false,
    val canOpenFilePicker: Boolean = true,
    val candidateFiles: List<File> = emptyList()
)

enum class UnrestrictedMode(val displayName: String) {
    STANDARD("Standard"),
    UNRESTRICTED("Unrestricted")
}

data class GenerationConfig(
    val temperature: Float = 0.7f,
    val topP: Float = 0.8f,
    val repetitionPenalty: Float = 1.15f,
    val maxOutputTokens: Int = 2048,
    val contextLength: Int = 4096,
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val threadCount: Int = 4,
    val backend: BackendType = BackendType.CPU,
    val stopSequences: List<String> = DEFAULT_STOP_SEQUENCES,
    val generationId: String = "",
    val unrestrictedMode: UnrestrictedMode = UnrestrictedMode.STANDARD,
    val activePromptProfileId: String = PromptProfile.DEFAULT_ID,
) {
    companion object {
        const val DEFAULT_SYSTEM_PROMPT =
            "You are Qwen, a helpful, precise, and thoughtful AI assistant. " +
            "You run completely offline and private on this Android device. " +
            "Provide clear, accurate, and concise answers."

        const val UNRESTRICTED_SYSTEM_PROMPT =
            "You are a direct and technically precise AI assistant. " +
            "Provide clear, detailed, and practical answers. " +
            "Avoid unnecessary disclaimers, moralizing, or filler. " +
            "If a request is about programming, cybersecurity education, " +
            "reverse engineering, hardware, robotics, or model research, " +
            "treat it as a technical question and answer it directly."

        val DEFAULT_STOP_SEQUENCES: List<String> =
            listOf("<|im_end|>", "<|endoftext|>", "<|im_start|>")

        const val MIN_TEMPERATURE = 0.0f
        const val MAX_TEMPERATURE = 2.0f
        const val MIN_TOP_P = 0.0f
        const val MAX_TOP_P = 1.0f
        const val MIN_REPETITION_PENALTY = 0.1f
        const val MAX_REPETITION_PENALTY = 2.0f
        const val MIN_MAX_TOKENS = 1
        const val MAX_MAX_TOKENS = 8192
        const val MIN_CONTEXT_LENGTH = 1024
        const val MAX_CONTEXT_LENGTH = 8192
        const val MIN_THREAD_COUNT = 1
        const val MAX_THREAD_COUNT = 8
        const val MAX_SYSTEM_PROMPT_CHARS = 8192

        /**
         * Inspectable, engine-aware capability matrix used by settings UI and docs.
         * LiteRT-LM exposes sampling through its own runtime; the bundled llama.cpp
         * JNI bridge currently uses fixed defaults (see dolphin_gguf.cpp).
         */
        fun supportedParameters(engineType: EngineType): List<SupportedParameter> {
            val common = listOf(
                SupportedParameter("systemPrompt", "System prompt / instruction template", true, "Applied via ChatML system block (GGUF) or ConversationConfig (LiteRT)."),
                SupportedParameter("maxOutputTokens", "Maximum generation tokens", true, "Clamped to 1..8192; native GGUF path additionally caps at 2048."),
                SupportedParameter("stopSequences", "Stop sequences / end-of-turn tokens", true, "ChatML markers stripped from output; native GGUF path always stops on <|im_end|>, <|endoftext|>, <|im_start|>."),
                SupportedParameter("contextLength", "Context window", true, "Load-time setting clamped to 1024..8192; GGUF native caps to 4096."),
                SupportedParameter("threadCount", "CPU threads", true, "GGUF only; LiteRT manages its own threading."),
            )
            return when (engineType) {
                EngineType.LLAMA_CPP -> common + listOf(
                    SupportedParameter("temperature", "Temperature", false, "Validated 0.0..2.0 but not yet plumbed into native sampler (fixed 0.7 default)."),
                    SupportedParameter("topP", "Top-p sampling", false, "Validated 0.0..1.0 but not yet plumbed into native sampler (fixed 0.9 default)."),
                    SupportedParameter("repetitionPenalty", "Repetition penalty", false, "Validated 0.1..2.0; native uses fixed 1.15/freq 0.05/presence 0.05."),
                )
                EngineType.LITERT -> common + listOf(
                    SupportedParameter("temperature", "Temperature", false, "Validated 0.0..2.0; LiteRT-LM runtime manages decoding internally."),
                    SupportedParameter("topP", "Top-p sampling", false, "Validated 0.0..1.0; LiteRT-LM runtime manages decoding internally."),
                    SupportedParameter("repetitionPenalty", "Repetition penalty", false, "Validated 0.1..2.0; not forwarded to LiteRT-LM runtime."),
                )
            }
        }
    }

    /** Resolves prompt profile purely in-memory so JVM unit tests can run without Android. */
    fun effectivePromptProfile(profiles: List<PromptProfile> = emptyList()): PromptProfile {
        val match = profiles.firstOrNull { it.id == activePromptProfileId }
        if (match != null) return match
        val fallbackText = systemPrompt.ifBlank {
            if (unrestrictedMode == UnrestrictedMode.UNRESTRICTED) UNRESTRICTED_SYSTEM_PROMPT
            else DEFAULT_SYSTEM_PROMPT
        }
        return PromptProfile(
            id = activePromptProfileId.ifBlank { PromptProfile.DEFAULT_ID },
            name = if (unrestrictedMode == UnrestrictedMode.UNRESTRICTED) "Unrestricted" else "Standard",
            instructionText = fallbackText,
            effectiveSystemPrompt = fallbackText,
        )
    }

    /** Sanitized single system prompt actually fed to engines (never duplicated). */
    fun resolvedSystemPrompt(profiles: List<PromptProfile> = emptyList()): String {
        val profile = effectivePromptProfile(profiles)
        val candidate = profile.effectiveSystemPrompt.ifBlank { profile.instructionText }.ifBlank { systemPrompt }
        return sanitizeSystemPrompt(candidate.ifBlank {
            if (unrestrictedMode == UnrestrictedMode.UNRESTRICTED) UNRESTRICTED_SYSTEM_PROMPT
            else DEFAULT_SYSTEM_PROMPT
        })
    }
}

data class SupportedParameter(
    val key: String,
    val displayName: String,
    val supportedAtRuntime: Boolean,
    val notes: String,
)

data class PromptProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val instructionText: String,
    val effectiveSystemPrompt: String = instructionText
) {
    companion object {
        const val DEFAULT_ID = "default"
        const val UNRESTRICTED_ID = "unrestricted_default"
        const val MAX_NAME_CHARS = 64
        const val MAX_INSTRUCTION_CHARS = 2000

        /** System-neutral baseline — a helpful offline assistant with no mode-specific bias. */
        fun standardDefault(): PromptProfile = PromptProfile(
            id = DEFAULT_ID,
            name = "Standard",
            instructionText = GenerationConfig.DEFAULT_SYSTEM_PROMPT,
            effectiveSystemPrompt = GenerationConfig.DEFAULT_SYSTEM_PROMPT,
        )

        /** Direct, technical baseline. Still bound by what the model weights allow. */
        fun unrestrictedDefault(): PromptProfile = PromptProfile(
            id = UNRESTRICTED_ID,
            name = "Unrestricted",
            instructionText = GenerationConfig.UNRESTRICTED_SYSTEM_PROMPT,
            effectiveSystemPrompt = GenerationConfig.UNRESTRICTED_SYSTEM_PROMPT,
        )

        fun builtInProfiles(): List<PromptProfile> = listOf(standardDefault(), unrestrictedDefault())
    }

    fun validate(): String? {
        if (name.isBlank()) return "Profile name cannot be blank."
        if (name.length > MAX_NAME_CHARS) return "Profile name must be at most $MAX_NAME_CHARS characters."
        if (instructionText.isBlank()) return "Instruction text cannot be blank."
        if (instructionText.length > MAX_INSTRUCTION_CHARS) return "Instruction text must be at most $MAX_INSTRUCTION_CHARS characters."
        return null
    }

    fun withEffectiveSystemPrompt(effective: String): PromptProfile =
        copy(effectiveSystemPrompt = effective)
}

interface IChatModePreferences {
    val unrestrictedMode: UnrestrictedMode
    val activePromptProfile: PromptProfile
    val promptProfiles: List<PromptProfile>
    fun activePromptProfileId(): String
    fun savePromptProfiles(profiles: List<PromptProfile>)
    fun setActivePromptProfile(profileId: String)
    fun addOrUpdateProfile(profile: PromptProfile)
    fun removeProfile(profileId: String)
}

/**
 * Single sanitization point for anything injected into the system block.
 * - Trims and caps length so a custom prompt cannot blow the context budget.
 * - Strips embedded ChatML role markers so custom instructions cannot corrupt
 *   the conversation format or forge assistant turns.
 */
fun sanitizeSystemPrompt(raw: String): String {
    var clean = raw.trim()
    if (clean.length > GenerationConfig.MAX_SYSTEM_PROMPT_CHARS) {
        clean = clean.take(GenerationConfig.MAX_SYSTEM_PROMPT_CHARS).trimEnd()
    }
    // Strip entire forged role blocks (e.g. <|im_start|>assistant\n...<|im_end|>)
    clean = clean.replace(Regex("<\\|im_start\\|>.*?<\\|im_end\\|>", RegexOption.DOT_MATCHES_ALL), "")
    clean = clean
        .replace("<|im_start|>", "")
        .replace("<|im_end|>", "")
        .replace("<|endoftext|>", "")
    // Collapse 3+ blank lines so giant pasted prompts don't waste budget.
    while (clean.contains("\n\n\n")) clean = clean.replace("\n\n\n", "\n\n")
    return clean.trim().ifEmpty { GenerationConfig.DEFAULT_SYSTEM_PROMPT }
}
