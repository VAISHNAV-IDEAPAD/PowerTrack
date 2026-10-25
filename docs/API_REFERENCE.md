# PowerTrack Cloud Backend REST API Reference

Base URL: `http://localhost:5000/api` (or configured server host)

All requests and responses use `application/json`.
Protected endpoints require:
`Authorization: Bearer <JWT_TOKEN>`

---

## 1. Authentication Endpoints

### `POST /auth/register`
Creates a new user account and returns a JWT token.

**Request Body:**
```json
{
  "email": "user@example.com",
  "password": "SecurePassword123"
}
```

**Success Response (201 Created):**
```json
{
  "message": "User registered successfully",
  "token": "eyJhbGciOiJIUzI1Ni...",
  "user": {
    "id": "e8d649f8-7901-44be-b35f-141a4a15a0c3",
    "email": "user@example.com",
    "created_at": "2026-10-25T12:00:00.000Z"
  }
}
```

**Error Responses:**
- `400 Bad Request`: Email invalid or password < 8 characters.
- `409 Conflict`: Email already exists.

---

### `POST /auth/login`
Authenticates user credentials and returns a JWT token.

**Request Body:**
```json
{
  "email": "user@example.com",
  "password": "SecurePassword123"
}
```

**Success Response (200 OK):**
```json
{
  "message": "Login successful",
  "token": "eyJhbGciOiJIUzI1Ni...",
  "user": {
    "id": "e8d649f8-7901-44be-b35f-141a4a15a0c3",
    "email": "user@example.com",
    "created_at": "2026-10-25T12:00:00.000Z"
  }
}
```

**Error Responses:**
- `401 Unauthorized`: Invalid email or password.

---

## 2. Device Management Endpoints

### `POST /devices/register`
*Protected (Auth Required)*

Registers an Android phone under the authenticated user account.

**Request Body:**
```json
{
  "device_name": "Google Pixel 8 Pro",
  "platform": "android",
  "device_id": "optional-custom-uuid-or-generated"
}
```

**Success Response (201 Created / 200 OK):**
```json
{
  "message": "Device registered successfully",
  "device": {
    "id": "a90fc832-bc90-410a-b33c-36a56eef40d1",
    "user_id": "e8d649f8-7901-44be-b35f-141a4a15a0c3",
    "device_name": "Google Pixel 8 Pro",
    "platform": "android",
    "registered_at": "2026-10-25T12:05:00.000Z",
    "last_seen_at": null,
    "last_battery_percentage": null,
    "tracking_enabled": 1
  }
}
```

---

### `GET /devices`
*Protected (Auth Required)*

Lists all registered devices belonging to the authenticated user with computed real-time status.

**Success Response (200 OK):**
```json
{
  "devices": [
    {
      "id": "a90fc832-bc90-410a-b33c-36a56eef40d1",
      "user_id": "e8d649f8-7901-44be-b35f-141a4a15a0c3",
      "device_name": "Google Pixel 8 Pro",
      "platform": "android",
      "registered_at": "2026-10-25T12:05:00.000Z",
      "last_seen_at": "2026-10-25T12:30:00.000Z",
      "last_battery_percentage": 78,
      "tracking_enabled": 1,
      "status": "ONLINE",
      "total_locations": 142,
      "latest_location": {
        "id": 142,
        "event_id": "764b8bb3-4706-4bfa-9b4e-eef014a070e1",
        "device_id": "a90fc832-bc90-410a-b33c-36a56eef40d1",
        "latitude": 28.6139,
        "longitude": 77.2090,
        "accuracy_meters": 11.2,
        "battery_percentage": 78,
        "recorded_at_utc": "2026-10-25T12:30:00.000Z"
      }
    }
  ]
}
```

---

### `GET /devices/:deviceId/latest-location`
*Protected (Auth + Device Ownership Required)*

Retrieves the most recent recorded location fix for the device.

**Success Response (200 OK):**
```json
{
  "device_id": "a90fc832-bc90-410a-b33c-36a56eef40d1",
  "latest_location": {
    "id": 142,
    "event_id": "764b8bb3-4706-4bfa-9b4e-eef014a070e1",
    "device_id": "a90fc832-bc90-410a-b33c-36a56eef40d1",
    "latitude": 28.6139,
    "longitude": 77.2090,
    "accuracy_meters": 11.2,
    "altitude_meters": 216.5,
    "speed_meters_per_second": 1.4,
    "bearing_degrees": 180.0,
    "battery_percentage": 78,
    "recorded_at_utc": "2026-10-25T12:30:00.000Z",
    "received_at_utc": "2026-10-25T12:30:01.120Z",
    "location_source": "gps",
    "is_fresh_location": 1,
    "is_stale": false
  }
}
```

---

### `GET /devices/:deviceId/location-history`
*Protected (Auth + Device Ownership Required)*

Returns paginated location fixes, sorted newest first (`recorded_at_utc DESC`).

**Query Parameters:**
- `page`: Page index (default: `1`)
- `limit`: Records per page (default: `50`, max: `200`)
- `start_date`: ISO 8601 UTC timestamp filter start
- `end_date`: ISO 8601 UTC timestamp filter end

**Success Response (200 OK):**
```json
{
  "device_id": "a90fc832-bc90-410a-b33c-36a56eef40d1",
  "pagination": {
    "page": 1,
    "limit": 50,
    "total": 142,
    "totalPages": 3
  },
  "locations": [
    {
      "id": 142,
      "event_id": "764b8bb3-4706-4bfa-9b4e-eef014a070e1",
      "device_id": "a90fc832-bc90-410a-b33c-36a56eef40d1",
      "latitude": 28.6139,
      "longitude": 77.2090,
      "accuracy_meters": 11.2,
      "battery_percentage": 78,
      "recorded_at_utc": "2026-10-25T12:30:00.000Z",
      "location_source": "gps",
      "is_fresh_location": 1
    }
  ]
}
```

---

### `POST /devices/:deviceId/locations`
*Protected (Auth + Device Ownership Required)*

Uploads single location or batch of offline-queued locations. Deduplicates via `event_id`.

**Request Body (Batch Array or Object):**
```json
{
  "locations": [
    {
      "event_id": "764b8bb3-4706-4bfa-9b4e-eef014a070e1",
      "latitude": 28.6139,
      "longitude": 77.2090,
      "accuracy_meters": 11.2,
      "altitude_meters": 216.5,
      "speed_meters_per_second": 1.4,
      "bearing_degrees": 180.0,
      "battery_percentage": 78,
      "recorded_at_utc": "2026-10-25T12:30:00.000Z",
      "location_source": "gps",
      "is_fresh_location": true
    }
  ]
}
```

**Success Response (201 Created):**
```json
{
  "message": "Location records processed successfully",
  "device_id": "a90fc832-bc90-410a-b33c-36a56eef40d1",
  "processed": 1,
  "inserted": 1,
  "duplicates": 0,
  "received_at_utc": "2026-10-25T12:30:01.120Z",
  "last_battery_percentage": 78
}
```

---

### `POST /devices/:deviceId/heartbeat`
*Protected (Auth + Device Ownership Required)*

Lightweight keep-alive ping updating `last_seen_at` without generating a full location coordinate fix.

**Request Body:**
```json
{
  "battery_percentage": 82
}
```

---

### `PATCH /devices/:deviceId/settings`
*Protected (Auth + Device Ownership Required)*

Updates device preferences.

**Request Body:**
```json
{
  "device_name": "Updated Pixel 8",
  "tracking_enabled": true
}
```

---

### `GET /health`
*Public (No Auth Required)*

Returns service health, version, and server uptime.
