package com.example.on_device_qwen.ui.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.on_device_qwen.R

/**
 * Hairline-bordered text field following Obsidian & Vellum surface and focus tokens.
 */
@Composable
fun SovereignTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    errorText: String? = null,
    isError: Boolean = errorText != null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    textStyle: TextStyle = SovereignTypography.bodyCompact,
    shape: Shape = SovereignTheme.shapes.sm,
    containerColor: Color = SovereignTheme.colors.surfaceLow,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val colors = SovereignTheme.colors
    val motion = SovereignTheme.motion
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val borderColor by animateColorAsState(
        targetValue = when {
            isError -> colors.error
            isFocused -> colors.accentPrimary
            else -> colors.hairlineSubtle
        },
        animationSpec = motion.effectsQuick(),
        label = "SovereignTextFieldBorder",
    )

    val resolvedBg by animateColorAsState(
        targetValue = if (isFocused) colors.surfaceHigh else containerColor,
        animationSpec = motion.effectsQuick(),
        label = "SovereignTextFieldBg",
    )

    Column(modifier = modifier) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                style = SovereignTypography.actionLabel,
                color = if (isError) colors.error else colors.textSecondary,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        Surface(
            shape = shape,
            color = resolvedBg,
            border = BorderStroke(SovereignElevation.hairlineWidth, borderColor),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
            ) {
                if (leadingIcon != null) {
                    Box(modifier = Modifier.padding(end = 10.dp)) {
                        CompositionLocalProvider(LocalContentColor provides colors.textMuted) {
                            leadingIcon()
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty() && !placeholder.isNullOrEmpty()) {
                        Text(
                            text = placeholder,
                            style = textStyle,
                            color = colors.textMuted,
                            maxLines = if (singleLine) 1 else maxLines,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        enabled = enabled,
                        readOnly = readOnly,
                        singleLine = singleLine,
                        minLines = minLines,
                        maxLines = maxLines,
                        textStyle = textStyle.copy(
                            color = if (enabled) colors.textPrimary else colors.textMuted,
                        ),
                        cursorBrush = SolidColor(if (isError) colors.error else colors.accentPrimary),
                        interactionSource = interactionSource,
                        keyboardOptions = keyboardOptions,
                        keyboardActions = keyboardActions,
                        visualTransformation = visualTransformation,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (trailingIcon != null) {
                    Box(modifier = Modifier.padding(start = 8.dp)) {
                        CompositionLocalProvider(LocalContentColor provides colors.textSecondary) {
                            trailingIcon()
                        }
                    }
                }
            }
        }

        val helper = errorText ?: supportingText
        if (!helper.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = helper,
                style = SovereignTypography.telemetry,
                color = if (isError) colors.error else colors.textMuted,
            )
        }
    }
}

/**
 * Compact hairline search bar with leading search glyph and 48.dp clear icon button.
 */
@Composable
fun SovereignSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search…",
    enabled: Boolean = true,
    shape: Shape = SovereignTheme.shapes.sm,
    onSearch: (() -> Unit)? = null,
) {
    val colors = SovereignTheme.colors
    val motion = SovereignTheme.motion
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val borderColor by animateColorAsState(
        targetValue = if (isFocused) colors.accentPrimary else colors.hairlineSubtle,
        animationSpec = motion.effectsQuick(),
        label = "SearchBarBorder",
    )

    val containerColor by animateColorAsState(
        targetValue = if (isFocused) colors.surfaceHigh else colors.surfaceLow,
        animationSpec = motion.effectsQuick(),
        label = "SearchBarBg",
    )

    Surface(
        shape = shape,
        color = containerColor,
        border = BorderStroke(SovereignElevation.hairlineWidth, borderColor),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = if (isFocused) colors.accentPrimary else colors.textMuted,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = SovereignTypography.bodyCompact,
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    enabled = enabled,
                    singleLine = true,
                    textStyle = SovereignTypography.bodyCompact.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.accentPrimary),
                    interactionSource = interactionSource,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter = fadeIn(motion.effectsQuick()),
                exit = fadeOut(motion.effectsQuick()),
            ) {
                SovereignIconButton(
                    onClick = { onQueryChange("") },
                    contentDescription = stringResource(R.string.cd_clear_search),
                    visualSize = 36.dp,
                    contentColor = colors.textSecondary,
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
}
