package com.powertrack.app.data.model

import com.google.gson.annotations.SerializedName

// Authentication
data class AuthRequest(
    val email: String,
    val password: String
)

data class AuthResponse(
    val message: String,
    val token: String,
    val user: UserDto
)

data class UserDto(
    val id: String,
    val email: String,
    @SerializedName("created_at")
    val createdAt: String
)

// Device Registration
data class DeviceRegisterRequest(
    @SerializedName("device_name")
    val deviceName: String,
    val platform: String = "android",
    @SerializedName("device_id")
    val deviceId: String? = null
)

data class DeviceRegisterResponse(
    val message: String,
    val device: DeviceDto
)

data class DeviceDto(
    val id: String,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("device_name")
    val deviceName: String,
    val platform: String,
    @SerializedName("registered_at")
    val registeredAt: String,
    @SerializedName("last_seen_at")
    val lastSeenAt: String? = null,
    @SerializedName("last_battery_percentage")
    val lastBatteryPercentage: Int? = null,
    @SerializedName("tracking_enabled")
    val trackingEnabled: Int = 1
)

// Locations
data class LocationDto(
    @SerializedName("event_id")
    val eventId: String,
    val latitude: Double,
    val longitude: Double,
    @SerializedName("accuracy_meters")
    val accuracyMeters: Float,
    @SerializedName("altitude_meters")
    val altitudeMeters: Double? = null,
    @SerializedName("speed_meters_per_second")
    val speedMetersPerSecond: Float? = null,
    @SerializedName("bearing_degrees")
    val bearingDegrees: Float? = null,
    @SerializedName("battery_percentage")
    val batteryPercentage: Int,
    @SerializedName("recorded_at_utc")
    val recordedAtUtc: String,
    @SerializedName("location_source")
    val locationSource: String = "fused",
    @SerializedName("is_fresh_location")
    val isFreshLocation: Boolean = true
)

data class BatchLocationUploadRequest(
    val locations: List<LocationDto>
)

data class LocationUploadResponse(
    val message: String,
    @SerializedName("device_id")
    val deviceId: String,
    val processed: Int,
    val inserted: Int,
    val duplicates: Int,
    @SerializedName("received_at_utc")
    val receivedAtUtc: String,
    @SerializedName("last_battery_percentage")
    val lastBatteryPercentage: Int?
)

// Heartbeat
data class HeartbeatRequest(
    @SerializedName("battery_percentage")
    val batteryPercentage: Int? = null
)

data class HeartbeatResponse(
    val message: String,
    @SerializedName("device_id")
    val deviceId: String,
    @SerializedName("last_seen_at")
    val lastSeenAt: String
)

// Settings
data class SettingsUpdateRequest(
    @SerializedName("tracking_enabled")
    val trackingEnabled: Boolean? = null,
    @SerializedName("device_name")
    val deviceName: String? = null
)

data class SettingsUpdateResponse(
    val message: String,
    val device: DeviceDto
)

// Generic API Error
data class ApiErrorResponse(
    val error: String,
    val details: List<String>? = null
)
