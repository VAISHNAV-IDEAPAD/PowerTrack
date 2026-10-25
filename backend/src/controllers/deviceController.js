const { v4: uuidv4 } = require('uuid');
const config = require('../config');
const { getDatabase } = require('../db/database');

// Helper to determine device status accurately
function calculateDeviceStatus(device, latestLocation) {
  const now = Date.now();
  const offlineThresholdMs = config.deviceOfflineThresholdSeconds * 1000;
  const staleThresholdMs = config.locationStaleThresholdSeconds * 1000;

  const lastSeenMs = device.last_seen_at ? new Date(device.last_seen_at).getTime() : 0;
  const lastLocationMs = latestLocation ? new Date(latestLocation.recorded_at_utc).getTime() : 0;

  // The latest contact is the maximum of last_seen_at (heartbeat) and last recorded location
  const latestContactMs = Math.max(lastSeenMs, lastLocationMs);

  if (latestContactMs === 0) {
    return 'UNKNOWN';
  }

  const isContactRecent = now - latestContactMs <= offlineThresholdMs;

  if (!isContactRecent) {
    return 'OFFLINE';
  }

  // Device is communicating recently, but check if location fix itself is stale
  if (lastLocationMs === 0 || (now - lastLocationMs > staleThresholdMs)) {
    return 'LOCATION STALE';
  }

  return 'ONLINE';
}

async function registerDevice(req, res) {
  const { device_name, platform = 'android', device_id } = req.body;
  const userId = req.user.id;

  try {
    const db = await getDatabase();
    const targetId = device_id ? device_id.trim() : uuidv4();

    // Check if device already exists
    const existing = await db.get('SELECT * FROM devices WHERE id = ?', [targetId]);
    if (existing) {
      if (existing.user_id === userId) {
        return res.status(200).json({
          message: 'Device already registered to this account',
          device: existing
        });
      } else {
        return res.status(409).json({
          error: 'This device ID is already registered under another account.'
        });
      }
    }

    const registeredAt = new Date().toISOString();
    await db.run(
      `INSERT INTO devices (id, user_id, device_name, platform, registered_at, tracking_enabled)
       VALUES (?, ?, ?, ?, ?, 1)`,
      [targetId, userId, device_name, platform, registeredAt]
    );

    const created = await db.get('SELECT * FROM devices WHERE id = ?', [targetId]);
    return res.status(201).json({
      message: 'Device registered successfully',
      device: created
    });
  } catch (err) {
    console.error('Error registering device:', err);
    return res.status(500).json({ error: 'Internal server error registering device.' });
  }
}

async function listDevices(req, res) {
  const userId = req.user.id;

  try {
    const db = await getDatabase();
    const devices = await db.query(
      'SELECT * FROM devices WHERE user_id = ? ORDER BY registered_at DESC',
      [userId]
    );

    // Fetch latest location and location count for each device
    const enrichedDevices = await Promise.all(
      devices.map(async (device) => {
        const latestLocation = await db.get(
          `SELECT * FROM locations 
           WHERE device_id = ? 
           ORDER BY recorded_at_utc DESC 
           LIMIT 1`,
          [device.id]
        );

        const countRow = await db.get(
          'SELECT COUNT(*) as total FROM locations WHERE device_id = ?',
          [device.id]
        );

        const status = calculateDeviceStatus(device, latestLocation);

        return {
          ...device,
          status,
          total_locations: countRow ? countRow.total : 0,
          latest_location: latestLocation || null
        };
      })
    );

    return res.status(200).json({ devices: enrichedDevices });
  } catch (err) {
    console.error('Error listing devices:', err);
    return res.status(500).json({ error: 'Internal server error retrieving devices.' });
  }
}

async function getLatestLocation(req, res) {
  const { deviceId } = req.params;

  try {
    const db = await getDatabase();
    const latest = await db.get(
      `SELECT * FROM locations 
       WHERE device_id = ? 
       ORDER BY recorded_at_utc DESC 
       LIMIT 1`,
      [deviceId]
    );

    if (!latest) {
      return res.status(200).json({
        device_id: deviceId,
        latest_location: null,
        message: 'No location records found for this device.'
      });
    }

    const recordedMs = new Date(latest.recorded_at_utc).getTime();
    const isStale = (Date.now() - recordedMs) > (config.locationStaleThresholdSeconds * 1000);

    return res.status(200).json({
      device_id: deviceId,
      latest_location: {
        ...latest,
        is_stale: isStale
      }
    });
  } catch (err) {
    console.error('Error getting latest location:', err);
    return res.status(500).json({ error: 'Internal server error retrieving latest location.' });
  }
}

async function getLocationHistory(req, res) {
  const { deviceId } = req.params;
  const page = Math.max(1, parseInt(req.query.page, 10) || 1);
  const limit = Math.min(200, Math.max(1, parseInt(req.query.limit, 10) || 50));
  const offset = (page - 1) * limit;

  const { start_date, end_date } = req.query;

  try {
    const db = await getDatabase();

    const whereClauses = ['device_id = ?'];
    const params = [deviceId];

    if (start_date) {
      whereClauses.push('recorded_at_utc >= ?');
      params.push(new Date(start_date).toISOString());
    }

    if (end_date) {
      whereClauses.push('recorded_at_utc <= ?');
      params.push(new Date(end_date).toISOString());
    }

    const whereSql = whereClauses.join(' AND ');

    // Get total count
    const countRow = await db.get(
      `SELECT COUNT(*) as total FROM locations WHERE ${whereSql}`,
      params
    );
    const total = countRow ? countRow.total : 0;
    const totalPages = Math.ceil(total / limit) || 1;

    // Get paginated results
    const queryParams = [...params, limit, offset];
    const rows = await db.query(
      `SELECT * FROM locations 
       WHERE ${whereSql} 
       ORDER BY recorded_at_utc DESC 
       LIMIT ? OFFSET ?`,
      queryParams
    );

    return res.status(200).json({
      device_id: deviceId,
      pagination: {
        page,
        limit,
        total,
        totalPages
      },
      locations: rows
    });
  } catch (err) {
    console.error('Error getting location history:', err);
    return res.status(500).json({ error: 'Internal server error retrieving location history.' });
  }
}

async function heartbeat(req, res) {
  const { deviceId } = req.params;
  const { battery_percentage } = req.body || {};

  try {
    const db = await getDatabase();
    const nowIso = new Date().toISOString();

    if (battery_percentage !== undefined && battery_percentage !== null) {
      await db.run(
        `UPDATE devices 
         SET last_seen_at = ?, last_battery_percentage = ? 
         WHERE id = ?`,
        [nowIso, battery_percentage, deviceId]
      );
    } else {
      await db.run(
        `UPDATE devices 
         SET last_seen_at = ? 
         WHERE id = ?`,
        [nowIso, deviceId]
      );
    }

    return res.status(200).json({
      message: 'Heartbeat acknowledged',
      device_id: deviceId,
      last_seen_at: nowIso
    });
  } catch (err) {
    console.error('Error processing heartbeat:', err);
    return res.status(500).json({ error: 'Internal server error processing heartbeat.' });
  }
}

async function updateDeviceSettings(req, res) {
  const { deviceId } = req.params;
  const { tracking_enabled, device_name } = req.body || {};

  try {
    const db = await getDatabase();
    const updates = [];
    const params = [];

    if (tracking_enabled !== undefined) {
      updates.push('tracking_enabled = ?');
      params.push(tracking_enabled ? 1 : 0);
    }

    if (device_name !== undefined && typeof device_name === 'string' && device_name.trim().length > 0) {
      updates.push('device_name = ?');
      params.push(device_name.trim());
    }

    if (updates.length === 0) {
      return res.status(400).json({ error: 'No valid settings fields provided to update.' });
    }

    params.push(deviceId);
    await db.run(
      `UPDATE devices SET ${updates.join(', ')} WHERE id = ?`,
      params
    );

    const updatedDevice = await db.get('SELECT * FROM devices WHERE id = ?', [deviceId]);
    return res.status(200).json({
      message: 'Device settings updated successfully',
      device: updatedDevice
    });
  } catch (err) {
    console.error('Error updating device settings:', err);
    return res.status(500).json({ error: 'Internal server error updating device settings.' });
  }
}

module.exports = {
  calculateDeviceStatus,
  registerDevice,
  listDevices,
  getLatestLocation,
  getLocationHistory,
  heartbeat,
  updateDeviceSettings
};
