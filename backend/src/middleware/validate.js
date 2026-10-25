// Validation middleware and helper routines

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const MAX_FUTURE_SKEW_MS = 5 * 60 * 1000; // 5 minutes clock-skew tolerance
const MIN_VALID_TIMESTAMP = new Date('2020-01-01T00:00:00Z').getTime();

function validateRegister(req, res, next) {
  const { email, password } = req.body || {};

  if (!email || typeof email !== 'string' || !EMAIL_REGEX.test(email.trim())) {
    return res.status(400).json({ error: 'A valid email address is required.' });
  }

  if (!password || typeof password !== 'string' || password.length < 8) {
    return res.status(400).json({
      error: 'Password must be at least 8 characters long.'
    });
  }

  req.body.email = email.trim().toLowerCase();
  next();
}

function validateLogin(req, res, next) {
  const { email, password } = req.body || {};

  if (!email || typeof email !== 'string' || !EMAIL_REGEX.test(email.trim())) {
    return res.status(400).json({ error: 'A valid email address is required.' });
  }

  if (!password || typeof password !== 'string' || password.length === 0) {
    return res.status(400).json({ error: 'Password is required.' });
  }

  req.body.email = email.trim().toLowerCase();
  next();
}

function validateDeviceRegister(req, res, next) {
  const { device_name, platform, device_id } = req.body || {};

  if (!device_name || typeof device_name !== 'string' || device_name.trim().length === 0) {
    return res.status(400).json({ error: 'device_name is required and must not be empty.' });
  }

  if (platform && typeof platform !== 'string') {
    return res.status(400).json({ error: 'platform must be a string.' });
  }

  if (device_id && (typeof device_id !== 'string' || device_id.trim().length === 0)) {
    return res.status(400).json({ error: 'device_id, if provided, must be a non-empty string.' });
  }

  req.body.device_name = device_name.trim();
  req.body.platform = platform ? platform.trim().toLowerCase() : 'android';
  next();
}

function validateSingleLocationRecord(loc, index = null) {
  const prefix = index !== null ? `Record at index ${index}: ` : '';

  if (!loc || typeof loc !== 'object') {
    return `${prefix}Payload must be a JSON object.`;
  }

  if (!loc.event_id || typeof loc.event_id !== 'string' || loc.event_id.trim().length === 0) {
    return `${prefix}event_id is required and must be a unique non-empty string.`;
  }

  const lat = Number(loc.latitude);
  if (isNaN(lat) || lat < -90 || lat > 90) {
    return `${prefix}latitude must be a valid number between -90 and 90. Received: ${loc.latitude}`;
  }

  const lon = Number(loc.longitude);
  if (isNaN(lon) || lon < -180 || lon > 180) {
    return `${prefix}longitude must be a valid number between -180 and 180. Received: ${loc.longitude}`;
  }

  // Reject false (0,0) fixes unless explicitly marked with source mock (which is rejected anyway)
  if (lat === 0 && lon === 0) {
    return `${prefix}Coordinates (0, 0) are invalid / false GPS fix.`;
  }

  const accuracy = Number(loc.accuracy_meters);
  if (isNaN(accuracy) || accuracy <= 0) {
    return `${prefix}accuracy_meters must be a positive number greater than 0. Received: ${loc.accuracy_meters}`;
  }

  const battery = Number(loc.battery_percentage);
  if (isNaN(battery) || !Number.isInteger(battery) || battery < 0 || battery > 100) {
    return `${prefix}battery_percentage must be an integer between 0 and 100. Received: ${loc.battery_percentage}`;
  }

  if (!loc.recorded_at_utc || typeof loc.recorded_at_utc !== 'string') {
    return `${prefix}recorded_at_utc is required as an ISO 8601 UTC timestamp string.`;
  }

  const recordedTime = new Date(loc.recorded_at_utc).getTime();
  if (isNaN(recordedTime)) {
    return `${prefix}recorded_at_utc is not a valid date.`;
  }

  const now = Date.now();
  if (recordedTime > now + MAX_FUTURE_SKEW_MS) {
    return `${prefix}recorded_at_utc cannot be in the future beyond 5 minutes clock-skew tolerance.`;
  }

  if (recordedTime < MIN_VALID_TIMESTAMP) {
    return `${prefix}recorded_at_utc is too far in the past (before 2020).`;
  }

  return null; // Valid
}

function validateLocationPayload(req, res, next) {
  const body = req.body;
  if (!body) {
    return res.status(400).json({ error: 'Request body is required.' });
  }

  // Check if batch or single
  const records = Array.isArray(body) ? body : (Array.isArray(body.locations) ? body.locations : [body]);

  if (records.length === 0) {
    return res.status(400).json({ error: 'No location records provided.' });
  }

  if (records.length > 500) {
    return res.status(400).json({ error: 'Batch size limit exceeded. Maximum 500 records per request.' });
  }

  const errors = [];
  for (let i = 0; i < records.length; i++) {
    const err = validateSingleLocationRecord(records[i], records.length > 1 ? i : null);
    if (err) {
      errors.push(err);
    }
  }

  if (errors.length > 0) {
    return res.status(400).json({
      error: 'Location validation failed.',
      details: errors
    });
  }

  // Normalize parsed records
  req.validatedLocations = records.map((loc) => ({
    event_id: loc.event_id.trim(),
    latitude: Number(loc.latitude),
    longitude: Number(loc.longitude),
    accuracy_meters: Number(loc.accuracy_meters),
    altitude_meters: loc.altitude_meters !== undefined && loc.altitude_meters !== null ? Number(loc.altitude_meters) : null,
    speed_meters_per_second: loc.speed_meters_per_second !== undefined && loc.speed_meters_per_second !== null ? Number(loc.speed_meters_per_second) : null,
    bearing_degrees: loc.bearing_degrees !== undefined && loc.bearing_degrees !== null ? Number(loc.bearing_degrees) : null,
    battery_percentage: Math.round(Number(loc.battery_percentage)),
    recorded_at_utc: new Date(loc.recorded_at_utc).toISOString(),
    location_source: loc.location_source ? String(loc.location_source).trim() : 'fused',
    is_fresh_location: loc.is_fresh_location === false || loc.is_fresh_location === 0 ? 0 : 1
  }));

  next();
}

function validateHeartbeat(req, res, next) {
  const { battery_percentage } = req.body || {};

  if (battery_percentage !== undefined && battery_percentage !== null) {
    const battery = Number(battery_percentage);
    if (isNaN(battery) || !Number.isInteger(battery) || battery < 0 || battery > 100) {
      return res.status(400).json({
        error: 'battery_percentage, if provided, must be an integer between 0 and 100.'
      });
    }
    req.body.battery_percentage = Math.round(battery);
  }

  next();
}

module.exports = {
  validateRegister,
  validateLogin,
  validateDeviceRegister,
  validateLocationPayload,
  validateHeartbeat,
  validateSingleLocationRecord
};
