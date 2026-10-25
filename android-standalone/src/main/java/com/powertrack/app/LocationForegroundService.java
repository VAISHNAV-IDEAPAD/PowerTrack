package com.powertrack.app;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import org.json.JSONArray;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

public class LocationForegroundService extends Service implements LocationListener {

    public static final String CHANNEL_ID = "powertrack_loc_channel";
    public static final int NOTIFICATION_ID = 101;
    public static final String ACTION_START = "com.powertrack.app.START";
    public static final String ACTION_STOP = "com.powertrack.app.STOP";

    private LocationManager locationManager;
    private DatabaseHelper dbHelper;
    private SharedPreferences prefs;

    @Override
    public void onCreate() {
        super.onCreate();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        dbHelper = DatabaseHelper.getInstance(this);
        prefs = getSharedPreferences("powertrack_prefs", MODE_PRIVATE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopTracking();
            stopForeground(true);
            stopSelf();
            prefs.edit().putBoolean("tracking_enabled", false).apply();
            return START_NOT_STICKY;
        }

        prefs.edit().putBoolean("tracking_enabled", true).apply();
        Notification notification = buildNotification("Monitoring active location...");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        startTracking();
        return START_STICKY;
    }

    @SuppressLint("MissingPermission")
    private void startTracking() {
        long minTime = prefs.getInt("interval_seconds", 30) * 1000L;
        float minDistance = 0f;

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, minTime, minDistance, this);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, minTime, minDistance, this);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopTracking() {
        try {
            locationManager.removeUpdates(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onLocationChanged(Location loc) {
        if (loc == null) return;
        double lat = loc.getLatitude();
        double lon = loc.getLongitude();

        // Reject invalid coordinates & null island
        if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) return;
        if (lat == 0.0 && lon == 0.0) return;

        int battery = getBatteryLevel();
        String eventId = UUID.randomUUID().toString();
        String deviceId = prefs.getString("device_id", "android-device");
        String baseUrl = prefs.getString("server_url", "https://assured-entirely-density-processor.trycloudflare.com");
        String token = prefs.getString("jwt_token", null);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String recordedAtUtc = sdf.format(new Date(loc.getTime() > 0 ? loc.getTime() : System.currentTimeMillis()));

        Double alt = loc.hasAltitude() ? loc.getAltitude() : null;
        Float spd = loc.hasSpeed() ? loc.getSpeed() : null;
        Float brg = loc.hasBearing() ? loc.getBearing() : null;
        String source = loc.getProvider() != null ? loc.getProvider() : "gps";

        // 1. Commit to SQLite (Room-equivalent) with PENDING
        dbHelper.insertLocation(eventId, deviceId, lat, lon, loc.getAccuracy(), alt, spd, brg, battery, recordedAtUtc, source, true);

        // 2. Upload pending asynchronously
        new Thread(() -> {
            try {
                if (token != null && !token.isEmpty()) {
                    JSONArray pending = dbHelper.getPendingLocationsJson(50);
                    if (pending.length() > 0) {
                        ApiClient.uploadLocations(baseUrl, token, deviceId, pending);
                        List<String> eventIds = new ArrayList<>();
                        for (int i = 0; i < pending.length(); i++) {
                            eventIds.add(pending.getJSONObject(i).getString("event_id"));
                        }
                        String uploadedAt = sdf.format(new Date());
                        dbHelper.markAsUploaded(eventIds, uploadedAt);
                    }
                }
            } catch (Exception e) {
                // Kept as PENDING in local SQLite
            }
        }).start();

        updateNotification("Lat: " + String.format(Locale.US, "%.4f", lat) + " Lon: " + String.format(Locale.US, "%.4f", lon) + " (±" + (int)loc.getAccuracy() + "m)");
    }

    private int getBatteryLevel() {
        try {
            android.content.IntentFilter ifilter = new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED);
            android.content.Intent bIntent = registerReceiver(null, ifilter);
            if (bIntent != null) {
                int level = bIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = bIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) {
                    return (int)((level * 100f) / scale);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 50;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel chan = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            chan.setDescription(getString(R.string.channel_desc));
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(chan);
        }
    }

    private Notification buildNotification(String text) {
        Intent notifIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, notifIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setContentTitle(getString(R.string.notification_title))
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(pi)
                .setOngoing(true);

        return builder.build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification(text));
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}
    @Override
    public void onProviderEnabled(String provider) {}
    @Override
    public void onProviderDisabled(String provider) {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopTracking();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
