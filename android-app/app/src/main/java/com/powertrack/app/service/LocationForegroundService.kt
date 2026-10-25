package com.powertrack.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.powertrack.app.MainActivity
import com.powertrack.app.R
import com.powertrack.app.data.local.SessionManager
import com.powertrack.app.data.model.LocationEntity
import com.powertrack.app.data.repository.LocationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class LocationForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var sessionManager: SessionManager
    private lateinit var locationRepository: LocationRepository

    companion object {
        const val CHANNEL_ID = "powertrack_location_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.powertrack.app.action.START"
        const val ACTION_STOP = "com.powertrack.app.action.STOP"

        fun startService(context: Context) {
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        sessionManager = SessionManager(this)
        locationRepository = LocationRepository(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        createNotificationChannel()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    if (location != null && LocationRepository.isValidCoordinate(location.latitude, location.longitude)) {
                        handleNewLocation(location)
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                sessionManager.isTrackingEnabled = false
                stopTracking()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                sessionManager.isTrackingEnabled = true
                val notification = buildNotification("PowerTrack Location Service Active")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                startTracking()
            }
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startTracking() {
        val intervalSeconds = sessionManager.trackingIntervalSeconds
        val intervalMillis = intervalSeconds * 1000L

        val priority = if (sessionManager.isHighAccuracyEnabled) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        val locationRequest = LocationRequest.Builder(priority, intervalMillis)
            .setMinUpdateIntervalMillis(intervalMillis / 2)
            .setMaxUpdateDelayMillis(intervalMillis * 2)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: Exception) {
            // Missing permission or disabled provider
        }
    }

    private fun stopTracking() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            // Error removing updates
        }
    }

    private fun handleNewLocation(location: android.location.Location) {
        val deviceId = sessionManager.deviceId ?: return
        val batteryPct = locationRepository.getBatteryPercentage()

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val recordedAtUtc = sdf.format(Date(location.time))

        val entity = LocationEntity(
            eventId = UUID.randomUUID().toString(),
            deviceId = deviceId,
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = location.accuracy,
            altitudeMeters = if (location.hasAltitude()) location.altitude else null,
            speedMetersPerSecond = if (location.hasSpeed()) location.speed else null,
            bearingDegrees = if (location.hasBearing()) location.bearing else null,
            batteryPercentage = batteryPct,
            recordedAtUtc = recordedAtUtc,
            locationSource = location.provider ?: "fused",
            isFreshLocation = true,
            uploadStatus = LocationEntity.STATUS_PENDING
        )

        serviceScope.launch {
            locationRepository.recordAndUploadLocation(entity)
            updateNotification("Last updated: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())} (Acc: ${entity.accuracyMeters.toInt()}m)")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LocationForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Tracking", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTracking()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
