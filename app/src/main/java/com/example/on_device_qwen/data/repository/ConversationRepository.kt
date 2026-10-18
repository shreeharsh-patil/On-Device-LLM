package com.example.on_device_qwen.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.example.on_device_qwen.data.db.ChatDatabaseHelper
import com.example.on_device_qwen.model.BenchmarkMetrics
import com.example.on_device_qwen.model.ChatMessage
import com.example.on_device_qwen.model.ConversationSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ConversationRepository(context: Context) : com.example.on_device_qwen.model.IMemoryRepository {

    private val dbHelper = ChatDatabaseHelper(context.applicationContext)

    suspend fun getAllConversations(): List<ConversationSession> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ConversationSession>()
        val db = dbHelper.readableDatabase
        val cursor: Cursor = db.query(
            ChatDatabaseHelper.TABLE_CONVERSATIONS,
            null,
            null,
            null,
            null,
            null,
            "${ChatDatabaseHelper.COL_CONV_PINNED} DESC, ${ChatDatabaseHelper.COL_CONV_UPDATED_AT} DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToConversation(it))
            }
        }
        list
    }

    suspend fun getConversation(id: String): ConversationSession? = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            ChatDatabaseHelper.TABLE_CONVERSATIONS,
            null,
            "${ChatDatabaseHelper.COL_CONV_ID} = ?",
            arrayOf(id),
            null,
            null,
            null
        )
        cursor.use {
            if (it.moveToFirst()) cursorToConversation(it) else null
        }
    }

    suspend fun saveConversation(session: ConversationSession) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(ChatDatabaseHelper.COL_CONV_ID, session.id)
            put(ChatDatabaseHelper.COL_CONV_TITLE, session.title)
            put(ChatDatabaseHelper.COL_CONV_CREATED_AT, session.createdAt)
            put(ChatDatabaseHelper.COL_CONV_UPDATED_AT, session.updatedAt)
            put(ChatDatabaseHelper.COL_CONV_MODEL_NAME, session.modelName)
            put(ChatDatabaseHelper.COL_CONV_PINNED, if (session.isPinned) 1 else 0)
            put(ChatDatabaseHelper.COL_CONV_PREVIEW, session.lastMessagePreview)
        }
        db.insertWithOnConflict(
            ChatDatabaseHelper.TABLE_CONVERSATIONS,
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    suspend fun updateConversationTitle(id: String, newTitle: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(ChatDatabaseHelper.COL_CONV_TITLE, newTitle)
            put(ChatDatabaseHelper.COL_CONV_UPDATED_AT, System.currentTimeMillis())
        }
        db.update(
            ChatDatabaseHelper.TABLE_CONVERSATIONS,
            values,
            "${ChatDatabaseHelper.COL_CONV_ID} = ?",
            arrayOf(id)
        )
    }

    suspend fun togglePinConversation(id: String, isPinned: Boolean) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(ChatDatabaseHelper.COL_CONV_PINNED, if (isPinned) 1 else 0)
        }
        db.update(
            ChatDatabaseHelper.TABLE_CONVERSATIONS,
            values,
            "${ChatDatabaseHelper.COL_CONV_ID} = ?",
            arrayOf(id)
        )
    }

    suspend fun deleteConversation(id: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        // BUG-005 FIX: Use a transaction so both deletes are atomic.
        // FK CASCADE (ON DELETE CASCADE) handles messages automatically when enabled,
        // but we keep the explicit delete as a belt-and-suspenders safety measure.
        db.beginTransaction()
        try {
            db.delete(
                ChatDatabaseHelper.TABLE_MESSAGES,
                "${ChatDatabaseHelper.COL_MSG_CONV_ID} = ?",
                arrayOf(id)
            )
            db.delete(
                ChatDatabaseHelper.TABLE_SUMMARIES,
                "${ChatDatabaseHelper.COL_SUM_CONV_ID} = ?",
                arrayOf(id)
            )
            db.delete(
                ChatDatabaseHelper.TABLE_CONVERSATIONS,
                "${ChatDatabaseHelper.COL_CONV_ID} = ?",
                arrayOf(id)
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun clearAllConversations() = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(ChatDatabaseHelper.TABLE_MESSAGES, null, null)
        db.delete(ChatDatabaseHelper.TABLE_CONVERSATIONS, null, null)
    }

    suspend fun searchConversations(query: String): List<ConversationSession> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext getAllConversations()
        val list = mutableListOf<ConversationSession>()
        val db = dbHelper.readableDatabase
        // BUG-014 FIX: Escape SQL LIKE wildcard chars so user search for "50% off"
        // does not accidentally match every conversation.
        val escapedQuery = query.trim()
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        val pattern = "%$escapedQuery%"
        val cursor = db.rawQuery(
            """
            SELECT DISTINCT c.* FROM ${ChatDatabaseHelper.TABLE_CONVERSATIONS} c
            LEFT JOIN ${ChatDatabaseHelper.TABLE_MESSAGES} m ON c.${ChatDatabaseHelper.COL_CONV_ID} = m.${ChatDatabaseHelper.COL_MSG_CONV_ID}
            WHERE c.${ChatDatabaseHelper.COL_CONV_TITLE} LIKE ? ESCAPE '\' OR m.${ChatDatabaseHelper.COL_MSG_TEXT} LIKE ? ESCAPE '\'
            ORDER BY c.${ChatDatabaseHelper.COL_CONV_UPDATED_AT} DESC
            """.trimIndent(),
            arrayOf(pattern, pattern)
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToConversation(it))
            }
        }
        list
    }

    suspend fun getMessages(conversationId: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ChatMessage>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            ChatDatabaseHelper.TABLE_MESSAGES,
            null,
            "${ChatDatabaseHelper.COL_MSG_CONV_ID} = ?",
            arrayOf(conversationId),
            null,
            null,
            "${ChatDatabaseHelper.COL_MSG_TIMESTAMP} ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToMessage(it))
            }
        }
        list
    }

    suspend fun saveMessage(message: ChatMessage) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(ChatDatabaseHelper.COL_MSG_ID, message.id)
            put(ChatDatabaseHelper.COL_MSG_CONV_ID, message.conversationId)
            put(ChatDatabaseHelper.COL_MSG_TEXT, message.text)
            put(ChatDatabaseHelper.COL_MSG_IS_USER, if (message.isUser) 1 else 0)
            put(ChatDatabaseHelper.COL_MSG_TIMESTAMP, message.timestamp)
            put(ChatDatabaseHelper.COL_MSG_IMAGE_PATH, message.imagePath)
            put(ChatDatabaseHelper.COL_MSG_THINK_TEXT, message.thinkText)
            put(ChatDatabaseHelper.COL_MSG_IS_ERROR, if (message.isError) 1 else 0)
            message.benchmark?.let { b ->
                put(ChatDatabaseHelper.COL_MSG_TTFT_MS, b.ttftMs)
                put(ChatDatabaseHelper.COL_MSG_DECODE_TPS, b.decodeTps)
                put(ChatDatabaseHelper.COL_MSG_DECODE_TOKENS, b.decodeTokens)
                put(ChatDatabaseHelper.COL_MSG_DURATION_MS, b.totalDurationMs)
                put(ChatDatabaseHelper.COL_MSG_BACKEND, b.backend)
            }
        }
        db.insertWithOnConflict(
            ChatDatabaseHelper.TABLE_MESSAGES,
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )

        // Update preview and updatedAt on parent conversation
        val convValues = ContentValues().apply {
            put(ChatDatabaseHelper.COL_CONV_UPDATED_AT, message.timestamp)
            put(ChatDatabaseHelper.COL_CONV_PREVIEW, message.text.take(80))
        }
        db.update(
            ChatDatabaseHelper.TABLE_CONVERSATIONS,
            convValues,
            "${ChatDatabaseHelper.COL_CONV_ID} = ?",
            arrayOf(message.conversationId)
        )
    }

    suspend fun deleteMessage(messageId: Long) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(
            ChatDatabaseHelper.TABLE_MESSAGES,
            "${ChatDatabaseHelper.COL_MSG_ID} = ?",
            arrayOf(messageId.toString())
        )
    }

    suspend fun deleteLastAssistantMessage(conversationId: String): Long? = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val cursor = db.rawQuery(
            """
            SELECT ${ChatDatabaseHelper.COL_MSG_ID} FROM ${ChatDatabaseHelper.TABLE_MESSAGES}
            WHERE ${ChatDatabaseHelper.COL_MSG_CONV_ID} = ? AND ${ChatDatabaseHelper.COL_MSG_IS_USER} = 0
            ORDER BY ${ChatDatabaseHelper.COL_MSG_TIMESTAMP} DESC LIMIT 1
            """.trimIndent(),
            arrayOf(conversationId)
        )
        var lastId: Long? = null
        cursor.use {
            if (it.moveToFirst()) {
                lastId = it.getLong(0)
            }
        }
        lastId?.let { id ->
            db.delete(
                ChatDatabaseHelper.TABLE_MESSAGES,
                "${ChatDatabaseHelper.COL_MSG_ID} = ?",
                arrayOf(id.toString())
            )
        }
        lastId
    }

    suspend fun deleteMessagesAfter(conversationId: String, timestamp: Long) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(
            ChatDatabaseHelper.TABLE_MESSAGES,
            "${ChatDatabaseHelper.COL_MSG_CONV_ID} = ? AND ${ChatDatabaseHelper.COL_MSG_TIMESTAMP} >= ?",
            arrayOf(conversationId, timestamp.toString())
        )
    }

    // ── Persistent Cross-Chat Memory ──────────────────────────────────────────

    override suspend fun saveMemory(item: com.example.on_device_qwen.model.MemoryItem) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(ChatDatabaseHelper.COL_MEM_ID, item.id)
            put(ChatDatabaseHelper.COL_MEM_CATEGORY, item.category.name)
            put(ChatDatabaseHelper.COL_MEM_CONTENT, item.content)
            put(ChatDatabaseHelper.COL_MEM_SOURCE_CONV_ID, item.sourceConversationId)
            put(ChatDatabaseHelper.COL_MEM_CREATED_AT, item.createdAt)
            put(ChatDatabaseHelper.COL_MEM_UPDATED_AT, item.updatedAt)
            put(ChatDatabaseHelper.COL_MEM_CONFIDENCE, item.confidence)
            put(ChatDatabaseHelper.COL_MEM_KEYWORDS, item.keywords)
            put(ChatDatabaseHelper.COL_MEM_PINNED, if (item.isPinned) 1 else 0)
        }
        db.insertWithOnConflict(
            ChatDatabaseHelper.TABLE_MEMORIES,
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    override suspend fun updateMemory(id: String, newContent: String, category: com.example.on_device_qwen.model.MemoryCategory?) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(ChatDatabaseHelper.COL_MEM_CONTENT, newContent)
            put(ChatDatabaseHelper.COL_MEM_UPDATED_AT, System.currentTimeMillis())
            category?.let { put(ChatDatabaseHelper.COL_MEM_CATEGORY, it.name) }
        }
        db.update(
            ChatDatabaseHelper.TABLE_MEMORIES,
            values,
            "${ChatDatabaseHelper.COL_MEM_ID} = ?",
            arrayOf(id)
        )
    }

    override suspend fun deleteMemory(id: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(
            ChatDatabaseHelper.TABLE_MEMORIES,
            "${ChatDatabaseHelper.COL_MEM_ID} = ?",
            arrayOf(id)
        )
    }

    override suspend fun clearAllMemories() = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(ChatDatabaseHelper.TABLE_MEMORIES, null, null)
    }

    override suspend fun getAllMemories(): List<com.example.on_device_qwen.model.MemoryItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<com.example.on_device_qwen.model.MemoryItem>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            ChatDatabaseHelper.TABLE_MEMORIES,
            null,
            null,
            null,
            null,
            null,
            "${ChatDatabaseHelper.COL_MEM_PINNED} DESC, ${ChatDatabaseHelper.COL_MEM_UPDATED_AT} DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToMemory(it))
            }
        }
        list
    }

    suspend fun searchMemories(
        query: String,
        category: com.example.on_device_qwen.model.MemoryCategory? = null
    ): List<com.example.on_device_qwen.model.MemoryItem> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<com.example.on_device_qwen.model.MemoryItem>()
        val escaped = query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        val pattern = "%$escaped%"

        val selection = if (category != null) {
            "(${ChatDatabaseHelper.COL_MEM_CONTENT} LIKE ? ESCAPE '\\' OR ${ChatDatabaseHelper.COL_MEM_KEYWORDS} LIKE ? ESCAPE '\\') AND ${ChatDatabaseHelper.COL_MEM_CATEGORY} = ?"
        } else {
            "${ChatDatabaseHelper.COL_MEM_CONTENT} LIKE ? ESCAPE '\\' OR ${ChatDatabaseHelper.COL_MEM_KEYWORDS} LIKE ? ESCAPE '\\'"
        }
        val selectionArgs = if (category != null) {
            arrayOf(pattern, pattern, category.name)
        } else {
            arrayOf(pattern, pattern)
        }

        val cursor = db.query(
            ChatDatabaseHelper.TABLE_MEMORIES,
            null,
            selection,
            selectionArgs,
            null,
            null,
            "${ChatDatabaseHelper.COL_MEM_PINNED} DESC, ${ChatDatabaseHelper.COL_MEM_UPDATED_AT} DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToMemory(it))
            }
        }
        list
    }

    /**
     * Scores and ranks memories based on keyword overlap with current query, recency, and pinning.
     */
    override suspend fun findRelevantMemories(query: String, limit: Int): List<com.example.on_device_qwen.model.MemoryItem> = withContext(Dispatchers.IO) {
        val all = getAllMemories()
        if (all.isEmpty()) return@withContext emptyList()

        val stopWords = setOf("the", "is", "at", "which", "on", "a", "an", "and", "or", "to", "in", "for", "with", "about", "what", "how", "why", "can", "you", "please", "this", "that")
        val terms = query.lowercase()
            .split(Regex("[^a-zA-Z0-9_]+"))
            .filter { it.length > 2 && it !in stopWords }

        if (terms.isEmpty()) {
            return@withContext all.filter { it.isPinned || it.category == com.example.on_device_qwen.model.MemoryCategory.USER }.take(limit)
        }

        val now = System.currentTimeMillis()
        val scored = all.map { memory ->
            val contentLower = memory.content.lowercase()
            val keywordsLower = memory.keywords.lowercase()

            var score = 0.0
            for (term in terms) {
                if (contentLower.contains(term)) score += 3.0
                if (keywordsLower.contains(term)) score += 4.0
            }

            // Always consider pinned memories
            if (memory.isPinned) score += 2.0

            // User preferences get baseline consideration
            if (memory.category == com.example.on_device_qwen.model.MemoryCategory.USER) score += 1.0

            // Recency boost (within 7 days)
            val ageDays = (now - memory.updatedAt).toDouble() / (1000 * 60 * 60 * 24)
            if (ageDays < 7.0) {
                score += (7.0 - ageDays) * 0.2
            }

            memory to score
        }

        scored.filter { it.second > 1.5 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    // ── Conversation Summaries ────────────────────────────────────────────────

    override suspend fun saveConversationSummary(summary: com.example.on_device_qwen.model.ConversationSummary) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(ChatDatabaseHelper.COL_SUM_CONV_ID, summary.conversationId)
            put(ChatDatabaseHelper.COL_SUM_TEXT, summary.summaryText)
            put(ChatDatabaseHelper.COL_SUM_LAST_MSG_TIME, summary.lastMessageTimestamp)
            put(ChatDatabaseHelper.COL_SUM_UPDATED_AT, summary.updatedAt)
        }
        db.insertWithOnConflict(
            ChatDatabaseHelper.TABLE_SUMMARIES,
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    override suspend fun getConversationSummary(conversationId: String): com.example.on_device_qwen.model.ConversationSummary? = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            ChatDatabaseHelper.TABLE_SUMMARIES,
            null,
            "${ChatDatabaseHelper.COL_SUM_CONV_ID} = ?",
            arrayOf(conversationId),
            null,
            null,
            null
        )
        cursor.use {
            if (it.moveToFirst()) {
                com.example.on_device_qwen.model.ConversationSummary(
                    conversationId = it.getString(it.getColumnIndexOrThrow(ChatDatabaseHelper.COL_SUM_CONV_ID)),
                    summaryText = it.getString(it.getColumnIndexOrThrow(ChatDatabaseHelper.COL_SUM_TEXT)),
                    lastMessageTimestamp = it.getLong(it.getColumnIndexOrThrow(ChatDatabaseHelper.COL_SUM_LAST_MSG_TIME)),
                    updatedAt = it.getLong(it.getColumnIndexOrThrow(ChatDatabaseHelper.COL_SUM_UPDATED_AT))
                )
            } else null
        }
    }

    override suspend fun deleteConversationSummary(conversationId: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(
            ChatDatabaseHelper.TABLE_SUMMARIES,
            "${ChatDatabaseHelper.COL_SUM_CONV_ID} = ?",
            arrayOf(conversationId)
        )
    }

    private fun cursorToMemory(cursor: Cursor): com.example.on_device_qwen.model.MemoryItem {
        val catName = cursor.getString(cursor.getColumnIndexOrThrow(ChatDatabaseHelper.COL_MEM_CATEGORY))
        val category = try {
            com.example.on_device_qwen.model.MemoryCategory.valueOf(catName)
        } catch (_: Exception) {
            com.example.on_device_qwen.model.MemoryCategory.EPISODIC
        }
        return com.example.on_device_qwen.model.MemoryItem(
            id = cursor.getString(cursor.getColumnIndexOrThrow(ChatDatabaseHelper.COL_MEM_ID)),
            category = category,
            content = cursor.getString(cursor.getColumnIndexOrThrow(ChatDatabaseHelper.COL_MEM_CONTENT)),
            sourceConversationId = cursor.getString(cursor.getColumnIndexOrThrow(ChatDatabaseHelper.COL_MEM_SOURCE_CONV_ID)),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(ChatDatabaseHelper.COL_MEM_CREATED_AT)),
            updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow(ChatDatabaseHelper.COL_MEM_UPDATED_AT)),
            confidence = cursor.getFloat(cursor.getColumnIndexOrThrow(ChatDatabaseHelper.COL_MEM_CONFIDENCE)),
            keywords = cursor.getString(cursor.getColumnIndexOrThrow(ChatDatabaseHelper.COL_MEM_KEYWORDS)) ?: "",
            isPinned = cursor.getInt(cursor.getColumnIndexOrThrow(ChatDatabaseHelper.COL_MEM_PINNED)) == 1
        )
    }

    suspend fun exportConversationAsMarkdown(conversationId: String): String = withContext(Dispatchers.IO) {
        val conv = getConversation(conversationId) ?: return@withContext ""
        val messages = getMessages(conversationId)
        buildString {
            appendLine("# ${conv.title}")
            appendLine("*Exported from On-Device Qwen | Model: ${conv.modelName}*")
            appendLine()
            for (msg in messages) {
                if (msg.isUser) {
                    appendLine("### User")
                    appendLine(msg.text)
                } else {
                    appendLine("### Assistant (${conv.modelName})")
                    if (!msg.thinkText.isNullOrBlank()) {
                        appendLine("> *Thinking process:*")
                        appendLine("> ${msg.thinkText.replace("\n", "\n> ")}")
                        appendLine()
                    }
                    appendLine(msg.text)
                    msg.benchmark?.let { b ->
                        appendLine("*Stats: ${String.format(java.util.Locale.US, "%.1f", b.decodeTps)} TPS, TTFT ${b.ttftMs}ms on ${b.backend}*")
                    }
                }
                appendLine()
                appendLine("---")
                appendLine()
            }
        }
    }

    private fun cursorToConversation(cursor: Cursor): ConversationSession {
        return ConversationSession(
            id = cursor.getString(cursor.getColumnIndexOrThrow(ChatDatabaseHelper.COL_CONV_ID)),
            title = cursor.getString(cursor.getColumnIndexOrThrow(ChatDatabaseHelper.COL_CONV_TITLE)),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(ChatDatabaseHelper.COL_CONV_CREATED_AT)),
            updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow(ChatDatabaseHelper.COL_CONV_UPDATED_AT)),
            modelName = cursor.getString(cursor.getColumnIndexOrThrow(ChatDatabaseHelper.COL_CONV_MODEL_NAME)) ?: "",
            isPinned = cursor.getInt(cursor.getColumnIndexOrThrow(ChatDatabaseHelper.COL_CONV_PINNED)) == 1,
            lastMessagePreview = cursor.getString(cursor.getColumnIndexOrThrow(ChatDatabaseHelper.COL_CONV_PREVIEW)) ?: ""
        )
    }

    private fun cursorToMessage(cursor: Cursor): ChatMessage {
        val hasBenchmark = cursor.getLong(cursor.getColumnIndexOrThrow(ChatDatabaseHelper.COL_MSG_TTFT_MS)) > 0 ||
                cursor.getDouble(cursor.getColumnIndexOrThrow(ChatDatabaseHelper.COL_MSG_DECODE_TPS)) > 0
        val benchmark = if (hasBenchmark) {
            BenchmarkMetrics(
                ttftMs = cursor.getLong(cursor.getColumnIndexOrThrow(ChatDatabaseHelper.COL_MSG_TTFT_MS)),
                decodeTps = cursor.getDouble(cursor.getColumnIndexOrThrow(ChatDatabaseHelper.COL_MSG_DECODE_TPS)),
                decodeTokens = cursor.getInt(cursor.getColumnIndexOrThrow(ChatDatabaseHelper.COL_MSG_DECODE_TOKENS)),
                totalDurationMs = cursor.getLong(cursor.getColumnIndexOrThrow(ChatDatabaseHelper.COL_MSG_DURATION_MS)),
                backend = cursor.getString(cursor.getColumnIndexOrThrow(ChatDatabaseHelper.COL_MSG_BACKEND)) ?: "CPU"
            )
        } else null

        return ChatMessage(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(ChatDatabaseHelper.COL_MSG_ID)),
            conversationId = cursor.getString(cursor.getColumnIndexOrThrow(ChatDatabaseHelper.COL_MSG_CONV_ID)),
            text = cursor.getString(cursor.getColumnIndexOrThrow(ChatDatabaseHelper.COL_MSG_TEXT)),
            isUser = cursor.getInt(cursor.getColumnIndexOrThrow(ChatDatabaseHelper.COL_MSG_IS_USER)) == 1,
            isStreaming = false,
            timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(ChatDatabaseHelper.COL_MSG_TIMESTAMP)),
            imagePath = cursor.getString(cursor.getColumnIndexOrThrow(ChatDatabaseHelper.COL_MSG_IMAGE_PATH)),
            thinkText = cursor.getString(cursor.getColumnIndexOrThrow(ChatDatabaseHelper.COL_MSG_THINK_TEXT)),
            isError = cursor.getInt(cursor.getColumnIndexOrThrow(ChatDatabaseHelper.COL_MSG_IS_ERROR)) == 1,
            benchmark = benchmark
        )
    }
}
