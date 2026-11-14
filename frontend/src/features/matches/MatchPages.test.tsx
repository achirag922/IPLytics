import { screen, waitFor, within } from '@testing-library/react';
import { http } from 'msw';
import { describe, expect, it } from 'vitest';
import { renderApp } from '@/test/renderApp';
import { envelope, problem, requestsTo, server } from '@/test/server';

describe('Matches page', () => {
  it('lists matches with result and score line', async () => {
    renderApp('/matches');
    const table = await screen.findByRole('table', { name: 'Matches matching the filters' });
    expect(within(table).getByRole('link', { name: '10 Apr 2019' })).toBeInTheDocument();
    expect(within(table).getAllByText('Mumbai Indians won by 10 runs')).toHaveLength(2);
    expect(within(table).getAllByText('MI 170/5 · CSK 160/8').length).toBeGreaterThan(0);
  });

  it('filters by stage and dates through the URL', async () => {
    const { user, location } = renderApp('/matches?team=MI');
    await user.selectOptions(await screen.findByLabelText('Stage'), 'Final');
    expect(location()).toBe('/matches?team=MI&stage=Final');
    await waitFor(() =>
      expect(requestsTo('/matches').some((u) => u.searchParams.get('stage') === 'Final')).toBe(true),
    );
    const last = requestsTo('/matches').at(-1);
    expect(last?.searchParams.get('team')).toBe('4');
  });

  it('pages on the server', async () => {
    const { user, location } = renderApp('/matches');
    await user.click(await screen.findByRole('button', { name: /Next/ }));
    expect(location()).toBe('/matches?page=1');
    await waitFor(() =>
      expect(requestsTo('/matches').some((u) => u.searchParams.get('page') === '1')).toBe(true),
    );
  });

  it('shows the empty state', async () => {
    server.use(
      http.get('*/api/v1/matches', () =>
        envelope([], { page: 0, size: 25, totalElements: 0, totalPages: 0 }),
      ),
    );
    renderApp('/matches?season=2008&team=MI&opponent=CSK');
    expect(await screen.findByText('No matches found')).toBeInTheDocument();
  });

  it('navigates to the scorecard', async () => {
    const { user, location } = renderApp('/matches');
    await user.click((await screen.findAllByRole('link', { name: '10 Apr 2019' }))[0] as HTMLElement);
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Mumbai Indians v Chennai Super Kings' }),
    ).toBeInTheDocument();
    expect(location()).toBe('/matches/77');
  });
});

describe('Match detail', () => {
  it('shows a full scorecard', async () => {
    renderApp('/matches/77');
    expect(await screen.findByText('chose to field', { exact: false })).toBeInTheDocument();
    const batting = await screen.findByRole('table', { name: 'Mumbai Indians batting' });
    expect(within(batting).getByText('c MS Dhoni b DL Chahar')).toBeInTheDocument();
    expect(within(batting).getByText('150.00')).toBeInTheDocument();
    const bowling = screen.getByRole('table', { name: 'Mumbai Indians bowling' });
    const header = within(bowling)
      .getAllByRole('columnheader')
      .map((h) => h.textContent);
    expect(header).toEqual(['Bowler', 'O', 'M', 'R', 'W', 'Econ', '0s', 'WD', 'NB']);
    expect(screen.getByText('Extras 10')).toBeInTheDocument();
    expect(screen.getByText(/1-45 \(RG Sharma, 5.3 ov\)/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Tail Ender' })).toBeInTheDocument();
  });

  it('shows worm and Manhattan charts on the progression tab', async () => {
    const { user, location } = renderApp('/matches/77');
    await user.click(await screen.findByRole('link', { name: 'Progression' }));
    expect(
      await screen.findByRole('img', { name: 'Cumulative runs by over for MI and CSK' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Runs per over for MI and CSK' })).toBeInTheDocument();
    expect(location()).toBe('/matches/77/progression');
  });

  it('shows not found for an unknown match', async () => {
    server.use(http.get('*/api/v1/matches/:id', () => problem(404)));
    renderApp('/matches/123456');
    expect(await screen.findByRole('heading', { name: 'Match not found' })).toBeInTheDocument();
  });
});
