import { expect, test } from '@playwright/test';

/**
 * Smoke test against a running target (the deployed demo by default; see playwright.config.ts). Deliberately
 * conservative + read-only so it is safe to run against the live, nightly-reset demo: it only asserts the SPA
 * boots and the login page renders. Deeper role/flow E2E can layer on top later.
 */
test('app boots and shows the login page', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveTitle(/Manufacturing ERP/i);
  // Login button text is bilingual (中 / EN) — match either.
  await expect(page.getByText(/sign in|登入/i).first()).toBeVisible();
});
