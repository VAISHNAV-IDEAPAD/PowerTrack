# PowerTrack Manual Testing & Verification Guide

This guide describes end-to-end testing procedures on both an Android Studio Emulator and a physical Android hardware device.

---

## 1. Prerequisites & Environment Setup

1. **Start the PowerTrack Cloud Backend**:
   ```bash
   cd PowerTrack/backend
   npm install
   npm run start
   ```
   *Backend starts on `http://localhost:5000` (and serves the dashboard at `http://localhost:5000`).*

2. **Start the Web Dashboard in Dev Mode (Optional)**:
   ```bash
   cd PowerTrack/dashboard
   npm install
   npm run dev
   ```
   *Dashboard available at `http://localhost:3000`.*

3. **Android Emulator Host Mapping**:
   * Android Emulator uses `http://10.0.2.2:5000` to reach your host computer's `localhost:5000`.
   * For a physical phone connected over Wi-Fi, use your computer's local LAN IP (e.g., `http://192.168.1.50:5000`).

---

## 2. Test Case 1: Account Registration & Device Linking

1. Open the Android application on your device or emulator.
2. Tap the **Settings icon** in the top bar of the Login screen and confirm the Backend URL:
   * Emulator: `http://10.0.2.2:5000`
   * Physical device: `http://<YOUR_LAN_IP>:5000`
3. Tap **"Don't have an account? Register"**.
4. Enter an email: `tester@example.com` and password: `Password123!`.
5. Enter Device Name: `Pixel Test Device`.
6. Tap **"Register & Link Device"**.
7. **Verification**:
   - The app transitions to the Home Screen.
   - Open the web dashboard in your browser (`http://localhost:5000`).
   - Log in with `tester@example.com` / `Password123!`.
   - Confirm `Pixel Test Device` is listed on the dashboard with status `UNKNOWN` or `ONLINE`.

---

## 3. Test Case 2: Manual GPS Refresh & Accuracy Verification

1. On the Android Home Screen, tap **"Refresh Location"**.
2. If prompted, grant **Precise Location** permission.
3. Observe progress: `Obtaining GPS Fix (Timeout: 15s)...`
4. **Verification**:
   - Coordinate readings (Latitude, Longitude) display on screen.
   - The reported accuracy radius displays (e.g. `±12m`).
   - Accuracy badge displays (High Accuracy green if <= 15m; Moderate yellow if <= 50m).
   - In the web dashboard, refresh or wait for auto-poll:
     - The map centers on the new coordinates.
     - A blue pin and an accuracy confidence circle render around the point.
     - Marker popup displays matching accuracy and timestamp.

---

## 4. Test Case 3: Offline Queue & Synchronization

1. Put the Android device or emulator in Airplane mode, or run:
   ```bash
   adb shell svc wifi disable
   adb shell svc data disable
   ```
2. In the PowerTrack app, tap **"Refresh Location"**.
3. **Verification**:
   - The location fix is acquired via GPS satellite receiver.
   - Look at the **Connectivity** tile on Home Screen: displays `OFFLINE`.
   - The **Pending Upload Queue** increments: `1 pending sync`.
   - Open **Offline Queue** screen: verifies record is stored with status `PENDING`.
4. Restore internet connectivity:
   ```bash
   adb shell svc wifi enable
   adb shell svc data enable
   ```
5. In the app, tap **"Force Sync Now"** or wait for WorkManager to detect network:
   - Status updates: `Successfully synchronized 1 pending location records!`
   - Pending count returns to `0`.
   - Open Dashboard: the offline record is displayed in the location history table with its original recorded timestamp.

---

## 5. Test Case 4: 10% Low-Battery Emergency Trigger Verification

Simulate battery depletion using ADB commands on an emulator or rooted device:

1. Unplug charger from emulator / device:
   ```bash
   adb shell dumpsys battery unplug
   ```
2. Set the battery level to 20%:
   ```bash
   adb shell dumpsys battery set level 20
   ```
   *In app Battery Monitor screen: Trigger status is ARMED.*

3. Set the battery level to 10% (Trigger threshold):
   ```bash
   adb shell dumpsys battery set level 10
   ```
4. **Verification**:
   - The emergency workflow executes immediately.
   - High-priority notification appears: *"Emergency Low Battery Trigger (Battery at 10%)"*.
   - A fresh high-accuracy location fix is captured and uploaded with battery percentage `10%`.
   - In the app Battery Status screen: status switches to `TRIGGERED / DISARMED`.
   - In the Web Dashboard: the latest fix displays battery `10%`.

5. Set battery level to 8%:
   ```bash
   adb shell dumpsys battery set level 8
   ```
   *Verification*: No duplicate trigger occurs because state is disarmed.

6. Simulate charging above the 15% reset threshold:
   ```bash
   adb shell dumpsys battery set level 25
   ```
   *Verification*: The trigger re-arms (`isArmed = true`), ready for the next cycle.

7. Reset battery to real system defaults:
   ```bash
   adb shell dumpsys battery reset
   ```

---

## 6. Test Case 5: Stale Location Warning Verification

1. On the Android device, disable tracking or put the phone in Airplane mode.
2. Wait or simulate time passing (> 15 minutes), or check a historical record on the dashboard.
3. **Verification on Web Dashboard**:
   - Device status badge switches to `LOCATION STALE` or `OFFLINE`.
   - An amber warning banner appears above the map:
     > **Warning: Location is Stale — This coordinate was recorded X minutes ago. The device may be offline, powered off, or experiencing GPS interruption.**
   - The dashboard never claims the old coordinate is the device's current location.
