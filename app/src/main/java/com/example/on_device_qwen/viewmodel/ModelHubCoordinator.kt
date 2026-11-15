package com.example.on_device_qwen.viewmodel

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.example.on_device_qwen.domain.modelmanager.ImportProgress
import com.example.on_device_qwen.domain.modelmanager.ModelCatalogItem
import com.example.on_device_qwen.domain.modelmanager.ModelImportState
import com.example.on_device_qwen.domain.modelmanager.ModelRepository
import com.example.on_device_qwen.domain.performance.DeviceMemoryStatus
import com.example.on_device_qwen.domain.performance.DeviceThermalState
import com.example.on_device_qwen.domain.performance.MemoryMonitor
import com.example.on_device_qwen.model.BackendType
import com.example.on_device_qwen.model.ModelCompatibilityStatus
import com.example.on_device_qwen.model.ModelDiagnostic
import com.example.on_device_qwen.model.ModelFormat
import com.example.on_device_qwen.model.ModelMetadata
import java.io.File

/**
 * Coordinates hardware detection, local model scanning, compatibility assessment,
 * catalog recommendations, SAF imports, and safe deletions for the Model Hub & Onboarding.
 */
class ModelHubCoordinator(
    private val context: Context,
    private val scanModelsFn: suspend () -> List<ModelMetadata>,
    private val diagnoseStatusFn: suspend () -> ModelDiagnostic,
    private val parseMetadataFn: (File) -> ModelMetadata?,
    private val isGgufBinaryFn: (File) -> Boolean,
    private val importFromUriFn: suspend (Uri, (Float) -> Unit) -> Result<File>,
    private val deleteModelFn: suspend (File) -> Boolean,
    private val memoryStatusFn: () -> DeviceMemoryStatus,
    private val supportedAbisProvider: () -> List<String> = { Build.SUPPORTED_ABIS?.toList().orEmpty() },
    private val cpuCoresProvider: () -> Int = { Runtime.getRuntime().availableProcessors() },
) {
    constructor(
        context: Context,
        modelRepository: ModelRepository,
        memoryMonitor: MemoryMonitor,
    ) : this(
        context = context.applicationContext,
        scanModelsFn = { modelRepository.scanModels() },
        diagnoseStatusFn = { modelRepository.diagnoseModelStatus() },
        parseMetadataFn = { file -> modelRepository.parseMetadata(file) },
        isGgufBinaryFn = { file -> modelRepository.isGgufBinary(file) },
        importFromUriFn = { uri, onProgress -> modelRepository.importModelFromUri(uri, onProgress) },
        deleteModelFn = { file -> modelRepository.deleteModel(file) },
        memoryStatusFn = { memoryMonitor.getMemoryStatus() },
    )

    fun readHardwareInfo(
        thermalState: DeviceThermalState = DeviceThermalState.NOMINAL,
        batteryTempCelsius: Float = 28.0f,
        batteryPercent: Int = 100,
        isCharging: Boolean = false,
        gpuSupported: Boolean = false,
        npuSupported: Boolean = false,
    ): DeviceHardwareInfo {
        val mem = memoryStatusFn()
        val abis = supportedAbisProvider().ifEmpty { listOf("arm64-v8a") }
        val backends = buildList {
            add(BackendType.CPU)
            if (gpuSupported) add(BackendType.GPU)
            if (npuSupported) add(BackendType.NPU)
        }
        return DeviceHardwareInfo(
            totalRamMb = mem.totalRamMb,
            availRamMb = mem.availRamMb,
            cpuCores = cpuCoresProvider().coerceAtLeast(1),
            primaryAbi = abis.firstOrNull().orEmpty(),
            supportedAbis = abis,
            availableBackends = backends,
            thermalState = thermalState,
            batteryTempCelsius = batteryTempCelsius,
            batteryPercent = batteryPercent,
            isCharging = isCharging,
        )
    }

    suspend fun scanAndAssessModels(availRamMb: Long, totalRamMb: Long): List<ModelMetadata> {
        return scanModelsFn().map { metadata ->
            assessCompatibility(metadata, availRamMb, totalRamMb)
        }
    }

    suspend fun diagnoseMissingModels(): ModelDiagnostic = diagnoseStatusFn()

    fun parseOrFallbackMetadata(file: File): ModelMetadata {
        val parsed = parseMetadataFn(file)
        if (parsed != null) return parsed
        val isGguf = file.extension.equals("gguf", ignoreCase = true) || isGgufBinaryFn(file)
        return ModelMetadata(
            file = file,
            name = file.nameWithoutExtension,
            format = if (isGguf) ModelFormat.GGUF else ModelFormat.LITERT,
            engineType = if (isGguf) com.example.on_device_qwen.model.EngineType.LLAMA_CPP else com.example.on_device_qwen.model.EngineType.LITERT,
            fileSizeBytes = file.length(),
            estimatedRamBytes = (file.length() * 1.25).toLong(),
        )
    }

    fun assessCompatibility(
        metadata: ModelMetadata,
        availRamMb: Long,
        totalRamMb: Long,
    ): ModelMetadata {
        if (!metadata.isReadable) {
            return metadata.copy(
                compatibilityStatus = ModelCompatibilityStatus.UNSUPPORTED_FORMAT,
                compatibilityMessage = "File cannot be read. Import it again through the system picker.",
            )
        }
        if (metadata.isCorrupted) {
            return metadata.copy(
                compatibilityStatus = ModelCompatibilityStatus.CORRUPTED,
                compatibilityMessage = "Model header failed verification or file is truncated.",
            )
        }
        val estimatedMb = metadata.estimatedRamMb.coerceAtLeast(metadata.minRamMb)
        if (estimatedMb <= 0L || (availRamMb <= 0L && totalRamMb <= 0L)) {
            return metadata
        }
        val referenceTotal = totalRamMb.coerceAtLeast(availRamMb)
        return when {
            referenceTotal > 0L && estimatedMb > (referenceTotal * 0.9f) -> metadata.copy(
                compatibilityStatus = ModelCompatibilityStatus.INSUFFICIENT_RAM,
                compatibilityMessage = "Requires ~${estimatedMb} MB RAM (${referenceTotal} MB total on device).",
            )
            availRamMb > 0L && availRamMb < estimatedMb + 400L -> metadata.copy(
                compatibilityStatus = ModelCompatibilityStatus.TIGHT_RAM,
                compatibilityMessage = "Tight memory fit (~${estimatedMb} MB needed, ${availRamMb} MB currently free).",
            )
            else -> metadata.copy(
                compatibilityStatus = ModelCompatibilityStatus.COMPATIBLE,
                compatibilityMessage = null,
            )
        }
    }

    fun recommendCatalogModels(totalRamMb: Long, availRamMb: Long): List<CatalogModelRecommendation> {
        val effectiveRam = totalRamMb.coerceAtLeast(availRamMb)
        return ModelCatalogItem.VERIFIED_CATALOG.map { item ->
            val fits = effectiveRam <= 0L || item.minRamMb <= (effectiveRam * 0.85f)
            val recommended = effectiveRam <= 0L || item.recommendedRamMb <= effectiveRam
            val summary = when {
                effectiveRam <= 0L -> "Compatible starter option"
                recommended -> "Fits comfortably (${item.recommendedRamMb / 1024.0} GB rec.)"
                fits -> "Tight RAM fit (${item.minRamMb} MB min)"
                else -> "Exceeds device RAM (${item.minRamMb} MB min)"
            }
            CatalogModelRecommendation(
                item = item,
                fitsDeviceRam = fits,
                isRecommendedFit = recommended,
                fitSummary = summary,
            )
        }.sortedWith(
            compareByDescending<CatalogModelRecommendation> { it.isRecommendedFit }
                .thenByDescending { it.fitsDeviceRam }
                .thenBy { it.item.fileSizeBytes },
        )
    }

    suspend fun importModelSafely(
        uri: Uri,
        onState: (ModelImportState) -> Unit,
    ): Result<File> {
        val displayName = resolveUriDisplayName(uri)
        onState(
            ModelImportState(
                progress = ImportProgress.COPYING,
                fileName = displayName,
                bytesCopied = 0L,
                totalBytes = -1L,
            ),
        )

        val result = importFromUriFn(uri) { fraction ->
            val clamped = fraction.coerceIn(0f, 1f)
            val stage = if (clamped >= 0.99f) ImportProgress.VERIFYING else ImportProgress.COPYING
            onState(
                ModelImportState(
                    progress = stage,
                    fileName = displayName,
                    bytesCopied = (clamped * 1000).toLong(),
                    totalBytes = 1000L,
                ),
            )
        }

        return if (result.isSuccess) {
            onState(
                ModelImportState(
                    progress = ImportProgress.FINALIZING,
                    fileName = displayName,
                    bytesCopied = 1000L,
                    totalBytes = 1000L,
                ),
            )
            val file = result.getOrThrow()
            onState(
                ModelImportState(
                    progress = ImportProgress.READY,
                    fileName = file.name,
                    bytesCopied = file.length(),
                    totalBytes = file.length(),
                ),
            )
            Result.success(file)
        } else {
            val message = result.exceptionOrNull()?.localizedMessage?.takeIf { it.isNotBlank() }
                ?: "Could not import model file. Check storage space and select a valid .gguf or .litertlm file."
            onState(
                ModelImportState(
                    progress = ImportProgress.FAILED,
                    fileName = displayName,
                    errorMessage = message,
                ),
            )
            result
        }
    }

    suspend fun deleteModelFile(file: File): Boolean = deleteModelFn(file)

    private fun resolveUriDisplayName(uri: Uri): String {
        if (uri.scheme == "file") {
            uri.path?.let { return File(it).name }
        }
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) {
                    cursor.getString(idx)?.takeIf { it.isNotBlank() }
                } else null
            }
        } catch (_: Exception) {
            null
        } ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "Selected model"
    }
}
