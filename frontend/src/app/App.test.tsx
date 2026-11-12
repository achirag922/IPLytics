import { QueryClient } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { AppProviders } from './AppProviders';
import { routes } from './routes';

function renderAt(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <AppProviders queryClient={queryClient}>
      <RouterProvider router={router} />
    </AppProviders>,
  );
}

function stubFetchJson(status: number, body: unknown) {
  const fetchMock = vi.fn().mockResolvedValue(
    new Response(JSON.stringify(body), {
      status,
      headers: { 'Content-Type': 'application/json' },
    }),
  );
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

describe('App shell', () => {
  it('renders navigation and the backend status on the home page', async () => {
    const fetchMock = stubFetchJson(200, { application: 'iplytics', version: '0.0.1', schemaVersion: '1' });

    renderAt('/');

    expect(screen.getByRole('heading', { level: 1, name: 'IPLytics' })).toBeInTheDocument();
    const nav = screen.getByRole('navigation', { name: 'Main' });
    for (const label of ['Teams', 'Players', 'Matches', 'Venues', 'Seasons']) {
      expect(nav).toHaveTextContent(label);
    }
    expect(await screen.findByText('API 0.0.1 · database schema v1')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/system/status', expect.anything());
  });

  it('shows a clear message when the backend is unreachable', async () => {
    stubFetchJson(503, { title: 'Service Unavailable', status: 503 });

    renderAt('/');

    expect(await screen.findByText(/Backend unreachable/)).toBeInTheDocument();
  });

  it('renders a placeholder for sections planned in later versions', () => {
    stubFetchJson(200, {});

    renderAt('/teams');

    expect(screen.getByRole('heading', { level: 1, name: 'Teams' })).toBeInTheDocument();
    expect(screen.getByText('Planned for V1.')).toBeInTheDocument();
  });

  it('renders not found for unknown routes', () => {
    renderAt('/no-such-page');

    expect(screen.getByRole('heading', { name: 'Page not found' })).toBeInTheDocument();
  });
});
