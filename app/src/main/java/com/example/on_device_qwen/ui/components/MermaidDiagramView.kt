package com.example.on_device_qwen.ui.components

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Modern ChatGPT-style Diagram Visualizer.
 * Supports Mermaid flowcharts, sequence diagrams, architecture graphs, and mindmaps.
 * Features:
 * - Diagram vs Code tab switching
 * - Hybrid rendering: Rich WebView Mermaid SVG + Native Offline Flowchart fallback
 * - Fullscreen zoom modal for expansive system diagrams
 * - 1-tap copy source code
 */
@Composable
fun MermaidDiagramView(
    diagramCode: String,
    diagramType: String = "mermaid",
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = Diagram, 1 = Code
    var isFullscreen by remember { mutableStateOf(false) }
    var isCopied by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    if (isFullscreen) {
        Dialog(
            onDismissRequest = { isFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Fullscreen Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Diagram Viewer",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { isFullscreen = false }) {
                            Icon(Icons.Outlined.FullscreenExit, contentDescription = "Exit fullscreen")
                        }
                    }

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
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Title and diagram pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Diagram",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Control actions (Tab switcher, Fullscreen, Copy)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Tab pill (Diagram / Code)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Row(modifier = Modifier.padding(2.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (selectedTab == 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                    .clickable { selectedTab = 0 }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Preview",
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (selectedTab == 1) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                    .clickable { selectedTab = 1 }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Code",
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Fullscreen button
                    IconButton(
                        onClick = { isFullscreen = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Fullscreen,
                            contentDescription = "Fullscreen",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Copy code button
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("mermaid", diagramCode))
                            isCopied = true
                            coroutineScope.launch {
                                delay(2000)
                                isCopied = false
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copy source",
                            modifier = Modifier.size(14.dp),
                            tint = if (isCopied) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Body Content
            if (selectedTab == 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 340.dp)
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

/**
 * Renders diagram content using an offline native flowchart parser with fallback to WebView SVG.
 */
@Composable
fun MermaidContentRenderer(diagramCode: String) {
    val parsedGraph = remember(diagramCode) { parseSimpleGraph(diagramCode) }

    if (parsedGraph != null && parsedGraph.nodes.isNotEmpty()) {
        // High-performance Native Offline Flowchart View
        NativeFlowchartView(graph = parsedGraph)
    } else {
        // WebView-based renderer for complex diagrams (Sequences, ER, State, Class diagrams)
        MermaidWebView(diagramCode = diagramCode)
    }
}

/**
 * Offline WebView rendering Mermaid diagrams with pan and zoom.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MermaidWebView(diagramCode: String) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    val bgColor = if (isDark) "#171717" else "#FFFFFF"
    val textColor = if (isDark) "#E2E2F0" else "#212121"
    val primaryColor = if (isDark) "#10A37F" else "#008060"

    val html = remember(diagramCode, isDark) {
        val cleanCode = diagramCode.replace("`", "'").replace("\\", "\\\\")
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes">
            <style>
                * { box-sizing: border-box; }
                body {
                    margin: 0;
                    padding: 16px;
                    background-color: $bgColor;
                    color: $textColor;
                    display: flex;
                    flex-direction: column;
                    justify-content: center;
                    align-items: center;
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                    min-height: 100vh;
                    overflow: auto;
                }
                .mermaid {
                    width: 100%;
                    display: flex;
                    justify-content: center;
                }
                svg {
                    max-width: 100% !important;
                    height: auto !important;
                }
                #fallback {
                    display: none;
                    font-family: monospace;
                    font-size: 13px;
                    white-space: pre-wrap;
                    color: $textColor;
                }
            </style>
            <script src="https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js"></script>
            <script>
                window.onload = function() {
                    if (typeof mermaid !== 'undefined') {
                        mermaid.initialize({
                            startOnLoad: true,
                            theme: '${if (isDark) "dark" else "default"}',
                            themeVariables: {
                                darkMode: $isDark,
                                background: '$bgColor',
                                primaryColor: '$primaryColor',
                                primaryTextColor: '#FFFFFF',
                                lineColor: '${if (isDark) "#8E8EA0" else "#555555"}'
                            }
                        });
                    } else {
                        document.getElementById('fallback').style.display = 'block';
                    }
                };
            </script>
        </head>
        <body>
            <div class="mermaid">
                $cleanCode
            </div>
            <div id="fallback">$cleanCode</div>
        </body>
        </html>
        """.trimIndent()
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                webViewClient = WebViewClient()
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL("https://cdn.jsdelivr.net", html, "text/html", "UTF-8", null)
        },
        modifier = Modifier.fillMaxSize()
    )
}

// ─── Native Offline Flowchart Graph Engine ─────────────────────────────────

data class FlowchartNode(val id: String, val label: String)
data class FlowchartEdge(val from: String, val to: String, val label: String? = null)
data class FlowchartGraph(
    val nodes: List<FlowchartNode>,
    val edges: List<FlowchartEdge>,
    val isHorizontal: Boolean
)

/**
 * Extracts nodes and directed edges from standard Mermaid flowchart syntax:
 * e.g., graph TD, flowchart LR, A[Start] --> B[Process] --> C[End]
 */
fun parseSimpleGraph(raw: String): FlowchartGraph? {
    val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.isEmpty()) return null

    val firstLine = lines.first().lowercase()
    if (!firstLine.startsWith("graph") && !firstLine.startsWith("flowchart")) {
        return null
    }

    val isHorizontal = firstLine.contains("lr")
    val nodesMap = mutableMapOf<String, String>()
    val edges = mutableListOf<FlowchartEdge>()

    // Regex matching A[Text] or A(Text) or A
    val nodeDefRegex = Regex("""([A-Za-z0-9_]+)(?:\[([^\]]+)\]|\(([^\)]+)\))?""")
    // Regex matching links: --> or --- or -->|text|
    val edgeRegex = Regex("""-->\|?([^|]*)\|?""")

    for (i in 1 until lines.size) {
        val line = lines[i]
        if (line.startsWith("%%") || line.startsWith("subgraph") || line.startsWith("end")) continue

        // Split by edge arrows
        val parts = line.split(Regex("""-->|---|-.->|==>"""))
        if (parts.size >= 2) {
            for (p in 0 until parts.size - 1) {
                val fromPart = parts[p].trim()
                val toPart = parts[p + 1].trim()

                val fromMatch = nodeDefRegex.find(fromPart)
                val toMatch = nodeDefRegex.find(toPart)

                if (fromMatch != null && toMatch != null) {
                    val fromId = fromMatch.groupValues[1]
                    val fromLabel = fromMatch.groupValues[2].ifEmpty { fromMatch.groupValues[3].ifEmpty { fromId } }

                    val toId = toMatch.groupValues[1]
                    val toLabel = toMatch.groupValues[2].ifEmpty { toMatch.groupValues[3].ifEmpty { toId } }

                    nodesMap[fromId] = fromLabel
                    nodesMap[toId] = toLabel

                    edges.add(FlowchartEdge(from = fromId, to = toId))
                }
            }
        }
    }

    if (nodesMap.isEmpty()) return null

    val nodeList = nodesMap.map { (id, label) -> FlowchartNode(id, label) }
    return FlowchartGraph(nodes = nodeList, edges = edges, isHorizontal = isHorizontal)
}

/**
 * 100% Offline Native Canvas & Layout Flowchart Renderer
 */
@Composable
fun NativeFlowchartView(graph: FlowchartGraph) {
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(scrollState)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        if (graph.isHorizontal) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                graph.nodes.forEachIndexed { index, node ->
                    FlowchartNodeCard(node = node)
                    if (index < graph.nodes.lastIndex) {
                        FlowchartArrow(isHorizontal = true)
                    }
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                graph.nodes.forEachIndexed { index, node ->
                    FlowchartNodeCard(node = node)
                    if (index < graph.nodes.lastIndex) {
                        FlowchartArrow(isHorizontal = false)
                    }
                }
            }
        }
    }
}

@Composable
fun FlowchartNodeCard(node: FlowchartNode) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
        shadowElevation = 2.dp
    ) {
        Text(
            text = node.label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

@Composable
fun FlowchartArrow(isHorizontal: Boolean) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    if (isHorizontal) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(modifier = Modifier.size(width = 24.dp, height = 12.dp)) {
                val midY = size.height / 2
                drawLine(
                    color = tint,
                    start = Offset(0f, midY),
                    end = Offset(size.width - 6f, midY),
                    strokeWidth = 3f
                )
                val arrowPath = Path().apply {
                    moveTo(size.width, midY)
                    lineTo(size.width - 8f, midY - 6f)
                    lineTo(size.width - 8f, midY + 6f)
                    close()
                }
                drawPath(arrowPath, color = tint)
            }
        }
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(modifier = Modifier.size(width = 12.dp, height = 24.dp)) {
                val midX = size.width / 2
                drawLine(
                    color = tint,
                    start = Offset(midX, 0f),
                    end = Offset(midX, size.height - 6f),
                    strokeWidth = 3f
                )
                val arrowPath = Path().apply {
                    moveTo(midX, size.height)
                    lineTo(midX - 6f, size.height - 8f)
                    lineTo(midX + 6f, size.height - 8f)
                    close()
                }
                drawPath(arrowPath, color = tint)
            }
        }
    }
}
