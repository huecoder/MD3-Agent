package dev.huecoder.md3assistant.prompt

import android.content.Context
import android.os.BatteryManager
import android.os.Build

class DeviceInfoProvider(private val context: Context) {

    fun deviceName(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        return if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
    }

    fun androidVersion(): String = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    fun batteryDescription(): String {
        val battery = context.getSystemService(BatteryManager::class.java) ?: return UNKNOWN
        val level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (level !in 0..100) return UNKNOWN
        return if (battery.isCharging) "$level%, charging" else "$level%"
    }

    private companion object {
        const val UNKNOWN = "unknown"
    }
}
