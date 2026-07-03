import { defineConfig, devices } from '@playwright/test';

/**
 * Two projects:
 *  - `local`  — deterministic, offline: Playwright builds+serves dist (see webServer) and specs mock /api.
 *               This is the blocking-CI regression suite (e2e/nav.spec.ts navigation + e2e/axe.spec.ts
 *               accessibility). Runs in the sandbox too (plain node:http loopback works; vite dev/preview
 *               does not).
 *  - `live`   — the conservative smoke (e2e/smoke.spec.ts) against a running target (deployed demo by
 *               default). Nightly + manual only, never a required gate. Set PLAYWRIGHT_BASE_URL to point it.
 *
 * When PLAYWRIGHT_BASE_URL is set (the nightly/live run) the local webServer is skipped.
 */
const PORT = Number(process.env.E2E_PORT ?? 4321);
const LIVE_BASE_URL = process.env.PLAYWRIGHT_BASE_URL;

export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['html', { open: 'never' }], ['list']] : 'list',
  use: { trace: 'on-first-retry' },
  projects: [
    {
      name: 'local',
      testMatch: /(nav|axe|assistant)\.spec\.ts/,
      use: { ...devices['Desktop Chrome'], baseURL: `http://127.0.0.1:${PORT}` },
    },
    {
      name: 'live',
      testMatch: /smoke\.spec\.ts/,
      use: { ...devices['Desktop Chrome'], baseURL: LIVE_BASE_URL ?? 'https://erp.terrychou.com' },
    },
  ],
  // Only serve locally when not pointed at a live target. Assumes `npm run build` produced dist/.
  webServer: LIVE_BASE_URL
    ? undefined
    : {
        command: 'node scripts/serve-dist.mjs',
        url: `http://127.0.0.1:${PORT}`,
        reuseExistingServer: !process.env.CI,
        timeout: 60_000,
        env: { PORT: String(PORT) },
      },
});
