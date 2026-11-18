import { screen, waitFor, within } from '@testing-library/react';
import { http } from 'msw';
import { describe, expect, it } from 'vitest';
import { battingFirstInsights } from '@/test/fixtures';
import { renderApp } from '@/test/renderApp';
import { envelope, problem, requestsTo, server } from '@/test/server';

describe('Insights: toss impact', () => {
  it('shows toss-winner results with filters resolved to ids', async () => {
    renderApp('/insights?team=MI&seasonFrom=2025');
    expect(await screen.findByText('52.50%')).toBeInTheDocument();
    expect(await screen.findByText('MI won the toss')).toBeInTheDocument();
    expect(
      screen.getByRole('table', { name: /Mumbai Indians results after winning and losing the toss/ }),
    ).toHaveTextContent('60.00%');
    const request = requestsTo('/insights/toss')[0];
    expect(request?.searchParams.get('team')).toBe('4');
    expect(request?.searchParams.get('seasonFrom')).toBe('2025');
  });

  it('cross-filters to a season clicked in the trend chart', async () => {
    const { user, location } = renderApp('/insights');
    await user.click(
      await screen.findByRole('button', {
        name: /Toss-winner win percentage by season, all IPL matches: 2025/,
      }),
    );
    expect(location()).toBe('/insights?season=2025');
    await waitFor(() =>
      expect(requestsTo('/insights/toss').some((u) => u.searchParams.get('season') === '2025')).toBe(true),
    );
    expect(screen.getByRole('list', { name: 'Active filters' })).toHaveTextContent('Season:2025');
  });

  it('removes an active filter chip', async () => {
    const { user, location } = renderApp('/insights?season=2025&venue=6');
    await user.click(await screen.findByRole('button', { name: 'Remove filter Season: 2025' }));
    expect(location()).toBe('/insights?venue=6');
  });

  it('applies a quick season range', async () => {
    const { user, location } = renderApp('/insights');
    await user.click(await screen.findByRole('button', { name: 'Last 3' }));
    expect(location()).toBe('/insights?seasonFrom=2024&seasonTo=2026');
  });

  it('shows an error state', async () => {
    server.use(http.get('*/api/v1/insights/toss', () => problem(500)));
    renderApp('/insights');
    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to load data.');
  });
});

describe('Insights: batting first vs chasing', () => {
  it('shows results and chase success by target band', async () => {
    renderApp('/insights/chasing');
    expect(await screen.findByText('44.07%')).toBeInTheDocument();
    const table = await screen.findByRole('table', { name: 'Chases by target, all IPL matches' });
    expect(within(table).getByText('Under 150')).toBeInTheDocument();
    expect(within(table).getByText('210+')).toBeInTheDocument();
    expect(within(table).getByText('33.33%')).toBeInTheDocument();
  });

  it('shows an empty state when nothing is decisive', async () => {
    server.use(
      http.get('*/api/v1/insights/batting-first', () =>
        envelope({
          ...battingFirstInsights,
          overall: { ...battingFirstInsights.overall, decisiveMatches: 0 },
        }),
      ),
    );
    renderApp('/insights/chasing?venue=6');
    expect(await screen.findByText('No statistics available for the selected filters')).toBeInTheDocument();
  });
});

describe('Insights: phases', () => {
  it('shows league phase scoring', async () => {
    renderApp('/insights/phases');
    const table = await screen.findByRole('table', { name: 'Phases, all IPL matches' });
    expect(within(table).getByText('Powerplay (1–6)')).toBeInTheDocument();
    expect(within(table).getByText('9.05')).toBeInTheDocument();
    expect(
      screen.getByRole('img', { name: 'Phase run rate by season, all IPL matches' }),
    ).toBeInTheDocument();
  });

  it('splits a team into batting and bowling', async () => {
    renderApp('/insights/phases?team=MI');
    expect(await screen.findByRole('table', { name: 'Phases, MI, batting' })).toBeInTheDocument();
    expect(screen.getByRole('table', { name: 'Phases, MI, bowling' })).toBeInTheDocument();
  });
});

describe('Insights: season comparison', () => {
  it('compares the first and latest seasons by default with the backend change', async () => {
    renderApp('/insights/seasons');
    const table = await screen.findByRole('table', { name: 'Season comparison 2025, 2026' });
    const averageRow = within(table).getByText('Average score').closest('tr') as HTMLElement;
    expect(averageRow).toHaveTextContent('175.50');
    expect(averageRow).toHaveTextContent('182.40');
    expect(averageRow).toHaveTextContent('+6.90');
    expect(within(table).getByText('Batting first won').closest('tr')).toHaveTextContent('-3.00 pts');
    expect(requestsTo('/insights/seasons').some((u) => u.searchParams.get('seasons') === '2025,2026')).toBe(
      true,
    );
  });

  it('toggles seasons from the chips and the trend chart', async () => {
    const { user, location } = renderApp('/insights/seasons?compare=2025,2026');
    const chips = await screen.findByRole('group', { name: 'Seasons to compare' });
    expect(await within(chips).findByRole('button', { name: '2025' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    await user.click(await screen.findByRole('button', { name: 'Average score by season: 2025' }));
    expect(location()).toBe('/insights/seasons?compare=2026');
  });
});
