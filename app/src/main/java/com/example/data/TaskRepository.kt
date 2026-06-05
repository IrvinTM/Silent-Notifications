package com.example.data

import android.content.Context
import com.example.receiver.AlarmScheduler
import com.example.widget.ReminderWidgetProvider
import kotlinx.coroutines.flow.Flow

class TaskRepository(private val taskDao: TaskDao) {

    val allTasks: Flow<List<Task>> = taskDao.getAllTasks()

    suspend fun getTaskById(id: Int): Task? {
        return taskDao.getTaskById(id)
    }

    suspend fun getNextUpcomingTask(now: Long = System.currentTimeMillis()): Task? {
        return taskDao.getNextUpcomingTask(now)
    }

    suspend fun insert(task: Task, context: Context) {
        val insertedId = taskDao.insertTask(task)
        val insertedTask = task.copy(id = insertedId.toInt())
        if (!insertedTask.isCompleted && !insertedTask.isArchived) {
            AlarmScheduler.scheduleAlarm(context, insertedTask)
        }
        ReminderWidgetProvider.updateWidget(context)
    }

    suspend fun update(task: Task, context: Context) {
        taskDao.updateTask(task)
        if (task.isCompleted || task.isArchived) {
            AlarmScheduler.cancelAlarm(context, task)
        } else {
            AlarmScheduler.scheduleAlarm(context, task)
        }
        ReminderWidgetProvider.updateWidget(context)
    }

    suspend fun delete(task: Task, context: Context) {
        taskDao.deleteById(task.id)
        AlarmScheduler.cancelAlarm(context, task)
        ReminderWidgetProvider.updateWidget(context)
    }
}
