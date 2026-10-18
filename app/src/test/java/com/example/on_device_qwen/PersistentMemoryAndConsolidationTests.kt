package com.example.on_device_qwen

import com.example.on_device_qwen.domain.memory.MemoryManager
import com.example.on_device_qwen.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

class PersistentMemoryAndConsolidationTests {

    private lateinit var fakeRepo: FakeMemoryRepository
    private lateinit var fakePrefs: FakeMemoryPreferences
    private lateinit var memoryManager: MemoryManager

    class FakeMemoryPreferences(
        override var persistentMemoryEnabled: Boolean = true,
        override var maxMemoryItems: Int = 5
    ) : IMemoryPreferences

    class FakeMemoryRepository : IMemoryRepository {
        val memoryStore = mutableListOf<MemoryItem>()
        val summaryStore = mutableMapOf<String, ConversationSummary>()

        override suspend fun saveMemory(item: MemoryItem): Long {
            memoryStore.add(item)
            return memoryStore.size.toLong()
        }

        override suspend fun updateMemory(id: String, newContent: String, category: MemoryCategory?): Int {
            val idx = memoryStore.indexOfFirst { it.id == id }
            if (idx != -1) {
                val current = memoryStore[idx]
                memoryStore[idx] = current.copy(
                    content = newContent,
                    category = category ?: current.category,
                    updatedAt = System.currentTimeMillis()
                )
                return 1
            }
            return 0
        }

        override suspend fun deleteMemory(id: String): Int {
            val removed = memoryStore.removeAll { it.id == id }
            return if (removed) 1 else 0
        }

        override suspend fun clearAllMemories(): Int {
            val count = memoryStore.size
            memoryStore.clear()
            return count
        }

        override suspend fun getAllMemories(): List<MemoryItem> {
            return memoryStore.toList()
        }

        override suspend fun findRelevantMemories(query: String, limit: Int): List<MemoryItem> {
            val stopWords = setOf("the", "is", "at", "which", "on", "a", "an", "and", "or", "to", "in", "for", "with", "about", "what", "how", "why", "can", "you", "please", "this", "that")
            val terms = query.lowercase()
                .split(Regex("[^a-zA-Z0-9_]+"))
                .filter { it.length > 2 && it !in stopWords }

            if (terms.isEmpty()) {
                return memoryStore.filter { it.isPinned || it.category == MemoryCategory.USER }.take(limit)
            }

            val now = System.currentTimeMillis()
            val scored = memoryStore.map { memory ->
                val contentLower = memory.content.lowercase()
                val keywordsLower = memory.keywords.lowercase()

                var score = 0.0
                for (term in terms) {
                    if (contentLower.contains(term)) score += 3.0
                    if (keywordsLower.contains(term)) score += 4.0
                }

                if (memory.isPinned) score += 2.0
                if (memory.category == MemoryCategory.USER) score += 1.0

                val ageDays = (now - memory.updatedAt).toDouble() / (1000 * 60 * 60 * 24)
                if (ageDays < 7.0) {
                    score += (7.0 - ageDays) * 0.2
                }

                memory to score
            }

            return scored.filter { it.second > 1.5 }
                .sortedByDescending { it.second }
                .take(limit)
                .map { it.first }
        }

        override suspend fun getConversationSummary(conversationId: String): ConversationSummary? {
            return summaryStore[conversationId]
        }

        override suspend fun saveConversationSummary(summary: ConversationSummary): Long {
            summaryStore[summary.conversationId] = summary
            return 1L
        }

        override suspend fun deleteConversationSummary(conversationId: String): Int {
            return if (summaryStore.remove(conversationId) != null) 1 else 0
        }
    }

    @Before
    fun setUp() {
        fakeRepo = FakeMemoryRepository()
        fakePrefs = FakeMemoryPreferences()
        memoryManager = MemoryManager(fakeRepo, fakePrefs)
    }

    @Test
    fun testCrossChatMemoryRetrieval() = runBlocking {
        // Chat A: user explains a Kotlin project architecture
        val memoryItem = MemoryItem(
            id = UUID.randomUUID().toString(),
            category = MemoryCategory.PROJECT,
            content = "Project decision: we are building an offline chat app using SQLite and LiteRT-LM",
            sourceConversationId = "chat_A",
            keywords = "offline chat app sqlite litert lm"
        )
        fakeRepo.saveMemory(memoryItem)

        // Chat B: user asks a question about the project days later
        val context = memoryManager.retrieveMemoryContext(
            query = "What database and inference engine are we using for the offline chat app?",
            conversationId = "chat_B"
        )

        assertFalse("Memory context must not be empty across different conversations", context.isEmpty)
        assertEquals(1, context.memories.size)
        assertTrue(context.memories[0].content.contains("SQLite and LiteRT-LM"))
        assertEquals("chat_A", context.memories[0].sourceConversationId)
    }

    @Test
    fun testUserPreferenceExtraction() = runBlocking {
        memoryManager.extractAndConsolidate(
            conversationId = "chat_user_pref",
            userText = "I prefer concise code without semicolons and please always use Kotlin syntax.",
            assistantText = "Understood! I will use idiomatic Kotlin without semicolons."
        )

        val memories = fakeRepo.getAllMemories()
        assertEquals("Should extract 1 user preference memory", 1, memories.size)
        assertEquals(MemoryCategory.USER, memories[0].category)
        assertTrue(memories[0].content.contains("concise code without semicolons"))
    }

    @Test
    fun testProjectDecisionExtraction() = runBlocking {
        memoryManager.extractAndConsolidate(
            conversationId = "chat_proj",
            userText = "What stack should we use?",
            assistantText = "We decided to use Room database with SQLite for persistence and Jetpack Compose for UI."
        )

        val memories = fakeRepo.getAllMemories()
        assertEquals(1, memories.size)
        assertEquals(MemoryCategory.PROJECT, memories[0].category)
        assertTrue(memories[0].content.contains("decided to use Room database"))
    }

    @Test
    fun testSensitiveInformationSanitizationPreventsStorage() = runBlocking {
        // Attempt to extract turn containing an API key or password
        memoryManager.extractAndConsolidate(
            conversationId = "chat_sec",
            userText = "My secret password is Password123! and my token is ghp_99887766554433221100",
            assistantText = "I will keep that in mind."
        )

        val memories = fakeRepo.getAllMemories()
        assertTrue("Memories containing passwords or API tokens must NEVER be stored", memories.isEmpty())
    }

    @Test
    fun testDeduplicationAndFactUpdates() = runBlocking {
        // Initial project decision
        val initialMemory = MemoryItem(
            id = "mem_1",
            category = MemoryCategory.PROJECT,
            content = "Project decision: database is SQLite with standard tables",
            keywords = "project decision database sqlite standard tables"
        )
        fakeRepo.saveMemory(initialMemory)

        // Updated fact on the same topic
        val updatedMemory = MemoryItem(
            id = "mem_2",
            category = MemoryCategory.PROJECT,
            content = "Project decision: database is SQLite with FTS5 and vector extension support",
            keywords = "project decision database sqlite fts5 vector extension"
        )
        memoryManager.consolidateMemoryItem(updatedMemory)

        val all = fakeRepo.getAllMemories()
        assertEquals("Should deduplicate and update existing item instead of creating duplicates", 1, all.size)
        assertEquals("mem_1", all[0].id)
        assertTrue("Memory content should be updated with new details", all[0].content.contains("vector extension"))
    }

    @Test
    fun testDisabledMemoryReturnsEmptyContext() = runBlocking {
        fakePrefs.persistentMemoryEnabled = false

        val item = MemoryItem(
            category = MemoryCategory.USER,
            content = "User preference: I prefer dark theme",
            keywords = "dark theme"
        )
        fakeRepo.saveMemory(item)

        val context = memoryManager.retrieveMemoryContext(
            query = "What theme do I like?",
            conversationId = "chat_test"
        )

        assertTrue("When persistent memory is disabled, context must be empty", context.isEmpty)
        assertEquals(0, context.memories.size)

        // Also verify extraction does nothing when disabled
        memoryManager.extractAndConsolidate(
            conversationId = "chat_test",
            userText = "I like dark mode",
            assistantText = "Noted!"
        )
        assertEquals("No new memory should be extracted when disabled", 1, fakeRepo.getAllMemories().size)
    }

    @Test
    fun testIrrelevantQueryDoesNotRetrieveUnrelatedMemories() = runBlocking {
        fakeRepo.saveMemory(MemoryItem(
            category = MemoryCategory.PROJECT,
            content = "Project decision: backend framework is Spring Boot",
            keywords = "spring boot backend framework"
        ))

        // Query completely unrelated to Spring Boot
        val context = memoryManager.retrieveMemoryContext(
            query = "What is the recipe for chocolate chip cookies?",
            conversationId = "chat_cookies"
        )

        assertTrue("Unrelated memories must not be retrieved for unrelated queries", context.memories.isEmpty())
    }
}
