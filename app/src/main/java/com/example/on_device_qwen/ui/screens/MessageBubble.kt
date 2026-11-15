package com.example.on_device_qwen.ui.screens

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.on_device_qwen.R
import com.example.on_device_qwen.model.ChatMessage
import com.example.on_device_qwen.ui.components.BenchmarkBadge
import com.example.on_device_qwen.ui.components.MarkdownText
import com.example.on_device_qwen.ui.components.ThinkingSection
import com.example.on_device_qwen.ui.designsystem.SovereignBadge
import com.example.on_device_qwen.ui.designsystem.SovereignElevation
import com.example.on_device_qwen.ui.designsystem.SovereignErrorCard
import com.example.on_device_qwen.ui.designsystem.SovereignIconButton
import com.example.on_device_qwen.ui.designsystem.SovereignSecondaryButton
import com.example.on_device_qwen.ui.designsystem.SovereignStatusTone
import com.example.on_device_qwen.ui.designsystem.SovereignSurface
import com.example.on_device_qwen.ui.designsystem.SovereignTheme
import com.example.on_device_qwen.ui.designsystem.SovereignTypingIndicator
import java.io.File

@Composable
fun MessageBubble(
    message: ChatMessage,
    isGenerating: Boolean,
    isSpeaking: Boolean,
    suggestions: List<String>,
    onSpeak: (String) -> Unit,
    onStopSpeaking: () -> Unit,
    onEditMessage: () -> Unit,
    onRegenerate: () -> Unit,
    onSelectSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (message.isUser) {
        UserMessageTurn(
            message = message,
            isGenerating = isGenerating,
            onEditMessage = onEditMessage,
            modifier = modifier,
        )
    } else {
        AssistantMessageTurn(
            message = message,
            isGenerating = isGenerating,
            isSpeaking = isSpeaking,
            suggestions = suggestions,
            onSpeak = onSpeak,
            onStopSpeaking = onStopSpeaking,
            onRegenerate = onRegenerate,
            onSelectSuggestion = onSelectSuggestion,
            modifier = modifier,
        )
    }
}

@Composable
private fun UserMessageTurn(
    message: ChatMessage,
    isGenerating: Boolean,
    onEditMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SovereignTheme.colors
    val typography = SovereignTheme.typography
    val spacing = SovereignTheme.spacing
    val context = LocalContext.current

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.End,
    ) {
        SovereignIconButton(
            onClick = onEditMessage,
            enabled = !isGenerating,
            contentDescription = stringResource(R.string.cd_edit_message),
            contentColor = colors.textMuted,
        ) {
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }

        SovereignSurface(
            shape = SovereignTheme.shapes.userBubble,
            color = colors.userBubble,
            border = BorderStroke(SovereignElevation.hairlineWidth, colors.hairlineSubtle),
            modifier = Modifier.widthIn(max = 520.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                message.imagePath?.let { path ->
                    val imageRequest = remember(path) {
                        ImageRequest.Builder(context)
                            .data(File(path))
                            .crossfade(true)
                            .build()
                    }
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = stringResource(R.string.cd_attached_image),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .clip(SovereignTheme.shapes.sm),
                    )
                }
                Text(
                    text = message.text,
                    style = typography.chatProse,
                    color = colors.textPrimary,
                )
            }
        }
    }
}

@Composable
private fun AssistantMessageTurn(
    message: ChatMessage,
    isGenerating: Boolean,
    isSpeaking: Boolean,
    suggestions: List<String>,
    onSpeak: (String) -> Unit,
    onStopSpeaking: () -> Unit,
    onRegenerate: () -> Unit,
    onSelectSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SovereignTheme.colors
    val typography = SovereignTheme.typography
    val spacing = SovereignTheme.spacing
    val context = LocalContext.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        if (message.isError) {
            SovereignErrorCard(
                title = stringResource(R.string.msg_error_title),
                message = message.text,
                actions = {
                    SovereignSecondaryButton(
                        text = stringResource(R.string.label_retry),
                        onClick = onRegenerate,
                        enabled = !isGenerating,
                        leadingIcon = Icons.Rounded.Refresh,
                    )
                },
            )
            return
        }

        if (!message.thinkText.isNullOrBlank() || (message.isStreaming && message.text.isBlank())) {
            ThinkingSection(
                thinkText = message.thinkText.orEmpty(),
                isThinking = message.isStreaming && message.text.isBlank(),
            )
        }

        if (message.text.isNotEmpty()) {
            MarkdownText(
                text = message.text,
                color = colors.textPrimary,
                isStreaming = message.isStreaming,
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (message.isStreaming && message.thinkText.isNullOrBlank()) {
            SovereignTypingIndicator()
        }

        if (message.isCancelled) {
            SovereignBadge(
                text = stringResource(R.string.msg_stopped_partial),
                tone = SovereignStatusTone.WARNING,
            )
        }

        if (message.benchmark != null && (message.text.isNotEmpty() || message.isStreaming)) {
            BenchmarkBadge(
                benchmark = message.benchmark,
                isStreaming = message.isStreaming,
            )
        }

        if (!message.isStreaming && message.text.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val copiedMsg = stringResource(R.string.msg_copied_toast)
                SovereignIconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("Response", message.text))
                        Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
                    },
                    contentDescription = stringResource(R.string.cd_copy_message),
                    hapticFeedback = true,
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                }

                val speakCd = if (isSpeaking) {
                    stringResource(R.string.cd_stop_speaking)
                } else {
                    stringResource(R.string.cd_read_aloud)
                }
                SovereignIconButton(
                    onClick = { if (isSpeaking) onStopSpeaking() else onSpeak(message.text) },
                    contentDescription = speakCd,
                    contentColor = if (isSpeaking) colors.accentPrimary else colors.textSecondary,
                    selected = isSpeaking,
                ) {
                    Icon(
                        imageVector = if (isSpeaking) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                    )
                }

                val shareTitle = stringResource(R.string.msg_share_chooser_title)
                val noShareApp = stringResource(R.string.msg_no_share_app)
                SovereignIconButton(
                    onClick = {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            putExtra(Intent.EXTRA_TEXT, message.text)
                            type = "text/plain"
                        }
                        try {
                            context.startActivity(Intent.createChooser(sendIntent, shareTitle))
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, noShareApp, Toast.LENGTH_SHORT).show()
                        }
                    },
                    contentDescription = stringResource(R.string.cd_share_message),
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                }

                SovereignIconButton(
                    onClick = onRegenerate,
                    enabled = !isGenerating,
                    contentDescription = stringResource(R.string.cd_regenerate),
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }

        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                suggestions.forEach { suggestion ->
                    SovereignSurface(
                        shape = SovereignTheme.shapes.pill,
                        color = colors.surfaceLow,
                        border = BorderStroke(SovereignElevation.hairlineWidth, colors.hairlineSubtle),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(SovereignTheme.shapes.pill)
                            .clickable(role = Role.Button) { onSelectSuggestion(suggestion) },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(PaddingValues(horizontal = 14.dp, vertical = 12.dp)),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = colors.accentPrimary,
                            )
                            Text(
                                text = suggestion,
                                style = typography.bodyCompact,
                                color = colors.textPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}
