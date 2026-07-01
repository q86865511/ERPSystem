import { beforeEach, describe, expect, it } from 'vitest';
import { loadOnboardingState, resetOnboardingState, saveOnboardingState } from './onboardingPreference';

describe('onboardingPreference', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('returns null when nothing is persisted', () => {
    expect(loadOnboardingState()).toBeNull();
  });

  it('round-trips a saved state', () => {
    saveOnboardingState({ completed: false, currentStep: 2 });
    expect(loadOnboardingState()).toEqual({ completed: false, currentStep: 2 });
  });

  it('round-trips the optional dismissedAt field', () => {
    saveOnboardingState({ completed: true, currentStep: 1, dismissedAt: '2026-07-01T00:00:00.000Z' });
    expect(loadOnboardingState()).toEqual({
      completed: true,
      currentStep: 1,
      dismissedAt: '2026-07-01T00:00:00.000Z',
    });
  });

  it('returns null for malformed JSON rather than throwing', () => {
    localStorage.setItem('erp.onboarding', '{not json');
    expect(loadOnboardingState()).toBeNull();
  });

  it('returns null for a shape missing the required fields', () => {
    localStorage.setItem('erp.onboarding', JSON.stringify({ completed: true }));
    expect(loadOnboardingState()).toBeNull();
  });

  it('reset removes the persisted state', () => {
    saveOnboardingState({ completed: true, currentStep: 4 });
    resetOnboardingState();
    expect(loadOnboardingState()).toBeNull();
  });
});
