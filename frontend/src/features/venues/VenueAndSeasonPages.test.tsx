import { screen, waitFor, within } from '@testing-library/react';
import { http } from 'msw';
import { describe, expect, it } from 'vitest';
import { renderApp } from '@/test/renderApp';
import { envelope, problem, requestsTo, server } from '@/test/server';

describe('Venues', () => {
  it('lists venues and filters them by name', async () => {
    const { user, location } = renderApp('/venues');
    const table = await screen.findByRole('table', { name: 'IPL venues' });
    expect(within(table).getAllByRole('row')).toHaveLength(3);
    await user.type(screen.getByLabelText('Search'), 'chepauk');
    await waitFor(() => expect(location()).toBe('/venues?search=chepauk'));
    await waitFor(() =>
      expect(within(screen.getByRole('table', { name: 'IPL venues' })).getAllByRole('row')).toHaveLength(2),
    );
  });

  it('shows venue characteristics and charts', async () => {
    renderApp('/venues/6?seasonFrom=2020');
    expect(await screen.findByText('172.40')).toBeInTheDocument();
    expect(screen.getByText('44.83%')).toBeInTheDocument();
    expect(
      await screen.findByRole('img', { name: 'Distribution of first-innings totals at Wankhede Stadium' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('img', { name: /won batting first and chasing/ })).toBeInTheDocument();
    expect(await screen.findByRole('img', { name: /per season at Wankhede Stadium/ })).toBeInTheDocument();
    expect(requestsTo('/venues/6/stats')[0]?.searchParams.get('seasonFrom')).toBe('2020');
  });
});

describe('Seasons', () => {
  it('lists seasons newest first with champions', async () => {
    renderApp('/seasons');
    const rows = within(await screen.findByRole('table', { name: 'IPL seasons' })).getAllByRole('row');
    expect(rows[1]).toHaveTextContent('IPL 2026');
    expect(rows[1]).toHaveTextContent('Mumbai Indians');
  });

  it('shows the season summary and points table', async () => {
    renderApp('/seasons/2026');
    expect(await screen.findByRole('heading', { level: 1, name: 'IPL 2026' })).toBeInTheDocument();
    expect(screen.getByText('265/4')).toBeInTheDocument();
    const table = await screen.findByRole('table', { name: 'IPL 2026 points table' });
    expect(within(table).getByText('+0.412')).toBeInTheDocument();
    expect(within(table).getByText('-0.412')).toBeInTheDocument();
  });

  it('shows an error for the points table without hiding the summary', async () => {
    server.use(http.get('*/api/v1/seasons/:year/points-table', () => problem(500)));
    renderApp('/seasons/2026');
    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to load data.');
    expect(screen.getByText('265/4')).toBeInTheDocument();
  });

  it('shows not found for a season without matches', async () => {
    server.use(http.get('*/api/v1/seasons/:year/summary', () => problem(404)));
    renderApp('/seasons/2011');
    expect(await screen.findByRole('heading', { name: 'Season not found' })).toBeInTheDocument();
  });

  it('shows an empty points table', async () => {
    server.use(http.get('*/api/v1/seasons/:year/points-table', () => envelope({ year: 2026, rows: [] })));
    renderApp('/seasons/2026');
    expect(await screen.findByText('No league matches this season')).toBeInTheDocument();
  });
});
