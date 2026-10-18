package com.example.on_device_qwen.domain.context

import com.example.on_device_qwen.model.ChatMessage
import com.example.on_device_qwen.model.GenerationConfig
import com.example.on_device_qwen.model.MemoryCategory
import com.example.on_device_qwen.model.RetrievedMemoryContext

/**
 * Robust, bounded context window manager and prompt constructor.
 * Solves follow-up resolution, token budget reservation, and memory context injection.
 */
object PromptContextBuilder {

    /**
     * Estimates token count using a fast character heuristic (~3.8 chars per token for English & code).
     */
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
            appendLine("[End Context]")
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
        config: GenerationConfig
    ): String {
        val totalCapacity = config.contextLength
        val reserveOutputTokens = config.maxOutputTokens.coerceAtLeast(256)
        val budgetForInput = (totalCapacity - reserveOutputTokens - 32).coerceAtLeast(512)

        // 1. Build System Block
        val baseSystem = config.systemPrompt.ifBlank { GenerationConfig.DEFAULT_SYSTEM_PROMPT }
        val memoryBlock = formatMemorySection(memoryContext)
        val fullSystemText = if (memoryBlock.isNotBlank()) "$baseSystem\n$memoryBlock" else baseSystem
        val systemTokens = estimateTokens(fullSystemText) + 8

        // 2. Latest User Prompt (mandatory)
        val latestUserTokens = estimateTokens(latestUserPrompt) + 8

        var remainingBudget = budgetForInput - systemTokens - latestUserTokens

        // 3. Select previous messages backwards in time that fit in remaining budget
        // Filter out streaming or empty assistant messages
        val validHistory = historyMessages.filter { it.text.isNotBlank() && !it.isStreaming }
        val selectedMessages = mutableListOf<ChatMessage>()

        for (i in validHistory.indices.reversed()) {
            val msg = validHistory[i]
            val msgTokens = estimateTokens(msg.text) + 8
            if (remainingBudget - msgTokens >= 0) {
                selectedMessages.add(0, msg)
                remainingBudget -= msgTokens
            } else {
                // Out of budget, stop including older messages
                break
            }
        }

        // 4. Assemble standard ChatML format
        return buildString {
            append("<|im_start|>system\n")
            append(fullSystemText)
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

    /**
     * Prepares structured message list for LiteRT or multi-turn conversational backends.
     */
    fun buildTurns(
        historyMessages: List<ChatMessage>,
        latestUserPrompt: String,
        memoryContext: RetrievedMemoryContext,
        config: GenerationConfig
    ): List<Pair<String, Boolean>> {
        val validHistory = historyMessages.filter { it.text.isNotBlank() && !it.isStreaming }
        val turns = mutableListOf<Pair<String, Boolean>>()

        // Include recent history (up to last 10 turns)
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
