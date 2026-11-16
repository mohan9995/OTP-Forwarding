package com.otpforwarder.app.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "otp_settings")

enum class FilterMode {
    DISABLED,
    WHITELIST,
    BLACKLIST
}

data class UserSettings(
    val isForwardingEnabled: Boolean = false,
    val recipientPhoneNumber: String = "",
    val otpRegexPattern: String = DEFAULT_OTP_REGEX,
    val filterMode: FilterMode = FilterMode.DISABLED,
    val senderList: Set<String> = emptySet(),
    val selectedSubscriptionId: Int = -1,
    val customPrefix: String = DEFAULT_PREFIX,
    val isBiometricLockEnabled: Boolean = false,
    val maskSensitiveData: Boolean = false,
    val carrierSafeMode: Boolean = true
) {
    companion object {
        const val DEFAULT_OTP_REGEX = "(?i)\\b(otp|one[- ]?time[- ]?password|verification[- ]?code|secret\\s+otp|security[- ]?code)\\b"
        const val DEFAULT_PREFIX = "[FWD from {sender}]: "
    }
}

class SettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val KEY_FORWARDING_ENABLED = booleanPreferencesKey("forwarding_enabled")
        val KEY_RECIPIENT_PHONE = stringPreferencesKey("recipient_phone")
        val KEY_REGEX_PATTERN = stringPreferencesKey("regex_pattern")
        val KEY_FILTER_MODE = stringPreferencesKey("filter_mode")
        val KEY_SENDER_LIST = stringSetPreferencesKey("sender_list")
        val KEY_SUBSCRIPTION_ID = intPreferencesKey("subscription_id")
        val KEY_CUSTOM_PREFIX = stringPreferencesKey("custom_prefix")
        val KEY_BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
        val KEY_MASK_SENSITIVE = booleanPreferencesKey("mask_sensitive")
        val KEY_CARRIER_SAFE_MODE = booleanPreferencesKey("carrier_safe_mode")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Log.e("SettingsRepository", "IOException reading datastore", exception)
                emit(emptyPreferences())
            } else {
                Log.e("SettingsRepository", "Unexpected error reading datastore", exception)
                emit(emptyPreferences())
            }
        }
        .map { preferences ->
        val filterModeStr = preferences[PreferencesKeys.KEY_FILTER_MODE] ?: FilterMode.DISABLED.name
        val filterMode = try {
            FilterMode.valueOf(filterModeStr)
        } catch (e: Exception) {
            FilterMode.DISABLED
        }

        UserSettings(
            isForwardingEnabled = preferences[PreferencesKeys.KEY_FORWARDING_ENABLED] ?: false,
            recipientPhoneNumber = preferences[PreferencesKeys.KEY_RECIPIENT_PHONE] ?: "",
            otpRegexPattern = preferences[PreferencesKeys.KEY_REGEX_PATTERN] ?: UserSettings.DEFAULT_OTP_REGEX,
            filterMode = filterMode,
            senderList = preferences[PreferencesKeys.KEY_SENDER_LIST] ?: emptySet(),
            selectedSubscriptionId = preferences[PreferencesKeys.KEY_SUBSCRIPTION_ID] ?: -1,
            customPrefix = preferences[PreferencesKeys.KEY_CUSTOM_PREFIX] ?: UserSettings.DEFAULT_PREFIX,
            isBiometricLockEnabled = preferences[PreferencesKeys.KEY_BIOMETRIC_LOCK] ?: false,
            maskSensitiveData = preferences[PreferencesKeys.KEY_MASK_SENSITIVE] ?: false,
            carrierSafeMode = preferences[PreferencesKeys.KEY_CARRIER_SAFE_MODE] ?: true
        )
    }

    suspend fun getSettingsSnapshot(): UserSettings = settingsFlow.first()

    suspend fun setForwardingEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_FORWARDING_ENABLED] = enabled
        }
    }

    suspend fun setRecipientPhoneNumber(phone: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_RECIPIENT_PHONE] = phone.trim()
        }
    }

    suspend fun setOtpRegexPattern(pattern: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_REGEX_PATTERN] = pattern
        }
    }

    suspend fun setFilterMode(mode: FilterMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_FILTER_MODE] = mode.name
        }
    }

    suspend fun addSender(sender: String) {
        val trimmed = sender.trim().uppercase()
        if (trimmed.isEmpty()) return
        context.dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.KEY_SENDER_LIST]?.toMutableSet() ?: mutableSetOf()
            current.add(trimmed)
            preferences[PreferencesKeys.KEY_SENDER_LIST] = current
        }
    }

    suspend fun removeSender(sender: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.KEY_SENDER_LIST]?.toMutableSet() ?: mutableSetOf()
            current.remove(sender.trim().uppercase())
            preferences[PreferencesKeys.KEY_SENDER_LIST] = current
        }
    }

    suspend fun setSelectedSubscriptionId(subId: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_SUBSCRIPTION_ID] = subId
        }
    }

    suspend fun setCustomPrefix(prefix: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_CUSTOM_PREFIX] = prefix
        }
    }

    suspend fun setBiometricLockEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_BIOMETRIC_LOCK] = enabled
        }
    }

    suspend fun setMaskSensitiveData(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_MASK_SENSITIVE] = enabled
        }
    }

    suspend fun setCarrierSafeMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_CARRIER_SAFE_MODE] = enabled
        }
    }
}
