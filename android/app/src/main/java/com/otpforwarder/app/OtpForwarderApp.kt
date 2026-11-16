package com.otpforwarder.app

import android.app.Application
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
        database = AppDatabase.getInstance(this)
        settingsRepository = SettingsRepository(this)
        logRepository = LogRepository(database.logDao())
    }
}
