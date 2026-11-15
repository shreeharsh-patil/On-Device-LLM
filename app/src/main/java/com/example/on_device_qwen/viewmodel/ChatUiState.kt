package com.example.on_device_qwen.viewmodel

import androidx.compose.runtime.Immutable
import com.example.on_device_qwen.data.preferences.PerformanceMode
import com.example.on_device_qwen.data.preferences.ThemeMode
import com.example.on_device_qwen.domain.engine.EngineCapabilities
import com.example.on_device_qwen.domain.modelmanager.ModelCatalogItem
import com.example.on_device_qwen.domain.modelmanager.ModelImportState
import com.example.on_device_qwen.domain.performance.DeviceThermalState
import com.example.on_device_qwen.model.BackendType
import com.example.on_device_qwen.model.ChatMessage
import com.example.on_device_qwen.model.ConversationSession
import com.example.on_device_qwen.model.EngineType
import com.example.on_device_qwen.model.ExecutionBackendInfo
import com.example.on_device_qwen.model.MemoryCategory
import com.example.on_device_qwen.model.MemoryItem
import com.example.on_device_qwen.model.ModelErrorCode
import com.example.on_device_qwen.model.ModelMetadata
import com.example.on_device_qwen.model.PromptProfile
import com.example.on_device_qwen.model.SupportedParameter
import com.example.on_device_qwen.model.UnrestrictedMode
import java.io.File

sealed class EngineState {
    data object Idle : EngineState()
    data class Loading(val progress: String) : EngineState()
    data object Ready : EngineState()
    data class Error(
        val message: String,
        val title: String = "Engine Error",
        val errorCode: ModelErrorCode = ModelErrorCode.UNKNOWN,
        val canRequestPermission: Boolean = false,
        val canOpenFilePicker: Boolean = true,
        val candidateFile: File? = null,
    ) : EngineState()
}

@Immutable
data class DeviceHardwareInfo(
    val totalRamMb: Long = 0L,
    val availRamMb: Long = 0L,
    val cpuCores: Int = 0,
    val primaryAbi: String = "",
    val supportedAbis: List<String> = emptyList(),
    val availableBackends: List<BackendType> = listOf(BackendType.CPU),
    val thermalState: DeviceThermalState = DeviceThermalState.NOMINAL,
    val batteryTempCelsius: Float = 28.0f,
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
)

@Immutable
data class ChatScreenUiState(
    val engineState: EngineState = EngineState.Idle,
    val modelTitle: String = "No Model",
    val activeModelMetadata: ModelMetadata? = null,
    val executionBackendInfo: ExecutionBackendInfo? = null,
    val engineCapabilities: EngineCapabilities? = null,
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val isVisionSupported: Boolean = false,
    val visionUnsupportedReason: String? = null,
    val isSpeaking: Boolean = false,
    val chatMode: UnrestrictedMode = UnrestrictedMode.STANDARD,
    val activeProfile: PromptProfile = PromptProfile.standardDefault(),
    val lastUsedMemories: List<MemoryItem> = emptyList(),
    val validationBanner: String? = null,
    val backendFallbackBanner: String? = null,
    val transientErrorBanner: String? = null,
    val activeConversationId: String? = null,
    val activeConversationTitle: String? = null,
    val showOnboarding: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val hardwareInfo: DeviceHardwareInfo = DeviceHardwareInfo(),
)

@Immutable
data class CatalogModelRecommendation(
    val item: ModelCatalogItem,
    val fitsDeviceRam: Boolean,
    val isRecommendedFit: Boolean,
    val fitSummary: String,
)

@Immutable
data class ModelHubUiState(
    val discoveredModels: List<ModelMetadata> = emptyList(),
    val activeModel: ModelMetadata? = null,
    val availRamMb: Long = 0L,
    val totalRamMb: Long = 0L,
    val importState: ModelImportState = ModelImportState(),
    val importError: String? = null,
    val isScanning: Boolean = false,
    val currentBackend: BackendType = BackendType.CPU,
    val executionBackendInfo: ExecutionBackendInfo? = null,
    val catalogRecommendations: List<CatalogModelRecommendation> = emptyList(),
)

@Immutable
data class SettingsUiState(
    val performanceMode: PerformanceMode = PerformanceMode.BALANCED,
    val backendType: BackendType = BackendType.CPU,
    val activeEngineType: EngineType = EngineType.LLAMA_CPP,
    val executionBackendInfo: ExecutionBackendInfo? = null,
    val engineCapabilities: EngineCapabilities? = null,
    val threadCount: Int = 4,
    val temperature: Float = 0.7f,
    val topP: Float = 0.8f,
    val repetitionPenalty: Float = 1.15f,
    val maxTokens: Int = 2048,
    val contextLength: Int = 4096,
    val systemPrompt: String = "",
    val chatMode: UnrestrictedMode = UnrestrictedMode.STANDARD,
    val activeProfile: PromptProfile = PromptProfile.standardDefault(),
    val profiles: List<PromptProfile> = PromptProfile.builtInProfiles(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val hapticsEnabled: Boolean = true,
    val persistentMemoryEnabled: Boolean = true,
    val validationErrors: List<String> = emptyList(),
    val runtimeSupportNotices: List<String> = emptyList(),
    val supportedParameters: List<SupportedParameter> = emptyList(),
    val hardwareInfo: DeviceHardwareInfo = DeviceHardwareInfo(),
)

@Immutable
data class MemoryUiState(
    val memories: List<MemoryItem> = emptyList(),
    val totalMemoryCount: Int = 0,
    val searchQuery: String = "",
    val isMemoryEnabled: Boolean = true,
    val selectedCategory: MemoryCategory? = null,
)

@Immutable
data class NavigationUiState(
    val conversations: List<ConversationSession> = emptyList(),
    val activeConversationId: String? = null,
    val searchQuery: String = "",
    val batteryPercent: Int = 100,
    val batteryTemp: Float = 28.0f,
    val availableRamMb: Long = 0L,
)

/**
 * Coordinates automatic follow-scroll during token streaming while preserving
 * the user's reading position when they deliberately scroll upward, and keeps
 * header index math exact when banner items precede the message list.
 */
class ScrollFollowController {
    var userScrolledAway: Boolean = false
        private set

    var autoScrollTriggerCount: Int = 0
        private set

    fun onScrollStateChanged(isUserDragging: Boolean, canScrollForward: Boolean) {
        if (isUserDragging && canScrollForward) {
            userScrolledAway = true
        } else if (!canScrollForward) {
            userScrolledAway = false
        }
    }

    fun onNewMessageAdded(): Boolean {
        userScrolledAway = false
        autoScrollTriggerCount++
        return true
    }

    fun onTokenStreamChunkArrived(): Boolean {
        if (userScrolledAway) return false
        autoScrollTriggerCount++
        return true
    }

    fun onJumpToBottomClicked() {
        userScrolledAway = false
        autoScrollTriggerCount++
    }

    fun resetForConversationSwitch() {
        userScrolledAway = false
    }

    companion object {
        fun messageListIndex(messageIndex: Int, leadingHeaderCount: Int): Int =
            (leadingHeaderCount.coerceAtLeast(0)) + messageIndex.coerceAtLeast(0)

        fun bottomAnchorIndex(messageCount: Int, leadingHeaderCount: Int): Int =
            leadingHeaderCount.coerceAtLeast(0) + messageCount.coerceAtLeast(0)
    }
}
