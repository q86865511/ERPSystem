import { lazy, Suspense } from 'react';
import type { ComponentType, ReactNode } from 'react';
import { MantineProvider } from '@mantine/core';
import { ModalsProvider } from '@mantine/modals';
import { Notifications } from '@mantine/notifications';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RouterProvider, createMemoryRouter, Outlet, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { I18nProvider } from '../i18n';
import { theme } from '../theme';
import { OnboardingTourProvider } from './useOnboardingTour';
import { ONBOARDING_STEPS } from './steps';
import { loadOnboardingState, saveOnboardingState } from './onboardingPreference';

// Integration test: REAL provider + REAL overlay + a router whose target page is React.lazy so we can hold a
// route's chunk unresolved and reproduce the Suspense gap that used to make locate() ping-pong. Only useAuth
// is mocked (fixed ADMIN so the full step list is present, matching the un-filtered ONBOARDING_STEPS).
vi.mock('../auth/useAuth', () => ({
  useAuth: () => ({
    user: { username: 'admin', roles: ['ADMIN', 'ACCOUNTANT', 'WAREHOUSE', 'SALES', 'HR'] },
    bootstrapping: false,
    login: vi.fn(),
    logout: vi.fn(),
    hasRole: () => true,
    canDo: () => true,
  }),
}));

// jsdom has no layout/scroll; stub scrollIntoView so the overlay's off-screen handling is a no-op.
Element.prototype.scrollIntoView = vi.fn();

/** A deferred lazy component: its chunk stays pending until `resolve()` is called, modelling the Suspense gap. */
function makeControlledLazy(node: ReactNode) {
  let resolve!: () => void;
  const promise = new Promise<{ default: ComponentType }>((res) => {
    resolve = () => res({ default: () => <>{node}</> });
  });
  const Lazy = lazy(() => promise);
  return { Lazy, resolve };
}

/** Surfaces the current pathname so navigation is observable under MemoryRouter (no window.location). */
function LocationProbe() {
  const loc = useLocation();
  return <div data-testid="loc">{loc.pathname}</div>;
}

// Index of the module-purchasing step within the (unfiltered, ADMIN) step list — the first cross-page,
// lazy-loaded step. Its predecessor (nav-modules) lives on '/', so a Next press there crosses into the gap.
const PURCHASING_INDEX = ONBOARDING_STEPS.findIndex((s) => s.id === 'module-purchasing');
const NAV_MODULES_INDEX = ONBOARDING_STEPS.findIndex((s) => s.id === 'nav-modules');

function renderTour(PurchasingLazy: ComponentType, initialRoute = '/') {
  const router = createMemoryRouter(
    [
      {
        element: (
          <OnboardingTourProvider>
            <LocationProbe />
            <Outlet />
          </OnboardingTourProvider>
        ),
        children: [
          // The dashboard renders the nav-modules target so that step resolves on '/'.
          { path: '/', element: <div data-onboarding="nav-purchasing">nav purchasing</div> },
          {
            path: '/purchasing',
            element: (
              <Suspense fallback={<div data-testid="suspense-fallback">loading…</div>}>
                <PurchasingLazy />
              </Suspense>
            ),
          },
          { path: '/inventory', element: <div data-onboarding="module-inventory">inventory</div> },
        ],
      },
    ],
    { initialEntries: [initialRoute] },
  );

  return render(
    <MantineProvider theme={theme} defaultColorScheme="light">
      <I18nProvider>
        <Notifications />
        <ModalsProvider>
          <RouterProvider router={router} />
        </ModalsProvider>
      </I18nProvider>
    </MantineProvider>,
  );
}

describe('Onboarding tour ↔ overlay integration (lazy Suspense gap)', () => {
  beforeEach(() => {
    localStorage.clear();
    localStorage.setItem('erp.locale', 'en');
  });

  it('waits on the target route during the lazy gap and does not fast-forward past it', async () => {
    // Seed on the nav-modules step (on '/'), so pressing Next crosses into the lazy '/purchasing' route.
    saveOnboardingState({ completed: false, currentStep: NAV_MODULES_INDEX });
    const { Lazy, resolve } = makeControlledLazy(
      <div data-onboarding="module-purchasing">purchasing hero</div>,
    );

    renderTour(Lazy, '/');
    const user = userEvent.setup();

    // The nav-modules callout is on-screen first (its target is on '/').
    await waitFor(() => expect(screen.getByRole('dialog')).toBeInTheDocument());
    expect(screen.getByText('Navigate Modules')).toBeInTheDocument();

    // Press Next → advance to module-purchasing (route '/purchasing', chunk still pending).
    await user.click(screen.getByRole('button', { name: 'Next' }));

    // (a) During the Suspense gap the overlay must sit on '/purchasing' waiting — never advance to a LATER
    // route (e.g. '/inventory'). It also renders no callout while waiting.
    await waitFor(() => expect(screen.getByTestId('suspense-fallback')).toBeInTheDocument());
    expect(screen.getByTestId('loc')).toHaveTextContent('/purchasing');
    expect(screen.queryByRole('dialog')).toBeNull();

    // (b) Resolve the chunk → the purchasing target mounts, the observer re-locates, spotlight appears with
    // the correct step title and 1-based number (PURCHASING_INDEX + 1 of total).
    resolve();
    await waitFor(() => expect(screen.getByText('Purchasing')).toBeInTheDocument());
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(
      screen.getByText(`Step ${PURCHASING_INDEX + 1} of ${ONBOARDING_STEPS.length}`),
    ).toBeInTheDocument();

    // (c) The resolved index is persisted.
    await waitFor(() => expect(loadOnboardingState()?.currentStep).toBe(PURCHASING_INDEX));
  });
});
