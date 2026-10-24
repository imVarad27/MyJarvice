package com.example.myjarvice.wake

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restores inbox and task reminders after a reboot. Wake listening is started explicitly from Settings. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            com.example.myjarvice.data.RememberInboxStore(context).restoreReminders()
            com.example.myjarvice.data.LocalTaskStore(context).restoreReminders()
        }
    }
}
