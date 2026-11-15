package com.example.on_device_qwen

import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.on_device_qwen.data.preferences.ThemeMode
import com.example.on_device_qwen.ui.ChatScreen
import com.example.on_device_qwen.ui.theme.OnDeviceQwenTheme
import com.example.on_device_qwen.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {

    private val chatViewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val initialSystemDark =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        applySystemBars(isDark = initialSystemDark)
        super.onCreate(savedInstanceState)

        setContent {
            val themeMode by chatViewModel.themeMode.collectAsStateWithLifecycle()
            val hapticsEnabled by chatViewModel.hapticsEnabled.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }

            DisposableEffect(isDark) {
                applySystemBars(isDark = isDark)
                onDispose {}
            }

            OnDeviceQwenTheme(
                darkTheme = isDark,
                dynamicColor = false,
                hapticsEnabled = hapticsEnabled,
            ) {
                ChatScreen(viewModel = chatViewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        chatViewModel.onAppForegrounded()
    }

    private fun applySystemBars(isDark: Boolean) {
        val style = if (isDark) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
            SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        }
        enableEdgeToEdge(
            statusBarStyle = style,
            navigationBarStyle = style,
        )
    }
}
