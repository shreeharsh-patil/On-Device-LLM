package com.example.on_device_qwen.domain.engine

import android.content.Context
import com.example.on_device_qwen.model.EngineType
import com.example.on_device_qwen.model.ModelFormat
import java.io.File

class InferenceEngineFactory(private val context: Context) {

    private val liteRtEngine by lazy { LiteRTInferenceEngine(context) }
    private val llamaCppEngine by lazy { LlamaCppInferenceEngine(context) }

    fun getEngineForModel(file: File): InferenceEngine {
        val extension = file.extension.lowercase()
        return when {
            extension == "gguf" -> llamaCppEngine
            extension in listOf("litertlm", "litertl") -> liteRtEngine
            else -> {
                // If unknown extension, inspect file header magic
                if (isGgufFile(file)) llamaCppEngine else liteRtEngine
            }
        }
    }

    fun getEngine(type: EngineType): InferenceEngine {
        return when (type) {
            EngineType.LITERT -> liteRtEngine
            EngineType.LLAMA_CPP -> llamaCppEngine
        }
    }

    private fun isGgufFile(file: File): Boolean {
        return try {
            if (!file.exists() || file.length() < 4) false
            else {
                file.inputStream().use { stream ->
                    val magic = ByteArray(4)
                    stream.read(magic)
                    // ASCII "GGUF" = 0x47, 0x47, 0x55, 0x46
                    magic[0] == 'G'.code.toByte() &&
                    magic[1] == 'G'.code.toByte() &&
                    magic[2] == 'U'.code.toByte() &&
                    magic[3] == 'F'.code.toByte()
                }
            }
        } catch (_: Exception) {
            false
        }
    }
}
