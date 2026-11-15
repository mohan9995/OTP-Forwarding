package com.otpforwarder.app.ui.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.otpforwarder.app.data.repository.FilterMode
import com.otpforwarder.app.data.repository.LogRepository
import com.otpforwarder.app.data.repository.SettingsRepository
import com.otpforwarder.app.forwarding.FilterEngine
import com.otpforwarder.app.forwarding.SmsForwarder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(
    private val settingsRepository: SettingsRepository,
    private val logRepository: LogRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var initialBiometricCheckDone = false

    init {
        // Observe settings changes
        settingsRepository.settingsFlow.onEach { settings ->
            _uiState.update { current ->
                val shouldLock = if (!initialBiometricCheckDone && settings.isBiometricLockEnabled) {
                    initialBiometricCheckDone = true
                    true
                } else current.isAppLocked

                current.copy(
                    settings = settings,
                    recipientInput = if (current.recipientInput.isEmpty()) settings.recipientPhoneNumber else current.recipientInput,
                    isAppLocked = shouldLock
                )
            }
        }.launchIn(viewModelScope)

        // Observe audit logs and stats
        combine(
            logRepository.allLogs,
            logRepository.forwardedCount,
            logRepository.blockedCount
        ) { logs, forwarded, blocked ->
            _uiState.update { current ->
                current.copy(
                    logs = logs,
                    forwardedCount = forwarded,
                    blockedCount = blocked
                )
            }
        }.launchIn(viewModelScope)
    }

    fun refreshSystemState(context: Context) {
        val hasReceive = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        val hasSend = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        val hasPhoneState = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isIgnoringBattery = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false

        val activeSims = if (hasPhoneState) {
            SmsForwarder.getActiveSubscriptions(context)
        } else emptyList()

        _uiState.update { current ->
            current.copy(
                hasSmsPermissions = hasReceive && hasSend,
                isIgnoringBatteryOptimizations = isIgnoringBattery,
                availableSims = activeSims
            )
        }
    }

    fun toggleForwarding(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setForwardingEnabled(enabled)
        }
    }

    fun onRecipientInputChange(newNumber: String) {
        _uiState.update { it.copy(recipientInput = newNumber) }
    }

    fun saveRecipientPhoneNumber() {
        val number = _uiState.value.recipientInput.trim()
        viewModelScope.launch {
            settingsRepository.setRecipientPhoneNumber(number)
        }
    }

    fun updateRegexPattern(pattern: String) {
        // Validate regex syntax
        val error = try {
            Regex(pattern)
            null
        } catch (e: Exception) {
            e.localizedMessage ?: "Invalid regular expression"
        }

        _uiState.update { it.copy(regexValidationError = error) }

        if (error == null) {
            viewModelScope.launch {
                settingsRepository.setOtpRegexPattern(pattern)
            }
        }
    }

    fun testRegexPattern(sampleText: String) {
        val currentPattern = _uiState.value.settings.otpRegexPattern
        val isMatch = try {
            val regex = Regex(currentPattern, setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
            regex.containsMatchIn(sampleText)
        } catch (e: Exception) {
            false
        }

        _uiState.update {
            it.copy(
                regexTestSample = sampleText,
                regexTestResult = isMatch
            )
        }
    }

    fun setFilterMode(mode: FilterMode) {
        viewModelScope.launch {
            settingsRepository.setFilterMode(mode)
        }
    }

    fun addSender(sender: String) {
        if (sender.isBlank()) return
        viewModelScope.launch {
            settingsRepository.addSender(sender)
        }
    }

    fun removeSender(sender: String) {
        viewModelScope.launch {
            settingsRepository.removeSender(sender)
        }
    }

    fun setSelectedSimSubscription(subId: Int) {
        viewModelScope.launch {
            settingsRepository.setSelectedSubscriptionId(subId)
        }
    }

    fun updateCustomPrefix(prefix: String) {
        viewModelScope.launch {
            settingsRepository.setCustomPrefix(prefix)
        }
    }

    fun toggleBiometricLock(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBiometricLockEnabled(enabled)
        }
    }

    fun toggleMaskSensitiveData(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setMaskSensitiveData(enabled)
        }
    }

    fun unlockApp() {
        _uiState.update { it.copy(isAppLocked = false) }
    }

    fun lockApp() {
        if (_uiState.value.settings.isBiometricLockEnabled) {
            _uiState.update { it.copy(isAppLocked = true) }
        }
    }

    fun setLogFilter(filter: LogFilter) {
        _uiState.update { it.copy(selectedLogFilter = filter) }
    }

    fun clearLogs() {
        viewModelScope.launch {
            logRepository.clearLogs()
        }
    }

    fun deleteLog(id: Long) {
        viewModelScope.launch {
            logRepository.deleteLog(id)
        }
    }
}

class MainViewModelFactory(
    private val settingsRepository: SettingsRepository,
    private val logRepository: LogRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(settingsRepository, logRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
