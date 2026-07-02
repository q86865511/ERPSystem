import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { expect, test, type Page } from '@playwright/test';

/**
 * Deterministic navigation regression for ERP-001 (project `local` in playwright.config.ts): served from a
 * built dist with every /api request mocked from the captured fixtures. Auth is faked by fulfilling
 * /api/auth/refresh with a canned admin session (same trick as scripts/shoot-screenshots.mjs), so the app
 * boots straight into the authenticated shell — no login step. Locale is pinned to English so nav items can
 * be selected by their labels.
 */
const fixtures = JSON.parse(
  readFileSync(fileURLToPath(new URL('../scripts/fixtures/api-fixtures.json', import.meta.url)), 'utf8'),
);
const AUTH = fixtures.auth ?? { accessToken: 'demo', username: 'admin', roles: ['ADMIN'] };
const GET: Record<string, unknown> = fixtures.get ?? {};

/** [label, path, hasChildren] for all 10 sidebar items (English labels; ADMIN sees Audit). */
const NAV: Array<[string, string, boolean]> = [
  ['Dashboard', '/', false],
  ['Master Data', '/masterdata', true],
  ['Purchasing', '/purchasing', true],
  ['Sales', '/sales', true],
  ['Manufacturing', '/manufacturing', true],
  ['Inventory', '/inventory', true],
  ['Reporting', '/reporting', true],
  ['Ledger', '/ledger', true],
  ['Human Resources', '/hr', true],
  ['Audit Trail', '/audit', false],
];

const pathname = (page: Page) => new URL(page.url()).pathname;

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
async function enterApp(page: Page) {
  await page.goto('/');
  await expect(page.getByRole('button', { name: /admin/i })).toBeVisible();
}

test.describe('sidebar navigation (ERP-001)', () => {
  test('every parent nav item navigates to its module on click', async ({ page }) => {
    await enterApp(page);
    const navbar = page.locator('.mantine-AppShell-navbar');

    for (const [label, path, hasChildren] of NAV) {
      await navbar.getByRole('link', { name: label, exact: true }).click();
      await expect.poll(() => pathname(page)).toBe(path);
      if (hasChildren) {
        // Navigating a parent auto-expands its section.
        await expect(
          navbar.getByRole('button', { name: `Toggle ${label} section`, exact: true }),
        ).toHaveAttribute('aria-expanded', 'true');
      }
    }
  });

  test('Enter on a focused parent nav item navigates', async ({ page }) => {
    await enterApp(page);
    const navbar = page.locator('.mantine-AppShell-navbar');
    await navbar.getByRole('link', { name: 'Reporting', exact: true }).focus();
    await page.keyboard.press('Enter');
    await expect.poll(() => pathname(page)).toBe('/reporting');
  });

  test('the chevron expands a section without navigating', async ({ page }) => {
    await enterApp(page);
    const navbar = page.locator('.mantine-AppShell-navbar');
    const chevron = navbar.getByRole('button', { name: 'Toggle Sales section', exact: true });
    await expect(chevron).toHaveAttribute('aria-expanded', 'false');

    await chevron.click();

    await expect(chevron).toHaveAttribute('aria-expanded', 'true');
    expect(pathname(page)).toBe('/'); // chevron toggles expansion only
  });
});

test.describe('mobile sidebar (ERP-001)', () => {
  test.use({ viewport: { width: 375, height: 812 } });

  test('parent label navigates and closes the drawer', async ({ page }) => {
    await enterApp(page);
    await page.locator('.mantine-Burger-root').click();
    const navbar = page.locator('.mantine-AppShell-navbar');
    const sales = navbar.getByRole('link', { name: 'Sales', exact: true });
    await expect(sales).toBeVisible();

    await sales.click();

    await expect.poll(() => pathname(page)).toBe('/sales');
    // Drawer slides off-canvas after navigating (Mantine keeps it mounted, so assert viewport, not DOM).
    await expect(sales).not.toBeInViewport();
  });

  test('chevron expands without navigating or closing the drawer', async ({ page }) => {
    await enterApp(page);
    await page.locator('.mantine-Burger-root').click();
    const navbar = page.locator('.mantine-AppShell-navbar');
    const chevron = navbar.getByRole('button', { name: 'Toggle Sales section', exact: true });

    await chevron.click();

    await expect(chevron).toHaveAttribute('aria-expanded', 'true');
    expect(pathname(page)).toBe('/'); // no navigation
    await expect(chevron).toBeVisible(); // drawer stayed open
  });
});
