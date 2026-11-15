package com.example.on_device_qwen.domain.engine

import com.example.on_device_qwen.model.ChatTemplateFamily
import com.example.on_device_qwen.model.EngineType
import com.example.on_device_qwen.model.GenerationConfig
import com.example.on_device_qwen.model.Modality
import com.example.on_device_qwen.model.ModelCompatibilityStatus
import com.example.on_device_qwen.model.ModelFormat
import com.example.on_device_qwen.model.ModelMetadata
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Binary GGUF header and KV metadata parser.
 * Reads header fields cleanly at the 24-byte boundary, skips large GGUF arrays/strings safely,
 * parses chat templates & KV cache dimensions, and reports honest capabilities.
 */
object GgufMetadataParser {

    private const val GGUF_MAGIC = 0x46554747 // "GGUF" in little-endian
    private const val GGUF_HEADER_BYTES = 24
    private const val MAX_KV_SCAN = 1024L

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
        if (!file.exists() || !file.isFile || file.length() < GGUF_HEADER_BYTES) return null

        return try {
            RandomAccessFile(file, "r").use { raf ->
                val cursor = BufferedCursor(raf, file.length())
                val headerBuf = ByteArray(GGUF_HEADER_BYTES)
                if (!cursor.readFully(headerBuf)) return null
                val buffer = ByteBuffer.wrap(headerBuf).order(ByteOrder.LITTLE_ENDIAN)

                val magic = buffer.int
                if (magic != GGUF_MAGIC) return null

                val version = buffer.int
                if (version !in 2..3) return null

                val tensorCount = buffer.long
                val metadataKvCount = buffer.long
                if (tensorCount < 0L || metadataKvCount < 0L || metadataKvCount > 10_000_000L) {
                    return null
                }

                var architecture: String? = null
                var modelName = file.nameWithoutExtension
                var fileType = -1
                var chatTemplate: String? = null
                val intKvs = HashMap<String, Int>()

                val maxKeysToRead = metadataKvCount.coerceAtMost(MAX_KV_SCAN)
                for (i in 0L until maxKeysToRead) {
                    if (cursor.isAtEnd) break
                    val key = readString(cursor, maxBytes = 65_536) ?: break
                    val valueType = cursor.readInt32() ?: break

                    when {
                        key == "general.architecture" -> {
                            readStringValue(cursor, valueType)?.let { architecture = it }
                        }
                        key == "general.name" -> {
                            readStringValue(cursor, valueType)?.let { modelName = it }
                        }
                        key == "general.file_type" -> {
                            readIntValue(cursor, valueType)?.let { fileType = it }
                        }
                        key == "tokenizer.chat_template" -> {
                            readStringValue(cursor, valueType, maxBytes = 256 * 1024)?.let {
                                chatTemplate = it
                            }
                        }
                        key == "general.context_length" ||
                            key.endsWith(".context_length") ||
                            key.endsWith(".block_count") ||
                            key.endsWith(".embedding_length") ||
                            key.endsWith(".attention.head_count") ||
                            key.endsWith(".attention.head_count_kv") ||
                            key.endsWith(".attention.key_length") -> {
                            readIntValue(cursor, valueType)?.let { intKvs[key] = it }
                        }
                        else -> {
                            if (!skipValue(cursor, valueType)) break
                        }
                    }
                }

                val resolvedArch = architecture?.takeIf { it.isNotBlank() } ?: "qwen2"
                val contextLength = (
                    intKvs["$resolvedArch.context_length"]
                        ?: intKvs["general.context_length"]
                        ?: intKvs.entries.firstOrNull { it.key.endsWith(".context_length") }?.value
                        ?: 4096
                    ).coerceAtLeast(256)
                val blockCount = (
                    intKvs["$resolvedArch.block_count"]
                        ?: intKvs.entries.firstOrNull { it.key.endsWith(".block_count") }?.value
                        ?: 28
                    ).coerceIn(1, 160)
                val embeddingLength = (
                    intKvs["$resolvedArch.embedding_length"]
                        ?: intKvs.entries.firstOrNull { it.key.endsWith(".embedding_length") }?.value
                        ?: 1536
                    ).coerceAtLeast(256)
                val headCount = (
                    intKvs["$resolvedArch.attention.head_count"]
                        ?: intKvs.entries.firstOrNull { it.key.endsWith(".attention.head_count") }?.value
                        ?: 12
                    ).coerceAtLeast(1)
                val headCountKv = (
                    intKvs["$resolvedArch.attention.head_count_kv"]
                        ?: intKvs.entries.firstOrNull { it.key.endsWith(".attention.head_count_kv") }?.value
                        ?: (headCount / 2).coerceAtLeast(1)
                    ).coerceIn(1, headCount)
                val headDim = (
                    intKvs["$resolvedArch.attention.key_length"]
                        ?: intKvs.entries.firstOrNull { it.key.endsWith(".attention.key_length") }?.value
                        ?: (embeddingLength / headCount)
                    ).coerceIn(64, 256)

                val quantName = resolveFileType(fileType, file.name)
                val isAbliterated = modelName.contains("abliterat", ignoreCase = true) ||
                    file.name.contains("abliterat", ignoreCase = true) ||
                    file.name.contains("uncensored", ignoreCase = true)

                val fileBytes = file.length()
                // Budget KV cache against a realistic 4096-token on-device window so models declaring
                // 32k-131k training context don't falsely claim tens of GBs on mobile.
                val effectiveCtx = contextLength.coerceIn(512, 4096)
                val kvCacheBytes = 2L * blockCount * headCountKv * headDim * effectiveCtx * 2L
                val minWorkingOverhead = 128L * 1024L * 1024L
                val recommendedScratchBytes = 256L * 1024L * 1024L

                val estimatedKvRamMb = (kvCacheBytes / (1024L * 1024L)).coerceAtLeast(16L)
                val minRamMb = ((fileBytes + kvCacheBytes + minWorkingOverhead) / (1024L * 1024L)).coerceAtLeast(64L)
                val recommendedRamMb = ((fileBytes + kvCacheBytes + recommendedScratchBytes) / (1024L * 1024L))
                    .coerceAtLeast(minRamMb)
                val estimatedRamBytes = fileBytes + kvCacheBytes + recommendedScratchBytes

                val templateFamily = ChatTemplateFamily.infer(
                    architecture = resolvedArch,
                    chatTemplate = chatTemplate,
                    modelName = "$modelName ${file.name}",
                )

                val looksMultimodal = resolvedArch.contains("vl", ignoreCase = true) ||
                    modelName.contains("vl", ignoreCase = true) ||
                    file.name.contains("-vl", ignoreCase = true) ||
                    file.name.contains("_vl", ignoreCase = true)
                val visionReason = if (looksMultimodal) {
                    "Vision inputs on GGUF models require an external multimodal projector (mmproj/mtmd), which is not supported in this standalone CPU GGUF runtime."
                } else {
                    "Standalone GGUF runtime supports text prompts only."
                }

                ModelMetadata(
                    file = file,
                    name = modelName,
                    format = ModelFormat.GGUF,
                    engineType = EngineType.LLAMA_CPP,
                    architecture = resolvedArch,
                    quantization = quantName,
                    contextLength = contextLength,
                    fileSizeBytes = fileBytes,
                    estimatedRamBytes = estimatedRamBytes,
                    hasVision = false,
                    isAbliterated = isAbliterated,
                    parameterCount = estimateParameterCount(fileBytes),
                    isReadable = file.canRead(),
                    isCorrupted = false,
                    chatTemplate = chatTemplate,
                    templateFamily = templateFamily,
                    supportedModalities = setOf(Modality.TEXT),
                    visionUnsupportedReason = visionReason,
                    recommendedRamMb = recommendedRamMb,
                    minRamMb = minRamMb,
                    estimatedKvRamMb = estimatedKvRamMb,
                    compatibilityStatus = ModelCompatibilityStatus.COMPATIBLE,
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private class BufferedCursor(
        private val raf: RandomAccessFile,
        private val fileLength: Long,
    ) {
        private val buf = ByteArray(64 * 1024)
        private var pos = 0
        private var limit = 0
        private var fileOffset = 0L

        val position: Long get() = fileOffset - (limit - pos)
        val isAtEnd: Boolean get() = position >= fileLength

        private fun refill(): Boolean {
            if (fileOffset >= fileLength) return false
            val n = raf.read(buf)
            if (n <= 0) return false
            pos = 0
            limit = n
            fileOffset += n
            return true
        }

        fun readByte(): Int? {
            if (pos >= limit && !refill()) return null
            return buf[pos++].toInt() and 0xFF
        }

        fun readFully(dst: ByteArray): Boolean {
            var written = 0
            while (written < dst.size) {
                if (pos >= limit && !refill()) return false
                val avail = limit - pos
                val toCopy = minOf(avail, dst.size - written)
                System.arraycopy(buf, pos, dst, written, toCopy)
                pos += toCopy
                written += toCopy
            }
            return true
        }

        fun readInt16(): Short? {
            val b = ByteArray(2)
            if (!readFully(b)) return null
            return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).short
        }

        fun readInt32(): Int? {
            val b = ByteArray(4)
            if (!readFully(b)) return null
            return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).int
        }

        fun readInt64(): Long? {
            val b = ByteArray(8)
            if (!readFully(b)) return null
            return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).long
        }

        fun skip(bytes: Long): Boolean {
            if (bytes < 0L) return false
            val target = position + bytes
            if (target < 0L || target > fileLength) return false
            val avail = (limit - pos).toLong()
            if (bytes <= avail) {
                pos += bytes.toInt()
                return true
            }
            raf.seek(target)
            fileOffset = target
            pos = 0
            limit = 0
            return true
        }
    }

    private fun readString(cursor: BufferedCursor, maxBytes: Int = 65_536): String? {
        val len = cursor.readInt64() ?: return null
        if (len < 0L || len > maxBytes.toLong()) return null
        val intLen = len.toInt()
        val strBytes = ByteArray(intLen)
        if (!cursor.readFully(strBytes)) return null
        return String(strBytes, Charsets.UTF_8)
    }

    private fun readStringValue(
        cursor: BufferedCursor,
        valueType: Int,
        maxBytes: Int = 65_536,
    ): String? {
        if (valueType != TYPE_STRING) {
            skipValue(cursor, valueType)
            return null
        }
        val len = cursor.readInt64() ?: return null
        if (len < 0L) return null
        if (len > maxBytes.toLong()) {
            cursor.skip(len)
            return null
        }
        val bytes = ByteArray(len.toInt())
        if (!cursor.readFully(bytes)) return null
        return String(bytes, Charsets.UTF_8)
    }

    private fun readIntValue(cursor: BufferedCursor, valueType: Int): Int? {
        return when (valueType) {
            TYPE_UINT8, TYPE_INT8 -> cursor.readByte()
            TYPE_UINT16 -> cursor.readInt16()?.toInt()?.and(0xFFFF)
            TYPE_INT16 -> cursor.readInt16()?.toInt()
            TYPE_UINT32, TYPE_INT32 -> cursor.readInt32()
            TYPE_UINT64, TYPE_INT64 -> cursor.readInt64()?.toInt()
            else -> {
                skipValue(cursor, valueType)
                null
            }
        }
    }

    private fun fixedScalarSize(valueType: Int): Long? = when (valueType) {
        TYPE_UINT8, TYPE_INT8, TYPE_BOOL -> 1L
        TYPE_UINT16, TYPE_INT16 -> 2L
        TYPE_UINT32, TYPE_INT32, TYPE_FLOAT32 -> 4L
        TYPE_UINT64, TYPE_INT64, TYPE_FLOAT64 -> 8L
        else -> null
    }

    private fun skipValue(cursor: BufferedCursor, valueType: Int): Boolean {
        val scalarBytes = fixedScalarSize(valueType)
        if (scalarBytes != null) {
            return cursor.skip(scalarBytes)
        }
        return when (valueType) {
            TYPE_STRING -> {
                val len = cursor.readInt64() ?: return false
                cursor.skip(len)
            }
            TYPE_ARRAY -> {
                val arrItemType = cursor.readInt32() ?: return false
                val count = cursor.readInt64() ?: return false
                if (count < 0L) return false
                val elemSize = fixedScalarSize(arrItemType)
                if (elemSize != null) {
                    if (count > Long.MAX_VALUE / elemSize) return false
                    return cursor.skip(count * elemSize)
                }
                for (i in 0L until count) {
                    if (cursor.isAtEnd || !skipValue(cursor, arrItemType)) return false
                }
                true
            }
            else -> false
        }
    }

    private fun resolveFileType(fileType: Int, fileName: String): String {
        return when (fileType) {
            0 -> "F32"
            1 -> "F16"
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
            19 -> "IQ2_XXS"
            20 -> "IQ2_XS"
            21 -> "Q2_K_S"
            22 -> "IQ3_XS"
            23 -> "IQ3_XXS"
            24 -> "IQ1_S"
            25 -> "IQ4_NL"
            26 -> "IQ3_S"
            27 -> "IQ3_M"
            28 -> "IQ2_S"
            29 -> "IQ2_M"
            30 -> "IQ4_XS"
            else -> {
                val lower = fileName.lowercase()
                when {
                    lower.contains("q4_k_m") -> "Q4_K_M"
                    lower.contains("q4_k_s") -> "Q4_K_S"
                    lower.contains("q5_k_m") -> "Q5_K_M"
                    lower.contains("q6_k") -> "Q6_K"
                    lower.contains("iq4_xs") -> "IQ4_XS"
                    lower.contains("q8_0") -> "Q8_0"
                    lower.contains("q4_0") -> "Q4_0"
                    else -> "Quantized"
                }
            }
        }
    }

    private fun estimateParameterCount(fileSizeBytes: Long): String {
        val mb = fileSizeBytes / (1024L * 1024L)
        return when {
            mb < 600L -> "0.6B"
            mb in 600L..1400L -> "1.5B - 1.7B"
            mb in 1401L..2800L -> "3B - 4B"
            mb in 2801L..5500L -> "7B - 8B"
            else -> "> 8B"
        }
    }
}
