import '@testing-library/jest-dom/vitest';
import { cleanup, configure } from '@testing-library/react';
import { afterEach, beforeAll, vi } from 'vitest';
import { configureApiOrigin } from '@/api/client';

/**
 * Contract verification setup: no mocked API. Requests go to a running backend (default http://localhost:8080,
 * override with IPLYTICS_API_ORIGIN), which reads PostgreSQL.
 */
const origin = (import.meta.env.IPLYTICS_API_ORIGIN as string | undefined) ?? 'http://localhost:8080';

vi.mock('@/components/charts/EChart', async () => await import('./FakeEChart'));
configure({ asyncUtilTimeout: 15_000 });

beforeAll(async () => {
  configureApiOrigin(origin);
  const health = await fetch(`${origin}/actuator/health`).catch(() => undefined);
  if (!health?.ok) {
    throw new Error(`No backend at ${origin}. Start it (mvnw spring-boot:run) before npm run test:contract.`);
  }
});

afterEach(() => cleanup());
