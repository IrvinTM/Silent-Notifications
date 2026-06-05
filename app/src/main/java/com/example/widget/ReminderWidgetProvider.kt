package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import com.example.data.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReminderWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        Log.d("WidgetProvider", "onUpdate triggered")
        updateAllWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.d("WidgetProvider", "onReceive: ${intent.action}")
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, ReminderWidgetProvider::class.java))
            updateAllWidgets(context, appWidgetManager, appWidgetIds)
        }
    }

    companion object {
        fun updateWidget(context: Context) {
            val intent = Intent(context, ReminderWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            context.sendBroadcast(intent)
        }

        private fun updateAllWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
            val database = AppDatabase.getDatabase(context)
            val repository = TaskRepository(database.taskDao())

            CoroutineScope(Dispatchers.IO).launch {
                val nextTask = repository.getNextUpcomingTask(System.currentTimeMillis())
                launch(Dispatchers.Main) {
                    for (appWidgetId in appWidgetIds) {
                        try {
                            val views = RemoteViews(context.packageName, R.layout.reminder_widget)
                            
                            // Click to open MainActivity
                            val clickIntent = Intent(context, MainActivity::class.java)
                            val pendingIntent = PendingIntent.getActivity(
                                context,
                                0,
                                clickIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

                            // Bind task model properties view
                            if (nextTask != null) {
                                views.setTextViewText(R.id.widget_task_title, nextTask.title)
                                val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                                val formattedDate = sdf.format(Date(nextTask.scheduleTime))
                                
                                val recurrenceLabel = when (nextTask.repeatType) {
                                    "WEEKLY" -> "Weekly - $formattedDate"
                                    "MONTHLY" -> "Monthly - $formattedDate"
                                    else -> "One-time - $formattedDate"
                                }
                                views.setTextViewText(R.id.widget_task_time, recurrenceLabel)
                                views.setTextViewText(R.id.widget_status, "• ACTIVE")
                                views.setTextColor(R.id.widget_status, android.graphics.Color.parseColor("#34D399")) // Active light green
                            } else {
                                views.setTextViewText(R.id.widget_task_title, "No Upcoming Tasks")
                                views.setTextViewText(R.id.widget_task_time, "Tap to add a new silent reminder")
                                views.setTextViewText(R.id.widget_status, "• IDLE")
                                views.setTextColor(R.id.widget_status, android.graphics.Color.parseColor("#8E9199")) // Grey
                            }

                            appWidgetManager.updateAppWidget(appWidgetId, views)
                        } catch (e: Exception) {
                            Log.e("WidgetProvider", "Error updating widget id: $appWidgetId", e)
                        }
                    }
                }
            }
        }
    }
}
