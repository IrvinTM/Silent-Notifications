package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val notes: String,
    val scheduleTime: Long, // timestamp in epoch millis
    val repeatType: String, // "ONCE", "WEEKLY", "MONTHLY"
    val isCompleted: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val hapticPatternId: Int = 0, // 0 = Default system pattern, or custom ID
    val googleEventId: String? = null // Associated calendar event ID
)
