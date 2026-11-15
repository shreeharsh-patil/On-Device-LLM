package com.example.on_device_qwen.domain.context

import com.example.on_device_qwen.model.ChatMessage
import com.example.on_device_qwen.model.ChatTemplateFamily
import com.example.on_device_qwen.model.GenerationConfig
import com.example.on_device_qwen.model.MemoryCategory
import com.example.on_device_qwen.model.ModelMetadata
import com.example.on_device_qwen.model.PromptProfile
import com.example.on_device_qwen.model.RetrievedMemoryContext
import com.example.on_device_qwen.model.UnrestrictedMode

data class BuiltPromptResult(
    val formattedPrompt: String,
    val templateFamily: ChatTemplateFamily,
    val includedHistoryCount: Int,
    val droppedHistoryCount: Int,
    val estimatedInputTokens: Int,
    val reservedOutputTokens: Int,
    val stopSequences: List<String>,
    val systemInstruction: String,
)

object PromptContextBuilder {

    private val TEMPLATE_CONTROL_MARKERS = listOf(
        "<|im_start|>",
        "<|im_end|>",
        "<|endoftext|>",
        "<|begin_of_text|>",
        "<|start_header_id|>",
        "<|end_header_id|>",
        "<|eot_id|>",
        "<start_of_turn>",
        "<end_of_turn>",
        "<|system|>",
        "<|user|>",
        "<|assistant|>",
        "<|end|>",
        "<｜begin▁of▁sentence｜>",
        "<｜end▁of▁sentence｜>",
        "<｜User｜>",
        "<｜Assistant｜>",
    )

    private val THINK_BLOCK_REGEX = Regex("""<think>[\s\S]*?(?:</think>|$)""", RegexOption.IGNORE_CASE)

    fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        return (text.length / 3.8).toInt() + 1
    }

    fun stripThinkingBlock(text: String): String {
        return text.replace(THINK_BLOCK_REGEX, "").trim()
    }

    /** Removes forged chat-template role/stop delimiters from user or assistant turn payloads. */
    fun sanitizeMessageContent(raw: String): String {
        var clean = raw.trim()
        for (marker in TEMPLATE_CONTROL_MARKERS) {
            if (clean.contains(marker)) {
                clean = clean.replace(marker, "")
            }
        }
        return clean.trim()
    }

    /** Formats retrieved persistent memories into a well-isolated prompt section. */
    fun formatMemorySection(memoryContext: RetrievedMemoryContext): String {
        if (memoryContext.isEmpty) return ""

        return buildString {
            appendLine()
            appendLine("[Relevant Context & Persistent Memory]")
            if (memoryContext.summary != null) {
                appendLine("Prior Conversation Summary: ${memoryContext.summary.summaryText}")
            }

            val userMems = memoryContext.memories.filter { it.category == MemoryCategory.USER }
            if (userMems.isNotEmpty()) {
                appendLine("User Preferences:")
                for (m in userMems) {
                    appendLine("- ${m.content}")
                }
            }

            val projectMems = memoryContext.memories.filter { it.category == MemoryCategory.PROJECT }
            if (projectMems.isNotEmpty()) {
                appendLine("Project Decisions & Architecture:")
                for (m in projectMems) {
                    appendLine("- ${m.content}")
                }
            }

            val episodicMems = memoryContext.memories.filter { it.category == MemoryCategory.EPISODIC }
            if (episodicMems.isNotEmpty()) {
                appendLine("Past Discussions:")
                for (m in episodicMems) {
                    appendLine("- ${m.content}")
                }
            }

            val otherMems = memoryContext.memories.filter {
                it.category !in listOf(MemoryCategory.USER, MemoryCategory.PROJECT, MemoryCategory.EPISODIC)
            }
            if (otherMems.isNotEmpty()) {
                appendLine("Additional Context:")
                for (m in otherMems) {
                    appendLine("- ${m.content}")
                }
            }
            append("[End Context]")
        }
    }

    fun buildInferenceModeBanner(config: GenerationConfig): String {
        return if (config.unrestrictedMode == UnrestrictedMode.UNRESTRICTED) {
            "[Inference mode: Unrestricted]\n"
        } else {
            "[Inference mode: Standard]\n"
        }
    }

    fun buildSystemInstructionWithContext(
        memoryContext: RetrievedMemoryContext,
        config: GenerationConfig,
        profiles: List<PromptProfile> = PromptProfile.builtInProfiles(),
        promptProfile: PromptProfile? = null,
    ): String {
        val resolvedProfile = promptProfile ?: config.effectivePromptProfile(profiles)
        val rawBase = resolvedProfile.effectiveSystemPrompt
            .ifBlank { config.systemPrompt }
            .ifBlank { GenerationConfig.DEFAULT_SYSTEM_PROMPT }
        val baseSystem = sanitizeSystemPrompt(rawBase)
        val modeBanner = buildInferenceModeBanner(config)
        val memoryBlock = formatMemorySection(memoryContext)
        return if (memoryBlock.isNotBlank()) {
            "$modeBanner$baseSystem\n$memoryBlock"
        } else {
            "$modeBanner$baseSystem"
        }.trim()
    }

    fun buildPromptForModel(
        historyMessages: List<ChatMessage>,
        latestUserPrompt: String,
        memoryContext: RetrievedMemoryContext,
        config: GenerationConfig,
        metadata: ModelMetadata? = null,
        profiles: List<PromptProfile> = PromptProfile.builtInProfiles(),
        promptProfile: PromptProfile? = null,
        templateOverride: ChatTemplateFamily? = null,
    ): BuiltPromptResult {
        val family = templateOverride ?: metadata?.templateFamily ?: ChatTemplateFamily.CHATML
        val totalCapacity = config.contextLength.coerceAtLeast(GenerationConfig.MIN_CONTEXT_LENGTH)
        val reserveOutputTokens = config.maxOutputTokens.coerceIn(64, (totalCapacity - 192).coerceAtLeast(64))
        val budgetForInput = (totalCapacity - reserveOutputTokens - 32).coerceAtLeast(256)

        val resolvedSystemText = buildSystemInstructionWithContext(
            memoryContext = memoryContext,
            config = config,
            profiles = profiles,
            promptProfile = promptProfile,
        )
        val systemTokens = estimateTokens(resolvedSystemText) + 8

        val cleanedLatestUser = sanitizeMessageContent(latestUserPrompt)
        val availableForUser = (budgetForInput - systemTokens - 8).coerceAtLeast(64)
        val boundedLatestUser = truncateTextToTokenBudget(cleanedLatestUser, availableForUser)
        val latestUserTokens = estimateTokens(boundedLatestUser) + 8
        var remainingBudget = (budgetForInput - systemTokens - latestUserTokens).coerceAtLeast(0)

        val validHistory = historyMessages.filter {
            !it.isStreaming && !it.isError && it.text.isNotBlank()
        }
        val historyWithoutLatest = if (
            validHistory.isNotEmpty() &&
            validHistory.last().isUser &&
            validHistory.last().text.trim() == latestUserPrompt.trim()
        ) {
            validHistory.dropLast(1)
        } else {
            validHistory
        }

        val selectedMessages = mutableListOf<Pair<Boolean, String>>()
        for (i in historyWithoutLatest.indices.reversed()) {
            val msg = historyWithoutLatest[i]
            val body = if (msg.isUser) {
                sanitizeMessageContent(msg.text)
            } else {
                sanitizeMessageContent(stripThinkingBlock(msg.text))
            }
            if (body.isBlank()) continue
            val msgTokens = estimateTokens(body) + 8
            if (remainingBudget - msgTokens >= 0) {
                selectedMessages.add(0, msg.isUser to body)
                remainingBudget -= msgTokens
            } else {
                break
            }
        }

        val formatted = formatPrompt(
            family = family,
            systemInstruction = resolvedSystemText,
            historyTurns = selectedMessages,
            latestUserPrompt = boundedLatestUser,
            addAssistantPrefix = true,
        )

        val effectiveStops = (config.stopSequences + family.defaultStopSequences)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        return BuiltPromptResult(
            formattedPrompt = formatted,
            templateFamily = family,
            includedHistoryCount = selectedMessages.size,
            droppedHistoryCount = (historyWithoutLatest.size - selectedMessages.size).coerceAtLeast(0),
            estimatedInputTokens = estimateTokens(formatted),
            reservedOutputTokens = reserveOutputTokens,
            stopSequences = effectiveStops,
            systemInstruction = resolvedSystemText,
        )
    }

    /** Backwards-compatible ChatML entrypoints delegating to the unified builder. */
    fun buildChatMl(
        historyMessages: List<ChatMessage>,
        latestUserPrompt: String,
        memoryContext: RetrievedMemoryContext,
        config: GenerationConfig,
        profiles: List<PromptProfile> = PromptProfile.builtInProfiles(),
        promptProfile: PromptProfile? = null,
    ): String = buildPromptForModel(
        historyMessages = historyMessages,
        latestUserPrompt = latestUserPrompt,
        memoryContext = memoryContext,
        config = config,
        profiles = profiles,
        promptProfile = promptProfile,
        templateOverride = ChatTemplateFamily.CHATML,
    ).formattedPrompt

    fun buildChatMl(
        historyMessages: List<ChatMessage>,
        latestUserPrompt: String,
        memoryContext: RetrievedMemoryContext,
        config: GenerationConfig,
    ): String = buildChatMl(
        historyMessages = historyMessages,
        latestUserPrompt = latestUserPrompt,
        memoryContext = memoryContext,
        config = config,
        profiles = PromptProfile.builtInProfiles(),
        promptProfile = null,
    )

    fun buildChatMl(
        historyMessages: List<ChatMessage>,
        latestUserPrompt: String,
        memoryContext: RetrievedMemoryContext,
        config: GenerationConfig,
        profiles: List<PromptProfile>,
    ): String = buildChatMl(
        historyMessages = historyMessages,
        latestUserPrompt = latestUserPrompt,
        memoryContext = memoryContext,
        config = config,
        profiles = profiles,
        promptProfile = config.effectivePromptProfile(profiles),
    )

    fun sanitizeSystemPrompt(raw: String): String =
        com.example.on_device_qwen.model.sanitizeSystemPrompt(raw)

    fun buildTurns(
        historyMessages: List<ChatMessage>,
        latestUserPrompt: String,
        memoryContext: RetrievedMemoryContext,
        config: GenerationConfig,
    ): List<Pair<String, Boolean>> {
        val validHistory = historyMessages.filter {
            !it.isStreaming && !it.isError && it.text.isNotBlank()
        }
        val turns = mutableListOf<Pair<String, Boolean>>()

        for (msg in validHistory.takeLast(10)) {
            val cleaned = if (msg.isUser) msg.text.trim() else stripThinkingBlock(msg.text)
            if (cleaned.isNotBlank()) {
                turns.add(Pair(cleaned, msg.isUser))
            }
        }

        val memoryBlock = formatMemorySection(memoryContext)
        val promptWithMemory = if (memoryBlock.isNotBlank() && turns.isEmpty()) {
            "$latestUserPrompt\n\n$memoryBlock"
        } else {
            latestUserPrompt
        }

        turns.add(Pair(promptWithMemory, true))
        return turns
    }

    fun formatMessages(
        messages: List<Pair<String, String>>,
        family: ChatTemplateFamily,
        addAssistant: Boolean = true,
    ): String {
        val systemMsg = messages.firstOrNull { it.first == "system" }?.second.orEmpty()
        val nonSystem = messages.filter { it.first != "system" }
        if (nonSystem.isEmpty()) {
            return formatPrompt(family, systemMsg, emptyList(), "", addAssistant)
        }
        val lastIsUser = nonSystem.last().first == "user"
        val history = if (lastIsUser) nonSystem.dropLast(1) else nonSystem
        val latestUser = if (lastIsUser) nonSystem.last().second else ""
        return formatPrompt(
            family = family,
            systemInstruction = systemMsg,
            historyTurns = history.map { (it.first == "user") to it.second },
            latestUserPrompt = latestUser,
            addAssistantPrefix = addAssistant,
        )
    }

    private fun truncateTextToTokenBudget(text: String, maxTokens: Int): String {
        if (estimateTokens(text) <= maxTokens) return text
        val maxChars = (maxTokens * 3.5).toInt().coerceAtLeast(32)
        val suffix = " [...truncated]"
        if (text.length <= maxChars) return text
        val keepChars = (maxChars - suffix.length).coerceAtLeast(16)
        return text.take(keepChars).trimEnd() + suffix
    }

    private fun formatPrompt(
        family: ChatTemplateFamily,
        systemInstruction: String,
        historyTurns: List<Pair<Boolean, String>>,
        latestUserPrompt: String,
        addAssistantPrefix: Boolean,
    ): String {
        return buildString {
            when (family) {
                ChatTemplateFamily.CHATML -> {
                    if (systemInstruction.isNotBlank()) {
                        append("<|im_start|>system\n").append(systemInstruction).append("<|im_end|>\n")
                    }
                    for ((isUser, text) in historyTurns) {
                        val role = if (isUser) "user" else "assistant"
                        append("<|im_start|>").append(role).append("\n").append(text).append("<|im_end|>\n")
                    }
                    if (latestUserPrompt.isNotBlank() || historyTurns.isEmpty()) {
                        append("<|im_start|>user\n").append(latestUserPrompt).append("<|im_end|>\n")
                    }
                    if (addAssistantPrefix) {
                        append("<|im_start|>assistant\n")
                    }
                }
                ChatTemplateFamily.LLAMA3 -> {
                    append("<|begin_of_text|>")
                    if (systemInstruction.isNotBlank()) {
                        append("<|start_header_id|>system<|end_header_id|>\n\n")
                            .append(systemInstruction)
                            .append("<|eot_id|>")
                    }
                    for ((isUser, text) in historyTurns) {
                        val role = if (isUser) "user" else "assistant"
                        append("<|start_header_id|>").append(role).append("<|end_header_id|>\n\n")
                            .append(text).append("<|eot_id|>")
                    }
                    if (latestUserPrompt.isNotBlank() || historyTurns.isEmpty()) {
                        append("<|start_header_id|>user<|end_header_id|>\n\n")
                            .append(latestUserPrompt).append("<|eot_id|>")
                    }
                    if (addAssistantPrefix) {
                        append("<|start_header_id|>assistant<|end_header_id|>\n\n")
                    }
                }
                ChatTemplateFamily.GEMMA -> {
                    var systemPrepended = false
                    fun prependSystemIfFirst(text: String): String {
                        if (systemPrepended || systemInstruction.isBlank()) return text
                        systemPrepended = true
                        return "$systemInstruction\n\n$text"
                    }
                    for ((isUser, text) in historyTurns) {
                        val role = if (isUser) "user" else "model"
                        val content = if (isUser) prependSystemIfFirst(text) else text
                        append("<start_of_turn>").append(role).append("\n")
                            .append(content).append("<end_of_turn>\n")
                    }
                    if (latestUserPrompt.isNotBlank() || historyTurns.isEmpty()) {
                        val content = prependSystemIfFirst(latestUserPrompt)
                        append("<start_of_turn>user\n").append(content).append("<end_of_turn>\n")
                    }
                    if (addAssistantPrefix) {
                        append("<start_of_turn>model\n")
                    }
                }
                ChatTemplateFamily.PHI -> {
                    if (systemInstruction.isNotBlank()) {
                        append("<|system|>\n").append(systemInstruction).append("<|end|>\n")
                    }
                    for ((isUser, text) in historyTurns) {
                        val role = if (isUser) "user" else "assistant"
                        append("<|").append(role).append("|>\n").append(text).append("<|end|>\n")
                    }
                    if (latestUserPrompt.isNotBlank() || historyTurns.isEmpty()) {
                        append("<|user|>\n").append(latestUserPrompt).append("<|end|>\n")
                    }
                    if (addAssistantPrefix) {
                        append("<|assistant|>\n")
                    }
                }
                ChatTemplateFamily.DEEPSEEK -> {
                    append("<｜begin▁of▁sentence｜>")
                    if (systemInstruction.isNotBlank()) {
                        append(systemInstruction).append("\n\n")
                    }
                    for ((isUser, text) in historyTurns) {
                        if (isUser) {
                            append("<｜User｜>").append(text)
                        } else {
                            append("<｜Assistant｜>").append(text).append("<｜end▁of▁sentence｜>")
                        }
                    }
                    if (latestUserPrompt.isNotBlank() || historyTurns.isEmpty()) {
                        append("<｜User｜>").append(latestUserPrompt)
                    }
                    if (addAssistantPrefix) {
                        append("<｜Assistant｜>")
                    }
                }
                ChatTemplateFamily.RAW -> {
                    if (systemInstruction.isNotBlank()) {
                        append("[System]\n").append(systemInstruction).append("\n\n")
                    }
                    for ((isUser, text) in historyTurns) {
                        append(if (isUser) "User: " else "Assistant: ").append(text).append("\n")
                    }
                    if (latestUserPrompt.isNotBlank() || historyTurns.isEmpty()) {
                        append("User: ").append(latestUserPrompt).append("\n")
                    }
                    if (addAssistantPrefix) {
                        append("Assistant: ")
                    }
                }
            }
        }
    }
}
