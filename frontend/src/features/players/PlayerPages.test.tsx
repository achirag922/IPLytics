import { screen, waitFor, within } from '@testing-library/react';
import { http } from 'msw';
import { describe, expect, it } from 'vitest';
import { renderApp } from '@/test/renderApp';
import { envelope, problem, requestsTo, server } from '@/test/server';

describe('Players page', () => {
  it('lists players from a server-side page', async () => {
    renderApp('/players');
    const table = await screen.findByRole('table', { name: 'Players matching the filters' });
    expect(within(table).getByRole('link', { name: 'RG Sharma' })).toBeInTheDocument();
    expect(within(table).getByText('7,329')).toBeInTheDocument();
    expect(screen.getByText(/1–25 of 60 players/)).toBeInTheDocument();
    expect(requestsTo('/players')[0]?.searchParams.get('size')).toBe('25');
  });

  it('pages on the server', async () => {
    const { user, location } = renderApp('/players');
    await user.click(await screen.findByRole('button', { name: /Next/ }));
    expect(location()).toBe('/players?page=1');
    await waitFor(() =>
      expect(requestsTo('/players').some((u) => u.searchParams.get('page') === '1')).toBe(true),
    );
    expect(await screen.findByText(/26–50 of 60 players/)).toBeInTheDocument();
  });

  it('searches and filters through the URL', async () => {
    const { user, location } = renderApp('/players');
    await user.type(await screen.findByLabelText('Search'), 'sharma');
    await waitFor(() => expect(location()).toBe('/players?search=sharma'));
    await waitFor(() =>
      expect(requestsTo('/players').some((u) => u.searchParams.get('search') === 'sharma')).toBe(true),
    );
    await user.selectOptions(screen.getByLabelText('Discipline'), 'bowling');
    await waitFor(() =>
      expect(requestsTo('/players').some((u) => u.searchParams.get('discipline') === 'bowling')).toBe(true),
    );
    expect(location()).toBe('/players?search=sharma&discipline=bowling');
  });

  it('resolves the team filter code to an id', async () => {
    renderApp('/players?team=MI&season=2026&sort=runs');
    await screen.findByRole('table', { name: 'Players matching the filters' });
    const request = requestsTo('/players')[0];
    expect(request?.searchParams.get('team')).toBe('4');
    expect(request?.searchParams.get('sort')).toBe('runs');
  });

  it('shows the empty state', async () => {
    server.use(
      http.get('*/api/v1/players', () =>
        envelope([], { page: 0, size: 25, totalElements: 0, totalPages: 0 }),
      ),
    );
    renderApp('/players?search=zzz');
    expect(await screen.findByText('No players found')).toBeInTheDocument();
  });

  it('shows an error', async () => {
    server.use(http.get('*/api/v1/players', () => problem(500)));
    renderApp('/players');
    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to load data.');
  });

  it('navigates to the player page', async () => {
    const { user, location } = renderApp('/players');
    await user.click(await screen.findByRole('link', { name: 'RG Sharma' }));
    expect(await screen.findByRole('heading', { level: 1, name: 'RG Sharma' })).toBeInTheDocument();
    expect(location()).toBe('/players/346');
  });
});

describe('Player detail', () => {
  it('shows the header and overview', async () => {
    renderApp('/players/346');
    expect(await screen.findByRole('heading', { level: 1, name: 'RG Sharma' })).toBeInTheDocument();
    expect(screen.getByText(/270 matches/)).toBeInTheDocument();
    expect(await screen.findByText('105*')).toBeInTheDocument();
    expect(screen.getByText('4/18')).toBeInTheDocument();
  });

  it('shows batting with filters from the URL and charts', async () => {
    renderApp('/players/346/batting?season=2016&opponent=CSK');
    expect(await screen.findByRole('img', { name: 'RG Sharma runs per season' })).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'RG Sharma fours and sixes per season' })).toBeInTheDocument();
    const request = requestsTo('/players/346/batting')[0];
    expect(request?.searchParams.get('season')).toBe('2016');
    expect(request?.searchParams.get('opponent')).toBe('2');
    expect(screen.getByLabelText('Opponent')).toHaveValue('CSK');
  });

  it('shows phases with boundary percentages and hides empty bowling phases', async () => {
    renderApp('/players/346/phases');
    const table = await screen.findByRole('table', { name: 'RG Sharma batting by phase of the innings' });
    expect(within(table).getByText('Powerplay (1–6)')).toBeInTheDocument();
    expect(within(table).getByText('20.67%')).toBeInTheDocument();
    expect(screen.queryByText('Bowling by phase')).not.toBeInTheDocument();
  });

  it('shows splits against teams and at venues', async () => {
    const { user } = renderApp('/players/346/vs-teams');
    expect(await screen.findByRole('table', { name: 'RG Sharma against each opponent' })).toBeInTheDocument();
    await user.click(
      within(screen.getByRole('navigation', { name: 'Player sections' })).getByRole('link', {
        name: 'Venues',
      }),
    );
    expect(await screen.findByRole('table', { name: 'RG Sharma at each venue' })).toBeInTheDocument();
  });

  it('shows not found for an unknown player', async () => {
    server.use(http.get('*/api/v1/players/:id', () => problem(404)));
    renderApp('/players/999999');
    expect(await screen.findByRole('heading', { name: 'Player not found' })).toBeInTheDocument();
  });
});
