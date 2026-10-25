package com.powertrack.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
            Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) ||
            "android.intent.action.QUICKBOOT_POWERON".equals(action)) {

            SharedPreferences prefs = context.getSharedPreferences("powertrack_prefs", Context.MODE_PRIVATE);
            boolean isTracking = prefs.getBoolean("tracking_enabled", false);
            String token = prefs.getString("jwt_token", null);

            if (isTracking && token != null && !token.isEmpty()) {
                Intent svcIntent = new Intent(context, LocationForegroundService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(svcIntent);
                } else {
                    context.startService(svcIntent);
                }
            }
        }
    }
}
