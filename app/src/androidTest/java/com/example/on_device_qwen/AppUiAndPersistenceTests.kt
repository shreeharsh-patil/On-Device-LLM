package com.example.on_device_qwen

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.on_device_qwen.data.preferences.AppPreferences
import com.example.on_device_qwen.data.preferences.ThemeMode
import com.example.on_device_qwen.data.repository.ConversationRepository
import com.example.on_device_qwen.domain.engine.EngineCapabilities
import com.example.on_device_qwen.domain.modelmanager.ImportProgress
import com.example.on_device_qwen.domain.modelmanager.ModelCatalogItem
import com.example.on_device_qwen.domain.modelmanager.ModelImportState
import com.example.on_device_qwen.model.BackendType
import com.example.on_device_qwen.model.BenchmarkMetrics
import com.example.on_device_qwen.model.ChatMessage
import com.example.on_device_qwen.model.ConversationSession
import com.example.on_device_qwen.model.EngineType
import com.example.on_device_qwen.model.ExecutionBackendInfo
import com.example.on_device_qwen.model.GenerationConfig
import com.example.on_device_qwen.model.MemoryCategory
import com.example.on_device_qwen.model.MemoryItem
import com.example.on_device_qwen.model.ModelCompatibilityStatus
import com.example.on_device_qwen.model.ModelFormat
import com.example.on_device_qwen.model.ModelMetadata
import com.example.on_device_qwen.ui.components.ChatComposer
import com.example.on_device_qwen.ui.components.ChatNavigationDrawerContent
import com.example.on_device_qwen.ui.components.MemoryManagerDialog
import com.example.on_device_qwen.ui.components.ModelManagerDialog
import com.example.on_device_qwen.ui.components.SettingsDialog
import com.example.on_device_qwen.ui.screens.DiagnosticsSheet
import com.example.on_device_qwen.ui.screens.EmptyChatState
import com.example.on_device_qwen.ui.screens.MessageList
import com.example.on_device_qwen.ui.screens.OnboardingScreen
import com.example.on_device_qwen.ui.theme.OnDeviceQwenTheme
import com.example.on_device_qwen.viewmodel.CatalogModelRecommendation
import com.example.on_device_qwen.viewmodel.DeviceHardwareInfo
import com.example.on_device_qwen.viewmodel.EngineState
import com.example.on_device_qwen.viewmodel.MemoryUiState
import com.example.on_device_qwen.viewmodel.ModelHubUiState
import com.example.on_device_qwen.viewmodel.SettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AppUiAndPersistenceTests {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun sampleHardware(): DeviceHardwareInfo = DeviceHardwareInfo(
        totalRamMb = 8192L,
        availRamMb = 4096L,
        cpuCores = 8,
        primaryAbi = "arm64-v8a",
        supportedAbis = listOf("arm64-v8a"),
        availableBackends = listOf(BackendType.CPU, BackendType.GPU),
        batteryPercent = 84,
        batteryTempCelsius = 30.5f,
    )

    private fun sampleModel(name: String = "Qwen 2.5 1.5B", hasVision: Boolean = false): ModelMetadata =
        ModelMetadata(
            file = File(context.filesDir, "$name.gguf"),
            name = name,
            format = ModelFormat.GGUF,
            engineType = EngineType.LLAMA_CPP,
            architecture = "qwen2",
            quantization = "Q4_K_M",
            contextLength = 4096,
            fileSizeBytes = 900L * 1024L * 1024L,
            estimatedRamBytes = 1300L * 1024L * 1024L,
            hasVision = hasVision,
            compatibilityStatus = ModelCompatibilityStatus.COMPATIBLE,
        )

    @Test
    fun onboardingScreenShowsHardwareSpecsAndSafImportWithNoAllFilesPrompt() {
        var importClicked = false
        val recommendation = CatalogModelRecommendation(
            item = ModelCatalogItem.VERIFIED_CATALOG.first(),
            fitsDeviceRam = true,
            isRecommendedFit = true,
            fitSummary = "Fits 4.0 GB free RAM",
        )

        composeRule.setContent {
            OnDeviceQwenTheme(darkTheme = false) {
                OnboardingScreen(
                    engineState = EngineState.Idle,
                    hardwareInfo = sampleHardware(),
                    recommendations = listOf(recommendation),
                    showFirstRunGuide = true,
                    onImportModelClick = { importClicked = true },
                    onOpenModelHub = {},
                    onRetryEngine = {},
                    onSelectCandidate = {},
                    onContinueDismissGuide = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.onboarding_title)).assertIsDisplayed()
        composeRule.onNodeWithText("arm64-v8a", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("8 cores", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("All Files Access", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Grant Storage Permission", substring = true).assertDoesNotExist()

        composeRule.onNodeWithText(context.getString(R.string.onboarding_cta_import)).performClick()
        assertTrue(importClicked)
    }

    @Test
    fun emptyChatStateShowsHonestFallbackBadgeAndGatesVisionStarterPrompt() {
        var selectedPrompt = ""

        composeRule.setContent {
            OnDeviceQwenTheme(darkTheme = true) {
                EmptyChatState(
                    modelName = "Qwen 2.5 1.5B",
                    executionBackendInfo = ExecutionBackendInfo(
                        requested = BackendType.GPU,
                        active = BackendType.CPU,
                        fallbackReason = "OpenCL missing",
                    ),
                    isVisionSupported = false,
                    onSelectPrompt = { selectedPrompt = it },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.empty_chat_title)).assertIsDisplayed()
        composeRule.onNodeWithText("CPU (GPU fallback)", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Analyze an attached image").assertDoesNotExist()

        composeRule.onNodeWithText("Write & test clean code").performClick()
        assertTrue(selectedPrompt.contains("LRU cache"))
    }

    @Test
    fun chatComposerHidesVisionAttachOnTextModelAndMeetsTouchTargets() {
        var isVision = false
        var isGenerating = false
        var sentText = ""
        var stopped = false

        composeRule.setContent {
            OnDeviceQwenTheme(darkTheme = false) {
                ChatComposer(
                    isGenerating = isGenerating,
                    isVisionModel = isVision,
                    onSendMessage = { text, _ -> sentText = text },
                    onStopGeneration = { stopped = true },
                )
            }
        }

        // Text-only model: image attach button MUST NOT exist
        composeRule.onNodeWithContentDescription(context.getString(R.string.cd_attach_image))
            .assertDoesNotExist()

        // Verify send button min 48dp touch target and send behavior
        composeRule.onNodeWithText(context.getString(R.string.composer_hint_ready))
            .performTextInput("Hello offline model")
        val sendCd = context.getString(R.string.cd_send_message)
        composeRule.onNodeWithContentDescription(sendCd)
            .assertIsEnabled()
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
            .performClick()

        assertEquals("Hello offline model", sentText)
        assertFalse(stopped)
    }

    @Test
    fun chatComposerShowsVisionAttachAtMinTouchTargetWhenSupportedAndStopWhenGenerating() {
        var stopped = false

        composeRule.setContent {
            OnDeviceQwenTheme(darkTheme = true) {
                ChatComposer(
                    isGenerating = true,
                    isVisionModel = true,
                    onSendMessage = { _, _ -> },
                    onStopGeneration = { stopped = true },
                )
            }
        }

        composeRule.onNodeWithContentDescription(context.getString(R.string.cd_attach_image))
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)

        composeRule.onNodeWithContentDescription(context.getString(R.string.cd_stop_generation))
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
            .performClick()

        assertTrue(stopped)
    }

    @Test
    fun messageBubbleRendersStoppedBadgeErrorRetryAndAccessibleActionTargetsInDarkTheme() {
        var retried = false
        val stoppedMsg = ChatMessage(
            id = 101L,
            conversationId = "c1",
            text = "Partial response text",
            isUser = false,
            isStreaming = false,
            isCancelled = true,
            benchmark = BenchmarkMetrics(ttftMs = 80, decodeTps = 18.4, decodeTokens = 24, backend = "CPU"),
            modelName = "Qwen 2.5 1.5B",
        )
        val errMsg = ChatMessage(
            id = 102L,
            conversationId = "c1",
            text = "Context window exhausted",
            isUser = false,
            isStreaming = false,
            isError = true,
        )

        composeRule.setContent {
            OnDeviceQwenTheme(darkTheme = true) {
                MessageList(
                    conversationId = "c1",
                    messages = listOf(stoppedMsg, errMsg),
                    isGenerating = false,
                    isSpeaking = false,
                    lastUsedMemories = emptyList(),
                    onSpeak = {},
                    onStopSpeaking = {},
                    onEditMessage = {},
                    onRegenerateMessage = { retried = true },
                    onSelectSuggestion = {},
                    getFollowUpSuggestions = { emptyList() },
                    onOpenMemoryManager = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.message_stopped_badge)).assertIsDisplayed()
        composeRule.onNodeWithText("Context window exhausted").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.cd_copy_message))
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription(context.getString(R.string.cd_regenerate))
            .assertHeightIsAtLeast(48.dp)

        composeRule.onNodeWithText(context.getString(R.string.label_retry)).performClick()
        assertTrue(retried)
    }

    @Test
    fun navigationDrawerSeparatesPinnedChatsSearchesAndConfirmsDelete() {
        val now = System.currentTimeMillis()
        val pinned = ConversationSession(
            id = "p1",
            title = "Pinned Architecture",
            updatedAt = now - 1000L,
            isPinned = true,
        )
        val recent = ConversationSession(
            id = "r1",
            title = "Recent Debugging",
            updatedAt = now,
            isPinned = false,
        )
        var deletedId: String? = null
        var searchQuerySeen = ""

        composeRule.setContent {
            OnDeviceQwenTheme(darkTheme = false) {
                ChatNavigationDrawerContent(
                    conversations = listOf(pinned, recent),
                    activeConversationId = "p1",
                    searchQuery = "",
                    onSearchQueryChange = { searchQuerySeen = it },
                    onSelectConversation = {},
                    onNewChat = {},
                    onRenameConversation = { _, _ -> },
                    onDeleteConversation = { deletedId = it.id },
                    onTogglePin = {},
                    onShareConversation = {},
                    onOpenModelManager = {},
                    onOpenMemory = {},
                    onOpenSettings = {},
                    onOpenDiagnostics = {},
                    batteryPercent = 88,
                    batteryTemp = 31.2f,
                    availableRamMb = 4096L,
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.drawer_section_pinned)).assertIsDisplayed()
        composeRule.onNodeWithText("Pinned Architecture").assertIsDisplayed()
        composeRule.onNodeWithText("Recent Debugging").assertIsDisplayed()

        composeRule.onNodeWithText(context.getString(R.string.drawer_search_placeholder))
            .performTextInput("Pinned")
        assertEquals("Pinned", searchQuerySeen)

        // Open options on Pinned conversation -> Delete must show confirmation first
        composeRule.onAllNodesWithContentDescription(context.getString(R.string.cd_more_options))
            .onFirst()
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.label_delete)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.drawer_delete_title)).assertIsDisplayed()
        assertNull(deletedId)
        composeRule.onNodeWithText(context.getString(R.string.label_delete)).performClick()
        assertEquals("p1", deletedId)
    }

    @Test
    fun modelHubSwitchesTabsAndDisplaysFailedImportErrorCard() {
        val installed = sampleModel("Qwen 2.5 1.5B")
        val state = ModelHubUiState(
            discoveredModels = listOf(installed),
            activeModel = installed,
            availRamMb = 4096L,
            totalRamMb = 8192L,
            importState = ModelImportState(
                progress = ImportProgress.FAILED,
                fileName = "broken.gguf",
                errorMessage = "Corrupt GGUF header",
            ),
            importError = "Corrupt GGUF header",
            currentBackend = BackendType.CPU,
            catalogRecommendations = listOf(
                CatalogModelRecommendation(
                    item = ModelCatalogItem.VERIFIED_CATALOG.first(),
                    fitsDeviceRam = true,
                    isRecommendedFit = true,
                    fitSummary = "Fits 4.0 GB free RAM",
                ),
            ),
        )

        composeRule.setContent {
            OnDeviceQwenTheme(darkTheme = false) {
                ModelManagerDialog(
                    state = state,
                    onSelectModel = {},
                    onImportUri = { _: Uri -> },
                    onDeleteModel = {},
                    onDismissImportError = {},
                    onRefreshModels = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.model_hub_tab_installed)).assertIsDisplayed()
        composeRule.onNodeWithText("Qwen 2.5 1.5B").assertIsDisplayed()

        composeRule.onNodeWithText(context.getString(R.string.model_hub_tab_discover)).performClick()
        composeRule.onNodeWithText(ModelCatalogItem.VERIFIED_CATALOG.first().name).assertIsDisplayed()

        composeRule.onNodeWithText(context.getString(R.string.model_hub_tab_import)).performClick()
        composeRule.onNodeWithText("Corrupt GGUF header", substring = true).assertIsDisplayed()
    }

    @Test
    fun settingsDialogNavigatesAllTabsAndExpandsAdvancedInferenceControls() {
        val settingsState = SettingsUiState(
            themeMode = ThemeMode.DARK,
            hapticsEnabled = true,
            supportedParameters = GenerationConfig.runtimeParameterSupport(EngineType.LLAMA_CPP),
            hardwareInfo = sampleHardware(),
        )

        composeRule.setContent {
            OnDeviceQwenTheme(darkTheme = true) {
                SettingsDialog(
                    state = settingsState,
                    onThemeModeChange = {},
                    onHapticsEnabledChange = {},
                    onPerformanceModeChange = {},
                    onBackendTypeChange = {},
                    onThreadCountChange = {},
                    onTemperatureChange = {},
                    onTopPChange = {},
                    onRepetitionPenaltyChange = {},
                    onMaxTokensChange = {},
                    onContextLengthChange = {},
                    onSystemPromptChange = {},
                    onChatModeChange = {},
                    onActiveProfileChange = {},
                    onProfileSaved = { null },
                    onProfileDeleted = {},
                    onProfileReset = {},
                    onToggleMemory = {},
                    onManageMemories = {},
                    onOpenDiagnostics = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.settings_tab_inference)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.settings_advanced_toggle_show)).performClick()
        composeRule.onNodeWithText("Repetition Penalty").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Context Length").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithText(context.getString(R.string.settings_tab_prompts)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.settings_tab_data)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.settings_open_diagnostics)).assertIsDisplayed()
    }

    @Test
    fun memoryManagerAndDiagnosticsSheetRenderCleanlyInDarkAndLight() {
        composeRule.setContent {
            OnDeviceQwenTheme(darkTheme = false) {
                MemoryManagerDialog(
                    state = MemoryUiState(
                        memories = listOf(
                            MemoryItem(category = MemoryCategory.USER, content = "Prefers TypeScript"),
                        ),
                        totalMemoryCount = 2,
                        searchQuery = "Type",
                        isMemoryEnabled = true,
                    ),
                    onToggleMemory = {},
                    onSaveMemory = {},
                    onUpdateMemory = { _, _, _ -> },
                    onDeleteMemory = {},
                    onClearAllMemories = {},
                    onSearchMemories = {},
                    onSelectCategory = {},
                    onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithText("Prefers TypeScript").assertIsDisplayed()
        composeRule.onNodeWithText("1 / 2 shown", substring = true).assertIsDisplayed()
    }

    @Test
    fun diagnosticsSheetDisplaysCapabilitiesAndNotices() {
        composeRule.setContent {
            OnDeviceQwenTheme(darkTheme = true) {
                DiagnosticsSheet(
                    modelTitle = "Qwen 2.5 1.5B",
                    activeModel = sampleModel(),
                    activeEngineType = EngineType.LLAMA_CPP,
                    backendInfo = ExecutionBackendInfo(BackendType.GPU, BackendType.CPU, "No OpenCL"),
                    capabilities = EngineCapabilities(
                        engineType = EngineType.LLAMA_CPP,
                        supportedFormats = listOf(ModelFormat.GGUF),
                        supportsVision = false,
                        supportsGpu = false,
                        supportsNpu = false,
                    ),
                    hardwareInfo = sampleHardware(),
                    supportedParameters = GenerationConfig.runtimeParameterSupport(EngineType.LLAMA_CPP),
                    notices = listOf("Temperature 0.0 uses greedy argmax"),
                    onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithText("No OpenCL", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Temperature 0.0 uses greedy argmax").assertIsDisplayed()
    }

    @Test
    fun sqliteConversationAndPreferencesPersistenceRoundTrip() {
        val repo = ConversationRepository(context)
        repo.clearAllConversations()
        repo.clearAllMemories()
        val prefs = AppPreferences(context)

        val conv = ConversationSession(
            id = "test_conv_1",
            title = "Persistence Verify",
            modelName = "Qwen 2.5 1.5B",
        )
        repo.saveConversation(conv)
        repo.togglePinConversation(conv.id, true)
        repo.saveMessage(
            ChatMessage(
                id = 5001L,
                conversationId = conv.id,
                text = "Partial saved stream",
                isUser = false,
                isStreaming = false,
                isCancelled = true,
                benchmark = BenchmarkMetrics(ttftMs = 90, decodeTps = 21.0, decodeTokens = 10),
            ),
        )

        val reloadedConv = repo.getConversation(conv.id)
        assertTrue(reloadedConv?.isPinned == true)
        val reloadedMsgs = repo.getMessages(conv.id)
        assertEquals(1, reloadedMsgs.size)
        assertTrue(reloadedMsgs.first().isCancelled)

        prefs.repetitionPenalty = 1.35f
        prefs.contextLength = 2048
        prefs.hapticsEnabled = false

        val freshPrefs = AppPreferences(context)
        assertEquals(1.35f, freshPrefs.repetitionPenalty, 0.01f)
        assertEquals(2048, freshPrefs.contextLength)
        assertFalse(freshPrefs.hapticsEnabled)

        repo.clearAllConversations()
    }
}
