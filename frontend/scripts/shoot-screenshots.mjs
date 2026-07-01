// Re-shoot docs/screenshots/*.png from a LOCAL production build, with no backend.
//
//   npm run build            # produce frontend/dist
//   npm run shoot            # this script -> docs/screenshots/*.png
//
// How it works: a tiny node static server serves dist/ over 127.0.0.1 (SPA fallback for client routes),
// Playwright drives Chromium against it, and every /api/** request is fulfilled from the captured
// fixtures (scripts/fixtures/api-fixtures.json, see capture-fixtures.mjs) — so the shots are deterministic,
// offline, and match the deployed demo's data. Auth is faked by replaying a canned session on
// /api/auth/refresh, so no login step is needed. Theme is driven by the emulated prefers-color-scheme
// (the app's MantineProvider uses defaultColorScheme="auto"), which needs no knowledge of Mantine's
// storage key. Locale + onboarding state are seeded in localStorage before the app boots.
//
// Not wired into CI or the build gate — it's a manual tool, run when the UI changes enough to re-shoot.
import http from 'node:http';
import { readFile, mkdir, stat } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, join, extname, normalize } from 'node:path';
import { chromium } from '@playwright/test';

const here = dirname(fileURLToPath(import.meta.url));
const DIST = join(here, '..', 'dist');
const OUT = join(here, '..', '..', 'docs', 'screenshots');
const FIX = join(here, 'fixtures', 'api-fixtures.json');

const VIEWPORT = { width: 1440, height: 900 }; // matches the existing screenshot set

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

// Hide the React Query devtools floating button (bundled in the prod build) so it never shows in a shot.
const HIDE_DEVTOOLS_CSS = `
  .tsqd-parent-container, [class*="tsqd"], [aria-label*="Devtools" i], [aria-label*="devtools" i] {
    display: none !important;
  }
`;

async function startServer() {
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
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  return server;
}

async function main() {
  const fixtures = JSON.parse(await readFile(FIX, 'utf8'));
  const AUTH = fixtures.auth ?? { accessToken: 'demo', username: 'admin', roles: ['ADMIN'] };
  const GET = fixtures.get ?? {};

  const server = await startServer();
  const { port } = server.address();
  const ORIGIN = `http://127.0.0.1:${port}`;
  console.log(`[shoot] serving ${DIST} at ${ORIGIN}`);

  const browser = await chromium.launch();
  const unmatched = new Set();

  async function shoot(name, { route, theme = 'light', locale = 'en', onboarding = 'done', prepare, waitMs = 1500 }) {
    const context = await browser.newContext({
      viewport: VIEWPORT,
      deviceScaleFactor: 1,
      colorScheme: theme, // drives Mantine "auto" -> light/dark deterministically
    });
    await context.addInitScript(
      (cfg) => {
        try {
          localStorage.setItem('erp.locale', cfg.locale);
          if (cfg.onboarding === 'show') localStorage.removeItem('erp.onboarding');
          else localStorage.setItem('erp.onboarding', JSON.stringify({ completed: true, currentStep: 99 }));
        } catch {
          /* storage unavailable — ignore */
        }
      },
      { locale, onboarding },
    );
    const page = await context.newPage();
    await page.route('**/api/**', async (r) => {
      const req = r.request();
      const p = new URL(req.url()).pathname;
      const json = (body) => r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
      if (p === '/api/auth/refresh' || p === '/api/auth/login') return json(AUTH);
      if (req.method() === 'GET') {
        if (Object.prototype.hasOwnProperty.call(GET, p)) return json(GET[p]);
        unmatched.add(p);
        return json([]);
      }
      return json({});
    });

    await page.goto(`${ORIGIN}${route}`, { waitUntil: 'networkidle', timeout: 30_000 });
    await page.addStyleTag({ content: HIDE_DEVTOOLS_CSS });
    if (prepare) await prepare(page);
    await page.waitForTimeout(waitMs); // let recharts / Mantine transitions settle
    await mkdir(OUT, { recursive: true });
    await page.screenshot({ path: join(OUT, `${name}.png`) });
    console.log(`[shoot] ${name}.png (${theme})`);
    await context.close();
  }

  const openNewItemModal = async (page) => {
    await page.getByRole('button', { name: 'New item' }).first().click();
    await page.getByRole('dialog').waitFor({ state: 'visible' });
  };
  const waitForTour = async (page) => {
    await page.getByRole('dialog').waitFor({ state: 'visible' });
  };

  await shoot('01-dashboard', { route: '/' });
  await shoot('02-masterdata', { route: '/masterdata' });
  await shoot('03-purchasing', { route: '/purchasing' });
  await shoot('04-sales', { route: '/sales' });
  await shoot('05-manufacturing', { route: '/manufacturing?tab=dashboard' });
  await shoot('06-inventory', { route: '/inventory?tab=dashboard' });
  await shoot('07-reporting', { route: '/reporting?tab=overview' });
  await shoot('08-ledger', { route: '/ledger' });
  await shoot('09-new-item-modal', { route: '/masterdata', prepare: openNewItemModal });
  await shoot('10-dark-mode', { route: '/', theme: 'dark' });
  await shoot('11-onboarding-tour', { route: '/', onboarding: 'show', prepare: waitForTour });

  await browser.close();
  server.close();

  if (unmatched.size) {
    console.warn(`[shoot] UNMATCHED /api endpoints (returned []), add to capture-fixtures.mjs:`);
    for (const p of unmatched) console.warn(`         ${p}`);
  }
  console.log('[shoot] done ->', OUT);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
