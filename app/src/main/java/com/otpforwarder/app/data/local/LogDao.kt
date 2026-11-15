package com.otpforwarder.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {
    @Query("SELECT * FROM forwarding_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<LogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: LogEntity): Long

    @Query("DELETE FROM forwarding_logs")
    suspend fun clearLogs()

    @Query("DELETE FROM forwarding_logs WHERE id = :id")
    suspend fun deleteLogById(id: Long)

    @Query("SELECT COUNT(*) FROM forwarding_logs WHERE status = 'FORWARDED'")
    fun getForwardedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM forwarding_logs WHERE status = 'BLOCKED'")
    fun getBlockedCount(): Flow<Int>
}
