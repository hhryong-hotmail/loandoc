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

// ==================== DASHBOARD API ENDPOINTS ====================
// In-memory storage for dashboard posts (fallback when DB is unavailable)
let dashboardPosts = [
  {
    id: 1,
    title: '환영합니다!',
    content: '외국인 근로자 대출 중개 포털 게시판입니다. 궁금한 사항이 있으시면 자유롭게 글을 작성해주세요.',
    author: 'LOANDOC 관리자',
    created_at: new Date().toISOString(),
    updated_at: new Date().toISOString()
  }
];
let nextPostId = 2;

// GET all dashboard posts
app.get('/api/dashboard', async (req, res) => {
  const client = getDbClient();
  try {
    await client.connect();
    const result = await client.query(
      'SELECT * FROM dashboard ORDER BY created_at DESC'
    );
    return res.json({ ok: true, posts: result.rows });
  } catch (err) {
    console.error('[DASHBOARD GET] db error, using in-memory fallback:', err.message);
    // Fallback to in-memory storage
    const sortedPosts = [...dashboardPosts].sort((a, b) => 
      new Date(b.created_at) - new Date(a.created_at)
    );
    return res.json({ ok: true, posts: sortedPosts, fallback: true });
  } finally {
    try { await client.end(); } catch(e){}
  }
});

// GET single dashboard post by ID
app.get('/api/dashboard/:id', async (req, res) => {
  const { id } = req.params;
  const client = getDbClient();
  try {
    await client.connect();
    const result = await client.query(
      'SELECT * FROM dashboard WHERE id = $1',
      [id]
    );
    if (result.rowCount === 0) {
      return res.status(404).json({ ok: false, error: 'post not found' });
    }
    return res.json({ ok: true, post: result.rows[0] });
  } catch (err) {
    console.error('[DASHBOARD GET ONE] db error, using in-memory fallback:', err.message);
    // Fallback to in-memory storage
    const post = dashboardPosts.find(p => p.id === parseInt(id));
    if (!post) {
      return res.status(404).json({ ok: false, error: 'post not found' });
    }
    return res.json({ ok: true, post, fallback: true });
  } finally {
    try { await client.end(); } catch(e){}
  }
});

// POST create new dashboard post
app.post('/api/dashboard', async (req, res) => {
  const { title, content, author } = req.body || {};
  if (!title || !content) {
    return res.status(400).json({ ok: false, error: 'title and content required' });
  }
  
  const client = getDbClient();
  try {
    await client.connect();
    const result = await client.query(
      'INSERT INTO dashboard(title, content, author, created_at, updated_at) VALUES($1, $2, $3, NOW(), NOW()) RETURNING *',
      [title, content, author || 'Anonymous']
    );
    return res.status(201).json({ ok: true, post: result.rows[0] });
  } catch (err) {
    console.error('[DASHBOARD POST] db error, using in-memory fallback:', err.message);
    // Fallback to in-memory storage
    const newPost = {
      id: nextPostId++,
      title,
      content,
      author: author || 'Anonymous',
      created_at: new Date().toISOString(),
      updated_at: new Date().toISOString()
    };
    dashboardPosts.push(newPost);
    return res.status(201).json({ ok: true, post: newPost, fallback: true });
  } finally {
    try { await client.end(); } catch(e){}
  }
});

// PUT update dashboard post
app.put('/api/dashboard/:id', async (req, res) => {
  const { id } = req.params;
  const { title, content } = req.body || {};
  
  if (!title && !content) {
    return res.status(400).json({ ok: false, error: 'title or content required' });
  }
  
  const client = getDbClient();
  try {
    await client.connect();
    
    // Check if post exists
    const check = await client.query('SELECT id FROM dashboard WHERE id = $1', [id]);
    if (check.rowCount === 0) {
      return res.status(404).json({ ok: false, error: 'post not found' });
    }
    
    // Build dynamic update query
    const updates = [];
    const values = [];
    let paramCount = 1;
    
    if (title) {
      updates.push(`title = $${paramCount}`);
      values.push(title);
      paramCount++;
    }
    if (content) {
      updates.push(`content = $${paramCount}`);
      values.push(content);
      paramCount++;
    }
    
    updates.push(`updated_at = NOW()`);
    values.push(id);
    
    const query = `UPDATE dashboard SET ${updates.join(', ')} WHERE id = $${paramCount} RETURNING *`;
    const result = await client.query(query, values);
    
    return res.json({ ok: true, post: result.rows[0] });
  } catch (err) {
    console.error('[DASHBOARD PUT] db error, using in-memory fallback:', err.message);
    // Fallback to in-memory storage
    const postIndex = dashboardPosts.findIndex(p => p.id === parseInt(id));
    if (postIndex === -1) {
      return res.status(404).json({ ok: false, error: 'post not found' });
    }
    
    if (title) dashboardPosts[postIndex].title = title;
    if (content) dashboardPosts[postIndex].content = content;
    dashboardPosts[postIndex].updated_at = new Date().toISOString();
    
    return res.json({ ok: true, post: dashboardPosts[postIndex], fallback: true });
  } finally {
    try { await client.end(); } catch(e){}
  }
});

// DELETE dashboard post
app.delete('/api/dashboard/:id', async (req, res) => {
  const { id } = req.params;
  
  const client = getDbClient();
  try {
    await client.connect();
    const result = await client.query(
      'DELETE FROM dashboard WHERE id = $1 RETURNING id',
      [id]
    );
    
    if (result.rowCount === 0) {
      return res.status(404).json({ ok: false, error: 'post not found' });
    }
    
    return res.json({ ok: true, deleted: true });
  } catch (err) {
    console.error('[DASHBOARD DELETE] db error, using in-memory fallback:', err.message);
    // Fallback to in-memory storage
    const postIndex = dashboardPosts.findIndex(p => p.id === parseInt(id));
    if (postIndex === -1) {
      return res.status(404).json({ ok: false, error: 'post not found' });
    }
    
    dashboardPosts.splice(postIndex, 1);
    return res.json({ ok: true, deleted: true, fallback: true });
  } finally {
    try { await client.end(); } catch(e){}
  }
});

// ==================== END DASHBOARD API ====================

const PORT = process.env.PORT || 8080;
app.listen(PORT, () => {
  console.log(`LOANDOC API listening on http://127.0.0.1:${PORT}`);
});


