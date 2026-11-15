package com.example.on_device_qwen.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.example.on_device_qwen.ui.designsystem.SovereignBadge
import com.example.on_device_qwen.ui.designsystem.SovereignDivider
import com.example.on_device_qwen.ui.designsystem.SovereignElevation
import com.example.on_device_qwen.ui.designsystem.SovereignHairlineCard
import com.example.on_device_qwen.ui.designsystem.SovereignIconButton
import com.example.on_device_qwen.ui.designsystem.SovereignModalSheet
import com.example.on_device_qwen.ui.designsystem.SovereignSecondaryButton
import com.example.on_device_qwen.ui.designsystem.SovereignSegmentedControl
import com.example.on_device_qwen.ui.designsystem.SovereignStatusTone
import com.example.on_device_qwen.ui.designsystem.SovereignSurface
import com.example.on_device_qwen.ui.designsystem.SovereignTheme
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class DiagramTab(val label: String) {
    DIAGRAM("Diagram"),
    SOURCE("Source")
}

/**
 * 100% Offline-First Native Mermaid Diagram Visualizer.
 * Supports:
 * - Flowchart / Graph (TD, LR, BT, RL) with shapes, topological layering, edge labels, and back-edges
 * - Sequence Diagrams (actors, lifelines, request/response arrows, self-messages, notes, blocks)
 * - State Diagrams (start/end terminal states, state capsules, labeled transitions)
 * - Graceful structured offline fallback for class/ER/gantt/pie/mindmap or partial streaming blocks
 * - "Diagram" / "Source" segmented toggle, 48.dp Copy action, and fullscreen SovereignModalSheet viewer
 */
@Composable
fun MermaidDiagramView(
    diagramCode: String,
    diagramType: String = "mermaid",
    modifier: Modifier = Modifier
) {
    val colors = SovereignTheme.colors
    val shapes = SovereignTheme.shapes
    val motion = SovereignTheme.motion
    val context = LocalContext.current

    var selectedTab by remember { mutableStateOf(DiagramTab.DIAGRAM) }
    var isFullscreen by remember { mutableStateOf(false) }
    var isCopied by remember { mutableStateOf(false) }

    val parsedDiagram = remember(diagramCode, diagramType) {
        parseMermaidDiagram(diagramCode, diagramType)
    }
    val highlightedSource = remember(diagramCode, colors) {
        highlightCodeSyntax(diagramCode, "mermaid", colors)
    }

    LaunchedEffect(isCopied) {
        if (isCopied) {
            delay(1800)
            isCopied = false
        }
    }

    val copyDiagramCode = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("mermaid", diagramCode))
        isCopied = true
    }

    if (isFullscreen) {
        FullscreenDiagramModalSheet(
            parsedDiagram = parsedDiagram,
            highlightedSource = highlightedSource,
            initialTab = selectedTab,
            isCopied = isCopied,
            onCopy = copyDiagramCode,
            onDismiss = { isFullscreen = false }
        )
    }

    SovereignHairlineCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = shapes.md,
        containerColor = colors.surfaceLow,
        borderColor = colors.hairlineSubtle,
        contentPadding = PaddingValues(0.dp)
    ) {
        // Header bar: title + badge + Copy + Fullscreen
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.codeHeader)
                .padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AccountTree,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = colors.accentPrimary
                )
                Text(
                    text = "Diagram",
                    style = SovereignTheme.typography.monoTag,
                    color = colors.textSecondary,
                    maxLines = 1
                )
                SovereignBadge(
                    text = parsedDiagram.kindLabel,
                    tone = SovereignStatusTone.ACCENT
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
                    onClick = copyDiagramCode,
                    contentDescription = if (isCopied) "Copied diagram source" else "Copy diagram source",
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

                SovereignIconButton(
                    onClick = { isFullscreen = true },
                    contentDescription = "Expand diagram fullscreen",
                    visualSize = 36.dp,
                    contentColor = colors.textSecondary,
                    hapticFeedback = true
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Fullscreen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Segmented toggle: Diagram | Source
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.codeHeader)
                .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
        ) {
            SovereignSegmentedControl(
                options = listOf(DiagramTab.DIAGRAM, DiagramTab.SOURCE),
                selected = selectedTab,
                onSelect = { selectedTab = it },
                label = { it.label }
            )
        }

        SovereignDivider()

        if (selectedTab == DiagramTab.DIAGRAM) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 380.dp)
                    .clickable(onClickLabel = "Expand diagram fullscreen") {
                        isFullscreen = true
                    }
                    .semantics {
                        role = Role.Button
                        contentDescription = "Diagram preview, tap to expand fullscreen"
                    }
            ) {
                MermaidContentRendererInternal(
                    parsedDiagram = parsedDiagram,
                    onSwitchToSource = { selectedTab = DiagramTab.SOURCE },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            SelectionContainer {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = highlightedSource,
                        style = SovereignTheme.typography.codeBody,
                        color = colors.textPrimary
                    )
                }
            }
        }
    }
}

/**
 * Fullscreen modal viewer hosted in [SovereignModalSheet] with bidirectional scrolling,
 * zoom controls, Diagram/Source switch, and Copy action.
 */
@Composable
private fun FullscreenDiagramModalSheet(
    parsedDiagram: ParsedMermaidDiagram,
    highlightedSource: androidx.compose.ui.text.AnnotatedString,
    initialTab: DiagramTab,
    isCopied: Boolean,
    onCopy: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = SovereignTheme.colors
    var sheetTab by remember { mutableStateOf(initialTab) }
    var zoomScale by remember { mutableFloatStateOf(1f) }

    SovereignModalSheet(
        title = "Diagram Viewer",
        subtitle = parsedDiagram.kindLabel,
        onDismissRequest = onDismiss,
        headerActions = {
            if (sheetTab == DiagramTab.DIAGRAM) {
                SovereignIconButton(
                    onClick = { zoomScale = (zoomScale - 0.25f).coerceAtLeast(0.6f) },
                    contentDescription = "Zoom out diagram",
                    visualSize = 36.dp,
                    contentColor = colors.textSecondary,
                    hapticFeedback = true
                ) {
                    Icon(Icons.Rounded.ZoomOut, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                SovereignIconButton(
                    onClick = { zoomScale = 1f },
                    contentDescription = "Reset diagram zoom",
                    visualSize = 36.dp,
                    contentColor = if (zoomScale == 1f) colors.textMuted else colors.accentPrimary,
                    hapticFeedback = true
                ) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                SovereignIconButton(
                    onClick = { zoomScale = (zoomScale + 0.25f).coerceAtMost(2.0f) },
                    contentDescription = "Zoom in diagram",
                    visualSize = 36.dp,
                    contentColor = colors.textSecondary,
                    hapticFeedback = true
                ) {
                    Icon(Icons.Rounded.ZoomIn, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }

            SovereignIconButton(
                onClick = onCopy,
                contentDescription = if (isCopied) "Copied diagram source" else "Copy diagram source",
                visualSize = 36.dp,
                contentColor = if (isCopied) colors.success else colors.textSecondary,
                hapticFeedback = true
            ) {
                Icon(
                    imageVector = if (isCopied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        stickyHeaderExtra = {
            SovereignSegmentedControl(
                options = listOf(DiagramTab.DIAGRAM, DiagramTab.SOURCE),
                selected = sheetTab,
                onSelect = { sheetTab = it },
                label = { it.label }
            )
        }
    ) {
        if (sheetTab == DiagramTab.DIAGRAM) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(colors.surfaceLow),
                contentAlignment = Alignment.Center
            ) {
                MermaidContentRendererInternal(
                    parsedDiagram = parsedDiagram,
                    onSwitchToSource = { sheetTab = DiagramTab.SOURCE },
                    zoomScale = zoomScale,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            SelectionContainer {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(colors.codeBackground)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Text(
                        text = highlightedSource,
                        style = SovereignTheme.typography.codeBody,
                        color = colors.textPrimary
                    )
                }
            }
        }
    }
}

/**
 * Renders parsed Mermaid diagrams 100% offline using native Canvas and Compose layouts.
 */
@Composable
fun MermaidContentRenderer(diagramCode: String) {
    val parsed = remember(diagramCode) { parseMermaidDiagram(diagramCode) }
    MermaidContentRendererInternal(
        parsedDiagram = parsed,
        onSwitchToSource = null,
        zoomScale = 1f,
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun MermaidContentRendererInternal(
    parsedDiagram: ParsedMermaidDiagram,
    onSwitchToSource: (() -> Unit)?,
    zoomScale: Float = 1f,
    modifier: Modifier = Modifier
) {
    when (parsedDiagram) {
        is ParsedMermaidDiagram.Flowchart -> {
            NativeFlowchartView(
                graph = parsedDiagram.graph,
                zoomScale = zoomScale,
                modifier = modifier
            )
        }
        is ParsedMermaidDiagram.Sequence -> {
            NativeSequenceDiagramView(
                diagram = parsedDiagram.diagram,
                zoomScale = zoomScale,
                modifier = modifier
            )
        }
        is ParsedMermaidDiagram.State -> {
            NativeFlowchartView(
                graph = parsedDiagram.diagram.asFlowchartGraph(),
                zoomScale = zoomScale,
                modifier = modifier
            )
        }
        is ParsedMermaidDiagram.StructuredFallback -> {
            StructuredDiagramFallbackView(
                fallback = parsedDiagram,
                onSwitchToSource = onSwitchToSource,
                modifier = modifier
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Pure Offline Mermaid Parsers (Zero Allocations in Regex Compilation)
// ─────────────────────────────────────────────────────────────────────────────

enum class FlowchartNodeShape {
    RECTANGLE,
    ROUNDED,
    STADIUM,
    DIAMOND,
    CIRCLE,
    DATABASE,
    HEXAGON,
    STATE_START,
    STATE_END
}

enum class EdgeStyle {
    SOLID_ARROW,
    DASHED_ARROW,
    THICK_ARROW,
    SOLID_LINE
}

enum class FlowchartDirection {
    TD, LR, BT, RL
}

data class FlowchartNode(
    val id: String,
    val label: String,
    val shape: FlowchartNodeShape = FlowchartNodeShape.RECTANGLE
)

data class FlowchartEdge(
    val from: String,
    val to: String,
    val label: String? = null,
    val style: EdgeStyle = EdgeStyle.SOLID_ARROW
)

data class FlowchartGraph(
    val nodes: List<FlowchartNode>,
    val edges: List<FlowchartEdge>,
    val isHorizontal: Boolean,
    val direction: FlowchartDirection = if (isHorizontal) FlowchartDirection.LR else FlowchartDirection.TD
)

data class SequenceParticipant(
    val id: String,
    val displayName: String,
    val isActor: Boolean = false
)

sealed interface SequenceItem {
    data class Message(
        val fromId: String,
        val toId: String,
        val label: String,
        val isDashed: Boolean,
        val isSelf: Boolean
    ) : SequenceItem

    data class Note(
        val participantIds: List<String>,
        val text: String
    ) : SequenceItem

    data class BlockMarker(
        val kind: String,
        val label: String
    ) : SequenceItem
}

data class SequenceDiagramGraph(
    val participants: List<SequenceParticipant>,
    val items: List<SequenceItem>
)

data class StateDiagramGraph(
    val nodes: List<FlowchartNode>,
    val edges: List<FlowchartEdge>,
    val direction: FlowchartDirection = FlowchartDirection.TD
) {
    fun asFlowchartGraph(): FlowchartGraph = FlowchartGraph(
        nodes = nodes,
        edges = edges,
        isHorizontal = direction == FlowchartDirection.LR || direction == FlowchartDirection.RL,
        direction = direction
    )
}

sealed interface ParsedMermaidDiagram {
    val kindLabel: String

    data class Flowchart(val graph: FlowchartGraph) : ParsedMermaidDiagram {
        override val kindLabel: String = "Flowchart"
    }

    data class Sequence(val diagram: SequenceDiagramGraph) : ParsedMermaidDiagram {
        override val kindLabel: String = "Sequence"
    }

    data class State(val diagram: StateDiagramGraph) : ParsedMermaidDiagram {
        override val kindLabel: String = "State"
    }

    data class StructuredFallback(
        override val kindLabel: String,
        val summaryLines: List<String>,
        val rawPreview: String
    ) : ParsedMermaidDiagram
}

// Top-level hoisted Regexes — compiled once per process.
private val FLOWCHART_EDGE_REGEX = Regex(
    """(-->|-.->|-..->|==>|---)\s*\|([^|]*)\||""" +
        """--\s+([^-\n]+?)\s+-->|""" +
        """-\.\s*([^.\n]+?)\s*\.->|""" +
        """==\s+([^=\n]+?)\s+==>|""" +
        """(-->|-.->|-..->|==>|---)"""
)
private val NODE_CLASS_SUFFIX_REGEX = Regex(""":::[A-Za-z0-9_-]+\s*$""")
private val NODE_STADIUM_REGEX = Regex("""^([A-Za-z0-9_.-]+)\s*\(\[(.+)]\)$""")
private val NODE_DATABASE_REGEX = Regex("""^([A-Za-z0-9_.-]+)\s*\[\((.+)\)]$""")
private val NODE_CIRCLE_REGEX = Regex("""^([A-Za-z0-9_.-]+)\s*\(\((.+)\)\)$""")
private val NODE_HEXAGON_REGEX = Regex("""^([A-Za-z0-9_.-]+)\s*\{\{(.+)}}$""")
private val NODE_RECT_REGEX = Regex("""^([A-Za-z0-9_.-]+)\s*\[(.+)]$""")
private val NODE_ROUNDED_REGEX = Regex("""^([A-Za-z0-9_.-]+)\s*\((.+)\)$""")
private val NODE_DIAMOND_REGEX = Regex("""^([A-Za-z0-9_.-]+)\s*\{(.+)}$""")
private val NODE_BARE_ID_REGEX = Regex("""^([A-Za-z0-9_.-]+)$""")
private val HTML_BR_REGEX = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)

private val SEQ_PARTICIPANT_REGEX = Regex(
    """^(participant|actor)\s+([A-Za-z0-9_]+(?:[.-][A-Za-z0-9_]+)*)(?:\s+as\s+(.+))?$""",
    RegexOption.IGNORE_CASE
)
private val SEQ_MESSAGE_REGEX = Regex(
    """^([A-Za-z0-9_]+(?:[.-][A-Za-z0-9_]+)*)\s*(-->>|->>|-->|->|--x|-x|--\)|-\))\s*[+\-]?([A-Za-z0-9_]+(?:[.-][A-Za-z0-9_]+)*)\s*:\s*(.*)$"""
)
private val SEQ_NOTE_REGEX = Regex(
    """^Note\s+(?:left of|right of|over)\s+([^:]+)\s*:\s*(.*)$""",
    RegexOption.IGNORE_CASE
)
private val SEQ_BLOCK_REGEX = Regex(
    """^(alt|else|opt|loop|par|and|critical|break)\b\s*(.*)$""",
    RegexOption.IGNORE_CASE
)

private val STATE_ALIAS_REGEX = Regex(
    """^state\s+"([^"]+)"\s+as\s+([A-Za-z0-9_]+(?:[.-][A-Za-z0-9_]+)*)$""",
    RegexOption.IGNORE_CASE
)
private val STATE_DESC_REGEX = Regex("""^([A-Za-z0-9_]+(?:[.-][A-Za-z0-9_]+)*)\s*:\s*(.+)$""")
private val STATE_TRANSITION_REGEX = Regex(
    """^(\[\*]|[A-Za-z0-9_]+(?:[.-][A-Za-z0-9_]+)*)\s*(-->|->)\s*(\[\*]|[A-Za-z0-9_]+(?:[.-][A-Za-z0-9_]+)*)(?:\s*:\s*(.*))?$"""
)
private val NON_EMPTY_LINE_SPLIT_REGEX = Regex("""\r?\n""")

fun parseMermaidDiagram(raw: String, hintType: String = "mermaid"): ParsedMermaidDiagram {
    val lines = raw.split(NON_EMPTY_LINE_SPLIT_REGEX)
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("%%") }

    if (lines.isEmpty()) {
        return ParsedMermaidDiagram.StructuredFallback(
            kindLabel = hintType.ifBlank { "Mermaid" }.replaceFirstChar { it.uppercase() },
            summaryLines = emptyList(),
            rawPreview = raw.trim()
        )
    }

    val header = lines.first().lowercase()
    val hint = hintType.trim().lowercase()
    val effectiveRaw = when {
        header.startsWith("graph") || header.startsWith("flowchart") ||
            header.startsWith("sequencediagram") || header.startsWith("statediagram") ||
            header.startsWith("classdiagram") || header.startsWith("erdiagram") -> raw
        hint == "sequencediagram" -> "sequenceDiagram\n$raw"
        hint.startsWith("statediagram") -> "stateDiagram-v2\n$raw"
        hint == "flowchart" || hint == "graph" -> "flowchart TD\n$raw"
        hint == "classdiagram" -> "classDiagram\n$raw"
        hint == "erdiagram" -> "erDiagram\n$raw"
        else -> raw
    }
    val effectiveLines = if (effectiveRaw === raw) lines else effectiveRaw.split(NON_EMPTY_LINE_SPLIT_REGEX).map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("%%") }
    val effectiveHeader = effectiveLines.first().lowercase()

    return when {
        effectiveHeader.startsWith("graph") || effectiveHeader.startsWith("flowchart") -> {
            val graph = parseSimpleGraph(effectiveRaw)
            if (graph != null && graph.nodes.isNotEmpty()) {
                ParsedMermaidDiagram.Flowchart(graph)
            } else {
                buildStructuredFallback("Flowchart", effectiveLines, raw)
            }
        }
        effectiveHeader.startsWith("sequencediagram") -> {
            val seq = parseSequenceDiagram(effectiveRaw)
            if (seq != null && (seq.participants.isNotEmpty() || seq.items.isNotEmpty())) {
                ParsedMermaidDiagram.Sequence(seq)
            } else {
                buildStructuredFallback("Sequence", effectiveLines, raw)
            }
        }
        effectiveHeader.startsWith("statediagram") -> {
            val state = parseStateDiagram(effectiveRaw)
            if (state != null && state.nodes.isNotEmpty()) {
                ParsedMermaidDiagram.State(state)
            } else {
                buildStructuredFallback("State", effectiveLines, raw)
            }
        }
        effectiveHeader.startsWith("classdiagram") -> buildStructuredFallback("Class Diagram", effectiveLines, raw)
        effectiveHeader.startsWith("erdiagram") -> buildStructuredFallback("ER Diagram", effectiveLines, raw)
        effectiveHeader.startsWith("gantt") -> buildStructuredFallback("Gantt", effectiveLines, raw)
        effectiveHeader.startsWith("pie") -> buildStructuredFallback("Pie Chart", effectiveLines, raw)
        effectiveHeader.startsWith("mindmap") -> buildStructuredFallback("Mindmap", effectiveLines, raw)
        else -> {
            val fallbackGraph = parseSimpleGraph(effectiveRaw)
            if (fallbackGraph != null && fallbackGraph.nodes.isNotEmpty()) {
                ParsedMermaidDiagram.Flowchart(fallbackGraph)
            } else {
                buildStructuredFallback(
                    kindLabel = hintType.ifBlank { "Mermaid" }.replaceFirstChar { it.uppercase() },
                    lines = effectiveLines,
                    raw = raw
                )
            }
        }
    }
}

private fun buildStructuredFallback(
    kindLabel: String,
    lines: List<String>,
    raw: String
): ParsedMermaidDiagram.StructuredFallback {
    val summary = lines.drop(1)
        .filter { !it.startsWith("style ") && !it.startsWith("classDef ") && it != "}" && it != "end" }
        .take(12)
    return ParsedMermaidDiagram.StructuredFallback(
        kindLabel = kindLabel,
        summaryLines = summary,
        rawPreview = raw.trim()
    )
}

/**
 * Extracts nodes (with shapes), directed edges (with labels & styles), and direction
 * from Mermaid flowchart/graph syntax.
 */
fun parseSimpleGraph(raw: String): FlowchartGraph? {
    val lines = raw.split(NON_EMPTY_LINE_SPLIT_REGEX)
        .map { it.trim().removeSuffix(";").trim() }
        .filter { it.isNotEmpty() && !it.startsWith("%%") }
    if (lines.isEmpty()) return null

    val firstLine = lines.first().lowercase()
    if (!firstLine.startsWith("graph") && !firstLine.startsWith("flowchart")) {
        return null
    }

    val direction = when {
        firstLine.contains(" lr") || firstLine.endsWith("lr") -> FlowchartDirection.LR
        firstLine.contains(" rl") || firstLine.endsWith("rl") -> FlowchartDirection.RL
        firstLine.contains(" bt") || firstLine.endsWith("bt") -> FlowchartDirection.BT
        else -> FlowchartDirection.TD
    }
    val isHorizontal = direction == FlowchartDirection.LR || direction == FlowchartDirection.RL

    val nodesMap = LinkedHashMap<String, FlowchartNode>()
    val edges = mutableListOf<FlowchartEdge>()

    fun upsertNode(node: FlowchartNode) {
        val existing = nodesMap[node.id]
        if (existing == null) {
            nodesMap[node.id] = node
        } else {
            val incomingRich = node.label != node.id || node.shape != FlowchartNodeShape.RECTANGLE
            val existingPlain = existing.label == existing.id && existing.shape == FlowchartNodeShape.RECTANGLE
            if (incomingRich && (existingPlain || node.label != node.id)) {
                nodesMap[node.id] = node
            }
        }
    }

    for (i in 1 until lines.size) {
        val line = lines[i]
        val lower = line.lowercase()
        if (lower.startsWith("subgraph") ||
            lower == "end" ||
            lower.startsWith("direction ") ||
            lower.startsWith("style ") ||
            lower.startsWith("classdef ") ||
            lower.startsWith("class ") ||
            lower.startsWith("linkstyle ") ||
            lower.startsWith("click ")
        ) {
            continue
        }

        val matches = FLOWCHART_EDGE_REGEX.findAll(line).toList()
        if (matches.isEmpty()) {
            // Standalone node declaration(s)
            line.split('&').forEach { part ->
                parseFlowchartNodeToken(part.trim())?.let { upsertNode(it) }
            }
            continue
        }

        val segments = ArrayList<String>(matches.size + 1)
        var cursor = 0
        for (m in matches) {
            segments.add(line.substring(cursor, m.range.first).trim())
            cursor = m.range.last + 1
        }
        segments.add(line.substring(cursor).trim())

        for (segIdx in matches.indices) {
            val fromSeg = segments[segIdx]
            val toSeg = segments[segIdx + 1]
            if (fromSeg.isEmpty() || toSeg.isEmpty()) continue

            val op = matches[segIdx]
            val (edgeStyle, edgeLabel) = parseEdgeOperator(op)

            val fromNodes = fromSeg.split('&').mapNotNull { parseFlowchartNodeToken(it.trim()) }
            val toNodes = toSeg.split('&').mapNotNull { parseFlowchartNodeToken(it.trim()) }

            fromNodes.forEach { upsertNode(it) }
            toNodes.forEach { upsertNode(it) }

            for (fn in fromNodes) {
                for (tn in toNodes) {
                    edges.add(
                        FlowchartEdge(
                            from = fn.id,
                            to = tn.id,
                            label = edgeLabel,
                            style = edgeStyle
                        )
                    )
                }
            }
        }
    }

    if (nodesMap.isEmpty()) return null
    return FlowchartGraph(
        nodes = nodesMap.values.toList(),
        edges = edges,
        isHorizontal = isHorizontal,
        direction = direction
    )
}

private fun parseEdgeOperator(match: MatchResult): Pair<EdgeStyle, String?> {
    val g1 = match.groupValues[1]
    val g2 = match.groupValues[2]
    val g3 = match.groupValues[3]
    val g4 = match.groupValues[4]
    val g5 = match.groupValues[5]
    val g6 = match.groupValues[6]

    return when {
        g1.isNotEmpty() -> {
            val style = edgeStyleFromToken(g1)
            val label = cleanLabel(g2).ifBlank { null }
            style to label
        }
        g3.isNotEmpty() -> EdgeStyle.SOLID_ARROW to cleanLabel(g3).ifBlank { null }
        g4.isNotEmpty() -> EdgeStyle.DASHED_ARROW to cleanLabel(g4).ifBlank { null }
        g5.isNotEmpty() -> EdgeStyle.THICK_ARROW to cleanLabel(g5).ifBlank { null }
        else -> edgeStyleFromToken(g6) to null
    }
}

private fun edgeStyleFromToken(token: String): EdgeStyle = when {
    token.contains('.') -> EdgeStyle.DASHED_ARROW
    token.contains('=') -> EdgeStyle.THICK_ARROW
    token == "---" -> EdgeStyle.SOLID_LINE
    else -> EdgeStyle.SOLID_ARROW
}

private fun parseFlowchartNodeToken(rawToken: String): FlowchartNode? {
    val token = NODE_CLASS_SUFFIX_REGEX.replace(rawToken.trim(), "").trim()
    if (token.isEmpty()) return null

    NODE_STADIUM_REGEX.matchEntire(token)?.let {
        return FlowchartNode(it.groupValues[1], cleanLabel(it.groupValues[2]), FlowchartNodeShape.STADIUM)
    }
    NODE_DATABASE_REGEX.matchEntire(token)?.let {
        return FlowchartNode(it.groupValues[1], cleanLabel(it.groupValues[2]), FlowchartNodeShape.DATABASE)
    }
    NODE_CIRCLE_REGEX.matchEntire(token)?.let {
        return FlowchartNode(it.groupValues[1], cleanLabel(it.groupValues[2]), FlowchartNodeShape.CIRCLE)
    }
    NODE_HEXAGON_REGEX.matchEntire(token)?.let {
        return FlowchartNode(it.groupValues[1], cleanLabel(it.groupValues[2]), FlowchartNodeShape.HEXAGON)
    }
    NODE_RECT_REGEX.matchEntire(token)?.let {
        return FlowchartNode(it.groupValues[1], cleanLabel(it.groupValues[2]), FlowchartNodeShape.RECTANGLE)
    }
    NODE_ROUNDED_REGEX.matchEntire(token)?.let {
        return FlowchartNode(it.groupValues[1], cleanLabel(it.groupValues[2]), FlowchartNodeShape.ROUNDED)
    }
    NODE_DIAMOND_REGEX.matchEntire(token)?.let {
        return FlowchartNode(it.groupValues[1], cleanLabel(it.groupValues[2]), FlowchartNodeShape.DIAMOND)
    }
    NODE_BARE_ID_REGEX.matchEntire(token)?.let {
        val id = it.groupValues[1]
        return FlowchartNode(id, id, FlowchartNodeShape.RECTANGLE)
    }
    return null
}

private fun cleanLabel(raw: String): String {
    var trimmed = raw.trim()
    if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
        (trimmed.startsWith("'") && trimmed.endsWith("'"))
    ) {
        if (trimmed.length >= 2) {
            trimmed = trimmed.substring(1, trimmed.length - 1).trim()
        }
    }
    return HTML_BR_REGEX.replace(trimmed, "\n")
}

fun parseSequenceDiagram(raw: String): SequenceDiagramGraph? {
    val lines = raw.split(NON_EMPTY_LINE_SPLIT_REGEX)
        .map { it.trim().removeSuffix(";").trim() }
        .filter { it.isNotEmpty() && !it.startsWith("%%") }
    if (lines.isEmpty() || !lines.first().lowercase().startsWith("sequencediagram")) {
        return null
    }

    val participants = LinkedHashMap<String, SequenceParticipant>()
    val items = mutableListOf<SequenceItem>()

    fun ensureParticipant(id: String) {
        if (!participants.containsKey(id)) {
            participants[id] = SequenceParticipant(id = id, displayName = id)
        }
    }

    for (i in 1 until lines.size) {
        val line = lines[i]
        val lower = line.lowercase()
        if (lower == "autonumber" ||
            lower.startsWith("activate ") ||
            lower.startsWith("deactivate ") ||
            lower == "end"
        ) {
            continue
        }

        SEQ_PARTICIPANT_REGEX.matchEntire(line)?.let { match ->
            val isActor = match.groupValues[1].equals("actor", ignoreCase = true)
            val id = match.groupValues[2]
            val alias = cleanLabel(match.groupValues[3]).ifBlank { id }
            participants[id] = SequenceParticipant(id = id, displayName = alias, isActor = isActor)
            continue
        }

        SEQ_MESSAGE_REGEX.matchEntire(line)?.let { match ->
            val from = match.groupValues[1]
            val arrow = match.groupValues[2]
            val to = match.groupValues[3]
            val text = cleanLabel(match.groupValues[4])
            ensureParticipant(from)
            ensureParticipant(to)
            items.add(
                SequenceItem.Message(
                    fromId = from,
                    toId = to,
                    label = text,
                    isDashed = arrow.startsWith("--"),
                    isSelf = from == to
                )
            )
            continue
        }

        SEQ_NOTE_REGEX.matchEntire(line)?.let { match ->
            val targets = match.groupValues[1].split(',').map { it.trim() }.filter { it.isNotEmpty() }
            targets.forEach { ensureParticipant(it) }
            items.add(SequenceItem.Note(targets, cleanLabel(match.groupValues[2])))
            continue
        }

        SEQ_BLOCK_REGEX.matchEntire(line)?.let { match ->
            items.add(
                SequenceItem.BlockMarker(
                    kind = match.groupValues[1].uppercase(),
                    label = cleanLabel(match.groupValues[2])
                )
            )
        }
    }

    if (participants.isEmpty() && items.isEmpty()) return null
    return SequenceDiagramGraph(
        participants = participants.values.toList(),
        items = items
    )
}

fun parseStateDiagram(raw: String): StateDiagramGraph? {
    val lines = raw.split(NON_EMPTY_LINE_SPLIT_REGEX)
        .map { it.trim().removeSuffix(";").trim() }
        .filter { it.isNotEmpty() && !it.startsWith("%%") }
    if (lines.isEmpty() || !lines.first().lowercase().startsWith("statediagram")) {
        return null
    }

    var direction = FlowchartDirection.TD
    val nodes = LinkedHashMap<String, FlowchartNode>()
    val edges = mutableListOf<FlowchartEdge>()

    fun ensureState(id: String, isStart: Boolean = false, isEnd: Boolean = false): String {
        val mappedId = when {
            id == "[*]" && isStart -> "__state_start__"
            id == "[*]" && isEnd -> "__state_end__"
            else -> id
        }
        if (!nodes.containsKey(mappedId)) {
            val shape = when (mappedId) {
                "__state_start__" -> FlowchartNodeShape.STATE_START
                "__state_end__" -> FlowchartNodeShape.STATE_END
                else -> FlowchartNodeShape.ROUNDED
            }
            val label = when (mappedId) {
                "__state_start__" -> "Start"
                "__state_end__" -> "End"
                else -> mappedId
            }
            nodes[mappedId] = FlowchartNode(mappedId, label, shape)
        }
        return mappedId
    }

    for (i in 1 until lines.size) {
        val line = lines[i]
        val lower = line.lowercase()
        if (lower.startsWith("direction ")) {
            direction = when {
                lower.endsWith("lr") -> FlowchartDirection.LR
                lower.endsWith("rl") -> FlowchartDirection.RL
                lower.endsWith("bt") -> FlowchartDirection.BT
                else -> FlowchartDirection.TD
            }
            continue
        }
        if (lower.startsWith("note ") || lower == "}" || lower == "end" || lower == "--") {
            continue
        }

        STATE_ALIAS_REGEX.matchEntire(line)?.let { match ->
            val label = cleanLabel(match.groupValues[1])
            val id = match.groupValues[2]
            nodes[id] = FlowchartNode(id, label, FlowchartNodeShape.ROUNDED)
            continue
        }

        STATE_TRANSITION_REGEX.matchEntire(line)?.let { match ->
            val rawFrom = match.groupValues[1]
            val rawTo = match.groupValues[3]
            val label = cleanLabel(match.groupValues[4]).ifBlank { null }
            val fromId = ensureState(rawFrom, isStart = true, isEnd = false)
            val toId = ensureState(rawTo, isStart = false, isEnd = true)
            edges.add(FlowchartEdge(from = fromId, to = toId, label = label))
            continue
        }

        STATE_DESC_REGEX.matchEntire(line)?.let { match ->
            val id = match.groupValues[1]
            val desc = cleanLabel(match.groupValues[2])
            if (id != "[*]") {
                val existing = nodes[id]?.label
                val combined = if (existing != null && existing != id) "$existing\n$desc" else "$id: $desc"
                nodes[id] = FlowchartNode(id, combined, FlowchartNodeShape.ROUNDED)
            }
        }
    }

    if (nodes.isEmpty()) return null
    return StateDiagramGraph(
        nodes = nodes.values.toList(),
        edges = edges,
        direction = direction
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Topological DAG Layering & Native Flowchart/State Canvas Renderer
// ─────────────────────────────────────────────────────────────────────────────

internal fun computeFlowchartLayers(
    nodes: List<FlowchartNode>,
    edges: List<FlowchartEdge>,
    direction: FlowchartDirection
): List<List<FlowchartNode>> {
    if (nodes.isEmpty()) return emptyList()

    val nodeIds = nodes.map { it.id }.toSet()
    val outAdj = LinkedHashMap<String, MutableList<String>>()
    val inDegree = LinkedHashMap<String, Int>()
    for (n in nodes) {
        outAdj[n.id] = mutableListOf()
        inDegree[n.id] = 0
    }
    for (edge in edges) {
        if (edge.from in nodeIds && edge.to in nodeIds && edge.from != edge.to) {
            outAdj[edge.from]?.add(edge.to)
            inDegree[edge.to] = (inDegree[edge.to] ?: 0) + 1
        }
    }

    val rank = LinkedHashMap<String, Int>()
    for (n in nodes) rank[n.id] = 0

    // Assign longest-path ranks safely while breaking cycles with active-path tracking
    val visiting = HashSet<String>()
    val visited = HashSet<String>()

    fun assignRanksDfs(currentId: String, currentRank: Int) {
        if (currentRank > (rank[currentId] ?: 0)) {
            rank[currentId] = currentRank
        }
        if (currentId in visiting) return
        visiting.add(currentId)
        visited.add(currentId)
        for (nextId in outAdj[currentId].orEmpty()) {
            if (nextId !in visiting) {
                val candidateRank = (rank[currentId] ?: 0) + 1
                if (candidateRank > (rank[nextId] ?: 0) || nextId !in visited) {
                    assignRanksDfs(nextId, candidateRank)
                }
            }
        }
        visiting.remove(currentId)
    }

    val roots = nodes.filter { (inDegree[it.id] ?: 0) == 0 }
    for (root in roots) {
        assignRanksDfs(root.id, 0)
    }
    // Cover disconnected cycles with non-zero in-degree
    for (n in nodes) {
        if (n.id !in visited) {
            assignRanksDfs(n.id, 0)
        }
    }

    val maxRank = rank.values.maxOrNull() ?: 0
    val layers = Array(maxRank + 1) { mutableListOf<FlowchartNode>() }
    for (n in nodes) {
        val r = (rank[n.id] ?: 0).coerceIn(0, maxRank)
        layers[r].add(n)
    }

    val nonEmptyLayers = layers.map { it.toList() }.filter { it.isNotEmpty() }
    return if (direction == FlowchartDirection.BT || direction == FlowchartDirection.RL) {
        nonEmptyLayers.reversed()
    } else {
        nonEmptyLayers
    }
}

private data class DrawnEdgeGeometry(
    val start: Offset,
    val ctrl1: Offset,
    val ctrl2: Offset,
    val end: Offset,
    val style: EdgeStyle,
    val labelCenter: Offset
)

private class DiagramGeometryCache {
    var edges: List<DrawnEdgeGeometry> = emptyList()
}

private val DIAMOND_SHAPE: Shape = GenericShape { size, _ ->
    moveTo(size.width / 2f, 0f)
    lineTo(size.width, size.height / 2f)
    lineTo(size.width / 2f, size.height)
    lineTo(0f, size.height / 2f)
    close()
}

private val HEXAGON_SHAPE: Shape = GenericShape { size, _ ->
    val inset = size.width * 0.16f
    moveTo(inset, 0f)
    lineTo(size.width - inset, 0f)
    lineTo(size.width, size.height / 2f)
    lineTo(size.width - inset, size.height)
    lineTo(inset, size.height)
    lineTo(0f, size.height / 2f)
    close()
}

private fun Modifier.zoomableDiagramLayout(scale: Float): Modifier =
    if (scale == 1f) {
        this
    } else {
        this
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val scaledW = (placeable.width * scale).roundToInt().coerceAtLeast(1)
                val scaledH = (placeable.height * scale).roundToInt().coerceAtLeast(1)
                layout(scaledW, scaledH) {
                    placeable.placeRelative(0, 0)
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
    }

@Composable
fun NativeFlowchartView(
    graph: FlowchartGraph,
    modifier: Modifier = Modifier,
    zoomScale: Float = 1f
) {
    val colors = SovereignTheme.colors
    val layers = remember(graph) {
        computeFlowchartLayers(graph.nodes, graph.edges, graph.direction)
    }
    val nodeLayerRank = remember(layers) {
        val map = HashMap<String, Int>(graph.nodes.size)
        layers.forEachIndexed { layerIdx, layerNodes ->
            layerNodes.forEach { map[it.id] = layerIdx }
        }
        map
    }
    val orderedNodes = remember(layers) { layers.flatten() }
    val labeledEdges = remember(graph.edges) {
        graph.edges.filter { !it.label.isNullOrBlank() }
    }
    val geometryCache = remember(graph) { DiagramGeometryCache() }

    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()
    val density = LocalDensity.current

    val primaryArrowColor = colors.textSecondary.copy(alpha = 0.78f)
    val accentArrowColor = colors.accentPrimary
    val thickArrowColor = colors.llamaViolet

    Box(
        modifier = modifier
            .horizontalScroll(hScroll)
            .verticalScroll(vScroll)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Layout(
            modifier = Modifier.zoomableDiagramLayout(zoomScale),
            content = {
                // 0 until orderedNodes.size: node composables
                orderedNodes.forEach { node ->
                    FlowchartNodeView(node = node)
                }

                // orderedNodes.size until orderedNodes.size + labeledEdges.size: edge labels
                labeledEdges.forEach { edge ->
                    SovereignSurface(
                        shape = SovereignTheme.shapes.xs,
                        color = colors.surfaceBase,
                        border = BorderStroke(SovereignElevation.hairlineWidth, colors.hairlineSubtle)
                    ) {
                        Text(
                            text = edge.label.orEmpty(),
                            style = SovereignTheme.typography.monoTag,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Last child: Canvas drawing all directed edges and arrowheads
                Canvas(modifier = Modifier) {
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f), 0f)
                    val baseStrokePx = 1.6.dp.toPx()
                    val thickStrokePx = 2.6.dp.toPx()
                    val arrowLenPx = 8.dp.toPx()

                    geometryCache.edges.forEach { edgeGeo ->
                        val strokeColor = when (edgeGeo.style) {
                            EdgeStyle.DASHED_ARROW -> accentArrowColor
                            EdgeStyle.THICK_ARROW -> thickArrowColor
                            else -> primaryArrowColor
                        }
                        val strokeWidth = if (edgeGeo.style == EdgeStyle.THICK_ARROW) thickStrokePx else baseStrokePx
                        val pathEffect = if (edgeGeo.style == EdgeStyle.DASHED_ARROW) dashEffect else null

                        val path = Path().apply {
                            moveTo(edgeGeo.start.x, edgeGeo.start.y)
                            cubicTo(
                                edgeGeo.ctrl1.x, edgeGeo.ctrl1.y,
                                edgeGeo.ctrl2.x, edgeGeo.ctrl2.y,
                                edgeGeo.end.x, edgeGeo.end.y
                            )
                        }
                        drawPath(
                            path = path,
                            color = strokeColor,
                            style = Stroke(
                                width = strokeWidth,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                                pathEffect = pathEffect
                            )
                        )

                        if (edgeGeo.style != EdgeStyle.SOLID_LINE) {
                            val angle = atan2(
                                edgeGeo.end.y - edgeGeo.ctrl2.y,
                                edgeGeo.end.x - edgeGeo.ctrl2.x
                            )
                            val spread = (26.0 * PI / 180.0).toFloat()
                            val p1 = Offset(
                                edgeGeo.end.x - arrowLenPx * cos(angle - spread),
                                edgeGeo.end.y - arrowLenPx * sin(angle - spread)
                            )
                            val p2 = Offset(
                                edgeGeo.end.x - arrowLenPx * cos(angle + spread),
                                edgeGeo.end.y - arrowLenPx * sin(angle + spread)
                            )
                            val arrowPath = Path().apply {
                                moveTo(edgeGeo.end.x, edgeGeo.end.y)
                                lineTo(p1.x, p1.y)
                                lineTo(p2.x, p2.y)
                                close()
                            }
                            drawPath(arrowPath, color = strokeColor)
                        }
                    }
                }
            }
        ) { measurables, _ ->
            val nodeCount = orderedNodes.size
            val labelCount = labeledEdges.size
            val canvasIdx = nodeCount + labelCount

            val nodeMaxW = with(density) { 180.dp.roundToPx() }
            val labelMaxW = with(density) { 140.dp.roundToPx() }
            val layerGapPx = with(density) { if (graph.isHorizontal) 56.dp.roundToPx() else 44.dp.roundToPx() }
            val siblingGapPx = with(density) { 18.dp.roundToPx() }
            val backEdgePadPx = with(density) { 36.dp.roundToPx() }

            val nodePlaceables = ArrayList<Placeable>(nodeCount)
            val nodeIndexById = HashMap<String, Int>(nodeCount)
            for (i in 0 until nodeCount) {
                val node = orderedNodes[i]
                nodeIndexById[node.id] = i
                nodePlaceables.add(
                    measurables[i].measure(
                        Constraints(minWidth = 0, maxWidth = nodeMaxW, minHeight = 0, maxHeight = Constraints.Infinity)
                    )
                )
            }

            val labelPlaceables = ArrayList<Placeable>(labelCount)
            for (j in 0 until labelCount) {
                labelPlaceables.add(
                    measurables[nodeCount + j].measure(
                        Constraints(minWidth = 0, maxWidth = labelMaxW, minHeight = 0, maxHeight = Constraints.Infinity)
                    )
                )
            }

            // Compute layer bounding dimensions
            val layerMainSizes = IntArray(layers.size)
            val layerCrossSizes = IntArray(layers.size)
            layers.forEachIndexed { layerIdx, layerNodes ->
                var mainMax = 0
                var crossSum = 0
                layerNodes.forEachIndexed { idxInLayer, node ->
                    val p = nodePlaceables[nodeIndexById[node.id]!!]
                    if (graph.isHorizontal) {
                        mainMax = max(mainMax, p.width)
                        crossSum += p.height + if (idxInLayer < layerNodes.lastIndex) siblingGapPx else 0
                    } else {
                        mainMax = max(mainMax, p.height)
                        crossSum += p.width + if (idxInLayer < layerNodes.lastIndex) siblingGapPx else 0
                    }
                }
                layerMainSizes[layerIdx] = mainMax
                layerCrossSizes[layerIdx] = crossSum
            }

            val maxCross = layerCrossSizes.maxOrNull()?.coerceAtLeast(1) ?: 1
            val hasBackEdges = graph.edges.any { edge ->
                val fr = nodeLayerRank[edge.from] ?: 0
                val tr = nodeLayerRank[edge.to] ?: 0
                fr >= tr
            }
            val extraSidePad = if (hasBackEdges) backEdgePadPx else 0

            val nodeRects = HashMap<String, Rect>(nodeCount)
            val nodePositions = Array<Offset>(nodeCount) { Offset.Zero }

            var mainCursor = 0
            layers.forEachIndexed { layerIdx, layerNodes ->
                val layerCross = layerCrossSizes[layerIdx]
                var crossCursor = (maxCross - layerCross) / 2
                val layerMain = layerMainSizes[layerIdx]

                layerNodes.forEach { node ->
                    val nIdx = nodeIndexById[node.id]!!
                    val p = nodePlaceables[nIdx]
                    val x: Int
                    val y: Int
                    if (graph.isHorizontal) {
                        x = mainCursor + (layerMain - p.width) / 2
                        y = crossCursor
                        crossCursor += p.height + siblingGapPx
                    } else {
                        x = crossCursor
                        y = mainCursor + (layerMain - p.height) / 2
                        crossCursor += p.width + siblingGapPx
                    }
                    nodePositions[nIdx] = Offset(x.toFloat(), y.toFloat())
                    nodeRects[node.id] = Rect(
                        left = x.toFloat(),
                        top = y.toFloat(),
                        right = (x + p.width).toFloat(),
                        bottom = (y + p.height).toFloat()
                    )
                }
                mainCursor += layerMain + if (layerIdx < layers.lastIndex) layerGapPx else 0
            }

            val totalWidth = (if (graph.isHorizontal) mainCursor else maxCross + extraSidePad).coerceAtLeast(1)
            val totalHeight = (if (graph.isHorizontal) maxCross + extraSidePad else mainCursor).coerceAtLeast(1)

            // Compute edge geometries
            val drawnEdges = ArrayList<DrawnEdgeGeometry>(graph.edges.size)
            val labelPositions = ArrayList<Offset>(labelCount)

            graph.edges.forEach { edge ->
                val fromRect = nodeRects[edge.from]
                val toRect = nodeRects[edge.to]
                if (fromRect != null && toRect != null) {
                    val fromRank = nodeLayerRank[edge.from] ?: 0
                    val toRank = nodeLayerRank[edge.to] ?: 0
                    val isForward = fromRank < toRank

                    val geo = if (isForward) {
                        if (graph.isHorizontal) {
                            val start = Offset(fromRect.right, fromRect.center.y)
                            val end = Offset(toRect.left, toRect.center.y)
                            val midX = (start.x + end.x) / 2f
                            DrawnEdgeGeometry(
                                start = start,
                                ctrl1 = Offset(midX, start.y),
                                ctrl2 = Offset(midX, end.y),
                                end = end,
                                style = edge.style,
                                labelCenter = Offset(midX, (start.y + end.y) / 2f)
                            )
                        } else {
                            val start = Offset(fromRect.center.x, fromRect.bottom)
                            val end = Offset(toRect.center.x, toRect.top)
                            val midY = (start.y + end.y) / 2f
                            DrawnEdgeGeometry(
                                start = start,
                                ctrl1 = Offset(start.x, midY),
                                ctrl2 = Offset(end.x, midY),
                                end = end,
                                style = edge.style,
                                labelCenter = Offset((start.x + end.x) / 2f, midY)
                            )
                        }
                    } else {
                        // Back-edge or self-loop: route gracefully along outer side
                        if (graph.isHorizontal) {
                            val laneY = max(fromRect.bottom, toRect.bottom) + backEdgePadPx * 0.7f
                            val start = Offset(fromRect.center.x, fromRect.bottom)
                            val end = Offset(toRect.center.x, toRect.bottom)
                            DrawnEdgeGeometry(
                                start = start,
                                ctrl1 = Offset(start.x, laneY),
                                ctrl2 = Offset(end.x, laneY),
                                end = end,
                                style = edge.style,
                                labelCenter = Offset((start.x + end.x) / 2f, laneY - 8f)
                            )
                        } else {
                            val laneX = max(fromRect.right, toRect.right) + backEdgePadPx * 0.7f
                            val start = Offset(fromRect.right, fromRect.center.y)
                            val end = Offset(toRect.right, toRect.center.y)
                            DrawnEdgeGeometry(
                                start = start,
                                ctrl1 = Offset(laneX, start.y),
                                ctrl2 = Offset(laneX, end.y),
                                end = end,
                                style = edge.style,
                                labelCenter = Offset(laneX - 8f, (start.y + end.y) / 2f)
                            )
                        }
                    }
                    drawnEdges.add(geo)
                    if (!edge.label.isNullOrBlank()) {
                        labelPositions.add(geo.labelCenter)
                    }
                }
            }
            geometryCache.edges = drawnEdges

            val canvasPlaceable = measurables[canvasIdx].measure(
                Constraints.fixed(width = totalWidth, height = totalHeight)
            )

            layout(totalWidth, totalHeight) {
                // Draw edges behind nodes
                canvasPlaceable.placeRelative(0, 0)

                // Draw nodes
                for (i in 0 until nodeCount) {
                    val pos = nodePositions[i]
                    nodePlaceables[i].placeRelative(pos.x.toInt(), pos.y.toInt())
                }

                // Draw edge label pills on top of lines
                for (j in 0 until minOf(labelCount, labelPositions.size)) {
                    val lp = labelPlaceables[j]
                    val center = labelPositions[j]
                    val lx = (center.x - lp.width / 2f).toInt().coerceIn(0, (totalWidth - lp.width).coerceAtLeast(0))
                    val ly = (center.y - lp.height / 2f).toInt().coerceIn(0, (totalHeight - lp.height).coerceAtLeast(0))
                    lp.placeRelative(lx, ly)
                }
            }
        }
    }
}

@Composable
private fun FlowchartNodeView(node: FlowchartNode) {
    val colors = SovereignTheme.colors
    val shapes = SovereignTheme.shapes

    when (node.shape) {
        FlowchartNodeShape.STATE_START -> {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(colors.ctaInk)
                    .semantics { contentDescription = "Start state" }
            )
        }
        FlowchartNodeShape.STATE_END -> {
            Surface(
                shape = CircleShape,
                color = Color.Transparent,
                border = BorderStroke(2.dp, colors.accentPrimary),
                modifier = Modifier
                    .size(24.dp)
                    .semantics { contentDescription = "End state" }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(colors.accentPrimary)
                    )
                }
            }
        }
        FlowchartNodeShape.DIAMOND -> {
            Surface(
                shape = DIAMOND_SHAPE,
                color = colors.amberContainer,
                border = BorderStroke(1.2.dp, colors.amber),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Text(
                    text = node.label,
                    style = SovereignTheme.typography.bodyCompact.copy(fontWeight = FontWeight.Medium),
                    color = colors.onAmberContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp)
                )
            }
        }
        FlowchartNodeShape.CIRCLE -> {
            Surface(
                shape = CircleShape,
                color = colors.accentContainer,
                border = BorderStroke(1.2.dp, colors.accentPrimary),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Text(
                    text = node.label,
                    style = SovereignTheme.typography.bodyCompact.copy(fontWeight = FontWeight.Medium),
                    color = colors.onAccentContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
                )
            }
        }
        FlowchartNodeShape.HEXAGON -> {
            Surface(
                shape = HEXAGON_SHAPE,
                color = colors.llamaVioletContainer,
                border = BorderStroke(1.2.dp, colors.llamaViolet),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Text(
                    text = node.label,
                    style = SovereignTheme.typography.bodyCompact.copy(fontWeight = FontWeight.Medium),
                    color = colors.onLlamaVioletContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp)
                )
            }
        }
        FlowchartNodeShape.DATABASE -> {
            Surface(
                shape = shapes.md,
                color = colors.llamaVioletContainer,
                border = BorderStroke(1.2.dp, colors.llamaViolet),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Text(
                    text = node.label,
                    style = SovereignTheme.typography.bodyCompact.copy(fontWeight = FontWeight.Medium),
                    color = colors.onLlamaVioletContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
        FlowchartNodeShape.ROUNDED,
        FlowchartNodeShape.STADIUM -> {
            Surface(
                shape = shapes.pill,
                color = colors.surfaceHigh,
                border = BorderStroke(1.2.dp, colors.accentPrimary),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Text(
                    text = node.label,
                    style = SovereignTheme.typography.bodyCompact.copy(fontWeight = FontWeight.Medium),
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                )
            }
        }
        FlowchartNodeShape.RECTANGLE -> {
            Surface(
                shape = shapes.sm,
                color = colors.surfaceHigh,
                border = BorderStroke(1.2.dp, colors.hairlineStrong),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Text(
                    text = node.label,
                    style = SovereignTheme.typography.bodyCompact.copy(fontWeight = FontWeight.Medium),
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Native Sequence Diagram Canvas + Compose Renderer
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NativeSequenceDiagramView(
    diagram: SequenceDiagramGraph,
    modifier: Modifier = Modifier,
    zoomScale: Float = 1f
) {
    val colors = SovereignTheme.colors
    val shapes = SovereignTheme.shapes
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    val participants = diagram.participants
    val participantIdx = remember(participants) {
        participants.mapIndexed { idx, p -> p.id to idx }.toMap()
    }
    val colWidth = 116.dp

    Box(
        modifier = modifier
            .horizontalScroll(hScroll)
            .verticalScroll(vScroll)
            .padding(16.dp)
    ) {
        val totalWidth = colWidth * participants.size.coerceAtLeast(1)

        Column(
            modifier = Modifier
                .width(totalWidth)
                .zoomableDiagramLayout(zoomScale)
        ) {
            // Top Actor / Participant Boxes
            Row(modifier = Modifier.fillMaxWidth()) {
                participants.forEach { p ->
                    Box(
                        modifier = Modifier
                            .width(colWidth)
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = if (p.isActor) shapes.pill else shapes.sm,
                            color = colors.surfaceHigh,
                            border = BorderStroke(1.2.dp, colors.accentPrimary),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp
                        ) {
                            Text(
                                text = p.displayName,
                                style = SovereignTheme.typography.actionLabel.copy(fontWeight = FontWeight.SemiBold),
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

            // Lifelines + Messages / Notes / Block markers
            Box(modifier = Modifier.fillMaxWidth()) {
                val lifelineColor = colors.hairlineStrong
                val requestArrowColor = colors.accentPrimary
                val responseArrowColor = colors.llamaViolet

                // Vertical dashed lifelines drawn behind sequence rows
                Canvas(modifier = Modifier.matchParentSize()) {
                    val colPx = size.width / participants.size.coerceAtLeast(1)
                    val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    val strokePx = 1.2.dp.toPx()
                    for (idx in participants.indices) {
                        val cx = colPx * idx + colPx / 2f
                        drawLine(
                            color = lifelineColor,
                            start = Offset(cx, 0f),
                            end = Offset(cx, size.height),
                            strokeWidth = strokePx,
                            pathEffect = dash
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    diagram.items.forEach { item ->
                        when (item) {
                            is SequenceItem.Message -> {
                                val fromIdx = participantIdx[item.fromId] ?: 0
                                val toIdx = participantIdx[item.toId] ?: 0
                                val arrowColor = if (item.isDashed) responseArrowColor else requestArrowColor

                                if (item.isSelf) {
                                    // Self-call arrow on a single lifeline
                                    val leftSpacer = colWidth * fromIdx + colWidth / 2f
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Spacer(Modifier.width(leftSpacer))
                                        Canvas(modifier = Modifier.size(width = 36.dp, height = 28.dp)) {
                                            val strokePx = 1.6.dp.toPx()
                                            val dash = if (item.isDashed) PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f) else null
                                            val path = Path().apply {
                                                moveTo(0f, 4f)
                                                lineTo(size.width - 4f, 4f)
                                                lineTo(size.width - 4f, size.height - 4f)
                                                lineTo(4f, size.height - 4f)
                                            }
                                            drawPath(path, arrowColor, style = Stroke(strokePx, pathEffect = dash))
                                            val arrowHead = Path().apply {
                                                moveTo(0f, size.height - 4f)
                                                lineTo(8.dp.toPx(), size.height - 9.dp.toPx())
                                                lineTo(8.dp.toPx(), size.height + 1.dp.toPx())
                                                close()
                                            }
                                            drawPath(arrowHead, arrowColor)
                                        }
                                        if (item.label.isNotBlank()) {
                                            Spacer(Modifier.width(6.dp))
                                            Surface(
                                                shape = shapes.xs,
                                                color = colors.surfaceHigh,
                                                border = BorderStroke(SovereignElevation.hairlineWidth, colors.hairlineSubtle)
                                            ) {
                                                Text(
                                                    text = item.label,
                                                    style = SovereignTheme.typography.monoTag,
                                                    color = colors.textPrimary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    val leftCol = minOf(fromIdx, toIdx)
                                    val rightCol = maxOf(fromIdx, toIdx)
                                    val spanCols = (rightCol - leftCol).coerceAtLeast(1)
                                    val startOffset = colWidth * leftCol + colWidth / 2f
                                    val spanWidth = colWidth * spanCols
                                    val goingRight = toIdx > fromIdx

                                    Column(
                                        modifier = Modifier
                                            .padding(start = startOffset)
                                            .width(spanWidth),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        if (item.label.isNotBlank()) {
                                            Surface(
                                                shape = shapes.xs,
                                                color = colors.surfaceHigh,
                                                border = BorderStroke(SovereignElevation.hairlineWidth, colors.hairlineSubtle)
                                            ) {
                                                Text(
                                                    text = item.label,
                                                    style = SovereignTheme.typography.monoTag,
                                                    color = colors.textPrimary,
                                                    textAlign = TextAlign.Center,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Spacer(Modifier.height(3.dp))
                                        }

                                        Canvas(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(14.dp)
                                        ) {
                                            val midY = size.height / 2f
                                            val strokePx = 1.6.dp.toPx()
                                            val arrowLen = 7.dp.toPx()
                                            val arrowHalfH = 4.5.dp.toPx()
                                            val dash = if (item.isDashed) PathEffect.dashPathEffect(floatArrayOf(9f, 6f), 0f) else null

                                            if (goingRight) {
                                                drawLine(
                                                    color = arrowColor,
                                                    start = Offset(0f, midY),
                                                    end = Offset(size.width - arrowLen, midY),
                                                    strokeWidth = strokePx,
                                                    pathEffect = dash
                                                )
                                                val head = Path().apply {
                                                    moveTo(size.width, midY)
                                                    lineTo(size.width - arrowLen, midY - arrowHalfH)
                                                    lineTo(size.width - arrowLen, midY + arrowHalfH)
                                                    close()
                                                }
                                                drawPath(head, arrowColor)
                                            } else {
                                                drawLine(
                                                    color = arrowColor,
                                                    start = Offset(size.width, midY),
                                                    end = Offset(arrowLen, midY),
                                                    strokeWidth = strokePx,
                                                    pathEffect = dash
                                                )
                                                val head = Path().apply {
                                                    moveTo(0f, midY)
                                                    lineTo(arrowLen, midY - arrowHalfH)
                                                    lineTo(arrowLen, midY + arrowHalfH)
                                                    close()
                                                }
                                                drawPath(head, arrowColor)
                                            }
                                        }
                                    }
                                }
                            }

                            is SequenceItem.Note -> {
                                val firstIdx = participantIdx[item.participantIds.firstOrNull()] ?: 0
                                val lastIdx = participantIdx[item.participantIds.lastOrNull()] ?: firstIdx
                                val leftCol = minOf(firstIdx, lastIdx)
                                val span = (maxOf(firstIdx, lastIdx) - leftCol + 1).coerceAtLeast(1)

                                Box(
                                    modifier = Modifier
                                        .padding(start = colWidth * leftCol + 8.dp)
                                        .widthIn(min = colWidth - 16.dp, max = colWidth * span - 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        shape = shapes.xs,
                                        color = colors.amberContainer,
                                        border = BorderStroke(SovereignElevation.hairlineWidth, colors.amber.copy(alpha = 0.45f))
                                    ) {
                                        Text(
                                            text = item.text,
                                            style = SovereignTheme.typography.monoTag,
                                            color = colors.onAmberContainer,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            is SequenceItem.BlockMarker -> {
                                Surface(
                                    shape = shapes.xs,
                                    color = colors.surfaceBase,
                                    border = BorderStroke(SovereignElevation.hairlineWidth, colors.hairlineStrong),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        SovereignBadge(
                                            text = item.kind,
                                            tone = SovereignStatusTone.VIOLET
                                        )
                                        if (item.label.isNotBlank()) {
                                            Text(
                                                text = item.label,
                                                style = SovereignTheme.typography.monoTag,
                                                color = colors.textSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
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
}

// ─────────────────────────────────────────────────────────────────────────────
// Graceful Structured Offline Fallback
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StructuredDiagramFallbackView(
    fallback: ParsedMermaidDiagram.StructuredFallback,
    onSwitchToSource: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val colors = SovereignTheme.colors
    val shapes = SovereignTheme.shapes
    val vScroll = rememberScrollState()

    Column(
        modifier = modifier
            .verticalScroll(vScroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SovereignBadge(
                text = fallback.kindLabel,
                tone = SovereignStatusTone.NEUTRAL
            )
            Text(
                text = "Offline structured outline",
                style = SovereignTheme.typography.monoTag,
                color = colors.textMuted
            )
        }

        if (fallback.summaryLines.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                fallback.summaryLines.forEach { line ->
                    Surface(
                        shape = shapes.xs,
                        color = colors.surfaceHigh,
                        border = BorderStroke(SovereignElevation.hairlineWidth, colors.hairlineSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = line,
                            style = SovereignTheme.typography.codeBody,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        } else {
            Text(
                text = fallback.rawPreview.ifBlank { "Waiting for diagram elements…" },
                style = SovereignTheme.typography.codeBody,
                color = colors.textSecondary
            )
        }

        if (onSwitchToSource != null) {
            SovereignSecondaryButton(
                text = "View Source",
                onClick = onSwitchToSource,
                hapticFeedback = true
            )
        }
    }
}
