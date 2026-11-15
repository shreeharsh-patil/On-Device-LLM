package com.example.on_device_qwen.domain.context

import com.example.on_device_qwen.model.BackendType
import com.example.on_device_qwen.model.EngineType
import com.example.on_device_qwen.model.GenerationConfig
import com.example.on_device_qwen.model.ModelErrorCode
import com.example.on_device_qwen.model.SupportedParameter

/**
 * Validates inference parameters before they reach the native engine.
 * Unsupported or out-of-range parameters produce a clear [PromptValidationError]
 * instead of being silently ignored.
 */
data class PromptValidationError(
    val errorCode: ModelErrorCode = ModelErrorCode.INFERENCE_PARAMETER_INVALID,
    val message: String,
    val invalidKey: String? = null,
)

object PromptValidation {

    private val TEMPERATURE_RANGE = GenerationConfig.MIN_TEMPERATURE..GenerationConfig.MAX_TEMPERATURE
    private val TOPP_RANGE = GenerationConfig.MIN_TOP_P..GenerationConfig.MAX_TOP_P
    private val TOPK_RANGE = GenerationConfig.MIN_TOP_K..GenerationConfig.MAX_TOP_K
    private val MINP_RANGE = GenerationConfig.MIN_MIN_P..GenerationConfig.MAX_MIN_P
    private val REPETITION_PENALTY_RANGE = GenerationConfig.MIN_REPETITION_PENALTY..GenerationConfig.MAX_REPETITION_PENALTY
    private val MAX_TOKENS_RANGE = GenerationConfig.MIN_MAX_TOKENS..GenerationConfig.MAX_MAX_TOKENS
    private val CONTEXT_LENGTH_RANGE = GenerationConfig.MIN_CONTEXT_LENGTH..GenerationConfig.MAX_CONTEXT_LENGTH
    private val THREAD_COUNT_RANGE = GenerationConfig.MIN_THREAD_COUNT..GenerationConfig.MAX_THREAD_COUNT
    private val STREAM_BATCH_RANGE = GenerationConfig.MIN_STREAM_BATCH_INTERVAL_MS..GenerationConfig.MAX_STREAM_BATCH_INTERVAL_MS
    private const val SUPPORTED_STOP_SEQUENCE_MAX = 64
    private const val SUPPORTED_STOP_SEQUENCE_TOTAL_CHARS = 4096
    private const val NATIVE_GGUF_MAX_OUTPUT_TOKENS = 4096

    fun validate(config: GenerationConfig): List<PromptValidationError> {
        val errors = mutableListOf<PromptValidationError>()

        errors.addAll(
            validateParameter(
                key = "temperature",
                value = config.temperature,
                range = TEMPERATURE_RANGE,
                message = "Temperature must be between ${TEMPERATURE_RANGE.start} and ${TEMPERATURE_RANGE.endInclusive}.",
            ),
        )
        errors.addAll(
            validateParameter(
                key = "top_p",
                value = config.topP,
                range = TOPP_RANGE,
                message = "Top-P must be between ${TOPP_RANGE.start} and ${TOPP_RANGE.endInclusive}.",
            ),
        )
        errors.addAll(
            validateParameter(
                key = "top_k",
                value = config.topK,
                range = TOPK_RANGE,
                message = "Top-K must be between ${TOPK_RANGE.first} and ${TOPK_RANGE.last}.",
            ),
        )
        errors.addAll(
            validateParameter(
                key = "min_p",
                value = config.minP,
                range = MINP_RANGE,
                message = "Min-P must be between ${MINP_RANGE.start} and ${MINP_RANGE.endInclusive}.",
            ),
        )
        errors.addAll(
            validateParameter(
                key = "repetition_penalty",
                value = config.repetitionPenalty,
                range = REPETITION_PENALTY_RANGE,
                message = "Repetition penalty must be between ${REPETITION_PENALTY_RANGE.start} and ${REPETITION_PENALTY_RANGE.endInclusive}.",
            ),
        )
        errors.addAll(
            validateParameter(
                key = "max_output_tokens",
                value = config.maxOutputTokens,
                range = MAX_TOKENS_RANGE,
                message = "Max output tokens must be between ${MAX_TOKENS_RANGE.first} and ${MAX_TOKENS_RANGE.last}.",
            ),
        )
        errors.addAll(
            validateParameter(
                key = "context_length",
                value = config.contextLength,
                range = CONTEXT_LENGTH_RANGE,
                message = "Context length must be between ${CONTEXT_LENGTH_RANGE.first} and ${CONTEXT_LENGTH_RANGE.last}.",
            ),
        )
        errors.addAll(
            validateParameter(
                key = "thread_count",
                value = config.threadCount,
                range = THREAD_COUNT_RANGE,
                message = "Thread count must be between ${THREAD_COUNT_RANGE.first} and ${THREAD_COUNT_RANGE.last}.",
            ),
        )
        errors.addAll(
            validateParameter(
                key = "stream_batch_interval_ms",
                value = config.streamBatchIntervalMs,
                range = STREAM_BATCH_RANGE,
                message = "Stream batch interval must be between ${STREAM_BATCH_RANGE.first} and ${STREAM_BATCH_RANGE.last} ms.",
            ),
        )
        if (config.seed < -1) {
            errors.add(
                PromptValidationError(
                    message = "Seed must be -1 (random) or a non-negative integer.",
                    invalidKey = "seed",
                ),
            )
        }

        if (config.stopSequences.size > SUPPORTED_STOP_SEQUENCE_MAX) {
            errors.add(
                PromptValidationError(
                    message = "Stop sequences must not contain more than $SUPPORTED_STOP_SEQUENCE_MAX entries.",
                    invalidKey = "stop_sequences",
                ),
            )
        } else {
            val totalChars = config.stopSequences.sumOf { it.length }
            if (totalChars > SUPPORTED_STOP_SEQUENCE_TOTAL_CHARS) {
                errors.add(
                    PromptValidationError(
                        message = "Total length of stop sequences must not exceed $SUPPORTED_STOP_SEQUENCE_TOTAL_CHARS characters.",
                        invalidKey = "stop_sequences",
                    ),
                )
            }
        }

        config.stopSequences.forEach { seq ->
            if (seq.isBlank()) {
                errors.add(
                    PromptValidationError(
                        message = "Stop sequences must not be blank.",
                        invalidKey = "stop_sequences",
                    ),
                )
            }
        }

        if (config.systemPrompt.isNotBlank()) {
            val trimmed = config.systemPrompt.trim()
            if (trimmed.length > GenerationConfig.MAX_SYSTEM_PROMPT_CHARS) {
                errors.add(
                    PromptValidationError(
                        message = "System prompt must not exceed ${GenerationConfig.MAX_SYSTEM_PROMPT_CHARS} characters after trimming.",
                        invalidKey = "system_prompt",
                    ),
                )
            }
        }

        return errors
    }

    /** Returns the up-to-date runtime parameter support matrix backed by Wave 2 JNI & LiteRT-LM 0.19.1 wiring. */
    fun effectiveRuntimeSupport(engineType: EngineType): List<SupportedParameter> {
        return when (engineType) {
            EngineType.LLAMA_CPP -> listOf(
                SupportedParameter("systemPrompt", "System prompt", true, "Single sanitized system block formatted per model template family."),
                SupportedParameter("maxOutputTokens", "Maximum output tokens", true, "Clamped 1..$NATIVE_GGUF_MAX_OUTPUT_TOKENS natively (bounded by n_ctx)."),
                SupportedParameter("stopSequences", "Stop sequences", true, "Checked via sliding holdback buffer alongside EOG tokens."),
                SupportedParameter("contextLength", "Context window", true, "Load-time n_ctx clamped 1024..8192 natively."),
                SupportedParameter("threadCount", "CPU threads", true, "Load-time n_threads clamped 1..8 and thermally throttled."),
                SupportedParameter("temperature", "Temperature", true, "0.0 uses greedy argmax; >0 uses scaled temperature sampler."),
                SupportedParameter("topP", "Top-P", true, "Applied natively via llama_sampler_init_top_p."),
                SupportedParameter("topK", "Top-K", true, "Applied natively via llama_sampler_init_top_k (0 disables)."),
                SupportedParameter("minP", "Min-P", true, "Applied natively via llama_sampler_init_min_p (0 disables)."),
                SupportedParameter("repetitionPenalty", "Repetition penalty", true, "Applied natively over last 64 tokens."),
                SupportedParameter("seed", "RNG seed", true, "Forwarded to llama_sampler_init_dist (-1 uses default seed)."),
            )
            EngineType.LITERT -> listOf(
                SupportedParameter("systemPrompt", "System prompt", true, "Applied via ConversationConfig.systemInstruction without user-turn duplication."),
                SupportedParameter("maxOutputTokens", "Maximum output tokens", true, "Applied via ConversationConfig / sendMessageAsync maxOutputToken."),
                SupportedParameter("stopSequences", "Stop sequences", false, "LiteRT-LM handles model EOG tokens internally and ignores custom stop strings."),
                SupportedParameter("contextLength", "Context window", true, "Applied via EngineConfig.maxNumTokens capped by ModelInfo.maxContextTokens()."),
                SupportedParameter("threadCount", "CPU threads", true, "Forwarded to Backend.CPU(threadCount) on CPU execution."),
                SupportedParameter("temperature", "Temperature", true, "Applied via SamplerConfig."),
                SupportedParameter("topP", "Top-P", true, "Applied via SamplerConfig."),
                SupportedParameter("topK", "Top-K", true, "Applied via SamplerConfig (minimum 1)."),
                SupportedParameter("minP", "Min-P", false, "LiteRT-LM 0.19.1 SamplerConfig does not expose minP filtering."),
                SupportedParameter("repetitionPenalty", "Repetition penalty", true, "Forwarded via RepetitionPenaltyConfig."),
                SupportedParameter("seed", "RNG seed", true, "Forwarded via SamplerConfig."),
            )
        }
    }

    fun auditRuntimeSupport(config: GenerationConfig): List<PromptValidationError> {
        val inferredEngine = if (config.backend == BackendType.GPU || config.backend == BackendType.NPU) {
            EngineType.LITERT
        } else {
            EngineType.LLAMA_CPP
        }
        return auditRuntimeSupport(config, inferredEngine)
    }

    fun auditRuntimeSupport(
        config: GenerationConfig,
        engineType: EngineType,
    ): List<PromptValidationError> {
        val notes = mutableListOf<PromptValidationError>()
        if (engineType == EngineType.LLAMA_CPP) {
            if (config.maxOutputTokens > NATIVE_GGUF_MAX_OUTPUT_TOKENS) {
                notes.add(
                    PromptValidationError(
                        errorCode = ModelErrorCode.UNKNOWN,
                        message = "GGUF native runtime caps maxOutputTokens at $NATIVE_GGUF_MAX_OUTPUT_TOKENS (requested ${config.maxOutputTokens}).",
                        invalidKey = "max_output_tokens",
                    ),
                )
            }
            if (config.backend != BackendType.CPU) {
                notes.add(
                    PromptValidationError(
                        errorCode = ModelErrorCode.UNKNOWN,
                        message = "Requested ${config.backend.chipText}, but GGUF inference runs CPU-only in this build.",
                        invalidKey = "backend",
                    ),
                )
            }
        } else {
            val defaultMinP = GenerationConfig().minP
            if (config.minP != defaultMinP && config.minP > 0f) {
                notes.add(
                    PromptValidationError(
                        errorCode = ModelErrorCode.UNKNOWN,
                        message = "Parameter 'minP' is accepted for GGUF but not exposed by LiteRT-LM SamplerConfig.",
                        invalidKey = "min_p",
                    ),
                )
            }
        }
        return notes
    }

    private fun <T : Comparable<T>> validateParameter(
        key: String,
        value: T,
        range: ClosedRange<T>,
        message: String,
    ): List<PromptValidationError> {
        return if (value in range) {
            emptyList()
        } else {
            listOf(
                PromptValidationError(
                    message = message,
                    invalidKey = key,
                ),
            )
        }
    }

    fun hasUnsupportedParameters(config: GenerationConfig): Boolean = validate(config).isNotEmpty()
}
