package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "command_logs")
data class CommandLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val rawText: String,
    val matchedRuleName: String? = null,
    val targetApp: String,
    val intentAction: String,
    val extrasSummary: String,
    val success: Boolean = true
)
