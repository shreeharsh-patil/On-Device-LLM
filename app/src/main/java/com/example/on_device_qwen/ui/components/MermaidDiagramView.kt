package com.example.on_device_qwen.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.on_device_qwen.ui.designsystem.SovereignElevation
import com.example.on_device_qwen.ui.designsystem.SovereignIconButton
import com.example.on_device_qwen.ui.designsystem.SovereignTheme
import com.example.on_device_qwen.ui.designsystem.SovereignTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 100% Offline, XSS-free Diagram Visualizer built in pure Jetpack Compose.
 *
 * Supports Mermaid flowcharts (TD/TB/LR/RL/BT), decision diamonds, subgraphs,
 * state diagrams, sequence diagrams (lifelines + messages), and clean code fallback.
 * No WebView, no external CDN requests, and no HTML/JavaScript interpolation.
 */
@Composable
fun MermaidDiagramView(
    diagramCode: String,
    diagramType: String = "mermaid",
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false
) {
    val colors = SovereignTheme.colors
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Preview, 1 = Code
    var isFullscreen by remember { mutableStateOf(false) }
    var isCopied by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    if (isStreaming) {
        StreamingDiagramPlaceholder(
            diagramCode = diagramCode,
            diagramType = diagramType,
            modifier = modifier
        )
        return
    }

    if (isFullscreen) {
        Dialog(
            onDismissRequest = { isFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = colors.bgCanvas
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surfaceLow)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Diagram Viewer",
                            style = SovereignTypography.sheetHeader,
                            color = colors.textPrimary
                        )
                        SovereignIconButton(
                            onClick = { isFullscreen = false },
                            contentDescription = "Exit fullscreen",
                            containerColor = colors.surfaceHigh,
                            contentColor = colors.textPrimary,
                            border = BorderStroke(SovereignElevation.hairlineWidth, colors.hairlineSubtle)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FullscreenExit,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    HorizontalDivider(
                        thickness = SovereignElevation.hairlineWidth,
                        color = colors.hairlineSubtle
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(16.dp)
                    ) {
                        MermaidContentRenderer(diagramCode = diagramCode)
                    }
                }
            }
        }
    }

    Surface(
        shape = SovereignTheme.shapes.md,
        color = colors.surfaceHigh,
        border = BorderStroke(SovereignElevation.hairlineWidth, colors.hairlineStrong),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceBase)
                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountTree,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = colors.accentPrimary
                    )
                    Text(
                        text = diagramType.ifBlank { "diagram" }.lowercase(),
                        style = SovereignTypography.monoTag,
                        color = colors.textSecondary
                    )
                }

                // Controls (Preview / Code tabs, Fullscreen, Copy) - all >= 48.dp touch targets
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    DiagramTabPill(
                        label = "Preview",
                        isSelected = selectedTab == 0,
                        onClick = { selectedTab = 0 }
                    )
                    DiagramTabPill(
                        label = "Code",
                        isSelected = selectedTab == 1,
                        onClick = { selectedTab = 1 }
                    )

                    SovereignIconButton(
                        onClick = { isFullscreen = true },
                        contentDescription = "Fullscreen diagram",
                        visualSize = 36.dp,
                        contentColor = colors.textSecondary
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Fullscreen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    SovereignIconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("mermaid", diagramCode))
                            isCopied = true
                            coroutineScope.launch {
                                delay(2000)
                                isCopied = false
                            }
                        },
                        contentDescription = if (isCopied) "Diagram source copied" else "Copy diagram source",
                        visualSize = 36.dp,
                        contentColor = if (isCopied) colors.success else colors.textSecondary
                    ) {
                        Icon(
                            imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            HorizontalDivider(
                thickness = SovereignElevation.hairlineWidth,
                color = colors.hairlineSubtle
            )

            // Body Content
            if (selectedTab == 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 380.dp)
                        .background(colors.surfaceLow)
                        .padding(8.dp)
                ) {
                    MermaidContentRenderer(diagramCode = diagramCode)
                }
            } else {
                CodeBlockView(code = diagramCode, language = diagramType)
            }
        }
    }
}

@Composable
private fun DiagramTabPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = SovereignTheme.colors
    Surface(
        onClick = onClick,
        shape = SovereignTheme.shapes.pill,
        color = Color.Transparent,
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                selected = isSelected
                contentDescription = "$label view"
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                shape = SovereignTheme.shapes.pill,
                color = if (isSelected) colors.accentContainer else Color.Transparent,
                border = if (isSelected) {
                    BorderStroke(SovereignElevation.hairlineWidth, colors.accentPrimary.copy(alpha = 0.35f))
                } else null
            ) {
                Text(
                    text = label,
                    style = SovereignTypography.monoTag,
                    color = if (isSelected) colors.onAccentContainer else colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun StreamingDiagramPlaceholder(
    diagramCode: String,
    diagramType: String,
    modifier: Modifier = Modifier
) {
    val colors = SovereignTheme.colors
    Surface(
        shape = SovereignTheme.shapes.md,
        color = colors.codeBackground,
        border = BorderStroke(SovereignElevation.hairlineWidth, colors.hairlineStrong),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.codeHeader)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountTree,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = colors.accentPrimary
                    )
                    Text(
                        text = diagramType.ifBlank { "mermaid" }.lowercase(),
                        style = SovereignTypography.monoTag,
                        color = colors.textSecondary
                    )
                }
                Text(
                    text = "Streaming diagram…",
                    style = SovereignTypography.monoTag,
                    color = colors.accentPrimary
                )
            }
            HorizontalDivider(
                thickness = SovereignElevation.hairlineWidth,
                color = colors.hairlineSubtle
            )
            Text(
                text = diagramCode.ifBlank { "…" },
                style = SovereignTypography.codeBody,
                color = colors.textSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            )
        }
    }
}

/**
 * Pure offline Compose dispatcher for flowcharts, state diagrams, sequence diagrams,
 * and structured source fallback.
 */
@Composable
fun MermaidContentRenderer(diagramCode: String) {
    val colors = SovereignTheme.colors
    val parsedDiagram = remember(diagramCode) { parseMermaidDiagram(diagramCode) }

    when (parsedDiagram) {
        is ParsedMermaidDiagram.Flowchart -> {
            NativeFlowchartView(graph = parsedDiagram.graph)
        }
        is ParsedMermaidDiagram.Sequence -> {
            NativeSequenceDiagramView(diagram = parsedDiagram.data)
        }
        null -> {
            // Structured offline source card fallback (no WebView / external network)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = diagramCode.trim(),
                    style = SovereignTypography.codeBody,
                    color = colors.textPrimary
                )
            }
        }
    }
}

// ─── Offline Diagram Model & Parsers ────────────────────────────────────────

enum class NodeShape {
    RECTANGLE,
    ROUNDED,
    STADIUM,
    DIAMOND,
    CIRCLE
}

@Immutable
data class FlowchartNode(
    val id: String,
    val label: String,
    val shape: NodeShape = NodeShape.RECTANGLE,
    val subgraph: String? = null
)

@Immutable
data class FlowchartEdge(
    val from: String,
    val to: String,
    val label: String? = null,
    val isDashed: Boolean = false
)

@Immutable
data class FlowchartGraph(
    val nodes: List<FlowchartNode>,
    val edges: List<FlowchartEdge>,
    val isHorizontal: Boolean,
    val isReversed: Boolean = false
)

@Immutable
data class SequenceParticipant(
    val id: String,
    val label: String
)

@Immutable
data class SequenceMessage(
    val from: String,
    val to: String,
    val label: String,
    val isDashed: Boolean = false
)

@Immutable
data class SequenceDiagramData(
    val participants: List<SequenceParticipant>,
    val messages: List<SequenceMessage>
)

sealed interface ParsedMermaidDiagram {
    data class Flowchart(val graph: FlowchartGraph) : ParsedMermaidDiagram
    data class Sequence(val data: SequenceDiagramData) : ParsedMermaidDiagram
}

fun parseMermaidDiagram(raw: String): ParsedMermaidDiagram? {
    val lines = raw.replace("\r\n", "\n")
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("%%") }
    if (lines.isEmpty()) return null

    val header = lines.first().lowercase()
    return when {
        header.startsWith("sequencediagram") -> {
            parseSequenceDiagram(raw)?.let { ParsedMermaidDiagram.Sequence(it) }
        }
        header.startsWith("statediagram") -> {
            parseStateDiagram(lines)?.let { ParsedMermaidDiagram.Flowchart(it) }
        }
        else -> {
            parseSimpleGraph(raw)?.let { ParsedMermaidDiagram.Flowchart(it) }
        }
    }
}

private data class ParsedTokenNode(
    val id: String,
    val label: String?,
    val shape: NodeShape
)

private val FLOWCHART_CONNECTOR_REGEX = Regex(
    """\s*(?:""" +
        """-\.\s*([^.\n]+?)\s*\.->|""" +      // -. label .->
        """--\s+([^->\n]+?)\s+-->|""" +       // -- label -->
        """==\s+([^=>\n]+?)\s+==>|""" +       // == label ==>
        """--\s+([^->\n]+?)\s+---|""" +       // -- label ---
        """-->\|([^|]*)\||""" +               // -->|label|
        """-.->\|([^|]*)\||""" +              // -.->|label|
        """==>\|([^|]*)\||""" +               // ==>|label|
        """---\|([^|]*)\||""" +               // ---|label|
        """-\.->|""" +                        // -.->
        """==>|""" +                          // ==>
        """-->|""" +                          // -->
        """---""" +                           // ---
        """)\s*"""
)

private val SUBGRAPH_REGEX = Regex("""^subgraph\s+(?:([A-Za-z0-9_]+)\s*\[([^\]]+)\]|(.+))$""", RegexOption.IGNORE_CASE)
private val IGNORED_FLOWCHART_DIRECTIVES = listOf(
    "classdef ", "class ", "style ", "linkstyle ", "click ", "direction "
)

/**
 * Extracts nodes (with shape + optional subgraph), directed edges, edge labels,
 * and layout direction from Mermaid `graph` / `flowchart` syntax.
 */
fun parseSimpleGraph(raw: String): FlowchartGraph? {
    val lines = raw.replace("\r\n", "\n")
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (lines.isEmpty()) return null

    val firstLine = lines.first().lowercase()
    if (!firstLine.startsWith("graph") && !firstLine.startsWith("flowchart")) {
        return null
    }

    val isHorizontal = firstLine.contains(" lr") || firstLine.contains(" rl")
    val isReversed = firstLine.contains(" rl") || firstLine.contains(" bt")
    val nodesOrder = LinkedHashMap<String, FlowchartNode>()
    val edges = mutableListOf<FlowchartEdge>()
    val subgraphStack = ArrayDeque<String>()

    fun recordNode(token: ParsedTokenNode) {
        val existing = nodesOrder[token.id]
        val currentSubgraph = subgraphStack.lastOrNull()
        val resolvedLabel = token.label ?: existing?.label ?: token.id
        val resolvedShape = if (token.label != null || existing == null) token.shape else existing.shape
        val resolvedSubgraph = existing?.subgraph ?: currentSubgraph
        nodesOrder[token.id] = FlowchartNode(
            id = token.id,
            label = resolvedLabel,
            shape = resolvedShape,
            subgraph = resolvedSubgraph
        )
    }

    for (i in 1 until lines.size) {
        val line = lines[i]
        val lower = line.lowercase()
        if (line.startsWith("%%")) continue
        if (IGNORED_FLOWCHART_DIRECTIVES.any { lower.startsWith(it) }) continue

        if (lower == "end") {
            if (subgraphStack.isNotEmpty()) subgraphStack.removeLast()
            continue
        }

        val subMatch = SUBGRAPH_REGEX.find(line)
        if (subMatch != null) {
            val title = subMatch.groupValues[2].ifEmpty {
                subMatch.groupValues[3].ifEmpty { subMatch.groupValues[1] }
            }.trim().trim('"', '\'')
            subgraphStack.addLast(title)
            continue
        }

        // Split chains by edge connectors while retaining connector metadata
        val segments = mutableListOf<String>()
        val connectors = mutableListOf<Pair<String?, Boolean>>() // (label, isDashed)
        var cursor = 0
        for (match in FLOWCHART_CONNECTOR_REGEX.findAll(line)) {
            segments.add(line.substring(cursor, match.range.first).trim())
            val fullConn = match.value
            val label = (1..8).firstNotNullOfOrNull { g ->
                match.groupValues.getOrNull(g)?.takeIf { it.isNotBlank() }?.trim()?.trim('"', '\'')
            }
            val isDashed = fullConn.contains("-.")
            connectors.add(label to isDashed)
            cursor = match.range.last + 1
        }
        segments.add(line.substring(cursor).trim())

        if (connectors.isNotEmpty()) {
            for (segIdx in connectors.indices) {
                val leftTokens = segments[segIdx].split('&').mapNotNull { parseNodeToken(it.trim()) }
                val rightTokens = segments[segIdx + 1].split('&').mapNotNull { parseNodeToken(it.trim()) }
                val (edgeLabel, isDashed) = connectors[segIdx]

                leftTokens.forEach(::recordNode)
                rightTokens.forEach(::recordNode)

                for (fromTok in leftTokens) {
                    for (toTok in rightTokens) {
                        edges.add(
                            FlowchartEdge(
                                from = fromTok.id,
                                to = toTok.id,
                                label = edgeLabel,
                                isDashed = isDashed
                            )
                        )
                    }
                }
            }
        } else {
            // Standalone node declaration inside graph or subgraph
            parseNodeToken(line)?.let(::recordNode)
        }
    }

    if (nodesOrder.isEmpty()) return null
    return FlowchartGraph(
        nodes = nodesOrder.values.toList(),
        edges = edges,
        isHorizontal = isHorizontal,
        isReversed = isReversed
    )
}

private fun parseNodeToken(rawToken: String): ParsedTokenNode? {
    val clean = rawToken.trim().trimEnd(';')
    if (clean.isEmpty()) return null

    // Extract leading node ID
    val idMatch = Regex("""^([A-Za-z0-9_]+)(.*)$""").find(clean) ?: return null
    val id = idMatch.groupValues[1]
    val rest = idMatch.groupValues[2].trim()

    if (rest.isEmpty()) {
        return ParsedTokenNode(id = id, label = null, shape = NodeShape.RECTANGLE)
    }

    fun cleanLabel(inner: String): String = inner.trim().trim('"', '\'')

    return when {
        rest.startsWith("([") && rest.endsWith("])") ->
            ParsedTokenNode(id, cleanLabel(rest.removeSurrounding("([", "])")), NodeShape.STADIUM)
        rest.startsWith("((") && rest.endsWith("))") ->
            ParsedTokenNode(id, cleanLabel(rest.removeSurrounding("((", "))")), NodeShape.CIRCLE)
        rest.startsWith("[(") && rest.endsWith(")]") ->
            ParsedTokenNode(id, cleanLabel(rest.removeSurrounding("[(", ")]")), NodeShape.ROUNDED)
        rest.startsWith("[[") && rest.endsWith("]]") ->
            ParsedTokenNode(id, cleanLabel(rest.removeSurrounding("[[", "]]")), NodeShape.RECTANGLE)
        rest.startsWith("{{") && rest.endsWith("}}") ->
            ParsedTokenNode(id, cleanLabel(rest.removeSurrounding("{{", "}}")), NodeShape.DIAMOND)
        rest.startsWith("[") && rest.endsWith("]") ->
            ParsedTokenNode(id, cleanLabel(rest.removeSurrounding("[", "]")), NodeShape.RECTANGLE)
        rest.startsWith("(") && rest.endsWith(")") ->
            ParsedTokenNode(id, cleanLabel(rest.removeSurrounding("(", ")")), NodeShape.ROUNDED)
        rest.startsWith("{") && rest.endsWith("}") ->
            ParsedTokenNode(id, cleanLabel(rest.removeSurrounding("{", "}")), NodeShape.DIAMOND)
        else -> ParsedTokenNode(id, null, NodeShape.RECTANGLE)
    }
}

private fun parseStateDiagram(lines: List<String>): FlowchartGraph? {
    val nodes = LinkedHashMap<String, FlowchartNode>()
    val edges = mutableListOf<FlowchartEdge>()
    var terminalCounter = 0

    fun stateNodeId(rawState: String, isTarget: Boolean): String {
        val cleaned = rawState.trim()
        return if (cleaned == "[*]") {
            val id = if (isTarget) "__end_${terminalCounter++}" else "__start"
            if (!nodes.containsKey(id)) {
                nodes[id] = FlowchartNode(
                    id = id,
                    label = if (isTarget) "End" else "Start",
                    shape = NodeShape.CIRCLE
                )
            }
            id
        } else {
            val safeId = cleaned.replace(Regex("""[^A-Za-z0-9_]"""), "_")
            if (!nodes.containsKey(safeId)) {
                nodes[safeId] = FlowchartNode(id = safeId, label = cleaned, shape = NodeShape.ROUNDED)
            }
            safeId
        }
    }

    val transitionRegex = Regex("""^(.+?)\s*-->\s*([^:]+?)(?::\s*(.+))?$""")
    val aliasRegex = Regex("""^state\s+"([^"]+)"\s+as\s+([A-Za-z0-9_]+)$""", RegexOption.IGNORE_CASE)

    for (i in 1 until lines.size) {
        val line = lines[i]
        val aliasMatch = aliasRegex.find(line)
        if (aliasMatch != null) {
            val label = aliasMatch.groupValues[1]
            val id = aliasMatch.groupValues[2]
            nodes[id] = FlowchartNode(id = id, label = label, shape = NodeShape.ROUNDED)
            continue
        }

        val transMatch = transitionRegex.find(line)
        if (transMatch != null) {
            val fromId = stateNodeId(transMatch.groupValues[1], isTarget = false)
            val toId = stateNodeId(transMatch.groupValues[2], isTarget = true)
            val label = transMatch.groupValues[3].trim().ifBlank { null }
            edges.add(FlowchartEdge(from = fromId, to = toId, label = label))
        }
    }

    if (nodes.isEmpty()) return null
    return FlowchartGraph(nodes = nodes.values.toList(), edges = edges, isHorizontal = false)
}

private val SEQ_PARTICIPANT_REGEX = Regex(
    """^(?:participant|actor)\s+([A-Za-z0-9_]+)(?:\s+as\s+(.+))?$""",
    RegexOption.IGNORE_CASE
)
private val SEQ_MESSAGE_REGEX = Regex(
    """^([A-Za-z0-9_ ]+?)\s*(-->>|->>|-->|->)\s*([A-Za-z0-9_ ]+?)\s*:\s*(.*)$"""
)

fun parseSequenceDiagram(raw: String): SequenceDiagramData? {
    val lines = raw.replace("\r\n", "\n")
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("%%") }
    if (lines.isEmpty() || !lines.first().lowercase().startsWith("sequencediagram")) {
        return null
    }

    val participants = LinkedHashMap<String, SequenceParticipant>()
    val messages = mutableListOf<SequenceMessage>()

    fun ensureParticipant(idOrLabel: String): String {
        val clean = idOrLabel.trim()
        val key = participants.entries.firstOrNull {
            it.key.equals(clean, ignoreCase = true) || it.value.label.equals(clean, ignoreCase = true)
        }?.key ?: clean
        if (!participants.containsKey(key)) {
            participants[key] = SequenceParticipant(id = key, label = clean)
        }
        return key
    }

    for (i in 1 until lines.size) {
        val line = lines[i]
        val partMatch = SEQ_PARTICIPANT_REGEX.find(line)
        if (partMatch != null) {
            val id = partMatch.groupValues[1].trim()
            val label = partMatch.groupValues[2].trim().trim('"', '\'').ifBlank { id }
            participants[id] = SequenceParticipant(id = id, label = label)
            continue
        }

        val msgMatch = SEQ_MESSAGE_REGEX.find(line)
        if (msgMatch != null) {
            val fromId = ensureParticipant(msgMatch.groupValues[1])
            val arrow = msgMatch.groupValues[2]
            val toId = ensureParticipant(msgMatch.groupValues[3])
            val label = msgMatch.groupValues[4].trim()
            messages.add(
                SequenceMessage(
                    from = fromId,
                    to = toId,
                    label = label,
                    isDashed = arrow.startsWith("--")
                )
            )
        }
    }

    if (participants.isEmpty() && messages.isEmpty()) return null
    return SequenceDiagramData(participants = participants.values.toList(), messages = messages)
}

// ─── Layered Topological Layout & Directed Edge Canvas ──────────────────────

private data class SubgraphBounds(
    val title: String,
    val rect: Rect
)

private data class ComputedEdgeGeometry(
    val edgeIndex: Int,
    val edge: FlowchartEdge,
    val start: Offset,
    val end: Offset,
    val c1: Offset,
    val c2: Offset,
    val labelMid: Offset,
    val arrowAngleRad: Double
)

private data class ComputedFlowchartLayout(
    val totalWidth: Int,
    val totalHeight: Int,
    val subgraphBounds: List<SubgraphBounds>,
    val edges: List<ComputedEdgeGeometry>
)

private class FlowchartGeometryCache {
    var computed: ComputedFlowchartLayout? = null
}

/**
 * Computes topological DAG ranks cleanly (safe on cyclic Mermaid graphs).
 */
internal fun assignFlowchartLayers(graph: FlowchartGraph): List<List<FlowchartNode>> {
    val nodeById = graph.nodes.associateBy { it.id }
    val adj = HashMap<String, MutableList<String>>()
    val inDegree = HashMap<String, Int>()
    graph.nodes.forEach {
        adj[it.id] = mutableListOf()
        inDegree[it.id] = 0
    }
    graph.edges.forEach { edge ->
        if (edge.from != edge.to && nodeById.containsKey(edge.from) && nodeById.containsKey(edge.to)) {
            adj[edge.from]?.add(edge.to)
            inDegree[edge.to] = (inDegree[edge.to] ?: 0) + 1
        }
    }

    // Break cycles via DFS state tracking so rank computation never hangs
    val state = HashMap<String, Int>() // 0 unvisited, 1 visiting, 2 done
    val acyclicAdj = HashMap<String, MutableList<String>>()
    graph.nodes.forEach { acyclicAdj[it.id] = mutableListOf() }

    fun dfs(u: String) {
        state[u] = 1
        for (v in adj[u].orEmpty()) {
            val s = state[v] ?: 0
            if (s == 0) {
                acyclicAdj[u]?.add(v)
                dfs(v)
            } else if (s == 2) {
                acyclicAdj[u]?.add(v)
            }
        }
        state[u] = 2
    }
    graph.nodes.forEach { if ((state[it.id] ?: 0) == 0) dfs(it.id) }

    val acyclicInDeg = HashMap<String, Int>()
    graph.nodes.forEach { acyclicInDeg[it.id] = 0 }
    acyclicAdj.values.flatten().forEach { v -> acyclicInDeg[v] = (acyclicInDeg[v] ?: 0) + 1 }

    val rank = HashMap<String, Int>()
    val queue = ArrayDeque<String>()
    graph.nodes.forEach { node ->
        if ((acyclicInDeg[node.id] ?: 0) == 0) {
            rank[node.id] = 0
            queue.addLast(node.id)
        }
    }

    while (queue.isNotEmpty()) {
        val u = queue.removeFirst()
        val nextRank = (rank[u] ?: 0) + 1
        for (v in acyclicAdj[u].orEmpty()) {
            rank[v] = max(rank[v] ?: 0, nextRank)
            val remaining = (acyclicInDeg[v] ?: 1) - 1
            acyclicInDeg[v] = remaining
            if (remaining <= 0) {
                queue.addLast(v)
            }
        }
    }

    val maxRank = rank.values.maxOrNull() ?: 0
    val grouped = LinkedHashMap<Int, MutableList<FlowchartNode>>()
    graph.nodes.forEach { node ->
        val rawRank = rank[node.id] ?: 0
        val finalRank = if (graph.isReversed) maxRank - rawRank else rawRank
        grouped.getOrPut(finalRank) { mutableListOf() }.add(node)
    }

    return grouped.keys.sorted().map { grouped[it].orEmpty() }
}

/**
 * 100% Offline Pure Compose Flowchart / State Diagram Renderer with layered node placement,
 * Bezier/orthogonal directed connectors, arrowheads, edge labels, and subgraph boxes.
 */
@Composable
fun NativeFlowchartView(graph: FlowchartGraph) {
    val colors = SovereignTheme.colors
    val density = LocalDensity.current
    val horizontalScroll = rememberScrollState()
    val verticalScroll = rememberScrollState()

    val layers = remember(graph) { assignFlowchartLayers(graph) }
    val labeledEdges = remember(graph) {
        graph.edges.mapIndexedNotNull { idx, edge ->
            if (!edge.label.isNullOrBlank()) idx to edge else null
        }
    }
    val labeledEdgeLookup = remember(labeledEdges) {
        labeledEdges.mapIndexed { chipIdx, (edgeIdx, _) -> edgeIdx to chipIdx }.toMap()
    }
    val geometryCache = remember(graph) { FlowchartGeometryCache() }

    val edgeColor = colors.textSecondary.copy(alpha = 0.85f)
    val subgraphFill = colors.surfaceBase.copy(alpha = 0.5f)
    val subgraphBorder = colors.hairlineStrong

    val outerPadPx = with(density) { 20.dp.roundToPx() }
    val nodeGapPx = with(density) { 24.dp.roundToPx() }
    val layerGapPx = with(density) { 54.dp.roundToPx() }
    val subPadPx = with(density) { 12.dp.roundToPx() }
    val subTitlePadPx = with(density) { 22.dp.roundToPx() }
    val bowStepPx = with(density) { 26.dp.toPx() }
    val arrowSizePx = with(density) { 8.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(horizontalScroll)
            .verticalScroll(verticalScroll),
        contentAlignment = Alignment.Center
    ) {
        Layout(
            modifier = Modifier.drawBehind {
                val layout = geometryCache.computed ?: return@drawBehind
                drawSubgraphs(layout.subgraphBounds, subgraphFill, subgraphBorder)
                drawFlowchartEdges(layout.edges, edgeColor, arrowSizePx)
            },
            content = {
                graph.nodes.forEach { node ->
                    FlowchartNodeCard(node = node)
                }
                labeledEdges.forEach { (_, edge) ->
                    EdgeLabelChip(text = edge.label.orEmpty())
                }
            }
        ) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val nodeCount = graph.nodes.size
            val nodePlaceables = HashMap<String, Placeable>(nodeCount)
            graph.nodes.forEachIndexed { index, node ->
                nodePlaceables[node.id] = measurables[index].measure(loose)
            }
            val labelPlaceables = labeledEdges.indices.map { idx ->
                measurables[nodeCount + idx].measure(loose)
            }

            // Compute coordinates per layer
            val nodePositions = HashMap<String, Offset>(nodeCount)
            val nodeRects = HashMap<String, Rect>(nodeCount)
            val nodeRankMap = HashMap<String, Int>(nodeCount)
            layers.forEachIndexed { r, list -> list.forEach { nodeRankMap[it.id] = r } }

            var totalW: Int
            var totalH: Int

            if (!graph.isHorizontal) {
                // Top-Down / Bottom-Up layers
                val layerDims = layers.map { layer ->
                    val sumW = layer.sumOf { nodePlaceables[it.id]?.width ?: 0 } +
                        max(0, layer.size - 1) * nodeGapPx
                    val maxH = layer.maxOfOrNull { nodePlaceables[it.id]?.height ?: 0 } ?: 0
                    sumW to maxH
                }
                val contentMaxW = layerDims.maxOfOrNull { it.first } ?: 0
                totalW = contentMaxW + outerPadPx * 2
                var y = outerPadPx
                layers.forEachIndexed { layerIdx, layer ->
                    val (rowW, rowH) = layerDims[layerIdx]
                    var x = (totalW - rowW) / 2
                    for (node in layer) {
                        val p = nodePlaceables[node.id] ?: continue
                        val topY = y + (rowH - p.height) / 2
                        nodePositions[node.id] = Offset(x.toFloat(), topY.toFloat())
                        nodeRects[node.id] = Rect(
                            x.toFloat(),
                            topY.toFloat(),
                            (x + p.width).toFloat(),
                            (topY + p.height).toFloat()
                        )
                        x += p.width + nodeGapPx
                    }
                    y += rowH + if (layerIdx < layers.lastIndex) layerGapPx else 0
                }
                totalH = y + outerPadPx
            } else {
                // Left-Right / Right-Left layers
                val layerDims = layers.map { layer ->
                    val maxW = layer.maxOfOrNull { nodePlaceables[it.id]?.width ?: 0 } ?: 0
                    val sumH = layer.sumOf { nodePlaceables[it.id]?.height ?: 0 } +
                        max(0, layer.size - 1) * nodeGapPx
                    maxW to sumH
                }
                val contentMaxH = layerDims.maxOfOrNull { it.second } ?: 0
                totalH = contentMaxH + outerPadPx * 2
                var x = outerPadPx
                layers.forEachIndexed { layerIdx, layer ->
                    val (colW, colH) = layerDims[layerIdx]
                    var y = (totalH - colH) / 2
                    for (node in layer) {
                        val p = nodePlaceables[node.id] ?: continue
                        val leftX = x + (colW - p.width) / 2
                        nodePositions[node.id] = Offset(leftX.toFloat(), y.toFloat())
                        nodeRects[node.id] = Rect(
                            leftX.toFloat(),
                            y.toFloat(),
                            (leftX + p.width).toFloat(),
                            (y + p.height).toFloat()
                        )
                        y += p.height + nodeGapPx
                    }
                    x += colW + if (layerIdx < layers.lastIndex) layerGapPx else 0
                }
                totalW = x + outerPadPx
            }

            // Group subgraph bounding boxes
            val subgraphs = graph.nodes
                .filter { !it.subgraph.isNullOrBlank() }
                .groupBy { it.subgraph!! }
                .mapNotNull { (title, subNodes) ->
                    val rects = subNodes.mapNotNull { nodeRects[it.id] }
                    if (rects.isEmpty()) null else {
                        SubgraphBounds(
                            title = title,
                            rect = Rect(
                                left = rects.minOf { it.left } - subPadPx,
                                top = rects.minOf { it.top } - subTitlePadPx,
                                right = rects.maxOf { it.right } + subPadPx,
                                bottom = rects.maxOf { it.bottom } + subPadPx
                            )
                        )
                    }
                }

            // Compute directed edge splines & midpoint label positions
            var maxRight = totalW.toFloat()
            var maxBottom = totalH.toFloat()
            val edgeGeometries = mutableListOf<ComputedEdgeGeometry>()

            graph.edges.forEachIndexed { edgeIdx, edge ->
                val fromRect = nodeRects[edge.from] ?: return@forEachIndexed
                val toRect = nodeRects[edge.to] ?: return@forEachIndexed
                val fromRank = nodeRankMap[edge.from] ?: 0
                val toRank = nodeRankMap[edge.to] ?: 0
                val isSelfLoop = edge.from == edge.to
                val isBackward = toRank <= fromRank && !isSelfLoop
                val rankSpan = kotlin.math.abs(toRank - fromRank)

                val geom = computeEdgeGeometry(
                    edgeIndex = edgeIdx,
                    edge = edge,
                    fromRect = fromRect,
                    toRect = toRect,
                    isHorizontal = graph.isHorizontal,
                    isSelfLoop = isSelfLoop,
                    isBackward = isBackward,
                    skipsLayer = rankSpan > 1,
                    bowOffset = ((edgeIdx % 3) + 1) * bowStepPx
                )
                edgeGeometries.add(geom)
                maxRight = max(maxRight, max(geom.c1.x, geom.c2.x) + outerPadPx)
                maxBottom = max(maxBottom, max(geom.c1.y, geom.c2.y) + outerPadPx)
            }

            totalW = max(totalW, maxRight.roundToInt())
            totalH = max(totalH, maxBottom.roundToInt())

            geometryCache.computed = ComputedFlowchartLayout(
                totalWidth = totalW,
                totalHeight = totalH,
                subgraphBounds = subgraphs,
                edges = edgeGeometries
            )

            layout(totalW, totalH) {
                graph.nodes.forEach { node ->
                    val p = nodePlaceables[node.id] ?: return@forEach
                    val pos = nodePositions[node.id] ?: Offset.Zero
                    p.placeRelative(pos.x.roundToInt(), pos.y.roundToInt())
                }
                edgeGeometries.forEach { geom ->
                    val chipIdx = labeledEdgeLookup[geom.edgeIndex] ?: return@forEach
                    val labelP = labelPlaceables.getOrNull(chipIdx) ?: return@forEach
                    val lx = (geom.labelMid.x - labelP.width / 2f).roundToInt().coerceIn(0, max(0, totalW - labelP.width))
                    val ly = (geom.labelMid.y - labelP.height / 2f).roundToInt().coerceIn(0, max(0, totalH - labelP.height))
                    labelP.placeRelative(lx, ly)
                }
            }
        }
    }
}

private fun computeEdgeGeometry(
    edgeIndex: Int,
    edge: FlowchartEdge,
    fromRect: Rect,
    toRect: Rect,
    isHorizontal: Boolean,
    isSelfLoop: Boolean,
    isBackward: Boolean,
    skipsLayer: Boolean,
    bowOffset: Float
): ComputedEdgeGeometry {
    if (isSelfLoop) {
        val start = Offset(fromRect.right, fromRect.top + fromRect.height * 0.3f)
        val end = Offset(fromRect.right, fromRect.bottom - fromRect.height * 0.25f)
        val c1 = Offset(fromRect.right + bowOffset * 1.4f, start.y - bowOffset * 0.4f)
        val c2 = Offset(fromRect.right + bowOffset * 1.4f, end.y + bowOffset * 0.4f)
        val mid = cubicPoint(start, c1, c2, end, 0.5f)
        val angle = atan2((end.y - c2.y).toDouble(), (end.x - c2.x).toDouble())
        return ComputedEdgeGeometry(edgeIndex, edge, start, end, c1, c2, mid, angle)
    }

    if (!isHorizontal) {
        if (isBackward) {
            val sideX = max(fromRect.right, toRect.right) + bowOffset
            val start = Offset(fromRect.right, fromRect.center.y)
            val end = Offset(toRect.right, toRect.center.y)
            val c1 = Offset(sideX, start.y)
            val c2 = Offset(sideX, end.y)
            val mid = cubicPoint(start, c1, c2, end, 0.5f)
            val angle = atan2((end.y - c2.y).toDouble(), (end.x - c2.x).toDouble())
            return ComputedEdgeGeometry(edgeIndex, edge, start, end, c1, c2, mid, angle)
        }

        val start = Offset(fromRect.center.x, fromRect.bottom)
        val end = Offset(toRect.center.x, toRect.top)
        val dy = max(24f, (end.y - start.y) * 0.48f)
        val lateralBow = if (skipsLayer) bowOffset else 0f
        val c1 = Offset(start.x + lateralBow, start.y + dy)
        val c2 = Offset(end.x + lateralBow, end.y - dy)
        val mid = cubicPoint(start, c1, c2, end, 0.5f)
        val angle = atan2((end.y - c2.y).toDouble(), (end.x - c2.x).toDouble())
        return ComputedEdgeGeometry(edgeIndex, edge, start, end, c1, c2, mid, angle)
    } else {
        if (isBackward) {
            val sideY = max(fromRect.bottom, toRect.bottom) + bowOffset
            val start = Offset(fromRect.center.x, fromRect.bottom)
            val end = Offset(toRect.center.x, toRect.bottom)
            val c1 = Offset(start.x, sideY)
            val c2 = Offset(end.x, sideY)
            val mid = cubicPoint(start, c1, c2, end, 0.5f)
            val angle = atan2((end.y - c2.y).toDouble(), (end.x - c2.x).toDouble())
            return ComputedEdgeGeometry(edgeIndex, edge, start, end, c1, c2, mid, angle)
        }

        val start = Offset(fromRect.right, fromRect.center.y)
        val end = Offset(toRect.left, toRect.center.y)
        val dx = max(24f, (end.x - start.x) * 0.48f)
        val verticalBow = if (skipsLayer) bowOffset else 0f
        val c1 = Offset(start.x + dx, start.y + verticalBow)
        val c2 = Offset(end.x - dx, end.y + verticalBow)
        val mid = cubicPoint(start, c1, c2, end, 0.5f)
        val angle = atan2((end.y - c2.y).toDouble(), (end.x - c2.x).toDouble())
        return ComputedEdgeGeometry(edgeIndex, edge, start, end, c1, c2, mid, angle)
    }
}

private fun cubicPoint(p0: Offset, p1: Offset, p2: Offset, p3: Offset, t: Float): Offset {
    val u = 1f - t
    val tt = t * t
    val uu = u * u
    val uuu = uu * u
    val ttt = tt * t
    val x = uuu * p0.x + 3f * uu * t * p1.x + 3f * u * tt * p2.x + ttt * p3.x
    val y = uuu * p0.y + 3f * uu * t * p1.y + 3f * u * tt * p2.y + ttt * p3.y
    return Offset(x, y)
}

private fun DrawScope.drawSubgraphs(
    subgraphs: List<SubgraphBounds>,
    fill: Color,
    border: Color
) {
    val radius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
    val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
    for (sub in subgraphs) {
        drawRoundRect(
            color = fill,
            topLeft = sub.rect.topLeft,
            size = Size(sub.rect.width, sub.rect.height),
            cornerRadius = radius
        )
        drawRoundRect(
            color = border,
            topLeft = sub.rect.topLeft,
            size = Size(sub.rect.width, sub.rect.height),
            cornerRadius = radius,
            style = Stroke(width = 1.5.dp.toPx(), pathEffect = dash)
        )
    }
}

private fun DrawScope.drawFlowchartEdges(
    edges: List<ComputedEdgeGeometry>,
    color: Color,
    arrowSizePx: Float
) {
    val strokePx = 1.8.dp.toPx()
    val dashedEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)

    for (geom in edges) {
        val path = Path().apply {
            moveTo(geom.start.x, geom.start.y)
            cubicTo(
                geom.c1.x, geom.c1.y,
                geom.c2.x, geom.c2.y,
                geom.end.x, geom.end.y
            )
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = strokePx,
                cap = StrokeCap.Round,
                pathEffect = if (geom.edge.isDashed) dashedEffect else null
            )
        )

        // Arrowhead pointing along final tangent at geom.end
        val tip = geom.end
        val spread = PI / 6.0
        val leftX = tip.x - arrowSizePx * cos(geom.arrowAngleRad - spread).toFloat()
        val leftY = tip.y - arrowSizePx * sin(geom.arrowAngleRad - spread).toFloat()
        val rightX = tip.x - arrowSizePx * cos(geom.arrowAngleRad + spread).toFloat()
        val rightY = tip.y - arrowSizePx * sin(geom.arrowAngleRad + spread).toFloat()
        val arrowPath = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(leftX, leftY)
            lineTo(rightX, rightY)
            close()
        }
        drawPath(arrowPath, color = color, style = Fill)
    }
}

@Composable
fun FlowchartNodeCard(node: FlowchartNode) {
    val colors = SovereignTheme.colors

    when (node.shape) {
        NodeShape.DIAMOND -> {
            val fillColor = colors.accentContainer
            val borderColor = colors.accentPrimary
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .widthIn(min = 84.dp, max = 180.dp)
                    .heightIn(min = 52.dp)
                    .drawBehind {
                        val w = size.width
                        val h = size.height
                        val diamond = Path().apply {
                            moveTo(w / 2f, 0f)
                            lineTo(w, h / 2f)
                            lineTo(w / 2f, h)
                            lineTo(0f, h / 2f)
                            close()
                        }
                        drawPath(diamond, color = fillColor, style = Fill)
                        drawPath(diamond, color = borderColor, style = Stroke(width = 1.5.dp.toPx()))
                    }
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Text(
                    text = node.label,
                    style = SovereignTypography.bodyCompact.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.onAccentContainer,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        NodeShape.CIRCLE -> {
            Surface(
                shape = CircleShape,
                color = colors.accentContainer,
                border = BorderStroke(1.5.dp, colors.accentPrimary),
                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = node.label,
                        style = SovereignTypography.monoTag,
                        color = colors.onAccentContainer,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        NodeShape.STADIUM, NodeShape.ROUNDED, NodeShape.RECTANGLE -> {
            val shape = when (node.shape) {
                NodeShape.STADIUM -> RoundedCornerShape(50)
                NodeShape.ROUNDED -> RoundedCornerShape(14.dp)
                else -> SovereignTheme.shapes.sm
            }
            Surface(
                shape = shape,
                color = colors.surfaceHigh,
                border = BorderStroke(1.5.dp, colors.accentPrimary.copy(alpha = 0.75f)),
                modifier = Modifier.widthIn(min = 72.dp, max = 188.dp)
            ) {
                Text(
                    text = node.label,
                    style = SovereignTypography.bodyCompact.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun EdgeLabelChip(text: String) {
    val colors = SovereignTheme.colors
    Surface(
        shape = SovereignTheme.shapes.pill,
        color = colors.surfaceHigh,
        border = BorderStroke(SovereignElevation.hairlineWidth, colors.hairlineStrong)
    ) {
        Text(
            text = text,
            style = SovereignTypography.monoTag.copy(fontSize = 10.sp),
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

// ─── Offline Sequence Diagram Renderer ──────────────────────────────────────

@Composable
private fun NativeSequenceDiagramView(diagram: SequenceDiagramData) {
    val colors = SovereignTheme.colors
    val participants = diagram.participants
    val colWidth = 132.dp
    val rowHeight = 56.dp
    val horizontalScroll = rememberScrollState()
    val verticalScroll = rememberScrollState()
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(horizontalScroll)
            .verticalScroll(verticalScroll)
            .padding(12.dp)
    ) {
        val totalCols = max(1, participants.size)
        val totalWidth = colWidth * totalCols
        val indexById = remember(participants) {
            participants.mapIndexed { idx, p -> p.id to idx }.toMap()
        }

        Column(modifier = Modifier.width(totalWidth)) {
            // Participant Headers
            Row(modifier = Modifier.fillMaxWidth()) {
                participants.forEach { participant ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .width(colWidth)
                            .padding(horizontal = 6.dp)
                    ) {
                        Surface(
                            shape = SovereignTheme.shapes.sm,
                            color = colors.surfaceHigh,
                            border = BorderStroke(1.5.dp, colors.accentPrimary.copy(alpha = 0.75f))
                        ) {
                            Text(
                                text = participant.label,
                                style = SovereignTypography.bodyCompact.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.textPrimary,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // Lifelines & Sequential Message Arrows
            Box(
                modifier = Modifier
                    .width(totalWidth)
                    .height(rowHeight * max(1, diagram.messages.size) + 20.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val colPx = size.width / totalCols
                    val rowPx = rowHeight.toPx()
                    val arrowLen = 8.dp.toPx()

                    // Lifelines
                    for (idx in participants.indices) {
                        val x = colPx * idx + colPx / 2f
                        drawLine(
                            color = colors.hairlineStrong,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = dashEffect
                        )
                    }

                    // Messages
                    diagram.messages.forEachIndexed { msgIdx, msg ->
                        val fromIdx = indexById[msg.from] ?: 0
                        val toIdx = indexById[msg.to] ?: 0
                        val fromX = colPx * fromIdx + colPx / 2f
                        val toX = colPx * toIdx + colPx / 2f
                        val y = (msgIdx + 0.65f) * rowPx

                        if (fromIdx == toIdx) {
                            val loopW = colPx * 0.35f
                            val path = Path().apply {
                                moveTo(fromX, y - 10.dp.toPx())
                                lineTo(fromX + loopW, y - 10.dp.toPx())
                                lineTo(fromX + loopW, y + 10.dp.toPx())
                                lineTo(fromX, y + 10.dp.toPx())
                            }
                            drawPath(
                                path = path,
                                color = colors.accentPrimary,
                                style = Stroke(
                                    width = 1.8.dp.toPx(),
                                    pathEffect = if (msg.isDashed) dashEffect else null
                                )
                            )
                            val tipX = fromX
                            val tipY = y + 10.dp.toPx()
                            val arrow = Path().apply {
                                moveTo(tipX, tipY)
                                lineTo(tipX + arrowLen, tipY - arrowLen * 0.55f)
                                lineTo(tipX + arrowLen, tipY + arrowLen * 0.55f)
                                close()
                            }
                            drawPath(arrow, color = colors.accentPrimary, style = Fill)
                        } else {
                            drawLine(
                                color = colors.accentPrimary,
                                start = Offset(fromX, y),
                                end = Offset(toX, y),
                                strokeWidth = 1.8.dp.toPx(),
                                pathEffect = if (msg.isDashed) dashEffect else null
                            )
                            val dir = if (toX > fromX) -1f else 1f
                            val arrow = Path().apply {
                                moveTo(toX, y)
                                lineTo(toX + dir * arrowLen, y - arrowLen * 0.55f)
                                lineTo(toX + dir * arrowLen, y + arrowLen * 0.55f)
                                close()
                            }
                            drawPath(arrow, color = colors.accentPrimary, style = Fill)
                        }
                    }
                }

                // Message labels above each arrow
                Column(modifier = Modifier.fillMaxSize()) {
                    diagram.messages.forEach { msg ->
                        val fromIdx = indexById[msg.from] ?: 0
                        val toIdx = indexById[msg.to] ?: 0
                        val leftCol = min(fromIdx, toIdx)
                        val spanCols = max(1, kotlin.math.abs(toIdx - fromIdx))

                        Box(
                            contentAlignment = Alignment.TopCenter,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(rowHeight)
                                .padding(top = 6.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                if (leftCol > 0) {
                                    Spacer(Modifier.width(colWidth * leftCol))
                                }
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.width(colWidth * spanCols)
                                ) {
                                    Surface(
                                        shape = SovereignTheme.shapes.pill,
                                        color = colors.surfaceHigh,
                                        border = BorderStroke(SovereignElevation.hairlineWidth, colors.hairlineSubtle)
                                    ) {
                                        Text(
                                            text = msg.label,
                                            style = SovereignTypography.monoTag.copy(fontSize = 10.sp),
                                            color = colors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
