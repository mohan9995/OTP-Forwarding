package com.otpforwarder.app

import android.app.Application
import android.content.Context
import android.content.Intent
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

        // Capture uncaught exceptions and launch CrashActivity to show exact stack trace
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("OtpForwarderApp", "Fatal uncaught exception on thread ${thread.name}", throwable)
            val stackTrace = Log.getStackTraceString(throwable)
            try {
                val intent = Intent(this, CrashActivity::class.java).apply {
                    putExtra("error_title", "Error in thread '${thread.name}'")
                    putExtra("error_message", throwable.message ?: throwable.javaClass.simpleName)
                    putExtra("stack_trace", stackTrace)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.e("OtpForwarderApp", "Failed to launch CrashActivity", e)
            }
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(10)
        }

        try {
            database = AppDatabase.getInstance(this)
            settingsRepository = SettingsRepository(this)
            logRepository = LogRepository(database.logDao())
        } catch (t: Throwable) {
            Log.e("OtpForwarderApp", "Fatal database/repository initialization error", t)
        }
    }
}
