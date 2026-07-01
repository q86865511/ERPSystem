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
  {
    id: 'header-toggles',
    targetSelector: '[data-onboarding="header-language"]',
    titleKey: 'onboarding.steps.headerToggles.title',
    descriptionKey: 'onboarding.steps.headerToggles.description',
    position: 'bottom',
  },
];
