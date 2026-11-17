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
        val connectionType = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            activeNetwork == null || caps == null -> "Offline"
            else -> "Connected"
        }

        val isPowerSave = runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            pm?.isPowerSaveMode == true
        }.getOrDefault(false)

        val isBluetoothEnabled = runCatching {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
            bm?.adapter?.isEnabled == true
        }.getOrNull()

        val ringerMode = runCatching {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            when (audio?.ringerMode) {
                android.media.AudioManager.RINGER_MODE_SILENT -> "Silent"
                android.media.AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
                android.media.AudioManager.RINGER_MODE_NORMAL -> "Normal"
                else -> null
            }
        }.getOrNull()

        val dndActive = runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            val filter = nm?.currentInterruptionFilter ?: android.app.NotificationManager.INTERRUPTION_FILTER_ALL
            filter != android.app.NotificationManager.INTERRUPTION_FILTER_ALL && filter != android.app.NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        }.getOrDefault(false)

        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        val storageInfo = runCatching {
            val statFs = android.os.StatFs(android.os.Environment.getDataDirectory().path)
            val freeGb = statFs.availableBytes / (1024.0 * 1024.0 * 1024.0)
            val totalGb = statFs.totalBytes / (1024.0 * 1024.0 * 1024.0)
            String.format(Locale.US, "%.1f GB free of %.1f GB", freeGb, totalGb)
        }.getOrNull()

        val ramInfo = runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            am?.getMemoryInfo(memInfo)
            if (memInfo.totalMem > 0) {
                val availGb = memInfo.availMem / (1024.0 * 1024.0 * 1024.0)
                val totalGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
                String.format(Locale.US, "%.1f GB available of %.1f GB", availGb, totalGb)
            } else null
        }.getOrNull()

        val openTasks = runCatching { LocalTaskStore(context).tasks().filterNot { it.completed } }.getOrDefault(emptyList())
        val today = LocalDayWindow.containing(System.currentTimeMillis())
        val dueToday = openTasks.filter { it.dueAt != null && it.dueAt >= today.startsAt && it.dueAt < today.endsAtExclusive }

        return mapOf(
            "battery_level" to "$batteryPct%",
            "is_charging" to isCharging,
            "is_power_save" to isPowerSave,
            "connection_type" to connectionType,
            "bluetooth_enabled" to (isBluetoothEnabled ?: false),
            "ringer_mode" to (ringerMode ?: "Normal"),
            "dnd_active" to dndActive,
            "time" to currentTime,
            "device_model" to android.os.Build.MODEL,
            "storage" to (storageInfo ?: "unknown"),
            "ram" to (ramInfo ?: "unknown"),
            "open_tasks_count" to openTasks.size,
            "due_today_count" to dueToday.size
        )
    }
}
