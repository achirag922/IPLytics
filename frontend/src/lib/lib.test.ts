import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { ApiError, apiGet, buildUrl } from '@/api/client';
import { server } from '@/test/server';
import {
  formatDate,
  formatDecimal,
  formatInteger,
  formatNetRunRate,
  formatPercent,
  formatScore,
} from './format';

describe('format', () => {
  it('formats numbers for display and dashes missing values', () => {
    expect(formatInteger(295732)).toBe('295,732');
    expect(formatDecimal(216.666)).toBe('216.67');
    expect(formatPercent(33.3)).toBe('33.30%');
    expect(formatNetRunRate(1.428)).toBe('+1.428');
    expect(formatNetRunRate(-0.2)).toBe('-0.200');
    expect(formatScore(113, true)).toBe('113*');
    expect(formatDate('2019-04-10')).toBe('10 Apr 2019');
    expect(formatInteger(undefined)).toBe('–');
    expect(formatDecimal(null)).toBe('–');
  });
});

describe('http client', () => {
  it('builds /api/v1 URLs without empty parameters', () => {
    const url = new URL(buildUrl('/matches', { season: 2019, team: undefined, stage: '', ids: [1, 2] }));
    expect(url.pathname).toBe('/api/v1/matches');
    expect([...url.searchParams.keys()]).toEqual(['season', 'ids']);
    expect(url.searchParams.get('ids')).toBe('1,2');
  });

  it('turns problem details into typed errors', async () => {
    server.use(
      http.get('*/api/v1/players/9', () =>
        HttpResponse.json(
          { title: 'Resource not found', status: 404, detail: 'Player 9 not found' },
          { status: 404 },
        ),
      ),
    );
    const error = await apiGet('/players/9').catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).kind).toBe('not-found');
    expect((error as ApiError).problem?.detail).toBe('Player 9 not found');
  });

  it('reports network failures as their own kind', async () => {
    server.use(http.get('*/api/v1/overview', () => HttpResponse.error()));
    const error = await apiGet('/overview').catch((e: unknown) => e);
    expect((error as ApiError).kind).toBe('network');
  });
});
