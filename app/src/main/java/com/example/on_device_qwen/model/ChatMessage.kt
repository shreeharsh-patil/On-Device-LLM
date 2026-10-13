package com.example.on_device_qwen.model

/**
 * Benchmarking metrics captured for LLM generation.
 */
data class BenchmarkMetrics(
    val ttftMs: Long = 0,
    val decodeTps: Double = 0.0,
    val prefillTps: Double? = null,
    val prefillTokens: Int? = null,
    val decodeTokens: Int = 0,
    val totalDurationMs: Long = 0,
    val backend: String = "CPU",
)

/**
 * Represents a single message in the chat conversation.
 */
data class ChatMessage(
    val id: Long = System.nanoTime(),
    val conversationId: String = "",
    val text: String,
    val isUser: Boolean,
    val isStreaming: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val benchmark: BenchmarkMetrics? = null,
    val imagePath: String? = null,
    val thinkText: String? = null,
    val isError: Boolean = false,
)

/**
 * Represents a conversation thread in persistent storage.
 */
data class ConversationSession(
    val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val modelName: String = "",
    val isPinned: Boolean = false,
    val lastMessagePreview: String = "",
)
