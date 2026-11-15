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
    CRITICAL("Critical Heat — Pausing", true),
}

class ThermalPerformanceManager(context: Context) {

    private val context = context.applicationContext
    private val powerManager = this.context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private val _thermalState = MutableStateFlow(DeviceThermalState.NOMINAL)
    val thermalState: StateFlow<DeviceThermalState> = _thermalState.asStateFlow()

    private val _shouldPauseGeneration = MutableStateFlow(false)
    val shouldPauseGeneration: StateFlow<Boolean> = _shouldPauseGeneration.asStateFlow()

    private val _batteryTempCelsius = MutableStateFlow(28.0f)
    val batteryTempCelsius: StateFlow<Float> = _batteryTempCelsius.asStateFlow()

    private val _batteryLevelPercent = MutableStateFlow(100)
    val batteryLevelPercent: StateFlow<Int> = _batteryLevelPercent.asStateFlow()

    private val _isCharging = MutableStateFlow(false)
    val isCharging: StateFlow<Boolean> = _isCharging.asStateFlow()

    private val _thermalHeadroom = MutableStateFlow(1.0f)
    val thermalHeadroom: StateFlow<Float> = _thermalHeadroom.asStateFlow()

    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null
    private var batteryReceiverRegistered = false

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
        updateHeadroom()
    }

    private fun applyThermalState(state: DeviceThermalState) {
        _thermalState.value = state
        _shouldPauseGeneration.value = (state == DeviceThermalState.CRITICAL)
    }

    private fun registerThermalCallbacks() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            val listener = PowerManager.OnThermalStatusChangedListener { status ->
                val mapped = when (status) {
                    PowerManager.THERMAL_STATUS_NONE -> DeviceThermalState.NOMINAL
                    PowerManager.THERMAL_STATUS_LIGHT -> DeviceThermalState.LIGHT
                    PowerManager.THERMAL_STATUS_MODERATE -> DeviceThermalState.MODERATE
                    PowerManager.THERMAL_STATUS_SEVERE -> DeviceThermalState.SEVERE
                    PowerManager.THERMAL_STATUS_CRITICAL,
                    PowerManager.THERMAL_STATUS_EMERGENCY,
                    PowerManager.THERMAL_STATUS_SHUTDOWN -> DeviceThermalState.CRITICAL
                    else -> DeviceThermalState.NOMINAL
                }
                applyThermalState(mapped)
            }
            thermalListener = listener
            try {
                powerManager.addThermalStatusListener(this.context.mainExecutor, listener)
            } catch (e: Exception) {
                android.util.Log.w("ThermalMgr", "Failed to register thermal status listener", e)
                thermalListener = null
            }
        }
    }

    private fun registerBatteryReceiver() {
        if (batteryReceiverRegistered) return
        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                this.context.registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                this.context.registerReceiver(batteryReceiver, filter)
            }
            batteryReceiverRegistered = true
        } catch (_: Exception) {
            batteryReceiverRegistered = false
        }
    }

    fun updateHeadroom() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && powerManager != null) {
            try {
                val headroom = powerManager.getThermalHeadroom(30)
                if (!headroom.isNaN()) {
                    _thermalHeadroom.value = headroom
                    if (headroom < 0.05f && _thermalState.value.ordinal < DeviceThermalState.SEVERE.ordinal) {
                        applyThermalState(DeviceThermalState.SEVERE)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    /** Recommends an inter-token yield/throttle delay in milliseconds during sustained decode. */
    fun recommendedThrottleDelayMs(): Long = when (_thermalState.value) {
        DeviceThermalState.NOMINAL, DeviceThermalState.LIGHT -> 0L
        DeviceThermalState.MODERATE -> 8L
        DeviceThermalState.SEVERE -> 25L
        DeviceThermalState.CRITICAL -> 100L
    }

    /**
     * Computes CPU thread budget respecting user preference, processor topology, and live
     * thermal status. Caps severe heat to 2 threads and critical heat to 1 thread.
     */
    fun getAdaptiveThreadCount(profile: PerformanceMode, requestedThreads: Int): Int {
        val totalCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val maxPerfThreads = if (totalCores >= 8) 4 else (totalCores - 1).coerceAtLeast(2)

        val baseThreads = when (profile) {
            PerformanceMode.ECO -> 2
            PerformanceMode.BALANCED -> requestedThreads.coerceIn(2, maxPerfThreads)
            PerformanceMode.PERFORMANCE -> requestedThreads.coerceIn(2, totalCores.coerceAtMost(8))
        }

        return when (_thermalState.value) {
            DeviceThermalState.NOMINAL, DeviceThermalState.LIGHT -> baseThreads
            DeviceThermalState.MODERATE -> (baseThreads - 1).coerceAtLeast(2)
            DeviceThermalState.SEVERE -> minOf(baseThreads, 2)
            DeviceThermalState.CRITICAL -> 1
        }
    }

    internal fun setThermalStateForTest(state: DeviceThermalState) {
        applyThermalState(state)
    }

    fun cleanup() {
        val listener = thermalListener
        thermalListener = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null && listener != null) {
            try {
                powerManager.removeThermalStatusListener(listener)
            } catch (_: Exception) {
            }
        }
        if (batteryReceiverRegistered) {
            batteryReceiverRegistered = false
            try {
                context.unregisterReceiver(batteryReceiver)
            } catch (_: Exception) {
            }
        }
    }
}
