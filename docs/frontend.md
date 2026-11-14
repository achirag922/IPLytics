# Frontend (V1.5)

React SPA over the `/api/v1` REST API (docs/api.md). It **displays** analytics; it never computes cricket
statistics. Every figure, ratio, ranking and qualification comes from the backend. The browser only formats
values, orders rows for display, and picks the slice of a response a chart shows.

Stack: React 19, TypeScript (strict, `noUncheckedIndexedAccess`), Vite 7, React Router 7 (data router),
TanStack Query 5, Tailwind CSS 4, shadcn/ui components, Apache ECharts 6, Vitest + React Testing Library + MSW,
ESLint + Prettier.

```bash
npm run dev            # http://localhost:5173, proxies /api to http://localhost:8080
npm test               # unit, component and page tests (mocked API)
npm run test:contract  # frontend/backend contract checks against a running backend
npm run lint && npm run format:check && npm run typecheck && npm run build
npm run generate:api   # regenerate API types after a backend change (needs backend/target/openapi.json)
npm run check:api      # fails when the committed types differ from the OpenAPI document
```

## 1. Architecture

```
src/
├── app/
│   ├── router/        routes.tsx (lazy routes), RouteErrorPage
│   ├── providers/     AppProviders, queryClient (TanStack Query), ThemeProvider
│   └── AppLayout/     sidebar + mobile navigation, header, theme toggle, skip link
├── api/
│   ├── client/        httpClient (the only fetch), request helpers, shared types
│   ├── generated/     schema.ts generated from the OpenAPI document (do not edit)
│   └── seasonsApi.ts, teamsApi.ts, playersApi.ts, matchesApi.ts, venuesApi.ts, analyticsApi.ts
├── components/
│   ├── ui/            shadcn/ui primitives (button, card, badge, form controls, table, skeleton)
│   ├── charts/        EChart wrapper + LineChart, BarChart, AreaChart, PieChart, RadarChart, Heatmap,
│   │                  WormChart, ManhattanChart, ChartCard
│   ├── cards/         StatCard, StatGrid, entity links
│   ├── tables/        DataTable, PaginationControls
│   ├── filters/       FilterBar, SelectFilter, SeasonFilter, SeasonRangeFilter, TeamFilter, VenueFilter,
│   │                  DateRangeFilter, SearchFilter, PlayerPicker, StatsFilterBar
│   ├── loading/       StatCardSkeleton, TableSkeleton, ChartSkeleton, PageSkeleton
│   ├── states/        EmptyState, ErrorState, QueryContent
│   └── layout/        PageHeader (with breadcrumbs), TabNav
├── features/          dashboard, teams, players, matches, venues, seasons, headToHead, comparison, matchups,
│                      leaderboards, notFound (pages compose the shared components)
├── hooks/             query hooks per domain, useUrlState, useStatsFilters, useNumericParam, useDebouncedValue
├── lib/               format, errors, queryKeys, theme, utils (cn)
├── styles/            index.css (Tailwind + design tokens, light and dark)
└── test/              MSW server and fixtures, render helpers, contract setup
```

Data flow, with no shortcuts:

```
Page / tab component
  → TanStack Query hook (hooks/use*Queries.ts)
    → API module (api/*Api.ts)
      → httpClient.apiGet (api/client/httpClient.ts)
        → /api/v1 (Vite proxy in dev, same origin in production)
```

Pages never call `fetch`. The API modules unwrap the `{data, pagination, meta}` envelope, so hooks return plain DTOs
(or `{items, pagination}` for paged lists).

## 2. Routing

| Route | Page |
|---|---|
| `/` | Dashboard |
| `/teams`, `/teams/:teamId` | Teams; team tabs: overview (index), `seasons`, `batting`, `bowling`, `venues`, `head-to-head` |
| `/players`, `/players/:playerId` | Players; player tabs: overview, `batting`, `bowling`, `phases`, `vs-teams`, `venues` |
| `/matches`, `/matches/:matchId` | Matches; match tabs: scorecard (index), `progression` |
| `/venues`, `/venues/:venueId` | Venues |
| `/seasons`, `/seasons/:year` | Seasons; summary and points table |
| `/head-to-head`, `/compare`, `/matchups`, `/leaderboards` | Analytics |
| `*` | 404 page |

- Every feature is a lazily loaded route chunk (`route.lazy`). ECharts is its own long-lived chunk.
- Tabs are nested routes rendered by `TabNav`, so a tab has a URL and survives refresh and back/forward. The current
  search parameters (filters) travel with the user between tabs.
- A detail page loads its entity once and hands it to its tabs through the outlet context
  (`features/detailContext.ts`).
- Unknown routes, malformed ids (`/players/abc`) and ids the API answers with 404 show the 404 page.

## 3. API integration

- **Types** come from the OpenAPI document: `api/generated/schema.ts` is produced by `openapi-typescript` from
  `backend/target/openapi.json` (written by the backend's contract test during `mvn verify`). API modules export
  aliases (`type Team = Schemas['TeamDto']`) and take query parameters typed from the operation
  (`QueryOf<'/api/v1/players'>`), so enum values such as leaderboard metrics are checked at compile time.
  Primitive fields are required in the document (a backend model converter marks them); nullable fields are
  optional and handled as "–".
- **Errors**: the client turns problem details into `ApiError` with a `kind` (`bad-request`, `not-found`,
  `unprocessable`, `server`, `network`). Users see "Unable to load data." with a friendly reason per kind and a
  Retry button. Raw API messages are never shown.
- **Team codes in URLs**: filters use franchise codes (`?opponent=CSK`). `useStatsFilters` resolves them to API ids
  through the cached team list, and statistics queries wait until the codes are resolved. An unknown code is
  reported and ignored.
- **Backend compatibility additions made in V1.5** (additive; existing responses unchanged): `GET /overview`;
  per-season totals in `GET /seasons`; `highestIndividualScore` in the season summary; all-time and latest-season
  records in `GET /teams`; `highestScore` in team stats; `GET /teams/{id}/venues` and `/opponents`; scoped,
  sortable `GET /players` with batting and bowling figures; fours, sixes and boundary % per batting phase;
  `maidens` in scorecard bowling; `GET /venues/{id}/seasons` and `/score-distribution`. See docs/api.md.

## 4. TanStack Query strategy

- One `QueryClient` (`app/providers/queryClient.ts`). IPL data changes only after an ingestion and analytics
  refresh, so statistics stay fresh for 10 minutes and reference data (seasons, teams, venues, finished matches)
  for 30; nothing refetches on window focus. Unused data is kept for 30 minutes.
- Client errors (400/404/422) are never retried; server and network errors are retried once.
- Query keys come from `lib/queryKeys.ts` (`['players', id, 'batting', params]`). Equal parameters give equal keys,
  so two components asking for the same data share one request.
- Paged lists and leaderboards use `placeholderData: keepPreviousData`: the current page stays on screen while the
  next loads.
- Queries that need user input (head-to-head, comparison, matchups) are disabled until it is given; the page shows
  an explanatory empty state instead of a request.
- Server state never goes into React state or a global store. The only client state is the theme preference
  (localStorage) and transient UI state (open menu, typed text).

## 5. URL filter strategy

- Filters live in the URL search parameters (`hooks/useUrlState.ts`). Refreshing or sharing a link reproduces the
  view, and every filter change is a new history entry, so back/forward walk through filter states.
- Changing any filter resets `page`.
- Shared names: `season`, or `seasonFrom`/`seasonTo` (choosing one clears the other, as the API requires), `team`,
  `opponent`, `team1`, `team2` (franchise codes), `venue` (id), `dateFrom`, `dateTo`, `stage`, `search`,
  `discipline`, `sort`, `metric`, `minimumBalls`, `minimumOvers`, `limit`, `ids` (comma-separated), `batter`,
  `bowler`, `page`, plus `type` on the leaderboards. Defaults are left out of the URL.
- Search boxes update the URL after a 350 ms pause in typing, so a search costs one request.
- Examples: `/players/576/batting?season=2016&opponent=PBKS`, `/matches?team=MI&stage=Final`,
  `/compare?ids=576,346&discipline=batting`, `/leaderboards?type=bowling&metric=economy&minimumOvers=50`.

## 6. Chart architecture

- `EChart` is the only component that touches ECharts. It creates the instance, resizes it with a
  `ResizeObserver` and disposes it on unmount. ECharts is tree-shaken (`components/charts/echarts.ts`).
- Chart components take data through props (`categories`, `series`, `innings`) and build options internally with
  `useChartOption`, memoised by value so equal data does not restart the animation. Pages never contain ECharts
  options.
- Every chart has an accessible name (`role="img"` + `aria-label`). ECharts' ARIA module adds a description and
  decal patterns, so series are distinguishable without colour. Wicket overs in the worm and Manhattan charts carry
  "nW" labels, and line series alternate solid and dashed strokes.
- An empty dataset renders `EmptyState`, never an empty chart. Loading and error states come from `QueryContent`
  around the chart (`ChartSkeleton`, `ErrorState`).
- The palette and axis colours follow the light or dark theme.
- The worm chart starts each innings at 0/0 and plots the API's cumulative runs. The Manhattan chart plots the API's
  runs per over. Neither recomputes anything from deliveries.

## 7. Component conventions

- Pages compose shared pieces: `PageHeader` → `FilterBar` → `QueryContent` (loading / error / empty / data) →
  `StatGrid` / `DataTable` / `ChartCard`. Large views are split into small components or tab routes.
- `QueryContent` makes the four states consistent: skeleton while the first response loads, `ErrorState` with retry,
  `EmptyState` when the predicate says so (e.g. "No statistics available for the selected filters"), otherwise
  the content.
- Formatting is centralised in `lib/format.ts` (thousands separators, 2 decimals for ratios, 3 for net run rate,
  "–" for missing values).
- `DataTable` renders semantic tables with captions, `scope="col"` headers and `<abbr title>` for abbreviations
  (M, W, NRR). It scrolls horizontally inside its card on small screens. Row clicks navigate, while keyboard users
  follow the link in the row.
- Accessibility baseline: landmarks (`nav` "Main", `main`, `role="search"` filter forms), a skip link, labelled form
  controls, an accessible player combobox (`role="combobox"`/`listbox`, arrow keys, Enter, Escape),
  `aria-current` on navigation, status regions for loading, `role="alert"` for errors, visible focus rings, and
  text on badges and results (never colour alone).
- Responsive: desktop-first with a 15 rem sidebar from 1024 px, and a slide-over menu (closed by Escape, the
  backdrop or navigating) below that. Grids collapse to one column on phones. Verified at 375, 768 and desktop
  widths with no horizontal page scroll.
- Styling: Tailwind utilities with shadcn/ui design tokens (CSS variables in `styles/index.css`, light and dark).
  shadcn/ui components live in `components/ui` (`components.json` keeps the shadcn CLI usable). Selects are native
  elements styled like shadcn, for robust keyboard and mobile behaviour.

## 8. Testing strategy

- **Unit and component tests** (`npm test`, Vitest + React Testing Library, jsdom): formatters, HTTP client
  (URL building, error kinds), StatCard, FilterBar, DataTable, pagination, skeletons, empty and error states,
  and chart data mapping.
- **Page tests** render the whole application (`test/renderApp.tsx`: real routes, lazy chunks, providers, query
  hooks, API modules and HTTP client) at a URL. The API is mocked with **MSW**, using typed fixtures
  (`test/fixtures.ts`) that follow the generated types, so a contract change breaks the build. Each page is
  tested for its success response and, where it applies, loading, empty response, API error, filtering
  (asserting the URL and the request parameters), server-side pagination and navigation. Unhandled requests fail
  the test.
- ECharts needs a canvas, which jsdom lacks. Tests replace the wrapper with `test/FakeEChart.tsx`, which keeps the
  accessible name and exposes the option for assertions. The real wrapper has its own test.
- **Contract verification** (`npm run test:contract`, `src/test/contract`): with the backend running, the real API
  modules call `/api/v1` and check invariants (season matches add up to the overview, worm totals equal scorecard
  totals), and the dashboard and a scorecard render against live data. React → `/api/v1` → Spring Boot →
  PostgreSQL, without duplicating the backend's integration tests. Not part of CI because it needs a loaded
  database.
- CI runs lint, format check, typecheck, unit/page tests and the production build.
