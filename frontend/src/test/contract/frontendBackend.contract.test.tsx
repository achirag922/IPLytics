import { screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { analyticsApi } from '@/api/analyticsApi';
import { matchesApi } from '@/api/matchesApi';
import { playersApi } from '@/api/playersApi';
import { seasonsApi } from '@/api/seasonsApi';
import { teamsApi } from '@/api/teamsApi';
import { venuesApi } from '@/api/venuesApi';
import { formatInteger } from '@/lib/format';
import { renderApp } from '@/test/renderApp';

/**
 * React -> /api/v1 -> Spring Boot -> PostgreSQL. Not a second backend test suite: it checks that the frontend's API
 * modules, generated types and pages work against the real server and whatever data is loaded. Ids are discovered
 * from responses, so it runs on the full data set or on a small one.
 */
describe('frontend/backend contract', () => {
  it('reference data and the dashboard overview', async () => {
    const overview = await analyticsApi.overview();
    expect(overview.matches).toBeGreaterThan(0);
    expect(overview.deliveries).toBeGreaterThan(overview.matches);

    const seasons = await seasonsApi.list();
    expect(seasons.length).toBe(overview.seasons);
    expect(seasons.reduce((sum, s) => sum + s.matches, 0)).toBe(overview.matches);
    const latest = Math.max(...seasons.map((s) => s.year));
    const summary = await seasonsApi.summary(latest, { minimumBalls: 100, minimumOvers: 10 });
    expect(summary.year).toBe(latest);
    expect(summary.strikeRateMinimumBalls).toBe(100);
    const points = await seasonsApi.pointsTable(latest);
    expect(Array.isArray(points.rows)).toBe(true);

    const teams = await teamsApi.list();
    expect(teams.every((t) => typeof t.id === 'number' && t.code)).toBe(true);
    const team = teams.find((t) => (t.record?.played ?? 0) > 0);
    expect(team).toBeDefined();
    const stats = await teamsApi.stats(team!.id, {});
    expect(stats.overall?.played).toBe(team!.record?.played);
  });

  it('players, matches, venues and analytics', async () => {
    const page = await playersApi.list({ size: 5, sort: 'runs' });
    expect(page.pagination.size).toBe(5);
    const [first, second] = page.items;
    expect(first && second).toBeTruthy();
    const batting = await playersApi.batting(first!.id, {});
    expect(batting.summary?.runs).toBe(first!.runs);

    const matches = await matchesApi.list({ size: 3 });
    const match = matches.items[0]!;
    const scorecard = await matchesApi.scorecard(match.id);
    const progression = await matchesApi.progression(match.id);
    for (const innings of scorecard.innings ?? []) {
      const worm = progression.innings?.find((i) => i.inningsNumber === innings.inningsNumber);
      expect(worm?.overs?.at(-1)?.cumulativeRuns).toBe(innings.runs);
    }

    const venues = await venuesApi.list();
    const bands = await venuesApi.scoreDistribution(venues[0]!.id, {});
    expect(bands).toHaveLength(7);

    const leaders = await analyticsApi.battingLeaderboard({ metric: 'runs', limit: 3 });
    expect(leaders[0]?.rank).toBe(1);
    const bowlers = await analyticsApi.bowlingLeaderboard({ metric: 'wickets', limit: 1 });
    const comparison = await analyticsApi.compare({ ids: [first!.id, second!.id], discipline: 'batting' });
    expect(comparison.players?.map((p) => p.player?.id)).toEqual([first!.id, second!.id]);
    const matchup = await analyticsApi.matchup({ batterId: first!.id, bowlerId: bowlers[0]!.player!.id });
    expect(typeof matchup.occurred).toBe('boolean');
    const h2h = await analyticsApi.headToHead({ team1: match.team1!.team!.id, team2: match.team2!.team!.id });
    expect(h2h.matches).toBeGreaterThan(0);
  });

  it('renders the dashboard with live data', async () => {
    const overview = await analyticsApi.overview();
    renderApp('/');
    expect(await screen.findByText(formatInteger(overview.matches))).toBeInTheDocument();
    expect(await screen.findByText(formatInteger(overview.deliveries))).toBeInTheDocument();
  });

  it('renders a live scorecard', async () => {
    const match = (await matchesApi.list({ size: 1 })).items[0]!;
    renderApp(`/matches/${match.id}`);
    expect(await screen.findByRole('heading', { level: 1 })).toHaveTextContent(' v ');
    const firstInnings = match.innings?.[0];
    if (firstInnings) {
      const table = await screen.findByRole('table', {
        name: new RegExp(`${firstInnings.team?.name} batting`),
      });
      expect(within(table).getAllByRole('row').length).toBeGreaterThan(1);
    }
  });
});
