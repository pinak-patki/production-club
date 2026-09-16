package com.Pinak.ProductiveClub.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val startTime: String,   // "HH:mm" 24-hour format (From)
    val endTime: String = "", // "HH:mm" 24-hour format (To)
    val durationMinutes: Int = 0,
    val date: String,        // "yyyy-MM-dd"
    val isCompleted: Boolean = false,
    val priority: String = "Medium", // "High", "Medium", "Low"
    val category: String = "General" // "Study", "Coding", "Gym", "Focus", "General"
)