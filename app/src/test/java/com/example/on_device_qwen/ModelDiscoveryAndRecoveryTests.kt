package com.example.on_device_qwen

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.on_device_qwen.domain.modelmanager.ModelRepository
import com.example.on_device_qwen.model.EngineType
import com.example.on_device_qwen.model.ModelCompatibilityStatus
import com.example.on_device_qwen.model.ModelErrorCode
import com.example.on_device_qwen.model.ModelFormat
import com.example.on_device_qwen.model.ModelMetadata
import com.example.on_device_qwen.model.Modality
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ModelDiscoveryAndRecoveryTests {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var repository: ModelRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = ModelRepository(context)
    }

    @After
    fun tearDown() {
        File(context.filesDir, "models").deleteRecursively()
        context.filesDir.listFiles()?.forEach {
            if (it.isFile) it.delete()
        }
    }

    private fun createDummyGgufFile(
        dir: File,
        name: String,
        sizeBytes: Long = 2048,
        version: Int = 3,
        tensorCount: Long = 1L,
    ): File {
        val file = File(dir, name)
        FileOutputStream(file).use { stream ->
            val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).apply {
                put('G'.code.toByte())
                put('G'.code.toByte())
                put('U'.code.toByte())
                put('F'.code.toByte())
                putInt(version)
                putLong(tensorCount)
                putLong(0L)
            }.array()
            stream.write(header)
            val remaining = (sizeBytes - header.size).toInt()
            if (remaining > 0) {
                stream.write(ByteArray(remaining))
            }
        }
        return file
    }

    private fun createDummyLiteRtLmFile(dir: File, name: String, sizeBytes: Long = 2048): File {
        val file = File(dir, name)
        FileOutputStream(file).use { stream ->
            stream.write("LITERTLM".toByteArray(Charsets.US_ASCII))
            val remaining = (sizeBytes - 8).toInt()
            if (remaining > 0) {
                stream.write(ByteArray(remaining))
            }
        }
        return file
    }

    private fun createDummyTfl3File(dir: File, name: String, sizeBytes: Long = 2048): File {
        val file = File(dir, name)
        FileOutputStream(file).use { stream ->
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
        val root = tempFolder.newFolder("models")
        val ggufFile = createDummyGgufFile(root, "qwen2.5-1.5b-chat.gguf")
        assertTrue("Header should match GGUF magic", repository.isGgufBinary(ggufFile))
    }

    @Test
    fun testLiteRtAndTfl3MagicHeaderDetection() {
        val root = tempFolder.newFolder("litert_models")
        val liteRtLmFile = createDummyLiteRtLmFile(root, "qwen2.5-0.5b.litertlm")
        val tfliteFile = createDummyTfl3File(root, "gemma-1b.tflite")

        assertTrue("LITERTLM header must be detected", repository.isLiteRtBinary(liteRtLmFile))
        assertTrue("TFL3 header must be detected", repository.isLiteRtBinary(tfliteFile))
    }

    @Test
    fun testExtensionlessModelDetectionViaMagic() {
        val root = tempFolder.newFolder("custom_store")
        val binFile = createDummyGgufFile(root, "model_cache.bin")
        assertTrue("Extensionless or .bin file with GGUF header must be recognized", repository.isGgufBinary(binFile))
        assertTrue("Candidate check should accept valid magic header", repository.isCandidateModelFile(binFile))
    }

    @Test
    fun testIncompleteDownloadFiltering() {
        val root = tempFolder.newFolder("partial_downloads")
        val partial1 = File(root, "qwen.gguf.crdownload").apply { writeBytes(ByteArray(1024)) }
        val partial2 = File(root, "qwen.gguf.part").apply { writeBytes(ByteArray(1024)) }
        val partial3 = File(root, "qwen.gguf.download").apply { writeBytes(ByteArray(1024)) }
        val partial4 = File(root, ".qwen.gguf.tmp").apply { writeBytes(ByteArray(1024)) }
        val complete = createDummyGgufFile(root, "qwen.gguf")

        assertFalse(repository.isCandidateModelFile(partial1))
        assertFalse(repository.isCandidateModelFile(partial2))
        assertFalse(repository.isCandidateModelFile(partial3))
        assertFalse(repository.isCandidateModelFile(partial4))
        assertTrue(repository.isCandidateModelFile(complete))
    }

    @Test
    fun testCorruptHeadersFlaggedInsteadOfFabricated() {
        val root = tempFolder.newFolder("corrupt_models")
        val badGguf = File(root, "bad_header.gguf").apply {
            writeBytes("NOT_A_VALID_GGUF_MODEL_FILE_1234567890".toByteArray())
        }
        val badVersionGguf = createDummyGgufFile(root, "bad_ver.gguf", version = 99)
        val badLiteRt = File(root, "html_404.litertlm").apply {
            writeText("<html><body>404 Not Found</body></html>")
        }

        val badGgufMeta = repository.parseMetadata(badGguf)
        val badVersionMeta = repository.parseMetadata(badVersionGguf)
        val badLiteRtMeta = repository.parseMetadata(badLiteRt)

        assertNotNull(badGgufMeta)
        assertTrue(badGgufMeta!!.isCorrupted)
        assertEquals(ModelCompatibilityStatus.CORRUPTED, badGgufMeta.compatibilityStatus)

        assertNotNull(badVersionMeta)
        assertTrue(badVersionMeta!!.isCorrupted)
        assertEquals(ModelCompatibilityStatus.CORRUPTED, badVersionMeta.compatibilityStatus)

        assertNotNull(badLiteRtMeta)
        assertTrue(badLiteRtMeta!!.isCorrupted)
        assertEquals(ModelCompatibilityStatus.CORRUPTED, badLiteRtMeta.compatibilityStatus)
    }

    @Test
    fun testLiteRtMetadataDoesNotInferVisionFromFilename() {
        val root = tempFolder.newFolder("vl_name_test")
        val fakeVlModel = createDummyLiteRtLmFile(root, "qwen2-vl-vision-1.5b.litertlm")
        val meta = repository.parseMetadata(fakeVlModel)

        assertNotNull(meta)
        assertFalse(meta!!.isCorrupted)
        assertFalse("Must not guess hasVision=true solely from filename", meta.hasVision)
        assertEquals(setOf(Modality.TEXT), meta.supportedModalities)
    }

    @Test
    fun testEvaluateDeviceCompatibilityClampsLargeContextEstimate() {
        val modelFile = File(tempFolder.root, "qwen2.5-1.5b.gguf")
        val metadata = ModelMetadata(
            file = modelFile,
            name = "Qwen 2.5 1.5B",
            format = ModelFormat.GGUF,
            engineType = EngineType.LLAMA_CPP,
            contextLength = 131072, // 128K metadata context that previously produced ~114 GB KV estimates
            fileSizeBytes = 1000L * 1024L * 1024L, // ~1000 MB
        )

        val compatible = repository.evaluateDeviceCompatibility(
            metadata = metadata,
            availRamMb = 4096L,
            totalRamMb = 8192L,
        )
        assertEquals(ModelCompatibilityStatus.COMPATIBLE, compatible.compatibilityStatus)
        assertTrue(
            "Clamped KV estimate must remain bounded for mobile instead of 100+ GB",
            compatible.estimatedKvRamMb in 48L..768L,
        )

        val tight = repository.evaluateDeviceCompatibility(
            metadata = metadata,
            availRamMb = 1400L,
            totalRamMb = 4096L,
        )
        assertEquals(ModelCompatibilityStatus.TIGHT_RAM, tight.compatibilityStatus)

        val insufficient = repository.evaluateDeviceCompatibility(
            metadata = metadata,
            availRamMb = 800L,
            totalRamMb = 2048L,
        )
        assertEquals(ModelCompatibilityStatus.INSUFFICIENT_RAM, insufficient.compatibilityStatus)
    }

    @Test
    fun testComputeSha256MatchesExpectedDigest() = runBlocking {
        val root = tempFolder.newFolder("hash_test")
        val file = createDummyGgufFile(root, "hashable.gguf", sizeBytes = 4096)
        val actual = repository.computeSha256(file)
        val expected = MessageDigest.getInstance("SHA-256")
            .digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }

        assertNotNull(actual)
        assertEquals(64, actual!!.length)
        assertEquals(expected, actual)
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
}
