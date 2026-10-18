package com.example.on_device_qwen.domain.memory

import android.content.Context
import com.example.on_device_qwen.data.preferences.AppPreferences
import com.example.on_device_qwen.data.repository.ConversationRepository
import com.example.on_device_qwen.model.ConversationSummary
import com.example.on_device_qwen.model.MemoryCategory
import com.example.on_device_qwen.model.MemoryItem
import com.example.on_device_qwen.model.RetrievedMemoryContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

import com.example.on_device_qwen.model.IMemoryPreferences
import com.example.on_device_qwen.model.IMemoryRepository

/**
 * Local-first, offline memory consolidation and extraction manager.
 * Operates without external APIs or cloud dependencies, strictly respecting device privacy.
 */
class MemoryManager(
    private val repository: IMemoryRepository,
    private val prefs: IMemoryPreferences
) {
    companion object {
        private val SENSITIVE_PATTERN = Regex(
            "(?i)\\b(password|passwd|secret|apikey|api_key|token|bearer\\s+[a-zA-Z0-9_\\-\\.]+|ghp_[a-zA-Z0-9]+|sk-[a-zA-Z0-9]+)\\b"
        )
    }

    /**
     * Retrieve relevant user, project, and episodic memories for the incoming query,
     * respecting the user's memory enablement preference.
     */
    suspend fun retrieveMemoryContext(
        query: String,
        conversationId: String,
        maxTokens: Int = 512
    ): RetrievedMemoryContext = withContext(Dispatchers.IO) {
        if (!prefs.persistentMemoryEnabled) {
            return@withContext RetrievedMemoryContext()
        }

        val limit = prefs.maxMemoryItems
        val relevantMemories = repository.findRelevantMemories(query, limit = limit)
        val summary = repository.getConversationSummary(conversationId)

        // Estimate token cost (~4 characters per token heuristic)
        val textLength = relevantMemories.sumOf { it.content.length } + (summary?.summaryText?.length ?: 0)
        val tokenEstimate = (textLength / 4) + 16

        RetrievedMemoryContext(
            memories = relevantMemories,
            summary = summary,
            tokenEstimate = tokenEstimate
        )
    }

    /**
     * Asynchronously extracts salient facts from conversation turn and persists them
     * with deduplication and sensitive information sanitization.
     */
    suspend fun extractAndConsolidate(
        conversationId: String,
        userText: String,
        assistantText: String
    ) = withContext(Dispatchers.IO) {
        if (!prefs.persistentMemoryEnabled) return@withContext
        if (containsSensitiveInfo(userText) || containsSensitiveInfo(assistantText)) return@withContext

        val extracted = mutableListOf<MemoryItem>()

        // 1. User Preference & Persona Extraction
        extractUserPreferences(userText, conversationId)?.let { extracted.add(it) }

        // 2. Project Architecture & Technical Decisions
        extractProjectDecisions(userText, assistantText, conversationId)?.let { extracted.add(it) }

        // 3. Persist and deduplicate
        for (item in extracted) {
            consolidateMemoryItem(item)
        }
    }

    /**
     * Deduplicates and consolidates newly extracted memories into the repository.
     * Updates existing memories if they pertain to the same entity.
     */
    suspend fun consolidateMemoryItem(newItem: MemoryItem) = withContext(Dispatchers.IO) {
        val allMemories = repository.getAllMemories()
        val newKeywords = extractKeywords(newItem.content)

        // Find existing memory with matching high-similarity keywords in the same category
        val existing = allMemories.firstOrNull { existingItem ->
            existingItem.category == newItem.category &&
                    hasSignificantOverlap(extractKeywords(existingItem.content), newKeywords)
        }

        if (existing != null) {
            // Update the existing memory with newer details and bump recency
            repository.updateMemory(existing.id, newItem.content, newItem.category)
        } else {
            // Persist as a new distinct memory item
            val itemToSave = newItem.copy(keywords = newKeywords.joinToString(" "))
            repository.saveMemory(itemToSave)
        }
    }

    private fun extractUserPreferences(userText: String, conversationId: String): MemoryItem? {
        val trimmed = userText.trim()
        val lower = trimmed.lowercase()

        val triggers = listOf(
            "i prefer ", "i like ", "my preference is ", "please always ", "always respond ",
            "my name is ", "call me ", "i am working with ", "i am developing on ", "i use "
        )

        for (trigger in triggers) {
            if (lower.contains(trigger)) {
                val startIdx = lower.indexOf(trigger)
                val statement = trimmed.substring(startIdx).take(180).trim().trimEnd('.', ',')
                if (statement.length > 8) {
                    val formatted = "User preference: $statement"
                    return MemoryItem(
                        category = MemoryCategory.USER,
                        content = formatted,
                        sourceConversationId = conversationId,
                        confidence = 0.95f
                    )
                }
            }
        }
        return null
    }

    private fun extractProjectDecisions(
        userText: String,
        assistantText: String,
        conversationId: String
    ): MemoryItem? {
        val combined = "$userText\n$assistantText"
        val lower = combined.lowercase()

        val projectTriggers = listOf(
            "project name is ", "we are building ", "the architecture is ", "tech stack is ",
            "decided to use ", "database is ", "framework is ", "the bug is ", "fixed by "
        )

        for (trigger in projectTriggers) {
            if (lower.contains(trigger)) {
                val idx = lower.indexOf(trigger)
                val snippet = combined.substring(idx).take(220).trim().lines().firstOrNull() ?: ""
                val clean = snippet.trimEnd('.', ',')
                if (clean.length > 10) {
                    return MemoryItem(
                        category = MemoryCategory.PROJECT,
                        content = "Project decision: $clean",
                        sourceConversationId = conversationId,
                        confidence = 0.90f
                    )
                }
            }
        }
        return null
    }

    private fun extractKeywords(text: String): Set<String> {
        val stopWords = setOf(
            "the", "is", "at", "which", "on", "a", "an", "and", "or", "to", "in", "for", "with",
            "user", "preference", "project", "decision", "that", "this", "from", "are", "have"
        )
        return text.lowercase()
            .split(Regex("[^a-zA-Z0-9_]+"))
            .filter { it.length > 2 && it !in stopWords }
            .toSet()
    }

    private fun hasSignificantOverlap(setA: Set<String>, setB: Set<String>): Boolean {
        if (setA.isEmpty() || setB.isEmpty()) return false
        val intersection = setA.intersect(setB)
        val minSize = minOf(setA.size, setB.size)
        return intersection.size >= 2 || (minSize <= 2 && intersection.isNotEmpty())
    }

    private fun containsSensitiveInfo(text: String): Boolean {
        return SENSITIVE_PATTERN.containsMatchIn(text)
    }
}
