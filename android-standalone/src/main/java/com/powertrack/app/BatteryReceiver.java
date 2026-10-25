package com.powertrack.app;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.os.BatteryManager;
import org.json.JSONArray;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

public class BatteryReceiver extends BroadcastReceiver {

    @SuppressLint("MissingPermission")
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        SharedPreferences prefs = context.getSharedPreferences("powertrack_prefs", Context.MODE_PRIVATE);
        DatabaseHelper dbHelper = DatabaseHelper.getInstance(context);

        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int batteryPct = (level >= 0 && scale > 0) ? (int)((level * 100f) / scale) : 50;

        boolean isArmed = prefs.getBoolean("battery_trigger_armed", true);

        // Hysteresis: re-arm if charged above 15%
        if (batteryPct >= 15) {
            if (!isArmed) {
                prefs.edit().putBoolean("battery_trigger_armed", true).apply();
            }
            return;
        }

        // Emergency 10% trigger
        if (batteryPct <= 10 && isArmed) {
            prefs.edit().putBoolean("battery_trigger_armed", false).apply();

            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            Location bestLoc = null;
            try {
                Location gpsLoc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                Location netLoc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (gpsLoc != null) bestLoc = gpsLoc;
                else if (netLoc != null) bestLoc = netLoc;
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (bestLoc != null) {
                String eventId = UUID.randomUUID().toString();
                String deviceId = prefs.getString("device_id", "android-device");
                String baseUrl = prefs.getString("server_url", "http://192.168.31.19:5000");
                String token = prefs.getString("jwt_token", null);

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                String recordedAtUtc = sdf.format(new Date(bestLoc.getTime() > 0 ? bestLoc.getTime() : System.currentTimeMillis()));

                dbHelper.insertLocation(
                        eventId, deviceId, bestLoc.getLatitude(), bestLoc.getLongitude(),
                        bestLoc.getAccuracy(),
                        bestLoc.hasAltitude() ? bestLoc.getAltitude() : null,
                        bestLoc.hasSpeed() ? bestLoc.getSpeed() : null,
                        bestLoc.hasBearing() ? bestLoc.getBearing() : null,
                        batteryPct, recordedAtUtc, "emergency_battery_trigger", false
                );

                if (token != null && !token.isEmpty()) {
                    new Thread(() -> {
                        try {
                            JSONArray pending = dbHelper.getPendingLocationsJson(20);
                            if (pending.length() > 0) {
                                ApiClient.uploadLocations(baseUrl, token, deviceId, pending);
                                List<String> eventIds = new ArrayList<>();
                                for (int i = 0; i < pending.length(); i++) {
                                    eventIds.add(pending.getJSONObject(i).getString("event_id"));
                                }
                                dbHelper.markAsUploaded(eventIds, sdf.format(new Date()));
                            }
                        } catch (Exception e) {
                            // Saved in DB
                        }
                    }).start();
                }
            }
        }
    }
}
