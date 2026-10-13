package com.example.on_device_qwen.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class TableAlignment {
    LEFT, CENTER, RIGHT
}

data class MarkdownTableData(
    val headers: List<String>,
    val alignments: List<TableAlignment>,
    val rows: List<List<String>>
)

/**
 * Modern ChatGPT-style Markdown Table component.
 * Features:
 * - Fluid horizontal scrolling for wide datasets
 * - Contrasting header row with bold typography
 * - Alternating zebra stripes on data rows
 * - Proper column text alignment (left, center, right)
 * - Rounded borders matching ChatGPT aesthetic
 */
@Composable
fun MarkdownTableView(
    table: MarkdownTableData,
    modifier: Modifier = Modifier
) {
    if (table.headers.isEmpty()) return

    val columnCount = table.headers.size
    val scrollState = rememberScrollState()

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (colIndex in 0 until columnCount) {
                    val headerText = table.headers.getOrElse(colIndex) { "" }
                    val alignment = table.alignments.getOrElse(colIndex) { TableAlignment.LEFT }
                    val textAlign = when (alignment) {
                        TableAlignment.LEFT -> TextAlign.Start
                        TableAlignment.CENTER -> TextAlign.Center
                        TableAlignment.RIGHT -> TextAlign.End
                    }

                    Box(
                        modifier = Modifier
                            .widthIn(min = 90.dp, max = 220.dp)
                            .padding(horizontal = 12.dp)
                    ) {
                        Text(
                            text = headerText,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = textAlign,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                thickness = 1.dp
            )

            // Data Rows
            table.rows.forEachIndexed { rowIndex, rowCells ->
                val isEven = rowIndex % 2 == 0
                val rowBackground = if (isEven) {
                    Color.Transparent
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                }

                Row(
                    modifier = Modifier
                        .background(rowBackground)
                        .padding(vertical = 9.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (colIndex in 0 until columnCount) {
                        val cellText = rowCells.getOrElse(colIndex) { "" }
                        val alignment = table.alignments.getOrElse(colIndex) { TableAlignment.LEFT }
                        val textAlign = when (alignment) {
                            TableAlignment.LEFT -> TextAlign.Start
                            TableAlignment.CENTER -> TextAlign.Center
                            TableAlignment.RIGHT -> TextAlign.End
                        }

                        Box(
                            modifier = Modifier
                                .widthIn(min = 90.dp, max = 220.dp)
                                .padding(horizontal = 12.dp)
                        ) {
                            FormattedInlineText(
                                text = cellText,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                if (rowIndex < table.rows.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }
}
