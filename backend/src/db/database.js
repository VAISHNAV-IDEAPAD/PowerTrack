const initSqlJs = require('sql.js');
const fs = require('fs');
const path = require('path');
const config = require('../config');

let dbInstance = null;
let SQL = null;

async function getDatabase(customPath) {
  if (dbInstance) {
    return dbInstance;
  }

  if (!SQL) {
    SQL = await initSqlJs();
  }

  const resolvedPath = customPath || config.dbPath;
  let rawDb;

  if (resolvedPath === ':memory:') {
    rawDb = new SQL.Database();
  } else {
    const fullPath = path.resolve(resolvedPath);
    const dir = path.dirname(fullPath);
    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, { recursive: true });
    }

    if (fs.existsSync(fullPath)) {
      const fileBuffer = fs.readFileSync(fullPath);
      rawDb = new SQL.Database(fileBuffer);
    } else {
      rawDb = new SQL.Database();
      const initialData = rawDb.export();
      fs.writeFileSync(fullPath, Buffer.from(initialData));
    }
  }

  // Enable foreign keys
  rawDb.run('PRAGMA foreign_keys = ON;');

  let inTransaction = false;
  const persist = () => {
    if (inTransaction) return; // Do not export during an active transaction, as export() commits/resets SQLite transaction state
    if (resolvedPath !== ':memory:') {
      const fullPath = path.resolve(resolvedPath);
      const data = rawDb.export();
      fs.writeFileSync(fullPath, Buffer.from(data));
    }
  };

  dbInstance = {
    raw: rawDb,

    async get(sql, params = []) {
      const stmt = rawDb.prepare(sql);
      try {
        if (params && params.length > 0) {
          stmt.bind(params);
        }
        if (stmt.step()) {
          return stmt.getAsObject();
        }
        return null;
      } finally {
        stmt.free();
      }
    },

    async query(sql, params = []) {
      const stmt = rawDb.prepare(sql);
      try {
        if (params && params.length > 0) {
          stmt.bind(params);
        }
        const rows = [];
        while (stmt.step()) {
          rows.push(stmt.getAsObject());
        }
        return rows;
      } finally {
        stmt.free();
      }
    },

    async run(sql, params = []) {
      rawDb.run(sql, params);
      const changes = rawDb.getRowsModified();
      let lastID = 0;
      try {
        const idRes = rawDb.exec('SELECT last_insert_rowid() as id');
        if (idRes.length > 0 && idRes[0].values.length > 0) {
          lastID = idRes[0].values[0][0];
        }
      } catch (e) {
        // Ignore if no insert was performed
      }
      persist();
      return { lastID, changes };
    },

    async exec(sql) {
      rawDb.run(sql);
      persist();
    },

    async transaction(callback) {
      inTransaction = true;
      rawDb.run('BEGIN TRANSACTION;');
      try {
        const result = await callback(this);
        rawDb.run('COMMIT;');
        inTransaction = false;
        persist();
        return result;
      } catch (err) {
        inTransaction = false;
        try {
          rawDb.run('ROLLBACK;');
        } catch (rbErr) {
          // ignore rollback failure if transaction already aborted
        }
        throw err;
      }
    },

    async close() {
      if (rawDb) {
        persist();
        rawDb.close();
      }
      dbInstance = null;
    }
  };

  return dbInstance;
}

function resetDatabaseInstance() {
  dbInstance = null;
}

module.exports = {
  getDatabase,
  resetDatabaseInstance
};
