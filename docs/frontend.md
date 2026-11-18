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

## 9. V1.6: advanced analytics and UX

**New views**

| Route | What it shows |
|---|---|
| `/insights` (toss impact), `/insights/chasing`, `/insights/phases`, `/insights/seasons` | League-wide insights hub with the shared filters (seasons, team, opponent, venue). Season comparison picks 2 to 10 seasons (`?compare=2008,2026`) and shows the backend's change from first to last, plus scoring, phase, boundary and toss trends. |
| `/teams/:id/phases`, `/teams/:id/toss` | Team batting vs bowling by phase; recent form (last 10 results as W/L/T/NR badges), wins batting first vs chasing by season, and the team's toss record. |
| `/players/:id/consistency` | Median score, spread, coefficient of variation, 30+/50+/under-10 shares, score distribution and recent form; bowling equivalents. |
| `/compare` (extended) | Consistency table, strike rate or economy by phase, season trend per player and a profile radar. |
| `/head-to-head` (extended) | Meetings by venue, both franchises' overall records over the same seasons, phases and toss in their meetings. |
| `/venues/:id` (extended) | Franchise records at the venue, top run scorers and wicket takers there, toss and phases at the venue. |
| `/` (extended) | "Season at a glance": average score, run rate, sixes per match and batting-first win %, each with the change from the previous season (computed by the backend) and a sparkline across seasons. |

**Interaction**

- **Drill-down**: chart items navigate. Top players and leaderboard bars open the player, franchise and venue bars
  open the team or venue, team and head-to-head season bars open the filtered match list, and recent-form bars open
  the match. `EChart` reports clicks (`onItemClick`); chart components translate them to the clicked category.
  Every drill-down chart has an equivalent table or link list for keyboard and screen-reader users.
- **Cross-filtering**: clicking a season in a by-season chart (dashboard, insights, venue, phases) writes `season`
  to the URL, so every query on the page refocuses together. Back undoes it.
- **Filters**: removable chips show the active filters; quick ranges ("Last season", "Last 3", "Last 5") set the
  season range from the latest season.
- **Charts**: zoom and pan (`zoom` prop, ECharts dataZoom) on long series; "Save as image" in every chart.

**CSV export**

- Every `DataTable` has "Export CSV", which saves the rows exactly as displayed (header titles, cell text).
  Thousands separators and "–" are undone, so spreadsheets read numbers. Opt out with `exportFileName={false}`.
- Players and matches also offer "Export all N", which fetches every filtered page (100 rows a request, through
  TanStack Query) up to 2,000 rows and writes raw API values (`features/exportColumns.ts`).
- `lib/csv.ts` follows RFC 4180 with a UTF-8 byte-order mark, and prefixes text starting with `= + - @` with `'` so a
  spreadsheet never runs it as a formula (numbers are left alone).

**Data handling**

- The HTTP client turns API `null`s into absent fields (`withoutNulls`), so runtime values match the generated
  types, where nullable fields are optional. This fixed "null–149"-style labels on real data.

**Tests added**: insight tabs (success, empty, error, team split, URL resolution), cross-filtering, filter chips and
presets, team phases and toss & form (with drill-down), player consistency (drill-down to a match), comparison and
head-to-head extensions, venue insights, dashboard trends and drill-down, leaderboard venue filter and drill-down,
CSV (field escaping, table export, export-all across pages), the null normalizer, and EChart click wiring. The
contract check covers the new endpoints, including a cross-check that runs by phase add up to the season totals.

## 10. V1.7: premium UI/UX

V1.7 is a presentation release: no API, analytics or backend change. Pages still only display values the API
returns.

### Design system

| Piece | Where | What it does |
|---|---|---|
| Brand tokens | `styles/index.css` | `--brand-from` / `--brand-to` (blue → orange) per theme, `--shadow-elevated`; utilities `bg-brand-gradient`, `text-brand-gradient`, `card-interactive` (hover lift and shadow), `hero-bar` / `hero-glow`. |
| Motion | `styles/index.css` | `animate-fade-up` (page and hero entry), `animate-slide-in` (mobile menu), shimmer on every `Skeleton`. All animations and transitions are cut to ~0 under `prefers-reduced-motion`. |
| Cards | `components/ui/card.tsx` | Rounded-xl surfaces; `StatCard` gains an icon chip, larger figures and trend pills (up = primary, down = orange, flat = muted; the arrow carries the meaning, not colour alone). |
| Theme | `ThemeProvider` (unchanged) | Light, dark or system; every new component uses theme tokens, chart colours follow the resolved theme. |

### Reusable components (new)

| Component | Use |
|---|---|
| `components/media/TeamLogo` | Franchise logo: the configured image if any, else an original shield crest in the team's colours (`xs`–`xl`). Decorative unless given a `label`. |
| `components/media/PlayerAvatar` | Player photo (lazy, async-decoded, fixed size, falls back on error) or initials on the latest team's colours. Always decorative: the name is next to it. |
| `components/layout/EntityHero` | Header of team, player, match, venue and dashboard pages: breadcrumbs, media, eyebrow, `h1`, badges, actions and a strip of headline figures, tinted with the subject's colours. |
| `components/cards/EntityCards` | `TeamCard`, `PlayerCard` (leader tile), `MatchCard` (both scores, winner emphasised, stage badge), `VenueCard`, `CardGrid`. Whole card is one link (no nested links). |
| `components/cards/RivalryBanner` | Head-to-head: crests, win counts and a bar split by share of wins. |
| `components/ui/segmented` | `SegmentedControl` (`aria-pressed` toggle group), e.g. cards / table view. |
| `components/loading/Skeletons` | `CardGridSkeleton` for card grids. |
| `EntityLinks` | `TeamLink logo` and `PlayerLink avatar` add a crest / avatar inline in tables. |

### Player and team imagery

- **Team branding** lives in `config/media/teamBranding.ts`, keyed by franchise code (the codes of
  `backend/src/main/resources/reference/franchises.csv`): kit colours plus optional `logoUrl`. Official logos are
  trademarks and are not bundled; the default is an original monogram crest. To use licensed logos, put files under
  `frontend/public/media/teams/` and set `logoUrl`.
- **Player photos** are resolved by `config/media/playerImages.ts` from the Cricsheet player id (`sourceId`, stable
  across database rebuilds): first `PLAYER_IMAGE_OVERRIDES`, then the build-time template
  `VITE_PLAYER_IMAGE_URL_TEMPLATE` (e.g. `/media/players/{sourceId}.webp` or a CDN URL). Components never contain
  image URLs. No photos ship by default (licensing), so every player shows an initials avatar.
- **Performance:** images are `loading="lazy"`, `decoding="async"` and have explicit width/height (no layout shift);
  a failed URL is remembered per component so it is not retried in a loop. Crests and initials are inline SVG/CSS:
  no request at all.
- References that carry only `{id, name}` (leaderboards, season leaders) have no `sourceId`, so they always use
  initials.
- **CSV export** of tables skips `aria-hidden` content, so avatars and crests never leak into exported cells.

### Reworked pages

| Page | Change |
|---|---|
| Layout | Gradient brand mark, active-item indicator, data credit in the sidebar; translucent sticky header with a season jump ("Go to season") and the theme toggle; animated mobile menu with a blurred backdrop; pages fade in on navigation (tabs inside a page do not re-animate). |
| Dashboard | Hero with the season selector; KPI cards; champion card with crest; highest team / individual score; Orange cap, Purple cap, strike-rate and economy leader cards; latest results as match cards. |
| Teams | Current and former franchises as team cards (crest, record, win-rate bar), all-time records table below. |
| Team / player / venue / match detail | `EntityHero` with crest or avatar and headline figures (team: record and titles from the seasons API; player: runs, average, strike rate, high score, wickets, economy, sharing the overview tab's queries and filters; match: scoreboard with both innings). |
| Matches | Cards (default) or table, chosen with a segmented control and kept in the URL (`view=table`). |
| Head-to-head | Rivalry banner above the figures. |
| Comparison | Selected players as avatar tiles in their team colours. |
| Players, leaderboards, seasons, match tables | Avatars and crests next to names. |

### Charts

Tooltips have padding, rounded corners, a theme-aware shadow and a dashed axis pointer; legends use rounded
markers with muted text; bars have rounded ends (unstacked), lines are 2.5 px with markers hidden on long series;
animations ease out.

### Responsive behaviour

Card grids go 1 → 2 → 3 → 4 columns; hero figure strips 2 → 3 → 4–6 columns; the header season jump and theme
toggle stay usable at 375 px; tables keep horizontal scrolling; the mobile menu slides in over a blurred backdrop.

### Tests

`components/premiumUi.test.tsx` (media configuration, crest/avatar fallback, entity cards, hero, rivalry banner,
segmented control, CSV without decorative content, teams grid, matches view switch, team hero titles, header season
jump) and `components/media/PlayerAvatar.test.tsx` (lazy photo and fallback on error). Page tests updated where the
hero now repeats a figure the overview tab also shows.
