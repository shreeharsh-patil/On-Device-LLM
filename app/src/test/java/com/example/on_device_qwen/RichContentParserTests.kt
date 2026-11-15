package com.example.on_device_qwen

import androidx.compose.ui.text.font.FontStyle
import com.example.on_device_qwen.ui.components.EdgeStyle
import com.example.on_device_qwen.ui.components.FlowchartDirection
import com.example.on_device_qwen.ui.components.FlowchartNodeShape
import com.example.on_device_qwen.ui.components.MarkdownBlock
import com.example.on_device_qwen.ui.components.ParsedMermaidDiagram
import com.example.on_device_qwen.ui.components.SequenceItem
import com.example.on_device_qwen.ui.components.TableAlignment
import com.example.on_device_qwen.ui.components.computeFlowchartLayers
import com.example.on_device_qwen.ui.components.formatLatexSymbols
import com.example.on_device_qwen.ui.components.highlightCodeSyntax
import com.example.on_device_qwen.ui.components.parseMarkdownBlocks
import com.example.on_device_qwen.ui.components.parseMermaidDiagram
import com.example.on_device_qwen.ui.components.parseSequenceDiagram
import com.example.on_device_qwen.ui.components.parseSimpleGraph
import com.example.on_device_qwen.ui.components.parseStateDiagram
import com.example.on_device_qwen.ui.designsystem.DarkSovereignColors
import com.example.on_device_qwen.ui.designsystem.LightSovereignColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RichContentParserTests {

    @Test
    fun parseMarkdownBlocks_handlesComplexDocumentAndCrlf() {
        val raw = (
            "# Title\r\n" +
                "First line\r\nsecond line\r\n\r\n" +
                "> Quote line 1\r\n> Quote line 2\r\n\r\n" +
                "1. First step\r\n2. Second step\r\n" +
                "$$ a^2 + b^2 = c^2 $$\r\n" +
                "| Key | Align | Value |\r\n" +
                "| :--- | :---: | ---: |\r\n" +
                "| tok/s | center | 42.5 |\r\n" +
                "```kotlin\r\nval x = 10\r\n```\r\n" +
                "```sequenceDiagram\r\nUser->>Model: Hi\r\n```"
            )

        val blocks = parseMarkdownBlocks(raw)

        val heading = blocks.filterIsInstance<MarkdownBlock.Heading>().single()
        assertEquals(1, heading.level)
        assertEquals("Title", heading.text)

        val paragraph = blocks.filterIsInstance<MarkdownBlock.Paragraph>().single()
        assertEquals("First line\nsecond line", paragraph.text)

        val quote = blocks.filterIsInstance<MarkdownBlock.Blockquote>().single()
        assertEquals("Quote line 1\nQuote line 2", quote.text)

        val lists = blocks.filterIsInstance<MarkdownBlock.ListItem>()
        assertEquals(2, lists.size)
        assertTrue(lists.all { it.isOrdered })
        assertEquals(1, lists[0].index)
        assertEquals(2, lists[1].index)

        val math = blocks.filterIsInstance<MarkdownBlock.MathBlock>().single()
        assertEquals("a^2 + b^2 = c^2", math.equation)

        val table = blocks.filterIsInstance<MarkdownBlock.Table>().single()
        assertEquals(listOf("Key", "Align", "Value"), table.tableData.headers)
        assertEquals(
            listOf(TableAlignment.LEFT, TableAlignment.CENTER, TableAlignment.RIGHT),
            table.tableData.alignments
        )
        assertEquals(listOf("tok/s", "center", "42.5"), table.tableData.rows.single())

        val code = blocks.filterIsInstance<MarkdownBlock.Code>().single()
        assertEquals("kotlin", code.language)
        assertEquals("val x = 10", code.code)

        val diagram = blocks.filterIsInstance<MarkdownBlock.Diagram>().single()
        assertTrue(diagram.code.contains("User->>Model: Hi"))
    }

    @Test
    fun parseMarkdownBlocks_handlesUnclosedStreamingFenceGracefully() {
        val streaming = "Working on it:\n```python\ndef infer():\n    return 42"
        val blocks = parseMarkdownBlocks(streaming)

        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Paragraph)
        val code = blocks[1] as MarkdownBlock.Code
        assertEquals("python", code.language)
        assertEquals("def infer():\n    return 42", code.code)
    }

    @Test
    fun formatLatexSymbols_avoidsPrefixCollisionsAndConvertsFractionsAndScripts() {
        val raw = """\int_0^\infty \frac{\alpha + \beta}{2} \leq \text{max}(x^2) \in \mathbb"""
        val formatted = formatLatexSymbols(raw)

        assertTrue(formatted.contains("∫₀^∞"))
        assertTrue(formatted.contains("(α + β / 2)"))
        assertTrue(formatted.contains("≤"))
        assertTrue(formatted.contains("max(x²)"))
        assertTrue(formatted.contains("∈"))
        // Ensure plain strings return unchanged cleanly
        assertEquals("plain text 123", formatLatexSymbols("plain text 123"))
    }

    @Test
    fun highlightCodeSyntax_appliesThemeTokensInLightAndDarkModes() {
        val kotlinSnippet = """
            // compute answer
            @Composable
            fun answer(name: String = "Qwen"): Int = 42
        """.trimIndent()

        for (palette in listOf(LightSovereignColors, DarkSovereignColors)) {
            val highlighted = highlightCodeSyntax(kotlinSnippet, "kotlin", palette)
            assertEquals(kotlinSnippet, highlighted.text)

            val spanColors = highlighted.spanStyles.map { it.item.color }.toSet()
            assertTrue("Comment color missing", palette.textMuted in spanColors)
            assertTrue("Keyword color missing", palette.llamaViolet in spanColors)
            assertTrue("String color missing", palette.success in spanColors)
            assertTrue("Type/annotation color missing", palette.accentPrimary in spanColors)
            assertTrue("Number color missing", palette.amber in spanColors)
            assertTrue(highlighted.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
        }

        val pySnippet = "# python comment\nval_x = 'ok'"
        val pyHighlighted = highlightCodeSyntax(pySnippet, "python", DarkSovereignColors)
        assertTrue(pyHighlighted.spanStyles.any { it.item.color == DarkSovereignColors.textMuted })
        assertTrue(pyHighlighted.spanStyles.any { it.item.color == DarkSovereignColors.success })
    }

    @Test
    fun parseSimpleGraph_extractsShapesLabelsDirectionAndPreservesRichNodes() {
        val source = """
            flowchart LR
                A([Start]) -->|init| B{Ready?}
                B -- Yes --> C[(SQLite DB)]
                B -. retry .-> A
                C ==> D((Done)) & E{{Metrics}}
                D --> F[Final Report]
        """.trimIndent()

        val graph = parseSimpleGraph(source)
        assertNotNull(graph)
        graph!!

        assertEquals(FlowchartDirection.LR, graph.direction)
        assertTrue(graph.isHorizontal)
        assertEquals(6, graph.nodes.size)

        val byId = graph.nodes.associateBy { it.id }
        assertEquals("Start", byId["A"]?.label)
        assertEquals(FlowchartNodeShape.STADIUM, byId["A"]?.shape)
        assertEquals(FlowchartNodeShape.DIAMOND, byId["B"]?.shape)
        assertEquals(FlowchartNodeShape.DATABASE, byId["C"]?.shape)
        assertEquals(FlowchartNodeShape.CIRCLE, byId["D"]?.shape)
        assertEquals(FlowchartNodeShape.HEXAGON, byId["E"]?.shape)
        // Rich label on D ("Done") must NOT be overwritten by bare "D --> F"
        assertEquals("Done", byId["D"]?.label)

        assertEquals(6, graph.edges.size)
        assertEquals("init", graph.edges[0].label)
        assertEquals(EdgeStyle.SOLID_ARROW, graph.edges[0].style)
        assertEquals("Yes", graph.edges[1].label)
        assertEquals("retry", graph.edges[2].label)
        assertEquals(EdgeStyle.DASHED_ARROW, graph.edges[2].style)
        assertEquals(EdgeStyle.THICK_ARROW, graph.edges[3].style)
        assertEquals(setOf("D", "E"), setOf(graph.edges[3].to, graph.edges[4].to))

        // Topological layering handles cycles safely without infinite recursion
        val layers = computeFlowchartLayers(graph.nodes, graph.edges, graph.direction)
        assertTrue(layers.isNotEmpty())
        assertEquals(graph.nodes.size, layers.sumOf { it.size })
    }

    @Test
    fun parseSequenceDiagram_parsesActorsMessagesNotesAndBlocks() {
        val source = """
            sequenceDiagram
                actor U as User
                participant E as Engine
                U->>E: Send prompt
                Note over U,E: 100% Offline
                alt Cache hit
                    E-->>U: Stream cached
                else Cache miss
                    E->>E: Prefill KV
                    E-->>U: Stream tokens
                end
        """.trimIndent()

        val seq = parseSequenceDiagram(source)
        assertNotNull(seq)
        seq!!

        assertEquals(2, seq.participants.size)
        assertEquals("User", seq.participants[0].displayName)
        assertTrue(seq.participants[0].isActor)
        assertEquals("Engine", seq.participants[1].displayName)

        val messages = seq.items.filterIsInstance<SequenceItem.Message>()
        assertEquals(4, messages.size)
        assertEquals(false, messages[0].isDashed)
        assertEquals(true, messages[1].isDashed)
        assertEquals(true, messages[2].isSelf)

        val notes = seq.items.filterIsInstance<SequenceItem.Note>()
        assertEquals(1, notes.size)
        assertEquals(listOf("U", "E"), notes[0].participantIds)

        val blocks = seq.items.filterIsInstance<SequenceItem.BlockMarker>()
        assertEquals(2, blocks.size)
        assertEquals("ALT", blocks[0].kind)
        assertEquals("ELSE", blocks[1].kind)
    }

    @Test
    fun parseStateDiagram_separatesTerminalStartAndEndNodes() {
        val source = """
            stateDiagram-v2
                [*] --> Idle
                state "Streaming Tokens" as Streaming
                Idle --> Streaming : prompt()
                Streaming --> [*] : finish()
        """.trimIndent()

        val state = parseStateDiagram(source)
        assertNotNull(state)
        state!!

        val byId = state.nodes.associateBy { it.id }
        assertEquals(FlowchartNodeShape.STATE_START, byId["__state_start__"]?.shape)
        assertEquals(FlowchartNodeShape.STATE_END, byId["__state_end__"]?.shape)
        assertEquals("Streaming Tokens", byId["Streaming"]?.label)

        val layers = computeFlowchartLayers(state.nodes, state.edges, state.direction)
        assertEquals(4, layers.size)
        assertEquals("__state_start__", layers.first().single().id)
        assertEquals("__state_end__", layers.last().single().id)
    }

    @Test
    fun parseMermaidDiagram_providesStructuredFallbackForOtherDiagramTypes() {
        val classDiag = """
            classDiagram
                class ChatViewModel
                class InferenceEngine
                ChatViewModel --> InferenceEngine
        """.trimIndent()

        val parsed = parseMermaidDiagram(classDiag)
        assertTrue(parsed is ParsedMermaidDiagram.StructuredFallback)
        val fallback = parsed as ParsedMermaidDiagram.StructuredFallback
        assertEquals("Class Diagram", fallback.kindLabel)
        assertEquals(3, fallback.summaryLines.size)
    }
}
