import { MantineProvider } from '@mantine/core';
import { ModalsProvider } from '@mantine/modals';
import { Notifications } from '@mantine/notifications';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, type RenderResult } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactElement, ReactNode } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { I18nProvider } from '../i18n';
import { theme } from '../theme';

/**
 * Renders a component through the app's real provider stack (Mantine > i18n > Notifications > Query > Modals
 * > Router), mirroring main.tsx. A FRESH QueryClient per call (retry off, gcTime Infinity) keeps tests
 * isolated and stops error-path queries from retrying/hanging.
 */
export function renderWithProviders(
  ui: ReactElement,
  options?: { route?: string; colorScheme?: 'light' | 'dark' },
): RenderResult {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: Number.POSITIVE_INFINITY },
      mutations: { retry: false },
    },
  });

  function Wrapper({ children }: { children: ReactNode }) {
    return (
      // `forceColorScheme` pins the scheme for dark-mode parity tests; default stays light when unset.
      <MantineProvider theme={theme} defaultColorScheme="light" forceColorScheme={options?.colorScheme}>
        <I18nProvider>
          <Notifications />
          <QueryClientProvider client={queryClient}>
            <ModalsProvider>
              <MemoryRouter initialEntries={[options?.route ?? '/']}>{children}</MemoryRouter>
            </ModalsProvider>
          </QueryClientProvider>
        </I18nProvider>
      </MantineProvider>
    );
  }

  return render(ui, { wrapper: Wrapper });
}

export { userEvent };
export { screen, within, waitFor, act } from '@testing-library/react';
