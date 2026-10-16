package com.example.on_device_qwen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.on_device_qwen.data.preferences.ThemeMode
import com.example.on_device_qwen.ui.ChatScreen
import com.example.on_device_qwen.ui.theme.OnDeviceQwenTheme
import com.example.on_device_qwen.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {

    private val chatViewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val themeMode by chatViewModel.themeMode.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }

            OnDeviceQwenTheme(darkTheme = isDark, dynamicColor = false) {
                ChatScreen(viewModel = chatViewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        chatViewModel.onAppForegrounded()
    }
}
