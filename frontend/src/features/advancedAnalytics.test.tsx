import { screen, waitFor, within } from '@testing-library/react';
import { http } from 'msw';
import { describe, expect, it } from 'vitest';
import { consistency } from '@/test/fixtures';
import { renderApp } from '@/test/renderApp';
import { envelope, requestsTo, server } from '@/test/server';

describe('Team: phases and toss & form', () => {
  it('shows the team batting and bowling by phase', async () => {
    renderApp('/teams/4/phases?season=2026');
    expect(await screen.findByRole('table', { name: 'Phases, Mumbai Indians, batting' })).toBeInTheDocument();
    expect(screen.getByRole('table', { name: 'Phases, Mumbai Indians, bowling' })).toBeInTheDocument();
    const request = requestsTo('/insights/phases')[0];
    expect(request?.searchParams.get('team')).toBe('4');
    expect(request?.searchParams.get('season')).toBe('2026');
  });

  it('shows recent form, toss record and drills from the season chart to matches', async () => {
    const { user, location } = renderApp('/teams/4/toss');
    const results = await screen.findByLabelText('Results, newest first');
    expect(within(results).getAllByText('W').length).toBeGreaterThan(0);
    expect(await screen.findByText('MI won the toss')).toBeInTheDocument();
    expect(requestsTo('/matches').some((u) => u.searchParams.get('size') === '10')).toBe(true);
    await user.click(
      await screen.findByRole('button', {
        name: 'Mumbai Indians wins batting first and chasing per season: 2026',
      }),
    );
    await waitFor(() => expect(location()).toBe('/matches?team=MI&season=2026'));
  });
});

describe('Player consistency', () => {
  it('shows spread, distribution and form, and opens a match from the form chart', async () => {
    const { user, location } = renderApp('/players/346/consistency');
    expect(await screen.findByText('31.00')).toBeInTheDocument();
    expect(screen.getByText('78.60%')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'RG Sharma innings by score band' })).toBeInTheDocument();
    expect(screen.getByRole('table', { name: 'RG Sharma recent batting innings' })).toHaveTextContent('75*');
    // Bowling consistency is hidden for a player who did not bowl.
    expect(screen.queryByText('Bowling consistency')).not.toBeInTheDocument();
    await user.click(
      screen.getByRole('button', { name: /RG Sharma runs in recent innings: 20 May 2026 v CSK/ }),
    );
    await waitFor(() => expect(location()).toBe('/matches/77'));
  });

  it('shows an empty state when the player has no innings in scope', async () => {
    server.use(
      http.get('*/api/v1/players/:id/consistency', () =>
        envelope({ ...consistency, batting: { innings: 0 }, bowling: { innings: 0 } }),
      ),
    );
    renderApp('/players/346/consistency?season=2008');
    expect(await screen.findByText('No statistics available for the selected filters')).toBeInTheDocument();
  });
});

describe('Comparison improvements', () => {
  it('adds consistency, phases and a profile, and opens players from the chart', async () => {
    const { user, location } = renderApp('/compare?ids=346,210');
    const table = await screen.findByRole('table', { name: 'batting consistency of the selected players' });
    expect(within(table).getByText('Median score')).toBeInTheDocument();
    expect(
      screen.getByRole('img', { name: 'Strike rate by phase of the selected players' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('img', { name: /Profile of the selected players/ })).toBeInTheDocument();
    expect(requestsTo('/players/346/consistency')).toHaveLength(1);
    await user.click(screen.getByRole('button', { name: 'Runs of the selected players: RG Sharma' }));
    await waitFor(() => expect(location()).toBe('/players/346'));
  });
});

describe('Head-to-head improvements', () => {
  it('adds venues, overall records, phases and toss in the meetings', async () => {
    const { user, location } = renderApp('/head-to-head?team1=CSK&team2=MI');
    const venues = await screen.findByRole('table', { name: 'CSK v MI by venue' });
    expect(within(venues).getByRole('link', { name: 'Wankhede Stadium' })).toBeInTheDocument();
    expect(await screen.findByRole('table', { name: 'CSK and MI overall records' })).toBeInTheDocument();
    expect(await screen.findByRole('table', { name: 'Phases, CSK v MI, batting' })).toBeInTheDocument();
    const toss = requestsTo('/insights/toss')[0];
    expect(toss?.searchParams.get('team')).toBe('2');
    expect(toss?.searchParams.get('opponent')).toBe('4');
    await user.click(screen.getByRole('button', { name: 'CSK and MI wins in each season: 2026' }));
    await waitFor(() => expect(location()).toBe('/matches?team=CSK&opponent=MI&season=2026'));
  });
});

describe('Venue insights', () => {
  it('shows franchises, top performers, toss and phases at the venue', async () => {
    const { user, location } = renderApp('/venues/6');
    const teams = await screen.findByRole('table', { name: 'Franchise records at Wankhede Stadium' });
    expect(within(teams).getByText('59.60%')).toBeInTheDocument();
    await waitFor(() =>
      expect(requestsTo('/leaderboards/batting').some((u) => u.searchParams.get('venue') === '6')).toBe(true),
    );
    expect(requestsTo('/insights/phases').some((u) => u.searchParams.get('venue') === '6')).toBe(true);
    await user.click(
      await screen.findByRole('button', { name: 'Franchise wins and losses at Wankhede Stadium: MI' }),
    );
    await waitFor(() => expect(location()).toBe('/teams/4'));
  });
});

describe('Dashboard improvements', () => {
  it('shows season changes from the backend with sparklines', async () => {
    renderApp('/');
    const glance = await screen.findByRole('region', { name: '2026 at a glance' });
    await waitFor(() => expect(within(glance).getByText('+6.90 vs 2025')).toBeInTheDocument());
    expect(within(glance).getAllByTestId('sparkline').length).toBeGreaterThan(0);
  });

  it('cross-filters by season and drills into players', async () => {
    const { user, location } = renderApp('/');
    await user.click(await screen.findByRole('button', { name: /Runs by season, 2025 to 2026: 2025/ }));
    expect(location()).toBe('/?season=2025');
    await user.click(await screen.findByRole('button', { name: 'Top 10 run scorers in 2025: RG Sharma' }));
    await waitFor(() => expect(location()).toBe('/players/346'));
  });
});

describe('Leaderboard improvements', () => {
  it('filters by venue and opens a player from the chart', async () => {
    const { user, location } = renderApp('/leaderboards?venue=6');
    expect(await screen.findByRole('table', { name: 'Batting leaderboard by Runs' })).toBeInTheDocument();
    expect(requestsTo('/leaderboards/batting')[0]?.searchParams.get('venue')).toBe('6');
    await user.click(screen.getByRole('button', { name: 'Batting leaderboard by Runs, top 2: RG Sharma' }));
    await waitFor(() => expect(location()).toBe('/players/346'));
  });
});
