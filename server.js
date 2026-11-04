/**
 * Arena.ai Optional Local Development Reverse Proxy
 *
 * Note: the native Android mobile client connects directly to https://arena.ai
 * and does NOT require this local proxy server.
 * This proxy is provided solely as an optional development utility for inspecting
 * web traffic, headers, and upstream responses from a desktop environment.
 */

const express = require('express');
const os = require('os');
const QRCode = require('qrcode');
const { createProxyMiddleware } = require('http-proxy-middleware');

const app = express();
const PORT = process.env.PORT || 3000;
const HOST = process.env.HOST || '127.0.0.1';
const pkg = require('./package.json');

/**
 * Picks the address a phone on the same network can actually reach.
 * `0.0.0.0` and `127.0.0.1` are bind addresses, never usable in a QR code.
 */
function findLanAddress(bindHost) {
  const candidates = [];
  const interfaces = os.networkInterfaces();
  for (const [name, addresses] of Object.entries(interfaces)) {
    for (const address of addresses || []) {
      if (address.family === 'IPv4' && !address.internal) {
        candidates.push({ name, address: address.address });
      }
    }
  }

  if (bindHost && bindHost !== '0.0.0.0' && bindHost !== '127.0.0.1' && bindHost !== 'localhost') {
    const exact = candidates.find((candidate) => candidate.address === bindHost);
    if (exact) {
      return exact;
    }
  }

  const preferred = candidates.find((candidate) => candidate.address.startsWith('192.168.'))
    || candidates.find((candidate) => candidate.address.startsWith('10.'))
    || candidates.find((candidate) => /^172\.(1[6-9]|2\d|3[01])\./.test(candidate.address))
    || candidates[0];

  return preferred || { name: 'Loopback', address: '127.0.0.1' };
}

/**
 * Rejects requests whose Host header is not an address this server actually listens on.
 * A loopback-bound dev proxy that forwards session cookies is exactly the shape DNS
 * rebinding targets: a page in a local browser can resolve an attacker domain to
 * 127.0.0.1 and ride the proxy. Pinning Host makes that request fail before it gets there.
 *
 * Only applied while bound to loopback. Binding to 0.0.0.0 is a deliberate choice to expose
 * the proxy on the LAN (phone testing, Docker port publishing) where the Host the client
 * sends is the *host machine's* address, not this process's; those setups get the startup
 * warning instead. Extra names can be added with ALLOWED_HOSTS=a,b.
 */
const LOOPBACK_BIND = !HOST || HOST === '127.0.0.1' || HOST === 'localhost';
const ALLOWED_HOSTS = new Set(['127.0.0.1', 'localhost', '::1']);
for (const extra of String(process.env.ALLOWED_HOSTS || '').split(',')) {
  if (extra.trim()) ALLOWED_HOSTS.add(extra.trim());
}
try {
  ALLOWED_HOSTS.add(findLanAddress(HOST).address);
} catch (e) {
  /* interface enumeration failed; the explicit values above still apply */
}

app.use((req, res, next) => {
  if (!LOOPBACK_BIND) {
    next();
    return;
  }
  const hostname = String(req.headers.host || '').replace(/:\d+$/, '').replace(/^\[|\]$/g, '');
  if (hostname && !ALLOWED_HOSTS.has(hostname)) {
    res.status(403).json({ error: 'Forbidden: unexpected Host header for a loopback-only dev proxy' });
    return;
  }
  next();
});

// 1. Diagnostics & Health Endpoints
app.get('/api/health', (req, res) => {
  res.json({
    status: 'ok',
    timestamp: new Date().toISOString(),
    uptimeSeconds: Math.floor(process.uptime()),
    platform: process.platform,
    nodeVersion: process.version,
    memoryUsageMB: Math.round(process.memoryUsage().rss / 1024 / 1024)
  });
});

app.get('/api/version', (req, res) => {
  res.json({
    name: pkg.name,
    version: pkg.version,
    node: process.version
  });
});

app.get('/api/network-info', async (req, res) => {
  try {
    const primary = findLanAddress(HOST);
    const mobileUrl = `http://${primary.address}:${PORT}`;
    const qrDataUrl = await QRCode.toDataURL(mobileUrl, {
      errorCorrectionLevel: 'M',
      margin: 2,
      width: 300,
      color: { dark: '#000000', light: '#ffffff' }
    });

    res.json({
      port: PORT,
      bindHost: HOST,
      primaryIp: primary.address,
      primaryInterface: primary.name,
      mobileUrl,
      reachableFromLan: HOST === '0.0.0.0' || HOST === '::',
      qrDataUrl
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

app.get('/api/arena-status', async (req, res) => {
  const targetUrl = 'https://arena.ai/';
  const startTime = Date.now();

  try {
    const response = await fetch(targetUrl, {
      method: 'GET',
      redirect: 'follow',
      signal: AbortSignal.timeout(8000),
      headers: {
        'User-Agent': 'Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36'
      }
    });

    const latencyMs = Date.now() - startTime;
    const headersObj = {};
    response.headers.forEach((v, k) => { headersObj[k] = v; });

    res.json({
      online: response.ok,
      status: response.status,
      statusText: response.statusText,
      url: response.url,
      latencyMs,
      serverTime: headersObj['date'] || null,
      cloudflareRay: headersObj['cf-ray'] || null,
      xFrameOptions: headersObj['x-frame-options'] || 'NONE',
      contentSecurityPolicy: headersObj['content-security-policy'] ? 'PRESENT' : 'NONE'
    });
  } catch (err) {
    const latencyMs = Date.now() - startTime;
    res.json({
      online: false,
      error: err.message,
      latencyMs,
      targetUrl
    });
  }
});

// 2. Transparent Streaming Reverse Proxy to arena.ai for development inspection
const arenaProxy = createProxyMiddleware({
  target: 'https://arena.ai',
  changeOrigin: true,
  secure: true,
  ws: true,
  cookieDomainRewrite: '',
  on: {
    proxyReq: (proxyReq, req, res) => {
      // Optional header cleanup during dev proxying
      const headersToStrip = [
        'x-forwarded-for',
        'x-real-ip',
        'forwarded',
        'via'
      ];
      headersToStrip.forEach(h => proxyReq.removeHeader(h));
    },
    error: (err, req, res) => {
      console.error('Proxy Error:', err.message);
      if (res && typeof res.status === 'function' && !res.headersSent) {
        res.status(502).send('Error connecting to Arena.ai.');
      } else if (res && typeof res.write === 'function' && typeof res.end === 'function') {
        // WebSocket upgrade failures have no HTTP response object to answer with.
        try { res.end(); } catch (e) { /* socket already gone */ }
      }
    }
  }
});

app.use(arenaProxy);

// Start Server bound to loopback by default for security
const server = app.listen(PORT, HOST, () => {
  // http-proxy-middleware only self-attaches its 'upgrade' listener once an HTTP
  // request has passed through it, so a WebSocket-only client would never be proxied.
  // Wiring it explicitly makes ws:// handling independent of that first request.
  if (typeof arenaProxy.upgrade === 'function') {
    server.on('upgrade', arenaProxy.upgrade);
  }

  const lan = findLanAddress(HOST);
  console.log('================================================================');
  console.log('  ARENA.AI LOCAL DEVELOPMENT PROXY ACTIVE                       ');
  console.log('================================================================');
  console.log(`  Local URL:        http://${HOST}:${PORT}`);
  console.log(`  LAN URL:          http://${lan.address}:${PORT} (${lan.name})`);
  console.log('  Target:           https://arena.ai');
  console.log('  Note:             The Android APK connects directly to Arena  ');
  console.log('                    and does not require this development proxy.');
  if (HOST === '127.0.0.1' || HOST === 'localhost') {
    console.log('  Tip:              Set HOST=0.0.0.0 to make the LAN URL/QR   ');
    console.log('                    reachable from a phone on the same network.');
  } else {
    console.log('  WARNING:          Bound to all interfaces. This proxy forwards');
    console.log('                    arena.ai cookies and needs no authentication,');
    console.log('                    so anyone on your network can use this port as');
    console.log('                    a signed-in relay to Arena while it is running.');
    console.log('                    Stop it when you are done, or use 127.0.0.1.');
  }
  console.log('================================================================');
});

module.exports = { app, server, findLanAddress };
