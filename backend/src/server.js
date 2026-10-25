const http = require('http');
const app = require('./app');
const config = require('./config');
const { migrate } = require('./db/migrate');
const { getDatabase } = require('./db/database');
const { purgeOldLocations } = require('./services/retentionService');

let server = null;
let retentionInterval = null;

async function startServer() {
  try {
    // 1. Run database migrations
    console.log('[PowerTrack Backend] Initializing database...');
    await migrate();
    console.log('[PowerTrack Backend] Database initialized and migrations verified.');

    // 2. Schedule daily retention policy check
    if (config.locationRetentionDays > 0) {
      console.log(`[PowerTrack Backend] Retention policy enabled: ${config.locationRetentionDays} days.`);
      // Run once at startup
      purgeOldLocations().catch((err) => console.error('Retention purge error:', err));

      // Run every 24 hours
      retentionInterval = setInterval(() => {
        purgeOldLocations().catch((err) => console.error('Retention purge error:', err));
      }, 24 * 60 * 60 * 1000);
    }

    // 3. Start HTTP server
    server = http.createServer(app);
    server.listen(config.port, () => {
      console.log(`====================================================`);
      console.log(` PowerTrack Backend Server running on port ${config.port}`);
      console.log(` Environment: ${config.nodeEnv}`);
      console.log(` Database:    ${config.dbPath}`);
      console.log(` Health:      http://localhost:${config.port}/api/health`);
      console.log(`====================================================`);
    });

  } catch (err) {
    console.error('Fatal server startup error:', err);
    process.exit(1);
  }
}

// Graceful shutdown
async function gracefulShutdown(signal) {
  console.log(`\nReceived ${signal}. Shutting down gracefully...`);
  if (retentionInterval) {
    clearInterval(retentionInterval);
  }

  if (server) {
    server.close(async () => {
      console.log('HTTP server closed.');
      try {
        const db = await getDatabase();
        await db.close();
        console.log('Database connection closed.');
      } catch (err) {
        console.error('Error closing database:', err);
      }
      process.exit(0);
    });
  } else {
    process.exit(0);
  }
}

process.on('SIGTERM', () => gracefulShutdown('SIGTERM'));
process.on('SIGINT', () => gracefulShutdown('SIGINT'));

if (require.main === module) {
  startServer();
}

module.exports = { startServer, gracefulShutdown };
