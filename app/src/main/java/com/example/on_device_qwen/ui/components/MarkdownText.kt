package com.example.on_device_qwen.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * High-performance Markdown renderer tailored for ChatGPT-style responses.
 * Renders:
 * - Code blocks with copy actions
 * - Interactive Mermaid diagrams & flowcharts
 * - Markdown tables with zebra striping and horizontal scrolling
 * - LaTeX math blocks and inline formulas
 * - Headings, Blockquotes, Ordered and Unordered lists
 * - Live typing cursor during token streaming
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    isStreaming: Boolean = false
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEachIndexed { index, block ->
            val isLastBlock = index == blocks.lastIndex

            when (block) {
                is MarkdownBlock.Diagram -> {
                    MermaidDiagramView(diagramCode = block.code, diagramType = block.diagramType)
                }
                is MarkdownBlock.Table -> {
                    MarkdownTableView(table = block.tableData)
                }
                is MarkdownBlock.MathBlock -> {
                    MathEquationView(equation = block.equation)
                }
                is MarkdownBlock.Code -> {
                    CodeBlockView(code = block.code, language = block.language)
                }
                is MarkdownBlock.Heading -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        2 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        else -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = block.text, style = style, color = color)
                        if (isStreaming && isLastBlock) {
                            StreamingCursor()
                        }
                    }
                }
                is MarkdownBlock.Blockquote -> {
                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(IntrinsicSize.Min)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = block.text,
                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic, lineHeight = 22.sp),
                            color = color.copy(alpha = 0.85f)
                        )
                    }
                }
                is MarkdownBlock.ListItem -> {
                    Row(modifier = Modifier.padding(start = 4.dp, top = 1.dp, bottom = 1.dp)) {
                        Text(
                            text = if (block.isOrdered) "${block.index}. " else "• ",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FormattedInlineText(text = block.text, color = color)
                            if (isStreaming && isLastBlock) {
                                StreamingCursor()
                            }
                        }
                    }
                }
                is MarkdownBlock.Paragraph -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FormattedInlineText(text = block.text, color = color)
                        if (isStreaming && isLastBlock) {
                            StreamingCursor()
                        }
                    }
                }
                is MarkdownBlock.Divider -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

/**
 * Pulsing typewriter cursor for active streaming tokens.
 */
@Composable
fun StreamingCursor(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )
    Box(
        modifier = modifier
            .padding(start = 3.dp)
            .size(width = 7.dp, height = 15.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
    )
}

@Composable
fun FormattedInlineText(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    val annotatedString = remember(text, color) {
        buildAnnotatedString {
            var i = 0
            while (i < text.length) {
                // Inline code `code`
                if (text[i] == '`' && i + 1 < text.length) {
                    val endIdx = text.indexOf('`', i + 1)
                    if (endIdx != -1) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = Color(0xFF2A2A38),
                                color = Color(0xFF03DAC6),
                                fontSize = 13.sp
                            )
                        ) {
                            append(text.substring(i + 1, endIdx))
                        }
                        i = endIdx + 1
                        continue
                    }
                }

                // Bold **text**
                if (text.startsWith("**", i) && i + 2 < text.length) {
                    val endIdx = text.indexOf("**", i + 2)
                    if (endIdx != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color)) {
                            append(text.substring(i + 2, endIdx))
                        }
                        i = endIdx + 2
                        continue
                    }
                }

                // Italic *text*
                if (text[i] == '*' && i + 1 < text.length) {
                    val endIdx = text.indexOf('*', i + 1)
                    if (endIdx != -1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = color)) {
                            append(text.substring(i + 1, endIdx))
                        }
                        i = endIdx + 1
                        continue
                    }
                }

                // Inline math $equation$
                if (text[i] == '$' && i + 1 < text.length && text[i + 1] != '$') {
                    val endIdx = text.indexOf('$', i + 1)
                    if (endIdx != -1) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Serif,
                                fontStyle = FontStyle.Italic,
                                color = Color(0xFFE2E2F0)
                            )
                        ) {
                            append(formatLatexSymbols(text.substring(i + 1, endIdx)))
                        }
                        i = endIdx + 1
                        continue
                    }
                }

                append(text[i])
                i++
            }
        }
    }

    Text(
        text = annotatedString,
        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
        color = color,
        modifier = modifier
    )
}

@Composable
fun CodeBlockView(
    code: String,
    language: String = "",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }

    Surface(
        color = Color(0xFF14141E),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFF28283C)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            // Header bar with language title & copy button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1C1C2B))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = language.ifBlank { "code" }.lowercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFA0A0C0),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )

                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("code", code)
                        clipboard.setPrimaryClip(clip)
                        isCopied = true
                        coroutineScope.launch {
                            delay(2000)
                            isCopied = false
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(
                        imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        modifier = Modifier.size(14.dp),
                        tint = if (isCopied) Color(0xFF4CAF50) else Color(0xFFA0A0C0)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (isCopied) "Copied!" else "Copy",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCopied) Color(0xFF4CAF50) else Color(0xFFA0A0C0)
                    )
                }
            }

            // Code content with horizontal scroll
            SelectionContainer {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    Text(
                        text = code,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = Color(0xFFE2E2F0)
                    )
                }
            }
        }
    }
}

sealed class MarkdownBlock {
    data class Code(val code: String, val language: String) : MarkdownBlock()
    data class Diagram(val code: String, val diagramType: String = "mermaid") : MarkdownBlock()
    data class Table(val tableData: MarkdownTableData) : MarkdownBlock()
    data class MathBlock(val equation: String) : MarkdownBlock()
    data class Heading(val text: String, val level: Int) : MarkdownBlock()
    data class Blockquote(val text: String) : MarkdownBlock()
    data class ListItem(val text: String, val isOrdered: Boolean, val index: Int = 1) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data object Divider : MarkdownBlock()
}

/**
 * Parses markdown into structured blocks with support for:
 * - Code & Mermaid diagrams
 * - GFM tables
 * - Math equations ($$ ... $$)
 * - Headings, Blockquotes, Lists, Paragraphs, Dividers
 */
fun parseMarkdownBlocks(rawText: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = rawText.split("\n")
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        // 1. Math Block $$ ... $$
        if (trimmed.startsWith("$$")) {
            if (trimmed.endsWith("$$") && trimmed.length > 2 && trimmed != "$$") {
                val equation = trimmed.removePrefix("$$").removeSuffix("$$").trim()
                blocks.add(MarkdownBlock.MathBlock(equation))
                i++
                continue
            } else {
                val mathLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().endsWith("$$")) {
                    mathLines.add(lines[i])
                    i++
                }
                if (i < lines.size) {
                    val last = lines[i].trim().removeSuffix("$$").trim()
                    if (last.isNotEmpty()) mathLines.add(last)
                    i++
                }
                blocks.add(MarkdownBlock.MathBlock(mathLines.joinToString("\n")))
                continue
            }
        }

        // 2. Fenced code block ``` or diagrams
        if (line.trimStart().startsWith("```")) {
            val lang = line.trimStart().removePrefix("```").trim().lowercase()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            val codeContent = codeLines.joinToString("\n")
            val isDiagram = lang in listOf("mermaid", "diagram", "flowchart", "graph", "chart") ||
                    codeContent.trimStart().startsWith("graph ") ||
                    codeContent.trimStart().startsWith("flowchart ") ||
                    codeContent.trimStart().startsWith("sequenceDiagram") ||
                    codeContent.trimStart().startsWith("classDiagram") ||
                    codeContent.trimStart().startsWith("stateDiagram") ||
                    codeContent.trimStart().startsWith("erDiagram")

            if (isDiagram) {
                blocks.add(MarkdownBlock.Diagram(codeContent, lang.ifBlank { "mermaid" }))
            } else {
                blocks.add(MarkdownBlock.Code(codeContent, lang))
            }
            i++
            continue
        }

        // 3. Markdown Table (| header | header |)
        if (line.contains("|") && i + 1 < lines.size && isTableSeparatorLine(lines[i + 1])) {
            val headers = parseTableRow(line)
            val alignments = parseTableAlignments(lines[i + 1])
            val rows = mutableListOf<List<String>>()
            i += 2
            while (i < lines.size && lines[i].trim().isNotEmpty() && lines[i].contains("|")) {
                rows.add(parseTableRow(lines[i]))
                i++
            }
            blocks.add(MarkdownBlock.Table(MarkdownTableData(headers, alignments, rows)))
            continue
        }

        // 4. Headings
        if (trimmed.startsWith("### ")) {
            blocks.add(MarkdownBlock.Heading(trimmed.removePrefix("### "), 3))
            i++
            continue
        } else if (trimmed.startsWith("## ")) {
            blocks.add(MarkdownBlock.Heading(trimmed.removePrefix("## "), 2))
            i++
            continue
        } else if (trimmed.startsWith("# ")) {
            blocks.add(MarkdownBlock.Heading(trimmed.removePrefix("# "), 1))
            i++
            continue
        }

        // 5. Horizontal Rule
        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            blocks.add(MarkdownBlock.Divider)
            i++
            continue
        }

        // 6. Blockquotes
        if (trimmed.startsWith("> ")) {
            blocks.add(MarkdownBlock.Blockquote(trimmed.removePrefix("> ")))
            i++
            continue
        }

        // 7. List item bullet
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            blocks.add(MarkdownBlock.ListItem(trimmed.substring(2), false))
            i++
            continue
        }

        // 8. Ordered list item
        val matchOrdered = Regex("""^(\d+)\.\s+(.*)""").find(trimmed)
        if (matchOrdered != null) {
            val num = matchOrdered.groupValues[1].toIntOrNull() ?: 1
            val content = matchOrdered.groupValues[2]
            blocks.add(MarkdownBlock.ListItem(content, true, num))
            i++
            continue
        }

        // 9. Regular Paragraph
        if (trimmed.isNotEmpty()) {
            blocks.add(MarkdownBlock.Paragraph(line))
        }
        i++
    }

    return blocks
}

private fun isTableSeparatorLine(line: String): Boolean {
    val trimmed = line.trim()
    if (!trimmed.contains("-") || !trimmed.contains("|")) return false
    val cells = trimmed.split("|").map { it.trim() }.filter { it.isNotEmpty() }
    if (cells.isEmpty()) return false
    return cells.all { it.matches(Regex("""^:?-+:?$""")) }
}

private fun parseTableRow(line: String): List<String> {
    return line.split("|")
        .map { it.trim() }
        .filterIndexed { index, cell ->
            // Exclude empty leading or trailing cell from outer pipes
            !(index == 0 && cell.isEmpty()) && !(index == line.split("|").lastIndex && cell.isEmpty())
        }
}

private fun parseTableAlignments(separatorLine: String): List<TableAlignment> {
    val cells = parseTableRow(separatorLine)
    return cells.map { cell ->
        when {
            cell.startsWith(":") && cell.endsWith(":") -> TableAlignment.CENTER
            cell.endsWith(":") -> TableAlignment.RIGHT
            else -> TableAlignment.LEFT
        }
    }
}
