import { QueryClient } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactElement } from 'react';
import { createMemoryRouter, MemoryRouter, RouterProvider } from 'react-router';
import { AppProviders } from '@/app/providers/AppProviders';
import { routes } from '@/app/router/routes';

/** A query client for tests: no retries (errors show at once) and no cache sharing between tests. */
export function testQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: Infinity, staleTime: Infinity } },
  });
}

/**
 * Renders the whole application (layout, lazy routes, providers) at a URL with a memory router. The API is mocked
 * by MSW, so the real query hooks, API modules and HTTP client run.
 */
export function renderApp(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  const user = userEvent.setup();
  const queryClient = testQueryClient();
  const view = render(
    <AppProviders queryClient={queryClient}>
      <RouterProvider router={router} />
    </AppProviders>,
  );
  /** Current URL (path + search) of the memory router. */
  const location = () => `${router.state.location.pathname}${router.state.location.search}`;
  return { ...view, router, user, queryClient, location };
}

/** Renders a single component with providers and a router (for components that use links or the URL). */
export function renderWithProviders(ui: ReactElement, { path = '/' }: { path?: string } = {}) {
  const user = userEvent.setup();
  const view = render(
    <AppProviders queryClient={testQueryClient()}>
      <MemoryRouter initialEntries={[path]}>{ui}</MemoryRouter>
    </AppProviders>,
  );
  return { ...view, user };
}
