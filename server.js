const express = require('express');
const cors = require('cors');
const http = require('http');
const path = require('path');

const app = express();

// Middlewares
// Serve static files (index.html, signup.html) from the webapp directory used for WAR packaging
const staticDir = path.join(__dirname, 'server', 'src', 'main', 'webapp');
console.log('[static] serving files from', staticDir);
app.use(express.static(staticDir));

// Set a permissive, explicit CSP so Chrome devtools and fetch can connect
// Allows inline scripts/styles used by the current HTML files
app.use((req, res, next) => {
  res.setHeader(
    'Content-Security-Policy',
    [
      "default-src 'self'",
      "script-src 'self' 'unsafe-inline'",
      "style-src 'self' 'unsafe-inline'",
      "img-src 'self' data:",
      "connect-src 'self' http://127.0.0.1:8080",
      "frame-ancestors 'self'"
    ].join('; ')
  );
  next();
});

app.use(cors({
  origin: '*',
  methods: ['GET','POST','PUT','DELETE','OPTIONS'],
  allowedHeaders: ['Content-Type','Authorization']
}));
app.use(express.json());

// JSON 파싱 에러 핸들러: 잘못된 JSON 본문일 때 400으로 응답
app.use((err, req, res, next) => {
  if (err && err.type === 'entity.parse.failed') {
    console.error('[JSON PARSE] invalid JSON body:', err.message);
    return res.status(400).json({ ok: false, error: '잘못된 JSON 본문입니다.' });
  }
  if (err instanceof SyntaxError && 'body' in err) {
    console.error('[JSON PARSE] syntax error:', err.message);
    return res.status(400).json({ ok: false, error: '잘못된 JSON 본문입니다.' });
  }
  return next(err);
});

// In-memory store for demo
const users = new Map();

// Health check
app.get('/health', (req, res) => {
  res.json({ ok: true, service: 'loandoc-api' });
});

// Register endpoint - handle directly in Node (DB-backed)
const { Client } = require('pg');
const bcrypt = require('bcrypt');

function getDbClient() {
  try {
    // Get connection parameters
    const dbUrl = process.env.DB_URL || process.env.DATABASE_URL || 'postgresql://postgres:postgres@localhost:5432/loandoc';
    
    // Parse connection string if it exists
    let config = {};
    
    if (dbUrl) {
      // If using connection string, make sure it's properly formatted
      config.connectionString = dbUrl;
    } else {
      // Fallback to individual parameters
      config = {
        host: process.env.DB_HOST || 'localhost',
        port: process.env.DB_PORT || 5432,
        database: process.env.DB_NAME || 'loandoc',
        user: process.env.DB_USER || 'postgres',
        password: process.env.DB_PASSWORD || 'postgres', // Default password, should be changed in production
      };
    }
    
    // Ensure password is a string
    if (config.password !== undefined) {
      config.password = String(config.password);
    }
    
    // Add SSL configuration (important for some PostgreSQL installations)
    config.ssl = process.env.NODE_ENV === 'production' ? { rejectUnauthorized: false } : false;
    
    console.log('[DB] Creating new client with config:', {
      ...config,
      password: config.password ? '***' : 'not set'
    });
    
    return new Client(config);
  } catch (err) {
    console.error('[DB] Error creating client:', err);
    throw err;
  }
}

// By default do NOT use legacy file fallback unless explicitly enabled via env
const ENABLE_FILE_FALLBACK = (process.env.ENABLE_FILE_FALLBACK || 'false').toLowerCase() === 'true';

// helper: sanitize values for DB (module scope so multiple handlers can use it)
function sanitizeDbValue(key, value) {
  if (value === undefined || value === null) return null;
  // if boolean or number already, return as-is
  if (typeof value === 'number' || typeof value === 'boolean') return value;
  if (typeof value === 'string') {
    const s = value.trim();
    // date-like fields: empty -> null
    if (s === '') {
      if (/(date|birth|expiry)/i.test(key)) return null;
      if (/salary|amount|income|_count$/i.test(key)) return null;
      // preserve empty string for textual fields
      return '';
    }

    // remove non-breaking spaces and commas
    let cleaned = s.replace(/[\u00A0]/g, '').replace(/,/g, '');

    // numeric-like fields
    if (key === 'annual_salary' || /salary|amount|income|_count/i.test(key)) {
      const n = Number(cleaned);
      return isNaN(n) ? null : n;
    }

    // date-like non-empty: return cleaned (Postgres accepts 'YYYY-MM-DD' strings)
    if (/(date|birth|expiry)/i.test(key)) {
      return cleaned;
    }

    // default: return cleaned string
    return cleaned;
  }
  return value;
}

app.post('/api/register', async (req, res) => {
  const { id, password } = req.body || {};
  if (!id || !password) return res.status(400).json({ ok: false, error: 'id and password required' });

  // Try DB first
  const client = getDbClient();
  try {
    await client.connect();

    // Server-side: ensure required date fields are present and not empty
    const requiredDateFields = ['birth_date','entry_date','company_entry_date','stay_expiry_date'];
    for (const f of requiredDateFields) {
      if (!(f in payload) || payload[f] === null || String(payload[f]).toString().trim() === '') {
        return res.status(400).json({ ok: false, error: `${f} is required and cannot be empty` });
      }
    }

    // helper: sanitize values for DB
    function sanitizeDbValue(key, value) {
      if (value === undefined || value === null) return null;
      // if boolean or number already, return as-is
      if (typeof value === 'number' || typeof value === 'boolean') return value;
      if (typeof value === 'string') {
        const s = value.trim();
        // date-like fields: empty -> null
        if (s === '') {
          if (/(date|birth|expiry)/i.test(key)) return null;
          if (/salary|amount|income|_count$/i.test(key)) return null;
          // preserve empty string for textual fields
          return '';
        }

        // remove non-breaking spaces and commas
        let cleaned = s.replace(/[\u00A0]/g, '').replace(/,/g, '');

        // numeric-like fields
        if (key === 'annual_salary' || /salary|amount|income|_count/i.test(key)) {
          const n = Number(cleaned);
          return isNaN(n) ? null : n;
        }

        // date-like non-empty: return cleaned (Postgres accepts 'YYYY-MM-DD' strings)
        if (/(date|birth|expiry)/i.test(key)) {
          return cleaned;
        }

        // default: return cleaned string
        return cleaned;
      }
      return value;
    }
  // simple table: user_account(user_id primary key, password varchar (hashed), created_at timestamp)
  const check = await client.query('SELECT user_id FROM user_account WHERE user_id = $1', [id]);
    if (check.rowCount > 0) {
      return res.status(409).json({ ok: false, error: 'already exists' });
    }

    const hash = await bcrypt.hash(password, 10);
  const insert = await client.query('INSERT INTO user_account(user_id,password,created_at) VALUES($1,$2,NOW())', [id, hash]);
    return res.status(201).json({ ok: true });
  } catch (err) {
    console.error('[REGISTER] db error:', err.code || err.message, err);
    // If DB unavailable and file fallback enabled, save locally
    if (ENABLE_FILE_FALLBACK) {
      try {
        const fs = require('fs');
        const path = require('path');
        const usersFile = path.resolve(__dirname, 'users.json');
        const users = fs.existsSync(usersFile) ? JSON.parse(fs.readFileSync(usersFile, 'utf8')) : [];
        if (users.find(u => u.id === id)) return res.status(409).json({ ok: false, error: 'already exists' });
        const hash = await bcrypt.hash(password, 10);
        users.push({ id, password_hash: hash, created_at: new Date().toISOString() });
        fs.writeFileSync(usersFile, JSON.stringify(users, null, 2), 'utf8');
        return res.status(201).json({ ok: true, fallback: true });
      } catch (fsErr) {
        console.error('[REGISTER] file fallback failed:', fsErr.message);
        return res.status(500).json({ ok: false, error: 'db error and fallback failed' });
      }
    }
    return res.status(500).json({ ok: false, error: 'db error' });
  } finally {
    try { await client.end(); } catch(e){}
  }
});

// Save foreign worker profile into foreign_worker_master table
app.post('/api/foreign_worker_master', async (req, res) => {
  const payload = req.body || {};
  // Minimal validation: require userId
  const userId = payload.userId || payload.user_id || payload.id;
  if (!userId) return res.status(400).json({ ok: false, error: 'userId required' });

  const client = getDbClient();
  try {
    await client.connect();

    // New flow: if a row exists with the same user_id, always UPDATE it (regardless of passport)
    const byUser = await client.query('SELECT * FROM foreign_worker_master WHERE user_id = $1', [userId]);
    if (byUser.rowCount > 0) {
      const updateKeys = Object.keys(payload).filter(k => k !== 'user_id' && typeof payload[k] !== 'object');
      if (updateKeys.length === 0) {
        return res.json({ ok: true, action: 'noop', message: '변경할 항목이 없습니다' });
      }
      const setClause = updateKeys.map((k, idx) => `${k} = $${idx+1}`).join(',');
  const updateValues = updateKeys.map(k => sanitizeDbValue(k, payload[k]));
      updateValues.push(userId);
      const updateSql = `UPDATE foreign_worker_master SET ${setClause} WHERE user_id = $${updateKeys.length + 1}`;
      try {
        await client.query(updateSql, updateValues);
        return res.json({ ok: true, action: 'update', message: '수정되었습니다' });
      } catch (uerr) {
        console.error('[SAVE FOREIGN WORKER] update by user_id failed:', uerr && uerr.message ? uerr.message : uerr);
        if (uerr && uerr.code === '23505') {
          return res.status(409).json({ ok: false, error: 'unique constraint violated on update', details: uerr.constraint });
        }
        throw uerr;
      }
    }

    // No existing user_id -> handle by passport (insert or conflict)
    const passport = payload.passport_number;
    if (!passport) {
      return res.status(400).json({ ok: false, error: 'passport_number is required' });
    }
    const existingQ = await client.query('SELECT * FROM foreign_worker_master WHERE passport_number = $1', [passport]);
    if (existingQ.rowCount === 0) {
      // INSERT path
      const keys = Object.keys(payload).filter(k => typeof payload[k] !== 'object');
      const cols = keys.map(k => k).join(',');
      const params = keys.map((_, i) => `$${i+1}`).join(',');
  const values = keys.map(k => sanitizeDbValue(k, payload[k]));
  const insertSql = `INSERT INTO foreign_worker_master(${cols}) VALUES(${params})`;
  await client.query(insertSql, values);
      return res.json({ ok: true, action: 'insert', message: '서버에 저장되었습니다' });
    } else {
      // passport exists and no user match -> conflict
      return res.status(409).json({ ok: false, error: 'passport_number already exists for a different user_id' });
    }

    return res.status(201).json({ ok: true });
  } catch (err) {
    // Log incoming payload and full error for debugging (do not expose to client)
    try { console.error('[SAVE FOREIGN WORKER] payload:', payload); } catch(e){}
    console.error('[SAVE FOREIGN WORKER] db error:', err && err.message ? err.message : err);
    if (err && err.stack) console.error(err.stack);
  // Fallback: if DB fails, return 503 so client can fallback to localStorage
  // In development include the error message/code to help debugging (do NOT expose in production)
  const devInfo = (process.env.NODE_ENV === 'production') ? {} : { error_detail: err && err.message ? err.message : String(err), error_code: err && err.code ? err.code : undefined };
  return res.status(503).json(Object.assign({ ok: false, error: 'db error' }, devInfo));
  } finally {
    try { await client.end(); } catch(e){}
  }
});

function mapForeignWorkerRow(row) {
  if (!row) return null;
  return {
    name: row.name || null,
    nationality: row.nationality || null,
    passport_number: row.passport_number || null,
    birth_date: row.birth_date ? row.birth_date.toISOString().split('T')[0] : null,
    entry_date: row.entry_date ? row.entry_date.toISOString().split('T')[0] : null,
    phone_number: row.phone_number || null,
    current_company: row.current_company || null,
    email: row.email || null
  };
}

// Login endpoint - check DB (or file fallback) for id and bcrypt-hashed password
app.post('/api/login', async (req, res) => {
  const { id, password } = req.body || {};
  if (!id || !password) return res.status(400).json({ ok: false, error: 'id and password required' });

  const client = getDbClient();
  try {
    await client.connect();
    const q = await client.query('SELECT password FROM user_account WHERE user_id = $1', [id]);
    if (q.rowCount === 0) {
      return res.status(404).json({ ok: false, error: 'no_user' });
    }
    const hash = q.rows[0].password;
    const match = await bcrypt.compare(password, hash);
    if (match) return res.json({ ok: true });
    return res.status(401).json({ ok: false, error: 'invalid_credentials' });
  } catch (err) {
    console.error('[LOGIN] db error:', err && err.message ? err.message : err);
    // try file fallback if enabled
    if (ENABLE_FILE_FALLBACK) {
      try {
        const fs = require('fs');
        const path = require('path');
        const usersFile = path.resolve(__dirname, 'users.json');
        const users = fs.existsSync(usersFile) ? JSON.parse(fs.readFileSync(usersFile, 'utf8')) : [];
        const u = users.find(x => x.id === id);
        if (!u) return res.status(404).json({ ok: false, error: 'no_user' });
        const match = await bcrypt.compare(password, u.password_hash);
        if (match) return res.json({ ok: true, fallback: true });
        return res.status(401).json({ ok: false, error: 'invalid_credentials' });
      } catch (fsErr) {
        console.error('[LOGIN] fallback read failed:', fsErr && fsErr.message ? fsErr.message : fsErr);
        return res.status(500).json({ ok: false, error: 'db error' });
      }
    }
    return res.status(500).json({ ok: false, error: 'db error' });
  } finally {
    try { await client.end(); } catch (e) {}
  }
});

// List users (diagnostic)
app.get('/api/users', (req, res) => {
  res.json({ ok: true, users: Array.from(users.keys()) });
});

// Delete a user (diagnostic)
app.delete('/api/users/:id', (req, res) => {
  const { id } = req.params;
  const existed = users.delete(id);
  res.json({ ok: true, deleted: existed });
});

// Reset all (diagnostic)
app.post('/api/reset', (req, res) => {
  users.clear();
  res.json({ ok: true });
});

// -----------------------------
// Dashboard CRUD API
// -----------------------------
const ALLOWED_MSG_TYPES = ['공지','필독','추천','질문','일반'];

// List / search posts
app.get('/api/dashboard', async (req, res) => {
  const q = req.query.q ? String(req.query.q).trim() : null;
  const msg_type = req.query.msg_type ? String(req.query.msg_type).trim() : null;
  const page = Math.max(0, parseInt(req.query.page || '0', 10));
  const pageSize = Math.min(100, Math.max(1, parseInt(req.query.pageSize || '20', 10)));

  const client = getDbClient();
  try {
    await client.connect();
    const whereClauses = [];
    const params = [];
    let idx = 1;
    if (q) {
      whereClauses.push(`(title ILIKE $${idx} OR content ILIKE $${idx} OR author ILIKE $${idx})`);
      params.push(`%${q}%`);
      idx++;
    }
    if (msg_type && ALLOWED_MSG_TYPES.includes(msg_type)) {
      whereClauses.push(`msg_type = $${idx}`);
      params.push(msg_type);
      idx++;
    }
    const where = whereClauses.length ? ('WHERE ' + whereClauses.join(' AND ')) : '';
    params.push(pageSize);
    params.push(page * pageSize);
    const sql = `SELECT msg_id, created_at, author, title, msg_type, views FROM dashboard_messages ${where} ORDER BY created_at DESC LIMIT $${idx} OFFSET $${idx+1}`;
    const qres = await client.query(sql, params);
    return res.json({ ok: true, rows: qres.rows });
  } catch (err) {
    console.error('[DASHBOARD][LIST] db error:', err && err.message ? err.message : err);
    return res.status(500).json({ ok: false, error: 'db error' });
  } finally {
    try { await client.end(); } catch(e){}
  }
});

// Get single post and increment views
app.get('/api/dashboard/:id', async (req, res) => {
  const id = Number(req.params.id);
  if (!Number.isFinite(id)) return res.status(400).json({ ok: false, error: 'invalid id' });
  const client = getDbClient();
  try {
    await client.connect();
    const u = await client.query('UPDATE dashboard_messages SET views = views + 1 WHERE msg_id = $1 RETURNING *', [id]);
    if (u.rowCount === 0) return res.status(404).json({ ok: false, error: 'not_found' });
    return res.json({ ok: true, row: u.rows[0] });
  } catch (err) {
    console.error('[DASHBOARD][GET] db error:', err && err.message ? err.message : err);
    return res.status(500).json({ ok: false, error: 'db error' });
  } finally { try { await client.end(); } catch(e){} }
});

// Create post
app.post('/api/dashboard', async (req, res) => {
  const payload = req.body || {};
  const author = sanitizeDbValue('author', payload.author) || '';
  const password = sanitizeDbValue('password', payload.password);
  const title = sanitizeDbValue('title', payload.title) || '';
  const msg_type = sanitizeDbValue('msg_type', payload.msg_type) || '일반';
  const content = sanitizeDbValue('content', payload.content) || '';

  if (!title) return res.status(400).json({ ok: false, error: 'title required' });
  if (!ALLOWED_MSG_TYPES.includes(msg_type)) return res.status(400).json({ ok: false, error: 'invalid msg_type' });

  const client = getDbClient();
  try {
    await client.connect();
    const sql = `INSERT INTO dashboard_messages(author,password,title,msg_type,content) VALUES($1,$2,$3,$4,$5) RETURNING msg_id, created_at, author, title, msg_type, views`;
    const qres = await client.query(sql, [author, password, title, msg_type, content]);
    return res.status(201).json({ ok: true, row: qres.rows[0] });
  } catch (err) {
    console.error('[DASHBOARD][CREATE] db error:', err && err.message ? err.message : err);
    return res.status(500).json({ ok: false, error: 'db error' });
  } finally { try { await client.end(); } catch(e){} }
});

// Update post
app.put('/api/dashboard/:id', async (req, res) => {
  const id = Number(req.params.id);
  if (!Number.isFinite(id)) return res.status(400).json({ ok: false, error: 'invalid id' });
  const payload = req.body || {};
  const allowed = ['author','password','title','msg_type','content'];
  const keys = Object.keys(payload).filter(k => allowed.includes(k));
  if (keys.length === 0) return res.status(400).json({ ok: false, error: 'nothing to update' });
  if (payload.msg_type && !ALLOWED_MSG_TYPES.includes(payload.msg_type)) return res.status(400).json({ ok: false, error: 'invalid msg_type' });

  const setParts = [];
  const params = [];
  let idx = 1;
  for (const k of keys) {
    setParts.push(`${k} = $${idx}`);
    params.push(sanitizeDbValue(k, payload[k]));
    idx++;
  }
  params.push(id);
  const sql = `UPDATE dashboard_messages SET ${setParts.join(',')} WHERE msg_id = $${idx} RETURNING msg_id, created_at, author, title, msg_type, views`;
  const client = getDbClient();
  try {
    await client.connect();
    const qres = await client.query(sql, params);
    if (qres.rowCount === 0) return res.status(404).json({ ok: false, error: 'not_found' });
    return res.json({ ok: true, row: qres.rows[0] });
  } catch (err) {
    console.error('[DASHBOARD][UPDATE] db error:', err && err.message ? err.message : err);
    return res.status(500).json({ ok: false, error: 'db error' });
  } finally { try { await client.end(); } catch(e){} }
});

// Delete post
app.delete('/api/dashboard/:id', async (req, res) => {
  const id = Number(req.params.id);
  if (!Number.isFinite(id)) return res.status(400).json({ ok: false, error: 'invalid id' });
  const client = getDbClient();
  try {
    await client.connect();
    const q = await client.query('DELETE FROM dashboard_messages WHERE msg_id = $1', [id]);
    if (q.rowCount === 0) return res.status(404).json({ ok: false, error: 'not_found' });
    return res.json({ ok: true });
  } catch (err) {
    console.error('[DASHBOARD][DELETE] db error:', err && err.message ? err.message : err);
    return res.status(500).json({ ok: false, error: 'db error' });
  } finally { try { await client.end(); } catch(e){} }
});

// Get documents groups (약관 목록)
app.get('/api/server/groups', async (req, res) => {
  const client = getDbClient();
  try {
    await client.connect();
    const query = `
      SELECT DISTINCT group_name, select_option 
      FROM documents 
      WHERE group_name IS NOT NULL 
      ORDER BY group_name, select_option
    `;
    const result = await client.query(query);
    return res.json(result.rows);
  } catch (err) {
    console.error('[DOCUMENTS][GROUPS] db error:', err && err.message ? err.message : err);
    return res.status(500).json({ ok: false, error: 'db error' });
  } finally { 
    try { await client.end(); } catch(e){} 
  }
});

// Get document titles by group_name (약관 상세 내역)
app.get('/api/server/group-details', async (req, res) => {
  const groupName = req.query.group_name;
  if (!groupName) {
    return res.status(400).json({ ok: false, error: 'group_name is required' });
  }
  
  const client = getDbClient();
  try {
    await client.connect();
    const query = `
      SELECT title 
      FROM (
        SELECT DISTINCT group_name, title
        FROM documents 
        WHERE group_name = $1
      ) AS subquery
    `;
    const result = await client.query(query, [groupName]);
    return res.json(result.rows);
  } catch (err) {
    console.error('[DOCUMENTS][DETAILS] db error:', err && err.message ? err.message : err);
    return res.status(500).json({ ok: false, error: 'db error' });
  } finally { 
    try { await client.end(); } catch(e){} 
  }
});

// Get document content by title (약관 내용 조회)
app.get('/api/server/document-content', async (req, res) => {
  const title = req.query.title;
  if (!title) {
    return res.status(400).json({ ok: false, error: 'title is required' });
  }
  
  const client = getDbClient();
  try {
    await client.connect();
    const query = `
      SELECT content 
      FROM documents 
      WHERE title = $1
      LIMIT 1
    `;
    const result = await client.query(query, [title]);
    if (result.rows.length === 0) {
      return res.status(404).json({ ok: false, error: 'content not found' });
    }
    return res.json(result.rows[0]);
  } catch (err) {
    console.error('[DOCUMENTS][CONTENT] db error:', err && err.message ? err.message : err);
    return res.status(500).json({ ok: false, error: 'db error' });
  } finally { 
    try { await client.end(); } catch(e){} 
  }
});

// Loan estimate endpoint (대출 한도 조회)
app.post('/api/server/loan-estimate', async (req, res) => {
  try {
    const { loginId, nationality, remainMonths, annualIncome, age, workingMonths, visaType } = req.body;
    
    // Validate required fields
    if (!loginId || !nationality || !remainMonths || !annualIncome || !age || !workingMonths || !visaType) {
      return res.status(400).json({ ok: false, error: '모든 필드가 필요합니다' });
    }
    
    const today = new Date();
    
    // Normalize visa type: E9 -> E-9, F2 -> F-2, etc.
    const normalizeVisaType = (visa) => {
      if (!visa) return '';
      const cleaned = visa.replace(/[-\s]/g, '').toUpperCase(); // Remove - and spaces
      // Add hyphen back: E9 -> E-9
      return cleaned.replace(/([A-Z])(\d+)/, '$1-$2');
    };
    
    const normalizedVisaType = normalizeVisaType(visaType);
    
    // Bank configurations
    const banks = [
      {
        name: 'KB저축은행',
        rank: 1,
        config: {
          allowedVisaTypes: ['E-7', 'F-2', 'E-9', 'F-4', 'F-5', 'F-6'],
          excludedCountries: ['Vietnam'],
          minAge: 19,
          minVisaExpiryDays: 1, // 오늘 이후
          minEmploymentDays: 30,
          minAnnualIncome: 2000,
          estimatedLimit: 2000,
          estimatedRate: 14.7
        }
      },
      {
        name: '전북은행',
        rank: null, // Will be calculated
        config: {
          allowedVisaTypes: ['E-7', 'F-2', 'E-9', 'F-4', 'F-5', 'F-6'],
          minAge: 19,
          minVisaExpiryDays: 1,
          minEmploymentDays: 30,
          minAnnualIncome: 2000,
          estimatedLimit: 2000,
          estimatedRate: 12
        }
      },
      {
        name: 'OK저축은행',
        rank: null,
        config: {
          allowedVisaTypes: ['E-7', 'F-2', 'E-9', 'F-4', 'F-5', 'F-6'],
          minAge: 19,
          minVisaExpiryDays: 1,
          minEmploymentDays: 30,
          minAnnualIncome: 2000,
          estimatedLimit: 2000,
          estimatedRate: 15
        }
      },
      {
        name: '웰컴저축은행',
        rank: null,
        config: {
          allowedVisaTypes: ['E-7', 'F-2', 'E-9', 'F-4', 'F-5', 'F-6'],
          minAge: 19,
          minVisaExpiryDays: 1,
          minEmploymentDays: 30,
          minAnnualIncome: 2000,
          estimatedLimit: 2000,
          estimatedRate: 16
        }
      },
      {
        name: '예가람저축은행',
        rank: 5,
        config: {
          allowedVisaTypes: ['E-7', 'F-2', 'E-9', 'F-4', 'F-5', 'F-6'],
          minAge: 20,
          minVisaExpiryDays: 1,
          minEmploymentDays: 30,
          minAnnualIncome: 2000,
          estimatedLimit: null,
          estimatedRate: null
        }
      }
    ];
    
    // Process each bank
    const results = banks.map(bank => {
      const result = {
        bankName: bank.name,
        visaType: { valid: true, error: '' },
        country: { valid: true, error: '' },
        age: { valid: true, error: '' },
        visaExpiry: { valid: true, error: '' },
        employmentDate: { valid: true, error: '' },
        annualIncome: { valid: true, error: '' },
        estimatedLimit: bank.config.estimatedLimit,
        estimatedRate: bank.config.estimatedRate,
        rank: bank.rank
      };
      
      // Visa type validation - use normalized visa type
      if (!bank.config.allowedVisaTypes.includes(normalizedVisaType)) {
        result.visaType.valid = false;
        result.visaType.error = 'E비자종류';
      }
      
      // Country validation
      if (bank.config.excludedCountries && bank.config.excludedCountries.includes(nationality)) {
        result.country.valid = false;
        result.country.error = 'E국가';
      }
      if (bank.config.requiredCountries && !bank.config.requiredCountries.includes(nationality)) {
        result.country.valid = false;
        result.country.error = 'E국가';
      }
      
      // Age validation
      if (age <= bank.config.minAge) {
        result.age.valid = false;
        result.age.error = 'E나이';
      }
      
      // Visa expiry validation (assuming remainMonths represents visa validity)
      // If remainMonths <= 0, visa is expired
      if (remainMonths < bank.config.minVisaExpiryDays / 30) {
        result.visaExpiry.valid = false;
        result.visaExpiry.error = 'E비자만료';
      }
      
      // Employment date validation (workingMonths should be >= minEmploymentDays/30)
      if (workingMonths < bank.config.minEmploymentDays / 30) {
        result.employmentDate.valid = false;
        result.employmentDate.error = 'E재직일자';
      }
      
      // Annual income validation
      if (annualIncome < bank.config.minAnnualIncome) {
        result.annualIncome.valid = false;
        result.annualIncome.error = 'E연소득';
      }
      
      return result;
    });
    
    // Sort by interest rate (ascending), but keep KB at rank 1 and 예가람 at rank 5
    const sortedResults = [...results];
    
    // Assign ranks based on interest rate
    const middleBanks = sortedResults.filter(r => r.rank === null);
    middleBanks.sort((a, b) => {
      if (a.estimatedRate === null) return 1;
      if (b.estimatedRate === null) return -1;
      return a.estimatedRate - b.estimatedRate;
    });
    
    middleBanks.forEach((bank, idx) => {
      bank.rank = idx + 2; // Start from rank 2
    });
    
    // Sort by rank for final output
    sortedResults.sort((a, b) => a.rank - b.rank);
    
    return res.json(sortedResults);
    
  } catch (err) {
    console.error('[LOAN-ESTIMATE] error:', err);
    return res.status(500).json({ ok: false, error: 'server error' });
  }
});

const PORT = process.env.PORT || 8080;
app.listen(PORT, () => {
  console.log(`LOANDOC API listening on http://127.0.0.1:${PORT}`);
});