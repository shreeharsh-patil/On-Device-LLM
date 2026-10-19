package com.example.on_device_qwen.domain.context

import com.example.on_device_qwen.model.GenerationConfig
import com.example.on_device_qwen.model.ModelErrorCode
import com.example.on_device_qwen.model.SupportedParameter

/**
 * Validates inference parameters before they reach the native engine.
 * Unsupported or out-of-range parameters produce a clear [PromptValidationError]
 * instead of being silently ignored.
 *
 * Important: this validates app-level configuration only. It does not change
 * restrictions embedded in the model weights, its fine-tuning, or the native
 * runtime. Unsupported parameters are explicitly listed in the returned errors
 * so the UI can tell the user what was ignored and why.
 */
data class PromptValidationError(
    val errorCode: ModelErrorCode = ModelErrorCode.INFERENCE_PARAMETER_INVALID,
    val message: String,
    val invalidKey: String? = null
)

object PromptValidation {

    private val TEMPERATURE_RANGE = 0.0f..2.0f
    private val TOPP_RANGE = 0.0f..1.0f
    private val REPETITION_PENALTY_RANGE = 0.1f..2.0f
    private val MAX_TOKENS_RANGE = 1..8192
    private val CONTEXT_LENGTH_MIN = 1024
    private val CONTEXT_LENGTH_MAX = 8192
    private val THREAD_COUNT_MIN = 1
    private val THREAD_COUNT_MAX = 256
    private val SUPPORTED_STOP_SEQUENCE_MAX = 64
    private val SUPPORTED_STOP_SEQUENCE_TOTAL_CHARS = 4096

    fun validate(config: GenerationConfig): List<PromptValidationError> {
        val errors = mutableListOf<PromptValidationError>()

        errors.addAll(validateParameter(
            key = "temperature",
            value = config.temperature,
            range = TEMPERATURE_RANGE,
            message = "Temperature must be between ${TEMPERATURE_RANGE.start} and ${TEMPERATURE_RANGE.endInclusive}."
        ))

        errors.addAll(validateParameter(
            key = "top_p",
            value = config.topP,
            range = TOPP_RANGE,
            message = "Top-P must be between ${TOPP_RANGE.start} and ${TOPP_RANGE.endInclusive}."
        ))

        errors.addAll(validateParameter(
            key = "repetition_penalty",
            value = config.repetitionPenalty,
            range = REPETITION_PENALTY_RANGE,
            message = "Repetition penalty must be between ${REPETITION_PENALTY_RANGE.start} and ${REPETITION_PENALTY_RANGE.endInclusive}."
        ))

        errors.addAll(validateParameter(
            key = "max_output_tokens",
            value = config.maxOutputTokens,
            range = MAX_TOKENS_RANGE,
            message = "Max output tokens must be between ${MAX_TOKENS_RANGE.start} and ${MAX_TOKENS_RANGE.endInclusive}."
        ))

        errors.addAll(validateParameter(
            key = "context_length",
            value = config.contextLength,
            range = CONTEXT_LENGTH_MIN..CONTEXT_LENGTH_MAX,
            message = "Context length must be between $CONTEXT_LENGTH_MIN and $CONTEXT_LENGTH_MAX."
        ))

        errors.addAll(validateParameter(
            key = "thread_count",
            value = config.threadCount,
            range = THREAD_COUNT_MIN..THREAD_COUNT_MAX,
            message = "Thread count must be between $THREAD_COUNT_MIN and $THREAD_COUNT_MAX."
        ))

        if (config.stopSequences.size > SUPPORTED_STOP_SEQUENCE_MAX) {
            errors.add(
                PromptValidationError(
                    message = "Stop sequences must not contain more than $SUPPORTED_STOP_SEQUENCE_MAX entries.",
                    invalidKey = "stop_sequences"
                )
            )
        } else {
            val totalChars = config.stopSequences.sumOf { it.length }
            if (totalChars > SUPPORTED_STOP_SEQUENCE_TOTAL_CHARS) {
                errors.add(
                    PromptValidationError(
                        message = "Total length of stop sequences must not exceed $SUPPORTED_STOP_SEQUENCE_TOTAL_CHARS characters.",
                        invalidKey = "stop_sequences"
                    )
                )
            }
        }

        config.stopSequences.forEach { seq ->
            if (seq.isBlank()) {
                errors.add(
                    PromptValidationError(
                        message = "Stop sequences must not be blank.",
                        invalidKey = "stop_sequences"
                    )
                )
            }
        }

        if (config.systemPrompt.isNotBlank()) {
            val trimmed = config.systemPrompt.trim()
            if (trimmed.length > GenerationConfig.MAX_SYSTEM_PROMPT_CHARS) {
                errors.add(
                    PromptValidationError(
                        message = "System prompt must not exceed ${GenerationConfig.MAX_SYSTEM_PROMPT_CHARS} characters after trimming.",
                        invalidKey = "system_prompt"
                    )
                )
            }
        }

        return errors
    }

    fun auditRuntimeSupport(config: GenerationConfig): List<PromptValidationError> {
        val notes = mutableListOf<PromptValidationError>()
        val supported = GenerationConfig.supportedParameters(config.backend.toEngineType())
        supported.forEach { parameter ->
            when (parameter.key) {
                "temperature", "topP", "repetitionPenalty" -> {
                    if (!parameter.supportedAtRuntime) {
                        notes.add(
                            PromptValidationError(
                                errorCode = ModelErrorCode.UNKNOWN,
                                message = "Parameter '${parameter.key}' (${parameter.displayName}) is accepted by the app but ${parameter.notes}",
                                invalidKey = parameter.key
                            )
                        )
                    }
                }
            }
        }
        return notes
    }

    private fun <T : Comparable<T>> validateParameter(
        key: String,
        value: T,
        range: ClosedRange<T>,
        message: String
    ): List<PromptValidationError> {
        return if (value in range) {
            emptyList()
        } else {
            listOf(
                PromptValidationError(
                    message = message,
                    invalidKey = key
                )
            )
        }
    }

    fun hasUnsupportedParameters(config: GenerationConfig): Boolean {
        return validate(config).isNotEmpty()
    }

    private fun com.example.on_device_qwen.model.BackendType.toEngineType() =
        when (this) {
            com.example.on_device_qwen.model.BackendType.CPU ->
                com.example.on_device_qwen.model.EngineType.LLAMA_CPP
            com.example.on_device_qwen.model.BackendType.GPU,
            com.example.on_device_qwen.model.BackendType.NPU ->
                com.example.on_device_qwen.model.EngineType.LITERT
        }
}
