package com.powertrack.app

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import com.powertrack.app.service.BatteryReceiver

class PowerTrackApp : Application() {

    private val batteryReceiver = BatteryReceiver()

    override fun onCreate() {
        super.onCreate()

        // Register dynamic battery receiver for continuous ACTION_BATTERY_CHANGED monitoring
        val batteryFilter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
        }
        registerReceiver(batteryReceiver, batteryFilter)
    }
}
