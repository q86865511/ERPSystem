import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { useI18n } from '../i18n';
import { notifySuccess } from '../lib/notify';
import { loadOnboardingState, resetOnboardingState, saveOnboardingState } from './onboardingPreference';
import { ONBOARDING_STEPS } from './steps';
import { OnboardingTourOverlay } from './OnboardingTourOverlay';

interface OnboardingTourContextValue {
  /** Whether the tour hasn't been completed/skipped yet — the overlay only renders while this is true. */
  active: boolean;
  stepIndex: number;
  totalSteps: number;
  next: () => void;
  previous: () => void;
  skip: () => void;
  restart: () => void;
  /** Lets the overlay silently fast-forward past steps whose target isn't on the current page. */
  setStepIndex: (index: number) => void;
}

const OnboardingTourContext = createContext<OnboardingTourContextValue | null>(null);

export function useOnboardingTour(): OnboardingTourContextValue {
  const ctx = useContext(OnboardingTourContext);
  if (!ctx) throw new Error('useOnboardingTour must be used within OnboardingTourProvider');
  return ctx;
}

export function OnboardingTourProvider({ children }: { children: ReactNode }) {
  const { t } = useI18n();
  const initial = useState(() => loadOnboardingState())[0];
  const [completed, setCompleted] = useState(initial?.completed ?? false);
  const [stepIndex, setStepIndexState] = useState(initial?.currentStep ?? 0);

  const setStepIndex = useCallback((index: number) => {
    setStepIndexState(index);
    saveOnboardingState({ completed: false, currentStep: index });
  }, []);

  const next = useCallback(() => {
    setStepIndexState((i) => {
      const nextIndex = i + 1;
      if (nextIndex >= ONBOARDING_STEPS.length) {
        setCompleted(true);
        saveOnboardingState({ completed: true, currentStep: ONBOARDING_STEPS.length });
        notifySuccess(t('onboarding.completionMessage'));
        return i;
      }
      saveOnboardingState({ completed: false, currentStep: nextIndex });
      return nextIndex;
    });
  }, [t]);

  const previous = useCallback(() => {
    setStepIndexState((i) => {
      const prevIndex = Math.max(0, i - 1);
      saveOnboardingState({ completed: false, currentStep: prevIndex });
      return prevIndex;
    });
  }, []);

  const skip = useCallback(() => {
    setCompleted(true);
    setStepIndexState((i) => {
      saveOnboardingState({ completed: true, currentStep: i, dismissedAt: new Date().toISOString() });
      return i;
    });
  }, []);

  const restart = useCallback(() => {
    resetOnboardingState();
    setCompleted(false);
    setStepIndexState(0);
  }, []);

  const value = useMemo<OnboardingTourContextValue>(
    () => ({ active: !completed, stepIndex, totalSteps: ONBOARDING_STEPS.length, next, previous, skip, restart, setStepIndex }),
    [completed, stepIndex, next, previous, skip, restart, setStepIndex],
  );

  return (
    <OnboardingTourContext.Provider value={value}>
      {children}
      <OnboardingTourOverlay />
    </OnboardingTourContext.Provider>
  );
}
