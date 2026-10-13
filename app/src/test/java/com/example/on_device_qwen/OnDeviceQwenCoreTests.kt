package com.example.on_device_qwen

import com.example.on_device_qwen.data.preferences.PerformanceMode
import com.example.on_device_qwen.domain.engine.GgufMetadataParser
import com.example.on_device_qwen.domain.engine.InferenceEngineFactory
import com.example.on_device_qwen.model.BackendType
import com.example.on_device_qwen.model.EngineType
import com.example.on_device_qwen.ui.components.MarkdownBlock
import com.example.on_device_qwen.ui.components.parseMarkdownBlocks
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

class OnDeviceQwenCoreTests {

    @Test
    fun testMarkdownParserHeadingsAndCode() {
        val markdown = """
            # Heading 1
            Here is some text.
            ```python
            def hello():
                print("world")
            ```
            > This is a quote
            - Item 1
            - Item 2
            ---
        """.trimIndent()

        val blocks = parseMarkdownBlocks(markdown)
        assertTrue("Blocks should not be empty", blocks.isNotEmpty())

        val heading = blocks.filterIsInstance<MarkdownBlock.Heading>().firstOrNull()
        assertNotNull("Should parse heading", heading)
        assertEquals(1, heading?.level)
        assertEquals("Heading 1", heading?.text)

        val code = blocks.filterIsInstance<MarkdownBlock.Code>().firstOrNull()
        assertNotNull("Should parse code block", code)
        assertEquals("python", code?.language)
        assertTrue(code?.code?.contains("def hello():") == true)

        val quote = blocks.filterIsInstance<MarkdownBlock.Blockquote>().firstOrNull()
        assertNotNull("Should parse blockquote", quote)
        assertEquals("This is a quote", quote?.text)

        val listItems = blocks.filterIsInstance<MarkdownBlock.ListItem>()
        assertEquals(2, listItems.size)
        assertEquals("Item 1", listItems[0].text)

        val divider = blocks.filterIsInstance<MarkdownBlock.Divider>().firstOrNull()
        assertNotNull("Should parse divider", divider)
    }

    @Test
    fun testGgufHeaderValidationWithInvalidFile() {
        // Create dummy temporary file without GGUF magic
        val tempFile = File.createTempFile("invalid_model", ".gguf").apply { deleteOnExit() }
        FileOutputStream(tempFile).use { it.write(byteArrayOf(0, 1, 2, 3, 4, 5)) }

        val metadata = GgufMetadataParser.parse(tempFile)
        assertNull("Non-GGUF file should return null metadata", metadata)
    }

    @Test
    fun testGgufValidMagicDetection() {
        // Create temporary file with GGUF magic (0x47, 0x47, 0x55, 0x46) and version 3
        val tempFile = File.createTempFile("qwen3_abliterated", ".gguf").apply { deleteOnExit() }
        FileOutputStream(tempFile).use { stream ->
            // Magic: GGUF
            stream.write(byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte()))
            // Version: 3 (little-endian: 03 00 00 00)
            stream.write(byteArrayOf(3, 0, 0, 0))
            // Tensor count: 0 (uint64)
            stream.write(ByteArray(8))
            // Metadata count: 0 (uint64)
            stream.write(ByteArray(8))
            // Extra dummy bytes
            stream.write(ByteArray(32))
        }

        val metadata = GgufMetadataParser.parse(tempFile)
        assertNotNull("Valid GGUF header should be parsed", metadata)
        assertEquals(EngineType.LLAMA_CPP, metadata?.engineType)
        assertTrue("Should detect abliterated tag from filename", metadata?.isAbliterated == true)
    }

    @Test
    fun testPerformanceModeDefaults() {
        val eco = PerformanceMode.ECO
        val balanced = PerformanceMode.BALANCED
        val perf = PerformanceMode.PERFORMANCE

        assertEquals("Eco Mode", eco.displayName)
        assertEquals("Balanced Mode", balanced.displayName)
        assertEquals("Performance Mode", perf.displayName)
    }

    @Test
    fun testBackendTypes() {
        assertEquals("CPU", BackendType.CPU.chipText)
        assertEquals("GPU", BackendType.GPU.chipText)
        assertEquals("NPU", BackendType.NPU.chipText)
    }

    @Test
    fun testMarkdownTableParsing() {
        val tableMarkdown = """
            | Feature | Supported | Backend |
            | :--- | :---: | ---: |
            | LiteRT-LM | Yes | GPU/NPU |
            | Dolphin 3.0 | Yes | llama.cpp |
        """.trimIndent()

        val blocks = parseMarkdownBlocks(tableMarkdown)
        val tableBlock = blocks.filterIsInstance<MarkdownBlock.Table>().firstOrNull()

        assertNotNull("Should parse markdown table", tableBlock)
        val data = tableBlock!!.tableData
        assertEquals(3, data.headers.size)
        assertEquals("Feature", data.headers[0])
        assertEquals("Supported", data.headers[1])
        assertEquals("Backend", data.headers[2])
        assertEquals(2, data.rows.size)
        assertEquals("LiteRT-LM", data.rows[0][0])
        assertEquals("Dolphin 3.0", data.rows[1][0])
        assertEquals(com.example.on_device_qwen.ui.components.TableAlignment.CENTER, data.alignments[1])
        assertEquals(com.example.on_device_qwen.ui.components.TableAlignment.RIGHT, data.alignments[2])
    }

    @Test
    fun testMermaidDiagramDetection() {
        val mermaidMarkdown = """
            ```mermaid
            flowchart TD
            A[User Input] --> B[Inference Engine]
            B --> C[Token Stream]
            ```
        """.trimIndent()

        val blocks = parseMarkdownBlocks(mermaidMarkdown)
        val diagramBlock = blocks.filterIsInstance<MarkdownBlock.Diagram>().firstOrNull()

        assertNotNull("Should detect Mermaid diagram block", diagramBlock)
        assertEquals("mermaid", diagramBlock?.diagramType)
        assertTrue(diagramBlock?.code?.contains("flowchart TD") == true)
        assertTrue(diagramBlock?.code?.contains("A[User Input] --> B[Inference Engine]") == true)
    }

    @Test
    fun testMathEquationBlockParsing() {
        val mathMarkdown = """
            $$
            E = mc^2 + \int_0^\infty f(x) dx
            $$
        """.trimIndent()

        val blocks = parseMarkdownBlocks(mathMarkdown)
        val mathBlock = blocks.filterIsInstance<MarkdownBlock.MathBlock>().firstOrNull()

        assertNotNull("Should parse math block equation", mathBlock)
        assertTrue(mathBlock?.equation?.contains("E = mc^2") == true)
    }

    @Test
    fun testSqlLikeWildcardEscaping() {
        val rawQuery = "100% discount on _items_"
        val escaped = rawQuery.trim()
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")

        assertEquals("100\\% discount on \\_items\\_", escaped)
    }

    @Test
    fun testRegenerateMessageFilterLogic() {
        // Simulates the bug fix in ChatViewModel.regenerateLastResponse():
        // Finding and removing specifically the last bot message, not simply currentMsgs.lastOrNull()
        val user1 = com.example.on_device_qwen.model.ChatMessage(id = 1L, conversationId = "c1", text = "Hello", isUser = true)
        val bot1 = com.example.on_device_qwen.model.ChatMessage(id = 2L, conversationId = "c1", text = "Hi there!", isUser = false)
        val user2 = com.example.on_device_qwen.model.ChatMessage(id = 3L, conversationId = "c1", text = "How are you?", isUser = true)

        val listWithTrailingUser = listOf(user1, bot1, user2)
        val lastBotMsg = listWithTrailingUser.lastOrNull { !it.isUser }
        val filtered = if (lastBotMsg != null) listWithTrailingUser.filterNot { it.id == lastBotMsg.id } else listWithTrailingUser

        assertEquals(2, filtered.size)
        assertEquals(listOf(1L, 3L), filtered.map { it.id })
    }

    @Test
    fun testReasoningTagParser() {
        val raw = "<think>\nThinking step by step...\n</think>\nHere is the answer."
        val openTag = "<think>"
        val closeTag = "</think>"
        val openIdx = raw.indexOf(openTag)
        val afterOpen = raw.substring(openIdx + openTag.length)
        val closeIdx = afterOpen.indexOf(closeTag)

        val think = afterOpen.substring(0, closeIdx).trim()
        val answer = afterOpen.substring(closeIdx + closeTag.length).trimStart('\n', '\r', ' ')

        assertEquals("Thinking step by step...", think)
        assertEquals("Here is the answer.", answer)
    }
}
