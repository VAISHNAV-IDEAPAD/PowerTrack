package com.powertrack.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.powertrack.app.R
import com.powertrack.app.data.local.SessionManager
import com.powertrack.app.data.repository.LocationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BatteryReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val BATTERY_CHANNEL_ID = "powertrack_battery_alerts"
        const val BATTERY_NOTIFICATION_ID = 2002
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val sessionManager = SessionManager(context)
        val locationRepository = LocationRepository(context)

        // Read battery percentage
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val batteryPct = if (level >= 0 && scale > 0) {
            (level * 100 / scale.toFloat()).toInt()
        } else {
            locationRepository.getBatteryPercentage()
        }

        // 1. Check if battery charged above reset threshold (15%)
        if (batteryPct >= SessionManager.LOW_BATTERY_RESET_THRESHOLD) {
            if (!sessionManager.isLowBatteryTriggerArmed) {
                sessionManager.isLowBatteryTriggerArmed = true
            }
            return
        }

        // 2. Check if low-battery threshold (<= 10%) is reached and trigger is armed
        if (batteryPct <= SessionManager.LOW_BATTERY_TRIGGER_THRESHOLD && sessionManager.isLowBatteryTriggerArmed) {
            // Disarm trigger immediately to prevent duplicate runs
            sessionManager.isLowBatteryTriggerArmed = false
            sessionManager.lastLowBatteryTriggerTime = System.currentTimeMillis()

            showLowBatteryNotification(context, batteryPct)

            receiverScope.launch {
                executeLowBatteryWorkflow(locationRepository, batteryPct)
            }
        }
    }

    private suspend fun executeLowBatteryWorkflow(
        locationRepository: LocationRepository,
        batteryPct: Int
    ) {
        // Attempt fresh high-accuracy location fix (15-second timeout)
        val loc = locationRepository.obtainLocation(timeoutMs = 15000L)
        if (loc != null) {
            // Store in Room first, then upload
            locationRepository.recordAndUploadLocation(loc)
        }
    }

    private fun showLowBatteryNotification(context: Context, batteryPct: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                BATTERY_CHANNEL_ID,
                context.getString(R.string.battery_notification_channel),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.battery_notification_desc)
            }
            manager?.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, BATTERY_CHANNEL_ID)
            .setContentTitle("Emergency Low Battery Trigger (Battery at $batteryPct%)")
            .setContentText("Capturing and uploading final location coordinates before device shutdown.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager?.notify(BATTERY_NOTIFICATION_ID, notification)
    }
}
