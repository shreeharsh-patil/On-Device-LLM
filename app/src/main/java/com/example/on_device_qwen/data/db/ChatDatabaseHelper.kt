package com.example.on_device_qwen.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.on_device_qwen.model.BenchmarkMetrics
import com.example.on_device_qwen.model.ChatMessage
import com.example.on_device_qwen.model.ConversationSession

class ChatDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context, DATABASE_NAME, null, DATABASE_VERSION
) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_CONVERSATIONS (
                $COL_CONV_ID TEXT PRIMARY KEY,
                $COL_CONV_TITLE TEXT NOT NULL,
                $COL_CONV_CREATED_AT INTEGER NOT NULL,
                $COL_CONV_UPDATED_AT INTEGER NOT NULL,
                $COL_CONV_MODEL_NAME TEXT,
                $COL_CONV_PINNED INTEGER DEFAULT 0,
                $COL_CONV_PREVIEW TEXT DEFAULT ''
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_MESSAGES (
                $COL_MSG_ID INTEGER PRIMARY KEY,
                $COL_MSG_CONV_ID TEXT NOT NULL,
                $COL_MSG_TEXT TEXT NOT NULL,
                $COL_MSG_IS_USER INTEGER NOT NULL,
                $COL_MSG_TIMESTAMP INTEGER NOT NULL,
                $COL_MSG_IMAGE_PATH TEXT,
                $COL_MSG_THINK_TEXT TEXT,
                $COL_MSG_TTFT_MS INTEGER DEFAULT 0,
                $COL_MSG_DECODE_TPS REAL DEFAULT 0.0,
                $COL_MSG_DECODE_TOKENS INTEGER DEFAULT 0,
                $COL_MSG_DURATION_MS INTEGER DEFAULT 0,
                $COL_MSG_BACKEND TEXT DEFAULT 'CPU',
                $COL_MSG_IS_ERROR INTEGER DEFAULT 0,
                FOREIGN KEY ($COL_MSG_CONV_ID) REFERENCES $TABLE_CONVERSATIONS($COL_CONV_ID) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX idx_messages_conv ON $TABLE_MESSAGES($COL_MSG_CONV_ID, $COL_MSG_TIMESTAMP)")

        createMemoryTables(db)
    }

    private fun createMemoryTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_MEMORIES (
                $COL_MEM_ID TEXT PRIMARY KEY,
                $COL_MEM_CATEGORY TEXT NOT NULL,
                $COL_MEM_CONTENT TEXT NOT NULL,
                $COL_MEM_SOURCE_CONV_ID TEXT,
                $COL_MEM_CREATED_AT INTEGER NOT NULL,
                $COL_MEM_UPDATED_AT INTEGER NOT NULL,
                $COL_MEM_CONFIDENCE REAL DEFAULT 1.0,
                $COL_MEM_KEYWORDS TEXT DEFAULT '',
                $COL_MEM_PINNED INTEGER DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_memories_cat ON $TABLE_MEMORIES($COL_MEM_CATEGORY)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_memories_updated ON $TABLE_MEMORIES($COL_MEM_UPDATED_AT DESC)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_SUMMARIES (
                $COL_SUM_CONV_ID TEXT PRIMARY KEY,
                $COL_SUM_TEXT TEXT NOT NULL,
                $COL_SUM_LAST_MSG_TIME INTEGER NOT NULL,
                $COL_SUM_UPDATED_AT INTEGER NOT NULL,
                FOREIGN KEY ($COL_SUM_CONV_ID) REFERENCES $TABLE_CONVERSATIONS($COL_CONV_ID) ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createMemoryTables(db)
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    companion object {
        const val DATABASE_NAME = "qwen_chat.db"
        const val DATABASE_VERSION = 2

        const val TABLE_CONVERSATIONS = "conversations"
        const val COL_CONV_ID = "id"
        const val COL_CONV_TITLE = "title"
        const val COL_CONV_CREATED_AT = "created_at"
        const val COL_CONV_UPDATED_AT = "updated_at"
        const val COL_CONV_MODEL_NAME = "model_name"
        const val COL_CONV_PINNED = "is_pinned"
        const val COL_CONV_PREVIEW = "last_preview"

        const val TABLE_MESSAGES = "messages"
        const val COL_MSG_ID = "id"
        const val COL_MSG_CONV_ID = "conversation_id"
        const val COL_MSG_TEXT = "text"
        const val COL_MSG_IS_USER = "is_user"
        const val COL_MSG_TIMESTAMP = "timestamp"
        const val COL_MSG_IMAGE_PATH = "image_path"
        const val COL_MSG_THINK_TEXT = "think_text"
        const val COL_MSG_TTFT_MS = "ttft_ms"
        const val COL_MSG_DECODE_TPS = "decode_tps"
        const val COL_MSG_DECODE_TOKENS = "decode_tokens"
        const val COL_MSG_DURATION_MS = "duration_ms"
        const val COL_MSG_BACKEND = "backend"
        const val COL_MSG_IS_ERROR = "is_error"

        const val TABLE_MEMORIES = "memories"
        const val COL_MEM_ID = "id"
        const val COL_MEM_CATEGORY = "category"
        const val COL_MEM_CONTENT = "content"
        const val COL_MEM_SOURCE_CONV_ID = "source_conversation_id"
        const val COL_MEM_CREATED_AT = "created_at"
        const val COL_MEM_UPDATED_AT = "updated_at"
        const val COL_MEM_CONFIDENCE = "confidence"
        const val COL_MEM_KEYWORDS = "keywords"
        const val COL_MEM_PINNED = "is_pinned"

        const val TABLE_SUMMARIES = "conversation_summaries"
        const val COL_SUM_CONV_ID = "conversation_id"
        const val COL_SUM_TEXT = "summary_text"
        const val COL_SUM_LAST_MSG_TIME = "last_message_timestamp"
        const val COL_SUM_UPDATED_AT = "updated_at"
    }
}
