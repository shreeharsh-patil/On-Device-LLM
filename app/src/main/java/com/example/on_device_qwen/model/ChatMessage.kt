package com.example.on_device_qwen.model

import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe, strictly monotonic positive ID generator for persisted chat messages.
 * Unlike raw [System.nanoTime], generated IDs advance monotonically across process
 * restarts and device reboots, preventing SQLite PRIMARY KEY collisions.
 */
object MessageIdGenerator {
    private const val SEQUENCE_BITS = 20
    private const val MILLIS_MASK = 0x3FFFFFFFFFFL
    private val lastIssuedId = AtomicLong(0L)

    fun nextId(): Long {
        val base = (System.currentTimeMillis().coerceAtLeast(1L) and MILLIS_MASK) shl SEQUENCE_BITS
        return lastIssuedId.updateAndGet { prev ->
            if (base > prev) base else prev + 1L
        }
    }
}

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
    val id: Long = MessageIdGenerator.nextId(),
    val conversationId: String = "",
    val text: String,
    val isUser: Boolean,
    val isStreaming: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val benchmark: BenchmarkMetrics? = null,
    val imagePath: String? = null,
    val thinkText: String? = null,
    val isError: Boolean = false,
    val modelName: String? = null,
    val backendLabel: String? = null,
    val isCancelled: Boolean = false,
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
