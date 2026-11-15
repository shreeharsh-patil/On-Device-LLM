package com.example.on_device_qwen

import com.example.on_device_qwen.domain.performance.DeviceThermalState
import com.example.on_device_qwen.model.GenerationConfig
import com.example.on_device_qwen.model.MemoryCategory
import com.example.on_device_qwen.model.MemoryItem
import com.example.on_device_qwen.model.PromptProfile
import com.example.on_device_qwen.ui.components.computeAvailableRamFraction
import com.example.on_device_qwen.ui.components.countMemoriesByCategory
import com.example.on_device_qwen.ui.components.filterMemories
import com.example.on_device_qwen.ui.components.isBuiltInProfile
import com.example.on_device_qwen.ui.components.mapMemoryCategoryTone
import com.example.on_device_qwen.ui.components.mapThermalSeverityTone
import com.example.on_device_qwen.ui.components.resolveProfileToSave
import com.example.on_device_qwen.ui.components.resolveThreadSliderBounds
import com.example.on_device_qwen.ui.designsystem.SovereignStatusTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsAndMemoryUiTests {

    @Test
    fun threadSliderBoundsAlignWithGenerationConfigAndDeviceCores() {
        val singleCore = resolveThreadSliderBounds(availableProcessors = 1)
        assertEquals(GenerationConfig.MIN_THREAD_COUNT, singleCore.minThreads)
        assertEquals(1, singleCore.maxThreads)
        assertEquals(0, singleCore.steps)
        assertFalse(singleCore.isAdjustable)

        val dualCore = resolveThreadSliderBounds(availableProcessors = 2)
        assertEquals(1, dualCore.minThreads)
        assertEquals(2, dualCore.maxThreads)
        assertEquals(0, dualCore.steps)
        assertTrue(dualCore.isAdjustable)

        val octaCore = resolveThreadSliderBounds(availableProcessors = 8)
        assertEquals(1, octaCore.minThreads)
        assertEquals(GenerationConfig.MAX_THREAD_COUNT, octaCore.maxThreads)
        assertEquals(6, octaCore.steps)
        assertTrue(octaCore.isAdjustable)

        val sixteenCore = resolveThreadSliderBounds(availableProcessors = 16)
        assertEquals(GenerationConfig.MAX_THREAD_COUNT, sixteenCore.maxThreads)
        assertEquals(6, sixteenCore.steps)
        assertTrue(sixteenCore.isAdjustable)
    }

    @Test
    fun resolveProfileToSavePreservesBuiltInIdsAndProtectsThemOnRename() {
        val builtIns = PromptProfile.builtInProfiles()
        val standard = builtIns.first { it.id == PromptProfile.DEFAULT_ID }
        val custom = PromptProfile(
            id = "custom-1",
            name = "Coder",
            instructionText = "Answer with concise Kotlin code.",
        )
        val profiles = builtIns + custom

        assertTrue(isBuiltInProfile(PromptProfile.DEFAULT_ID))
        assertTrue(isBuiltInProfile(PromptProfile.UNRESTRICTED_ID))
        assertFalse(isBuiltInProfile("custom-1"))

        // Updating active built-in under its own name keeps built-in ID so reset remains possible
        val updatedStandard = resolveProfileToSave(
            activeProfile = standard,
            profiles = profiles,
            editorName = "  Standard  ",
            editorText = "Updated baseline instruction",
            isCreatingNew = false,
            newProfileId = "draft-id",
        )
        assertEquals(PromptProfile.DEFAULT_ID, updatedStandard.id)
        assertEquals("Standard", updatedStandard.name)
        assertNull(updatedStandard.validate())

        // Saving built-in under a brand new name spawns a custom profile ID instead of overwriting default
        val branchedFromBuiltIn = resolveProfileToSave(
            activeProfile = standard,
            profiles = profiles,
            editorName = "Security Reviewer",
            editorText = "Audit code carefully.",
            isCreatingNew = false,
            newProfileId = "draft-id",
        )
        assertEquals("draft-id", branchedFromBuiltIn.id)
        assertEquals("Security Reviewer", branchedFromBuiltIn.name)
        assertNull(branchedFromBuiltIn.validate())

        // Creating new mode always allocates the new ID even if text matches
        val createdNew = resolveProfileToSave(
            activeProfile = custom,
            profiles = profiles,
            editorName = "Architect",
            editorText = "Design clean systems.",
            isCreatingNew = true,
            newProfileId = "new-uuid",
        )
        assertEquals("new-uuid", createdNew.id)
        assertEquals("Architect", createdNew.name)
        assertNull(createdNew.validate())

        // Renaming an existing custom profile keeps its own ID
        val renamedCustom = resolveProfileToSave(
            activeProfile = custom,
            profiles = profiles,
            editorName = "Kotlin Architect",
            editorText = "Use coroutines.",
            isCreatingNew = false,
            newProfileId = "draft-id",
        )
        assertEquals("custom-1", renamedCustom.id)
        assertEquals("Kotlin Architect", renamedCustom.name)

        // Validation rejects blank or oversized instruction text
        val tooLong = resolveProfileToSave(
            activeProfile = custom,
            profiles = profiles,
            editorName = "Valid",
            editorText = "x".repeat(PromptProfile.MAX_INSTRUCTION_CHARS + 1),
            isCreatingNew = false,
        )
        assertNotNull(tooLong.validate())
    }

    @Test
    fun thermalToneAndRamFractionMapSeverityAndEdgeCases() {
        assertEquals(SovereignStatusTone.SUCCESS, mapThermalSeverityTone(DeviceThermalState.NOMINAL))
        assertEquals(SovereignStatusTone.ACCENT, mapThermalSeverityTone(DeviceThermalState.LIGHT))
        assertEquals(SovereignStatusTone.WARNING, mapThermalSeverityTone(DeviceThermalState.MODERATE))
        assertEquals(SovereignStatusTone.ERROR, mapThermalSeverityTone(DeviceThermalState.SEVERE))
        assertEquals(SovereignStatusTone.ERROR, mapThermalSeverityTone(DeviceThermalState.CRITICAL))

        assertEquals(0f, computeAvailableRamFraction(availRamMb = 1024L, totalRamMb = 0L), 0.0001f)
        assertEquals(0.5f, computeAvailableRamFraction(availRamMb = 4096L, totalRamMb = 8192L), 0.0001f)
        assertEquals(1f, computeAvailableRamFraction(availRamMb = 9000L, totalRamMb = 8192L), 0.0001f)
        assertEquals(0f, computeAvailableRamFraction(availRamMb = -100L, totalRamMb = 8192L), 0.0001f)
    }

    @Test
    fun memoryCategoryCountingAndFilteringHandleAllAndQueryMatches() {
        val items = listOf(
            MemoryItem(
                id = "1",
                category = MemoryCategory.USER,
                content = "Prefers Jetpack Compose with Kotlin",
                keywords = "ui,android",
            ),
            MemoryItem(
                id = "2",
                category = MemoryCategory.PROJECT,
                content = "On-Device Qwen offline inference app",
                keywords = "gguf,litert",
            ),
            MemoryItem(
                id = "3",
                category = MemoryCategory.USER,
                content = "Uses dark Obsidian theme at night",
                keywords = "theme",
            ),
        )

        val counts = countMemoriesByCategory(items)
        assertEquals(2, counts[MemoryCategory.USER])
        assertEquals(1, counts[MemoryCategory.PROJECT])
        assertEquals(0, counts[MemoryCategory.EPISODIC])

        assertEquals(3, filterMemories(items, selectedCategory = null, query = "   ").size)
        assertEquals(2, filterMemories(items, selectedCategory = MemoryCategory.USER, query = "").size)
        assertEquals(0, filterMemories(items, selectedCategory = MemoryCategory.EPISODIC, query = "").size)

        // Keyword search match
        val byKeyword = filterMemories(items, selectedCategory = null, query = "GGUF")
        assertEquals(listOf("2"), byKeyword.map { it.id })

        // Combined category + content filter
        val byBoth = filterMemories(items, selectedCategory = MemoryCategory.USER, query = "obsidian")
        assertEquals(listOf("3"), byBoth.map { it.id })

        assertEquals(SovereignStatusTone.ACCENT, mapMemoryCategoryTone(MemoryCategory.USER))
        assertEquals(SovereignStatusTone.VIOLET, mapMemoryCategoryTone(MemoryCategory.PROJECT))
        assertEquals(SovereignStatusTone.SUCCESS, mapMemoryCategoryTone(MemoryCategory.EPISODIC))
    }
}
