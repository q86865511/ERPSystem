// Minimal static server for the built SPA (frontend/dist), used as Playwright's webServer for the
// deterministic local E2E run. Plain node:http on 127.0.0.1 with SPA fallback — the sandbox and CI can
// both bind this (unlike a vite dev/preview server). The app's /api calls are mocked in the specs.
//
//   npm run build && node scripts/serve-dist.mjs   # serves dist on http://127.0.0.1:4321
import http from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, join, extname, normalize } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const DIST = join(here, '..', 'dist');
const PORT = Number(process.env.PORT ?? 4321);

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.webp': 'image/webp',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.map': 'application/json; charset=utf-8',
  '.txt': 'text/plain; charset=utf-8',
};
const mimeFor = (p) => MIME[extname(p).toLowerCase()] ?? 'application/octet-stream';

const server = http.createServer(async (req, res) => {
  const pathname = decodeURIComponent(new URL(req.url, 'http://127.0.0.1').pathname);
  let filePath = normalize(join(DIST, pathname));
  if (!filePath.startsWith(DIST)) {
    res.writeHead(403);
    return res.end('forbidden');
  }
  try {
    const s = await stat(filePath);
    if (!s.isFile()) throw new Error('not a file');
  } catch {
    filePath = join(DIST, 'index.html'); // SPA fallback for client-side routes
  }
  try {
    const buf = await readFile(filePath);
    res.writeHead(200, { 'content-type': mimeFor(filePath), 'cache-control': 'no-store' });
    res.end(buf);
  } catch {
    res.writeHead(404);
    res.end('not found');
  }
});

server.listen(PORT, '127.0.0.1', () => {
  console.log(`[serve-dist] serving ${DIST} at http://127.0.0.1:${PORT}`);
});
