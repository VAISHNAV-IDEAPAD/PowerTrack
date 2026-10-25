package com.powertrack.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "locations",
    indices = [
        Index(value = ["event_id"], unique = true),
        Index(value = ["recorded_at_utc"]),
        Index(value = ["upload_status"])
    ]
)
data class LocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "event_id")
    val eventId: String,

    @ColumnInfo(name = "device_id")
    val deviceId: String,

    @ColumnInfo(name = "latitude")
    val latitude: Double,

    @ColumnInfo(name = "longitude")
    val longitude: Double,

    @ColumnInfo(name = "accuracy_meters")
    val accuracyMeters: Float,

    @ColumnInfo(name = "altitude_meters")
    val altitudeMeters: Double? = null,

    @ColumnInfo(name = "speed_meters_per_second")
    val speedMetersPerSecond: Float? = null,

    @ColumnInfo(name = "bearing_degrees")
    val bearingDegrees: Float? = null,

    @ColumnInfo(name = "battery_percentage")
    val batteryPercentage: Int,

    @ColumnInfo(name = "recorded_at_utc")
    val recordedAtUtc: String,

    @ColumnInfo(name = "uploaded_at_utc")
    val uploadedAtUtc: String? = null,

    @ColumnInfo(name = "location_source")
    val locationSource: String = "fused",

    @ColumnInfo(name = "is_fresh_location")
    val isFreshLocation: Boolean = true,

    @ColumnInfo(name = "upload_status")
    val uploadStatus: String = STATUS_PENDING
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_UPLOADING = "UPLOADING"
        const val STATUS_UPLOADED = "UPLOADED"
        const val STATUS_FAILED = "FAILED"
    }
}
