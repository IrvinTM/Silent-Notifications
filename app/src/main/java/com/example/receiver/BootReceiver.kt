package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device reboot detected. Rescheduling future alarms...")
            val database = AppDatabase.getDatabase(context)
            val repository = TaskRepository(database.taskDao())

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Fetch a single snapshot of all tasks
                    val tasks = repository.allTasks.first()
                    val now = System.currentTimeMillis()
                    var count = 0
                    tasks.forEach { task ->
                        if (!task.isCompleted && !task.isArchived && task.scheduleTime > now) {
                            AlarmScheduler.scheduleAlarm(context, task)
                            count++
                        }
                    }
                    Log.d("BootReceiver", "Successfully re-established $count silent alarms on boot.")
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Error while rescheduling alarms on boot", e)
                }
            }
        }
    }
}
