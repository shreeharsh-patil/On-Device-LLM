package com.example.on_device_qwen.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.on_device_qwen.R
import com.example.on_device_qwen.ui.designsystem.SovereignBanner
import com.example.on_device_qwen.ui.designsystem.SovereignElevation
import com.example.on_device_qwen.ui.designsystem.SovereignIconButton
import com.example.on_device_qwen.ui.designsystem.SovereignStatusTone
import com.example.on_device_qwen.ui.designsystem.SovereignSurface
import com.example.on_device_qwen.ui.designsystem.SovereignTheme

private val NoOpOpenModelManager: () -> Unit = {}

/**
 * Floating Obsidian & Vellum message composer pill (`26.dp` radius) anchored over
 * a subtle `bgCanvas` top fade scrim. Supports vision attachments via Coil async
 * decoding, 1..6 line auto-expanding input, and spring-animated Send/Stop transitions.
 */
@Composable
fun ChatComposer(
    isGenerating: Boolean,
    isVisionModel: Boolean,
    onSendMessage: (String, Uri?) -> Unit,
    onStopGeneration: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenModelManager: () -> Unit = NoOpOpenModelManager,
    modelTitle: String = "",
) {
    val colors = SovereignTheme.colors
    val shapes = SovereignTheme.shapes
    val spacing = SovereignTheme.spacing
    val motion = SovereignTheme.motion
    val typography = SovereignTheme.typography
    val adaptive = SovereignTheme.adaptive
    val hapticsEnabled = SovereignTheme.hapticsEnabled

    var inputText by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var showTextOnlyNotice by remember { mutableStateOf(false) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val hasModelManagerAction = onOpenModelManager !== NoOpOpenModelManager

    LaunchedEffect(isVisionModel) {
        if (isVisionModel) {
            showTextOnlyNotice = false
        } else {
            selectedImageUri = null
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            showTextOnlyNotice = false
            if (hapticsEnabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    val canSend = (inputText.isNotBlank() || selectedImageUri != null) && !isGenerating

    val handleSend = {
        if (canSend) {
            if (hapticsEnabled) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            val textToSend = if (inputText.isBlank() && selectedImageUri != null) {
                "Describe this image in detail."
            } else {
                inputText
            }
            onSendMessage(textToSend, selectedImageUri)
            inputText = ""
            selectedImageUri = null
            keyboardController?.hide()
        }
    }

    val pillBorderColor by animateColorAsState(
        targetValue = if (isFocused) {
            colors.accentPrimary.copy(alpha = 0.55f)
        } else {
            colors.hairlineSubtle
        },
        animationSpec = motion.effectsQuick(),
        label = "ComposerFocusBorder",
    )

    val topScrimBrush = remember(colors.bgCanvas) {
        Brush.verticalGradient(
            0.0f to Color.Transparent,
            0.22f to colors.bgCanvas.copy(alpha = 0.88f),
            1.0f to colors.bgCanvas,
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(topScrimBrush)
            .navigationBarsPadding()
            .imePadding()
            .padding(
                start = spacing.screenGutter(adaptive),
                end = spacing.screenGutter(adaptive),
                top = 12.dp,
                bottom = 10.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = adaptive.maxContentWidth)
                .fillMaxWidth(),
        ) {
            SovereignBanner(
                message = if (showTextOnlyNotice && !isVisionModel) {
                    "Current model is text-only — switch to a Vision model to attach images"
                } else {
                    null
                },
                tone = SovereignStatusTone.ACCENT,
                actionLabel = if (hasModelManagerAction) "Switch Model" else null,
                onAction = if (hasModelManagerAction) {
                    {
                        showTextOnlyNotice = false
                        onOpenModelManager()
                    }
                } else {
                    null
                },
                onDismiss = { showTextOnlyNotice = false },
                modifier = Modifier.padding(bottom = 8.dp),
            )

            AnimatedVisibility(
                visible = selectedImageUri != null,
                enter = fadeIn(motion.effectsQuick()) + expandVertically(motion.spatialExpressive()),
                exit = fadeOut(motion.effectsQuick()) + shrinkVertically(motion.spatialFast()),
            ) {
                val uri = selectedImageUri
                if (uri != null) {
                    SovereignSurface(
                        shape = shapes.md,
                        color = colors.surfaceLow,
                        border = BorderStroke(SovereignElevation.hairlineWidth, colors.hairlineSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                        ) {
                            AsyncImage(
                                model = uri,
                                contentDescription = "Attached photo preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(shapes.sm)
                                    .background(colors.surfaceBase),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Image attached",
                                    style = typography.pillTitle,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "Multimodal visual query ready",
                                    style = typography.bodyCompact,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            SovereignIconButton(
                                onClick = { selectedImageUri = null },
                                contentDescription = stringResource(R.string.cd_remove_image),
                                visualSize = 36.dp,
                                contentColor = colors.textSecondary,
                                hapticFeedback = true,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }

            Surface(
                shape = shapes.xl,
                color = colors.surfaceHigh,
                contentColor = colors.textPrimary,
                border = BorderStroke(SovereignElevation.hairlineWidth, pillBorderColor),
                tonalElevation = 0.dp,
                shadowElevation = SovereignElevation.floatingOverlay,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    SovereignIconButton(
                        onClick = {
                            if (isVisionModel) {
                                showTextOnlyNotice = false
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            } else {
                                showTextOnlyNotice = true
                            }
                        },
                        contentDescription = if (isVisionModel) {
                            stringResource(R.string.cd_attach_image)
                        } else {
                            "Attach image (requires Vision model)"
                        },
                        visualSize = 38.dp,
                        containerColor = colors.surfaceBase,
                        contentColor = if (isVisionModel) colors.textPrimary else colors.textMuted,
                        hapticFeedback = true,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (inputText.isEmpty()) {
                            val placeholder = when {
                                isGenerating -> "Generating response…"
                                modelTitle.isNotBlank() -> "Message $modelTitle…"
                                else -> "Ask anything offline…"
                            }
                            Text(
                                text = placeholder,
                                style = typography.chatProse,
                                color = colors.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        BasicTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            textStyle = typography.chatProse.copy(color = colors.textPrimary),
                            cursorBrush = SolidColor(colors.accentPrimary),
                            interactionSource = interactionSource,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { handleSend() }),
                            minLines = 1,
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    AnimatedContent(
                        targetState = isGenerating,
                        transitionSpec = {
                            val enter = fadeIn(motion.effectsQuick()) + scaleIn(
                                initialScale = if (motion.reducedMotion) 1f else 0.82f,
                                animationSpec = motion.spatialExpressive(),
                            )
                            val exit = fadeOut(motion.effectsQuick()) + scaleOut(
                                targetScale = if (motion.reducedMotion) 1f else 0.82f,
                                animationSpec = motion.spatialExpressive(),
                            )
                            enter togetherWith exit
                        },
                        label = "SendStopButtonMorph",
                    ) { generating ->
                        if (generating) {
                            SovereignIconButton(
                                onClick = onStopGeneration,
                                contentDescription = stringResource(R.string.cd_stop_generation),
                                visualSize = 38.dp,
                                containerColor = colors.errorContainer,
                                contentColor = colors.error,
                                border = BorderStroke(
                                    SovereignElevation.hairlineWidth,
                                    colors.error.copy(alpha = 0.40f),
                                ),
                                hapticFeedback = true,
                                hapticType = HapticFeedbackType.LongPress,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Stop,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else {
                            SovereignIconButton(
                                onClick = { handleSend() },
                                enabled = canSend,
                                contentDescription = stringResource(R.string.cd_send_message),
                                visualSize = 38.dp,
                                containerColor = if (canSend) colors.ctaInk else colors.surfaceBase,
                                contentColor = if (canSend) colors.onCtaInk else colors.textMuted,
                                disabledContentColor = colors.textMuted.copy(alpha = 0.45f),
                                hapticFeedback = false,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowUpward,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
