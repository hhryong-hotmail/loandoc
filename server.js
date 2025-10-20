const express = require('express');
const cors = require('cors');
const http = require('http');

const app = express();

// Middlewares
// Serve static files (e.g., signup.html, index.html) from project root
app.use(express.static(__dirname));

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
      "connect-src 'self' http://127.0.0.1:8081",
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

// Register endpoint
app.post('/api/register', (req, res) => {
  const payload = JSON.stringify(req.body || {});
  const options = {
    host: '127.0.0.1',
    port: 8080,
    path: '/server/api/register',
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Content-Length': Buffer.byteLength(payload)
    },
    timeout: 10000
  };

  const proxyReq = http.request(options, (proxyRes) => {
    res.status(proxyRes.statusCode || 502);
    // 그대로 스트림 파이프하여 Tomcat 응답을 전달
    proxyRes.pipe(res);
  });

  proxyReq.on('timeout', () => {
    proxyReq.destroy(new Error('upstream timeout'));
  });

  proxyReq.on('error', (err) => {
    console.error('[REGISTER PROXY] error:', err.message);
    res.status(502).json({ ok: false, error: `upstream error: ${err.message}` });
  });

  proxyReq.write(payload);
  proxyReq.end();
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

const PORT = process.env.PORT || 8081;
app.listen(PORT, () => {
  console.log(`LOANDOC API listening on http://127.0.0.1:${PORT}`);
});


