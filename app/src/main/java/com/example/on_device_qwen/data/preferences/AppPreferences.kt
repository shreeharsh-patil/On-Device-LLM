package com.example.on_device_qwen.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.on_device_qwen.model.BackendType
import com.example.on_device_qwen.model.GenerationConfig
import com.example.on_device_qwen.model.PromptProfile
import com.example.on_device_qwen.model.UnrestrictedMode
import com.example.on_device_qwen.model.sanitizeSystemPrompt
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class ThemeMode {
    SYSTEM, DARK, LIGHT
}

enum class PerformanceMode(val displayName: String, val description: String) {
    ECO("Eco Mode", "Lower CPU usage, minimal thermal impact, optimal for battery"),
    BALANCED("Balanced Mode", "Dynamic resource scaling, fast and stable for daily use"),
    PERFORMANCE("Performance Mode", "Maximum sustained inference speed and hardware acceleration")
}

class AppPreferences(context: Context) : com.example.on_device_qwen.model.IMemoryPreferences, com.example.on_device_qwen.model.IChatModePreferences {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var activeModelPath: String?
        get() = prefs.getString(KEY_ACTIVE_MODEL_PATH, null)
        set(value) = prefs.edit().putString(KEY_ACTIVE_MODEL_PATH, value).apply()

    var registeredModelPaths: Set<String>
        get() = prefs.getStringSet(KEY_REGISTERED_MODEL_PATHS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_REGISTERED_MODEL_PATHS, value).apply()

    fun registerModelPath(path: String) {
        val current = registeredModelPaths.toMutableSet()
        current.add(path)
        registeredModelPaths = current
    }

    fun unregisterModelPath(path: String) {
        val current = registeredModelPaths.toMutableSet()
        current.remove(path)
        registeredModelPaths = current
    }

    override val promptProfiles: List<PromptProfile>
        get() {
            val json = promptProfilesJson ?: ""
            if (json.isBlank()) return defaultProfiles()
            return try {
                val array = JSONArray(json)
                (0 until array.length()).map { idx ->
                    val obj = array.getJSONObject(idx)
                    PromptProfile(
                        id = obj.optString(KEY_PROFILE_ID, UUID.randomUUID().toString()),
                        name = obj.optString(KEY_PROFILE_NAME, "Unnamed"),
                        instructionText = obj.optString(KEY_PROFILE_TEXT, ""),
                        effectiveSystemPrompt = obj.optString(KEY_PROFILE_EFFECTIVE, obj.optString(KEY_PROFILE_TEXT, ""))
                    )
                }
            } catch (_: Exception) {
                defaultProfiles()
            }
        }

    override var activePromptProfile: PromptProfile
        get() {
            val id = activePromptProfileId()
            val profiles = promptProfiles
            return profiles.firstOrNull { it.id == id } ?: profiles.firstOrNull() ?: PromptProfile.standardDefault()
        }
        set(value) {
            addOrUpdateProfile(value)
            setActivePromptProfile(value.id)
        }

    override fun activePromptProfileId(): String {
        val stored = activePromptProfileJson ?: return PromptProfile.DEFAULT_ID
        return try {
            JSONObject(stored).optString(KEY_PROFILE_ID, PromptProfile.DEFAULT_ID)
        } catch (_: Exception) {
            PromptProfile.DEFAULT_ID
        }
    }

    override fun savePromptProfiles(profiles: List<PromptProfile>) {
        val json = JSONArray().apply {
            profiles.forEach { profile ->
                put(JSONObject().apply {
                    put(KEY_PROFILE_ID, profile.id)
                    put(KEY_PROFILE_NAME, profile.name)
                    put(KEY_PROFILE_TEXT, profile.instructionText)
                    put(KEY_PROFILE_EFFECTIVE, profile.effectiveSystemPrompt)
                })
            }
        }.toString()
        promptProfilesJson = json
    }

    override fun setActivePromptProfile(profileId: String) {
        activePromptProfileJson = JSONObject().apply { put(KEY_PROFILE_ID, profileId) }.toString()
    }

    override fun addOrUpdateProfile(profile: PromptProfile) {
        val sanitized = profile.copy(
            name = profile.name.trim().take(PromptProfile.MAX_NAME_CHARS),
            instructionText = profile.instructionText.trim().take(PromptProfile.MAX_INSTRUCTION_CHARS),
            effectiveSystemPrompt = sanitizeSystemPrompt(
                profile.effectiveSystemPrompt.ifBlank { profile.instructionText }
            ),
        )
        val current = promptProfiles.toMutableList()
        val idx = current.indexOfFirst { it.id == sanitized.id }
        if (idx >= 0) {
            current[idx] = sanitized
        } else {
            current.add(sanitized)
        }
        savePromptProfiles(current)
    }

    fun resetProfileToDefault(profileId: String) {
        val builtIn = PromptProfile.builtInProfiles().firstOrNull { it.id == profileId } ?: return
        val current = promptProfiles.toMutableList()
        val idx = current.indexOfFirst { it.id == profileId }
        if (idx >= 0) current[idx] = builtIn else current.add(builtIn)
        savePromptProfiles(current)
    }

    override fun removeProfile(profileId: String) {
        // Built-ins can be reset, never deleted — prevents bricking the prompt selector.
        if (PromptProfile.builtInProfiles().any { it.id == profileId }) return
        if (profileId == PromptProfile.DEFAULT_ID || profileId == PromptProfile.UNRESTRICTED_ID) return
        val current = promptProfiles.toMutableList()
        if (current.size <= 1) return
        current.removeAll { it.id == profileId }
        savePromptProfiles(current)
        if (activePromptProfileId() == profileId) {
            activePromptProfileJson = JSONObject().apply { put(KEY_PROFILE_ID, current.firstOrNull()?.id ?: PromptProfile.DEFAULT_ID) }.toString()
        }
    }

    private fun defaultProfiles(): List<PromptProfile> {
        return PromptProfile.builtInProfiles()
    }

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
            .coerceIn(GenerationConfig.MIN_TEMPERATURE, GenerationConfig.MAX_TEMPERATURE)
        set(value) = prefs.edit().putFloat(
            KEY_TEMPERATURE,
            value.coerceIn(GenerationConfig.MIN_TEMPERATURE, GenerationConfig.MAX_TEMPERATURE),
        ).apply()

    var topP: Float
        get() = prefs.getFloat(KEY_TOP_P, 0.8f)
            .coerceIn(GenerationConfig.MIN_TOP_P, GenerationConfig.MAX_TOP_P)
        set(value) = prefs.edit().putFloat(
            KEY_TOP_P,
            value.coerceIn(GenerationConfig.MIN_TOP_P, GenerationConfig.MAX_TOP_P),
        ).apply()

    var maxOutputTokens: Int
        get() = prefs.getInt(KEY_MAX_TOKENS, 2048)
            .coerceIn(GenerationConfig.MIN_MAX_TOKENS, GenerationConfig.MAX_MAX_TOKENS)
        set(value) = prefs.edit().putInt(
            KEY_MAX_TOKENS,
            value.coerceIn(GenerationConfig.MIN_MAX_TOKENS, GenerationConfig.MAX_MAX_TOKENS),
        ).apply()

    var contextLength: Int
        get() = prefs.getInt(KEY_CONTEXT_LENGTH, 4096)
            .coerceIn(GenerationConfig.MIN_CONTEXT_LENGTH, GenerationConfig.MAX_CONTEXT_LENGTH)
        set(value) = prefs.edit().putInt(
            KEY_CONTEXT_LENGTH,
            value.coerceIn(GenerationConfig.MIN_CONTEXT_LENGTH, GenerationConfig.MAX_CONTEXT_LENGTH),
        ).apply()

    var systemPrompt: String
        get() = sanitizeSystemPrompt(
            prefs.getString(KEY_SYSTEM_PROMPT, GenerationConfig.DEFAULT_SYSTEM_PROMPT)
                ?: GenerationConfig.DEFAULT_SYSTEM_PROMPT
        )
        set(value) = prefs.edit().putString(KEY_SYSTEM_PROMPT, sanitizeSystemPrompt(value)).apply()

    override var unrestrictedMode: UnrestrictedMode
        get() {
            val name = prefs.getString(KEY_UNRESTRICTED_MODE, UnrestrictedMode.STANDARD.name)
            return try { UnrestrictedMode.valueOf(name!!) } catch (_: Exception) { UnrestrictedMode.STANDARD }
        }
        set(value) = prefs.edit().putString(KEY_UNRESTRICTED_MODE, value.name).apply()

    var activePromptProfileJson: String?
        get() = prefs.getString(KEY_ACTIVE_PROMPT_PROFILE, null)
        set(value) = prefs.edit().putString(KEY_ACTIVE_PROMPT_PROFILE, value).apply()

    var promptProfilesJson: String?
        get() = prefs.getString(KEY_PROMPT_PROFILES, null)
        set(value) = prefs.edit().putString(KEY_PROMPT_PROFILES, value).apply()

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
                .coerceIn(GenerationConfig.MIN_THREAD_COUNT, GenerationConfig.MAX_THREAD_COUNT)
        }
        set(value) = prefs.edit().putInt(
            KEY_THREAD_COUNT,
            value.coerceIn(GenerationConfig.MIN_THREAD_COUNT, GenerationConfig.MAX_THREAD_COUNT),
        ).apply()

    var hapticsEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTICS, value).apply()

    override var persistentMemoryEnabled: Boolean
        get() = prefs.getBoolean(KEY_PERSISTENT_MEMORY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_PERSISTENT_MEMORY_ENABLED, value).apply()

    var repetitionPenalty: Float
        get() = prefs.getFloat(KEY_REPETITION_PENALTY, 1.15f)
            .coerceIn(GenerationConfig.MIN_REPETITION_PENALTY, GenerationConfig.MAX_REPETITION_PENALTY)
        set(value) = prefs.edit().putFloat(
            KEY_REPETITION_PENALTY,
            value.coerceIn(GenerationConfig.MIN_REPETITION_PENALTY, GenerationConfig.MAX_REPETITION_PENALTY),
        ).apply()

    override var maxMemoryItems: Int
        get() = prefs.getInt(KEY_MAX_MEMORY_ITEMS, 4)
        set(value) = prefs.edit().putInt(KEY_MAX_MEMORY_ITEMS, value).apply()

    companion object {
        private const val PREFS_NAME = "on_device_qwen_prefs"
        private const val KEY_ACTIVE_MODEL_PATH = "active_model_path"
        private const val KEY_REGISTERED_MODEL_PATHS = "registered_model_paths"
        private const val KEY_PROFILE_ID = "profile_id"
        private const val KEY_PROFILE_NAME = "profile_name"
        private const val KEY_PROFILE_TEXT = "profile_text"
        private const val KEY_PROFILE_EFFECTIVE = "profile_effective"
        private const val KEY_PERFORMANCE_MODE = "performance_mode"
        private const val KEY_BACKEND_TYPE = "backend_type"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_TOP_P = "top_p"
        private const val KEY_MAX_TOKENS = "max_tokens"
        private const val KEY_CONTEXT_LENGTH = "context_length"
        private const val KEY_SYSTEM_PROMPT = "system_prompt"
        private const val KEY_UNRESTRICTED_MODE = "chat_mode"
        private const val KEY_ACTIVE_PROMPT_PROFILE = "active_prompt_profile"
        private const val KEY_PROMPT_PROFILES = "prompt_profiles"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_THREAD_COUNT = "thread_count"
        private const val KEY_HAPTICS = "haptics"
        private const val KEY_PERSISTENT_MEMORY_ENABLED = "persistent_memory_enabled"
        private const val KEY_REPETITION_PENALTY = "repetition_penalty"
        private const val KEY_MAX_MEMORY_ITEMS = "max_memory_items"
    }
}
