package com.example.on_device_qwen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.example.on_device_qwen.ui.designsystem.SovereignHairlineCard
import com.example.on_device_qwen.ui.designsystem.SovereignTheme

enum class TableAlignment {
    LEFT, CENTER, RIGHT
}

data class MarkdownTableData(
    val headers: List<String>,
    val alignments: List<TableAlignment>,
    val rows: List<List<String>>
)

/**
 * Sovereign Markdown Table component.
 * Features:
 * - 14.dp hairline frame matching Obsidian & Vellum surfaces
 * - Contrasting surfaceHigh header row with bold Inter typography
 * - Alternating zebra striping across full table width
 * - Pixel-aligned column widths with fluid horizontal scrolling
 * - Proper column text alignment (left, center, right)
 */
@Composable
fun MarkdownTableView(
    table: MarkdownTableData,
    modifier: Modifier = Modifier
) {
    if (table.headers.isEmpty()) return

    val colors = SovereignTheme.colors
    val shapes = SovereignTheme.shapes
    val columnCount = table.headers.size
    val rowCount = table.rows.size
    val scrollState = rememberScrollState()

    val headerDividerColor = colors.hairlineStrong
    val rowDividerColor = colors.hairlineSubtle
    val headerBgColor = colors.surfaceHigh
    val zebraBgColor = colors.surfaceBase.copy(alpha = 0.55f)

    SovereignHairlineCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = shapes.md,
        containerColor = colors.surfaceLow,
        borderColor = colors.hairlineSubtle,
        contentPadding = PaddingValues(0.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val minTableWidthPx = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
            ) {
                AlignedMarkdownTableGrid(
                    columnCount = columnCount,
                    rowCount = rowCount,
                    minTableWidthPx = minTableWidthPx
                ) {
                    // 1. Header cells (indices 0 until columnCount)
                    for (colIndex in 0 until columnCount) {
                        val headerText = table.headers.getOrElse(colIndex) { "" }
                        val alignment = table.alignments.getOrElse(colIndex) { TableAlignment.LEFT }
                        Box(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = headerText,
                                style = SovereignTheme.typography.actionLabel.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = colors.textPrimary,
                                textAlign = alignment.toTextAlign(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // 2. Data cells (indices columnCount until (1 + rowCount) * columnCount)
                    table.rows.forEach { rowCells ->
                        for (colIndex in 0 until columnCount) {
                            val cellText = rowCells.getOrElse(colIndex) { "" }
                            val alignment = table.alignments.getOrElse(colIndex) { TableAlignment.LEFT }
                            Box(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                            ) {
                                FormattedInlineText(
                                    text = cellText,
                                    color = colors.textPrimary,
                                    style = SovereignTheme.typography.bodyCompact,
                                    textAlign = alignment.toTextAlign(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // 3. Full-width row surface backgrounds + bottom hairlines (indices after cells)
                    // Header row background
                    Box(
                        modifier = Modifier
                            .background(headerBgColor)
                            .drawBehind {
                                if (rowCount > 0) {
                                    val strokePx = 1.dp.toPx()
                                    val y = size.height - strokePx / 2f
                                    drawLine(
                                        color = headerDividerColor,
                                        start = Offset(0f, y),
                                        end = Offset(size.width, y),
                                        strokeWidth = strokePx
                                    )
                                }
                            }
                    )

                    // Data row backgrounds
                    for (rowIndex in 0 until rowCount) {
                        val rowBackground = if (rowIndex % 2 == 1) zebraBgColor else Color.Transparent
                        val hasBottomDivider = rowIndex < rowCount - 1
                        Box(
                            modifier = Modifier
                                .background(rowBackground)
                                .drawBehind {
                                    if (hasBottomDivider) {
                                        val strokePx = 0.5.dp.toPx()
                                        val y = size.height - strokePx / 2f
                                        drawLine(
                                            color = rowDividerColor,
                                            start = Offset(0f, y),
                                            end = Offset(size.width, y),
                                            strokeWidth = strokePx
                                        )
                                    }
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlignedMarkdownTableGrid(
    columnCount: Int,
    rowCount: Int,
    minTableWidthPx: Int,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val minColPx = with(density) { 88.dp.roundToPx() }
    val maxColPx = with(density) { 240.dp.roundToPx() }

    Layout(content = content) { measurables, _ ->
        val totalRows = 1 + rowCount
        val cellCount = totalRows * columnCount

        // Compute uniform column widths from intrinsic cell widths
        val colWidths = IntArray(columnCount) { minColPx }
        for (col in 0 until columnCount) {
            var maxIntrinsic = minColPx
            for (row in 0 until totalRows) {
                val idx = row * columnCount + col
                if (idx < cellCount && idx < measurables.size) {
                    val intrinsic = measurables[idx].maxIntrinsicWidth(Constraints.Infinity)
                    if (intrinsic > maxIntrinsic) {
                        maxIntrinsic = intrinsic
                    }
                }
            }
            colWidths[col] = maxIntrinsic.coerceIn(minColPx, maxColPx)
        }

        // Expand columns evenly if table is narrower than the card viewport
        val naturalWidth = colWidths.sum()
        if (minTableWidthPx > naturalWidth && columnCount > 0) {
            val extra = minTableWidthPx - naturalWidth
            val perCol = extra / columnCount
            var rem = extra % columnCount
            for (col in 0 until columnCount) {
                colWidths[col] += perCol + if (rem > 0) {
                    rem--
                    1
                } else {
                    0
                }
            }
        }

        val totalWidth = colWidths.sum().coerceAtLeast(1)

        // Measure all cells once with exact column width
        val cellPlaceables = ArrayList<Placeable>(cellCount)
        val rowHeights = IntArray(totalRows)
        for (row in 0 until totalRows) {
            var maxRowH = 0
            for (col in 0 until columnCount) {
                val idx = row * columnCount + col
                val placeable = measurables[idx].measure(
                    Constraints(
                        minWidth = colWidths[col],
                        maxWidth = colWidths[col],
                        minHeight = 0,
                        maxHeight = Constraints.Infinity
                    )
                )
                cellPlaceables.add(placeable)
                if (placeable.height > maxRowH) {
                    maxRowH = placeable.height
                }
            }
            rowHeights[row] = maxRowH.coerceAtLeast(1)
        }

        // Measure row backgrounds at full table width and computed row heights
        val bgPlaceables = ArrayList<Placeable>(totalRows)
        for (row in 0 until totalRows) {
            val bgIdx = cellCount + row
            if (bgIdx < measurables.size) {
                bgPlaceables.add(
                    measurables[bgIdx].measure(
                        Constraints.fixed(width = totalWidth, height = rowHeights[row])
                    )
                )
            }
        }

        val totalHeight = rowHeights.sum().coerceAtLeast(1)

        layout(totalWidth, totalHeight) {
            var y = 0
            for (row in 0 until totalRows) {
                if (row < bgPlaceables.size) {
                    bgPlaceables[row].placeRelative(0, y)
                }
                y += rowHeights[row]
            }

            y = 0
            for (row in 0 until totalRows) {
                var x = 0
                val rowH = rowHeights[row]
                for (col in 0 until columnCount) {
                    val cell = cellPlaceables[row * columnCount + col]
                    val yOffset = (rowH - cell.height) / 2
                    cell.placeRelative(x, y + yOffset)
                    x += colWidths[col]
                }
                y += rowH
            }
        }
    }
}

private fun TableAlignment.toTextAlign(): TextAlign = when (this) {
    TableAlignment.LEFT -> TextAlign.Start
    TableAlignment.CENTER -> TextAlign.Center
    TableAlignment.RIGHT -> TextAlign.End
}
