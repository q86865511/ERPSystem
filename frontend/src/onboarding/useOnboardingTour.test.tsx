import { beforeEach, describe, expect, it } from 'vitest';
import { renderWithProviders, screen, userEvent } from '../test/test-utils';
import { loadOnboardingState, saveOnboardingState } from './onboardingPreference';
import { OnboardingTourProvider, useOnboardingTour } from './useOnboardingTour';
import { ONBOARDING_STEPS } from './steps';

function Harness() {
  const tour = useOnboardingTour();
  return (
    <div>
      <span data-testid="step">{tour.stepIndex}</span>
      <span data-testid="active">{String(tour.active)}</span>
      <button onClick={tour.next}>next</button>
      <button onClick={tour.previous}>previous</button>
      <button onClick={tour.skip}>skip</button>
      <button onClick={tour.restart}>restart</button>
    </div>
  );
}

function renderHarness() {
  return renderWithProviders(
    <OnboardingTourProvider>
      <Harness />
    </OnboardingTourProvider>,
  );
}

describe('OnboardingTourProvider', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('starts active at step 0 when nothing is persisted', () => {
    renderHarness();
    expect(screen.getByTestId('step')).toHaveTextContent('0');
    expect(screen.getByTestId('active')).toHaveTextContent('true');
  });

  it('resumes from a persisted step', () => {
    saveOnboardingState({ completed: false, currentStep: 2 });
    renderHarness();
    expect(screen.getByTestId('step')).toHaveTextContent('2');
  });

  it('next() advances the step and persists it', async () => {
    renderHarness();
    await userEvent.click(screen.getByRole('button', { name: 'next' }));
    expect(screen.getByTestId('step')).toHaveTextContent('1');
    expect(loadOnboardingState()).toEqual({ completed: false, currentStep: 1 });
  });

  it('previous() steps back and never goes below 0', async () => {
    saveOnboardingState({ completed: false, currentStep: 1 });
    renderHarness();
    await userEvent.click(screen.getByRole('button', { name: 'previous' }));
    expect(screen.getByTestId('step')).toHaveTextContent('0');
    await userEvent.click(screen.getByRole('button', { name: 'previous' }));
    expect(screen.getByTestId('step')).toHaveTextContent('0');
  });

  it('next() past the last step marks the tour completed', async () => {
    saveOnboardingState({ completed: false, currentStep: ONBOARDING_STEPS.length - 1 });
    renderHarness();
    expect(screen.getByTestId('active')).toHaveTextContent('true');
    await userEvent.click(screen.getByRole('button', { name: 'next' }));
    expect(screen.getByTestId('active')).toHaveTextContent('false');
    expect(loadOnboardingState()?.completed).toBe(true);
  });

  it('skip() completes the tour and records dismissedAt', async () => {
    renderHarness();
    await userEvent.click(screen.getByRole('button', { name: 'skip' }));
    expect(screen.getByTestId('active')).toHaveTextContent('false');
    const state = loadOnboardingState();
    expect(state?.completed).toBe(true);
    expect(typeof state?.dismissedAt).toBe('string');
  });

  it('restart() clears persisted state and resets to step 0, active', async () => {
    renderHarness();
    await userEvent.click(screen.getByRole('button', { name: 'skip' }));
    expect(screen.getByTestId('active')).toHaveTextContent('false');
    await userEvent.click(screen.getByRole('button', { name: 'restart' }));
    expect(screen.getByTestId('step')).toHaveTextContent('0');
    expect(screen.getByTestId('active')).toHaveTextContent('true');
    expect(loadOnboardingState()).toBeNull();
  });
});
