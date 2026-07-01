import type { TranslationKey } from '../i18n';

export interface OnboardingStep {
  id: string;
  /** CSS selector for the element this step points at; omit for a centered (no-target) step. */
  targetSelector?: string;
  titleKey: TranslationKey;
  descriptionKey: TranslationKey;
  actionKey?: TranslationKey;
  position?: 'top' | 'bottom' | 'left' | 'right';
}

/**
 * Route-aware by construction: each step (after the centered welcome) targets an element that only
 * exists on one page. `OnboardingTourOverlay` silently fast-forwards past any step whose target isn't
 * in the current page's DOM, so the tour naturally continues as the user moves from login → dashboard.
 */
export const ONBOARDING_STEPS: OnboardingStep[] = [
  {
    id: 'login-welcome',
    titleKey: 'onboarding.steps.loginWelcome.title',
    descriptionKey: 'onboarding.steps.loginWelcome.description',
  },
  {
    id: 'login-demo-accounts',
    targetSelector: '[data-onboarding="demo-accounts"]',
    titleKey: 'onboarding.steps.demoAccounts.title',
    descriptionKey: 'onboarding.steps.demoAccounts.description',
    position: 'top',
  },
  {
    id: 'dashboard-reconciliation',
    targetSelector: '[data-onboarding="reconciliation-hero"]',
    titleKey: 'onboarding.steps.reconciliation.title',
    descriptionKey: 'onboarding.steps.reconciliation.description',
    position: 'bottom',
  },
  {
    id: 'nav-modules',
    targetSelector: '[data-onboarding="nav-purchasing"]',
    titleKey: 'onboarding.steps.navModules.title',
    descriptionKey: 'onboarding.steps.navModules.description',
    actionKey: 'onboarding.steps.navModules.tryIt',
    position: 'right',
  },
  // One step per module landing page (its `PageHeader`, or — for Audit, which has no PageHeader — its
  // title block). Title/description reuse each module's own nav label + subtitle strings verbatim, so
  // this sweep needed zero new i18n copy. Ordered to match the "Purchasing → Inventory → Manufacturing →
  // Sales" flow called out in the nav-modules step above, then the remaining modules in nav order.
  {
    id: 'module-purchasing',
    targetSelector: '[data-onboarding="module-purchasing"]',
    titleKey: 'nav.purchasing',
    descriptionKey: 'purchasing.subtitle',
    position: 'right',
  },
  {
    id: 'module-inventory',
    targetSelector: '[data-onboarding="module-inventory"]',
    titleKey: 'nav.inventory',
    descriptionKey: 'inventory.subtitle',
    position: 'right',
  },
  {
    id: 'module-manufacturing',
    targetSelector: '[data-onboarding="module-manufacturing"]',
    titleKey: 'nav.manufacturing',
    descriptionKey: 'manufacturing.page.subtitle',
    position: 'right',
  },
  {
    id: 'module-sales',
    targetSelector: '[data-onboarding="module-sales"]',
    titleKey: 'nav.sales',
    descriptionKey: 'sales.subtitle',
    position: 'right',
  },
  {
    id: 'module-masterdata',
    targetSelector: '[data-onboarding="module-masterdata"]',
    titleKey: 'nav.masterData',
    descriptionKey: 'masterdata.subtitle',
    position: 'right',
  },
  {
    id: 'module-reporting',
    targetSelector: '[data-onboarding="module-reporting"]',
    titleKey: 'nav.reporting',
    descriptionKey: 'reporting.subtitle',
    position: 'right',
  },
  {
    id: 'module-ledger',
    targetSelector: '[data-onboarding="module-ledger"]',
    titleKey: 'nav.ledger',
    descriptionKey: 'ledger.page.subtitle',
    position: 'right',
  },
  {
    id: 'module-audit',
    targetSelector: '[data-onboarding="module-audit"]',
    titleKey: 'nav.audit',
    descriptionKey: 'audit.subtitle',
    position: 'right',
  },
  {
    id: 'header-toggles',
    targetSelector: '[data-onboarding="header-language"]',
    titleKey: 'onboarding.steps.headerToggles.title',
    descriptionKey: 'onboarding.steps.headerToggles.description',
    position: 'bottom',
  },
];
