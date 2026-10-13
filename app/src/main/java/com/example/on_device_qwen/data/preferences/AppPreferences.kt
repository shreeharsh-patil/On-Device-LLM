package com.example.on_device_qwen.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.on_device_qwen.model.BackendType
import com.example.on_device_qwen.model.GenerationConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    SYSTEM, DARK, LIGHT
}

enum class PerformanceMode(val displayName: String, val description: String) {
    ECO("Eco Mode", "Lower CPU usage, minimal thermal impact, optimal for battery"),
    BALANCED("Balanced Mode", "Dynamic resource scaling, fast and stable for daily use"),
    PERFORMANCE("Performance Mode", "Maximum sustained inference speed and hardware acceleration")
}

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var activeModelPath: String?
        get() = prefs.getString(KEY_ACTIVE_MODEL_PATH, null)
        set(value) = prefs.edit().putString(KEY_ACTIVE_MODEL_PATH, value).apply()

    var performanceMode: PerformanceMode
        get() {
            val name = prefs.getString(KEY_PERFORMANCE_MODE, PerformanceMode.BALANCED.name)
            return try { PerformanceMode.valueOf(name!!) } catch (_: Exception) { PerformanceMode.BALANCED }
        }
        set(value) = prefs.edit().putString(KEY_PERFORMANCE_MODE, value.name).apply()

    var backendType: BackendType
        get() {
            val name = prefs.getString(KEY_BACKEND_TYPE, BackendType.CPU.name)
            return try { BackendType.valueOf(name!!) } catch (_: Exception) { BackendType.CPU }
        }
        set(value) = prefs.edit().putString(KEY_BACKEND_TYPE, value.name).apply()

    var temperature: Float
        get() = prefs.getFloat(KEY_TEMPERATURE, 0.7f)
        set(value) = prefs.edit().putFloat(KEY_TEMPERATURE, value).apply()

    var topP: Float
        get() = prefs.getFloat(KEY_TOP_P, 0.8f)
        set(value) = prefs.edit().putFloat(KEY_TOP_P, value).apply()

    var maxOutputTokens: Int
        get() = prefs.getInt(KEY_MAX_TOKENS, 2048)
        set(value) = prefs.edit().putInt(KEY_MAX_TOKENS, value).apply()

    var contextLength: Int
        get() = prefs.getInt(KEY_CONTEXT_LENGTH, 4096)
        set(value) = prefs.edit().putInt(KEY_CONTEXT_LENGTH, value).apply()

    var systemPrompt: String
        get() = prefs.getString(KEY_SYSTEM_PROMPT, GenerationConfig.DEFAULT_SYSTEM_PROMPT) ?: GenerationConfig.DEFAULT_SYSTEM_PROMPT
        set(value) = prefs.edit().putString(KEY_SYSTEM_PROMPT, value).apply()

    var themeMode: ThemeMode
        get() {
            val name = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
            return try { ThemeMode.valueOf(name!!) } catch (_: Exception) { ThemeMode.SYSTEM }
        }
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value.name).apply()

    var threadCount: Int
        get() {
            val defaultThreads = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 6)
            return prefs.getInt(KEY_THREAD_COUNT, defaultThreads)
        }
        set(value) = prefs.edit().putInt(KEY_THREAD_COUNT, value).apply()

    var hapticsEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTICS, value).apply()

    companion object {
        private const val PREFS_NAME = "on_device_qwen_prefs"
        private const val KEY_ACTIVE_MODEL_PATH = "active_model_path"
        private const val KEY_PERFORMANCE_MODE = "performance_mode"
        private const val KEY_BACKEND_TYPE = "backend_type"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_TOP_P = "top_p"
        private const val KEY_MAX_TOKENS = "max_tokens"
        private const val KEY_CONTEXT_LENGTH = "context_length"
        private const val KEY_SYSTEM_PROMPT = "system_prompt"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_THREAD_COUNT = "thread_count"
        private const val KEY_HAPTICS = "haptics"
    }
}
