package com.otpforwarder.app.data.repository

import com.otpforwarder.app.data.local.LogDao
import com.otpforwarder.app.data.local.LogEntity
import kotlinx.coroutines.flow.Flow

class LogRepository(private val logDao: LogDao) {

    val allLogs: Flow<List<LogEntity>> = logDao.getAllLogs()
    val forwardedCount: Flow<Int> = logDao.getForwardedCount()
    val blockedCount: Flow<Int> = logDao.getBlockedCount()

    suspend fun insertLog(log: LogEntity): Long = logDao.insertLog(log)

    suspend fun clearLogs() = logDao.clearLogs()

    suspend fun deleteLog(id: Long) = logDao.deleteLogById(id)
}
