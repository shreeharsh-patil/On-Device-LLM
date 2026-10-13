package com.example.on_device_qwen.domain.performance

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.example.on_device_qwen.data.preferences.PerformanceMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DeviceThermalState(val label: String, val isWarning: Boolean) {
    NOMINAL("Nominal", false),
    LIGHT("Light Warming", false),
    MODERATE("Moderate Heat", false),
    SEVERE("Severe Thermal Throttling", true),
    CRITICAL("Critical Heat — Pausing", true)
}

class ThermalPerformanceManager(context: Context) {

    // BUG-011 FIX: Always use applicationContext to prevent Activity context leaks
    // across configuration changes (ThermalPerformanceManager lives in the ViewModel scope).
    private val context = context.applicationContext
    private val powerManager = this.context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private val _thermalState = MutableStateFlow(DeviceThermalState.NOMINAL)
    val thermalState: StateFlow<DeviceThermalState> = _thermalState.asStateFlow()

    private val _batteryTempCelsius = MutableStateFlow(28.0f)
    val batteryTempCelsius: StateFlow<Float> = _batteryTempCelsius.asStateFlow()

    private val _batteryLevelPercent = MutableStateFlow(100)
    val batteryLevelPercent: StateFlow<Int> = _batteryLevelPercent.asStateFlow()

    private val _isCharging = MutableStateFlow(false)
    val isCharging: StateFlow<Boolean> = _isCharging.asStateFlow()

    private val _thermalHeadroom = MutableStateFlow(1.0f)
    val thermalHeadroom: StateFlow<Float> = _thermalHeadroom.asStateFlow()

    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 250)
                _batteryTempCelsius.value = tempRaw / 10.0f

                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    _batteryLevelPercent.value = ((level.toFloat() / scale) * 100).toInt()
                }

                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                _isCharging.value = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
            }
        }
    }

    init {
        registerThermalCallbacks()
        registerBatteryReceiver()
    }

    private fun registerThermalCallbacks() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
                _thermalState.value = when (status) {
                    PowerManager.THERMAL_STATUS_NONE -> DeviceThermalState.NOMINAL
                    PowerManager.THERMAL_STATUS_LIGHT -> DeviceThermalState.LIGHT
                    PowerManager.THERMAL_STATUS_MODERATE -> DeviceThermalState.MODERATE
                    PowerManager.THERMAL_STATUS_SEVERE -> DeviceThermalState.SEVERE
                    PowerManager.THERMAL_STATUS_CRITICAL,
                    PowerManager.THERMAL_STATUS_EMERGENCY,
                    PowerManager.THERMAL_STATUS_SHUTDOWN -> DeviceThermalState.CRITICAL
                    else -> DeviceThermalState.NOMINAL
                }
            }
            try {
                powerManager.addThermalStatusListener(this.context.mainExecutor, thermalListener!!)
            } catch (e: Exception) {
                // BUG-012 FIX: Log failure so it's diagnosable; thermal state will remain NOMINAL
                android.util.Log.w("ThermalMgr", "Failed to register thermal status listener — thermal throttling disabled", e)
                thermalListener = null
            }
        }
    }

    private fun registerBatteryReceiver() {
        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            // BUG-024 FIX: Specify RECEIVER_NOT_EXPORTED on Android 14+ (API 34) to comply
            // with the new dynamic receiver export requirement.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                this.context.registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                this.context.registerReceiver(batteryReceiver, filter)
            }
        } catch (_: Exception) {}
    }

    fun updateHeadroom() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && powerManager != null) {
            try {
                val headroom = powerManager.getThermalHeadroom(30)
                if (!headroom.isNaN()) {
                    _thermalHeadroom.value = headroom
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Calculates the optimal CPU thread count considering current thermal conditions,
     * available processor cores, and user-selected performance profile.
     * Prevents scheduling onto slow efficiency cores which causes multi-threading barrier stalls
     * and excessive thermal dissipation on ARM big.LITTLE architectures.
     */
    fun getAdaptiveThreadCount(profile: PerformanceMode, requestedThreads: Int): Int {
        val totalCores = Runtime.getRuntime().availableProcessors()
        // Mobile SoCs (Snapdragon, Dimensity, Tensor) typically have 3-4 performance cores.
        // Pinning 4 threads yields maximum speed while keeping silicon temperatures cool.
        val maxPerfThreads = if (totalCores >= 8) 4 else (totalCores - 1).coerceAtLeast(2)

        val baseThreads = when (profile) {
            PerformanceMode.ECO -> 2
            PerformanceMode.BALANCED -> requestedThreads.coerceIn(2, maxPerfThreads)
            PerformanceMode.PERFORMANCE -> maxPerfThreads
        }

        // Dynamic thermal throttling based on real Android OS thermal status
        return when (_thermalState.value) {
            DeviceThermalState.NOMINAL -> baseThreads
            DeviceThermalState.LIGHT -> baseThreads
            DeviceThermalState.MODERATE -> (baseThreads - 1).coerceAtLeast(2)
            DeviceThermalState.SEVERE -> 2 // Force drop to 2 threads to cool down SoC
            DeviceThermalState.CRITICAL -> 1 // Emergency single-thread fallback to protect device
        }
    }

    fun cleanup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null && thermalListener != null) {
            try {
                powerManager.removeThermalStatusListener(thermalListener!!)
            } catch (_: Exception) {}
        }
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {}
    }
}
