package com.example.on_device_qwen.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.on_device_qwen.ui.designsystem.SovereignColors
import com.example.on_device_qwen.ui.designsystem.SovereignDivider
import com.example.on_device_qwen.ui.designsystem.SovereignElevation
import com.example.on_device_qwen.ui.designsystem.SovereignHairlineCard
import com.example.on_device_qwen.ui.designsystem.SovereignIconButton
import com.example.on_device_qwen.ui.designsystem.SovereignTheme
import kotlinx.coroutines.delay

private const val STREAMING_CURSOR_INLINE_ID = "sovereign_streaming_cursor"

// Top-level hoisted constants — zero per-composition Regex or Set allocations.
private val ORDERED_LIST_REGEX = Regex("""^(\d+)\.\s+(.*)""")
private val TABLE_SEPARATOR_CELL_REGEX = Regex("""^:?-+:?$""")

private val DIAGRAM_LANGUAGES = setOf(
    "mermaid", "diagram", "flowchart", "graph", "chart",
    "sequencediagram", "statediagram", "statediagram-v2", "classdiagram", "erdiagram"
)

private val HASH_COMMENT_LANGUAGES = setOf(
    "python", "py", "bash", "sh", "zsh", "shell",
    "yaml", "yml", "toml", "ruby", "rb", "r",
    "perl", "dockerfile", "makefile", "ini", "conf"
)

private val CODE_KEYWORDS = setOf(
    // Kotlin / Java / Swift / JS / TS / C / Rust / Go / Python / SQL
    "abstract", "actual", "and", "as", "async", "await", "break", "by", "case", "catch",
    "class", "companion", "const", "continue", "data", "def", "default", "defer", "do",
    "elif", "else", "enum", "expect", "export", "extends", "false", "False", "final",
    "finally", "fn", "for", "from", "fun", "func", "function", "go", "guard", "if",
    "impl", "implements", "import", "in", "infix", "init", "inline", "interface",
    "internal", "is", "lambda", "lateinit", "let", "loop", "match", "mod", "mut",
    "new", "nil", "none", "None", "not", "null", "object", "open", "operator", "or",
    "override", "package", "pass", "private", "protected", "pub", "public", "raise",
    "range", "return", "sealed", "self", "Self", "static", "struct", "super", "suspend",
    "switch", "this", "throw", "throws", "trait", "true", "True", "try", "type",
    "typealias", "typeof", "undefined", "use", "val", "var", "void", "when", "where",
    "while", "with", "yield",
    // SQL & Mermaid directives
    "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE",
    "CREATE", "TABLE", "JOIN", "LEFT", "RIGHT", "INNER", "ON", "GROUP", "ORDER", "BY",
    "LIMIT", "AS", "AND", "OR", "NOT", "NULL",
    "flowchart", "graph", "sequenceDiagram", "stateDiagram", "classDiagram", "erDiagram",
    "participant", "actor", "subgraph", "end", "direction", "state", "note", "Note"
)

/**
 * High-performance Markdown renderer tailored for Sovereign Obsidian & Vellum responses.
 * Renders:
 * - Syntax-highlighted fenced code blocks with copy actions
 * - 100% offline native Mermaid diagrams & flowcharts
 * - Pixel-aligned Markdown tables with zebra striping and horizontal scrolling
 * - LaTeX math blocks and inline formulas (theme-contrast safe)
 * - Headings, Blockquotes, Ordered and Unordered lists, Horizontal rules
 * - Live inline typing cursor during token streaming
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = SovereignTheme.colors.textPrimary,
    isStreaming: Boolean = false
) {
    val colors = SovereignTheme.colors
    val spacing = SovereignTheme.spacing
    val shapes = SovereignTheme.shapes
    val blocks = remember(text) { parseMarkdownBlocks(text) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        blocks.forEachIndexed { index, block ->
            val isLastBlock = index == blocks.lastIndex
            val showCursor = isStreaming && isLastBlock

            key(index, block::class) {
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
                        val headingStyle = when (block.level) {
                            1 -> SovereignTheme.typography.sheetHeader
                            2 -> SovereignTheme.typography.cardTitle
                            else -> SovereignTheme.typography.pillTitle.copy(fontWeight = FontWeight.SemiBold)
                        }
                        FormattedInlineText(
                            text = block.text,
                            color = color,
                            style = headingStyle,
                            showStreamingCursor = showCursor,
                            modifier = Modifier.padding(top = if (block.level <= 2) 4.dp else 2.dp)
                        )
                    }
                    is MarkdownBlock.Blockquote -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shapes.sm)
                                .background(colors.surfaceLow)
                                .height(IntrinsicSize.Min)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .fillMaxHeight()
                                    .background(colors.accentPrimary)
                            )
                            FormattedInlineText(
                                text = block.text,
                                color = colors.textSecondary,
                                style = SovereignTheme.typography.chatProse.copy(fontStyle = FontStyle.Italic),
                                showStreamingCursor = showCursor,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }
                    is MarkdownBlock.ListItem -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp, top = 1.dp, bottom = 1.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = if (block.isOrdered) "${block.index}. " else "• ",
                                style = if (block.isOrdered) {
                                    SovereignTheme.typography.telemetry.copy(fontSize = 14.sp)
                                } else {
                                    SovereignTheme.typography.chatProse.copy(fontWeight = FontWeight.Bold)
                                },
                                color = colors.accentPrimary,
                                modifier = Modifier.padding(top = if (block.isOrdered) 3.dp else 0.dp)
                            )
                            FormattedInlineText(
                                text = block.text,
                                color = color,
                                style = SovereignTheme.typography.chatProse,
                                showStreamingCursor = showCursor,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    is MarkdownBlock.Paragraph -> {
                        FormattedInlineText(
                            text = block.text,
                            color = color,
                            style = SovereignTheme.typography.chatProse,
                            showStreamingCursor = showCursor
                        )
                    }
                    is MarkdownBlock.Divider -> {
                        SovereignDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }
}

/**
 * Pulsing typewriter cursor for active streaming tokens, respecting [SovereignTheme.reducedMotion].
 */
@Composable
fun StreamingCursor(modifier: Modifier = Modifier) {
    val colors = SovereignTheme.colors
    val reducedMotion = SovereignTheme.reducedMotion

    if (reducedMotion) {
        Box(
            modifier = modifier
                .padding(start = 3.dp)
                .size(width = 7.dp, height = 15.dp)
                .clip(SovereignTheme.shapes.xs)
                .background(colors.accentPrimary)
        )
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "streaming_cursor")
        val cursorAlpha by infiniteTransition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 520, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "cursorAlpha"
        )
        Box(
            modifier = modifier
                .padding(start = 3.dp)
                .size(width = 7.dp, height = 15.dp)
                .graphicsLayer { alpha = cursorAlpha }
                .clip(SovereignTheme.shapes.xs)
                .background(colors.accentPrimary)
        )
    }
}

@Composable
fun FormattedInlineText(
    text: String,
    color: Color = SovereignTheme.colors.textPrimary,
    modifier: Modifier = Modifier,
    style: TextStyle = SovereignTheme.typography.chatProse,
    textAlign: TextAlign = TextAlign.Start,
    showStreamingCursor: Boolean = false
) {
    val colors = SovereignTheme.colors
    val inlineCodeStyle = remember(colors) {
        SpanStyle(
            fontFamily = SovereignTheme.typography.JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Medium,
            background = colors.surfaceHighest,
            color = colors.onAccentContainer,
            fontSize = 13.5.sp
        )
    }
    val linkStyle = remember(colors) {
        SpanStyle(
            color = colors.accentPrimary,
            textDecoration = TextDecoration.Underline,
            fontWeight = FontWeight.Medium
        )
    }

    val annotatedString = remember(text, color, inlineCodeStyle, linkStyle, showStreamingCursor) {
        buildAnnotatedString {
            var i = 0
            val len = text.length
            while (i < len) {
                val ch = text[i]

                // 1. Inline code `code`
                if (ch == '`' && i + 1 < len) {
                    val endIdx = text.indexOf('`', i + 1)
                    if (endIdx > i + 1 && !text.substring(i + 1, endIdx).contains('\n')) {
                        withStyle(inlineCodeStyle) {
                            append(' ')
                            append(text, i + 1, endIdx)
                            append(' ')
                        }
                        i = endIdx + 1
                        continue
                    }
                }

                // 2. Bold + Italic ***text***
                if (ch == '*' && text.startsWith("***", i) && i + 3 < len) {
                    val endIdx = text.indexOf("***", i + 3)
                    if (endIdx > i + 3) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, color = color)) {
                            append(text, i + 3, endIdx)
                        }
                        i = endIdx + 3
                        continue
                    }
                }

                // 3. Bold **text**
                if (ch == '*' && text.startsWith("**", i) && i + 2 < len) {
                    val endIdx = text.indexOf("**", i + 2)
                    if (endIdx > i + 2) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color)) {
                            append(text, i + 2, endIdx)
                        }
                        i = endIdx + 2
                        continue
                    }
                }

                // 4. Italic *text*
                if (ch == '*' && i + 1 < len && !text[i + 1].isWhitespace()) {
                    val endIdx = text.indexOf('*', i + 1)
                    if (endIdx > i + 1 && !text[endIdx - 1].isWhitespace() && !text.substring(i + 1, endIdx).contains('\n')) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = color)) {
                            append(text, i + 1, endIdx)
                        }
                        i = endIdx + 1
                        continue
                    }
                }

                // 5. Markdown Link [label](url)
                if (ch == '[' && i + 1 < len) {
                    val closeBracket = text.indexOf(']', i + 1)
                    if (closeBracket > i + 1 &&
                        closeBracket + 1 < len &&
                        text[closeBracket + 1] == '(' &&
                        !text.substring(i + 1, closeBracket).contains('\n')
                    ) {
                        val closeParen = text.indexOf(')', closeBracket + 2)
                        if (closeParen > closeBracket + 2 && !text.substring(closeBracket + 2, closeParen).contains('\n')) {
                            withStyle(linkStyle) {
                                append(text, i + 1, closeBracket)
                            }
                            i = closeParen + 1
                            continue
                        }
                    }
                }

                // 6. Inline math $equation$
                if (ch == '$' && i + 1 < len && text[i + 1] != '$' && !text[i + 1].isWhitespace()) {
                    val endIdx = text.indexOf('$', i + 1)
                    if (endIdx > i + 1 &&
                        !text[endIdx - 1].isWhitespace() &&
                        !text.substring(i + 1, endIdx).contains('\n')
                    ) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Serif,
                                fontStyle = FontStyle.Italic,
                                color = color
                            )
                        ) {
                            append(formatLatexSymbols(text.substring(i + 1, endIdx)))
                        }
                        i = endIdx + 1
                        continue
                    }
                }

                append(ch)
                i++
            }

            if (showStreamingCursor) {
                appendInlineContent(STREAMING_CURSOR_INLINE_ID, "\u200B")
            }
        }
    }

    val inlineContent = if (showStreamingCursor) {
        remember {
            mapOf(
                STREAMING_CURSOR_INLINE_ID to InlineTextContent(
                    Placeholder(
                        width = 11.sp,
                        height = 16.sp,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                    )
                ) {
                    StreamingCursor()
                }
            )
        }
    } else {
        emptyMap()
    }

    Text(
        text = annotatedString,
        style = style,
        color = color,
        textAlign = textAlign,
        inlineContent = inlineContent,
        modifier = modifier
    )
}

@Composable
fun CodeBlockView(
    code: String,
    language: String = "",
    modifier: Modifier = Modifier
) {
    val colors = SovereignTheme.colors
    val shapes = SovereignTheme.shapes
    val motion = SovereignTheme.motion
    val context = LocalContext.current

    var isCopied by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val displayLang = remember(language) { language.ifBlank { "code" }.lowercase() }
    val highlightedCode = remember(code, displayLang, colors) {
        highlightCodeSyntax(code, displayLang, colors)
    }

    LaunchedEffect(isCopied) {
        if (isCopied) {
            delay(1800)
            isCopied = false
        }
    }

    SovereignHairlineCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = shapes.md,
        containerColor = colors.codeBackground,
        borderColor = colors.hairlineSubtle,
        contentPadding = PaddingValues(0.dp)
    ) {
        // Header bar with language badge & accessible >=48.dp Copy SovereignIconButton
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.codeHeader)
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = shapes.xs,
                color = colors.surfaceBase,
                border = BorderStroke(SovereignElevation.hairlineWidth, colors.hairlineSubtle),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Text(
                    text = displayLang,
                    style = SovereignTheme.typography.monoTag,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(
                    visible = isCopied,
                    enter = fadeIn(motion.effectsQuick()),
                    exit = fadeOut(motion.effectsQuick())
                ) {
                    Text(
                        text = "Copied",
                        style = SovereignTheme.typography.monoTag,
                        color = colors.success,
                        modifier = Modifier.padding(end = 2.dp)
                    )
                }

                SovereignIconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("code", code))
                        isCopied = true
                    },
                    contentDescription = if (isCopied) "Copied code" else "Copy code",
                    visualSize = 36.dp,
                    contentColor = if (isCopied) colors.success else colors.textSecondary,
                    hapticFeedback = true
                ) {
                    Icon(
                        imageVector = if (isCopied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        SovereignDivider()

        SelectionContainer {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(
                    text = highlightedCode,
                    style = SovereignTheme.typography.codeBody,
                    color = colors.textPrimary
                )
            }
        }
    }
}

/**
 * Lightweight single-pass O(N) theme-aware syntax highlighter using [SovereignColors] tokens.
 * Zero per-call Regex allocations and safe for incomplete/streaming code blocks.
 */
internal fun highlightCodeSyntax(
    code: String,
    language: String,
    colors: SovereignColors
): AnnotatedString {
    if (code.isEmpty()) return AnnotatedString("")

    val commentStyle = SpanStyle(color = colors.textMuted, fontStyle = FontStyle.Italic)
    val stringStyle = SpanStyle(color = colors.success)
    val keywordStyle = SpanStyle(color = colors.llamaViolet, fontWeight = FontWeight.Medium)
    val typeStyle = SpanStyle(color = colors.accentPrimary)
    val numberStyle = SpanStyle(color = colors.amber)

    val supportsHashComments = language in HASH_COMMENT_LANGUAGES
    val supportsPercentComments = language == "mermaid" || language == "diagram"

    return buildAnnotatedString {
        val len = code.length
        var i = 0
        var plainStart = 0

        fun flushPlain(upTo: Int) {
            if (upTo > plainStart) {
                append(code, plainStart, upTo)
            }
        }

        while (i < len) {
            val c = code[i]

            // 1. Line comments: //, # (for Python/Bash/YAML/etc.), %% (for Mermaid)
            val isSlashLineComment = c == '/' && i + 1 < len && code[i + 1] == '/'
            val isHashComment = c == '#' && supportsHashComments
            val isMermaidComment = c == '%' && supportsPercentComments && i + 1 < len && code[i + 1] == '%'
            if (isSlashLineComment || isHashComment || isMermaidComment) {
                flushPlain(i)
                val endLine = code.indexOf('\n', i).let { if (it == -1) len else it }
                withStyle(commentStyle) {
                    append(code, i, endLine)
                }
                i = endLine
                plainStart = i
                continue
            }

            // 2. Block comment /* ... */
            if (c == '/' && i + 1 < len && code[i + 1] == '*') {
                flushPlain(i)
                val closeIdx = code.indexOf("*/", i + 2)
                val endBlock = if (closeIdx == -1) len else closeIdx + 2
                withStyle(commentStyle) {
                    append(code, i, endBlock)
                }
                i = endBlock
                plainStart = i
                continue
            }

            // 3. Quoted string "..." or '...'
            if (c == '"' || c == '\'') {
                flushPlain(i)
                var j = i + 1
                while (j < len) {
                    val sc = code[j]
                    if (sc == '\\' && j + 1 < len) {
                        j += 2
                        continue
                    }
                    if (sc == c) {
                        j++
                        break
                    }
                    if (sc == '\n') {
                        break
                    }
                    j++
                }
                withStyle(stringStyle) {
                    append(code, i, j)
                }
                i = j
                plainStart = i
                continue
            }

            // 4. Annotations (@Composable, @Test, etc.)
            if (c == '@' && i + 1 < len && (code[i + 1].isLetter() || code[i + 1] == '_')) {
                flushPlain(i)
                var j = i + 1
                while (j < len && (code[j].isLetterOrDigit() || code[j] == '_')) {
                    j++
                }
                withStyle(typeStyle) {
                    append(code, i, j)
                }
                i = j
                plainStart = i
                continue
            }

            // 5. Numbers (start of token)
            val prevIsIdent = i > 0 && (code[i - 1].isLetterOrDigit() || code[i - 1] == '_')
            if (c.isDigit() && !prevIsIdent) {
                flushPlain(i)
                var j = i + 1
                while (j < len && (code[j].isLetterOrDigit() || code[j] == '.' || code[j] == '_')) {
                    j++
                }
                withStyle(numberStyle) {
                    append(code, i, j)
                }
                i = j
                plainStart = i
                continue
            }

            // 6. Identifiers, Keywords & Types
            if ((c.isLetter() || c == '_') && !prevIsIdent) {
                var j = i + 1
                while (j < len && (code[j].isLetterOrDigit() || code[j] == '_')) {
                    j++
                }
                val token = code.substring(i, j)
                val tokenStyle = when {
                    token in CODE_KEYWORDS -> keywordStyle
                    token.length > 1 && token[0].isUpperCase() && token.any { it.isLowerCase() } -> typeStyle
                    else -> null
                }
                if (tokenStyle != null) {
                    flushPlain(i)
                    withStyle(tokenStyle) {
                        append(token)
                    }
                    i = j
                    plainStart = i
                    continue
                }
                i = j
                continue
            }

            i++
        }

        flushPlain(len)
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
    if (rawText.isEmpty()) return emptyList()

    val normalized = if (rawText.contains('\r')) {
        rawText.replace("\r\n", "\n").replace('\r', '\n')
    } else {
        rawText
    }
    val lines = normalized.split('\n')
    val blocks = ArrayList<MarkdownBlock>(lines.size.coerceAtMost(32))
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        // 1. Math Block $$ ... $$
        if (trimmed.startsWith("$$")) {
            if (trimmed.endsWith("$$") && trimmed.length > 4) {
                val equation = trimmed.removePrefix("$$").removeSuffix("$$").trim()
                blocks.add(MarkdownBlock.MathBlock(equation))
                i++
                continue
            } else {
                val mathBuilder = StringBuilder()
                val firstLineRest = trimmed.removePrefix("$$").trim()
                if (firstLineRest.isNotEmpty()) {
                    mathBuilder.append(firstLineRest)
                }
                i++
                while (i < lines.size && !lines[i].trim().endsWith("$$")) {
                    if (mathBuilder.isNotEmpty()) mathBuilder.append('\n')
                    mathBuilder.append(lines[i])
                    i++
                }
                if (i < lines.size) {
                    val last = lines[i].trim().removeSuffix("$$").trim()
                    if (last.isNotEmpty()) {
                        if (mathBuilder.isNotEmpty()) mathBuilder.append('\n')
                        mathBuilder.append(last)
                    }
                    i++
                }
                blocks.add(MarkdownBlock.MathBlock(mathBuilder.toString()))
                continue
            }
        }

        // 2. Fenced code block ``` or diagrams
        val trimmedStart = line.trimStart()
        if (trimmedStart.startsWith("```")) {
            val lang = trimmedStart.removePrefix("```").trim().lowercase()
            val codeBuilder = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                if (codeBuilder.isNotEmpty()) codeBuilder.append('\n')
                codeBuilder.append(lines[i])
                i++
            }
            if (i < lines.size) i++ // consume closing fence if present

            val codeContent = codeBuilder.toString()
            val codeHead = codeContent.trimStart()
            val isDiagram = lang in DIAGRAM_LANGUAGES ||
                codeHead.startsWith("graph ") ||
                codeHead.startsWith("flowchart ") ||
                codeHead.startsWith("sequenceDiagram") ||
                codeHead.startsWith("classDiagram") ||
                codeHead.startsWith("stateDiagram") ||
                codeHead.startsWith("erDiagram")

            if (isDiagram) {
                blocks.add(MarkdownBlock.Diagram(codeContent, lang.ifBlank { "mermaid" }))
            } else {
                blocks.add(MarkdownBlock.Code(codeContent, lang))
            }
            continue
        }

        // 3. Markdown Table (| header | header |)
        if (line.contains('|') && i + 1 < lines.size && isTableSeparatorLine(lines[i + 1])) {
            val headers = parseTableRow(line)
            val alignments = parseTableAlignments(lines[i + 1])
            val rows = mutableListOf<List<String>>()
            i += 2
            while (i < lines.size && lines[i].trim().isNotEmpty() && lines[i].contains('|')) {
                rows.add(parseTableRow(lines[i]))
                i++
            }
            blocks.add(MarkdownBlock.Table(MarkdownTableData(headers, alignments, rows)))
            continue
        }

        // 4. Headings (#, ##, ###, ####)
        if (trimmed.startsWith("#### ")) {
            blocks.add(MarkdownBlock.Heading(trimmed.removePrefix("#### "), 3))
            i++
            continue
        } else if (trimmed.startsWith("### ")) {
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

        // 6. Blockquotes (coalesce consecutive > lines)
        if (trimmed.startsWith("> ") || trimmed == ">") {
            val quoteBuilder = StringBuilder()
            while (i < lines.size) {
                val qt = lines[i].trim()
                if (qt.startsWith("> ")) {
                    if (quoteBuilder.isNotEmpty()) quoteBuilder.append('\n')
                    quoteBuilder.append(qt.removePrefix("> "))
                    i++
                } else if (qt == ">") {
                    if (quoteBuilder.isNotEmpty()) quoteBuilder.append('\n')
                    i++
                } else {
                    break
                }
            }
            blocks.add(MarkdownBlock.Blockquote(quoteBuilder.toString()))
            continue
        }

        // 7. List item bullet
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            blocks.add(MarkdownBlock.ListItem(trimmed.substring(2), isOrdered = false))
            i++
            continue
        }

        // 8. Ordered list item
        val matchOrdered = ORDERED_LIST_REGEX.find(trimmed)
        if (matchOrdered != null) {
            val num = matchOrdered.groupValues[1].toIntOrNull() ?: 1
            val content = matchOrdered.groupValues[2]
            blocks.add(MarkdownBlock.ListItem(content, isOrdered = true, index = num))
            i++
            continue
        }

        // 9. Regular Paragraph (coalesce consecutive plain lines)
        if (trimmed.isNotEmpty()) {
            val paraBuilder = StringBuilder(line)
            i++
            while (i < lines.size) {
                val nextLine = lines[i]
                val nextTrimmed = nextLine.trim()
                if (nextTrimmed.isEmpty() ||
                    isBlockStartLine(nextLine, nextTrimmed, lines.getOrNull(i + 1))
                ) {
                    break
                }
                paraBuilder.append('\n').append(nextLine)
                i++
            }
            blocks.add(MarkdownBlock.Paragraph(paraBuilder.toString()))
            continue
        }

        i++
    }

    return blocks
}

private fun isBlockStartLine(rawLine: String, trimmed: String, nextLine: String?): Boolean {
    if (trimmed.startsWith("$$") || rawLine.trimStart().startsWith("```")) return true
    if (trimmed.startsWith("# ") || trimmed.startsWith("## ") || trimmed.startsWith("### ") || trimmed.startsWith("#### ")) return true
    if (trimmed == "---" || trimmed == "***" || trimmed == "___") return true
    if (trimmed.startsWith("> ") || trimmed == ">") return true
    if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) return true
    if (ORDERED_LIST_REGEX.containsMatchIn(trimmed)) return true
    if (rawLine.contains('|') && nextLine != null && isTableSeparatorLine(nextLine)) return true
    return false
}

private fun isTableSeparatorLine(line: String): Boolean {
    val trimmed = line.trim()
    if (!trimmed.contains('-') || !trimmed.contains('|')) return false
    val rawParts = trimmed.split('|')
    var hasCell = false
    for (part in rawParts) {
        val cell = part.trim()
        if (cell.isNotEmpty()) {
            hasCell = true
            if (!TABLE_SEPARATOR_CELL_REGEX.matches(cell)) return false
        }
    }
    return hasCell
}

private fun parseTableRow(line: String): List<String> {
    val parts = line.split('|')
    if (parts.isEmpty()) return emptyList()
    val startIdx = if (parts.first().trim().isEmpty()) 1 else 0
    val endIdx = if (parts.size > startIdx && parts.last().trim().isEmpty()) parts.lastIndex else parts.size
    if (startIdx >= endIdx) return emptyList()
    val result = ArrayList<String>(endIdx - startIdx)
    for (idx in startIdx until endIdx) {
        result.add(parts[idx].trim())
    }
    return result
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
