const { getDatabase } = require('../db/database');

async function uploadLocations(req, res) {
  const { deviceId } = req.params;
  const locations = req.validatedLocations; // Already validated & normalized by validateLocationPayload

  if (!locations || locations.length === 0) {
    return res.status(400).json({ error: 'No valid location records to process.' });
  }

  try {
    const db = await getDatabase();
    const receivedAtUtc = new Date().toISOString();

    let insertedCount = 0;
    let duplicateCount = 0;
    let latestBattery = null;
    let latestRecordedAt = null;

    // Use transaction for atomic batch insertion
    await db.transaction(async (tx) => {
      for (const loc of locations) {
        // Track latest battery and timestamp for device update
        if (!latestRecordedAt || new Date(loc.recorded_at_utc) > new Date(latestRecordedAt)) {
          latestRecordedAt = loc.recorded_at_utc;
          latestBattery = loc.battery_percentage;
        }

        // Check if event_id already exists to prevent duplicates
        const existing = await tx.get(
          'SELECT id FROM locations WHERE event_id = ?',
          [loc.event_id]
        );

        if (existing) {
          duplicateCount++;
          continue;
        }

        await tx.run(
          `INSERT INTO locations (
            event_id,
            device_id,
            latitude,
            longitude,
            accuracy_meters,
            altitude_meters,
            speed_meters_per_second,
            bearing_degrees,
            battery_percentage,
            recorded_at_utc,
            received_at_utc,
            location_source,
            is_fresh_location
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
          [
            loc.event_id,
            deviceId,
            loc.latitude,
            loc.longitude,
            loc.accuracy_meters,
            loc.altitude_meters,
            loc.speed_meters_per_second,
            loc.bearing_degrees,
            loc.battery_percentage,
            loc.recorded_at_utc,
            receivedAtUtc,
            loc.location_source,
            loc.is_fresh_location
          ]
        );
        insertedCount++;
      }

      // Update device last_seen_at and battery
      if (latestBattery !== null) {
        await tx.run(
          `UPDATE devices 
           SET last_seen_at = ?, last_battery_percentage = ? 
           WHERE id = ?`,
          [receivedAtUtc, latestBattery, deviceId]
        );
      } else {
        await tx.run(
          `UPDATE devices 
           SET last_seen_at = ? 
           WHERE id = ?`,
          [receivedAtUtc, deviceId]
        );
      }
    });

    return res.status(201).json({
      message: 'Location records processed successfully',
      device_id: deviceId,
      processed: locations.length,
      inserted: insertedCount,
      duplicates: duplicateCount,
      received_at_utc: receivedAtUtc,
      last_battery_percentage: latestBattery
    });
  } catch (err) {
    console.error('Error uploading locations:', err);
    return res.status(500).json({ error: 'Internal server error processing location updates.' });
  }
}

module.exports = {
  uploadLocations
};
