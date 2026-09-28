package com.example.on_device_qwen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.on_device_qwen.ui.ChatScreen
import com.example.on_device_qwen.ui.theme.OnDeviceQwenTheme
import com.example.on_device_qwen.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {

    private val chatViewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            OnDeviceQwenTheme(dynamicColor = false) {
                ChatScreen(viewModel = chatViewModel)
            }
        }
    }
}
