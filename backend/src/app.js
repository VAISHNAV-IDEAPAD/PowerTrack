const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const path = require('path');
const fs = require('fs');
const config = require('./config');
const { generalLimiter } = require('./middleware/rateLimiter');
const authRoutes = require('./routes/authRoutes');
const deviceRoutes = require('./routes/deviceRoutes');

const app = express();

// Trust reverse proxy / tunnel headers (Cloudflare, Render, etc.)
app.set('trust proxy', 1);

// Security HTTP headers (configured to allow Leaflet CDN and tiles)
app.use(
  helmet({
    contentSecurityPolicy: false // Allows OpenStreetMap tile images and Leaflet CDN styles
  })
);

// CORS configuration
app.use(cors({
  origin: config.corsOrigin === '*' ? '*' : config.corsOrigin.split(','),
  methods: ['GET', 'POST', 'PATCH', 'DELETE', 'OPTIONS'],
  allowedHeaders: ['Content-Type', 'Authorization']
}));

// Body parsers
app.use(express.json({ limit: '2mb' }));
app.use(express.urlencoded({ extended: true, limit: '2mb' }));

// Apply general rate limiting
app.use(generalLimiter);

// Health check endpoint (unauthenticated)
app.get('/api/health', (req, res) => {
  res.status(200).json({
    status: 'ok',
    timestamp: new Date().toISOString(),
    uptime: Math.round(process.uptime()),
    version: '1.0.0',
    service: 'powertrack-backend'
  });
});

// Mount application API routes
app.use('/api/auth', authRoutes);
app.use('/api/devices', deviceRoutes);

// Static Web Dashboard serving (if built dist directory exists)
const dashboardDist = path.resolve(__dirname, '../../dashboard/dist');
if (fs.existsSync(dashboardDist)) {
  app.use(express.static(dashboardDist));
  app.get('*', (req, res, next) => {
    if (req.path.startsWith('/api')) {
      return next();
    }
    res.sendFile(path.join(dashboardDist, 'index.html'));
  });
}

// 404 Not Found handler for unmatched API routes
app.use((req, res) => {
  res.status(404).json({
    error: `Route ${req.method} ${req.originalUrl} not found.`
  });
});

// Global error handler
app.use((err, req, res, next) => {
  console.error(`Unhandled error at ${req.method} ${req.originalUrl}:`, err.message);

  const statusCode = err.status || err.statusCode || 500;
  const message = config.nodeEnv === 'production' && statusCode === 500
    ? 'Internal server error.'
    : err.message || 'Internal server error.';

  res.status(statusCode).json({ error: message });
});

module.exports = app;
