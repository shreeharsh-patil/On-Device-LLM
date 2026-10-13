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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // BUG-020 FIX: Never use DROP TABLE in onUpgrade — it destroys all user data.
        // Use additive ALTER TABLE migrations for each version step.
        // Current version is 1. Future migrations should follow this pattern:
        //
        //   if (oldVersion < 2) {
        //       db.execSQL("ALTER TABLE $TABLE_MESSAGES ADD COLUMN new_column TEXT DEFAULT ''")
        //   }
        //   if (oldVersion < 3) { ... }
        //
        // For any schema changes that truly cannot be done with ALTER TABLE (e.g., column
        // rename / type change), copy data to a temp table, recreate, and copy back.
        //
        // If migration is impossible (e.g., completely incompatible schema), preserve data
        // integrity by exporting before dropping, never silently destroying user history.
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    companion object {
        const val DATABASE_NAME = "qwen_chat.db"
        const val DATABASE_VERSION = 1

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
    }
}
