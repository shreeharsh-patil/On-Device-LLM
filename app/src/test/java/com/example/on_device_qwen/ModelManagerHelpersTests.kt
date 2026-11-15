package com.example.on_device_qwen

import com.example.on_device_qwen.domain.modelmanager.ModelCatalogItem
import com.example.on_device_qwen.model.EngineType
import com.example.on_device_qwen.model.ModelCompatibilityStatus
import com.example.on_device_qwen.model.ModelFormat
import com.example.on_device_qwen.model.ModelMetadata
import com.example.on_device_qwen.ui.components.MODEL_RAM_HEADROOM_MB
import com.example.on_device_qwen.ui.components.assessCatalogModelRam
import com.example.on_device_qwen.ui.components.assessInstalledModelSafety
import com.example.on_device_qwen.ui.components.computeRamBudgetSummary
import com.example.on_device_qwen.ui.components.isCatalogItemInstalled
import com.example.on_device_qwen.ui.designsystem.SovereignStatusTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ModelManagerHelpersTests {

    private fun sampleModel(
        fileName: String = "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",
        name: String = "Qwen 2.5 1.5B",
        format: ModelFormat = ModelFormat.GGUF,
        engineType: EngineType = EngineType.LLAMA_CPP,
        fileSizeMb: Long = 1100L,
        estimatedRamMb: Long = 1600L,
        isReadable: Boolean = true,
        isCorrupted: Boolean = false,
        compatibilityStatus: ModelCompatibilityStatus = ModelCompatibilityStatus.COMPATIBLE,
        compatibilityMessage: String? = null,
    ): ModelMetadata {
        val mb = 1024L * 1024L
        return ModelMetadata(
            file = File("/models/$fileName"),
            name = name,
            format = format,
            engineType = engineType,
            fileSizeBytes = fileSizeMb * mb,
            estimatedRamBytes = estimatedRamMb * mb,
            isReadable = isReadable,
            isCorrupted = isCorrupted,
            compatibilityStatus = compatibilityStatus,
            compatibilityMessage = compatibilityMessage,
        )
    }

    @Test
    fun installedModelSafetyDistinguishesCorruptedUnreadableRamAndCompatibleStates() {
        val corrupted = assessInstalledModelSafety(
            sampleModel(isCorrupted = true, compatibilityStatus = ModelCompatibilityStatus.CORRUPTED),
            availRamMb = 4096L,
        )
        assertEquals("Corrupted", corrupted.label)
        assertEquals(SovereignStatusTone.ERROR, corrupted.tone)
        assertFalse(corrupted.isLoadable)

        val unreadable = assessInstalledModelSafety(
            sampleModel(isReadable = false),
            availRamMb = 4096L,
        )
        assertEquals("Unreadable", unreadable.label)
        assertEquals(SovereignStatusTone.ERROR, unreadable.tone)
        assertFalse(unreadable.isLoadable)

        val unsupported = assessInstalledModelSafety(
            sampleModel(compatibilityStatus = ModelCompatibilityStatus.UNSUPPORTED_FORMAT),
            availRamMb = 4096L,
        )
        assertEquals("Unsupported", unsupported.label)
        assertEquals(SovereignStatusTone.ERROR, unsupported.tone)
        assertFalse(unsupported.isLoadable)

        val exceeds = assessInstalledModelSafety(
            sampleModel(estimatedRamMb = 3500L),
            availRamMb = 2500L,
        )
        assertEquals("Exceeds Free RAM", exceeds.label)
        assertEquals(SovereignStatusTone.ERROR, exceeds.tone)
        assertTrue(exceeds.isLoadable)

        val tight = assessInstalledModelSafety(
            sampleModel(estimatedRamMb = 2000L),
            availRamMb = 2000L + MODEL_RAM_HEADROOM_MB - 100L,
        )
        assertEquals("Tight RAM", tight.label)
        assertEquals(SovereignStatusTone.WARNING, tight.tone)
        assertTrue(tight.isLoadable)

        val fits = assessInstalledModelSafety(
            sampleModel(estimatedRamMb = 2000L),
            availRamMb = 2000L + MODEL_RAM_HEADROOM_MB,
        )
        assertEquals("Fits RAM", fits.label)
        assertEquals(SovereignStatusTone.SUCCESS, fits.tone)
        assertTrue(fits.isLoadable)

        val unknownRam = assessInstalledModelSafety(
            sampleModel(estimatedRamMb = 2000L),
            availRamMb = 0L,
        )
        assertEquals("RAM Unknown", unknownRam.label)
        assertEquals(SovereignStatusTone.NEUTRAL, unknownRam.tone)
        assertTrue(unknownRam.isLoadable)
    }

    @Test
    fun catalogRamAssessmentHandlesAvailAndTotalRamProperly() {
        val coder7B = ModelCatalogItem.VERIFIED_CATALOG.first { it.id == "qwen2.5-coder-7b-abliterated-gguf" }
        // recommended = 6400, min = 5500
        assertEquals(SovereignStatusTone.SUCCESS, assessCatalogModelRam(coder7B, availRamMb = 7000L, totalRamMb = 12288L).tone)
        assertEquals(SovereignStatusTone.WARNING, assessCatalogModelRam(coder7B, availRamMb = 5800L, totalRamMb = 12288L).tone)
        assertEquals(SovereignStatusTone.ERROR, assessCatalogModelRam(coder7B, availRamMb = 3200L, totalRamMb = 12288L).tone)
        assertEquals(SovereignStatusTone.ERROR, assessCatalogModelRam(coder7B, availRamMb = 3200L, totalRamMb = 8192L).tone)

        // Fallback to totalRamMb when availRamMb unavailable
        assertEquals(SovereignStatusTone.SUCCESS, assessCatalogModelRam(coder7B, availRamMb = 0L, totalRamMb = 12288L).tone)
        assertEquals(SovereignStatusTone.NEUTRAL, assessCatalogModelRam(coder7B, availRamMb = 0L, totalRamMb = 0L).tone)
    }

    @Test
    fun ramBudgetSummaryFormatsWithAndWithoutTotalRam() {
        val knownHealthy = computeRamBudgetSummary(availRamMb = 4200L, totalRamMb = 8192L)
        assertEquals("4200 / 8192 MB free", knownHealthy.labelText)
        assertEquals(SovereignStatusTone.SUCCESS, knownHealthy.tone)
        assertTrue(knownHealthy.freeFraction!! in 0.50f..0.52f)

        val knownWarning = computeRamBudgetSummary(availRamMb = 1500L, totalRamMb = 8192L)
        assertEquals(SovereignStatusTone.WARNING, knownWarning.tone)

        val knownCritical = computeRamBudgetSummary(availRamMb = 600L, totalRamMb = 8192L)
        assertEquals(SovereignStatusTone.ERROR, knownCritical.tone)

        val unknownTotal = computeRamBudgetSummary(availRamMb = 2400L, totalRamMb = 0L)
        assertEquals("2400 MB free", unknownTotal.labelText)
        assertEquals(SovereignStatusTone.SUCCESS, unknownTotal.tone)
        assertNull(unknownTotal.freeFraction)

        val unknownBoth = computeRamBudgetSummary(availRamMb = 0L, totalRamMb = 0L)
        assertEquals("RAM status unavailable", unknownBoth.labelText)
        assertEquals(SovereignStatusTone.NEUTRAL, unknownBoth.tone)
    }

    @Test
    fun catalogInstalledMatchingDistinguishesFormatsAndAvoidsFalsePositives() {
        val dolphinCatalog = ModelCatalogItem.VERIFIED_CATALOG.first { it.id == "dolphin-3.0-qwen2.5-3b-q4km" }
        val qwen05bCatalog = ModelCatalogItem.VERIFIED_CATALOG.first { it.id == "qwen2.5-0.5b-abliterated-gguf" }
        val qwen15bGgufCatalog = ModelCatalogItem.VERIFIED_CATALOG.first { it.id == "qwen-2.5-1.5b-instruct-gguf" }
        val qwen15bLitertCatalog = ModelCatalogItem.VERIFIED_CATALOG.first { it.id == "qwen-2.5-1.5b-litert" }

        val installed = listOf(
            sampleModel(
                fileName = "Qwen2.5-0.5B-Instruct-abliterated-SFT.i1-Q4_K_M.gguf",
                name = "Qwen2.5-0.5B-Instruct-abliterated",
                format = ModelFormat.GGUF,
            ),
            sampleModel(
                fileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
                name = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096",
                format = ModelFormat.LITERT,
                engineType = EngineType.LITERT,
            ),
            sampleModel(
                fileName = "qwen.gguf",
                name = "qwen",
                format = ModelFormat.GGUF,
            ),
        )

        assertTrue(isCatalogItemInstalled(qwen05bCatalog, installed))
        assertTrue(isCatalogItemInstalled(qwen15bLitertCatalog, installed))
        assertFalse("LiteRT model should not mark GGUF catalog card as installed", isCatalogItemInstalled(qwen15bGgufCatalog, installed))
        assertFalse("Short unrelated file qwen.gguf must not match Dolphin", isCatalogItemInstalled(dolphinCatalog, installed))
    }

    @Test
    fun allVerifiedCatalogEntriesMatchTheirOfficialDownloadFilenamesUniquely() {
        for (target in ModelCatalogItem.VERIFIED_CATALOG) {
            val fileName = target.downloadUrl.substringAfterLast('/')
            val installed = listOf(
                sampleModel(
                    fileName = fileName,
                    name = fileName.substringBeforeLast('.'),
                    format = target.format,
                    engineType = target.engineType,
                ),
            )
            assertTrue("Expected ${target.id} to match $fileName", isCatalogItemInstalled(target, installed))
            val otherMatches = ModelCatalogItem.VERIFIED_CATALOG
                .filter { it.id != target.id && isCatalogItemInstalled(it, installed) }
            assertTrue(
                "Expected $fileName not to falsely match other entries, but matched ${otherMatches.map { it.id }}",
                otherMatches.isEmpty(),
            )
        }
    }

    @Test
    fun customCompatibilityMessageIsPreserved() {
        val custom = assessInstalledModelSafety(
            sampleModel(
                isCorrupted = true,
                compatibilityStatus = ModelCompatibilityStatus.CORRUPTED,
                compatibilityMessage = "Truncated tensor table at offset 1024",
            ),
            availRamMb = 4096L,
        )
        assertFalse(custom.isLoadable)
        assertEquals("Truncated tensor table at offset 1024", custom.reason)
    }
}
