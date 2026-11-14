import { screen, waitFor, within } from '@testing-library/react';
import { http } from 'msw';
import { describe, expect, it } from 'vitest';
import { teamStats } from '@/test/fixtures';
import { renderApp } from '@/test/renderApp';
import { envelope, problem, requestsTo, server } from '@/test/server';

describe('Teams page', () => {
  it('lists franchises with record, win % and latest season', async () => {
    renderApp('/teams');
    const table = await screen.findByRole('table', { name: /IPL franchises/ });
    const mi = within(table).getByRole('link', { name: 'Mumbai Indians' }).closest('tr') as HTMLElement;
    expect(within(mi).getByText('157')).toBeInTheDocument();
    expect(within(mi).getByText('56.07%')).toBeInTheDocument();
    expect(within(mi).getByText('2026: 8 won, 6 lost')).toBeInTheDocument();
    expect(within(table).getByText('Defunct')).toBeInTheDocument();
  });

  it('shows an empty state and errors', async () => {
    server.use(http.get('*/api/v1/teams', () => problem(500)));
    renderApp('/teams');
    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to load data.');
  });

  it('navigates to the team page', async () => {
    const { user, location } = renderApp('/teams');
    await user.click(await screen.findByRole('link', { name: 'Mumbai Indians' }));
    expect(await screen.findByRole('heading', { level: 1, name: /Mumbai Indians/ })).toBeInTheDocument();
    expect(location()).toBe('/teams/4');
    expect(await screen.findByText('246')).toBeInTheDocument();
  });
});

describe('Team detail', () => {
  it('shows the overview from the API with charts', async () => {
    renderApp('/teams/4');
    expect(await screen.findByText('Highest score')).toBeInTheDocument();
    expect(screen.getAllByText('57.14%').length).toBeGreaterThan(0);
    expect(
      await screen.findByRole('img', { name: 'Mumbai Indians wins and losses per season' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('img', { name: /batting first and chasing/ })).toBeInTheDocument();
  });

  it('resolves team codes in the URL to API ids', async () => {
    renderApp('/teams/4?opponent=CSK&season=2026');
    await screen.findByText('Highest score');
    const stats = requestsTo('/teams/4/stats')[0];
    expect(stats?.searchParams.get('opponent')).toBe('2');
    expect(stats?.searchParams.get('season')).toBe('2026');
  });

  it('keeps filters when switching tabs', async () => {
    const { user, location } = renderApp('/teams/4?season=2026');
    const tabs = await screen.findByRole('navigation', { name: 'Team sections' });
    await user.click(within(tabs).getByRole('link', { name: 'Seasons' }));
    await screen.findByRole('table', { name: 'Mumbai Indians season by season' });
    expect(location()).toBe('/teams/4/seasons?season=2026');
    expect(requestsTo('/teams/4/seasons')[0]?.searchParams.get('season')).toBe('2026');
  });

  it('shows the head-to-head and venue tabs', async () => {
    const { user } = renderApp('/teams/4/head-to-head');
    expect(await screen.findByRole('link', { name: 'Head to head' })).toHaveAttribute(
      'href',
      '/head-to-head?team1=MI&team2=CSK',
    );
    await user.click(
      within(screen.getByRole('navigation', { name: 'Team sections' })).getByRole('link', { name: 'Venues' }),
    );
    expect(await screen.findByRole('link', { name: 'Wankhede Stadium' })).toBeInTheDocument();
  });

  it('shows an empty state when the filters select no matches', async () => {
    server.use(
      http.get('*/api/v1/teams/:id/stats', () =>
        envelope({ ...teamStats, overall: { played: 0, won: 0, lost: 0, tied: 0, noResult: 0 } }),
      ),
    );
    renderApp('/teams/4?season=2008');
    expect(await screen.findByText('No statistics available for the selected filters')).toBeInTheDocument();
  });

  it('shows not found for an unknown team', async () => {
    renderApp('/teams/999');
    expect(await screen.findByRole('heading', { name: 'Team not found' })).toBeInTheDocument();
  });

  it('lists top performers on the batting tab', async () => {
    renderApp('/teams/4/batting');
    const table = await screen.findByRole('table', { name: 'Mumbai Indians leading run scorers' });
    expect(within(table).getByRole('link', { name: 'RG Sharma' })).toBeInTheDocument();
    await waitFor(() =>
      expect(requestsTo('/teams/4/top-performers')[0]?.searchParams.get('limit')).toBe('25'),
    );
  });
});
