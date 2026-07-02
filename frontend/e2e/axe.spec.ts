import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page, type TestInfo } from '@playwright/test';

/**
 * Accessibility regression (project `local` in playwright.config.ts): same offline, fixture-mocked setup
 * as e2e/nav.spec.ts (built dist + /api mocked from captured fixtures, auth faked via /api/auth/refresh).
 * Scans login, dashboard and a representative table page (Sales > Orders) with axe-core.
 *
 * Gate: serious + critical violations must be zero. moderate/minor are logged (test.info().attach) but do
 * not fail the run — see docs/TEST_REPORT_2026-07-02.md §3.7, which found the light dashboard already
 * clean; this spec pins that down as a regression guard rather than chasing every moderate nit.
 */
const fixtures = JSON.parse(
  readFileSync(fileURLToPath(new URL('../scripts/fixtures/api-fixtures.json', import.meta.url)), 'utf8'),
);
const AUTH = fixtures.auth ?? { accessToken: 'demo', username: 'admin', roles: ['ADMIN'] };
const GET: Record<string, unknown> = fixtures.get ?? {};

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('erp.locale', 'en');
    localStorage.setItem('erp.onboarding', JSON.stringify({ completed: true, currentStep: 99 }));
  });
  await page.route('**/api/**', async (route) => {
    const req = route.request();
    const p = new URL(req.url()).pathname;
    const json = (body: unknown) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
    if (p === '/api/auth/refresh' || p === '/api/auth/login') return json(AUTH);
    if (req.method() === 'GET') return json(Object.prototype.hasOwnProperty.call(GET, p) ? GET[p] : []);
    return json({});
  });
});

/** Boots the authenticated shell (refresh mock auto-authenticates) and waits for the header to render. */
async function enterApp(page: Page, path = '/') {
  await page.goto(path);
  await expect(page.getByRole('button', { name: /admin/i })).toBeVisible();
  // Never scan a half-rendered page: skeletons read as low-information "clean" DOM and would let a
  // real violation slip through as a false green.
  await expect(page.locator('.mantine-Skeleton-root')).toHaveCount(0);
}

/** Runs axe, splits violations by impact, and attaches the moderate/minor ones (informational only). */
async function scan(page: Page, testInfo: TestInfo, name: string) {
  const results = await new AxeBuilder({ page }).analyze();
  const blocking = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
  const informational = results.violations.filter((v) => v.impact !== 'serious' && v.impact !== 'critical');

  if (informational.length > 0) {
    await testInfo.attach(`${name}-moderate-or-below`, {
      body: JSON.stringify(informational.map((v) => ({ id: v.id, impact: v.impact, nodes: v.nodes.length })),
        null, 2),
      contentType: 'application/json',
    });
  }

  expect(blocking, JSON.stringify(blocking.map((v) => ({ id: v.id, impact: v.impact, nodes: v.nodes.length })),
    null, 2)).toEqual([]);
}

test.describe('accessibility (axe, light)', () => {
  test('login page has no serious/critical violations', async ({ page }, testInfo) => {
    // Stay unauthenticated for this page: the shared refresh mock would auto-login and race the
    // /login render into a redirect (later-registered routes win, so this overrides beforeEach).
    await page.route('**/api/auth/refresh', (route) =>
      route.fulfill({ status: 401, contentType: 'application/json', body: '{}' }));
    await page.goto('/login');
    await expect(page.getByRole('heading').first()).toBeVisible();
    await scan(page, testInfo, 'login-light');
  });

  test('dashboard has no serious/critical violations', async ({ page }, testInfo) => {
    await enterApp(page);
    await expect(page.getByText(/order pipeline/i)).toBeVisible();
    await scan(page, testInfo, 'dashboard-light');
  });

  test('sales orders table page has no serious/critical violations', async ({ page }, testInfo) => {
    await enterApp(page, '/sales');
    await expect(page.getByRole('tab', { name: /orders/i })).toBeVisible();
    await expect(page.locator('table tbody tr').first()).toBeVisible();
    await scan(page, testInfo, 'sales-orders-light');
  });
});

test.describe('accessibility (axe, dark)', () => {
  test.use({ colorScheme: 'dark' });

  test('dashboard has no serious/critical violations in dark mode', async ({ page }, testInfo) => {
    await enterApp(page);
    await expect(page.locator('html')).toHaveAttribute('data-mantine-color-scheme', 'dark');
    await expect(page.getByText(/order pipeline/i)).toBeVisible();
    await scan(page, testInfo, 'dashboard-dark');
  });
});
