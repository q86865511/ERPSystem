/**
 * Persists onboarding-tour progress in localStorage. Mirrors `i18n/localePreference.ts`'s
 * load/save + try-catch shape (best-effort; storage may be unavailable in private mode).
 */
const KEY = 'erp.onboarding';

export interface OnboardingState {
  completed: boolean;
  currentStep: number;
  dismissedAt?: string;
}

export function loadOnboardingState(): OnboardingState | null {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return null;
    const parsed: unknown = JSON.parse(raw);
    if (
      typeof parsed === 'object' &&
      parsed !== null &&
      typeof (parsed as OnboardingState).completed === 'boolean' &&
      typeof (parsed as OnboardingState).currentStep === 'number'
    ) {
      return parsed as OnboardingState;
    }
    return null;
  } catch {
    return null;
  }
}

export function saveOnboardingState(state: OnboardingState): void {
  try {
    localStorage.setItem(KEY, JSON.stringify(state));
  } catch {
    // Storage may be unavailable (private mode / quota); preference is best-effort.
  }
}

export function resetOnboardingState(): void {
  try {
    localStorage.removeItem(KEY);
  } catch {
    // no-op
  }
}
