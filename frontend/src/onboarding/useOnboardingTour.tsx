import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { useI18n } from '../i18n';
import { useAuth } from '../auth/useAuth';
import { notifySuccess } from '../lib/notify';
import { loadOnboardingState, resetOnboardingState, saveOnboardingState } from './onboardingPreference';
import { ONBOARDING_STEPS } from './steps';
import type { OnboardingStep } from './steps';
import { OnboardingTourOverlay } from './OnboardingTourOverlay';

interface OnboardingTourContextValue {
  /** Whether the tour hasn't been completed/skipped yet — the overlay only renders while this is true. */
  active: boolean;
  stepIndex: number;
  totalSteps: number;
  /** The steps reachable by the current user (RBAC-filtered); numbering/count derive from this list. */
  steps: OnboardingStep[];
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
  const { hasRole } = useAuth();
  const initial = useState(() => loadOnboardingState())[0];
  const [completed, setCompleted] = useState(initial?.completed ?? false);
  const [stepIndex, setStepIndexState] = useState(initial?.currentStep ?? 0);

  // RBAC filter: drop steps whose `requiredRole` the current user lacks. Numbering and total count then
  // derive from this per-user list, so a non-ADMIN sees a continuous 1..N-1 with no gap where Audit was.
  const steps = useMemo(
    () => ONBOARDING_STEPS.filter((step) => !step.requiredRole || hasRole(step.requiredRole)),
    [hasRole],
  );

  // A persisted index is against whatever step list existed last session; if the user's roles changed
  // (re-login) it may now point past the end. Clamp into range so numbering stays valid.
  const safeStepIndex = Math.min(stepIndex, Math.max(0, steps.length - 1));

  const setStepIndex = useCallback((index: number) => {
    setStepIndexState(index);
    saveOnboardingState({ completed: false, currentStep: index });
  }, []);

  const next = useCallback(() => {
    setStepIndexState((i) => {
      // Compute from the clamped base, not the raw persisted index: after a role downgrade + re-login the
      // stored index can point past the (now shorter) list, and reading it raw would mis-fire the
      // completion branch or stall.
      const base = Math.min(i, steps.length - 1);
      const nextIndex = base + 1;
      if (nextIndex >= steps.length) {
        setCompleted(true);
        saveOnboardingState({ completed: true, currentStep: steps.length });
        notifySuccess(t('onboarding.completionMessage'));
        return base;
      }
      saveOnboardingState({ completed: false, currentStep: nextIndex });
      return nextIndex;
    });
  }, [t, steps.length]);

  const previous = useCallback(() => {
    setStepIndexState((i) => {
      // Same clamped-base rule as next(): a stale over-range index must step back from the list's end, not
      // from the raw value (which would swallow the first Back press).
      const base = Math.min(i, steps.length - 1);
      const prevIndex = Math.max(0, base - 1);
      saveOnboardingState({ completed: false, currentStep: prevIndex });
      return prevIndex;
    });
  }, [steps.length]);

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
    () => ({
      active: !completed,
      stepIndex: safeStepIndex,
      totalSteps: steps.length,
      steps,
      next,
      previous,
      skip,
      restart,
      setStepIndex,
    }),
    [completed, safeStepIndex, steps, next, previous, skip, restart, setStepIndex],
  );

  return (
    <OnboardingTourContext.Provider value={value}>
      {children}
      <OnboardingTourOverlay />
    </OnboardingTourContext.Provider>
  );
}
