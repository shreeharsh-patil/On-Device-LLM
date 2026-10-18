package com.example.on_device_qwen

import com.example.on_device_qwen.domain.context.PromptContextBuilder
import com.example.on_device_qwen.model.*
import org.junit.Assert.*
import org.junit.Test

class ConversationContextAndPromptTests {

    @Test
    fun testFollowUpQuestionIncludesPriorHistory() {
        val history = listOf(
            ChatMessage(id = 1L, conversationId = "chat_1", text = "I am designing a cache with LRU eviction.", isUser = true),
            ChatMessage(id = 2L, conversationId = "chat_1", text = "LRU eviction removes the least recently used item first.", isUser = false)
        )

        val prompt = PromptContextBuilder.buildChatMl(
            historyMessages = history,
            latestUserPrompt = "Can you show the Kotlin implementation for it?",
            memoryContext = RetrievedMemoryContext(),
            config = GenerationConfig(contextLength = 4096, maxOutputTokens = 1024)
        )

        assertTrue("Prompt must contain earlier user query", prompt.contains("I am designing a cache with LRU eviction."))
        assertTrue("Prompt must contain earlier assistant response", prompt.contains("LRU eviction removes the least recently used item first."))
        assertTrue("Prompt must contain follow-up query", prompt.contains("Can you show the Kotlin implementation for it?"))
        assertTrue("Prompt must follow ChatML role format", prompt.contains("<|im_start|>user\nCan you show the Kotlin implementation for it?<|im_end|>"))
        assertTrue("Prompt must terminate with assistant generation marker", prompt.endsWith("<|im_start|>assistant\n"))
    }

    @Test
    fun testNewConversationDoesNotInheritUnrelatedHistory() {
        // Chat A history
        val chatAHistory = listOf(
            ChatMessage(id = 1L, conversationId = "chat_A", text = "My favorite color is emerald green.", isUser = true),
            ChatMessage(id = 2L, conversationId = "chat_A", text = "Noted! Emerald green is a great color.", isUser = false)
        )

        // Chat B starts with empty history
        val chatBHistory = emptyList<ChatMessage>()

        val promptB = PromptContextBuilder.buildChatMl(
            historyMessages = chatBHistory,
            latestUserPrompt = "What is the capital of France?",
            memoryContext = RetrievedMemoryContext(),
            config = GenerationConfig(contextLength = 4096, maxOutputTokens = 1024)
        )

        assertFalse("Chat B must NOT contain Chat A's private messages", promptB.contains("emerald green"))
        assertTrue("Chat B must contain only its own prompt", promptB.contains("What is the capital of France?"))
    }

    @Test
    fun testContextWindowBudgetingTruncatesOlderMessagesSafely() {
        // Generate a very long conversation that exceeds a small context budget
        val history = mutableListOf<ChatMessage>()
        for (i in 1..25) {
            history.add(ChatMessage(id = (i * 2 - 1).toLong(), conversationId = "chat_long", text = "Question $i: " + "long text ".repeat(30), isUser = true))
            history.add(ChatMessage(id = (i * 2).toLong(), conversationId = "chat_long", text = "Answer $i: " + "detailed explanation ".repeat(30), isUser = false))
        }

        val config = GenerationConfig(contextLength = 1024, maxOutputTokens = 256)
        val prompt = PromptContextBuilder.buildChatMl(
            historyMessages = history,
            latestUserPrompt = "Final question to answer",
            memoryContext = RetrievedMemoryContext(),
            config = config
        )

        // Mandatory invariant: The latest user prompt MUST ALWAYS be present!
        assertTrue("Latest user prompt must never be discarded", prompt.contains("Final question to answer"))
        assertTrue("System instruction must be present", prompt.contains("<|im_start|>system"))

        // Older questions should have been truncated to prevent context overflow
        assertFalse("Oldest question 1 should be truncated due to budget limit", prompt.contains("Question 1:"))
        // Recent questions should still be present
        assertTrue("Recent question 25 should be included", prompt.contains("Question 25:"))
    }

    @Test
    fun testSummaryIsInjectedWhenContextTruncated() {
        val summary = ConversationSummary(
            conversationId = "chat_1",
            summaryText = "User previously chose Kotlin Coroutines over RxJava for concurrency.",
            lastMessageTimestamp = System.currentTimeMillis() - 10000
        )
        val memoryContext = RetrievedMemoryContext(summary = summary)

        val prompt = PromptContextBuilder.buildChatMl(
            historyMessages = emptyList(),
            latestUserPrompt = "What library did we pick for async tasks?",
            memoryContext = memoryContext,
            config = GenerationConfig(contextLength = 2048, maxOutputTokens = 512)
        )

        assertTrue("Summary must be included in prompt context", prompt.contains("Prior Conversation Summary: User previously chose Kotlin Coroutines over RxJava"))
        assertTrue("Current query must be present", prompt.contains("What library did we pick for async tasks?"))
    }

    @Test
    fun testStreamingPlaceholderMessagesAreExcludedFromPrompt() {
        val history = listOf(
            ChatMessage(id = 1L, conversationId = "c1", text = "Hello", isUser = true),
            ChatMessage(id = 2L, conversationId = "c1", text = "Hi there!", isUser = false),
            // Unfinished or empty streaming message
            ChatMessage(id = 3L, conversationId = "c1", text = "", isUser = false, isStreaming = true)
        )

        val prompt = PromptContextBuilder.buildChatMl(
            historyMessages = history,
            latestUserPrompt = "How are you?",
            memoryContext = RetrievedMemoryContext(),
            config = GenerationConfig()
        )

        // Ensure no empty assistant tags are present before latest user prompt
        assertFalse(prompt.contains("<|im_start|>assistant\n<|im_end|>"))
    }
}
