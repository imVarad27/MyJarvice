package com.example.myjarvice.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import com.example.myjarvice.MainActivity
import com.example.myjarvice.R
import com.example.myjarvice.data.LocalDayWindow
import com.example.myjarvice.data.LocalTask
import com.example.myjarvice.data.LocalTaskStore
import com.example.myjarvice.data.TaskAgenda
import java.text.DateFormat
import java.util.Date

class JarvisTodayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = buildRemoteViews(context)
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context) ?: return
                val component = ComponentName(context, JarvisTodayWidgetProvider::class.java)
                val ids = manager.getAppWidgetIds(component)
                if (ids != null && ids.isNotEmpty()) {
                    val views = buildRemoteViews(context)
                    manager.updateAppWidget(ids, views)
                }
            } catch (_: Exception) {
                // Ignore widget refresh failures in background or unit tests
            }
        }

        fun buildRemoteViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_jarvis_today)

            val now = System.currentTimeMillis()
            val tasks = try {
                LocalTaskStore(context).tasks()
            } catch (_: Exception) {
                emptyList()
            }
            val agenda = TaskAgenda.from(tasks, now)
            val openTasks = agenda.open

            // Subtitle
            val subtitle = when {
                openTasks.isEmpty() -> "All caught up"
                openTasks.size == 1 -> "1 open task"
                else -> "${openTasks.size} open tasks"
            }
            views.setTextViewText(R.id.widget_subtitle, subtitle)

            // Setup click intents
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

            // Main app intent (header click)
            val appIntent = PendingIntent.getActivity(
                context, 101,
                Intent(context, MainActivity::class.java),
                flags
            )
            views.setOnClickPendingIntent(R.id.widget_header, appIntent)

            // Voice action
            val voiceIntent = PendingIntent.getActivity(
                context, 102,
                Intent(context, MainActivity::class.java).apply {
                    putExtra(MainActivity.EXTRA_START_VOICE, true)
                },
                flags
            )
            views.setOnClickPendingIntent(R.id.widget_btn_voice, voiceIntent)

            // Capture action
            val captureIntent = PendingIntent.getActivity(
                context, 103,
                Intent(context, MainActivity::class.java).apply {
                    putExtra(MainActivity.EXTRA_OPEN_CAPTURE, true)
                },
                flags
            )
            views.setOnClickPendingIntent(R.id.widget_btn_capture, captureIntent)

            // Tasks action
            val tasksIntent = PendingIntent.getActivity(
                context, 104,
                Intent(context, MainActivity::class.java).apply {
                    putExtra(MainActivity.EXTRA_OPEN_TASKS, true)
                },
                flags
            )
            views.setOnClickPendingIntent(R.id.widget_btn_tasks, tasksIntent)

            // Populate up to 3 task rows
            val rowIds = listOf(R.id.widget_task_row_1, R.id.widget_task_row_2, R.id.widget_task_row_3)
            val titleIds = listOf(R.id.widget_task_title_1, R.id.widget_task_title_2, R.id.widget_task_title_3)
            val dueIds = listOf(R.id.widget_task_due_1, R.id.widget_task_due_2, R.id.widget_task_due_3)

            if (openTasks.isEmpty()) {
                views.setViewVisibility(R.id.widget_empty_text, View.VISIBLE)
                rowIds.forEach { views.setViewVisibility(it, View.GONE) }
            } else {
                views.setViewVisibility(R.id.widget_empty_text, View.GONE)
                for (i in 0 until 3) {
                    if (i < openTasks.size) {
                        val task = openTasks[i]
                        views.setViewVisibility(rowIds[i], View.VISIBLE)
                        views.setTextViewText(titleIds[i], task.title)
                        views.setTextViewText(dueIds[i], formatWidgetDue(task, now))
                        views.setOnClickPendingIntent(rowIds[i], tasksIntent)
                    } else {
                        views.setViewVisibility(rowIds[i], View.GONE)
                    }
                }
            }

            return views
        }

        fun formatWidgetDue(task: LocalTask, now: Long): String {
            val due = task.dueAt ?: return ""
            val today = LocalDayWindow.containing(now)
            return when {
                due < today.startsAt -> "Overdue"
                due < today.endsAtExclusive -> "Today"
                due < LocalDayWindow.containing(today.endsAtExclusive).endsAtExclusive -> "Tomorrow"
                else -> DateFormat.getDateInstance(DateFormat.SHORT).format(Date(due))
            }
        }
    }
}
