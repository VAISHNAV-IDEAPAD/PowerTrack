const config = require('../config');
const { getDatabase } = require('../db/database');

async function purgeOldLocations(retentionDaysOverride = null) {
  const days = retentionDaysOverride !== null ? retentionDaysOverride : config.locationRetentionDays;

  if (!days || days <= 0) {
    return { purged: 0, message: 'Retention policy is disabled (retention days <= 0).' };
  }

  const cutoffDate = new Date(Date.now() - days * 24 * 60 * 60 * 1000).toISOString();

  try {
    const db = await getDatabase();
    const result = await db.run(
      'DELETE FROM locations WHERE recorded_at_utc < ?',
      [cutoffDate]
    );

    return {
      purged: result.changes,
      cutoffDate,
      message: `Purged ${result.changes} records older than ${days} days (${cutoffDate}).`
    };
  } catch (err) {
    console.error('Error running retention policy purge:', err);
    throw err;
  }
}

module.exports = {
  purgeOldLocations
};
