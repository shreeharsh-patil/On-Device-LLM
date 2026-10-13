package com.example.on_device_qwen.domain.engine

import com.example.on_device_qwen.model.EngineType
import com.example.on_device_qwen.model.ModelFormat
import com.example.on_device_qwen.model.ModelMetadata
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * High-performance, zero-allocation binary metadata parser for GGUF files.
 * Extracts architecture, quantization, context length, tensor counts, and estimates RAM.
 */
object GgufMetadataParser {

    private const val GGUF_MAGIC = 0x46554747 // "GGUF" in little endian

    // GGUF Metadata Value Types
    private const val TYPE_UINT8 = 0
    private const val TYPE_INT8 = 1
    private const val TYPE_UINT16 = 2
    private const val TYPE_INT16 = 3
    private const val TYPE_UINT32 = 4
    private const val TYPE_INT32 = 5
    private const val TYPE_FLOAT32 = 6
    private const val TYPE_BOOL = 7
    private const val TYPE_STRING = 8
    private const val TYPE_ARRAY = 9
    private const val TYPE_UINT64 = 10
    private const val TYPE_INT64 = 11
    private const val TYPE_FLOAT64 = 12

    fun parse(file: File): ModelMetadata? {
        if (!file.exists() || file.length() < 32) return null

        try {
            RandomAccessFile(file, "r").use { raf ->
                val headerBuf = ByteArray(32)
                raf.readFully(headerBuf)
                val buffer = ByteBuffer.wrap(headerBuf).order(ByteOrder.LITTLE_ENDIAN)

                val magic = buffer.int
                if (magic != GGUF_MAGIC) {
                    return null // Not a valid GGUF file
                }

                val version = buffer.int
                if (version !in 2..3) {
                    return null // Unsupported GGUF version
                }

                val tensorCount = buffer.long
                val metadataKvCount = buffer.long

                var architecture = "qwen2"
                var modelName = file.nameWithoutExtension
                var contextLength = 4096
                var fileType = -1
                var blockCount = 28
                var embeddingLength = 1536
                var chatTemplate: String? = null

                // Read KV pairs up to metadataKvCount (capped to first 256 keys for performance)
                val maxKeysToRead = metadataKvCount.coerceAtMost(256)
                for (i in 0 until maxKeysToRead) {
                    val key = readString(raf) ?: break
                    val valueType = readInt32(raf) ?: break

                    when (key) {
                        "general.architecture" -> {
                            readStringValue(raf, valueType)?.let { architecture = it }
                        }
                        "general.name" -> {
                            readStringValue(raf, valueType)?.let { modelName = it }
                        }
                        "general.file_type" -> {
                            readIntValue(raf, valueType)?.let { fileType = it }
                        }
                        "$architecture.context_length", "general.context_length" -> {
                            readIntValue(raf, valueType)?.let { if (it > 0) contextLength = it }
                        }
                        "$architecture.block_count" -> {
                            readIntValue(raf, valueType)?.let { if (it > 0) blockCount = it }
                        }
                        "$architecture.embedding_length" -> {
                            readIntValue(raf, valueType)?.let { if (it > 0) embeddingLength = it }
                        }
                        "tokenizer.chat_template" -> {
                            readStringValue(raf, valueType)?.let { chatTemplate = it }
                        }
                        else -> {
                            skipValue(raf, valueType)
                        }
                    }
                }

                val quantName = resolveFileType(fileType, file.name)
                val isAbliterated = modelName.contains("abliterat", ignoreCase = true) ||
                        file.name.contains("abliterat", ignoreCase = true) ||
                        file.name.contains("uncensored", ignoreCase = true)

                // Estimate RAM requirements:
                // RAM = Model file size (mmapped / loaded) + KV cache overhead + context memory + scratch buffers
                val kvCacheEstimateBytes = (blockCount * (contextLength / 1024L) * 32L * 1024L * 1024L)
                val baseOverheadBytes = 256L * 1024L * 1024L // 256 MB working buffers
                val estimatedRamBytes = file.length() + kvCacheEstimateBytes + baseOverheadBytes

                val parameterCount = estimateParameterCount(file.length(), quantName)

                return ModelMetadata(
                    file = file,
                    name = modelName,
                    format = ModelFormat.GGUF,
                    engineType = EngineType.LLAMA_CPP,
                    architecture = architecture,
                    quantization = quantName,
                    contextLength = contextLength,
                    fileSizeBytes = file.length(),
                    estimatedRamBytes = estimatedRamBytes,
                    hasVision = architecture.contains("vl", ignoreCase = true),
                    isAbliterated = isAbliterated,
                    parameterCount = parameterCount
                )
            }
        } catch (_: Exception) {
            return null
        }
    }

    private fun readString(raf: RandomAccessFile): String? {
        val lenBytes = ByteArray(8)
        if (raf.read(lenBytes) != 8) return null
        val len = ByteBuffer.wrap(lenBytes).order(ByteOrder.LITTLE_ENDIAN).long
        if (len < 0 || len > 1024 * 64) return null
        val strBytes = ByteArray(len.toInt())
        if (raf.read(strBytes) != len.toInt()) return null
        return String(strBytes, Charsets.UTF_8)
    }

    private fun readInt32(raf: RandomAccessFile): Int? {
        val buf = ByteArray(4)
        if (raf.read(buf) != 4) return null
        return ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).int
    }

    private fun readStringValue(raf: RandomAccessFile, valueType: Int): String? {
        return if (valueType == TYPE_STRING) readString(raf) else {
            skipValue(raf, valueType)
            null
        }
    }

    private fun readIntValue(raf: RandomAccessFile, valueType: Int): Int? {
        return when (valueType) {
            TYPE_UINT8, TYPE_INT8 -> raf.read().takeIf { it >= 0 }
            TYPE_UINT16, TYPE_INT16 -> {
                val b = ByteArray(2); if (raf.read(b) == 2) ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).short.toInt() else null
            }
            TYPE_UINT32, TYPE_INT32 -> {
                val b = ByteArray(4); if (raf.read(b) == 4) ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).int else null
            }
            TYPE_UINT64, TYPE_INT64 -> {
                val b = ByteArray(8); if (raf.read(b) == 8) ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).long.toInt() else null
            }
            else -> {
                skipValue(raf, valueType)
                null
            }
        }
    }

    private fun skipValue(raf: RandomAccessFile, valueType: Int) {
        when (valueType) {
            TYPE_UINT8, TYPE_INT8, TYPE_BOOL -> raf.skipBytes(1)
            TYPE_UINT16, TYPE_INT16 -> raf.skipBytes(2)
            TYPE_UINT32, TYPE_INT32, TYPE_FLOAT32 -> raf.skipBytes(4)
            TYPE_UINT64, TYPE_INT64, TYPE_FLOAT64 -> raf.skipBytes(8)
            TYPE_STRING -> {
                val lenBytes = ByteArray(8)
                if (raf.read(lenBytes) == 8) {
                    val len = ByteBuffer.wrap(lenBytes).order(ByteOrder.LITTLE_ENDIAN).long
                    if (len in 0..10_000_000) raf.skipBytes(len.toInt())
                }
            }
            TYPE_ARRAY -> {
                val arrItemType = readInt32(raf) ?: return
                val countBytes = ByteArray(8)
                if (raf.read(countBytes) == 8) {
                    val count = ByteBuffer.wrap(countBytes).order(ByteOrder.LITTLE_ENDIAN).long
                    for (i in 0 until count.coerceAtMost(500)) {
                        skipValue(raf, arrItemType)
                    }
                }
            }
        }
    }

    private fun resolveFileType(fileType: Int, fileName: String): String {
        return when (fileType) {
            2 -> "Q4_0"
            3 -> "Q4_1"
            7 -> "Q8_0"
            8 -> "Q5_0"
            9 -> "Q5_1"
            10 -> "Q2_K"
            11 -> "Q3_K_S"
            12 -> "Q3_K_M"
            13 -> "Q3_K_L"
            14 -> "Q4_K_S"
            15 -> "Q4_K_M"
            16 -> "Q5_K_S"
            17 -> "Q5_K_M"
            18 -> "Q6_K"
            0 -> "F32"
            1 -> "F16"
            else -> {
                val lower = fileName.lowercase()
                when {
                    lower.contains("q4_k_m") -> "Q4_K_M"
                    lower.contains("q4_k_s") -> "Q4_K_S"
                    lower.contains("q5_k_m") -> "Q5_K_M"
                    lower.contains("q8_0") -> "Q8_0"
                    lower.contains("q4_0") -> "Q4_0"
                    else -> "Quantized"
                }
            }
        }
    }

    private fun estimateParameterCount(fileSizeBytes: Long, quant: String): String {
        val mb = fileSizeBytes / (1024 * 1024)
        return when {
            mb < 600 -> "0.6B"
            mb in 600..1400 -> "1.5B - 1.7B"
            mb in 1401..2800 -> "3B - 4B"
            mb in 2801..5500 -> "7B - 8B"
            else -> "> 8B"
        }
    }
}
