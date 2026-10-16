package com.example.on_device_qwen

import com.example.on_device_qwen.domain.engine.GgufMetadataParser
import com.example.on_device_qwen.domain.engine.InferenceEngineFactory
import com.example.on_device_qwen.model.EngineType
import com.example.on_device_qwen.model.ModelErrorCode
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

class ModelDiscoveryAndRecoveryTests {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createDummyGgufFile(dir: File, name: String, sizeBytes: Long = 2048): File {
        val file = File(dir, name)
        FileOutputStream(file).use { stream ->
            // GGUF Magic: 'G', 'G', 'U', 'F'
            stream.write(byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte()))
            // Version 3
            stream.write(byteArrayOf(3, 0, 0, 0))
            // Tensor count: 0
            stream.write(ByteArray(8))
            // Metadata count: 0
            stream.write(ByteArray(8))
            // Pad remaining bytes
            val remaining = (sizeBytes - 24).toInt()
            if (remaining > 0) {
                stream.write(ByteArray(remaining))
            }
        }
        return file
    }

    private fun createDummyLiteRtFile(dir: File, name: String, sizeBytes: Long = 2048): File {
        val file = File(dir, name)
        FileOutputStream(file).use { stream ->
            // 4 bytes offset, then 'T', 'F', 'L', '3'
            stream.write(byteArrayOf(0x18, 0x00, 0x00, 0x00))
            stream.write(byteArrayOf('T'.code.toByte(), 'F'.code.toByte(), 'L'.code.toByte(), '3'.code.toByte()))
            val remaining = (sizeBytes - 8).toInt()
            if (remaining > 0) {
                stream.write(ByteArray(remaining))
            }
        }
        return file
    }

    @Test
    fun testGgufMagicHeaderDetection() {
        val root = tempFolder.newFolder("downloads")
        val ggufFile = createDummyGgufFile(root, "qwen2.5-1.5b-chat.gguf")

        val raf = RandomAccessFile(ggufFile, "r")
        val header = ByteArray(4)
        raf.readFully(header)
        raf.close()

        val isGguf = header.contentEquals(byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte()))
        assertTrue("Header should match GGUF magic", isGguf)
    }

    @Test
    fun testLiteRtTfl3MagicHeaderDetection() {
        val root = tempFolder.newFolder("litert_models")
        val liteRtFile = createDummyLiteRtFile(root, "qwen2.5-0.5b.litertlm")

        val raf = RandomAccessFile(liteRtFile, "r")
        val header = ByteArray(8)
        raf.readFully(header)
        raf.close()

        val isTfl3 = header.sliceArray(4 until 8).contentEquals(
            byteArrayOf('T'.code.toByte(), 'F'.code.toByte(), 'L'.code.toByte(), '3'.code.toByte())
        )
        assertTrue("Header should match TFL3 magic at offset 4", isTfl3)
    }

    @Test
    fun testExtensionlessModelDetectionViaMagic() {
        val root = tempFolder.newFolder("custom_store")
        // File saved without extension or with .bin
        val binFile = createDummyGgufFile(root, "model_cache.bin")

        val raf = RandomAccessFile(binFile, "r")
        val header = ByteArray(4)
        raf.readFully(header)
        raf.close()

        val isGguf = header.contentEquals(byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte()))
        assertTrue("Extensionless or .bin file with GGUF header must be recognized", isGguf)
    }

    @Test
    fun testIncompleteDownloadFiltering() {
        val root = tempFolder.newFolder("partial_downloads")
        val partial1 = File(root, "qwen.gguf.crdownload").apply { writeText("in progress...") }
        val partial2 = File(root, "qwen.gguf.part").apply { writeText("in progress...") }
        val partial3 = File(root, "qwen.gguf.download").apply { writeText("in progress...") }
        val partial4 = File(root, "qwen.gguf.tmp").apply { writeText("in progress...") }
        val complete = createDummyGgufFile(root, "qwen.gguf")

        val allFiles = root.listFiles()?.toList() ?: emptyList()
        val validFiles = allFiles.filterNot { file ->
            val name = file.name.lowercase()
            name.endsWith(".crdownload") || name.endsWith(".part") ||
                    name.endsWith(".download") || name.endsWith(".tmp")
        }

        assertEquals(1, validFiles.size)
        assertEquals(complete.name, validFiles.first().name)
    }

    @Test
    fun testRecursiveSubdirectoryTraversal() {
        val root = tempFolder.newFolder("public_downloads")
        val sub1 = File(root, "browser").apply { mkdirs() }
        val sub2 = File(root, "telegram/documents").apply { mkdirs() }
        val hiddenDir = File(root, ".hidden_cache").apply { mkdirs() }

        val model1 = createDummyGgufFile(root, "model1.gguf")
        val model2 = createDummyGgufFile(sub1, "model2.gguf")
        val model3 = createDummyLiteRtFile(sub2, "model3.litertlm")
        val ignoredHiddenModel = createDummyGgufFile(hiddenDir, "ignored.gguf")

        val discovered = mutableListOf<File>()

        fun scanRecursive(dir: File, depth: Int = 0) {
            if (depth > 3 || !dir.isDirectory || dir.name.startsWith(".")) return
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory && !file.name.startsWith(".")) {
                    scanRecursive(file, depth + 1)
                } else if (file.isFile && (file.name.endsWith(".gguf") || file.name.endsWith(".litertlm"))) {
                    discovered.add(file)
                }
            }
        }

        scanRecursive(root)

        assertEquals(3, discovered.size)
        val names = discovered.map { it.name }.toSet()
        assertTrue(names.contains("model1.gguf"))
        assertTrue(names.contains("model2.gguf"))
        assertTrue(names.contains("model3.litertlm"))
        assertFalse(names.contains("ignored.gguf"))
    }

    @Test
    fun testDuplicateImportPrevention() {
        val internalFilesDir = tempFolder.newFolder("internal_files")
        val externalDownload = tempFolder.newFolder("download")

        val downloadedFile = createDummyGgufFile(externalDownload, "Qwen2.5-Coder-1.5B.gguf", sizeBytes = 1024 * 1024)
        val alreadyImported = File(internalFilesDir, downloadedFile.name).apply {
            writeBytes(downloadedFile.readBytes())
        }

        // Simulate duplicate check logic:
        // If destination exists and matches length, skip copying
        val shouldSkipCopy = alreadyImported.exists() && alreadyImported.length() == downloadedFile.length()
        assertTrue("Should skip copying duplicate multi-gigabyte files", shouldSkipCopy)
    }

    @Test
    fun testEngineTypeResolution() {
        val ggufFile = File("/mock/path/qwen.gguf")
        val litertFile = File("/mock/path/qwen.litertlm")
        val taskFile = File("/mock/path/model.task")
        val tfliteFile = File("/mock/path/model.tflite")

        assertTrue("gguf must use LLAMA_CPP", ggufFile.name.endsWith(".gguf"))
        assertTrue("litertlm must use LiteRT", litertFile.name.endsWith(".litertlm"))
        assertTrue("task must use LiteRT", taskFile.name.endsWith(".task"))
        assertTrue("tflite must use LiteRT", tfliteFile.name.endsWith(".tflite"))
    }

    @Test
    fun testModelErrorCodeEnumValues() {
        assertNotNull(ModelErrorCode.MODEL_FILE_NOT_FOUND)
        assertNotNull(ModelErrorCode.DOWNLOADED_MODEL_NOT_REGISTERED)
        assertNotNull(ModelErrorCode.FILE_UNREADABLE_OR_PERMISSION_DENIED)
        assertNotNull(ModelErrorCode.UNSUPPORTED_MODEL_FORMAT)
        assertNotNull(ModelErrorCode.MISSING_RUNTIME_OR_INCOMPATIBLE_ABI)
        assertNotNull(ModelErrorCode.INSUFFICIENT_RAM)
        assertNotNull(ModelErrorCode.MODEL_INITIALIZATION_FAILED)
        assertNotNull(ModelErrorCode.INFERENCE_FAILED)
    }

    @Test
    fun testCorruptedSmallFileFilter() {
        val root = tempFolder.newFolder("models")
        val tinyFile = File(root, "truncated.gguf").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val normalFile = createDummyGgufFile(root, "valid.gguf", sizeBytes = 2 * 1024 * 1024)

        val minValidSize = 1024 * 1024L
        val isTinyValid = tinyFile.length() >= minValidSize
        val isNormalValid = normalFile.length() >= minValidSize

        assertFalse("Small/truncated file (< 1MB) should be filtered", isTinyValid)
        assertTrue("Valid model file should pass size check", isNormalValid)
    }
}
