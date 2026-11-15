package com.example.on_device_qwen.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.on_device_qwen.R
import com.example.on_device_qwen.ui.designsystem.SovereignElevation
import com.example.on_device_qwen.ui.designsystem.SovereignIconButton
import com.example.on_device_qwen.ui.designsystem.SovereignStatusDot
import com.example.on_device_qwen.ui.designsystem.SovereignStatusTone
import com.example.on_device_qwen.ui.designsystem.SovereignSurface
import com.example.on_device_qwen.ui.designsystem.SovereignTheme

@Composable
fun ChatComposer(
    isGenerating: Boolean,
    isVisionModel: Boolean,
    onSendMessage: (String, Uri?) -> Unit,
    onStopGeneration: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SovereignTheme.colors
    val typography = SovereignTheme.typography
    val spacing = SovereignTheme.spacing
    val adaptive = SovereignTheme.adaptive
    val hapticsOn = SovereignTheme.hapticsEnabled

    var inputText by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(isVisionModel) {
        if (!isVisionModel && selectedImageUri != null) {
            selectedImageUri = null
        }
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null && isVisionModel) {
            selectedImageUri = uri
            if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val defaultImagePrompt = stringResource(R.string.composer_default_image_prompt)
    val canSend = (inputText.isNotBlank() || (isVisionModel && selectedImageUri != null)) && !isGenerating

    val submitMessage = {
        if (canSend) {
            if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            val textToSend = if (inputText.isBlank() && selectedImageUri != null) {
                defaultImagePrompt
            } else {
                inputText
            }
            onSendMessage(textToSend, if (isVisionModel) selectedImageUri else null)
            inputText = ""
            selectedImageUri = null
            keyboardController?.hide()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = adaptive.horizontalGutter, vertical = spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = adaptive.maxContentWidth)
                .fillMaxWidth(),
        ) {
            AnimatedVisibility(
                visible = isVisionModel && selectedImageUri != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                selectedImageUri?.let { uri ->
                    Box(
                        modifier = Modifier
                            .padding(start = spacing.sm, bottom = spacing.xs)
                            .size(84.dp),
                    ) {
                        val req = remember(uri) {
                            ImageRequest.Builder(context)
                                .data(uri)
                                .crossfade(true)
                                .build()
                        }
                        AsyncImage(
                            model = req,
                            contentDescription = stringResource(R.string.cd_attached_image),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(80.dp)
                                .clip(SovereignTheme.shapes.md)
                                .background(colors.surfaceHigh),
                        )
                        SovereignIconButton(
                            onClick = { selectedImageUri = null },
                            contentDescription = stringResource(R.string.cd_remove_image),
                            visualSize = 30.dp,
                            containerColor = colors.bgCanvas.copy(alpha = 0.9f),
                            contentColor = colors.textPrimary,
                            modifier = Modifier.align(Alignment.TopEnd),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            SovereignSurface(
                shape = SovereignTheme.shapes.xl,
                color = colors.surfaceLow,
                border = BorderStroke(
                    width = SovereignElevation.hairlineWidth,
                    color = if (isGenerating) colors.accentPrimary.copy(alpha = 0.45f) else colors.hairlineStrong,
                ),
                shadowElevation = SovereignElevation.floatingOverlay,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 52.dp)
                        .padding(horizontal = spacing.xs, vertical = spacing.xxs),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    if (isVisionModel) {
                        SovereignIconButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                            enabled = !isGenerating,
                            contentDescription = stringResource(R.string.cd_attach_image),
                            contentColor = colors.textSecondary,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AddPhotoAlternate,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    } else {
                        Spacer(Modifier.width(spacing.sm))
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .align(Alignment.CenterVertically)
                            .padding(vertical = 10.dp),
                    ) {
                        if (inputText.isEmpty()) {
                            if (isGenerating) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                                ) {
                                    SovereignStatusDot(
                                        tone = SovereignStatusTone.ACCENT,
                                        pulsing = true,
                                        size = 7.dp,
                                    )
                                    Text(
                                        text = stringResource(R.string.composer_generating_status),
                                        style = typography.chatProse,
                                        color = colors.textMuted,
                                    )
                                }
                            } else {
                                Text(
                                    text = stringResource(
                                        if (isVisionModel) R.string.composer_placeholder_vision
                                        else R.string.composer_placeholder_text,
                                    ),
                                    style = typography.chatProse,
                                    color = colors.textMuted,
                                )
                            }
                        }

                        BasicTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            textStyle = typography.chatProse.copy(color = colors.textPrimary),
                            cursorBrush = SolidColor(colors.accentPrimary),
                            maxLines = 5,
                            enabled = !isGenerating,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { submitMessage() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (isGenerating) {
                        SovereignIconButton(
                            onClick = {
                                if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onStopGeneration()
                            },
                            contentDescription = stringResource(R.string.cd_stop_generation),
                            containerColor = colors.ctaInk,
                            contentColor = colors.onCtaInk,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Stop,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        SovereignIconButton(
                            onClick = submitMessage,
                            enabled = canSend,
                            contentDescription = stringResource(R.string.cd_send_message),
                            containerColor = if (canSend) colors.accentPrimary else colors.surfaceBase,
                            contentColor = if (canSend) colors.onAccent else colors.textMuted,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowUpward,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
