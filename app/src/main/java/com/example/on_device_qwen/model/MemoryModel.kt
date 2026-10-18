package com.example.on_device_qwen.model

import java.util.UUID

/**
 * Distinct classification categories for persistent cross-chat memory.
 */
enum class MemoryCategory(val displayName: String, val promptHeader: String) {
    USER("User Preference", "User Profile & Preferences"),
    PROJECT("Project Memory", "Active Projects & Technical Decisions"),
    EPISODIC("Past Discussions", "Relevant Past Discussions & Completed Tasks")
}

/**
 * Represents a single persistent cross-chat memory unit.
 */
data class MemoryItem(
    val id: String = UUID.randomUUID().toString(),
    val category: MemoryCategory,
    val content: String,
    val sourceConversationId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val confidence: Float = 1.0f,
    val keywords: String = "",
    val isPinned: Boolean = false
)

/**
 * Represents a summarized representation of older conversation turns.
 */
data class ConversationSummary(
    val conversationId: String,
    val summaryText: String,
    val lastMessageTimestamp: Long,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Structured container for memories retrieved for prompt generation.
 */
data class RetrievedMemoryContext(
    val memories: List<MemoryItem> = emptyList(),
    val summary: ConversationSummary? = null,
    val tokenEstimate: Int = 0
) {
    val isEmpty: Boolean get() = memories.isEmpty() && summary == null
}

/**
 * Interface contract for persistent memory storage and retrieval.
 */
interface IMemoryRepository {
    suspend fun saveMemory(item: MemoryItem): Long
    suspend fun updateMemory(id: String, newContent: String, category: MemoryCategory? = null): Int
    suspend fun deleteMemory(id: String): Int
    suspend fun clearAllMemories(): Int
    suspend fun getAllMemories(): List<MemoryItem>
    suspend fun findRelevantMemories(query: String, limit: Int = 5): List<MemoryItem>
    suspend fun getConversationSummary(conversationId: String): ConversationSummary?
    suspend fun saveConversationSummary(summary: ConversationSummary): Long
    suspend fun deleteConversationSummary(conversationId: String): Int
}

/**
 * Interface contract for memory preferences.
 */
interface IMemoryPreferences {
    val persistentMemoryEnabled: Boolean
    val maxMemoryItems: Int
}
