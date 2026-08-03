package com.example.myjarvice.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeviceContextProvider(private val context: Context) {

    fun getDeviceContext(): Map<String, Any> {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            context.registerReceiver(null, filter)
        }

        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else 0

        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        return mapOf(
            "battery_level" to "$batteryPct%",
            "is_charging" to isCharging,
            "connection_type" to if (isWifi) "Wi-Fi" else "Cellular/Offline",
            "time" to currentTime,
            "device_model" to android.os.Build.MODEL
        )
    }
}
