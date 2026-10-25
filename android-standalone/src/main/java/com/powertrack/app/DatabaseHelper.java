package com.powertrack.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "powertrack_local.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_LOCATIONS = "locations";
    public static final String COL_ID = "id";
    public static final String COL_EVENT_ID = "event_id";
    public static final String COL_DEVICE_ID = "device_id";
    public static final String COL_LATITUDE = "latitude";
    public static final String COL_LONGITUDE = "longitude";
    public static final String COL_ACCURACY = "accuracy_meters";
    public static final String COL_ALTITUDE = "altitude_meters";
    public static final String COL_SPEED = "speed_meters_per_second";
    public static final String COL_BEARING = "bearing_degrees";
    public static final String COL_BATTERY = "battery_percentage";
    public static final String COL_RECORDED_AT = "recorded_at_utc";
    public static final String COL_UPLOADED_AT = "uploaded_at_utc";
    public static final String COL_SOURCE = "location_source";
    public static final String COL_IS_FRESH = "is_fresh_location";
    public static final String COL_UPLOAD_STATUS = "upload_status";

    private static DatabaseHelper sInstance;

    public static synchronized DatabaseHelper getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new DatabaseHelper(context.getApplicationContext());
        }
        return sInstance;
    }

    private DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_LOCATIONS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_EVENT_ID + " TEXT UNIQUE NOT NULL, "
                + COL_DEVICE_ID + " TEXT NOT NULL, "
                + COL_LATITUDE + " REAL NOT NULL, "
                + COL_LONGITUDE + " REAL NOT NULL, "
                + COL_ACCURACY + " REAL NOT NULL, "
                + COL_ALTITUDE + " REAL, "
                + COL_SPEED + " REAL, "
                + COL_BEARING + " REAL, "
                + COL_BATTERY + " INTEGER NOT NULL, "
                + COL_RECORDED_AT + " TEXT NOT NULL, "
                + COL_UPLOADED_AT + " TEXT, "
                + COL_SOURCE + " TEXT NOT NULL, "
                + COL_IS_FRESH + " INTEGER NOT NULL, "
                + COL_UPLOAD_STATUS + " TEXT NOT NULL"
                + ");";
        db.execSQL(createTable);
        db.execSQL("CREATE INDEX idx_loc_event ON " + TABLE_LOCATIONS + "(" + COL_EVENT_ID + ");");
        db.execSQL("CREATE INDEX idx_loc_recorded ON " + TABLE_LOCATIONS + "(" + COL_RECORDED_AT + ");");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_LOCATIONS);
        onCreate(db);
    }

    public synchronized long insertLocation(String eventId, String deviceId, double lat, double lon,
                                            float accuracy, Double altitude, Float speed, Float bearing,
                                            int battery, String recordedAtUtc, String source, boolean isFresh) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_EVENT_ID, eventId);
        cv.put(COL_DEVICE_ID, deviceId);
        cv.put(COL_LATITUDE, lat);
        cv.put(COL_LONGITUDE, lon);
        cv.put(COL_ACCURACY, accuracy);
        if (altitude != null) cv.put(COL_ALTITUDE, altitude);
        if (speed != null) cv.put(COL_SPEED, speed);
        if (bearing != null) cv.put(COL_BEARING, bearing);
        cv.put(COL_BATTERY, battery);
        cv.put(COL_RECORDED_AT, recordedAtUtc);
        cv.put(COL_SOURCE, source);
        cv.put(COL_IS_FRESH, isFresh ? 1 : 0);
        cv.put(COL_UPLOAD_STATUS, "PENDING");
        return db.insertWithOnConflict(TABLE_LOCATIONS, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public synchronized int getPendingCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_LOCATIONS + " WHERE " + COL_UPLOAD_STATUS + " = 'PENDING'", null);
        int count = 0;
        if (c.moveToFirst()) {
            count = c.getInt(0);
        }
        c.close();
        return count;
    }

    public synchronized JSONArray getPendingLocationsJson(int limit) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT * FROM " + TABLE_LOCATIONS + " WHERE " + COL_UPLOAD_STATUS + " = 'PENDING' ORDER BY " + COL_RECORDED_AT + " ASC LIMIT " + limit, null);
        JSONArray arr = new JSONArray();
        try {
            while (c.moveToNext()) {
                JSONObject obj = new JSONObject();
                obj.put("event_id", c.getString(c.getColumnIndexOrThrow(COL_EVENT_ID)));
                obj.put("latitude", c.getDouble(c.getColumnIndexOrThrow(COL_LATITUDE)));
                obj.put("longitude", c.getDouble(c.getColumnIndexOrThrow(COL_LONGITUDE)));
                obj.put("accuracy_meters", c.getDouble(c.getColumnIndexOrThrow(COL_ACCURACY)));
                if (!c.isNull(c.getColumnIndexOrThrow(COL_ALTITUDE))) {
                    obj.put("altitude_meters", c.getDouble(c.getColumnIndexOrThrow(COL_ALTITUDE)));
                }
                if (!c.isNull(c.getColumnIndexOrThrow(COL_SPEED))) {
                    obj.put("speed_meters_per_second", c.getDouble(c.getColumnIndexOrThrow(COL_SPEED)));
                }
                if (!c.isNull(c.getColumnIndexOrThrow(COL_BEARING))) {
                    obj.put("bearing_degrees", c.getDouble(c.getColumnIndexOrThrow(COL_BEARING)));
                }
                obj.put("battery_percentage", c.getInt(c.getColumnIndexOrThrow(COL_BATTERY)));
                obj.put("recorded_at_utc", c.getString(c.getColumnIndexOrThrow(COL_RECORDED_AT)));
                obj.put("location_source", c.getString(c.getColumnIndexOrThrow(COL_SOURCE)));
                obj.put("is_fresh_location", c.getInt(c.getColumnIndexOrThrow(COL_IS_FRESH)) == 1);
                arr.put(obj);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            c.close();
        }
        return arr;
    }

    public synchronized void markAsUploaded(List<String> eventIds, String uploadedAtUtc) {
        if (eventIds.isEmpty()) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues cv = new ContentValues();
            cv.put(COL_UPLOAD_STATUS, "UPLOADED");
            cv.put(COL_UPLOADED_AT, uploadedAtUtc);
            for (String eid : eventIds) {
                db.update(TABLE_LOCATIONS, cv, COL_EVENT_ID + " = ?", new String[]{eid});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}
