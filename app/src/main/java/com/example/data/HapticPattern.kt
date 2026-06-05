package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "haptic_patterns")
data class HapticPattern(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val patternStr: String, // Comma-separated list of durations: "0,200,100,500"
    val isSystem: Boolean = false
)
