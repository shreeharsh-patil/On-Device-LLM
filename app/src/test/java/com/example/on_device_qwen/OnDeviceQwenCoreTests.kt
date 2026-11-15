package com.example.on_device_qwen

import com.example.on_device_qwen.data.preferences.PerformanceMode
import com.example.on_device_qwen.domain.engine.GgufMetadataParser
import com.example.on_device_qwen.domain.engine.InferenceEngineFactory
import com.example.on_device_qwen.model.BackendType
import com.example.on_device_qwen.model.EngineType
import com.example.on_device_qwen.ui.components.IncrementalMarkdownParser
import com.example.on_device_qwen.ui.components.MarkdownBlock
import com.example.on_device_qwen.ui.components.NodeShape
import com.example.on_device_qwen.ui.components.ParsedMermaidDiagram
import com.example.on_device_qwen.ui.components.assignFlowchartLayers
import com.example.on_device_qwen.ui.components.formatLatexSymbols
import com.example.on_device_qwen.ui.components.parseMarkdownBlocks
import com.example.on_device_qwen.ui.components.parseMermaidDiagram
import com.example.on_device_qwen.ui.components.parseSimpleGraph
import com.example.on_device_qwen.ui.components.resolveThinkingExpandedState
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
    fun testIncrementalMarkdownParserCommitsStablePrefixAndHandlesResets() {
        val parser = IncrementalMarkdownParser()
        val fullMarkdown = """
            # Architecture

            First completed paragraph.

            ```kotlin
            fun run() = 42
            ```

            Final trailing line
        """.trimIndent()

        var lastBlocks = emptyList<MarkdownBlock>()
        for (len in 1..fullMarkdown.length step 7) {
            lastBlocks = parser.parse(fullMarkdown.substring(0, len), isStreaming = true)
        }
        val finalizedBlocks = parser.parse(fullMarkdown, isStreaming = false)

        assertTrue("Expected prefix blocks to commit incrementally while streaming", parser.committedBlockCount >= 3)
        assertEquals(parseMarkdownBlocks(fullMarkdown), finalizedBlocks)
        assertTrue(lastBlocks.isNotEmpty())

        // Verify reset on a completely different message/prompt
        val nextMsg = "## Replaced\n\nFresh content"
        val resetBlocks = parser.parse(nextMsg, isStreaming = false)
        assertEquals(parseMarkdownBlocks(nextMsg), resetBlocks)
    }

    @Test
    fun testStreamingUnclosedDiagramAndCodeFlags() {
        val streamingMermaid = """
            ```mermaid
            graph TD
            A[Start] --> B[Running]
        """.trimIndent()
        val streamingBlocks = parseMarkdownBlocks(streamingMermaid)
        val diagram = streamingBlocks.filterIsInstance<MarkdownBlock.Diagram>().single()
        assertFalse("Unclosed mermaid fence during streaming must report isClosed=false", diagram.isClosed)

        val closedMermaid = "$streamingMermaid\n```"
        val closedDiagram = parseMarkdownBlocks(closedMermaid).filterIsInstance<MarkdownBlock.Diagram>().single()
        assertTrue("Closed mermaid fence must report isClosed=true", closedDiagram.isClosed)

        val unclosedCode = "```rust\nfn main() {"
        val codeBlock = parseMarkdownBlocks(unclosedCode).filterIsInstance<MarkdownBlock.Code>().single()
        assertFalse("Unclosed code fence must report isClosed=false", codeBlock.isClosed)
    }

    @Test
    fun testMermaidFlowchartEdgesShapesSubgraphsAndCycleSafety() {
        val mermaid = """
            flowchart LR
            subgraph Core[Execution Engine]
                A([Input]) --> B{Valid?}
            end
            B -->|Yes| C[Run Inference]
            B -. Retry .-> A
            C -- Done --> D((End))
        """.trimIndent()

        val graph = parseSimpleGraph(mermaid)
        assertNotNull("Flowchart should parse cleanly", graph)
        graph!!
        assertTrue("flowchart LR must be horizontal", graph.isHorizontal)

        val byId = graph.nodes.associateBy { it.id }
        assertEquals(NodeShape.STADIUM, byId["A"]?.shape)
        assertEquals(NodeShape.DIAMOND, byId["B"]?.shape)
        assertEquals(NodeShape.RECTANGLE, byId["C"]?.shape)
        assertEquals(NodeShape.CIRCLE, byId["D"]?.shape)
        assertEquals("Execution Engine", byId["A"]?.subgraph)
        assertEquals("Execution Engine", byId["B"]?.subgraph)

        assertEquals(4, graph.edges.size)
        assertEquals("Yes", graph.edges[1].label)
        assertEquals("Retry", graph.edges[2].label)
        assertTrue("Dashed edge -.-> should preserve isDashed", graph.edges[2].isDashed)
        assertEquals("Done", graph.edges[3].label)

        // Ensure cyclic back-edge A <- B terminates cleanly in layered topological assignment
        val layers = assignFlowchartLayers(graph)
        assertTrue("Layered DAG layout must place all nodes", layers.flatten().size == graph.nodes.size)

        // Sequence diagram parsing
        val sequence = """
            sequenceDiagram
            participant U as User
            participant M as Model
            U->>M: Send prompt
            M-->>U: Stream tokens
            M->>M: Update KV cache
        """.trimIndent()
        val seqParsed = parseMermaidDiagram(sequence) as? ParsedMermaidDiagram.Sequence
        assertNotNull("Sequence diagram must parse offline", seqParsed)
        assertEquals(listOf("User", "Model"), seqParsed!!.data.participants.map { it.label })
        assertEquals(3, seqParsed.data.messages.size)
        assertTrue(seqParsed.data.messages[1].isDashed)

        // State diagram parsing
        val stateDiag = """
            stateDiagram-v2
            [*] --> Idle
            Idle --> Generating : prompt
            Generating --> [*] : complete
        """.trimIndent()
        val stateParsed = parseMermaidDiagram(stateDiag) as? ParsedMermaidDiagram.Flowchart
        assertNotNull("State diagram must parse offline", stateParsed)
        assertTrue(stateParsed!!.graph.edges.any { it.label == "prompt" })
    }

    @Test
    fun testLatexMathFormatterAndThinkingStateResolution() {
        val formatted = formatLatexSymbols("""\sum_{i=1}^n \frac{\alpha + \beta}{2} \neq \infty""")
        assertTrue(formatted.contains("∑"))
        assertTrue(formatted.contains("((α + β) / 2)") || formatted.contains("(α + β / 2)"))
        assertTrue(formatted.contains("≠"))
        assertTrue(formatted.contains("∞"))

        // Thinking drawer visibility state rules
        assertFalse("Completed thought process should collapse by default", resolveThinkingExpandedState(false, "done", null))
        assertFalse("Blank live thinking should stay collapsed until tokens arrive", resolveThinkingExpandedState(true, "   ", null))
        assertTrue("Active non-blank reasoning should auto-expand", resolveThinkingExpandedState(true, "Step 1", null))
        assertFalse("User collapse override must win even while actively thinking", resolveThinkingExpandedState(true, "Step 1", false))
        assertTrue("User expand override must win on completed messages", resolveThinkingExpandedState(false, "done", true))
    }
}
