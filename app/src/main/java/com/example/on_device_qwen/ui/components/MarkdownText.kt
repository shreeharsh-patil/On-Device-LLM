package com.example.on_device_qwen.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.on_device_qwen.ui.designsystem.LocalReducedMotion
import com.example.on_device_qwen.ui.designsystem.SovereignElevation
import com.example.on_device_qwen.ui.designsystem.SovereignTheme
import com.example.on_device_qwen.ui.designsystem.SovereignTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val STREAMING_CURSOR_ID = "streaming_cursor"

/**
 * High-performance streaming Markdown renderer using Sovereign Vellum/Obsidian tokens.
 *
 * Uses [IncrementalMarkdownParser] + keyed blocks so completed paragraphs, tables, diagrams,
 * and fenced code blocks are cached rather than re-parsed/recomposed on every streamed token.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false,
    color: Color = Color.Unspecified
) {
    val colors = SovereignTheme.colors
    val resolvedColor = if (color == Color.Unspecified) colors.textPrimary else color
    val parser = remember { IncrementalMarkdownParser() }
    val blocks = remember(text, isStreaming) { parser.parse(text, isStreaming) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEachIndexed { index, block ->
            val isLastBlock = index == blocks.lastIndex
            val showCursorOnBlock = isStreaming && isLastBlock

            key(index) {
                when (block) {
                    is MarkdownBlock.Diagram -> {
                        MermaidDiagramView(
                            diagramCode = block.code,
                            diagramType = block.diagramType,
                            isStreaming = isStreaming && !block.isClosed
                        )
                    }
                    is MarkdownBlock.Table -> {
                        MarkdownTableView(table = block.tableData)
                    }
                    is MarkdownBlock.MathBlock -> {
                        MathEquationView(equation = block.equation)
                    }
                    is MarkdownBlock.Code -> {
                        CodeBlockView(
                            code = block.code,
                            language = block.language,
                            isStreaming = isStreaming && !block.isClosed
                        )
                    }
                    is MarkdownBlock.Heading -> {
                        val headingStyle = when (block.level) {
                            1 -> SovereignTypography.sheetHeader.copy(fontSize = 20.sp, lineHeight = 28.sp)
                            2 -> SovereignTypography.cardTitle.copy(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
                            else -> SovereignTypography.cardTitle.copy(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
                        }
                        FormattedInlineText(
                            text = block.text,
                            color = resolvedColor,
                            textStyle = headingStyle,
                            showCursor = showCursorOnBlock,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    is MarkdownBlock.Blockquote -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                                .padding(vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(colors.accentPrimary.copy(alpha = 0.75f))
                            )
                            Spacer(Modifier.width(10.dp))
                            FormattedInlineText(
                                text = block.text,
                                color = colors.textSecondary,
                                textStyle = SovereignTypography.chatProse.copy(fontStyle = FontStyle.Italic),
                                showCursor = showCursorOnBlock,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    is MarkdownBlock.ListItem -> {
                        val markerText = if (block.isOrdered) "${block.index}. " else "• "
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = (4 + block.indentLevel * 12).dp,
                                    top = 1.dp,
                                    bottom = 1.dp
                                )
                        ) {
                            Text(
                                text = markerText,
                                style = SovereignTypography.chatProse.copy(fontWeight = FontWeight.Bold),
                                color = colors.accentPrimary
                            )
                            FormattedInlineText(
                                text = block.text,
                                color = resolvedColor,
                                showCursor = showCursorOnBlock,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    is MarkdownBlock.Paragraph -> {
                        FormattedInlineText(
                            text = block.text,
                            color = resolvedColor,
                            showCursor = showCursorOnBlock,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    is MarkdownBlock.Divider -> {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            thickness = SovereignElevation.hairlineWidth,
                            color = colors.hairlineSubtle
                        )
                    }
                }
            }
        }
    }
}

/**
 * Pulsing typewriter cursor for active streaming tokens, respecting [LocalReducedMotion].
 */
@Composable
fun StreamingCursor(modifier: Modifier = Modifier) {
    val colors = SovereignTheme.colors
    val reducedMotion = LocalReducedMotion.current
    val alpha = if (reducedMotion) {
        0.75f
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "cursor")
        val animated by infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(520, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "cursorAlpha"
        )
        animated
    }

    Box(
        modifier = modifier
            .padding(start = 2.dp)
            .size(width = 7.dp, height = 15.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(colors.accentPrimary.copy(alpha = alpha))
    )
}

@Composable
fun FormattedInlineText(
    text: String,
    color: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
    textStyle: TextStyle? = null,
    textAlign: TextAlign? = null,
    showCursor: Boolean = false
) {
    val colors = SovereignTheme.colors
    val resolvedColor = if (color == Color.Unspecified) colors.textPrimary else color
    val inlineCodeBg = colors.codeBackground
    val inlineCodeFg = colors.accentPrimary
    val baseStyle = (textStyle ?: SovereignTypography.chatProse).let {
        if (textAlign != null) it.copy(textAlign = textAlign) else it
    }

    val annotatedString = remember(text, resolvedColor, inlineCodeBg, inlineCodeFg, showCursor) {
        buildAnnotatedString {
            var i = 0
            while (i < text.length) {
                // Inline code `code`
                if (text[i] == '`' && i + 1 < text.length) {
                    val endIdx = text.indexOf('`', i + 1)
                    if (endIdx != -1) {
                        withStyle(
                            SpanStyle(
                                fontFamily = SovereignTypography.JetBrainsMonoFontFamily,
                                background = inlineCodeBg,
                                color = inlineCodeFg,
                                fontSize = 13.sp
                            )
                        ) {
                            append(text.substring(i + 1, endIdx))
                        }
                        i = endIdx + 1
                        continue
                    }
                }

                // Bold + Italic ***text***
                if (text.startsWith("***", i) && i + 3 < text.length) {
                    val endIdx = text.indexOf("***", i + 3)
                    if (endIdx != -1) {
                        withStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Italic,
                                color = resolvedColor
                            )
                        ) {
                            append(text.substring(i + 3, endIdx))
                        }
                        i = endIdx + 3
                        continue
                    }
                }

                // Bold **text**
                if (text.startsWith("**", i) && i + 2 < text.length) {
                    val endIdx = text.indexOf("**", i + 2)
                    if (endIdx != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = resolvedColor)) {
                            append(text.substring(i + 2, endIdx))
                        }
                        i = endIdx + 2
                        continue
                    }
                }

                // Strikethrough ~~text~~
                if (text.startsWith("~~", i) && i + 2 < text.length) {
                    val endIdx = text.indexOf("~~", i + 2)
                    if (endIdx != -1) {
                        withStyle(
                            SpanStyle(
                                textDecoration = TextDecoration.LineThrough,
                                color = resolvedColor.copy(alpha = 0.7f)
                            )
                        ) {
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
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = resolvedColor)) {
                            append(text.substring(i + 1, endIdx))
                        }
                        i = endIdx + 1
                        continue
                    }
                }

                // Inline Markdown link [label](url)
                if (text[i] == '[') {
                    val closeBracket = text.indexOf(']', i + 1)
                    if (closeBracket != -1 && closeBracket + 1 < text.length && text[closeBracket + 1] == '(') {
                        val closeParen = text.indexOf(')', closeBracket + 2)
                        if (closeParen != -1) {
                            val label = text.substring(i + 1, closeBracket)
                            withStyle(
                                SpanStyle(
                                    color = inlineCodeFg,
                                    textDecoration = TextDecoration.Underline,
                                    fontWeight = FontWeight.Medium
                                )
                            ) {
                                append(label)
                            }
                            i = closeParen + 1
                            continue
                        }
                    }
                }

                // Inline math $equation$
                if (text[i] == '$' && i + 1 < text.length && text[i + 1] != '$') {
                    val endIdx = text.indexOf('$', i + 1)
                    if (endIdx != -1) {
                        withStyle(
                            SpanStyle(
                                fontFamily = SovereignTypography.EditorialSerifFontFamily,
                                fontStyle = FontStyle.Italic,
                                color = resolvedColor
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
            if (showCursor) {
                appendInlineContent(STREAMING_CURSOR_ID, " ")
            }
        }
    }

    val inlineContent = remember(showCursor) {
        if (showCursor) {
            mapOf(
                STREAMING_CURSOR_ID to InlineTextContent(
                    Placeholder(
                        width = 10.sp,
                        height = 16.sp,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                    )
                ) {
                    Box(contentAlignment = Alignment.CenterStart) {
                        StreamingCursor()
                    }
                }
            )
        } else {
            emptyMap()
        }
    }

    Text(
        text = annotatedString,
        style = baseStyle,
        color = resolvedColor,
        inlineContent = inlineContent,
        modifier = modifier
    )
}

@Composable
fun CodeBlockView(
    code: String,
    language: String = "",
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false
) {
    val colors = SovereignTheme.colors
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }

    Surface(
        color = colors.codeBackground,
        shape = SovereignTheme.shapes.sm,
        border = BorderStroke(SovereignElevation.hairlineWidth, colors.hairlineStrong),
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            // Header bar with language badge & >=48.dp touch-target copy control
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.codeHeader)
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = language.ifBlank { "code" }.lowercase(),
                        style = SovereignTypography.monoTag,
                        color = colors.textSecondary
                    )
                    if (isStreaming) {
                        Text(
                            text = "streaming",
                            style = SovereignTypography.monoTag,
                            color = colors.accentPrimary
                        )
                    }
                }

                val copyTint = if (isCopied) colors.success else colors.textSecondary
                val copyLabel = if (isCopied) "Copied" else "Copy"
                Surface(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("code", code))
                        isCopied = true
                        coroutineScope.launch {
                            delay(2000)
                            isCopied = false
                        }
                    },
                    shape = SovereignTheme.shapes.xs,
                    color = Color.Transparent,
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics(mergeDescendants = true) {
                            role = Role.Button
                            contentDescription = if (isCopied) "Code copied to clipboard" else "Copy code"
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = copyTint
                        )
                        Text(
                            text = copyLabel,
                            style = SovereignTypography.monoTag,
                            color = copyTint
                        )
                    }
                }
            }

            HorizontalDivider(
                thickness = SovereignElevation.hairlineWidth,
                color = colors.hairlineSubtle
            )

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
                        style = SovereignTypography.codeBody,
                        color = colors.textPrimary
                    )
                }
            }
        }
    }
}

@Immutable
sealed class MarkdownBlock {
    @Immutable
    data class Code(
        val code: String,
        val language: String,
        val isClosed: Boolean = true
    ) : MarkdownBlock()

    @Immutable
    data class Diagram(
        val code: String,
        val diagramType: String = "mermaid",
        val isClosed: Boolean = true
    ) : MarkdownBlock()

    @Immutable
    data class Table(val tableData: MarkdownTableData) : MarkdownBlock()

    @Immutable
    data class MathBlock(val equation: String) : MarkdownBlock()

    @Immutable
    data class Heading(val text: String, val level: Int) : MarkdownBlock()

    @Immutable
    data class Blockquote(val text: String) : MarkdownBlock()

    @Immutable
    data class ListItem(
        val text: String,
        val isOrdered: Boolean,
        val index: Int = 1,
        val indentLevel: Int = 0
    ) : MarkdownBlock()

    @Immutable
    data class Paragraph(val text: String) : MarkdownBlock()

    @Immutable
    data object Divider : MarkdownBlock()
}

/**
 * Stateful incremental block parser for streaming Markdown responses.
 * Commits finalized top-level blocks after safe boundaries (blank lines outside open
 * fences/math blocks, or closed fenced blocks) so token appends only re-parse the active tail.
 */
class IncrementalMarkdownParser {
    private var lastRawText: String = ""
    private var lastIsStreaming: Boolean = false
    private var committedPrefixEnd: Int = 0
    private var committedPrefixText: String = ""
    private val committedBlocks = mutableListOf<MarkdownBlock>()
    private var cachedBlocks: List<MarkdownBlock> = emptyList()

    /** Diagnostic counter: number of active-tail / finalize parses executed across updates. */
    var tailParseCount: Int = 0
        private set

    /** Number of prefix blocks committed so far without needing re-parse on future tokens. */
    var committedBlockCount: Int = 0
        private set

    fun parse(rawText: String, isStreaming: Boolean): List<MarkdownBlock> {
        if (rawText == lastRawText && isStreaming == lastIsStreaming && lastRawText.isNotEmpty()) {
            return cachedBlocks
        }
        if (rawText.isEmpty()) {
            reset()
            return emptyList()
        }

        if (!rawText.startsWith(committedPrefixText)) {
            reset()
        }

        val tail = rawText.substring(committedPrefixEnd)
        if (isStreaming) {
            val safeAdvance = findSafeCommitBoundary(tail)
            if (safeAdvance > 0) {
                val newlyCommittedSlice = tail.substring(0, safeAdvance)
                committedBlocks.addAll(parseMarkdownBlocks(newlyCommittedSlice))
                committedPrefixEnd += safeAdvance
                committedPrefixText = rawText.substring(0, committedPrefixEnd)
                committedBlockCount = committedBlocks.size
            }
            val activeTail = if (committedPrefixEnd < rawText.length) {
                rawText.substring(committedPrefixEnd)
            } else {
                ""
            }
            tailParseCount++
            val tailBlocks = if (activeTail.isNotEmpty()) parseMarkdownBlocks(activeTail) else emptyList()
            cachedBlocks = combineBlocks(committedBlocks, tailBlocks)
        } else {
            val activeTail = if (committedPrefixEnd < rawText.length) {
                rawText.substring(committedPrefixEnd)
            } else {
                ""
            }
            tailParseCount++
            val tailBlocks = if (activeTail.isNotEmpty()) parseMarkdownBlocks(activeTail) else emptyList()
            val combined = combineBlocks(committedBlocks, tailBlocks)
            committedBlocks.clear()
            committedBlocks.addAll(combined)
            committedPrefixEnd = rawText.length
            committedPrefixText = rawText
            committedBlockCount = committedBlocks.size
            cachedBlocks = combined
        }

        lastRawText = rawText
        lastIsStreaming = isStreaming
        return cachedBlocks
    }

    fun reset() {
        lastRawText = ""
        lastIsStreaming = false
        committedPrefixEnd = 0
        committedPrefixText = ""
        committedBlocks.clear()
        cachedBlocks = emptyList()
        tailParseCount = 0
        committedBlockCount = 0
    }

    private fun combineBlocks(
        prefix: List<MarkdownBlock>,
        tail: List<MarkdownBlock>
    ): List<MarkdownBlock> {
        if (prefix.isEmpty()) return tail
        if (tail.isEmpty()) return prefix.toList()
        return ArrayList<MarkdownBlock>(prefix.size + tail.size).apply {
            addAll(prefix)
            addAll(tail)
        }
    }
}

/**
 * Returns the character length inside [tail] up to the last position where earlier blocks are
 * structurally closed (a completed fence/math block followed by newline, or a blank line outside
 * open fences/math blocks).
 */
internal fun findSafeCommitBoundary(tail: String): Int {
    if (tail.isEmpty()) return 0
    var inCodeFence = false
    var inMathBlock = false
    var lastSafeEnd = 0
    var pos = 0

    while (pos < tail.length) {
        val nextNewline = tail.indexOf('\n', pos)
        if (nextNewline == -1) break

        val lineEndExclusive = nextNewline + 1
        val line = tail.substring(pos, nextNewline)
        val trimmedStart = line.trimStart()
        val trimmed = line.trim()

        if (inCodeFence) {
            if (trimmedStart.startsWith("```")) {
                inCodeFence = false
                lastSafeEnd = lineEndExclusive
            }
        } else if (inMathBlock) {
            if (trimmed.endsWith("$$")) {
                inMathBlock = false
                lastSafeEnd = lineEndExclusive
            }
        } else {
            when {
                trimmedStart.startsWith("```") -> inCodeFence = true
                trimmed.startsWith("$$") -> {
                    if (trimmed.endsWith("$$") && trimmed.length > 2 && trimmed != "$$") {
                        lastSafeEnd = lineEndExclusive
                    } else {
                        inMathBlock = true
                    }
                }
                trimmed.isEmpty() -> lastSafeEnd = lineEndExclusive
            }
        }
        pos = lineEndExclusive
    }

    return lastSafeEnd
}

/**
 * Parses markdown into structured blocks with support for:
 * - Code & Mermaid diagrams (recording closed vs streaming-unclosed fences)
 * - GFM tables with alignments
 * - Math equations ($$ ... $$)
 * - Headings, Blockquotes, Ordered/Unordered lists (including nested bullets), Paragraphs, Dividers
 */
fun parseMarkdownBlocks(rawText: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = rawText.replace("\r\n", "\n").split("\n")
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
            var isClosed = false
            while (i < lines.size) {
                if (lines[i].trimStart().startsWith("```")) {
                    isClosed = true
                    break
                }
                codeLines.add(lines[i])
                i++
            }
            val codeContent = codeLines.joinToString("\n")
            val trimmedContent = codeContent.trimStart()
            val isDiagram = lang in DIAGRAM_LANGS ||
                DIAGRAM_PREFIXES.any { trimmedContent.startsWith(it) }

            if (isDiagram) {
                blocks.add(
                    MarkdownBlock.Diagram(
                        code = codeContent,
                        diagramType = lang.ifBlank { "mermaid" },
                        isClosed = isClosed
                    )
                )
            } else {
                blocks.add(
                    MarkdownBlock.Code(
                        code = codeContent,
                        language = lang,
                        isClosed = isClosed
                    )
                )
            }
            if (isClosed) i++
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

        // 4. Headings (h1..h4)
        when {
            trimmed.startsWith("#### ") -> {
                blocks.add(MarkdownBlock.Heading(trimmed.removePrefix("#### "), 4))
                i++
                continue
            }
            trimmed.startsWith("### ") -> {
                blocks.add(MarkdownBlock.Heading(trimmed.removePrefix("### "), 3))
                i++
                continue
            }
            trimmed.startsWith("## ") -> {
                blocks.add(MarkdownBlock.Heading(trimmed.removePrefix("## "), 2))
                i++
                continue
            }
            trimmed.startsWith("# ") -> {
                blocks.add(MarkdownBlock.Heading(trimmed.removePrefix("# "), 1))
                i++
                continue
            }
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

        // 7. Unordered list item
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            val leadingSpaces = line.length - line.trimStart().length
            blocks.add(
                MarkdownBlock.ListItem(
                    text = trimmed.substring(2),
                    isOrdered = false,
                    indentLevel = (leadingSpaces / 2).coerceIn(0, 4)
                )
            )
            i++
            continue
        }

        // 8. Ordered list item
        val matchOrdered = ORDERED_LIST_REGEX.find(trimmed)
        if (matchOrdered != null) {
            val num = matchOrdered.groupValues[1].toIntOrNull() ?: 1
            val content = matchOrdered.groupValues[2]
            val leadingSpaces = line.length - line.trimStart().length
            blocks.add(
                MarkdownBlock.ListItem(
                    text = content,
                    isOrdered = true,
                    index = num,
                    indentLevel = (leadingSpaces / 2).coerceIn(0, 4)
                )
            )
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

private val DIAGRAM_LANGS = setOf("mermaid", "diagram", "flowchart", "graph", "chart")
private val DIAGRAM_PREFIXES = listOf(
    "graph ",
    "flowchart ",
    "sequenceDiagram",
    "classDiagram",
    "stateDiagram",
    "erDiagram"
)
private val ORDERED_LIST_REGEX = Regex("""^(\d+)\.\s+(.*)""")
private val TABLE_SEPARATOR_CELL_REGEX = Regex("""^:?-+:?$""")

private fun isTableSeparatorLine(line: String): Boolean {
    val trimmed = line.trim()
    if (!trimmed.contains("-") || !trimmed.contains("|")) return false
    val cells = trimmed.split("|").map { it.trim() }.filter { it.isNotEmpty() }
    if (cells.isEmpty()) return false
    return cells.all { it.matches(TABLE_SEPARATOR_CELL_REGEX) }
}

private fun parseTableRow(line: String): List<String> {
    val rawCells = line.split("|")
    val lastIdx = rawCells.lastIndex
    return rawCells
        .map { it.trim() }
        .filterIndexed { index, cell ->
            !(index == 0 && cell.isEmpty()) && !(index == lastIdx && cell.isEmpty())
        }
}

private fun parseTableAlignments(separatorLine: String): List<TableAlignment> {
    return parseTableRow(separatorLine).map { cell ->
        when {
            cell.startsWith(":") && cell.endsWith(":") -> TableAlignment.CENTER
            cell.endsWith(":") -> TableAlignment.RIGHT
            else -> TableAlignment.LEFT
        }
    }
}
