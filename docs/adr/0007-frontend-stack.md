# 0007 Frontend stack

**Status:** Accepted (V0)

## Decision
- **React 19 + TypeScript (strict) + Vite.** Plain SPA, no server-side rendering.
- **React Router** (data router) for routing. Filters live in URL search params so every view is shareable.
- **TanStack Query** for all server state. No Redux or global client store.
- **ECharts** (tree-shaken through `components/charts/echarts.ts`) behind a single `EChart` wrapper component. It was chosen over Recharts for heatmaps, radar charts and large series.
- **Tailwind CSS v4** for styling. shadcn/ui components are added as needed from V1.
- Folder layout: `app/` (shell, routes, providers), `api/` (HTTP client, types), `features/<area>/` (pages, components, hooks), `components/` (shared UI).
- In development, Vite proxies `/api` to `localhost:8080`, so the backend needs no CORS configuration.

## Consequences
- From V1, API types will be generated from the backend OpenAPI document (`openapi-typescript`) instead of being written by hand.
- ECharts needs a canvas, so unit tests mock the wrapper's ECharts module. Real rendering is covered by end-to-end tests in V1.3.
