const express = require('express');
const { authenticateToken } = require('../middleware/auth');
const { verifyDeviceOwnership } = require('../middleware/ownership');
const {
  validateDeviceRegister,
  validateLocationPayload,
  validateHeartbeat
} = require('../middleware/validate');
const {
  registerDevice,
  listDevices,
  getLatestLocation,
  getLocationHistory,
  heartbeat,
  updateDeviceSettings
} = require('../controllers/deviceController');
const { uploadLocations } = require('../controllers/locationController');

const router = express.Router();

// All device routes require authentication
router.use(authenticateToken);

// Device management
router.post('/register', validateDeviceRegister, registerDevice);
router.get('/', listDevices);

// Device-specific endpoints (ownership verified)
router.get('/:deviceId/latest-location', verifyDeviceOwnership, getLatestLocation);
router.get('/:deviceId/location-history', verifyDeviceOwnership, getLocationHistory);
router.post('/:deviceId/locations', verifyDeviceOwnership, validateLocationPayload, uploadLocations);
router.post('/:deviceId/heartbeat', verifyDeviceOwnership, validateHeartbeat, heartbeat);
router.patch('/:deviceId/settings', verifyDeviceOwnership, updateDeviceSettings);

module.exports = router;
