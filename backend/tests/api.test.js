const request = require('supertest');
const app = require('../src/app');
const { getDatabase, resetDatabaseInstance } = require('../src/db/database');
const { migrate } = require('../src/db/migrate');
const { v4: uuidv4 } = require('uuid');

describe('PowerTrack Cloud Backend API Test Suite', () => {
  let db;

  // Test state
  let user1Token;
  let user1Id;
  let user2Token;
  let user2Id;
  let device1Id;
  let device2Id;

  beforeAll(async () => {
    process.env.NODE_ENV = 'test';
    process.env.DB_PATH = ':memory:';
    resetDatabaseInstance();
    db = await getDatabase(':memory:');
    await migrate(db);
  });

  afterAll(async () => {
    if (db) {
      await db.close();
    }
    resetDatabaseInstance();
  });

  // 1. Health check
  describe('GET /api/health', () => {
    it('should return 200 OK and health status', async () => {
      const res = await request(app).get('/api/health');
      expect(res.status).toBe(200);
      expect(res.body.status).toBe('ok');
      expect(res.body.service).toBe('powertrack-backend');
      expect(res.body.uptime).toBeDefined();
    });
  });

  // 2. Authentication: Register & Login
  describe('Authentication Endpoints', () => {
    it('should reject registration with invalid email', async () => {
      const res = await request(app)
        .post('/api/auth/register')
        .send({ email: 'not-an-email', password: 'ValidPassword123' });
      expect(res.status).toBe(400);
      expect(res.body.error).toMatch(/valid email/i);
    });

    it('should reject registration with password shorter than 8 characters', async () => {
      const res = await request(app)
        .post('/api/auth/register')
        .send({ email: 'user@example.com', password: '123' });
      expect(res.status).toBe(400);
      expect(res.body.error).toMatch(/at least 8 characters/i);
    });

    it('should successfully register User 1', async () => {
      const res = await request(app)
        .post('/api/auth/register')
        .send({ email: 'user1@example.com', password: 'Password12345!' });
      expect(res.status).toBe(201);
      expect(res.body.token).toBeDefined();
      expect(res.body.user.email).toBe('user1@example.com');
      user1Token = res.body.token;
      user1Id = res.body.user.id;
    });

    it('should reject registering duplicate email for User 1', async () => {
      const res = await request(app)
        .post('/api/auth/register')
        .send({ email: 'user1@example.com', password: 'AnotherPassword!' });
      expect(res.status).toBe(409);
      expect(res.body.error).toMatch(/already exists/i);
    });

    it('should successfully register User 2', async () => {
      const res = await request(app)
        .post('/api/auth/register')
        .send({ email: 'user2@example.com', password: 'Password12345!' });
      expect(res.status).toBe(201);
      user2Token = res.body.token;
      user2Id = res.body.user.id;
    });

    it('should login successfully with correct credentials', async () => {
      const res = await request(app)
        .post('/api/auth/login')
        .send({ email: 'user1@example.com', password: 'Password12345!' });
      expect(res.status).toBe(200);
      expect(res.body.token).toBeDefined();
      expect(res.body.user.id).toBe(user1Id);
    });

    it('should reject login with wrong password', async () => {
      const res = await request(app)
        .post('/api/auth/login')
        .send({ email: 'user1@example.com', password: 'WrongPassword' });
      expect(res.status).toBe(401);
      expect(res.body.error).toMatch(/invalid email or password/i);
    });

    it('should reject protected routes without authorization header', async () => {
      const res = await request(app).get('/api/devices');
      expect(res.status).toBe(401);
    });
  });

  // 3. Device Registration & Ownership
  describe('Device Management & Ownership', () => {
    it('should register Device 1 for User 1', async () => {
      device1Id = uuidv4();
      const res = await request(app)
        .post('/api/devices/register')
        .set('Authorization', `Bearer ${user1Token}`)
        .send({
          device_name: 'Pixel 8 Pro - User 1',
          platform: 'android',
          device_id: device1Id
        });
      expect(res.status).toBe(201);
      expect(res.body.device.id).toBe(device1Id);
      expect(res.body.device.device_name).toBe('Pixel 8 Pro - User 1');
    });

    it('should register Device 2 for User 2', async () => {
      device2Id = uuidv4();
      const res = await request(app)
        .post('/api/devices/register')
        .set('Authorization', `Bearer ${user2Token}`)
        .send({
          device_name: 'Galaxy S24 - User 2',
          platform: 'android',
          device_id: device2Id
        });
      expect(res.status).toBe(201);
      expect(res.body.device.id).toBe(device2Id);
    });

    it('should prevent User 1 from accessing User 2 device (403 Forbidden)', async () => {
      const res = await request(app)
        .get(`/api/devices/${device2Id}/latest-location`)
        .set('Authorization', `Bearer ${user1Token}`);
      expect(res.status).toBe(403);
      expect(res.body.error).toMatch(/do not have permission/i);
    });

    it('should prevent User 2 from updating User 1 device settings (403 Forbidden)', async () => {
      const res = await request(app)
        .patch(`/api/devices/${device1Id}/settings`)
        .set('Authorization', `Bearer ${user2Token}`)
        .send({ tracking_enabled: false });
      expect(res.status).toBe(403);
    });

    it('should return 404 for nonexistent device', async () => {
      const res = await request(app)
        .get(`/api/devices/${uuidv4()}/latest-location`)
        .set('Authorization', `Bearer ${user1Token}`);
      expect(res.status).toBe(404);
    });

    it('should list only User 1 devices when requested by User 1', async () => {
      const res = await request(app)
        .get('/api/devices')
        .set('Authorization', `Bearer ${user1Token}`);
      expect(res.status).toBe(200);
      expect(res.body.devices).toHaveLength(1);
      expect(res.body.devices[0].id).toBe(device1Id);
      expect(res.body.devices[0].status).toBe('UNKNOWN'); // No locations yet
    });
  });

  // 4. Location Ingestion & Validation
  describe('Location Ingestion and Validation', () => {
    it('should reject invalid latitude (> 90)', async () => {
      const res = await request(app)
        .post(`/api/devices/${device1Id}/locations`)
        .set('Authorization', `Bearer ${user1Token}`)
        .send({
          event_id: uuidv4(),
          latitude: 95.123,
          longitude: 77.209,
          accuracy_meters: 10.5,
          battery_percentage: 85,
          recorded_at_utc: new Date().toISOString()
        });
      expect(res.status).toBe(400);
      expect(res.body.error).toMatch(/validation failed/i);
    });

    it('should reject invalid longitude (< -180)', async () => {
      const res = await request(app)
        .post(`/api/devices/${device1Id}/locations`)
        .set('Authorization', `Bearer ${user1Token}`)
        .send({
          event_id: uuidv4(),
          latitude: 28.6139,
          longitude: -195.123,
          accuracy_meters: 10.5,
          battery_percentage: 85,
          recorded_at_utc: new Date().toISOString()
        });
      expect(res.status).toBe(400);
    });

    it('should reject coordinates (0, 0)', async () => {
      const res = await request(app)
        .post(`/api/devices/${device1Id}/locations`)
        .set('Authorization', `Bearer ${user1Token}`)
        .send({
          event_id: uuidv4(),
          latitude: 0,
          longitude: 0,
          accuracy_meters: 5,
          battery_percentage: 85,
          recorded_at_utc: new Date().toISOString()
        });
      expect(res.status).toBe(400);
      expect(res.body.details[0]).toMatch(/invalid \/ false GPS fix/i);
    });

    it('should reject negative or zero accuracy', async () => {
      const res = await request(app)
        .post(`/api/devices/${device1Id}/locations`)
        .set('Authorization', `Bearer ${user1Token}`)
        .send({
          event_id: uuidv4(),
          latitude: 28.6139,
          longitude: 77.209,
          accuracy_meters: 0,
          battery_percentage: 85,
          recorded_at_utc: new Date().toISOString()
        });
      expect(res.status).toBe(400);
    });

    it('should reject invalid battery percentage (> 100 or < 0)', async () => {
      const res = await request(app)
        .post(`/api/devices/${device1Id}/locations`)
        .set('Authorization', `Bearer ${user1Token}`)
        .send({
          event_id: uuidv4(),
          latitude: 28.6139,
          longitude: 77.209,
          accuracy_meters: 10,
          battery_percentage: 105,
          recorded_at_utc: new Date().toISOString()
        });
      expect(res.status).toBe(400);
    });

    it('should reject future timestamps beyond 5 minutes', async () => {
      const farFuture = new Date(Date.now() + 10 * 60 * 1000).toISOString();
      const res = await request(app)
        .post(`/api/devices/${device1Id}/locations`)
        .set('Authorization', `Bearer ${user1Token}`)
        .send({
          event_id: uuidv4(),
          latitude: 28.6139,
          longitude: 77.209,
          accuracy_meters: 10,
          battery_percentage: 50,
          recorded_at_utc: farFuture
        });
      expect(res.status).toBe(400);
      expect(res.body.details[0]).toMatch(/future beyond 5 minutes/i);
    });

    it('should accept valid location record and record accurately', async () => {
      const eventId = uuidv4();
      const recordedAt = new Date().toISOString();
      const res = await request(app)
        .post(`/api/devices/${device1Id}/locations`)
        .set('Authorization', `Bearer ${user1Token}`)
        .send({
          event_id: eventId,
          latitude: 28.6139,
          longitude: 77.2090,
          accuracy_meters: 14.8,
          altitude_meters: 216.5,
          speed_meters_per_second: 1.2,
          bearing_degrees: 180.0,
          battery_percentage: 65,
          recorded_at_utc: recordedAt,
          location_source: 'gps',
          is_fresh_location: true
        });
      expect(res.status).toBe(201);
      expect(res.body.inserted).toBe(1);
      expect(res.body.duplicates).toBe(0);

      // Verify latest location endpoint returns exact real coordinates
      const latestRes = await request(app)
        .get(`/api/devices/${device1Id}/latest-location`)
        .set('Authorization', `Bearer ${user1Token}`);
      expect(latestRes.status).toBe(200);
      expect(latestRes.body.latest_location.latitude).toBe(28.6139);
      expect(latestRes.body.latest_location.longitude).toBe(77.2090);
      expect(latestRes.body.latest_location.accuracy_meters).toBe(14.8);
      expect(latestRes.body.latest_location.battery_percentage).toBe(65);
      expect(latestRes.body.latest_location.is_stale).toBe(false);
    });

    it('should prevent duplicate insertion of identical event_id', async () => {
      const eventId = uuidv4();
      const payload = {
        event_id: eventId,
        latitude: 28.6140,
        longitude: 77.2095,
        accuracy_meters: 12.0,
        battery_percentage: 60,
        recorded_at_utc: new Date().toISOString()
      };

      // First upload
      const res1 = await request(app)
        .post(`/api/devices/${device1Id}/locations`)
        .set('Authorization', `Bearer ${user1Token}`)
        .send(payload);
      expect(res1.status).toBe(201);
      expect(res1.body.inserted).toBe(1);

      // Duplicate upload with same event_id
      const res2 = await request(app)
        .post(`/api/devices/${device1Id}/locations`)
        .set('Authorization', `Bearer ${user1Token}`)
        .send(payload);
      expect(res2.status).toBe(201);
      expect(res2.body.inserted).toBe(0);
      expect(res2.body.duplicates).toBe(1);
    });

    it('should support batch offline location upload in chronological order', async () => {
      const now = Date.now();
      const batch = [
        {
          event_id: uuidv4(),
          latitude: 28.6145,
          longitude: 77.2100,
          accuracy_meters: 8.5,
          battery_percentage: 20,
          recorded_at_utc: new Date(now - 120000).toISOString() // 2 mins ago
        },
        {
          event_id: uuidv4(),
          latitude: 28.6150,
          longitude: 77.2105,
          accuracy_meters: 9.0,
          battery_percentage: 10, // 10% low battery trigger record
          recorded_at_utc: new Date(now - 60000).toISOString() // 1 min ago
        }
      ];

      const res = await request(app)
        .post(`/api/devices/${device1Id}/locations`)
        .set('Authorization', `Bearer ${user1Token}`)
        .send({ locations: batch });

      expect(res.status).toBe(201);
      expect(res.body.processed).toBe(2);
      expect(res.body.inserted).toBe(2);
      expect(res.body.last_battery_percentage).toBe(10);
    });
  });

  // 5. Pagination and Date Filtering
  describe('Location History Pagination & Filtering', () => {
    it('should return paginated history with metadata', async () => {
      const res = await request(app)
        .get(`/api/devices/${device1Id}/location-history?page=1&limit=2`)
        .set('Authorization', `Bearer ${user1Token}`);

      expect(res.status).toBe(200);
      expect(res.body.locations).toHaveLength(2);
      expect(res.body.pagination.page).toBe(1);
      expect(res.body.pagination.limit).toBe(2);
      expect(res.body.pagination.total).toBeGreaterThanOrEqual(4);
      expect(res.body.pagination.totalPages).toBeGreaterThanOrEqual(2);
    });

    it('should filter locations by date range', async () => {
      const now = new Date();
      const past = new Date(Date.now() - 3600000); // 1 hour ago
      const future = new Date(Date.now() + 60000);

      const res = await request(app)
        .get(`/api/devices/${device1Id}/location-history?start_date=${past.toISOString()}&end_date=${future.toISOString()}`)
        .set('Authorization', `Bearer ${user1Token}`);

      expect(res.status).toBe(200);
      expect(res.body.locations.length).toBeGreaterThan(0);
    });
  });

  // 6. Heartbeat & Settings
  describe('Device Heartbeat and Settings Update', () => {
    it('should update device heartbeat and battery', async () => {
      const res = await request(app)
        .post(`/api/devices/${device1Id}/heartbeat`)
        .set('Authorization', `Bearer ${user1Token}`)
        .send({ battery_percentage: 95 });

      expect(res.status).toBe(200);
      expect(res.body.last_seen_at).toBeDefined();

      const devRes = await request(app)
        .get('/api/devices')
        .set('Authorization', `Bearer ${user1Token}`);
      const dev = devRes.body.devices.find((d) => d.id === device1Id);
      expect(dev.last_battery_percentage).toBe(95);
      expect(dev.status).toBe('ONLINE');
    });

    it('should update device settings', async () => {
      const res = await request(app)
        .patch(`/api/devices/${device1Id}/settings`)
        .set('Authorization', `Bearer ${user1Token}`)
        .send({
          tracking_enabled: false,
          device_name: 'Renamed Pixel Device'
        });

      expect(res.status).toBe(200);
      expect(res.body.device.tracking_enabled).toBe(0);
      expect(res.body.device.device_name).toBe('Renamed Pixel Device');
    });
  });
});
