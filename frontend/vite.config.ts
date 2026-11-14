import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 5173,
    // Same-origin API calls in dev: no CORS configuration needed on the backend.
    proxy: { '/api': 'http://localhost:8080' },
  },
  build: {
    // The tree-shaken ECharts chunk is ~640 kB (~220 kB gzipped); it is cached separately from app code.
    chunkSizeWarningLimit: 700,
    rollupOptions: {
      output: {
        // Keep the large charting library in its own long-lived chunk.
        manualChunks: {
          echarts: ['echarts/core', 'echarts/charts', 'echarts/components', 'echarts/renderers'],
        },
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    exclude: ['src/**/*.contract.test.{ts,tsx}', 'node_modules/**'],
    // Page tests render the whole app with lazy route chunks.
    testTimeout: 15_000,
  },
});
