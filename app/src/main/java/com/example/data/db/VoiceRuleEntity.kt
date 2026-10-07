package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.MatchMode
import com.example.data.model.TargetApp

@Entity(tableName = "voice_rules")
data class VoiceRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val triggerPhrase: String,
    val matchMode: MatchMode = MatchMode.CONTAINS,
    val targetApp: TargetApp = TargetApp.TASKER,
    val customAction: String = "",
    val customExtraKey: String = "",
    val customPayload: String = "",
    val customTtsFeedback: String = "",
    val isEnabled: Boolean = true
)
