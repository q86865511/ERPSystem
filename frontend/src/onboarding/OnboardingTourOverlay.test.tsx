import { MantineProvider } from '@mantine/core';
import { Notifications } from '@mantine/notifications';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { I18nProvider } from '../i18n';
import { theme } from '../theme';
import { OnboardingTourOverlay } from './OnboardingTourOverlay';
import type { OnboardingStep } from './steps';

// The overlay reads its step list + navigation callbacks from the tour context. Mock the hook so each test
// pins an exact set of steps and can observe next()/setStepIndex without the real provider/RBAC/persistence.
const tour = {
  active: true,
  stepIndex: 0,
  totalSteps: 3,
  steps: [] as OnboardingStep[],
  next: vi.fn(),
  previous: vi.fn(),
  skip: vi.fn(),
  restart: vi.fn(),
  setStepIndex: vi.fn(),
};
vi.mock('./useOnboardingTour', () => ({ useOnboardingTour: () => tour }));

/** Exposes the current pathname so navigation triggered by the overlay is observable. */
function LocationProbe() {
  const loc = useLocation();
  return <div data-testid="loc">{loc.pathname}</div>;
}

function renderOverlay(initialRoute = '/') {
  return render(
    <MantineProvider theme={theme} defaultColorScheme="light">
      <I18nProvider>
        <Notifications />
        <MemoryRouter initialEntries={[initialRoute]}>
          <LocationProbe />
          {/* A dashboard target the overlay can spotlight on '/'. */}
          <Routes>
            <Route path="/" element={<div data-onboarding="reconciliation-hero">hero</div>} />
            <Route path="/purchasing" element={<div data-onboarding="module-purchasing">purchasing</div>} />
          </Routes>
          <OnboardingTourOverlay />
        </MemoryRouter>
      </I18nProvider>
    </MantineProvider>,
  );
}

const scrollIntoViewMock = vi.fn();

beforeEach(() => {
  localStorage.setItem('erp.locale', 'en');
  tour.stepIndex = 0;
  tour.steps = [];
  tour.next.mockReset();
  tour.previous.mockReset();
  tour.setStepIndex.mockReset();
  // jsdom implements neither scrollIntoView nor a real layout; stub both.
  Element.prototype.scrollIntoView = scrollIntoViewMock;
  scrollIntoViewMock.mockReset();
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('OnboardingTourOverlay routing', () => {
  it('navigates to the target step route when the step lives on another page', async () => {
    // Current index points at a step whose route is '/purchasing' while we start on '/'.
    tour.stepIndex = 0;
    tour.steps = [
      {
        id: 'module-purchasing',
        targetSelector: '[data-onboarding="module-purchasing"]',
        titleKey: 'nav.purchasing',
        descriptionKey: 'purchasing.subtitle',
        route: '/purchasing',
      },
    ];
    renderOverlay('/');

    await waitFor(() => expect(screen.getByTestId('loc')).toHaveTextContent('/purchasing'));
  });

  it('does not fast-forward to a later every-page step while navigating to an off-page step', async () => {
    tour.stepIndex = 0;
    tour.steps = [
      {
        id: 'module-purchasing',
        targetSelector: '[data-onboarding="module-purchasing"]',
        titleKey: 'nav.purchasing',
        descriptionKey: 'purchasing.subtitle',
        route: '/purchasing',
      },
      // A later every-page step (header-language is always in the DOM here) that must NOT be shown
      // during the transition — the overlay must wait for '/purchasing' rather than jumping to it.
      {
        id: 'header-toggles',
        targetSelector: '[data-onboarding="header-language"]',
        titleKey: 'onboarding.steps.headerToggles.title',
        descriptionKey: 'onboarding.steps.headerToggles.description',
      },
    ];
    // Start on '/'. The header target is NOT rendered here, but the point stands: even had it been, the
    // overlay must navigate to '/purchasing' and land on the purchasing step, never skip ahead.
    const headerTarget = document.createElement('div');
    headerTarget.setAttribute('data-onboarding', 'header-language');
    document.body.appendChild(headerTarget);

    renderOverlay('/');

    // The overlay navigates to '/purchasing' and lands on the purchasing step (index 0) — it never
    // fast-forwards to the always-present header step, even though that target exists in the DOM.
    await waitFor(() => expect(screen.getByTestId('loc')).toHaveTextContent('/purchasing'));
    await waitFor(() => expect(screen.getByText('Purchasing')).toBeInTheDocument());
    expect(screen.queryByText('Language & Theme')).toBeNull();

    document.body.removeChild(headerTarget);
  });

  it('scrolls an off-screen target into view before spotlighting it', async () => {
    // Force the hero to report as outside the viewport so scrollIntoView is invoked.
    vi.spyOn(Element.prototype, 'getBoundingClientRect').mockReturnValue({
      top: 5000, left: 0, width: 100, height: 40, bottom: 5040, right: 100, x: 0, y: 5000, toJSON: () => ({}),
    } as DOMRect);

    tour.stepIndex = 0;
    tour.steps = [
      {
        id: 'dashboard-reconciliation',
        targetSelector: '[data-onboarding="reconciliation-hero"]',
        titleKey: 'onboarding.steps.reconciliation.title',
        descriptionKey: 'onboarding.steps.reconciliation.description',
        route: '/',
      },
    ];
    renderOverlay('/');

    await waitFor(() => expect(scrollIntoViewMock).toHaveBeenCalled());
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });
});
