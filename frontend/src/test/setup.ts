import '@testing-library/jest-dom/vitest';
import { cleanup, configure } from '@testing-library/react';
import { afterAll, afterEach, beforeAll, vi } from 'vitest';
import { requests, server } from './server';

// jsdom has no canvas: charts render as an accessible placeholder carrying the option they were given.
vi.mock('@/components/charts/EChart', async () => await import('./FakeEChart'));

// Pages are lazy route chunks: give the first render time to load them.
configure({ asyncUtilTimeout: 5000 });

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));

afterEach(() => {
  cleanup();
  server.resetHandlers();
  requests.length = 0;
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

afterAll(() => server.close());
