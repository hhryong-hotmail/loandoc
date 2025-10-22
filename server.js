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
  // Accept DATABASE_URL or individual env vars
  const dbUrl = process.env.DB_URL || process.env.DATABASE_URL || 'postgresql://postgres@localhost:5432/loandoc';
  const user = process.env.DB_USER;
  const password = process.env.DB_PASSWORD;
  if (user && password) {
    return new Client({ connectionString: dbUrl, user, password });
  }
  return new Client({ connectionString: dbUrl });
}

const ENABLE_FILE_FALLBACK = (process.env.ENABLE_FILE_FALLBACK || 'true').toLowerCase() === 'true';

app.post('/api/register', async (req, res) => {
  const { id, password } = req.body || {};
  if (!id || !password) return res.status(400).json({ ok: false, error: 'id and password required' });

  // Try DB first
  const client = getDbClient();
  try {
    await client.connect();
    // simple table: user_account(id primary key, password_hash varchar, created_at timestamp)
    const check = await client.query('SELECT id FROM user_account WHERE id = $1', [id]);
    if (check.rowCount > 0) {
      return res.status(409).json({ ok: false, error: 'already exists' });
    }

    const hash = await bcrypt.hash(password, 10);
    const insert = await client.query('INSERT INTO user_account(id,password_hash,created_at) VALUES($1,$2,NOW())', [id, hash]);
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

const PORT = process.env.PORT || 8080;
app.listen(PORT, () => {
  console.log(`LOANDOC API listening on http://127.0.0.1:${PORT}`);
});


