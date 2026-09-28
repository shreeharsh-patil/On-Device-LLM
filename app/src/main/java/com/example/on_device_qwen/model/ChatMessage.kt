package com.example.on_device_qwen.model

/**
 * Benchmarking metrics captured for LLM generation.
 */
data class BenchmarkMetrics(
    val ttftMs: Long,
    val decodeTps: Double,
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
    val text: String,
    val isUser: Boolean,
    val isStreaming: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val benchmark: BenchmarkMetrics? = null,
    val imagePath: String? = null,
)
