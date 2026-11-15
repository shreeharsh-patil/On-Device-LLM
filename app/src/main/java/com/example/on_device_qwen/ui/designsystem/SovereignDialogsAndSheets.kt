package com.example.on_device_qwen.ui.designsystem

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.example.on_device_qwen.R

/**
 * Adaptive modal sheet container:
 * - On [WindowWidthClass.COMPACT]: full-screen edge-to-edge canvas with `safeDrawingPadding()`
 *   and `imePadding()` + predictive back dismissal.
 * - On [WindowWidthClass.MEDIUM] / [WindowWidthClass.EXPANDED]: centered 640.dp rounded
 *   hairline modal over scrim with `safeDrawingPadding()` and `imePadding()`.
 */
@Composable
fun SovereignModalSheet(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    dismissOnClickOutside: Boolean = true,
    headerActions: @Composable RowScope.() -> Unit = {},
    stickyHeaderExtra: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = SovereignTheme.colors
    val adaptive = SovereignTheme.adaptive
    val shapes = SovereignTheme.shapes

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = dismissOnClickOutside,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val view = LocalView.current
        val isDark = colors.isDark
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !isDark
                controller.isAppearanceLightNavigationBars = !isDark
            }
        }

        BackHandler(enabled = true, onBack = onDismissRequest)

        if (adaptive.isCompact) {
            Surface(
                modifier = modifier.fillMaxSize(),
                color = colors.bgCanvas,
                contentColor = colors.textPrimary,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .imePadding(),
                ) {
                    SovereignSheetHeader(
                        title = title,
                        subtitle = subtitle,
                        onClose = onDismissRequest,
                        actions = headerActions,
                        stickyExtra = stickyHeaderExtra,
                    )
                    content()
                }
            }
        } else {
            val backdropInteraction = remember { MutableInteractionSource() }
            val sheetInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.scrim)
                    .clickable(
                        interactionSource = backdropInteraction,
                        indication = null,
                        enabled = dismissOnClickOutside,
                        onClick = onDismissRequest,
                    )
                    .safeDrawingPadding()
                    .imePadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = modifier
                        .widthIn(max = adaptive.maxSheetWidth)
                        .fillMaxWidth()
                        .fillMaxHeight(0.88f)
                        .clickable(
                            interactionSource = sheetInteraction,
                            indication = null,
                            onClick = {},
                        ),
                    shape = shapes.lg,
                    color = colors.bgCanvas,
                    contentColor = colors.textPrimary,
                    border = BorderStroke(SovereignElevation.hairlineWidth, colors.hairlineStrong),
                    tonalElevation = 0.dp,
                    shadowElevation = SovereignElevation.floatingOverlay,
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        SovereignSheetHeader(
                            title = title,
                            subtitle = subtitle,
                            onClose = onDismissRequest,
                            actions = headerActions,
                            stickyExtra = stickyHeaderExtra,
                        )
                        content()
                    }
                }
            }
        }
    }
}

@Composable
private fun SovereignSheetHeader(
    title: String,
    subtitle: String?,
    onClose: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    stickyExtra: (@Composable ColumnScope.() -> Unit)?,
) {
    val colors = SovereignTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgCanvas),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = SovereignTypography.sheetHeader,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = SovereignTypography.bodyCompact,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                content = actions,
            )
            SovereignIconButton(
                onClick = onClose,
                contentDescription = stringResource(R.string.cd_close),
                contentColor = colors.textSecondary,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (stickyExtra != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                content = stickyExtra,
            )
        }
        SovereignDivider()
    }
}

/**
 * Obsidian & Vellum confirmation / prompt dialog with hairline border,
 * zero M3 purple fallback, and `imePadding()` for keyboard-safe input dialogs.
 */
@Composable
fun SovereignAlertDialog(
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    dismissText: String? = "Cancel",
    onDismiss: () -> Unit = onDismissRequest,
    confirmEnabled: Boolean = true,
    isDestructive: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = SovereignTheme.colors
    val shapes = SovereignTheme.shapes

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val view = LocalView.current
        val isDark = colors.isDark
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !isDark
                controller.isAppearanceLightNavigationBars = !isDark
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest,
                )
                .safeDrawingPadding()
                .imePadding()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = modifier
                    .widthIn(min = 280.dp, max = 460.dp)
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                shape = shapes.lg,
                color = colors.surfaceHigh,
                contentColor = colors.textPrimary,
                border = BorderStroke(SovereignElevation.hairlineWidth, colors.hairlineStrong),
                tonalElevation = 0.dp,
                shadowElevation = SovereignElevation.floatingOverlay,
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (icon != null) {
                            Box(modifier = Modifier.padding(end = 12.dp)) {
                                icon()
                            }
                        }
                        Text(
                            text = title,
                            style = SovereignTypography.cardTitle,
                            color = colors.textPrimary,
                            modifier = Modifier.semantics { heading() },
                        )
                    }

                    if (!text.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = text,
                            style = SovereignTypography.bodyCompact,
                            color = colors.textSecondary,
                        )
                    }

                    if (content != null) {
                        Spacer(Modifier.height(14.dp))
                        content()
                    }

                    Spacer(Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!dismissText.isNullOrBlank()) {
                            SovereignSecondaryButton(
                                text = dismissText,
                                onClick = onDismiss,
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        if (isDestructive) {
                            SovereignTonalButton(
                                text = confirmText,
                                onClick = onConfirm,
                                enabled = confirmEnabled,
                                containerColor = colors.errorContainer,
                                contentColor = colors.onErrorContainer,
                                border = BorderStroke(
                                    SovereignElevation.hairlineWidth,
                                    colors.error.copy(alpha = 0.4f),
                                ),
                            )
                        } else {
                            SovereignPrimaryButton(
                                text = confirmText,
                                onClick = onConfirm,
                                enabled = confirmEnabled,
                            )
                        }
                    }
                }
            }
        }
    }
}
