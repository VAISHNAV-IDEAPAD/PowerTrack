const path = require('path');
const dotenv = require('dotenv');

// Load environment variables
dotenv.config({ path: path.resolve(__dirname, '../../.env') });

module.exports = {
  port: parseInt(process.env.PORT, 10) || 5000,
  nodeEnv: process.env.NODE_ENV || 'development',
  jwtSecret: process.env.JWT_SECRET || 'powertrack_dev_secret_key_change_in_production_2026',
  jwtExpiresIn: process.env.JWT_EXPIRES_IN || '7d',
  dbPath: process.env.DB_PATH || path.resolve(__dirname, '../../data/powertrack.sqlite'),
  locationRetentionDays: parseInt(process.env.LOCATION_RETENTION_DAYS, 10) || 90,
  rateLimit: {
    windowMs: parseInt(process.env.RATE_LIMIT_WINDOW_MS, 10) || 15 * 60 * 1000, // 15 mins
    max: parseInt(process.env.RATE_LIMIT_MAX_REQUESTS, 10) || 500
  },
  corsOrigin: process.env.CORS_ORIGIN || '*',
  deviceOfflineThresholdSeconds: parseInt(process.env.DEVICE_OFFLINE_THRESHOLD_SECONDS, 10) || 300,
  locationStaleThresholdSeconds: parseInt(process.env.LOCATION_STALE_THRESHOLD_SECONDS, 10) || 900
};
