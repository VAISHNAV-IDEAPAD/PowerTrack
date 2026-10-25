package com.powertrack.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

public class MainActivity extends Activity implements LocationListener {

    public static final String DEFAULT_SERVER_URL = "http://192.168.31.19:5000";

    public static String cleanUrl(String input) {
        if (input == null) return DEFAULT_SERVER_URL;
        String trimmed = input.trim();
        if (trimmed.isEmpty()) return DEFAULT_SERVER_URL;
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "http://" + trimmed;
        }
        return trimmed.replaceAll("/+$", "");
    }

    private SharedPreferences prefs;
    private DatabaseHelper dbHelper;
    private LocationManager locationManager;
    private Handler mainHandler;

    private Switch switchTracking;
    private EditText etServerUrl;
    private Button btnSaveServer;
    private TextView tvAuthStatus;
    private EditText etEmail, etPassword;
    private Button btnLogin, btnRegister;
    private TextView tvBatteryPct, tvBatteryTriggerStatus;
    private TextView tvAccuracyBadge, tvCoordinates, tvAccuracy, tvTimestamp, tvKinematics;
    private Button btnRefreshLocation, btnForceSync;
    private TextView tvPendingCount, tvUploadStatus, tvLog;

    private BroadcastReceiver dynamicBatteryReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("powertrack_prefs", MODE_PRIVATE);
        dbHelper = DatabaseHelper.getInstance(this);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        mainHandler = new Handler(Looper.getMainLooper());

        initViews();
        checkAndRequestPermissions();
        registerBatteryMonitor();
        updateUIState();
    }

    private void initViews() {
        switchTracking = findViewById(R.id.switchTracking);
        etServerUrl = findViewById(R.id.etServerUrl);
        btnSaveServer = findViewById(R.id.btnSaveServer);
        tvAuthStatus = findViewById(R.id.tvAuthStatus);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);
        tvBatteryPct = findViewById(R.id.tvBatteryPct);
        tvBatteryTriggerStatus = findViewById(R.id.tvBatteryTriggerStatus);
        tvAccuracyBadge = findViewById(R.id.tvAccuracyBadge);
        tvCoordinates = findViewById(R.id.tvCoordinates);
        tvAccuracy = findViewById(R.id.tvAccuracy);
        tvTimestamp = findViewById(R.id.tvTimestamp);
        tvKinematics = findViewById(R.id.tvKinematics);
        btnRefreshLocation = findViewById(R.id.btnRefreshLocation);
        btnForceSync = findViewById(R.id.btnForceSync);
        tvPendingCount = findViewById(R.id.tvPendingCount);
        tvUploadStatus = findViewById(R.id.tvUploadStatus);
        tvLog = findViewById(R.id.tvLog);

        String savedServer = prefs.getString("server_url", DEFAULT_SERVER_URL);
        if (savedServer.contains("10.0.2.2") || savedServer.trim().isEmpty()) {
            savedServer = DEFAULT_SERVER_URL;
            prefs.edit().putString("server_url", savedServer).apply();
        }
        etServerUrl.setText(savedServer);

        btnSaveServer.setOnClickListener(v -> {
            String raw = etServerUrl.getText().toString();
            String cleaned = cleanUrl(raw);
            etServerUrl.setText(cleaned);
            prefs.edit().putString("server_url", cleaned).apply();
            log("[Config] Server URL updated to " + cleaned);
            Toast.makeText(this, "Server URL set to " + cleaned, Toast.LENGTH_SHORT).show();
        });

        btnLogin.setOnClickListener(v -> performAuth(false));
        btnRegister.setOnClickListener(v -> performAuth(true));

        switchTracking.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Intent intent = new Intent(this, LocationForegroundService.class);
            if (isChecked) {
                intent.setAction(LocationForegroundService.ACTION_START);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent);
                } else {
                    startService(intent);
                }
                log("[Service] Foreground location tracking started.");
            } else {
                intent.setAction(LocationForegroundService.ACTION_STOP);
                startService(intent);
                log("[Service] Foreground location tracking stopped.");
            }
        });

        btnRefreshLocation.setOnClickListener(v -> refreshLocationManual());
        btnForceSync.setOnClickListener(v -> syncPendingRecordsManual());
    }

    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            List<String> perms = new ArrayList<>();
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    perms.add(Manifest.permission.POST_NOTIFICATIONS);
                }
            }
            if (!perms.isEmpty()) {
                requestPermissions(perms.toArray(new String[0]), 101);
            }
        }
    }

    private void performAuth(boolean isRegister) {
        String email = etEmail.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();
        String rawBaseUrl = etServerUrl.getText().toString().trim();
        String baseUrl = cleanUrl(rawBaseUrl);
        etServerUrl.setText(baseUrl);
        prefs.edit().putString("server_url", baseUrl).apply();

        if (email.isEmpty() || pass.length() < 8) {
            Toast.makeText(this, "Enter valid email and password (min 8 chars)", Toast.LENGTH_SHORT).show();
            return;
        }

        log("[Auth] Connecting to " + baseUrl + " for " + (isRegister ? "registration" : "login") + "...");
        new Thread(() -> {
            try {
                JSONObject res;
                if (isRegister) {
                    res = ApiClient.register(baseUrl, email, pass);
                } else {
                    res = ApiClient.login(baseUrl, email, pass);
                }

                String token = res.getString("token");
                String userId = res.getJSONObject("user").getString("id");

                String deviceId = prefs.getString("device_id", UUID.randomUUID().toString());
                prefs.edit().putString("device_id", deviceId)
                        .putString("jwt_token", token)
                        .putString("user_email", email)
                        .putString("user_id", userId)
                        .apply();

                // Register device
                String model = Build.MANUFACTURER + " " + Build.MODEL;
                ApiClient.registerDevice(baseUrl, token, model, deviceId);

                mainHandler.post(() -> {
                    log("[Auth] Successfully authenticated! Device linked.");
                    Toast.makeText(this, "Authenticated successfully!", Toast.LENGTH_SHORT).show();
                    updateUIState();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    String err = e.getMessage() != null ? e.getMessage() : "Unknown error";
                    log("[Auth Error] " + err);
                    if (isRegister && (err.contains("already exists") || err.contains("409"))) {
                        Toast.makeText(this, "Account exists! Tap 'Sign In' instead of 'Register'.", Toast.LENGTH_LONG).show();
                        log("[Auth Tip] Account already exists. Use 'Sign In' with this email.");
                    } else if (!isRegister && (err.contains("Invalid email or password") || err.contains("401"))) {
                        Toast.makeText(this, "Invalid credentials. If new, tap 'Register' first.", Toast.LENGTH_LONG).show();
                    } else if (err.contains("Failed to connect") || err.contains("ConnectException") || err.contains("timeout") || err.contains("ECONNREFUSED") || err.contains("unreachable")) {
                        Toast.makeText(this, "Cannot reach " + baseUrl + ". Verify Wi-Fi connection.", Toast.LENGTH_LONG).show();
                        log("[Connection Tip] Ensure phone & PC are on the same Wi-Fi network.");
                    } else {
                        Toast.makeText(this, "Auth error: " + err, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    @SuppressLint("MissingPermission")
    private void refreshLocationManual() {
        log("[GPS] Requesting fresh high-accuracy location fix...");
        btnRefreshLocation.setEnabled(false);

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, this, Looper.getMainLooper());
            } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, this, Looper.getMainLooper());
            } else {
                log("[GPS Error] Location providers disabled. Enable GPS in Android Settings.");
                btnRefreshLocation.setEnabled(true);
            }
        } catch (Exception e) {
            log("[GPS Error] " + e.getMessage());
            btnRefreshLocation.setEnabled(true);
        }
    }

    @Override
    public void onLocationChanged(Location loc) {
        btnRefreshLocation.setEnabled(true);
        if (loc == null) return;

        double lat = loc.getLatitude();
        double lon = loc.getLongitude();
        float acc = loc.getAccuracy();

        tvCoordinates.setText("Coordinates: " + String.format(Locale.US, "%.5f", lat) + ", " + String.format(Locale.US, "%.5f", lon));
        tvAccuracy.setText("Accuracy: ±" + String.format(Locale.US, "%.1f", acc) + " metres");

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String timeStr = sdf.format(new Date(loc.getTime() > 0 ? loc.getTime() : System.currentTimeMillis()));
        tvTimestamp.setText("Recorded (UTC): " + timeStr);

        float spd = loc.hasSpeed() ? loc.getSpeed() * 3.6f : 0f;
        float brg = loc.hasBearing() ? loc.getBearing() : 0f;
        tvKinematics.setText("Speed: " + String.format(Locale.US, "%.1f", spd) + " km/h | Bearing: " + (int)brg + "°");

        if (acc <= 15) {
            tvAccuracyBadge.setText("High Accuracy (±" + (int)acc + "m)");
            tvAccuracyBadge.setTextColor(0xFF16A34A);
        } else if (acc <= 50) {
            tvAccuracyBadge.setText("Moderate Accuracy (±" + (int)acc + "m)");
            tvAccuracyBadge.setTextColor(0xFFD97706);
        } else {
            tvAccuracyBadge.setText("Low Accuracy (±" + (int)acc + "m)");
            tvAccuracyBadge.setTextColor(0xFFDC2626);
        }

        log("[GPS] Acquired fix: " + lat + ", " + lon + " (±" + (int)acc + "m)");

        // Save in DB
        String eventId = UUID.randomUUID().toString();
        String deviceId = prefs.getString("device_id", "android-device");
        int battery = getBatteryLevel();

        dbHelper.insertLocation(eventId, deviceId, lat, lon, acc,
                loc.hasAltitude() ? loc.getAltitude() : null,
                loc.hasSpeed() ? loc.getSpeed() : null,
                loc.hasBearing() ? loc.getBearing() : null,
                battery, timeStr, loc.getProvider() != null ? loc.getProvider() : "gps", true);

        updatePendingCount();

        // Attempt upload
        String token = prefs.getString("jwt_token", null);
        String baseUrl = cleanUrl(prefs.getString("server_url", DEFAULT_SERVER_URL));
        if (token != null && !token.isEmpty()) {
            new Thread(() -> {
                try {
                    JSONArray pending = dbHelper.getPendingLocationsJson(20);
                    ApiClient.uploadLocations(baseUrl, token, deviceId, pending);
                    List<String> ids = new ArrayList<>();
                    for (int i = 0; i < pending.length(); i++) {
                        ids.add(pending.getJSONObject(i).getString("event_id"));
                    }
                    dbHelper.markAsUploaded(ids, timeStr);
                    mainHandler.post(() -> {
                        log("[Sync] Fix successfully uploaded to server!");
                        updatePendingCount();
                    });
                } catch (Exception e) {
                    mainHandler.post(() -> log("[Sync] Offline. Stored in local DB queue."));
                }
            }).start();
        }
    }

    private void syncPendingRecordsManual() {
        String token = prefs.getString("jwt_token", null);
        String baseUrl = cleanUrl(prefs.getString("server_url", DEFAULT_SERVER_URL));
        String deviceId = prefs.getString("device_id", "android-device");

        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please sign in first to sync records.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnForceSync.setEnabled(false);
        log("[Sync] Synchronizing pending offline records...");
        new Thread(() -> {
            try {
                JSONArray pending = dbHelper.getPendingLocationsJson(50);
                if (pending.length() == 0) {
                    mainHandler.post(() -> {
                        log("[Sync] Queue is already empty.");
                        btnForceSync.setEnabled(true);
                    });
                    return;
                }
                ApiClient.uploadLocations(baseUrl, token, deviceId, pending);
                List<String> ids = new ArrayList<>();
                for (int i = 0; i < pending.length(); i++) {
                    ids.add(pending.getJSONObject(i).getString("event_id"));
                }
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                dbHelper.markAsUploaded(ids, sdf.format(new Date()));

                mainHandler.post(() -> {
                    log("[Sync] Successfully synchronized " + ids.size() + " records!");
                    updatePendingCount();
                    btnForceSync.setEnabled(true);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    log("[Sync Error] " + e.getMessage());
                    btnForceSync.setEnabled(true);
                });
            }
        }).start();
    }

    private void updatePendingCount() {
        int pending = dbHelper.getPendingCount();
        tvPendingCount.setText("Offline Queue: " + pending + " pending");
        tvUploadStatus.setText(pending > 0 ? "Stored locally. Will upload when online." : "All records synchronized with cloud.");
    }

    private void registerBatteryMonitor() {
        dynamicBatteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                int pct = (level >= 0 && scale > 0) ? (int)((level * 100f) / scale) : 50;

                tvBatteryPct.setText(pct + "%");
                if (pct <= 10) {
                    tvBatteryPct.setTextColor(0xFFDC2626);
                    tvBatteryTriggerStatus.setText("Trigger: 10% THRESHOLD ACTIVE");
                    tvBatteryTriggerStatus.setTextColor(0xFFDC2626);
                } else {
                    tvBatteryPct.setTextColor(0xFF1D4ED8);
                    boolean isArmed = prefs.getBoolean("battery_trigger_armed", true);
                    tvBatteryTriggerStatus.setText("Trigger: " + (isArmed ? "ARMED (Waiting for 10%)" : "DISARMED (Re-arms >= 15%)"));
                    tvBatteryTriggerStatus.setTextColor(isArmed ? 0xFF16A34A : 0xFFD97706);
                }
            }
        };
        registerReceiver(dynamicBatteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    private int getBatteryLevel() {
        try {
            Intent bIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (bIntent != null) {
                int level = bIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = bIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) return (int)((level * 100f) / scale);
            }
        } catch (Exception ignored) {}
        return 50;
    }

    private void updateUIState() {
        String token = prefs.getString("jwt_token", null);
        String email = prefs.getString("user_email", null);
        boolean isTracking = prefs.getBoolean("tracking_enabled", false);

        switchTracking.setChecked(isTracking);
        if (token != null && !token.isEmpty()) {
            tvAuthStatus.setText("Authenticated as: " + email);
            tvAuthStatus.setTextColor(0xFF16A34A);
            etEmail.setVisibility(View.GONE);
            etPassword.setVisibility(View.GONE);
            btnLogin.setVisibility(View.GONE);
            btnRegister.setVisibility(View.GONE);
        }
        updatePendingCount();
    }

    private void log(String msg) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        tvLog.append("\n" + time + " " + msg);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dynamicBatteryReceiver != null) {
            unregisterReceiver(dynamicBatteryReceiver);
        }
    }

    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    @Override public void onProviderEnabled(String provider) {}
    @Override public void onProviderDisabled(String provider) {}
}
