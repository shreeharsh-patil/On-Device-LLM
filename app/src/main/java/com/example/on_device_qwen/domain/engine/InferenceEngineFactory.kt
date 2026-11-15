package com.example.on_device_qwen.domain.engine

import android.content.Context
import com.example.on_device_qwen.domain.performance.ThermalPerformanceManager
import com.example.on_device_qwen.model.EngineType
import java.io.File

class InferenceEngineFactory(
    private val context: Context,
    private val thermalManager: ThermalPerformanceManager? = null,
) {

    private val liteRtEngine by lazy { LiteRTInferenceEngine(context.applicationContext) }
    private val llamaCppEngine by lazy {
        LlamaCppInferenceEngine(context.applicationContext, thermalManager)
    }

    fun attachThermalManager(manager: ThermalPerformanceManager?) {
        llamaCppEngine.setThermalManager(manager)
    }

    fun getEngineForModel(file: File): InferenceEngine {
        val extension = file.extension.lowercase()
        return when {
            extension == "gguf" -> llamaCppEngine
            extension in listOf("litertlm", "litertl", "task", "tflite") -> liteRtEngine
            else -> if (isGgufFile(file)) llamaCppEngine else liteRtEngine
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
            if (!file.exists() || !file.isFile || file.length() < 4) {
                false
            } else {
                file.inputStream().use { stream ->
                    val magic = ByteArray(4)
                    if (stream.read(magic) < 4) return@use false
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
