package com.example.myjarvice.wake

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.myjarvice.data.SettingsStore

/** Restarts the wake-word service after the device reboots, if the user enabled it. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val settings = SettingsStore(context.applicationContext)
            if (settings.wakeWordEnabled) {
                WakeWordService.start(context.applicationContext)
            }
        }
    }
}
