package com.example.android.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

/**
 * Demonstrates Context-Registered (Dynamic) Broadcast Receiver
 * as covered in "Broadcasts.pdf" (Slides 4, 6, 8).
 * Monitors system power/battery broadcasts (ACTION_POWER_CONNECTED, ACTION_POWER_DISCONNECTED,
 * ACTION_BATTERY_LOW, ACTION_BATTERY_OKAY) to inform users during assessment tests.
 */
class PowerStatusReceiver(private val context: Context) : BroadcastReceiver() {

    private val _isCharging = mutableStateOf(false)
    val isCharging: State<Boolean> = _isCharging

    private val _isLowBattery = mutableStateOf(false)
    val isLowBattery: State<Boolean> = _isLowBattery

    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_POWER_CONNECTED -> _isCharging.value = true
            Intent.ACTION_POWER_DISCONNECTED -> _isCharging.value = false
            Intent.ACTION_BATTERY_LOW -> _isLowBattery.value = true
            Intent.ACTION_BATTERY_OKAY -> _isLowBattery.value = false
        }
    }

    fun register() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
        }
        context.registerReceiver(this, filter)

        // Initial check via sticky broadcast
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }
        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        _isCharging.value = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
    }

    fun unregister() {
        try {
            context.unregisterReceiver(this)
        } catch (_: Exception) {}
    }
}
