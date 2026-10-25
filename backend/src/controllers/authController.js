const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const { v4: uuidv4 } = require('uuid');
const config = require('../config');
const { getDatabase } = require('../db/database');

async function register(req, res) {
  const { email, password } = req.body;

  try {
    const db = await getDatabase();

    // Check if user already exists
    const existing = await db.get('SELECT id FROM users WHERE email = ?', [email]);
    if (existing) {
      return res.status(409).json({ error: 'A user with this email address already exists.' });
    }

    // Hash password securely (12 rounds)
    const saltRounds = 12;
    const passwordHash = await bcrypt.hash(password, saltRounds);

    const userId = uuidv4();
    const createdAt = new Date().toISOString();

    await db.run(
      'INSERT INTO users (id, email, password_hash, created_at) VALUES (?, ?, ?, ?)',
      [userId, email, passwordHash, createdAt]
    );

    // Generate JWT token
    const token = jwt.sign(
      { userId, email },
      config.jwtSecret,
      { expiresIn: config.jwtExpiresIn }
    );

    return res.status(201).json({
      message: 'User registered successfully',
      token,
      user: {
        id: userId,
        email,
        created_at: createdAt
      }
    });
  } catch (err) {
    console.error('Registration error:', err);
    return res.status(500).json({ error: 'Internal server error during registration.' });
  }
}

async function login(req, res) {
  const { email, password } = req.body;

  try {
    const db = await getDatabase();
    const user = await db.get(
      'SELECT id, email, password_hash, created_at FROM users WHERE email = ?',
      [email]
    );

    if (!user) {
      return res.status(401).json({ error: 'Invalid email or password.' });
    }

    const isMatch = await bcrypt.compare(password, user.password_hash);
    if (!isMatch) {
      return res.status(401).json({ error: 'Invalid email or password.' });
    }

    const token = jwt.sign(
      { userId: user.id, email: user.email },
      config.jwtSecret,
      { expiresIn: config.jwtExpiresIn }
    );

    return res.status(200).json({
      message: 'Login successful',
      token,
      user: {
        id: user.id,
        email: user.email,
        created_at: user.created_at
      }
    });
  } catch (err) {
    console.error('Login error:', err);
    return res.status(500).json({ error: 'Internal server error during login.' });
  }
}

module.exports = {
  register,
  login
};
