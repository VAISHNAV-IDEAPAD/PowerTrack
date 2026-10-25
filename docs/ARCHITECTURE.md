# PowerTrack Architecture & System Design Document

## 1. Overview & Core Philosophy

PowerTrack is an authorized personal mobile tracking system designed to help users locate their Android device accurately, reliably, and transparently.

### System Principles:
1. **Genuine Location Accuracy**: No fabricated coordinates, simulated tracks, or synthetic accuracy figures. Real GPS/cell/Wi-Fi fixes are reported with their true horizontal accuracy radius.
2. **Transparent Operation**: No stealth tracking or hidden surveillance. Persistent foreground notifications and explicit user consent are mandatory.
3. **Data Resilience**: Every coordinate fix is persisted locally in an on-device SQLite database (Room) with state `PENDING` before network transmission. If connectivity is lost, updates are queued and automatically uploaded via WorkManager upon network restoration.
4. **Honest Stale Status**: If a device loses signal or powers off, the system clearly marks historical coordinates as stale and never misrepresents old data as real-time.
5. **Battery Consciousness**: Tracking intervals are configurable (10s, 30s, 60s, 300s). An emergency 10% battery trigger captures a final high-accuracy fix before shutdown and disarms until recharged to 15%.

---

## 2. High-Level System Architecture

```mermaid
flowchart TD
    subgraph Android["Android Application (Kotlin / Jetpack Compose)"]
        UI["Jetpack Compose UI (10 Screens)"]
        FS["Location Foreground Service"]
        BR["Battery Receiver (10% Trigger)"]
        Boot["Boot Receiver (Auto-resume)"]
        FLC["FusedLocationProviderClient"]
        ROOM[("Local Room Database")]
        WM["WorkManager (Offline Sync Worker)"]
        API_CLIENT["Retrofit 2 HTTP Client"]
    end

    subgraph Backend["Cloud Backend (Node.js / Express)"]
        AUTH_ROUTER["Auth Controller (JWT / bcrypt)"]
        DEV_ROUTER["Device Controller"]
        LOC_ROUTER["Location Controller"]
        VALIDATOR["Validation Middleware"]
        RATE_LIMIT["Rate Limiter & Helmet"]
        RETENTION["Retention Policy Engine"]
        SQL_DB[("SQLite Database / Postgres Ready")]
    end

    subgraph WebDashboard["Web Dashboard (React / Vite / Leaflet)"]
        DASH_UI["Responsive Dashboard UI"]
        LEAFLET["Leaflet & OpenStreetMap"]
        AXIOS["Axios API Client"]
    end

    FLC -->|Raw Location| FS
    BR -->|10% Battery Event| FLC
    FS -->|Coordinate Fix| ROOM
    ROOM -->|Unsynced Records| WM
    FS -->|Immediate Fix| API_CLIENT
    WM -->|Batch Synchronization| API_CLIENT
    Boot -->|Restart Service| FS

    API_CLIENT -->|HTTPS / REST| RATE_LIMIT
    RATE_LIMIT --> VALIDATOR
    VALIDATOR --> AUTH_ROUTER
    VALIDATOR --> DEV_ROUTER
    VALIDATOR --> LOC_ROUTER
    AUTH_ROUTER --> SQL_DB
    DEV_ROUTER --> SQL_DB
    LOC_ROUTER --> SQL_DB
    RETENTION --> SQL_DB

    DASH_UI --> AXIOS
    AXIOS -->|REST API| RATE_LIMIT
    LOC_ROUTER -.->|Latest & History| DASH_UI
    DASH_UI --> LEAFLET
```

---

## 3. Database Schema

The database is built on relational SQLite and designed for transparent migration to PostgreSQL.

### `users` Table
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | TEXT | PRIMARY KEY | User UUID |
| `email` | TEXT | UNIQUE, NOT NULL | Normalized user email |
| `password_hash` | TEXT | NOT NULL | bcrypt hash (12 salt rounds) |
| `created_at` | TEXT | NOT NULL | UTC ISO 8601 registration time |

### `devices` Table
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | TEXT | PRIMARY KEY | Unique Device UUID |
| `user_id` | TEXT | NOT NULL, FK -> users(id) | Owning user |
| `device_name` | TEXT | NOT NULL | Display name (e.g. Pixel 8) |
| `platform` | TEXT | NOT NULL DEFAULT 'android' | Operating system |
| `registered_at` | TEXT | NOT NULL | Registration UTC ISO timestamp |
| `last_seen_at` | TEXT | NULLABLE | Heartbeat / last contact UTC timestamp |
| `last_battery_percentage` | INTEGER | NULLABLE | Latest reported battery (0–100) |
| `tracking_enabled` | INTEGER | NOT NULL DEFAULT 1 | Remote tracking toggle |

### `locations` Table
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | INTEGER | PRIMARY KEY AUTOINCREMENT | Surrogate key |
| `event_id` | TEXT | UNIQUE, NOT NULL | Idempotency UUID from device |
| `device_id` | TEXT | NOT NULL, FK -> devices(id) | Target device |
| `latitude` | REAL | NOT NULL | Validated between -90 and +90 |
| `longitude` | REAL | NOT NULL | Validated between -180 and +180 |
| `accuracy_meters` | REAL | NOT NULL | Horizontal 68% confidence radius in metres |
| `altitude_meters` | REAL | NULLABLE | Altitude in metres above WGS 84 ellipsoid |
| `speed_meters_per_second` | REAL | NULLABLE | Ground speed |
| `bearing_degrees` | REAL | NULLABLE | Travel direction (0–360°) |
| `battery_percentage` | INTEGER | NOT NULL | Battery level at capture (0–100) |
| `recorded_at_utc` | TEXT | NOT NULL | Phone device capture timestamp |
| `received_at_utc` | TEXT | NOT NULL | Server ingestion timestamp |
| `location_source` | TEXT | NOT NULL DEFAULT 'fused' | GPS, network, fused, passive |
| `is_fresh_location` | INTEGER | NOT NULL DEFAULT 1 | 1 = Fresh GPS fix; 0 = Cached |

### Database Indexes
- `idx_users_email` on `users(email)`
- `idx_devices_user` on `devices(user_id)`
- `idx_locations_device_recorded` on `locations(device_id, recorded_at_utc DESC)`
- `idx_locations_event_id` on `locations(event_id)`

---

## 4. Device Status State Machine

The dashboard computes device statuses dynamically using strict temporal rules:

| Status | Condition | Meaning |
|---|---|---|
| **`ONLINE`** | Contact within 300s AND location within 900s | Device is communicating and transmitting fresh fixes |
| **`LOCATION STALE`** | Contact within 300s BUT location older than 900s | Device is connected (heartbeat active), but GPS is unavailable (e.g. indoors, underground) |
| **`OFFLINE`** | No contact within 300s | No heartbeat or location received within 5 minutes |
| **`UNKNOWN`** | Zero contacts and zero locations | Newly registered device that has never communicated |

---

## 5. Offline Queue & WorkManager Protocol

```mermaid
sequenceDiagram
    participant GPS as FusedLocationProviderClient
    participant FS as Foreground Service
    participant Room as Room Local DB
    participant WM as WorkManager Worker
    participant API as Cloud Backend

    GPS->>FS: onLocationResult(lat, lon, acc, time)
    FS->>FS: Validate coordinates & time
    FS->>Room: INSERT INTO locations (status='PENDING')
    alt Internet is Online
        FS->>API: POST /api/devices/:id/locations
        API-->>FS: 201 Created
        FS->>Room: UPDATE status='UPLOADED'
    else Internet is Offline
        FS->>WM: Enqueue unique connected work
        Note over Room,WM: Record preserved locally as PENDING
        Note over WM: Android detects network restoration
        WM->>Room: SELECT * WHERE status='PENDING' ORDER BY time ASC
        WM->>API: POST batch locations
        API-->>WM: 201 Created (confirmed)
        WM->>Room: UPDATE status='UPLOADED'
    end
```
