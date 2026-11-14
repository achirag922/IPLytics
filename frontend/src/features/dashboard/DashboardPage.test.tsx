import { screen, waitFor, within } from '@testing-library/react';
import { delay, http } from 'msw';
import { describe, expect, it } from 'vitest';
import { overview } from '@/test/fixtures';
import { renderApp } from '@/test/renderApp';
import { envelope, problem, requestsTo, server } from '@/test/server';

describe('Dashboard', () => {
  it('shows data-set KPIs, the latest season highlights and charts from the API', async () => {
    renderApp('/');
    expect(await screen.findByText('1,243')).toBeInTheDocument();
    expect(screen.getByText('295,732')).toBeInTheDocument();
    expect(await screen.findByText('152* (67)')).toBeInTheDocument();
    expect(screen.getByText('min 100 balls', { exact: false })).toBeInTheDocument();
    expect(await screen.findByRole('img', { name: 'Top 10 run scorers in 2026' })).toBeInTheDocument();
    expect(screen.getByRole('img', { name: /Runs by season, 2025 to 2026/ })).toBeInTheDocument();
    // The latest season is chosen by default and its summary asked with the documented qualification.
    const summary = requestsTo('/seasons/2026/summary')[0];
    expect(summary?.searchParams.get('minimumBalls')).toBe('100');
    expect(summary?.searchParams.get('minimumOvers')).toBe('10');
  });

  it('shows skeletons while loading', async () => {
    server.use(http.get('*/api/v1/overview', async () => delay('infinite')));
    renderApp('/');
    expect((await screen.findAllByText(/Loading statistics/)).length).toBeGreaterThan(0);
    expect(screen.getAllByRole('status').every((s) => s.getAttribute('aria-busy') === 'true')).toBe(true);
  });

  it('shows an error with retry when the API fails', async () => {
    let calls = 0;
    server.use(http.get('*/api/v1/overview', () => (++calls === 1 ? problem(500) : envelope(overview))));
    const { user } = renderApp('/');
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Unable to load data.');
    expect(alert).not.toHaveTextContent('internal detail');
    await user.click(within(alert).getByRole('button', { name: 'Retry' }));
    expect(await screen.findByText('1,243')).toBeInTheDocument();
  });

  it('changes the highlighted season through the URL', async () => {
    const { user, location } = renderApp('/');
    const select = await screen.findByRole('combobox', { name: 'Season' });
    await waitFor(() => expect(select).toHaveValue('2026'));
    await user.selectOptions(select, '2025');
    expect(location()).toBe('/?season=2025');
    await waitFor(() => expect(requestsTo('/seasons/2025/summary')).toHaveLength(1));
    expect(requestsTo('/leaderboards/batting').some((u) => u.searchParams.get('season') === '2025')).toBe(
      true,
    );
  });
});
