const { getDatabase } = require('../db/database');

async function verifyDeviceOwnership(req, res, next) {
  const { deviceId } = req.params;
  if (!deviceId) {
    return res.status(400).json({ error: 'Device ID parameter is required.' });
  }

  try {
    const db = await getDatabase();
    const device = await db.get('SELECT * FROM devices WHERE id = ?', [deviceId]);

    if (!device) {
      return res.status(404).json({ error: `Device with ID '${deviceId}' was not found.` });
    }

    if (device.user_id !== req.user.id) {
      return res.status(403).json({
        error: 'Forbidden: You do not have permission to access or modify this device.'
      });
    }

    req.device = device;
    next();
  } catch (err) {
    console.error('Error in verifyDeviceOwnership:', err);
    return res.status(500).json({ error: 'Internal server error verifying device ownership.' });
  }
}

module.exports = { verifyDeviceOwnership };
