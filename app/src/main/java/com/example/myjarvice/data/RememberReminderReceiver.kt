package com.example.myjarvice.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.myjarvice.MainActivity
import com.example.myjarvice.R

class RememberReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Saved item" }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Jarvis reminders", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val openApp = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        manager.notify(intent.getStringExtra(EXTRA_ITEM_ID).orEmpty().hashCode(),
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Jarvis reminder")
                .setContentText(title)
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .build())
    }

    companion object {
        const val CHANNEL_ID = "jarvis_remember_reminders"
        const val EXTRA_ITEM_ID = "remember_item_id"
        const val EXTRA_TITLE = "remember_title"
    }
}
