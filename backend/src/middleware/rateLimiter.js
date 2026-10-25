const rateLimit = require('express-rate-limit');
const config = require('../config');

const generalLimiter = rateLimit({
  windowMs: config.rateLimit.windowMs,
  max: config.rateLimit.max,
  standardHeaders: true,
  legacyHeaders: false,
  validate: { xForwardedForHeader: false },
  message: {
    error: 'Too many requests from this IP address. Please try again later.'
  }
});

const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 30, // 30 requests per 15 mins for login/register
  standardHeaders: true,
  legacyHeaders: false,
  validate: { xForwardedForHeader: false },
  message: {
    error: 'Too many authentication attempts. Please try again after 15 minutes.'
  }
});

module.exports = {
  generalLimiter,
  authLimiter
};
