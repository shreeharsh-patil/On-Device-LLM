package com.example.on_device_qwen.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.on_device_qwen.R
import com.example.on_device_qwen.model.ExecutionBackendInfo
import com.example.on_device_qwen.ui.designsystem.SovereignBadge
import com.example.on_device_qwen.ui.designsystem.SovereignHairlineCard
import com.example.on_device_qwen.ui.designsystem.SovereignStatusTone
import com.example.on_device_qwen.ui.designsystem.SovereignTheme

private data class StarterPrompt(
    val title: String,
    val prompt: String,
    val icon: ImageVector,
)

@Composable
fun EmptyChatState(
    modelName: String,
    executionBackendInfo: ExecutionBackendInfo?,
    isVisionSupported: Boolean,
    onSelectPrompt: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SovereignTheme.colors
    val typography = SovereignTheme.typography
    val spacing = SovereignTheme.spacing
    val adaptive = SovereignTheme.adaptive

    val prompts = buildList {
        add(
            StarterPrompt(
                title = "Explain architecture & trade-offs",
                prompt = "Compare B-trees vs LSM-trees for write-heavy mobile databases and include a concise comparison table.",
                icon = Icons.Rounded.Lightbulb,
            ),
        )
        add(
            StarterPrompt(
                title = "Write & test clean code",
                prompt = "Write an LRU cache in Kotlin with O(1) get/put operations and unit test edge cases.",
                icon = Icons.Rounded.Code,
            ),
        )
        add(
            StarterPrompt(
                title = "Draft clearly & concisely",
                prompt = "Turn rough engineering notes into a crisp 3-bullet status update without filler words.",
                icon = Icons.Rounded.Description,
            ),
        )
        if (isVisionSupported) {
            add(
                StarterPrompt(
                    title = "Analyze an attached image",
                    prompt = "Inspect the attached image step by step and summarize key text, layout, and details.",
                    icon = Icons.Rounded.ImageSearch,
                ),
            )
        } else {
            add(
                StarterPrompt(
                    title = "Design a system flowchart",
                    prompt = "Outline a local-first sync queue with retries and exponential backoff as a Mermaid flowchart.",
                    icon = Icons.Rounded.AutoAwesome,
                ),
            )
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = adaptive.maxContentWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = adaptive.horizontalGutter, vertical = spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SovereignBadge(
                    text = modelName,
                    tone = SovereignStatusTone.ACCENT,
                    leadingDot = true,
                )
                executionBackendInfo?.let { info ->
                    val label = if (info.isFallback) {
                        "${info.active.chipText} (fallback from ${info.requested.chipText})"
                    } else {
                        info.active.chipText
                    }
                    SovereignBadge(
                        text = label,
                        tone = if (info.isFallback) SovereignStatusTone.WARNING else SovereignStatusTone.SUCCESS,
                        leadingIcon = if (info.isFallback) Icons.Rounded.WarningAmber else null,
                    )
                }
                SovereignBadge(
                    text = if (isVisionSupported) "Text + Vision" else "Text Only",
                    tone = if (isVisionSupported) SovereignStatusTone.VIOLET else SovereignStatusTone.NEUTRAL,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(
                    text = stringResource(R.string.empty_chat_headline),
                    style = typography.editorialGreeting,
                    color = colors.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.empty_chat_subtitle),
                    style = typography.chatProse,
                    color = colors.textSecondary,
                )
            }

            Text(
                text = stringResource(R.string.empty_chat_prompts_label).uppercase(),
                style = typography.monoTag,
                color = colors.textMuted,
            )

            if (adaptive.isCompact) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    prompts.forEach { item ->
                        StarterPromptCard(item = item, onClick = { onSelectPrompt(item.prompt) })
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    prompts.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                        ) {
                            pair.forEach { item ->
                                Box(modifier = Modifier.weight(1f)) {
                                    StarterPromptCard(
                                        item = item,
                                        onClick = { onSelectPrompt(item.prompt) },
                                    )
                                }
                            }
                            if (pair.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StarterPromptCard(
    item: StarterPrompt,
    onClick: () -> Unit,
) {
    val colors = SovereignTheme.colors
    val typography = SovereignTheme.typography
    val spacing = SovereignTheme.spacing

    SovereignHairlineCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(spacing.md),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = colors.accentPrimary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = item.title,
                style = typography.cardTitle,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(spacing.xs))
        Text(
            text = item.prompt,
            style = typography.bodyCompact,
            color = colors.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
