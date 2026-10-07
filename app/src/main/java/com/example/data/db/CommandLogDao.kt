package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CommandLogDao {
    @Query("SELECT * FROM command_logs ORDER BY timestamp DESC LIMIT 200")
    fun getAllLogs(): Flow<List<CommandLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: CommandLogEntity): Long

    @Query("DELETE FROM command_logs")
    suspend fun clearLogs()

    @Query("SELECT COUNT(*) FROM command_logs")
    suspend fun getLogCount(): Int
}
