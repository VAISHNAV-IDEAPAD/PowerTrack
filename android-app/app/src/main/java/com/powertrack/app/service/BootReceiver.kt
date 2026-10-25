package com.powertrack.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.powertrack.app.data.local.SessionManager

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val sessionManager = SessionManager(context)
            // Resume location tracking only if tracking was enabled and device is authenticated
            if (sessionManager.isLoggedIn && sessionManager.isTrackingEnabled) {
                try {
                    LocationForegroundService.startService(context)
                } catch (e: Exception) {
                    // Android background launch restrictions might prevent service start
                    // if permissions or background execution limits are enforced by OEM
                }
            }
        }
    }
}
