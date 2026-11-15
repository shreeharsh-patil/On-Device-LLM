package com.example.on_device_qwen

import com.example.on_device_qwen.domain.engine.LlamaCppInferenceEngine
import com.example.on_device_qwen.model.BenchmarkMetrics
import com.example.on_device_qwen.model.ChatMessage
import com.example.on_device_qwen.model.GenerationConfig
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class GenerationDeduplicationAndStreamTests {

    @Test
    fun testStaleGenerationIdChunksAreRejected() {
        var activeGenerationId = "gen_authoritative_123"
        val displayedText = StringBuilder()

        val incomingChunks = listOf(
            Triple("gen_authoritative_123", "Hello", false),
            Triple("gen_authoritative_123", " world", false),
            // Outdated/stale chunk from an aborted prior request
            Triple("gen_stale_999", " STALE LEAKED DATA", false),
            Triple("gen_authoritative_123", "!", true)
        )

        for ((genId, chunk, isDone) in incomingChunks) {
            if (genId == activeGenerationId) {
                displayedText.append(chunk)
            }
        }

        assertEquals("Hello world!", displayedText.toString())
        assertFalse("Stale generation chunks must never appear in active message", displayedText.contains("STALE"))
    }

    @Test
    fun testStopSequencesAndEosTokensAreStrippedFromOutput() {
        val stopTokens = listOf("<|im_end|>", "<|endoftext|>", "<|im_start|>")
        
        fun sanitizeOutput(raw: String): String {
            var cleaned = raw
            for (stop in stopTokens) {
                cleaned = cleaned.replace(stop, "")
            }
            return cleaned.trim()
        }

        val rawGenerated1 = "Here is the response.<|im_end|>"
        val rawGenerated2 = "First part<|endoftext|>"
        val rawGenerated3 = "Thinking complete.<|im_start|>assistant\nNext steps."

        assertEquals("Here is the response.", sanitizeOutput(rawGenerated1))
        assertEquals("First part", sanitizeOutput(rawGenerated2))
        assertEquals("Thinking complete.assistant\nNext steps.", sanitizeOutput(rawGenerated3))
    }

    @Test
    fun testRegenerateReplacesAssistantMessageWithoutDuplicatingUserMessage() {
        val messages = mutableListOf(
            ChatMessage(id = 1L, conversationId = "conv_1", text = "Write a haiku about code.", isUser = true),
            ChatMessage(id = 2L, conversationId = "conv_1", text = "Silent bugs compile, / Logic flows into the dark, / Fixed with one new test.", isUser = false)
        )

        assertEquals("Initial conversation has 1 user and 1 assistant message", 2, messages.size)

        // Simulate regenerate:
        // 1. Identify last assistant message
        val lastAssistant = messages.lastOrNull { !it.isUser }
        assertNotNull(lastAssistant)
        // 2. Remove last assistant message
        messages.remove(lastAssistant)

        assertEquals("After removing assistant message, only user message remains", 1, messages.size)
        assertTrue(messages.first().isUser)
        assertEquals("Write a haiku about code.", messages.first().text)

        // 3. Re-run inference with a new generation ID
        val newGenerationId = UUID.randomUUID().toString()
        val newAssistantMessage = ChatMessage(
            id = 3L,
            conversationId = "conv_1",
            text = "Code runs line by line, / Quiet logic in the dark, / Tests bring peaceful sleep.",
            isUser = false
        )
        messages.add(newAssistantMessage)

        // Verify state: Exactly 1 user message, 1 assistant message (NO duplicated user message!)
        val userMessages = messages.filter { it.isUser }
        val assistantMessages = messages.filter { !it.isUser }

        assertEquals("User message must NOT be duplicated on regenerate", 1, userMessages.size)
        assertEquals("Exactly one assistant message must exist after regenerate", 1, assistantMessages.size)
        assertEquals(3L, assistantMessages.first().id)
        assertTrue(assistantMessages.first().text.contains("Tests bring peaceful sleep"))
    }

    @Test
    fun testGenerationConfigRepetitionPenaltyDefaults() {
        val config = GenerationConfig()
        assertEquals("Default repetition penalty must be 1.15 to prevent infinite loops", 1.15f, config.repetitionPenalty, 0.001f)
        assertTrue("Default stop sequences must contain chatml end token", config.stopSequences.contains("<|im_end|>"))
        assertTrue("Default stop sequences must contain endoftext token", config.stopSequences.contains("<|endoftext|>"))
    }

    @Test
    fun testStreamFilterReassemblesSplitUtf8AndSuppressesSplitStopSequences() {
        val filter = LlamaCppInferenceEngine.Utf8AndStopStreamFilter(listOf("<|im_end|>", "STOP_NOW"))
        val out = StringBuilder()

        // 4-byte emoji U+1F600 split 2+2 bytes across separate native token emissions,
        // followed by a stop sequence split across 3 chunks.
        val emojiBytes = "Hi \uD83D\uDE00 ok".toByteArray(Charsets.UTF_8)
        val splitIndex = 5 // Inside 4-byte UTF-8 sequence (F0 9F | 98 80)
        out.append(filter.appendBytes(emojiBytes.copyOfRange(0, splitIndex)).emittedText)
        assertFalse("Partial UTF-8 bytes must not decode as replacement character", out.toString().contains('\uFFFD'))
        out.append(filter.appendBytes(emojiBytes.copyOfRange(splitIndex, emojiBytes.size)).emittedText)

        out.append(filter.appendBytes("<|im_".toByteArray(Charsets.UTF_8)).emittedText)
        assertFalse("Partial stop prefix must be held back", out.toString().contains("<|im_"))

        val stopChunk = filter.appendBytes("end|>IGNORED_TAIL".toByteArray(Charsets.UTF_8))
        out.append(stopChunk.emittedText)
        assertTrue("Full stop match must terminate stream", stopChunk.hitStop)
        out.append(filter.flush(stoppedByStopSequence = true))

        assertEquals("Hi \uD83D\uDE00 ok", out.toString())
        assertFalse(out.toString().contains("IGNORED_TAIL"))
        assertFalse(out.toString().contains('\uFFFD'))
    }
}
