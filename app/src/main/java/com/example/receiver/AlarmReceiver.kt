package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.Task
import com.example.data.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
        private const val CHANNEL_ID = "silent_reminders_channel_vibe_v2"
        private const val CHANNEL_NAME = "Silent Reminders (Vibration Only)"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getIntExtra("TASK_ID", -1)
        if (taskId == -1) return

        Log.d(TAG, "Alarm triggered for task ID: $taskId")

        val database = AppDatabase.getDatabase(context)
        val repository = TaskRepository(database.taskDao())

        val pendingResult = goAsync()
        // Run asynchronously on background dispatcher
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val task = repository.getTaskById(taskId)
                if (task != null && !task.isCompleted && !task.isArchived) {
                    // Determine active haptic pattern
                    val pattern = if (task.hapticPatternId != 0) {
                        val p = database.hapticPatternDao().getPatternById(task.hapticPatternId)
                        if (p != null) {
                            try {
                                p.patternStr.split(",").map { it.trim().toLong() }.toLongArray()
                            } catch (e: Exception) {
                                longArrayOf(0, 500, 200, 500, 200, 800)
                            }
                        } else {
                            longArrayOf(0, 500, 200, 500, 200, 800)
                        }
                    } else {
                        longArrayOf(0, 500, 200, 500, 200, 800)
                    }

                    val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.POST_NOTIFICATIONS
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    } else {
                        true
                    }

                    if (hasNotificationPermission) {
                        // 1. Show notification with dynamic vibration channel (no sound, system process handles it natively)
                        showNotification(context, task, pattern)
                        // Allow some time for database operations and notification to register fully
                        kotlinx.coroutines.delay(1000)
                    } else {
                        // Direct hardware vibration fallback (app process must stay alive to finish it)
                        vibratePhone(context, pattern)
                        val totalDuration = pattern.sum()
                        if (totalDuration > 0) {
                            val delayTime = minOf(totalDuration, 9000L) // limit to 9s max duration for broadcast safety
                            kotlinx.coroutines.delay(delayTime)
                        }
                    }

                    // 2. Reschedule or mark complete
                    rescheduleOrComplete(context, repository, task)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing alarm receiver", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun vibratePhone(context: Context, pattern: LongArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, -1)
                }
            }
            Log.d(TAG, "Hardware vibration triggered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering program vibration", e)
        }
    }

    private fun showNotification(context: Context, task: Task, pattern: LongArray) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        // Use dynamic channel IDs incorporating the pattern ID. 
        // This is crucial because NotificationChannel parameters (such as vibrationStyle) are locked by system on first creation!
        val dynamicChannelId = "${CHANNEL_ID}_p_${task.hapticPatternId}"
        val dynamicChannelName = "$CHANNEL_NAME (Style ${task.hapticPatternId})"

        // Create Channel with NO sound and custom vibration on Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                dynamicChannelId,
                dynamicChannelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Vibrate-only alerts for Silent Reminders"
                enableVibration(true)
                vibrationPattern = pattern
                setSound(null, null) // NO SOUND
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            task.id,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, dynamicChannelId)
            .setContentTitle(task.title)
            .setContentText(task.notes.ifBlank { "Silent alert scheduled for now." })
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setSound(null) // No sound
            .setVibrate(pattern) // Custom wave pattern
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        notificationManager.notify(task.id, notification)
    }

    private suspend fun rescheduleOrComplete(context: Context, repository: TaskRepository, task: Task) {
        when (task.repeatType) {
            "ONCE" -> {
                // Non-repeating timer finished. Mark as completed.
                val updatedTask = task.copy(isCompleted = true)
                repository.update(updatedTask, context)
            }
            "WEEKLY" -> {
                // Reschedule for next week (7 days)
                val newTime = task.scheduleTime + (7 * 24 * 60 * 60 * 1000L)
                val updatedTask = task.copy(scheduleTime = newTime)
                repository.update(updatedTask, context)
                Log.d(TAG, "Rescheduled WEEKLY task ${task.id} to $newTime")
            }
            "MONTHLY" -> {
                // Reschedule for exactly next month
                val calendar = Calendar.getInstance().apply {
                    timeInMillis = task.scheduleTime
                }
                calendar.add(Calendar.MONTH, 1)
                val newTime = calendar.timeInMillis
                val updatedTask = task.copy(scheduleTime = newTime)
                repository.update(updatedTask, context)
                Log.d(TAG, "Rescheduled MONTHLY task ${task.id} to $newTime")
            }
        }
    }
}
