import type { TranslationKey } from '../i18n';
import type { Role } from '../auth/roles';

export interface OnboardingStep {
  id: string;
  /** CSS selector for the element this step points at; omit for a centered (no-target) step. */
  targetSelector?: string;
  titleKey: TranslationKey;
  descriptionKey: TranslationKey;
  actionKey?: TranslationKey;
  position?: 'top' | 'bottom' | 'left' | 'right';
  /** Route on which this step's target lives; the overlay navigates here before showing the step. Omit for
   *  steps whose target exists on every authenticated page (e.g. the header) or that have no target at all. */
  route?: string;
  /** Only show this step to users holding this role; other users skip it and the total step count drops. */
  requiredRole?: Role;
}

/**
 * Route-aware by construction: each targeted step declares the `route` its target lives on.
 * `OnboardingTourOverlay` navigates to that route before showing the step (so cross-module steps are reached
 * instead of silently skipped) and only fast-forwards past a step when we're already on its route but the
 * target is genuinely absent. `useOnboardingTour` filters out `requiredRole` steps the current user can't
 * reach, so the total count and step numbering stay continuous per role.
 */
export const ONBOARDING_STEPS: OnboardingStep[] = [
  {
    id: 'login-welcome',
    titleKey: 'onboarding.steps.loginWelcome.title',
    descriptionKey: 'onboarding.steps.loginWelcome.description',
  },
  {
    id: 'dashboard-reconciliation',
    targetSelector: '[data-onboarding="reconciliation-hero"]',
    titleKey: 'onboarding.steps.reconciliation.title',
    descriptionKey: 'onboarding.steps.reconciliation.description',
    position: 'bottom',
    route: '/',
  },
  {
    id: 'nav-modules',
    targetSelector: '[data-onboarding="nav-purchasing"]',
    titleKey: 'onboarding.steps.navModules.title',
    descriptionKey: 'onboarding.steps.navModules.description',
    actionKey: 'onboarding.steps.navModules.tryIt',
    position: 'right',
    route: '/',
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
    route: '/purchasing',
  },
  {
    id: 'module-inventory',
    targetSelector: '[data-onboarding="module-inventory"]',
    titleKey: 'nav.inventory',
    descriptionKey: 'inventory.subtitle',
    position: 'right',
    route: '/inventory',
  },
  {
    id: 'module-manufacturing',
    targetSelector: '[data-onboarding="module-manufacturing"]',
    titleKey: 'nav.manufacturing',
    descriptionKey: 'manufacturing.page.subtitle',
    position: 'right',
    route: '/manufacturing',
  },
  {
    id: 'module-sales',
    targetSelector: '[data-onboarding="module-sales"]',
    titleKey: 'nav.sales',
    descriptionKey: 'sales.subtitle',
    position: 'right',
    route: '/sales',
  },
  {
    id: 'module-masterdata',
    targetSelector: '[data-onboarding="module-masterdata"]',
    titleKey: 'nav.masterData',
    descriptionKey: 'masterdata.subtitle',
    position: 'right',
    route: '/masterdata',
  },
  {
    id: 'module-reporting',
    targetSelector: '[data-onboarding="module-reporting"]',
    titleKey: 'nav.reporting',
    descriptionKey: 'reporting.subtitle',
    position: 'right',
    route: '/reporting',
  },
  {
    id: 'module-ledger',
    targetSelector: '[data-onboarding="module-ledger"]',
    titleKey: 'nav.ledger',
    descriptionKey: 'ledger.page.subtitle',
    position: 'right',
    route: '/ledger',
  },
  {
    id: 'module-audit',
    targetSelector: '[data-onboarding="module-audit"]',
    titleKey: 'nav.audit',
    descriptionKey: 'audit.subtitle',
    position: 'right',
    route: '/audit',
    requiredRole: 'ADMIN',
  },
  {
    id: 'header-toggles',
    targetSelector: '[data-onboarding="header-language"]',
    titleKey: 'onboarding.steps.headerToggles.title',
    descriptionKey: 'onboarding.steps.headerToggles.description',
    position: 'bottom',
  },
];
