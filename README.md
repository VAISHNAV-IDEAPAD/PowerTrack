# PowerTrack — Android Mobile Tracking System

**PowerTrack** is a personal mobile-device tracking system engineered to help authorized users locate their Android smartphone with precision, reliable offline storage, battery-conscious tracking, and honest reporting of device availability.

---

## Key Features

1. **True Location Accuracy**: Integrates Google Play Services `FusedLocationProviderClient` to capture genuine satellite/network coordinates. Records horizontal accuracy radius in metres. Rejects invalid coordinates, Null Island (0,0), and future timestamps.
2. **Offline Resilience with Room**: Every coordinate fix is committed to an on-device SQLite database (Room) with status `PENDING` prior to network transmission. If the phone is offline, coordinates are preserved and uploaded chronologically via WorkManager when connectivity returns.
3. **Emergency 10% Low-Battery Trigger**: Captures a final high-accuracy GPS fix the instant battery drops to 10% and uploads before device shutdown. Disarms to prevent duplicate triggers and resets only after recharging to 15%.
4. **Honest Stale Status Reporting**: The cloud backend and web dashboard distinguish real-time coordinates from historical fixes. When a device is offline or powered off, an amber warning indicates the location is stale and displays the last known position.
5. **Continuous Foreground Tracking**: Supports ongoing tracking via an Android foreground service with a persistent notification and configurable intervals (10s, 30s, 60s, 300s).
6. **Reboot Auto-Resume**: Recovers tracking state upon device restart using a `BootReceiver` (`BOOT_COMPLETED`), subject to Android permission and background restrictions.
7. **Cloud Backend & REST API**: Built with Node.js, Express, and an abstracted SQLite database tier (ready for PostgreSQL). Protected with bcrypt password hashing, JWT authentication, rate limiting, and device ownership validation.
8. **Interactive Web Dashboard**: Built with React, Vite, and Leaflet with OpenStreetMap tiles. Displays accuracy radius circles, real-time device status (ONLINE, OFFLINE, LOCATION STALE, UNKNOWN), and paginated location history.

---

## Project Structure

```
PowerTrack/
├── android-app/                # Android Application (Kotlin, Jetpack Compose, Room, WorkManager)
│   ├── app/
│   │   ├── src/main/java/com/powertrack/app/
│   │   │   ├── data/
│   │   │   │   ├── local/      # Room Database, DAO, SessionManager
│   │   │   │   ├── model/      # LocationEntity, Network DTOs
│   │   │   │   ├── remote/     # Retrofit API, AuthInterceptor, NetworkClient
│   │   │   │   └── repository/ # LocationRepository, AuthRepository
│   │   │   ├── service/        # LocationForegroundService, BootReceiver, BatteryReceiver
│   │   │   ├── ui/
│   │   │   │   ├── screens/    # 10 Jetpack Compose Screens
│   │   │   │   └── theme/      # Material 3 Theme & Colors
│   │   │   ├── worker/         # WorkManager LocationSyncWorker
│   │   │   └── MainActivity.kt
│   │   ├── src/test/java/      # Unit tests (Coordinates, Low-Battery Trigger, Event ID)
│   │   └── AndroidManifest.xml # Permissions (Foreground/Background location, Foreground Service)
│   ├── build.gradle.kts
│   └── settings.gradle.kts
│
├── backend/                    # Cloud Backend (Node.js, Express, SQLite / Postgres-ready)
│   ├── src/
│   │   ├── config/             # Environment configuration
│   │   ├── controllers/        # Auth, Device, and Location Controllers
│   │   ├── db/                 # Database connection & migrations
│   │   ├── middleware/         # JWT Auth, Device Ownership, Input Validation, Rate Limiter
│   │   ├── routes/             # REST API routes (/api/auth, /api/devices)
│   │   ├── services/           # Data retention policy service
│   │   ├── app.js              # Express app & static dashboard serving
│   │   └── server.js           # HTTP server entry point & graceful shutdown
│   ├── tests/                  # Jest & Supertest test suite (28 passing tests)
│   ├── package.json
│   └── .env.example
│
├── dashboard/                  # Web Dashboard (React, Vite, Leaflet, Axios)
│   ├── src/
│   │   ├── api/                # Axios client with JWT interceptor
│   │   ├── components/         # MapViewer (Leaflet), DeviceStatusBadge, HistoryTable, Navbar
│   │   ├── context/            # AuthContext (login, register, session)
│   │   ├── pages/              # DashboardHome, DeviceDetails, AuthPage
│   │   ├── styles/             # Responsive CSS stylesheet
│   │   ├── App.jsx
│   │   └── main.jsx
│   ├── vite.config.js
│   └── package.json
│
├── docs/                       # Comprehensive Documentation
│   ├── ARCHITECTURE.md         # System design, data models, state machine, sequences
│   ├── API_REFERENCE.md        # Full REST API endpoint reference
│   ├── ANDROID_LOCATION_STRATEGY.md # GPS accuracy, FusedLocation, battery triggers, OS limits
│   └── MANUAL_TESTING_GUIDE.md # Testing procedures with Android Studio / ADB commands
│
├── .gitignore
└── README.md
```

---

## Quick-Start Instructions

### Step 1: Start the Backend Server

```bash
cd PowerTrack/backend
npm install
npm test       # Runs the 28 automated test suite
npm start      # Starts backend on http://localhost:5000
```

*Note*: Because the dashboard production build is included in `dashboard/dist`, browsing to `http://localhost:5000` automatically serves the full Web Dashboard directly!

### Step 2: Run the Web Dashboard in Development Mode (Optional)

If you wish to make live modifications to the dashboard with hot module replacement:

```bash
cd PowerTrack/dashboard
npm install
npm run dev    # Starts Vite dev server on http://localhost:3000
```

### Step 3: Launch the Android Application

1. Open Android Studio (Ladybug / Hedgehog / Iguana).
2. Select **File -> Open...** and choose the `PowerTrack/android-app` directory.
3. Wait for Gradle sync to complete.
4. Launch on an Android Emulator or connected physical device running Android 8.0 (API 26) through Android 14+ (API 34).
5. In the Android app Login screen, tap the **Settings** icon:
   * **Android Emulator**: Ensure Backend URL is `http://10.0.2.2:5000` (default).
   * **Physical Phone**: Set Backend URL to your computer's local Wi-Fi IP (e.g. `http://192.168.1.50:5000`).
6. Register a new account or log in. The device will automatically link to your account.

---

## Automated Backend Test Suite Results

The backend includes a comprehensive Jest and Supertest test suite (`backend/tests/api.test.js`) verifying:
- Health check endpoints
- Registration with valid/invalid emails and short passwords
- Password hashing with bcrypt
- Duplicate user detection
- Login authentication with correct/incorrect credentials
- Protected route authorization checks
- Device registration and device ownership validation (403 Forbidden for unauthorized access)
- Coordinate validation (rejecting lat > 90, lat < -90, lon > 180, lon < -180, (0,0))
- Negative / zero accuracy rejection
- Battery percentage bounds validation (0–100)
- Future timestamp skew rejection (> 5 minutes)
- Idempotency & duplicate event prevention (`event_id` uniqueness)
- Batch offline location ingestion
- Pagination and date range filtering
- Heartbeat and device status computation

Run tests anytime with:
```bash
cd PowerTrack/backend && npm test
```

---

## Android Architecture Highlights

- **Jetpack Compose**: Modern declarative UI with Material 3 components and light/dark theme support.
- **Room Database**: 100% offline-first local persistence with indexes on `event_id` and `recorded_at_utc`.
- **WorkManager**: Retryable background synchronization using `NetworkType.CONNECTED` constraints with exponential backoff.
- **Foreground Service**: Ongoing location tracking with persistent notification (`FOREGROUND_SERVICE_LOCATION` on Android 14+).
- **Idempotency**: Every location fix is assigned a UUID `event_id` on-device to prevent server duplication.

---

## License

This project is licensed under the ISC License.
