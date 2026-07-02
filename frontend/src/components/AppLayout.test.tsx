import { MantineProvider } from '@mantine/core';
import { ModalsProvider } from '@mantine/modals';
import { Notifications } from '@mantine/notifications';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RouterProvider, createMemoryRouter, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AppLayout } from './AppLayout';
import { I18nProvider } from '../i18n';
import { theme } from '../theme';

// AppLayout depends on auth + onboarding contexts; mock them so the test isolates the nav interaction
// (the bug under test is DOM/event behaviour, not auth). hasRole returns true so every item renders.
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

vi.mock('../onboarding/useOnboardingTour', () => ({
  useOnboardingTour: () => ({
    active: false,
    stepIndex: 0,
    totalSteps: 0,
    next: vi.fn(),
    previous: vi.fn(),
    skip: vi.fn(),
    restart: vi.fn(),
    setStepIndex: vi.fn(),
  }),
}));

/** Renders the current route so navigation is observable without real pages. */
function LocationProbe() {
  const loc = useLocation();
  return <div data-testid="loc">{loc.pathname + loc.search}</div>;
}

const NAV_PATHS = [
  '/',
  '/masterdata',
  '/purchasing',
  '/sales',
  '/manufacturing',
  '/inventory',
  '/reporting',
  '/ledger',
  '/hr',
  '/audit',
];

function renderApp(initialRoute = '/') {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: Number.POSITIVE_INFINITY }, mutations: { retry: false } },
  });
  const router = createMemoryRouter(
    [
      {
        element: <AppLayout />,
        children: NAV_PATHS.map((path) => ({ path, element: <LocationProbe /> })),
      },
    ],
    { initialEntries: [initialRoute] },
  );
  return render(
    <MantineProvider theme={theme} defaultColorScheme="light">
      <I18nProvider>
        <Notifications />
        <QueryClientProvider client={queryClient}>
          <ModalsProvider>
            <RouterProvider router={router} />
          </ModalsProvider>
        </QueryClientProvider>
      </I18nProvider>
    </MantineProvider>,
  );
}

describe('AppLayout navigation (ERP-001)', () => {
  beforeEach(() => {
    // Pin locale so we can select nav items by their English labels regardless of the host locale.
    localStorage.setItem('erp.locale', 'en');
  });

  it('navigates to a module when its parent nav item is clicked', async () => {
    const user = userEvent.setup();
    renderApp('/');
    expect(screen.getByTestId('loc')).toHaveTextContent('/');

    await user.click(screen.getByRole('link', { name: 'Purchasing' }));

    expect(screen.getByTestId('loc')).toHaveTextContent('/purchasing');
  });

  it('navigates when Enter is pressed on a focused parent nav item', async () => {
    const user = userEvent.setup();
    renderApp('/');

    const link = screen.getByRole('link', { name: 'Sales' });
    link.focus();
    await user.keyboard('{Enter}');

    expect(screen.getByTestId('loc')).toHaveTextContent('/sales');
  });

  it('expands a section via its chevron without navigating', async () => {
    const user = userEvent.setup();
    renderApp('/');

    const chevron = screen.getByRole('button', { name: /purchasing/i });
    expect(chevron).toHaveAttribute('aria-expanded', 'false');

    await user.click(chevron);

    expect(chevron).toHaveAttribute('aria-expanded', 'true');
    // Chevron toggles expansion only — the route must not change.
    expect(screen.getByTestId('loc')).toHaveTextContent('/');
  });

  it('expands the active module by default', () => {
    renderApp('/purchasing');
    expect(screen.getByRole('button', { name: /purchasing/i })).toHaveAttribute('aria-expanded', 'true');
    // A non-active module stays collapsed.
    expect(screen.getByRole('button', { name: /sales/i })).toHaveAttribute('aria-expanded', 'false');
  });
});
