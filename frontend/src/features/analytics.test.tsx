import { screen, waitFor, within } from '@testing-library/react';
import { http } from 'msw';
import { describe, expect, it } from 'vitest';
import { headToHead, matchup } from '@/test/fixtures';
import { renderApp } from '@/test/renderApp';
import { envelope, problem, requestsTo, server } from '@/test/server';

describe('Head to head', () => {
  it('waits for two teams', async () => {
    renderApp('/head-to-head');
    expect(await screen.findByText('Choose two teams')).toBeInTheDocument();
    expect(requestsTo('/head-to-head')).toHaveLength(0);
  });

  it('shows the rivalry for teams chosen in the URL', async () => {
    renderApp('/head-to-head?team1=CSK&team2=MI&seasonFrom=2020');
    expect(await screen.findByText('41')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: /CSK and MI wins, wins batting first/ })).toBeInTheDocument();
    expect(screen.getByRole('table', { name: 'Latest meetings' })).toHaveTextContent(
      'Chennai Super Kings won by 8 wickets',
    );
    const request = requestsTo('/head-to-head')[0];
    expect(request?.searchParams.get('team1')).toBe('2');
    expect(request?.searchParams.get('team2')).toBe('4');
    expect(request?.searchParams.get('seasonFrom')).toBe('2020');
  });

  it('selects teams with the filters', async () => {
    const { user, location } = renderApp('/head-to-head');
    await screen.findAllByRole('option', { name: 'Mumbai Indians' });
    await user.selectOptions(screen.getByLabelText('Team 1'), 'MI');
    await user.selectOptions(screen.getByLabelText('Team 2'), 'CSK');
    expect(location()).toBe('/head-to-head?team1=MI&team2=CSK');
    // A team cannot face itself: it is not offered on the other side.
    expect(
      within(screen.getByLabelText('Team 2')).queryByRole('option', { name: 'Mumbai Indians' }),
    ).toBeNull();
    expect(await screen.findByText('41')).toBeInTheDocument();
  });

  it('explains when the teams never met', async () => {
    server.use(
      http.get('*/api/v1/head-to-head', () => envelope({ ...headToHead, matches: 0, recentMatches: [] })),
    );
    renderApp('/head-to-head?team1=CSK&team2=MI&seasonTo=2008');
    expect(await screen.findByText('These teams have not met')).toBeInTheDocument();
  });
});

describe('Player comparison', () => {
  it('waits for two players', async () => {
    renderApp('/compare?ids=346');
    expect(await screen.findByText('Choose at least two players')).toBeInTheDocument();
    expect(await screen.findByText('RG Sharma')).toBeInTheDocument();
  });

  it('compares batting side by side', async () => {
    renderApp('/compare?ids=346,210');
    const table = await screen.findByRole('table', { name: 'batting figures of the selected players' });
    expect(within(table).getByRole('columnheader', { name: 'RG Sharma' })).toBeInTheDocument();
    expect(within(table).getByText('Strike rate')).toBeInTheDocument();
    expect(within(table).queryByText('Economy')).toBeNull();
    expect(requestsTo('/players/compare')[0]?.searchParams.get('ids')).toBe('346,210');
  });

  it('shows only bowling metrics for bowling', async () => {
    const { user, location } = renderApp('/compare?ids=346,210');
    await user.selectOptions(await screen.findByLabelText('Discipline'), 'bowling');
    expect(location()).toBe('/compare?ids=346%2C210&discipline=bowling');
    const table = await screen.findByRole('table', { name: 'bowling figures of the selected players' });
    expect(within(table).getByText('Economy')).toBeInTheDocument();
    expect(within(table).queryByText('Fours')).toBeNull();
  });

  it('removes a player', async () => {
    const { user, location } = renderApp('/compare?ids=346,210');
    await user.click(await screen.findByRole('button', { name: 'Remove RG Sharma' }));
    expect(location()).toBe('/compare?ids=210');
  });

  it('adds a player found by search', async () => {
    const { user, location } = renderApp('/compare?ids=346');
    await user.type(await screen.findByRole('combobox', { name: 'Add player' }), 'bum');
    await user.click(await screen.findByRole('option', { name: /JJ Bumrah/ }));
    expect(location()).toBe('/compare?ids=346%2C210');
  });
});

describe('Matchups', () => {
  it('waits for both players', async () => {
    renderApp('/matchups?batter=346');
    expect(await screen.findByText('Choose a batter and a bowler')).toBeInTheDocument();
  });

  it('shows the matchup', async () => {
    renderApp('/matchups?batter=346&bowler=210&seasonFrom=2020');
    expect(await screen.findByText('140.00')).toBeInTheDocument();
    expect(screen.getByText('36.00%')).toBeInTheDocument();
    const request = requestsTo('/matchups')[0];
    expect(request?.searchParams.get('batterId')).toBe('346');
    expect(request?.searchParams.get('bowlerId')).toBe('210');
  });

  it('handles a pair that never met', async () => {
    server.use(
      http.get('*/api/v1/matchups', () => envelope({ ...matchup, occurred: false, balls: 0, runs: 0 })),
    );
    renderApp('/matchups?batter=346&bowler=210');
    expect(await screen.findByText('No matchup found')).toBeInTheDocument();
  });
});

describe('Leaderboards', () => {
  it('ranks batters by runs by default', async () => {
    renderApp('/leaderboards');
    const table = await screen.findByRole('table', { name: 'Batting leaderboard by Runs' });
    expect(within(table).getByRole('link', { name: 'RG Sharma' })).toBeInTheDocument();
    expect(requestsTo('/leaderboards/batting')[0]?.searchParams.get('metric')).toBe('runs');
  });

  it('switches to bowling and changes metric and qualification', async () => {
    const { user, location } = renderApp('/leaderboards');
    await user.click(await screen.findByRole('button', { name: 'bowling' }));
    expect(await screen.findByRole('table', { name: 'Bowling leaderboard by Wickets' })).toBeInTheDocument();
    await user.selectOptions(screen.getByLabelText('Metric'), 'economy');
    await user.type(screen.getByLabelText('Minimum overs'), '50');
    await waitFor(() => expect(location()).toBe('/leaderboards?type=bowling&metric=economy&minimumOvers=50'));
    await waitFor(() =>
      expect(
        requestsTo('/leaderboards/bowling').some(
          (u) => u.searchParams.get('metric') === 'economy' && u.searchParams.get('minimumOvers') === '50',
        ),
      ).toBe(true),
    );
  });

  it('shows an empty state when nobody qualifies', async () => {
    server.use(http.get('*/api/v1/leaderboards/batting', () => envelope([])));
    renderApp('/leaderboards?metric=strikeRate&minimumBalls=100000');
    expect(await screen.findByText('No statistics available for the selected filters')).toBeInTheDocument();
  });

  it('shows an error', async () => {
    server.use(http.get('*/api/v1/leaderboards/batting', () => problem(400)));
    renderApp('/leaderboards');
    expect(await screen.findByRole('alert')).toHaveTextContent('These filters cannot be applied');
  });
});
