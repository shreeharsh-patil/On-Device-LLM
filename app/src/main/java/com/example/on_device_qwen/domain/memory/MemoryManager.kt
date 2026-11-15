package com.example.on_device_qwen.domain.memory

import com.example.on_device_qwen.model.ConversationSummary
import com.example.on_device_qwen.model.IMemoryPreferences
import com.example.on_device_qwen.model.IMemoryRepository
import com.example.on_device_qwen.model.MemoryCategory
import com.example.on_device_qwen.model.MemoryItem
import com.example.on_device_qwen.model.RetrievedMemoryContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
            listOf(
                // Credentials & common API secrets
                "(?i)\\b(?:password|passwd|secret|apikey|api_key|token)\\b",
                "(?i)\\bbearer\\s+[A-Za-z0-9\\-._~+/]+=*",
                "\\b(?:ghp|gho)_[A-Za-z0-9]+",
                "\\bgithub_pat_[A-Za-z0-9_]+",
                "\\bsk-[A-Za-z0-9_\\-]{8,}",
                "\\bAIza[0-9A-Za-z_\\-]{20,}",
                "\\bAKIA[0-9A-Z]{16}\\b",
                // JWTs
                "\\beyJ[A-Za-z0-9_\\-]{5,}\\.[A-Za-z0-9_\\-]{5,}\\.[A-Za-z0-9_\\-]{5,}\\b",
                // PEM private keys
                "-----BEGIN\\s+(?:[A-Z0-9]+\\s+)*PRIVATE KEY-----",
                // Emails
                "[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}",
                // US SSNs
                "\\b\\d{3}-\\d{2}-\\d{4}\\b",
                // Credit card patterns (16-digit grouped or 13-16 digit Visa/MC/Amex)
                "(?<!\\d)(?:\\d{4}[-\\s]?){3}\\d{4}(?!\\d)",
                "\\b(?:3[47]\\d{13}|4\\d{12}(?:\\d{3})?|5[1-5]\\d{14})\\b",
                // Phone numbers (US formatted & international +country formats)
                "(?<!\\d)(?:\\+\\d{1,3}[\\s.\\-]?)?(?:\\(\\d{3}\\)\\s*|\\d{3}[\\s.\\-])\\d{3}[\\s.\\-]\\d{4}(?!\\d)",
                "(?<!\\d)\\+\\d{1,3}[\\s.\\-]?\\d{7,12}(?!\\d)"
            ).joinToString("|")
        )

        fun containsSensitiveInfo(text: String): Boolean = SENSITIVE_PATTERN.containsMatchIn(text)

        fun redactSensitiveInfo(text: String): String = SENSITIVE_PATTERN.replace(text, "[REDACTED]")

        fun sanitizeContent(content: String): String =
            MemoryItem(category = MemoryCategory.EPISODIC, content = content).sanitizedContent()
    }

    /**
     * Retrieve relevant user, project, and episodic memories for the incoming query,
     * respecting the user's memory enablement preference and [maxTokens] budget without
     * truncating any individual entry mid-string.
     */
    suspend fun retrieveMemoryContext(
        query: String,
        conversationId: String,
        maxTokens: Int = 512
    ): RetrievedMemoryContext = withContext(Dispatchers.IO) {
        if (!prefs.persistentMemoryEnabled || maxTokens <= 0) {
            return@withContext RetrievedMemoryContext()
        }

        val limit = prefs.maxMemoryItems.coerceAtLeast(0)
        val candidates = if (limit > 0) {
            repository.findRelevantMemories(query, limit = limit)
        } else {
            emptyList()
        }
        val rawSummary = repository.getConversationSummary(conversationId)

        val includedMemories = mutableListOf<MemoryItem>()
        var usedTokens = 0

        for (memory in candidates) {
            val cleanText = memory.sanitizedContent()
            if (cleanText.isBlank() || containsSensitiveInfo(cleanText)) continue
            val entryTokens = estimateTokens(cleanText)
            if (usedTokens + entryTokens <= maxTokens) {
                includedMemories.add(memory.copy(content = cleanText))
                usedTokens += entryTokens
            }
        }

        val includedSummary: ConversationSummary? = rawSummary?.let { sum ->
            val cleanSummary = sanitizeContent(sum.summaryText)
            if (cleanSummary.isBlank() || containsSensitiveInfo(cleanSummary)) {
                null
            } else {
                val summaryTokens = estimateTokens(cleanSummary)
                if (usedTokens + summaryTokens <= maxTokens) {
                    usedTokens += summaryTokens
                    sum.copy(summaryText = cleanSummary)
                } else {
                    null
                }
            }
        }

        RetrievedMemoryContext(
            memories = includedMemories,
            summary = includedSummary,
            tokenEstimate = usedTokens
        )
    }

    /**
     * Safely validates and saves a memory item, blocking PII/secrets and stripping template tokens.
     */
    suspend fun saveMemory(item: MemoryItem): Long = withContext(Dispatchers.IO) {
        val cleanContent = item.sanitizedContent()
        if (cleanContent.isBlank() || containsSensitiveInfo(cleanContent)) {
            return@withContext -1L
        }
        val keywords = item.keywords.ifBlank { extractKeywords(cleanContent).joinToString(" ") }
        repository.saveMemory(item.copy(content = cleanContent, keywords = keywords))
    }

    /**
     * Safely validates and updates an existing memory item, syncing keywords and blocking PII/secrets.
     */
    suspend fun updateMemory(
        id: String,
        newContent: String,
        category: MemoryCategory? = null
    ): Int = withContext(Dispatchers.IO) {
        val cleanContent = sanitizeContent(newContent)
        if (cleanContent.isBlank() || containsSensitiveInfo(cleanContent)) {
            return@withContext 0
        }
        val updatedKeywords = extractKeywords(cleanContent).joinToString(" ")
        repository.updateMemoryWithKeywords(id, cleanContent, updatedKeywords, category)
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

        val cleanUser = sanitizeContent(userText)
        val cleanAssistant = sanitizeContent(assistantText)
        val extracted = mutableListOf<MemoryItem>()

        // 1. User Preference & Persona Extraction
        extractUserPreferences(cleanUser, conversationId)?.let { extracted.add(it) }

        // 2. Project Architecture & Technical Decisions
        extractProjectDecisions(cleanUser, cleanAssistant, conversationId)?.let { extracted.add(it) }

        // 3. Persist and deduplicate
        for (item in extracted) {
            consolidateMemoryItem(item)
        }
    }

    /**
     * Deduplicates and consolidates newly extracted memories into the repository.
     * Updates existing memories (and their keywords) if they pertain to the same entity.
     */
    suspend fun consolidateMemoryItem(newItem: MemoryItem) = withContext(Dispatchers.IO) {
        val cleanContent = newItem.sanitizedContent()
        if (cleanContent.isBlank() || containsSensitiveInfo(cleanContent)) return@withContext

        val allMemories = repository.getAllMemories()
        val newKeywords = extractKeywords(cleanContent)
        val keywordsString = newKeywords.joinToString(" ")

        // Find existing memory with matching high-similarity keywords in the same category
        val existing = allMemories.firstOrNull { existingItem ->
            existingItem.category == newItem.category &&
                    hasSignificantOverlap(extractKeywords(existingItem.content), newKeywords)
        }

        if (existing != null) {
            // Update existing memory content and keywords so relevance scoring stays accurate
            repository.updateMemoryWithKeywords(existing.id, cleanContent, keywordsString, newItem.category)
        } else {
            // Persist as a new distinct memory item
            val itemToSave = newItem.copy(content = cleanContent, keywords = keywordsString)
            repository.saveMemory(itemToSave)
        }
    }

    private fun estimateTokens(text: String): Int {
        return maxOf(1, (text.length + 3) / 4)
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
}
