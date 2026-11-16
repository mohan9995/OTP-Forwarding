package com.otpforwarder.app

import android.app.Application
import android.content.Context
import android.util.Log
import com.otpforwarder.app.data.local.AppDatabase
import com.otpforwarder.app.data.repository.LogRepository
import com.otpforwarder.app.data.repository.SettingsRepository

class OtpForwarderApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var logRepository: LogRepository
        private set

    override fun onCreate() {
        super.onCreate()

        // Capture uncaught exceptions to help diagnose any device-specific crashes
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("OtpForwarderApp", "Fatal uncaught exception on thread ${thread.name}", throwable)
            try {
                val prefs = getSharedPreferences("crash_logs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("last_crash", Log.getStackTraceString(throwable))
                    .putLong("crash_timestamp", System.currentTimeMillis())
                    .commit()
            } catch (e: Exception) {
                Log.e("OtpForwarderApp", "Failed to persist crash trace", e)
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        database = AppDatabase.getInstance(this)
        settingsRepository = SettingsRepository(this)
        logRepository = LogRepository(database.logDao())
    }
}
