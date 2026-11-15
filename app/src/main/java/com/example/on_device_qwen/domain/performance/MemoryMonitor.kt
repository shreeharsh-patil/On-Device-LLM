package com.example.on_device_qwen.domain.performance

import android.app.ActivityManager
import android.content.Context
import com.example.on_device_qwen.model.ModelCompatibilityStatus
import com.example.on_device_qwen.model.ModelMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DeviceMemoryStatus(
    val totalRamMb: Long,
    val availRamMb: Long,
    val usedRamMb: Long,
    val isLowMemory: Boolean,
    val thresholdMb: Long,
)

class MemoryMonitor(context: Context) {

    private val activityManager = context.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

    private val _memoryStatus = MutableStateFlow(readMemoryStatus())
    val memoryStatus: StateFlow<DeviceMemoryStatus> = _memoryStatus.asStateFlow()

    fun refresh(): DeviceMemoryStatus {
        val current = readMemoryStatus()
        _memoryStatus.value = current
        return current
    }

    fun getMemoryStatus(): DeviceMemoryStatus = refresh()

    private fun readMemoryStatus(): DeviceMemoryStatus {
        if (activityManager == null) {
            return DeviceMemoryStatus(
                totalRamMb = 0L,
                availRamMb = 0L,
                usedRamMb = 0L,
                isLowMemory = false,
                thresholdMb = 0L,
            )
        }
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val total = memoryInfo.totalMem / (1024L * 1024L)
        val avail = memoryInfo.availMem / (1024L * 1024L)
        val used = (total - avail).coerceAtLeast(0L)
        val threshold = memoryInfo.threshold / (1024L * 1024L)

        return DeviceMemoryStatus(
            totalRamMb = total,
            availRamMb = avail,
            usedRamMb = used,
            isLowMemory = memoryInfo.lowMemory,
            thresholdMb = threshold,
        )
    }

    /**
     * Checks whether a model's estimated RAM requirement fits cleanly with OS headroom.
     * Avoids false rejections when Android memory telemetry is unavailable (totalRamMb == 0).
     */
    fun canFitModel(estimatedRamBytes: Long): Pair<Boolean, String?> {
        val status = refresh()
        if (status.totalRamMb <= 0L) {
            return Pair(true, null)
        }
        val estimatedMb = estimatedRamBytes / (1024L * 1024L)
        val safetyBufferMb = 500L

        if (status.isLowMemory || status.availRamMb < (estimatedMb + safetyBufferMb)) {
            val warning = "Model requires ~${estimatedMb}MB RAM (plus ${safetyBufferMb}MB OS headroom), " +
                "but ${status.availRamMb}MB is currently free. Memory pressure or crashes may occur."
            return Pair(false, warning)
        }
        return Pair(true, null)
    }

    fun evaluateCompatibility(metadata: ModelMetadata): Pair<ModelCompatibilityStatus, String?> {
        return evaluateCompatibility(metadata, refresh())
    }

    companion object {
        internal fun evaluateCompatibility(
            metadata: ModelMetadata,
            status: DeviceMemoryStatus,
        ): Pair<ModelCompatibilityStatus, String?> {
            if (metadata.isCorrupted) {
                return ModelCompatibilityStatus.CORRUPTED to
                    (metadata.compatibilityMessage ?: "Model file appears corrupted or incomplete.")
            }
            if (!metadata.isReadable) {
                return ModelCompatibilityStatus.UNSUPPORTED_FORMAT to
                    "Model file cannot be read (check storage permissions)."
            }
            if (status.totalRamMb <= 0L) {
                return ModelCompatibilityStatus.COMPATIBLE to null
            }

            val fallbackMb = (metadata.estimatedRamBytes / (1024L * 1024L)).coerceAtLeast(1L)
            val minNeededMb = metadata.minRamMb.takeIf { it > 0L } ?: fallbackMb
            val recNeededMb = metadata.recommendedRamMb.takeIf { it > 0L } ?: minNeededMb

            if (status.isLowMemory || status.availRamMb + 128L < minNeededMb) {
                return ModelCompatibilityStatus.INSUFFICIENT_RAM to
                    "Needs at least ${minNeededMb}MB RAM (${status.availRamMb}MB available)."
            }
            if (status.availRamMb < recNeededMb + 300L) {
                return ModelCompatibilityStatus.TIGHT_RAM to
                    "Tight memory headroom (${status.availRamMb}MB free vs ${recNeededMb}MB recommended); consider a smaller context window or quantization."
            }
            return ModelCompatibilityStatus.COMPATIBLE to null
        }
    }
}
