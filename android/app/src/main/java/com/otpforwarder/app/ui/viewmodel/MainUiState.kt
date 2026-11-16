package com.otpforwarder.app.ui.viewmodel

import com.otpforwarder.app.data.local.LogEntity
import com.otpforwarder.app.data.repository.UserSettings
import com.otpforwarder.app.forwarding.SimSubscriptionInfo

enum class LogFilter {
    ALL,
    FORWARDED,
    BLOCKED,
    FAILED
}

data class MainUiState(
    val settings: UserSettings = UserSettings(),
    val logs: List<LogEntity> = emptyList(),
    val forwardedCount: Int = 0,
    val blockedCount: Int = 0,
    val availableSims: List<SimSubscriptionInfo> = emptyList(),
    val isAppLocked: Boolean = false,
    val regexTestSample: String = "",
    val regexTestResult: Boolean? = null,
    val regexValidationError: String? = null,
    val recipientInput: String = "",
    val hasSmsPermissions: Boolean = false,
    val isIgnoringBatteryOptimizations: Boolean = false,
    val selectedLogFilter: LogFilter = LogFilter.ALL
)
