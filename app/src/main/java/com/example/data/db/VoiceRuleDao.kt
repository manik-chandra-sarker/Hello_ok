package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceRuleDao {
    @Query("SELECT * FROM voice_rules ORDER BY id DESC")
    fun getAllRules(): Flow<List<VoiceRuleEntity>>

    @Query("SELECT * FROM voice_rules WHERE isEnabled = 1")
    suspend fun getActiveRules(): List<VoiceRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: VoiceRuleEntity): Long

    @Update
    suspend fun updateRule(rule: VoiceRuleEntity)

    @Delete
    suspend fun deleteRule(rule: VoiceRuleEntity)

    @Query("SELECT COUNT(*) FROM voice_rules")
    suspend fun getRuleCount(): Int
}
