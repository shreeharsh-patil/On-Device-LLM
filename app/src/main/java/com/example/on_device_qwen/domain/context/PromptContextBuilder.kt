package com.example.on_device_qwen.domain.context

import com.example.on_device_qwen.model.*

object PromptContextBuilder {

    fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        return (text.length / 3.8).toInt() + 1
    }

    /**
     * Formats retrieved persistent memories into a well-isolated prompt section.
     */
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

    /**
     * Builds bounded ChatML prompt formatted specifically for Dolphin / Qwen models.
     * Guarantees:
     * - System prompt and latest user prompt are NEVER dropped.
     * - Sufficient space is reserved for new tokens (maxOutputTokens).
     * - Multi-turn conversation turns are included in chronological order within budget.
     */
    fun buildChatMl(
        historyMessages: List<ChatMessage>,
        latestUserPrompt: String,
        memoryContext: RetrievedMemoryContext,
        config: GenerationConfig,
        profiles: List<PromptProfile> = PromptProfile.builtInProfiles(),
        promptProfile: PromptProfile? = null
    ): String {
        val totalCapacity = config.contextLength
        val reserveOutputTokens = config.maxOutputTokens.coerceAtLeast(256)
        val budgetForInput = (totalCapacity - reserveOutputTokens - 32).coerceAtLeast(512)

        val resolvedProfile = promptProfile
            ?: config.effectivePromptProfile(profiles)

        val baseSystem = resolvedProfile.effectiveSystemPrompt.ifBlank { config.systemPrompt }.ifBlank { GenerationConfig.DEFAULT_SYSTEM_PROMPT }
        val modeBanner = buildInferenceModeBanner(config)
        val memoryBlock = formatMemorySection(memoryContext)
        val fullSystemText = if (memoryBlock.isNotBlank()) "$modeBanner$baseSystem\n$memoryBlock" else "$modeBanner$baseSystem"
        val systemTokens = estimateTokens(fullSystemText) + 8

        val latestUserTokens = estimateTokens(latestUserPrompt) + 8
        var remainingBudget = budgetForInput - systemTokens - latestUserTokens

        val validHistory = historyMessages.filter { it.text.isNotBlank() && !it.isStreaming }
        val historyWithoutLatest = if (validHistory.isNotEmpty() && validHistory.last().isUser && validHistory.last().text.trim() == latestUserPrompt.trim()) {
            validHistory.dropLast(1)
        } else {
            validHistory
        }
        val selectedMessages = mutableListOf<ChatMessage>()

        for (i in historyWithoutLatest.indices.reversed()) {
            val msg = historyWithoutLatest[i]
            val msgTokens = estimateTokens(msg.text) + 8
            if (remainingBudget - msgTokens >= 0) {
                selectedMessages.add(0, msg)
                remainingBudget -= msgTokens
            } else {
                break
            }
        }

        return buildString {
            append("<|im_start|>system\n")
            append(fullSystemText.trim())
            append("<|im_end|>\n")

            for (msg in selectedMessages) {
                val role = if (msg.isUser) "user" else "assistant"
                append("<|im_start|>$role\n")
                append(msg.text.trim())
                append("<|im_end|>\n")
            }

            append("<|im_start|>user\n")
            append(latestUserPrompt.trim())
            append("<|im_end|>\n")
            append("<|im_start|>assistant\n")
        }
    }

    fun buildChatMl(
        historyMessages: List<ChatMessage>,
        latestUserPrompt: String,
        memoryContext: RetrievedMemoryContext,
        config: GenerationConfig
    ): String = buildChatMl(
        historyMessages = historyMessages,
        latestUserPrompt = latestUserPrompt,
        memoryContext = memoryContext,
        config = config,
        profiles = PromptProfile.builtInProfiles(),
        promptProfile = null
    )

    fun buildChatMl(
        historyMessages: List<ChatMessage>,
        latestUserPrompt: String,
        memoryContext: RetrievedMemoryContext,
        config: GenerationConfig,
        profiles: List<PromptProfile>
    ): String = buildChatMl(
        historyMessages = historyMessages,
        latestUserPrompt = latestUserPrompt,
        memoryContext = memoryContext,
        config = config,
        profiles = profiles,
        promptProfile = config.effectivePromptProfile(profiles)
    )

    fun sanitizeSystemPrompt(raw: String): String =
        com.example.on_device_qwen.model.sanitizeSystemPrompt(raw)

    fun buildTurns(
        historyMessages: List<ChatMessage>,
        latestUserPrompt: String,
        memoryContext: RetrievedMemoryContext,
        config: GenerationConfig
    ): List<Pair<String, Boolean>> {
        val validHistory = historyMessages.filter { it.text.isNotBlank() && !it.isStreaming }
        val turns = mutableListOf<Pair<String, Boolean>>()

        for (msg in validHistory.takeLast(10)) {
            turns.add(Pair(msg.text, msg.isUser))
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
}
