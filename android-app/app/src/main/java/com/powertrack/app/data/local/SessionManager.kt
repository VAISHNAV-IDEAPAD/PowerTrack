package com.powertrack.app.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("powertrack_session_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_JWT_TOKEN = "jwt_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_TRACKING_INTERVAL = "tracking_interval_seconds"
        private const val KEY_TRACKING_ENABLED = "tracking_enabled"
        private const val KEY_HIGH_ACCURACY = "high_accuracy_enabled"
        private const val KEY_LOW_BATTERY_ARMED = "low_battery_trigger_armed"
        private const val KEY_LAST_LOW_BATTERY_TIME = "last_low_battery_trigger_time"
        private const val KEY_LAST_SUCCESSFUL_UPLOAD = "last_successful_upload_time"

        const val DEFAULT_SERVER_URL = "http://10.0.2.2:5000" // Standard Android Emulator host mapping
        const val DEFAULT_INTERVAL_SECONDS = 30
        const val LOW_BATTERY_TRIGGER_THRESHOLD = 10 // 10%
        const val LOW_BATTERY_RESET_THRESHOLD = 15   // 15%
    }

    private val _trackingEnabledFlow = MutableStateFlow(isTrackingEnabled)
    val trackingEnabledFlow: StateFlow<Boolean> = _trackingEnabledFlow.asStateFlow()

    private val _trackingIntervalFlow = MutableStateFlow(trackingIntervalSeconds)
    val trackingIntervalFlow: StateFlow<Int> = _trackingIntervalFlow.asStateFlow()

    var jwtToken: String?
        get() = prefs.getString(KEY_JWT_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_JWT_TOKEN, value).apply()

    var userId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        set(value) = prefs.edit().putString(KEY_USER_ID, value).apply()

    var userEmail: String?
        get() = prefs.getString(KEY_USER_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_USER_EMAIL, value).apply()

    var deviceId: String?
        get() = prefs.getString(KEY_DEVICE_ID, null)
        set(value) = prefs.edit().putString(KEY_DEVICE_ID, value).apply()

    var deviceName: String
        get() = prefs.getString(KEY_DEVICE_NAME, android.os.Build.MODEL ?: "Android Device") ?: "Android Device"
        set(value) = prefs.edit().putString(KEY_DEVICE_NAME, value).apply()

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        set(value) {
            val normalized = if (value.endsWith("/")) value.dropLast(1) else value
            prefs.edit().putString(KEY_SERVER_URL, normalized).apply()
        }

    var trackingIntervalSeconds: Int
        get() = prefs.getInt(KEY_TRACKING_INTERVAL, DEFAULT_INTERVAL_SECONDS)
        set(value) {
            prefs.edit().putInt(KEY_TRACKING_INTERVAL, value).apply()
            _trackingIntervalFlow.value = value
        }

    var isTrackingEnabled: Boolean
        get() = prefs.getBoolean(KEY_TRACKING_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_TRACKING_ENABLED, value).apply()
            _trackingEnabledFlow.value = value
        }

    var isHighAccuracyEnabled: Boolean
        get() = prefs.getBoolean(KEY_HIGH_ACCURACY, true)
        set(value) = prefs.edit().putBoolean(KEY_HIGH_ACCURACY, value).apply()

    var isLowBatteryTriggerArmed: Boolean
        get() = prefs.getBoolean(KEY_LOW_BATTERY_ARMED, true)
        set(value) = prefs.edit().putBoolean(KEY_LOW_BATTERY_ARMED, value).apply()

    var lastLowBatteryTriggerTime: Long
        get() = prefs.getLong(KEY_LAST_LOW_BATTERY_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_LOW_BATTERY_TIME, value).apply()

    var lastSuccessfulUploadTime: Long
        get() = prefs.getLong(KEY_LAST_SUCCESSFUL_UPLOAD, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SUCCESSFUL_UPLOAD, value).apply()

    val isLoggedIn: Boolean
        get() = !jwtToken.isNullOrBlank()

    fun saveLogin(token: String, id: String, email: String) {
        prefs.edit()
            .putString(KEY_JWT_TOKEN, token)
            .putString(KEY_USER_ID, id)
            .putString(KEY_USER_EMAIL, email)
            .apply()
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_JWT_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_USER_EMAIL)
            .putBoolean(KEY_TRACKING_ENABLED, false)
            .apply()
        _trackingEnabledFlow.value = false
    }
}
