package com.example.on_device_qwen.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.on_device_qwen.R
import com.example.on_device_qwen.model.ChatMessage
import com.example.on_device_qwen.model.MemoryItem
import com.example.on_device_qwen.ui.designsystem.SovereignElevation
import com.example.on_device_qwen.ui.designsystem.SovereignStatusDot
import com.example.on_device_qwen.ui.designsystem.SovereignStatusTone
import com.example.on_device_qwen.ui.designsystem.SovereignSurface
import com.example.on_device_qwen.ui.designsystem.SovereignTheme
import com.example.on_device_qwen.viewmodel.ScrollFollowController
import kotlinx.coroutines.launch

@Composable
fun MessageList(
    conversationId: String?,
    messages: List<ChatMessage>,
    isGenerating: Boolean,
    isSpeaking: Boolean,
    lastUsedMemories: List<MemoryItem>,
    onSpeak: (String) -> Unit,
    onStopSpeaking: () -> Unit,
    onEditMessage: (ChatMessage) -> Unit,
    onRegenerateMessage: (Long) -> Unit,
    onSelectSuggestion: (String) -> Unit,
    getFollowUpSuggestions: (String?) -> List<String>,
    onOpenMemoryManager: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SovereignTheme.colors
    val typography = SovereignTheme.typography
    val spacing = SovereignTheme.spacing
    val adaptive = SovereignTheme.adaptive

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val isUserDragging by listState.interactionSource.collectIsDraggedAsState()
    val followController = remember { ScrollFollowController() }
    var showJumpToLatest by remember { mutableStateOf(false) }

    val leadingHeaderCount = if (lastUsedMemories.isNotEmpty()) 1 else 0
    val bottomAnchorIndex = ScrollFollowController.bottomAnchorIndex(messages.size, leadingHeaderCount)

    LaunchedEffect(conversationId) {
        followController.resetForConversationSwitch()
        showJumpToLatest = false
        if (messages.isNotEmpty()) {
            listState.scrollToItem(bottomAnchorIndex)
        }
    }

    LaunchedEffect(isUserDragging) {
        followController.onScrollStateChanged(
            isUserDragging = isUserDragging,
            canScrollForward = listState.canScrollForward,
        )
        showJumpToLatest = followController.userScrolledAway
    }

    LaunchedEffect(listState.canScrollForward) {
        followController.onScrollStateChanged(
            isUserDragging = isUserDragging,
            canScrollForward = listState.canScrollForward,
        )
        showJumpToLatest = followController.userScrolledAway
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            followController.onNewMessageAdded()
            showJumpToLatest = false
            listState.scrollToItem(bottomAnchorIndex)
        }
    }

    val lastMsg = messages.lastOrNull()
    LaunchedEffect(lastMsg?.text?.length, lastMsg?.thinkText?.length) {
        if (isGenerating && messages.isNotEmpty() && !isUserDragging) {
            if (followController.onTokenStreamChunkArrived()) {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val lastMsgListIdx = ScrollFollowController.messageListIndex(messages.lastIndex, leadingHeaderCount)
                if (lastVisible >= lastMsgListIdx - 1) {
                    listState.scrollToItem(bottomAnchorIndex)
                }
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = adaptive.maxContentWidth)
                .fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = adaptive.horizontalGutter,
                    end = adaptive.horizontalGutter,
                    top = spacing.sm,
                    bottom = spacing.xxl,
                ),
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                if (lastUsedMemories.isNotEmpty()) {
                    item(key = "memory_active_badge", contentType = "memory_banner") {
                        val label = if (lastUsedMemories.size == 1) {
                            stringResource(R.string.msg_memories_used_singular)
                        } else {
                            stringResource(R.string.msg_memories_used_plural, lastUsedMemories.size)
                        }
                        SovereignSurface(
                            shape = SovereignTheme.shapes.sm,
                            color = colors.accentContainer,
                            border = BorderStroke(
                                SovereignElevation.hairlineWidth,
                                colors.accentPrimary.copy(alpha = 0.28f),
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(SovereignTheme.shapes.sm)
                                .clickable(role = Role.Button, onClick = onOpenMemoryManager),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = spacing.md, vertical = spacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Psychology,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = colors.onAccentContainer,
                                )
                                Text(
                                    text = label,
                                    style = typography.actionLabel,
                                    color = colors.onAccentContainer,
                                )
                            }
                        }
                    }
                }

                items(
                    items = messages,
                    key = { it.id },
                    contentType = { msg ->
                        when {
                            msg.isUser -> "user"
                            msg.isError -> "assistant_error"
                            else -> "assistant"
                        }
                    },
                ) { message ->
                    val isLast = message.id == lastMsg?.id
                    val suggestions = if (isLast && !message.isUser && !message.isStreaming && !message.isError && !isGenerating) {
                        remember(message.text) { getFollowUpSuggestions(message.text) }
                    } else {
                        emptyList()
                    }

                    MessageBubble(
                        message = message,
                        isGenerating = isGenerating,
                        isSpeaking = isSpeaking,
                        suggestions = suggestions,
                        onSpeak = onSpeak,
                        onStopSpeaking = onStopSpeaking,
                        onEditMessage = { onEditMessage(message) },
                        onRegenerate = { onRegenerateMessage(message.id) },
                        onSelectSuggestion = onSelectSuggestion,
                    )
                }

                // Bottom anchor guarantees tall streaming bubbles scroll cleanly to the latest tokens.
                item(key = "bottom_anchor", contentType = "anchor") {
                    Spacer(Modifier.height(1.dp))
                }
            }

            val jumpLabel = stringResource(R.string.cd_scroll_to_bottom)
            AnimatedVisibility(
                visible = showJumpToLatest && messages.isNotEmpty(),
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = spacing.md),
            ) {
                SovereignSurface(
                    shape = SovereignTheme.shapes.pill,
                    color = colors.surfaceHigh,
                    border = BorderStroke(SovereignElevation.hairlineWidth, colors.hairlineStrong),
                    shadowElevation = SovereignElevation.floatingOverlay,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(SovereignTheme.shapes.pill)
                        .clickable(role = Role.Button) {
                            followController.onJumpToBottomClicked()
                            showJumpToLatest = false
                            scope.launch {
                                listState.animateScrollToItem(bottomAnchorIndex)
                            }
                        }
                        .semantics { contentDescription = jumpLabel },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                        modifier = Modifier.padding(horizontal = spacing.md, vertical = 12.dp),
                    ) {
                        if (isGenerating) {
                            SovereignStatusDot(
                                tone = SovereignStatusTone.ACCENT,
                                pulsing = true,
                                size = 7.dp,
                            )
                        }
                        Text(
                            text = if (isGenerating) {
                                stringResource(R.string.msg_generating_jump)
                            } else {
                                stringResource(R.string.msg_jump_to_latest)
                            },
                            style = typography.actionLabel,
                            color = colors.textPrimary,
                        )
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}
