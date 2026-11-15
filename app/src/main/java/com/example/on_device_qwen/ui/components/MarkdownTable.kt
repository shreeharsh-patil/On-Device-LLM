package com.example.on_device_qwen.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.on_device_qwen.ui.designsystem.SovereignElevation
import com.example.on_device_qwen.ui.designsystem.SovereignTheme
import com.example.on_device_qwen.ui.designsystem.SovereignTypography

enum class TableAlignment {
    LEFT, CENTER, RIGHT
}

@androidx.compose.runtime.Immutable
data class MarkdownTableData(
    val headers: List<String>,
    val alignments: List<TableAlignment>,
    val rows: List<List<String>>
)

/**
 * Responsive Markdown Table component styled with Sovereign Vellum/Obsidian tokens.
 * Features:
 * - Fluid horizontal scrolling for wide datasets
 * - Contrasting header row and subtle hairline borders
 * - Alternating zebra striping on data rows
 * - Accurate column alignment (left, center, right) applied to both header and data cells
 */
@Composable
fun MarkdownTableView(
    table: MarkdownTableData,
    modifier: Modifier = Modifier
) {
    if (table.headers.isEmpty()) return

    val colors = SovereignTheme.colors
    val columnCount = table.headers.size
    val scrollState = rememberScrollState()

    Surface(
        shape = SovereignTheme.shapes.sm,
        color = colors.surfaceHigh,
        border = BorderStroke(SovereignElevation.hairlineWidth, colors.hairlineStrong),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .width(IntrinsicSize.Max)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceBase)
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (colIndex in 0 until columnCount) {
                    val headerText = table.headers.getOrElse(colIndex) { "" }
                    val alignment = table.alignments.getOrElse(colIndex) { TableAlignment.LEFT }
                    val textAlign = alignment.toTextAlign()

                    Box(
                        modifier = Modifier
                            .widthIn(min = 96.dp, max = 220.dp)
                            .padding(horizontal = 12.dp)
                    ) {
                        FormattedInlineText(
                            text = headerText,
                            color = colors.textPrimary,
                            textStyle = SovereignTypography.bodyCompact.copy(fontWeight = FontWeight.SemiBold),
                            textAlign = textAlign,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            HorizontalDivider(
                color = colors.hairlineStrong,
                thickness = SovereignElevation.hairlineWidth
            )

            // Data Rows
            table.rows.forEachIndexed { rowIndex, rowCells ->
                val rowBackground = if (rowIndex % 2 == 0) {
                    Color.Transparent
                } else {
                    colors.surfaceLow
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(rowBackground)
                        .padding(vertical = 9.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (colIndex in 0 until columnCount) {
                        val cellText = rowCells.getOrElse(colIndex) { "" }
                        val alignment = table.alignments.getOrElse(colIndex) { TableAlignment.LEFT }
                        val textAlign = alignment.toTextAlign()

                        Box(
                            modifier = Modifier
                                .widthIn(min = 96.dp, max = 220.dp)
                                .padding(horizontal = 12.dp)
                        ) {
                            FormattedInlineText(
                                text = cellText,
                                color = colors.textPrimary,
                                textStyle = SovereignTypography.bodyCompact,
                                textAlign = textAlign,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                if (rowIndex < table.rows.lastIndex) {
                    HorizontalDivider(
                        color = colors.hairlineSubtle,
                        thickness = SovereignElevation.hairlineWidth
                    )
                }
            }
        }
    }
}

private fun TableAlignment.toTextAlign(): TextAlign = when (this) {
    TableAlignment.LEFT -> TextAlign.Start
    TableAlignment.CENTER -> TextAlign.Center
    TableAlignment.RIGHT -> TextAlign.End
}
