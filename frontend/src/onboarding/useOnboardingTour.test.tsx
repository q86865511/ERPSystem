import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderWithProviders, screen, userEvent } from '../test/test-utils';
import { loadOnboardingState, saveOnboardingState } from './onboardingPreference';
import { OnboardingTourProvider, useOnboardingTour } from './useOnboardingTour';
import { ONBOARDING_STEPS } from './steps';

// useOnboardingTour now RBAC-filters the step list via useAuth. Mock it so each test pins the roles it
// needs; hasRole is re-created per render from the mocked roles. Default: ADMIN (sees every step).
const authRoles = { current: ['ADMIN'] as string[] };
vi.mock('../auth/useAuth', () => ({
  useAuth: () => ({
    user: { username: 'admin', roles: authRoles.current },
    bootstrapping: false,
    login: vi.fn(),
    logout: vi.fn(),
    hasRole: (role: string) => authRoles.current.includes(role),
    canDo: () => true,
  }),
}));

// The overlay pulls in react-router navigation + DOM measurement that this provider-level test doesn't
// exercise; stub it so we isolate the step-model / RBAC logic.
vi.mock('./OnboardingTourOverlay', () => ({ OnboardingTourOverlay: () => null }));

const ADMIN_STEP_COUNT = ONBOARDING_STEPS.length;
const NON_ADMIN_STEP_COUNT = ONBOARDING_STEPS.filter((s) => !s.requiredRole).length;

function Harness() {
  const tour = useOnboardingTour();
  return (
    <div>
      <span data-testid="step">{tour.stepIndex}</span>
      <span data-testid="active">{String(tour.active)}</span>
      <span data-testid="total">{tour.totalSteps}</span>
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
    authRoles.current = ['ADMIN'];
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
    saveOnboardingState({ completed: false, currentStep: ADMIN_STEP_COUNT - 1 });
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

  describe('RBAC filtering', () => {
    it('an ADMIN sees every step', () => {
      authRoles.current = ['ADMIN'];
      renderHarness();
      expect(screen.getByTestId('total')).toHaveTextContent(String(ADMIN_STEP_COUNT));
    });

    it('a non-ADMIN loses exactly the ADMIN-only step(s) from the total', () => {
      authRoles.current = ['ACCOUNTANT'];
      renderHarness();
      expect(screen.getByTestId('total')).toHaveTextContent(String(NON_ADMIN_STEP_COUNT));
      expect(NON_ADMIN_STEP_COUNT).toBe(ADMIN_STEP_COUNT - 1);
    });

    it('clamps a persisted index that now points past the shorter non-ADMIN list', () => {
      // Persisted on the last ADMIN step; a non-ADMIN's list is one shorter, so the index must clamp.
      saveOnboardingState({ completed: false, currentStep: ADMIN_STEP_COUNT - 1 });
      authRoles.current = ['ACCOUNTANT'];
      renderHarness();
      expect(screen.getByTestId('step')).toHaveTextContent(String(NON_ADMIN_STEP_COUNT - 1));
    });
  });
});
