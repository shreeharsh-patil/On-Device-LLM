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

class ConversationRepository(context: Context) {

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
        db.delete(
            ChatDatabaseHelper.TABLE_MESSAGES,
            "${ChatDatabaseHelper.COL_MSG_CONV_ID} = ?",
            arrayOf(id)
        )
        db.delete(
            ChatDatabaseHelper.TABLE_CONVERSATIONS,
            "${ChatDatabaseHelper.COL_CONV_ID} = ?",
            arrayOf(id)
        )
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
        val pattern = "%${query.trim()}%"
        val cursor = db.rawQuery(
            """
            SELECT DISTINCT c.* FROM ${ChatDatabaseHelper.TABLE_CONVERSATIONS} c
            LEFT JOIN ${ChatDatabaseHelper.TABLE_MESSAGES} m ON c.${ChatDatabaseHelper.COL_CONV_ID} = m.${ChatDatabaseHelper.COL_MSG_CONV_ID}
            WHERE c.${ChatDatabaseHelper.COL_CONV_TITLE} LIKE ? OR m.${ChatDatabaseHelper.COL_MSG_TEXT} LIKE ?
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

    suspend fun deleteMessagesAfter(conversationId: String, timestamp: Long) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(
            ChatDatabaseHelper.TABLE_MESSAGES,
            "${ChatDatabaseHelper.COL_MSG_CONV_ID} = ? AND ${ChatDatabaseHelper.COL_MSG_TIMESTAMP} >= ?",
            arrayOf(conversationId, timestamp.toString())
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
