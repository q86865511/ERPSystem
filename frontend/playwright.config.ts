import { defineConfig, devices } from '@playwright/test';

/**
 * E2E config. The sandbox can't bind a loopback server, so these run in CI (the `e2e` workflow) or locally
 * against an already-running target — by default the deployed demo. Override with PLAYWRIGHT_BASE_URL
 * (e.g. http://localhost:8081 for the docker demo). Kept out of the blocking build gate.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['html', { open: 'never' }], ['list']] : 'list',
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL ?? 'https://erp.terrychou.com',
    trace: 'on-first-retry',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
