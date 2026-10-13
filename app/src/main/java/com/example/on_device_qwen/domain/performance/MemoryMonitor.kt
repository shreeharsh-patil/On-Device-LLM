package com.example.on_device_qwen.domain.performance

import android.app.ActivityManager
import android.content.Context

data class DeviceMemoryStatus(
    val totalRamMb: Long,
    val availRamMb: Long,
    val usedRamMb: Long,
    val isLowMemory: Boolean,
    val thresholdMb: Long
)

class MemoryMonitor(context: Context) {

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

    fun getMemoryStatus(): DeviceMemoryStatus {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)

        val total = memoryInfo.totalMem / (1024 * 1024)
        val avail = memoryInfo.availMem / (1024 * 1024)
        val used = (total - avail).coerceAtLeast(0)
        val threshold = memoryInfo.threshold / (1024 * 1024)

        return DeviceMemoryStatus(
            totalRamMb = total,
            availRamMb = avail,
            usedRamMb = used,
            isLowMemory = memoryInfo.lowMemory,
            thresholdMb = threshold
        )
    }

    /**
     * Checks if a model's estimated RAM requirement fits within device constraints,
     * leaving a safety headroom buffer of 600 MB for Android OS and UI rendering.
     */
    fun canFitModel(estimatedRamBytes: Long): Pair<Boolean, String?> {
        val status = getMemoryStatus()
        val estimatedMb = estimatedRamBytes / (1024 * 1024)
        val safetyBufferMb = 600L

        if (status.availRamMb < (estimatedMb + safetyBufferMb)) {
            val warning = "Model requires ~${estimatedMb}MB RAM, but only ${status.availRamMb}MB is available. Running this model may cause system memory pressure or app crashes."
            return Pair(false, warning)
        }

        return Pair(true, null)
    }
}
