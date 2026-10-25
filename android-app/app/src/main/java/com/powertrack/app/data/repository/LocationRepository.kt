package com.powertrack.app.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.BatteryManager
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.powertrack.app.data.local.PowerTrackDatabase
import com.powertrack.app.data.local.SessionManager
import com.powertrack.app.data.model.BatchLocationUploadRequest
import com.powertrack.app.data.model.LocationDto
import com.powertrack.app.data.model.LocationEntity
import com.powertrack.app.data.remote.NetworkClient
import com.powertrack.app.worker.LocationSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class LocationRepository(private val context: Context) {

    private val db = PowerTrackDatabase.getInstance(context)
    private val locationDao = db.locationDao()
    private val sessionManager = SessionManager(context)
    private val networkClient = NetworkClient.getInstance(context)
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    val latestLocationFlow: Flow<LocationEntity?> = locationDao.getLatestLocationFlow()
    val pendingCountFlow: Flow<Int> = locationDao.getPendingCountFlow()
    val allLocationsFlow: Flow<List<LocationEntity>> = locationDao.getAllLocationsFlow()

    fun getBatteryPercentage(): Int {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, intentFilter)
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) {
            (level * 100 / scale.toFloat()).toInt()
        } else {
            50 // Fallback if battery info cannot be read
        }
    }

    private fun getUtcIsoTimestamp(date: Date = Date()): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(date)
    }

    /**
     * Obtains a fresh high-accuracy location fix using FusedLocationProviderClient.
     * If obtaining a fresh location times out or fails, falls back to getLastLocation() marked as cached.
     */
    @SuppressLint("MissingPermission")
    suspend fun obtainLocation(timeoutMs: Long = 15000L): LocationEntity? =
        withContext(Dispatchers.IO) {
            val deviceId = sessionManager.deviceId ?: return@withContext null
            val batteryPct = getBatteryPercentage()
            val cts = CancellationTokenSource()

            var freshLocation: Location? = null
            try {
                freshLocation = withTimeoutOrNull(timeoutMs) {
                    val priority = if (sessionManager.isHighAccuracyEnabled) {
                        Priority.PRIORITY_HIGH_ACCURACY
                    } else {
                        Priority.PRIORITY_BALANCED_POWER_ACCURACY
                    }
                    fusedLocationClient.getCurrentLocation(priority, cts.token).await()
                }
            } catch (e: Exception) {
                // Timeout or permission/provider failure
            }

            if (freshLocation != null && isValidCoordinate(freshLocation.latitude, freshLocation.longitude)) {
                return@withContext LocationEntity(
                    eventId = UUID.randomUUID().toString(),
                    deviceId = deviceId,
                    latitude = freshLocation.latitude,
                    longitude = freshLocation.longitude,
                    accuracyMeters = freshLocation.accuracy,
                    altitudeMeters = if (freshLocation.hasAltitude()) freshLocation.altitude else null,
                    speedMetersPerSecond = if (freshLocation.hasSpeed()) freshLocation.speed else null,
                    bearingDegrees = if (freshLocation.hasBearing()) freshLocation.bearing else null,
                    batteryPercentage = batteryPct,
                    recordedAtUtc = getUtcIsoTimestamp(Date(freshLocation.time)),
                    locationSource = freshLocation.provider ?: "fused",
                    isFreshLocation = true,
                    uploadStatus = LocationEntity.STATUS_PENDING
                )
            }

            // Fall back to last known location, explicitly marked as cached (isFreshLocation = false)
            try {
                val cachedLoc = fusedLocationClient.lastLocation.await()
                if (cachedLoc != null && isValidCoordinate(cachedLoc.latitude, cachedLoc.longitude)) {
                    return@withContext LocationEntity(
                        eventId = UUID.randomUUID().toString(),
                        deviceId = deviceId,
                        latitude = cachedLoc.latitude,
                        longitude = cachedLoc.longitude,
                        accuracyMeters = cachedLoc.accuracy,
                        altitudeMeters = if (cachedLoc.hasAltitude()) cachedLoc.altitude else null,
                        speedMetersPerSecond = if (cachedLoc.hasSpeed()) cachedLoc.speed else null,
                        bearingDegrees = if (cachedLoc.hasBearing()) cachedLoc.bearing else null,
                        batteryPercentage = batteryPct,
                        recordedAtUtc = getUtcIsoTimestamp(Date(cachedLoc.time)),
                        locationSource = cachedLoc.provider ?: "cached",
                        isFreshLocation = false,
                        uploadStatus = LocationEntity.STATUS_PENDING
                    )
                }
            } catch (e: Exception) {
                // Last location unavailable
            }

            null
        }

    /**
     * Stores the location in local Room database first, then attempts network upload.
     * If network fails or is offline, schedules WorkManager sync.
     */
    suspend fun recordAndUploadLocation(location: LocationEntity): Boolean =
        withContext(Dispatchers.IO) {
            // 1. Save locally in Room first (requirement: never lose data, mark PENDING)
            locationDao.insertLocation(location)

            // 2. Attempt immediate upload
            val success = tryUploadBatch(listOf(location))
            if (!success) {
                // 3. Schedule WorkManager for background sync when network is restored
                scheduleSyncWork()
            }
            success
        }

    /**
     * Uploads pending records in chronological order.
     */
    suspend fun uploadPendingLocations(): Int =
        withContext(Dispatchers.IO) {
            val pending = locationDao.getPendingLocations(limit = 100)
            if (pending.isEmpty()) return@withContext 0

            val success = tryUploadBatch(pending)
            if (success) pending.size else 0
        }

    private suspend fun tryUploadBatch(entities: List<LocationEntity>): Boolean {
        val deviceId = sessionManager.deviceId ?: return false
        val token = sessionManager.jwtToken ?: return false

        try {
            val dtos = entities.map { entity ->
                LocationDto(
                    eventId = entity.eventId,
                    latitude = entity.latitude,
                    longitude = entity.longitude,
                    accuracyMeters = entity.accuracyMeters,
                    altitudeMeters = entity.altitudeMeters,
                    speedMetersPerSecond = entity.speedMetersPerSecond,
                    bearingDegrees = entity.bearingDegrees,
                    batteryPercentage = entity.batteryPercentage,
                    recordedAtUtc = entity.recordedAtUtc,
                    locationSource = entity.locationSource,
                    isFreshLocation = entity.isFreshLocation
                )
            }

            val response = networkClient.api.uploadLocations(
                deviceId = deviceId,
                request = BatchLocationUploadRequest(dtos)
            )

            if (response.isSuccessful) {
                val nowUtc = getUtcIsoTimestamp()
                val eventIds = entities.map { it.eventId }
                locationDao.markAsUploaded(eventIds, nowUtc)
                sessionManager.lastSuccessfulUploadTime = System.currentTimeMillis()
                return true
            }
        } catch (e: Exception) {
            // Network failure: records remain in Room as PENDING
        }
        return false
    }

    fun scheduleSyncWork() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<LocationSyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "powertrack_location_sync",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }

    /**
     * Manual Refresh Location action requested by the user.
     */
    suspend fun refreshLocationNow(): Result<LocationEntity> =
        withContext(Dispatchers.IO) {
            val loc = obtainLocation(timeoutMs = 15000L)
            if (loc != null) {
                recordAndUploadLocation(loc)
                Result.success(loc)
            } else {
                Result.failure(Exception("Failed to obtain accurate GPS fix within timeout. Check GPS signal and permissions."))
            }
        }

    companion object {
        fun isValidCoordinate(lat: Double, lon: Double): Boolean {
            if (lat.isNaN() || lon.isNaN()) return false
            if (lat < -90.0 || lat > 90.0) return false
            if (lon < -180.0 || lon > 180.0) return false
            // Null Island / false GPS fix check
            if (lat == 0.0 && lon == 0.0) return false
            return true
        }
    }
}
