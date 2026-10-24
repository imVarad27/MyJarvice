package com.example.myjarvice.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.myjarvice.MainActivity
import com.example.myjarvice.R

class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = LocalTaskStore(context)
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val task = store.tasks().firstOrNull { it.id == taskId } ?: return
        if (task.completed || task.reminderAt == null) return

        store.setReminder(task.id, null)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Jarvis task reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Reminders for your private phone tasks"
                }
            )
        }
        val openApp = PendingIntent.getActivity(
            context,
            task.id.hashCode(),
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_TASKS, true),
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        manager.notify(
            task.id.hashCode(),
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Task reminder")
                .setContentText(task.title)
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .build()
        )
    }

    companion object {
        const val CHANNEL_ID = "jarvis_task_reminders"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TITLE = "task_title"
    }
}
