package com.example.myjarvice.wake

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restores inbox reminders after a reboot. Wake listening is started explicitly from Settings. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            com.example.myjarvice.data.RememberInboxStore(context).restoreReminders()
        }
    }
}
