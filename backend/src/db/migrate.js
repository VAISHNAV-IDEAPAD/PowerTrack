const { getDatabase } = require('./database');

async function migrate(dbOverride = null) {
  const db = dbOverride || (await getDatabase());

  const schema = `
    CREATE TABLE IF NOT EXISTS users (
      id TEXT PRIMARY KEY,
      email TEXT UNIQUE NOT NULL,
      password_hash TEXT NOT NULL,
      created_at TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS devices (
      id TEXT PRIMARY KEY,
      user_id TEXT NOT NULL,
      device_name TEXT NOT NULL,
      platform TEXT NOT NULL DEFAULT 'android',
      registered_at TEXT NOT NULL,
      last_seen_at TEXT,
      last_battery_percentage INTEGER,
      tracking_enabled INTEGER NOT NULL DEFAULT 1,
      FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
    );

    CREATE TABLE IF NOT EXISTS locations (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      event_id TEXT UNIQUE NOT NULL,
      device_id TEXT NOT NULL,
      latitude REAL NOT NULL,
      longitude REAL NOT NULL,
      accuracy_meters REAL NOT NULL,
      altitude_meters REAL,
      speed_meters_per_second REAL,
      bearing_degrees REAL,
      battery_percentage INTEGER NOT NULL,
      recorded_at_utc TEXT NOT NULL,
      received_at_utc TEXT NOT NULL,
      location_source TEXT NOT NULL DEFAULT 'fused',
      is_fresh_location INTEGER NOT NULL DEFAULT 1,
      FOREIGN KEY (device_id) REFERENCES devices (id) ON DELETE CASCADE
    );

    CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
    CREATE INDEX IF NOT EXISTS idx_devices_user ON devices(user_id);
    CREATE INDEX IF NOT EXISTS idx_locations_device_recorded ON locations(device_id, recorded_at_utc DESC);
    CREATE INDEX IF NOT EXISTS idx_locations_event_id ON locations(event_id);
  `;

  await db.exec(schema);
  return true;
}

if (require.main === module) {
  migrate()
    .then(() => {
      console.log('Database migration completed successfully.');
      process.exit(0);
    })
    .catch((err) => {
      console.error('Migration failed:', err);
      process.exit(1);
    });
}

module.exports = { migrate };
