package com.example.on_device_qwen.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.on_device_qwen.data.db.ChatDatabaseHelper
import com.example.on_device_qwen.model.BenchmarkMetrics
import com.example.on_device_qwen.model.ChatMessage
import com.example.on_device_qwen.model.ConversationSession
import com.example.on_device_qwen.model.MessageIdGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ConversationRepository(context: Context) : com.example.on_device_qwen.model.IMemoryRepository {

    private val appContext = context.applicationContext
    private val dbHelper = ChatDatabaseHelper(appContext)

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

    suspend fun getConversationById(conversationId: String): ConversationSession? =
        getConversation(conversationId)

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
        val imagePaths = collectImagePaths(
            db,
            "${ChatDatabaseHelper.COL_MSG_CONV_ID} = ? AND ${ChatDatabaseHelper.COL_MSG_IMAGE_PATH} IS NOT NULL",
            arrayOf(id)
        )
        // BUG-005 FIX: Use a transaction so all deletes are atomic.
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
        cleanupOrphanedVlmImages(imagePaths, db)
    }

    suspend fun clearAllConversations() = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val imagePaths = collectImagePaths(
            db,
            "${ChatDatabaseHelper.COL_MSG_IMAGE_PATH} IS NOT NULL",
            null
        )
        db.beginTransaction()
        try {
            db.delete(ChatDatabaseHelper.TABLE_MESSAGES, null, null)
            db.delete(ChatDatabaseHelper.TABLE_SUMMARIES, null, null)
            db.delete(ChatDatabaseHelper.TABLE_CONVERSATIONS, null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        cleanupOrphanedVlmImages(imagePaths, db)
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
            "${ChatDatabaseHelper.COL_MSG_TIMESTAMP} ASC, ${ChatDatabaseHelper.COL_MSG_ID} ASC"
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
        val resolvedId = if (message.id <= 0L) MessageIdGenerator.nextId() else message.id
        val values = ContentValues().apply {
            put(ChatDatabaseHelper.COL_MSG_ID, resolvedId)
            put(ChatDatabaseHelper.COL_MSG_CONV_ID, message.conversationId)
            put(ChatDatabaseHelper.COL_MSG_TEXT, message.text)
            put(ChatDatabaseHelper.COL_MSG_IS_USER, if (message.isUser) 1 else 0)
            put(ChatDatabaseHelper.COL_MSG_TIMESTAMP, message.timestamp)
            put(ChatDatabaseHelper.COL_MSG_IMAGE_PATH, message.imagePath)
            put(ChatDatabaseHelper.COL_MSG_THINK_TEXT, message.thinkText)
            put(ChatDatabaseHelper.COL_MSG_IS_ERROR, if (message.isError) 1 else 0)
            put(ChatDatabaseHelper.COL_MSG_MODEL_NAME, message.modelName)
            put(ChatDatabaseHelper.COL_MSG_BACKEND_LABEL, message.backendLabel)
            put(ChatDatabaseHelper.COL_MSG_IS_CANCELLED, if (message.isCancelled) 1 else 0)
            val b = message.benchmark
            put(ChatDatabaseHelper.COL_MSG_TTFT_MS, b?.ttftMs ?: 0L)
            put(ChatDatabaseHelper.COL_MSG_DECODE_TPS, b?.decodeTps ?: 0.0)
            put(ChatDatabaseHelper.COL_MSG_DECODE_TOKENS, b?.decodeTokens ?: 0)
            put(ChatDatabaseHelper.COL_MSG_DURATION_MS, b?.totalDurationMs ?: 0L)
            put(ChatDatabaseHelper.COL_MSG_BACKEND, b?.backend ?: "CPU")
            put(ChatDatabaseHelper.COL_MSG_PREFILL_TPS, b?.prefillTps ?: 0.0)
            put(ChatDatabaseHelper.COL_MSG_PREFILL_TOKENS, b?.prefillTokens ?: 0)
        }
        db.beginTransaction()
        try {
            db.insertWithOnConflict(
                ChatDatabaseHelper.TABLE_MESSAGES,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            )
            // Keep parent conversation preview and timestamp synchronized atomically.
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
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun deleteMessage(messageId: Long) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        var convId: String? = null
        var imagePath: String? = null
        db.beginTransaction()
        try {
            db.query(
                ChatDatabaseHelper.TABLE_MESSAGES,
                arrayOf(ChatDatabaseHelper.COL_MSG_CONV_ID, ChatDatabaseHelper.COL_MSG_IMAGE_PATH),
                "${ChatDatabaseHelper.COL_MSG_ID} = ?",
                arrayOf(messageId.toString()),
                null,
                null,
                null
            ).use { c ->
                if (c.moveToFirst()) {
                    convId = c.getString(0)
                    imagePath = if (c.isNull(1)) null else c.getString(1)
                }
            }
            db.delete(
                ChatDatabaseHelper.TABLE_MESSAGES,
                "${ChatDatabaseHelper.COL_MSG_ID} = ?",
                arrayOf(messageId.toString())
            )
            convId?.let { refreshConversationPreview(db, it) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        imagePath?.let { cleanupOrphanedVlmImages(listOf(it), db) }
    }

    suspend fun deleteLastAssistantMessage(conversationId: String): Long? = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        var lastId: Long? = null
        var imagePath: String? = null
        db.beginTransaction()
        try {
            db.rawQuery(
                """
                SELECT ${ChatDatabaseHelper.COL_MSG_ID}, ${ChatDatabaseHelper.COL_MSG_IMAGE_PATH}
                FROM ${ChatDatabaseHelper.TABLE_MESSAGES}
                WHERE ${ChatDatabaseHelper.COL_MSG_CONV_ID} = ? AND ${ChatDatabaseHelper.COL_MSG_IS_USER} = 0
                ORDER BY ${ChatDatabaseHelper.COL_MSG_TIMESTAMP} DESC, ${ChatDatabaseHelper.COL_MSG_ID} DESC
                LIMIT 1
                """.trimIndent(),
                arrayOf(conversationId)
            ).use { c ->
                if (c.moveToFirst()) {
                    lastId = c.getLong(0)
                    imagePath = if (c.isNull(1)) null else c.getString(1)
                }
            }
            lastId?.let { id ->
                db.delete(
                    ChatDatabaseHelper.TABLE_MESSAGES,
                    "${ChatDatabaseHelper.COL_MSG_ID} = ?",
                    arrayOf(id.toString())
                )
                db.delete(
                    ChatDatabaseHelper.TABLE_SUMMARIES,
                    "${ChatDatabaseHelper.COL_SUM_CONV_ID} = ?",
                    arrayOf(conversationId)
                )
                refreshConversationPreview(db, conversationId)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        imagePath?.let { cleanupOrphanedVlmImages(listOf(it), db) }
        lastId
    }

    suspend fun deleteMessagesAfter(conversationId: String, timestamp: Long) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val selection = "${ChatDatabaseHelper.COL_MSG_CONV_ID} = ? AND ${ChatDatabaseHelper.COL_MSG_TIMESTAMP} >= ?"
        val args = arrayOf(conversationId, timestamp.toString())
        val imagePaths = collectImagePaths(
            db,
            "$selection AND ${ChatDatabaseHelper.COL_MSG_IMAGE_PATH} IS NOT NULL",
            args
        )
        db.beginTransaction()
        try {
            db.delete(ChatDatabaseHelper.TABLE_MESSAGES, selection, args)
            db.delete(
                ChatDatabaseHelper.TABLE_SUMMARIES,
                "${ChatDatabaseHelper.COL_SUM_CONV_ID} = ?",
                arrayOf(conversationId)
            )
            refreshConversationPreview(db, conversationId)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        cleanupOrphanedVlmImages(imagePaths, db)
    }

    suspend fun deleteMessagesFromId(conversationId: String, messageId: Long) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        var targetTs: Long? = null
        val imagePaths = mutableListOf<String>()
        db.beginTransaction()
        try {
            db.query(
                ChatDatabaseHelper.TABLE_MESSAGES,
                arrayOf(ChatDatabaseHelper.COL_MSG_TIMESTAMP),
                "${ChatDatabaseHelper.COL_MSG_CONV_ID} = ? AND ${ChatDatabaseHelper.COL_MSG_ID} = ?",
                arrayOf(conversationId, messageId.toString()),
                null,
                null,
                null
            ).use { c ->
                if (c.moveToFirst()) {
                    targetTs = c.getLong(0)
                }
            }
            val ts = targetTs
            if (ts != null) {
                val selection =
                    "${ChatDatabaseHelper.COL_MSG_CONV_ID} = ? AND (" +
                        "${ChatDatabaseHelper.COL_MSG_TIMESTAMP} > ? OR " +
                        "(${ChatDatabaseHelper.COL_MSG_TIMESTAMP} = ? AND ${ChatDatabaseHelper.COL_MSG_ID} >= ?))"
                val args = arrayOf(conversationId, ts.toString(), ts.toString(), messageId.toString())
                imagePaths += collectImagePaths(
                    db,
                    "$selection AND ${ChatDatabaseHelper.COL_MSG_IMAGE_PATH} IS NOT NULL",
                    args
                )
                db.delete(ChatDatabaseHelper.TABLE_MESSAGES, selection, args)
                db.delete(
                    ChatDatabaseHelper.TABLE_SUMMARIES,
                    "${ChatDatabaseHelper.COL_SUM_CONV_ID} = ?",
                    arrayOf(conversationId)
                )
                refreshConversationPreview(db, conversationId)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        cleanupOrphanedVlmImages(imagePaths, db)
    }

    suspend fun getUserMessageForAssistantTurn(
        conversationId: String,
        assistantMessageId: Long,
    ): ChatMessage? = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val targetTs: Long = db.query(
            ChatDatabaseHelper.TABLE_MESSAGES,
            arrayOf(ChatDatabaseHelper.COL_MSG_TIMESTAMP),
            "${ChatDatabaseHelper.COL_MSG_CONV_ID} = ? AND ${ChatDatabaseHelper.COL_MSG_ID} = ?",
            arrayOf(conversationId, assistantMessageId.toString()),
            null,
            null,
            null
        ).use { c ->
            if (c.moveToFirst()) c.getLong(0) else null
        } ?: return@withContext null

        db.rawQuery(
            """
            SELECT * FROM ${ChatDatabaseHelper.TABLE_MESSAGES}
            WHERE ${ChatDatabaseHelper.COL_MSG_CONV_ID} = ?
              AND ${ChatDatabaseHelper.COL_MSG_IS_USER} = 1
              AND (
                ${ChatDatabaseHelper.COL_MSG_TIMESTAMP} < ? OR
                (${ChatDatabaseHelper.COL_MSG_TIMESTAMP} = ? AND ${ChatDatabaseHelper.COL_MSG_ID} < ?)
              )
            ORDER BY ${ChatDatabaseHelper.COL_MSG_TIMESTAMP} DESC, ${ChatDatabaseHelper.COL_MSG_ID} DESC
            LIMIT 1
            """.trimIndent(),
            arrayOf(conversationId, targetTs.toString(), targetTs.toString(), assistantMessageId.toString())
        ).use { c ->
            if (c.moveToFirst()) cursorToMessage(c) else null
        }
    }

    private fun refreshConversationPreview(db: SQLiteDatabase, conversationId: String) {
        var preview = ""
        var updatedAt: Long? = null
        db.rawQuery(
            """
            SELECT ${ChatDatabaseHelper.COL_MSG_TEXT}, ${ChatDatabaseHelper.COL_MSG_TIMESTAMP}
            FROM ${ChatDatabaseHelper.TABLE_MESSAGES}
            WHERE ${ChatDatabaseHelper.COL_MSG_CONV_ID} = ?
            ORDER BY ${ChatDatabaseHelper.COL_MSG_TIMESTAMP} DESC, ${ChatDatabaseHelper.COL_MSG_ID} DESC
            LIMIT 1
            """.trimIndent(),
            arrayOf(conversationId)
        ).use { c ->
            if (c.moveToFirst()) {
                preview = (c.getString(0) ?: "").take(80)
                updatedAt = c.getLong(1)
            }
        }
        val convValues = ContentValues().apply {
            put(ChatDatabaseHelper.COL_CONV_PREVIEW, preview)
            updatedAt?.let { put(ChatDatabaseHelper.COL_CONV_UPDATED_AT, it) }
        }
        db.update(
            ChatDatabaseHelper.TABLE_CONVERSATIONS,
            convValues,
            "${ChatDatabaseHelper.COL_CONV_ID} = ?",
            arrayOf(conversationId)
        )
    }

    private fun collectImagePaths(
        db: SQLiteDatabase,
        selection: String?,
        selectionArgs: Array<String>?,
    ): List<String> {
        val paths = mutableListOf<String>()
        db.query(
            ChatDatabaseHelper.TABLE_MESSAGES,
            arrayOf(ChatDatabaseHelper.COL_MSG_IMAGE_PATH),
            selection,
            selectionArgs,
            null,
            null,
            null
        ).use { c ->
            while (c.moveToNext()) {
                if (!c.isNull(0)) {
                    c.getString(0)?.takeIf { it.isNotBlank() }?.let { paths.add(it) }
                }
            }
        }
        return paths
    }

    private fun cleanupOrphanedVlmImages(candidatePaths: List<String>, db: SQLiteDatabase) {
        if (candidatePaths.isEmpty()) return
        val cacheRootCanonical = try {
            appContext.cacheDir.canonicalPath
        } catch (_: Exception) {
            return
        }
        for (rawPath in candidatePaths.distinct()) {
            if (rawPath.isBlank()) continue
            val file = File(rawPath)
            if (!file.name.startsWith("vlm_input_")) continue
            val fileCanonical = try {
                file.canonicalPath
            } catch (_: Exception) {
                continue
            }
            if (!fileCanonical.startsWith(cacheRootCanonical + File.separator)) continue
            val stillReferenced = db.rawQuery(
                "SELECT 1 FROM ${ChatDatabaseHelper.TABLE_MESSAGES} WHERE ${ChatDatabaseHelper.COL_MSG_IMAGE_PATH} = ? LIMIT 1",
                arrayOf(rawPath)
            ).use { it.moveToFirst() }
            if (!stillReferenced && file.exists()) {
                file.delete()
            }
        }
    }

    // ── Persistent Cross-Chat Memory ──────────────────────────────────────────

    override suspend fun saveMemory(item: com.example.on_device_qwen.model.MemoryItem) = withContext(Dispatchers.IO) {
        val cleanContent = item.sanitizedContent()
        if (cleanContent.isBlank() || com.example.on_device_qwen.domain.memory.MemoryManager.containsSensitiveInfo(cleanContent)) {
            return@withContext -1L
        }
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(ChatDatabaseHelper.COL_MEM_ID, item.id)
            put(ChatDatabaseHelper.COL_MEM_CATEGORY, item.category.name)
            put(ChatDatabaseHelper.COL_MEM_CONTENT, cleanContent)
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
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    override suspend fun updateMemory(id: String, newContent: String, category: com.example.on_device_qwen.model.MemoryCategory?) = withContext(Dispatchers.IO) {
        val cleanContent = com.example.on_device_qwen.domain.memory.MemoryManager.sanitizeContent(newContent)
        if (cleanContent.isBlank() || com.example.on_device_qwen.domain.memory.MemoryManager.containsSensitiveInfo(cleanContent)) {
            return@withContext 0
        }
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(ChatDatabaseHelper.COL_MEM_CONTENT, cleanContent)
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

    override suspend fun updateMemoryWithKeywords(
        id: String,
        newContent: String,
        keywords: String,
        category: com.example.on_device_qwen.model.MemoryCategory?,
    ): Int = withContext(Dispatchers.IO) {
        val cleanContent = com.example.on_device_qwen.domain.memory.MemoryManager.sanitizeContent(newContent)
        if (cleanContent.isBlank() || com.example.on_device_qwen.domain.memory.MemoryManager.containsSensitiveInfo(cleanContent)) {
            return@withContext 0
        }
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(ChatDatabaseHelper.COL_MEM_CONTENT, cleanContent)
            put(ChatDatabaseHelper.COL_MEM_KEYWORDS, keywords)
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
        val ttftMs = cursor.getLong(cursor.getColumnIndexOrThrow(ChatDatabaseHelper.COL_MSG_TTFT_MS))
        val decodeTps = cursor.getDouble(cursor.getColumnIndexOrThrow(ChatDatabaseHelper.COL_MSG_DECODE_TPS))
        val decodeTokens = cursor.getInt(cursor.getColumnIndexOrThrow(ChatDatabaseHelper.COL_MSG_DECODE_TOKENS))
        val durationMs = cursor.getLong(cursor.getColumnIndexOrThrow(ChatDatabaseHelper.COL_MSG_DURATION_MS))
        val backend = cursor.getString(cursor.getColumnIndexOrThrow(ChatDatabaseHelper.COL_MSG_BACKEND)) ?: "CPU"

        val prefillTpsIdx = cursor.getColumnIndex(ChatDatabaseHelper.COL_MSG_PREFILL_TPS)
        val prefillTps = if (prefillTpsIdx >= 0 && !cursor.isNull(prefillTpsIdx)) {
            cursor.getDouble(prefillTpsIdx).takeIf { it > 0.0 }
        } else null

        val prefillTokensIdx = cursor.getColumnIndex(ChatDatabaseHelper.COL_MSG_PREFILL_TOKENS)
        val prefillTokens = if (prefillTokensIdx >= 0 && !cursor.isNull(prefillTokensIdx)) {
            cursor.getInt(prefillTokensIdx).takeIf { it > 0 }
        } else null

        val modelNameIdx = cursor.getColumnIndex(ChatDatabaseHelper.COL_MSG_MODEL_NAME)
        val modelName = if (modelNameIdx >= 0 && !cursor.isNull(modelNameIdx)) cursor.getString(modelNameIdx) else null

        val backendLabelIdx = cursor.getColumnIndex(ChatDatabaseHelper.COL_MSG_BACKEND_LABEL)
        val backendLabel = if (backendLabelIdx >= 0 && !cursor.isNull(backendLabelIdx)) cursor.getString(backendLabelIdx) else null

        val isCancelledIdx = cursor.getColumnIndex(ChatDatabaseHelper.COL_MSG_IS_CANCELLED)
        val isCancelled = isCancelledIdx >= 0 && cursor.getInt(isCancelledIdx) == 1

        val hasBenchmark = ttftMs > 0L ||
            decodeTps > 0.0 ||
            (prefillTps ?: 0.0) > 0.0 ||
            decodeTokens > 0 ||
            (prefillTokens ?: 0) > 0 ||
            isCancelled

        val benchmark = if (hasBenchmark) {
            BenchmarkMetrics(
                ttftMs = ttftMs,
                decodeTps = decodeTps,
                prefillTps = prefillTps,
                prefillTokens = prefillTokens,
                decodeTokens = decodeTokens,
                totalDurationMs = durationMs,
                backend = backend,
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
            benchmark = benchmark,
            modelName = modelName,
            backendLabel = backendLabel,
            isCancelled = isCancelled,
        )
    }
}
