import { defineConfig } from 'vitest/config';
import base from './vite.config';

/**
 * Contract verification against a running backend (React -> /api/v1 -> Spring Boot -> PostgreSQL). Uses its own
 * test settings (no MSW): start the backend first. IPLYTICS_API_ORIGIN defaults to http://localhost:8080.
 */
export default defineConfig({
  plugins: base.plugins,
  resolve: base.resolve,
  test: {
    environment: 'jsdom',
    include: ['src/**/*.contract.test.{ts,tsx}'],
    setupFiles: ['./src/test/contractSetup.ts'],
    testTimeout: 60_000,
    hookTimeout: 30_000,
  },
});
