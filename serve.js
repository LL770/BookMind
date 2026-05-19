const http = require('http');
const fs = require('fs');
const path = require('path');

const DIST = 'C:\\Users\\fantuan\\Desktop\\javaweb\\rent\\frontend\\dist';
const PORT = 3000;
const BACKEND = { host: '127.0.0.1', port: 8080 };
const MINIO = { host: '127.0.0.1', port: 9000, bucket: 'book-files' };

const MIME = {
  '.js': 'application/javascript',
  '.css': 'text/css',
  '.html': 'text/html; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.json': 'application/json',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.eot': 'application/vnd.ms-fontobject',
};

function serveFile(res, filePath) {
  const ext = path.extname(filePath).toLowerCase();
  fs.readFile(filePath, (err, data) => {
    if (err) { res.writeHead(404); res.end(); return }
    res.writeHead(200, { 'Content-Type': MIME[ext] || 'application/octet-stream', 'Cache-Control': 'no-cache' });
    res.end(data);
  });
}

function proxyTarget(req, res, target) {
  const options = {
    hostname: target.host,
    port: target.port,
    path: req.url,
    method: req.method,
    headers: { ...req.headers, host: target.host + ':' + target.port },
  };
  const proxy = http.request(options, proxyRes => {
    res.writeHead(proxyRes.statusCode, proxyRes.headers);
    proxyRes.pipe(res);
  });
  proxy.on('error', () => { res.writeHead(502); res.end() });
  req.pipe(proxy);
}

http.createServer((req, res) => {
  if (req.url.startsWith('/api/')) return proxyTarget(req, res, BACKEND);
  if (req.url.startsWith('/files/')) {
    req.url = req.url.replace('/files/', '/' + MINIO.bucket + '/');
    return proxyTarget(req, res, MINIO);
  }
  const filePath = path.join(DIST, req.url === '/' ? 'index.html' : req.url);
  if (fs.existsSync(filePath) && fs.statSync(filePath).isFile()) return serveFile(res, filePath);
  serveFile(res, path.join(DIST, 'index.html'));
}).listen(PORT, '0.0.0.0', () => console.log('Serving on http://0.0.0.0:' + PORT));
