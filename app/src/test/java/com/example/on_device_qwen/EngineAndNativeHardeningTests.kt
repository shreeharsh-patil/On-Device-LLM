package com.example.on_device_qwen

import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import com.example.on_device_qwen.data.preferences.PerformanceMode
import com.example.on_device_qwen.domain.context.PromptContextBuilder
import com.example.on_device_qwen.domain.context.PromptValidation
import com.example.on_device_qwen.domain.engine.GgufMetadataParser
import com.example.on_device_qwen.domain.engine.InferenceEngineFactory
import com.example.on_device_qwen.domain.engine.LiteRTInferenceEngine
import com.example.on_device_qwen.domain.engine.LlamaCppInferenceEngine
import com.example.on_device_qwen.domain.performance.DeviceMemoryStatus
import com.example.on_device_qwen.domain.performance.DeviceThermalState
import com.example.on_device_qwen.domain.performance.MemoryMonitor
import com.example.on_device_qwen.domain.performance.ThermalPerformanceManager
import com.example.on_device_qwen.domain.tts.TtsManager
import com.example.on_device_qwen.model.BackendType
import com.example.on_device_qwen.model.ChatMessage
import com.example.on_device_qwen.model.ChatTemplateFamily
import com.example.on_device_qwen.model.EngineType
import com.example.on_device_qwen.model.GenerationConfig
import com.example.on_device_qwen.model.Modality
import com.example.on_device_qwen.model.ModelCompatibilityStatus
import com.example.on_device_qwen.model.ModelErrorCode
import com.example.on_device_qwen.model.ModelFormat
import com.example.on_device_qwen.model.ModelMetadata
import com.example.on_device_qwen.model.RetrievedMemoryContext
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Message as LiteRtMessage
import com.google.ai.edge.litertlm.Role
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EngineAndNativeHardeningTests {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun appContext(): Application = ApplicationProvider.getApplicationContext()

    // --- Helper to write authentic little-endian GGUF v3 binary files ---
    private class GgufWriter {
        private val out = ByteArrayOutputStream()

        fun writeInt32(v: Int) {
            val b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()
            out.write(b)
        }

        fun writeInt64(v: Long) {
            val b = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(v).array()
            out.write(b)
        }

        fun writeFloat32(v: Float) {
            val b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(v).array()
            out.write(b)
        }

        fun writeStr(s: String) {
            val bytes = s.toByteArray(Charsets.UTF_8)
            writeInt64(bytes.size.toLong())
            out.write(bytes)
        }

        fun kvString(key: String, value: String) {
            writeStr(key)
            writeInt32(8) // TYPE_STRING
            writeStr(value)
        }

        fun kvUInt32(key: String, value: Int) {
            writeStr(key)
            writeInt32(4) // TYPE_UINT32
            writeInt32(value)
        }

        fun kvStringArray(key: String, items: List<String>) {
            writeStr(key)
            writeInt32(9) // TYPE_ARRAY
            writeInt32(8) // TYPE_STRING
            writeInt64(items.size.toLong())
            items.forEach { writeStr(it) }
        }

        fun kvFloatArray(key: String, count: Int) {
            writeStr(key)
            writeInt32(9) // TYPE_ARRAY
            writeInt32(6) // TYPE_FLOAT32
            writeInt64(count.toLong())
            repeat(count) { writeFloat32(0.1f) }
        }

        fun writeToFile(file: File, kvCount: Long, paddingBytes: Int = 0) {
            val body = out.toByteArray()
            DataOutputStream(file.outputStream()).use { dos ->
                val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
                header.putInt(0x46554747) // GGUF
                header.putInt(3)          // version 3
                header.putLong(0L)        // tensor_count
                header.putLong(kvCount)   // kv_count
                dos.write(header.array())
                dos.write(body)
                if (paddingBytes > 0) dos.write(ByteArray(paddingBytes))
            }
        }
    }

    @Test
    fun ggufParserSkipsLargeTokenArraysAndExtractsTrailingMetadataAndRealisticKvRam() {
        val file = File(tempFolder.root, "qwen3-4b-abliterated-q4_k_m.gguf")
        val writer = GgufWriter()
        // Place huge >500-element arrays BEFORE key metadata like tokenizer.chat_template
        writer.kvString("general.architecture", "qwen2")
        writer.kvString("general.name", "Qwen3-4B-Abliterated")
        writer.kvUInt32("general.file_type", 15) // Q4_K_M
        writer.kvStringArray("tokenizer.ggml.tokens", (0 until 1500).map { "tok_$it" })
        writer.kvFloatArray("tokenizer.ggml.scores", 1500)
        writer.kvUInt32("qwen2.context_length", 131_072) // 128k training context
        writer.kvUInt32("qwen2.block_count", 36)
        writer.kvUInt32("qwen2.embedding_length", 2560)
        writer.kvUInt32("qwen2.attention.head_count", 20)
        writer.kvUInt32("qwen2.attention.head_count_kv", 4)
        writer.kvString("tokenizer.chat_template", "{% for m in messages %}<|im_start|>{{m.role}}\n{{m.content}}<|im_end|>{% endfor %}")
        writer.writeToFile(file, kvCount = 11L, paddingBytes = 64)

        val meta = GgufMetadataParser.parse(file)
        assertNotNull("Valid GGUF with >500-element arrays must parse cleanly", meta)
        meta!!
        assertEquals("qwen2", meta.architecture)
        assertEquals("Q4_K_M", meta.quantization)
        assertEquals(131_072, meta.contextLength)
        assertNotNull("chat_template after large token array must still be read", meta.chatTemplate)
        assertEquals(ChatTemplateFamily.CHATML, meta.templateFamily)
        assertTrue(meta.isAbliterated)
        assertFalse("Standalone GGUF must never claim vision support", meta.hasVision)
        assertEquals(setOf(Modality.TEXT), meta.supportedModalities)

        // Realistic mobile KV estimate must NOT blow up into 100+ GB
        assertTrue("KV cache estimate (${meta.estimatedKvRamMb}MB) should stay well under 2GB", meta.estimatedKvRamMb in 16L..1500L)
        assertTrue(meta.recommendedRamMb >= meta.minRamMb)
    }

    @Test
    fun ggufParserHonestVisionFalseEvenOnQwen2VlFilenameAndRejectsCorruption() {
        val vlFile = File(tempFolder.root, "qwen2-vl-7b-instruct.gguf")
        val writer = GgufWriter()
        writer.kvString("general.architecture", "qwen2vl")
        writer.kvString("general.name", "Qwen2-VL")
        writer.writeToFile(vlFile, kvCount = 2L)

        val vlMeta = GgufMetadataParser.parse(vlFile)
        assertNotNull(vlMeta)
        assertFalse("GGUF qwen2vl without mmproj must report hasVision=false", vlMeta!!.hasVision)
        assertEquals(setOf(Modality.TEXT), vlMeta.supportedModalities)
        assertTrue(vlMeta.visionUnsupportedReason!!.contains("mmproj", ignoreCase = true))

        val corrupt = File(tempFolder.root, "corrupt.gguf").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }
        assertNull("Corrupt GGUF header must return null instead of fake metadata", GgufMetadataParser.parse(corrupt))
    }

    @Test
    fun promptContextBuilderAdaptsLlama3GemmaPhiDeepSeekAndReservesOutputBudget() {
        val configs = listOf(
            ChatTemplateFamily.LLAMA3 to "<|start_header_id|>assistant<|end_header_id|>\n\n",
            ChatTemplateFamily.GEMMA to "<start_of_turn>model\n",
            ChatTemplateFamily.PHI to "<|assistant|>\n",
            ChatTemplateFamily.DEEPSEEK to "<｜Assistant｜>",
            ChatTemplateFamily.CHATML to "<|im_start|>assistant\n",
        )
        val genConfig = GenerationConfig(contextLength = 2048, maxOutputTokens = 512)
        for ((family, suffix) in configs) {
            val meta = ModelMetadata(
                file = File(tempFolder.root, "${family.name}.gguf"),
                name = family.name,
                format = ModelFormat.GGUF,
                engineType = EngineType.LLAMA_CPP,
                templateFamily = family,
            )
            val built = PromptContextBuilder.buildPromptForModel(
                historyMessages = listOf(
                    ChatMessage(1L, "c", "Hi", isUser = true),
                    ChatMessage(2L, "c", "Hello!", isUser = false),
                    ChatMessage(3L, "c", "Error msg", isUser = false, isError = true),
                ),
                latestUserPrompt = "Tell me about Kotlin",
                memoryContext = RetrievedMemoryContext(),
                config = genConfig,
                metadata = meta,
            )
            assertEquals(family, built.templateFamily)
            assertTrue("Expected suffix $suffix for $family", built.formattedPrompt.endsWith(suffix))
            assertFalse("Error turns must be dropped", built.formattedPrompt.contains("Error msg"))
            assertEquals(512, built.reservedOutputTokens)
        }
    }

    @Test
    fun liteRtHelpersUnwrapAccidentalChatMlAndExtractThoughtChannels() {
        val chatMlWrapped = """
            <|im_start|>system
            Duplicate system prompt<|im_end|>
            <|im_start|>user
            What is 2 + 2?<|im_end|>
            <|im_start|>assistant
        """.trimIndent()
        assertEquals("What is 2 + 2?", LiteRTInferenceEngine.extractPlainUserTurn(chatMlWrapped))
        assertEquals("Plain user turn", LiteRTInferenceEngine.extractPlainUserTurn("Plain user turn"))

        val msg = LiteRtMessage.model("4")
        val (text, thought) = LiteRTInferenceEngine.extractMessageParts(msg)
        assertEquals("4", text)
        assertEquals("", thought)

        val historyMsgs = LiteRTInferenceEngine.toLiteRtInitialMessages(
            history = listOf(
                ChatMessage(1L, "c", "hi", isUser = true),
                ChatMessage(2L, "c", "<think>secret</think>hello!", isUser = false),
                ChatMessage(3L, "c", "broken", isUser = false, isError = true),
            ),
            maxHistoryTokens = 512,
        )
        assertEquals(2, historyMsgs.size)
        assertEquals(Role.USER, historyMsgs[0].role)
        assertEquals("hello!", historyMsgs[1].toString())
    }

    @Test
    fun enginesRejectMissingOrCorruptModelsAndKeepHonestCapabilities() = runTest {
        val ctx = appContext()
        val llama = LlamaCppInferenceEngine(ctx)
        val missingRes = llama.loadModelWithResult(
            modelPath = File(tempFolder.root, "missing.gguf").absolutePath,
            backend = BackendType.GPU,
            config = GenerationConfig(),
        )
        assertFalse(missingRes.success)
        assertFalse(llama.isLoaded)
        assertEquals(ModelErrorCode.MODEL_FILE_NOT_FOUND, missingRes.errorCode)
        assertEquals(BackendType.GPU, missingRes.backendInfo.requested)
        assertEquals(BackendType.CPU, missingRes.backendInfo.active)
        assertFalse(llama.getCapabilities().supportsGpu)
        assertFalse(llama.getCapabilities().supportsVision)

        val liteRt = LiteRTInferenceEngine(ctx)
        val tinyLiteRt = File(tempFolder.root, "broken.litertlm").apply { writeBytes(ByteArray(128)) }
        val liteRtRes = liteRt.loadModelWithResult(
            modelPath = tinyLiteRt.absolutePath,
            backend = BackendType.NPU,
            config = GenerationConfig(),
        )
        assertFalse(liteRtRes.success)
        assertEquals(ModelErrorCode.MODEL_INITIALIZATION_FAILED, liteRtRes.errorCode)
        assertNull("LiteRT does not support arbitrary string tokenization", liteRt.countTokens("hello"))
        assertFalse(liteRt.getCapabilities().supportsNativeTokenCount)

        // Ensure factory routes by extension & header magic properly
        val factory = InferenceEngineFactory(ctx)
        val ggufByHeader = File(tempFolder.root, "model_no_ext").apply {
            writeBytes(byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte(), 0, 0, 0, 0))
        }
        assertTrue(factory.getEngineForModel(ggufByHeader) is LlamaCppInferenceEngine)
    }

    @Test
    fun promptValidationAuditCoversEngineSpecificLimitsWithoutFalseWarnings() {
        val liteRtCpuConfig = GenerationConfig(backend = BackendType.CPU, minP = 0.05f)
        val notes = PromptValidation.auditRuntimeSupport(liteRtCpuConfig, EngineType.LITERT)
        assertTrue("Default config on LiteRT CPU should have zero mismatch warnings: $notes", notes.isEmpty())

        val ggufGpuRequested = GenerationConfig(backend = BackendType.GPU, maxOutputTokens = 6000)
        val ggufNotes = PromptValidation.auditRuntimeSupport(ggufGpuRequested, EngineType.LLAMA_CPP)
        assertTrue(ggufNotes.any { it.invalidKey == "backend" })
        assertTrue(ggufNotes.any { it.invalidKey == "max_output_tokens" })
    }

    @Test
    fun memoryMonitorEvaluatesCompatibilityAndUnknownTelemetrySafely() {
        val meta = ModelMetadata(
            file = File(tempFolder.root, "m.gguf"),
            name = "Test 3B",
            format = ModelFormat.GGUF,
            engineType = EngineType.LLAMA_CPP,
            minRamMb = 2200L,
            recommendedRamMb = 2600L,
            estimatedRamBytes = 2600L * 1024L * 1024L,
        )

        val unknown = DeviceMemoryStatus(0L, 0L, 0L, false, 0L)
        assertEquals(ModelCompatibilityStatus.COMPATIBLE, MemoryMonitor.evaluateCompatibility(meta, unknown).first)

        val plenty = DeviceMemoryStatus(8192L, 4500L, 3692L, false, 512L)
        assertEquals(ModelCompatibilityStatus.COMPATIBLE, MemoryMonitor.evaluateCompatibility(meta, plenty).first)

        val tight = DeviceMemoryStatus(4096L, 2400L, 1696L, false, 400L)
        assertEquals(ModelCompatibilityStatus.TIGHT_RAM, MemoryMonitor.evaluateCompatibility(meta, tight).first)

        val tooSmall = DeviceMemoryStatus(3072L, 1200L, 1872L, false, 300L)
        assertEquals(ModelCompatibilityStatus.INSUFFICIENT_RAM, MemoryMonitor.evaluateCompatibility(meta, tooSmall).first)
    }

    @Test
    fun thermalPerformanceManagerPausesOnCriticalAndCleansUpIdempotently() {
        val mgr = ThermalPerformanceManager(appContext())
        assertFalse(mgr.shouldPauseGeneration.value)

        mgr.setThermalStateForTest(DeviceThermalState.SEVERE)
        assertFalse(mgr.shouldPauseGeneration.value)
        assertTrue(mgr.getAdaptiveThreadCount(PerformanceMode.PERFORMANCE, 6) <= 2)
        assertTrue(mgr.recommendedThrottleDelayMs() >= 25L)

        mgr.setThermalStateForTest(DeviceThermalState.CRITICAL)
        assertTrue("CRITICAL thermal state must trigger shouldPauseGeneration", mgr.shouldPauseGeneration.value)
        assertEquals(1, mgr.getAdaptiveThreadCount(PerformanceMode.PERFORMANCE, 6))

        mgr.cleanup()
        mgr.cleanup() // Idempotent double-cleanup must not throw
    }

    @Test
    fun ttsManagerSanitizesMarkdownSplitsLongInputAndQueuesBeforeInit() {
        val raw = """
            <think>Hidden inner monologue</think>
            ## Title Here
            > A wise quote
            | Col1 | Col2 |
            | --- | --- |
            | Alpha | Beta |
            See [Docs](https://example.com) and inline `code()` plus $$ E = mc^2 $$.
            ```kotlin
            println("hello")
            ```
        """.trimIndent()

        val cleaned = TtsManager.cleanMarkdownForSpeech(raw)
        assertFalse(cleaned.contains("Hidden inner monologue"))
        assertFalse(cleaned.contains("---"))
        assertFalse(cleaned.contains("https://example.com"))
        assertTrue(cleaned.contains("Title Here"))
        assertTrue(cleaned.contains("Alpha") && cleaned.contains("Beta"))
        assertTrue(cleaned.contains("Code block omitted."))

        val longText = (1..40).joinToString(" ") { "Sentence $it concludes cleanly." }
        val chunks = TtsManager.splitIntoSpeechChunks(longText, maxChunkChars = 250)
        assertTrue("Long text should split into multiple chunks", chunks.size > 1)
        assertTrue("Every chunk must honor maxChunkChars", chunks.all { it.length <= 250 })

        val ttsManager = TtsManager(appContext())
        // Queue utterance while not initialized yet
        ttsManager.speak("Queued before ready. More words here.")
        ttsManager.onInit(TextToSpeech.SUCCESS)
        assertTrue("Queued utterance must speak automatically once onInit completes", ttsManager.isSpeaking.value)

        ttsManager.shutdown()
        assertFalse(ttsManager.isSpeaking.value)
    }
}
