# IPLytics: IPL Analytics & Intelligence Platform

A full-stack platform for exploring Indian Premier League data: team, player, match, venue and season analytics, head-to-heads, player comparisons and advanced batting and bowling analysis.

> **Status: V1.5 (React analytics dashboard).** Cricsheet IPL 2008–2026 data flows from source to staging, into a normalized `core` schema and a reconciled `analytics` layer, is served by a documented read-only REST API under `/api/v1`, and is explored in a React dashboard (teams, players, matches with scorecards and worm/Manhattan charts, venues, seasons, head-to-head, comparisons, matchups, leaderboards). See the [roadmap](#roadmap).

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3.5, Spring Data JPA, Flyway, springdoc-openapi |
| Database | PostgreSQL 16 (Docker Compose locally, Testcontainers in tests) |
| Frontend | React 19, TypeScript, Vite, React Router, TanStack Query, ECharts, Tailwind CSS, shadcn/ui; Vitest, Testing Library, MSW |
| Testing | JUnit 5, Mockito, AssertJ, Testcontainers, Vitest, React Testing Library |
| CI | GitHub Actions |

## Prerequisites

- JDK 17+ (Maven is provided by the wrapper; no separate install needed)
- Node.js 20.19+ (22 LTS recommended)
- Docker Desktop (for PostgreSQL and for the integration tests)

## Quick start

```bash
# 1. Start PostgreSQL (copy .env.example to .env first if you need non-default settings)
docker compose up -d
docker compose ps            # wait until the status shows "healthy"

# 2. Start the backend on http://localhost:8080 (Flyway migrates on startup)
cd backend
./mvnw spring-boot:run       # Windows cmd/PowerShell: mvnw.cmd spring-boot:run

# 3. In another terminal, start the frontend on http://localhost:5173
cd frontend
npm install                  # first time only
npm run dev
```

If port 5432 is already taken, set `DB_PORT` (for example `DB_PORT=5434`) both in `.env` (for Compose) and in the backend's environment.

| URL | What |
|---|---|
| http://localhost:5173 | Frontend |
| http://localhost:8080/swagger-ui.html | Swagger UI |
| http://localhost:8080/v3/api-docs | OpenAPI document (JSON) |
| http://localhost:8080/api/v1/system/status | App version + database schema version |
| http://localhost:8080/actuator/health | Health (includes database; status only) |
| http://localhost:8080/api/v1/seasons | REST API (see [docs/api.md](docs/api.md)) |

Stop PostgreSQL with `docker compose down`. Add `-v` to also delete the data volume.

## Data ingestion

The data comes from **Cricsheet** (ball-by-ball JSON, licensed ODC-By 1.0). A converter turns the official download into three CSV files, and an ingestion run loads them into PostgreSQL **staging** tables. Every value is stored as text exactly as read, after explicit validation; PostgreSQL never casts anything silently. Source, column mapping and attribution are in [docs/data-source.md](docs/data-source.md).

1. **Download the data.** Get the *Indian Premier League* JSON zip from <https://cricsheet.org/downloads/> and save it, unchanged, as `data/cricsheet/ipl_json.zip`. That folder is git-ignored.
2. **Build the jar** (from the repository root):

```bash
cd backend && ./mvnw -q -DskipTests package && cd ..
```

3. **Convert it** (no database needed). This writes `matches.csv`, `deliveries.csv`, `match_players.csv` and `cricsheet-conversion-report.json` to `data/raw/`, and prints matches per season, rejected files, duplicate match ids and missing fields. It exits with `1` if the report lists problems.

```bash
java -jar backend/target/iplytics-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=convert --convert.source=data/cricsheet/ipl_json.zip --convert.output-dir=data/raw
```

4. **Start PostgreSQL:** `docker compose up -d --wait`
5. **Ingest it:**

```bash
java -jar backend/target/iplytics-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=ingest --ingest.dir=data/raw
```

Flyway migrates the schema before any file is read. The exit code is `0` when every file was loaded or skipped, and `1` when anything failed.

6. **Transform staging into the core schema.** This resolves seasons, franchises, venues and players, loads matches, innings, deliveries, wickets and playing lists, then runs the reconciliation checks. It's all one transaction: it either fully succeeds or changes nothing. A rerun changes 0 rows.

```bash
java -jar backend/target/iplytics-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=transform
```

7. **Refresh the analytics layer.** This refreshes the materialized views concurrently, then reconciles them against core. If any check fails it rolls back, leaving the previous analytics in place.

```bash
java -jar backend/target/iplytics-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=analytics
```

You can run steps 5–7 together with `--spring.profiles.active=ingest,transform,analytics`. See [docs/analytics.md](docs/analytics.md).

You can also combine just the ingest and transform steps with `--spring.profiles.active=ingest,transform`. Each run, with its checks, is recorded in `core.transform_run`. The rules are in [docs/data-source.md §10](docs/data-source.md) and [ADR 0010](docs/adr/0010-core-model-and-transformation.md).

Expected output (shown here for the test fixtures; real counts come from your download):

```
20:53:23.488 INFO  matches.csv -> run #1 COMPLETED: read 5, loaded 5, rejected 0 (320 ms)
20:53:23.682 INFO  deliveries.csv -> run #2 COMPLETED: read 14, loaded 14, rejected 0 (194 ms)
20:53:23.832 INFO  match_players.csv -> run #3 COMPLETED: read 28, loaded 28, rejected 0 (147 ms)
20:53:23.836 INFO  Ingestion finished: 3 loaded, 0 skipped, 0 failed
```

- **Re-running is safe.** Files are identified by their SHA-256 hash, so a second run prints `SKIPPED: identical file already loaded by run #1` and loads nothing twice.
- **Invalid rows are kept.** Rows that fail validation (empty row, wrong number of values, missing required value, bad number or date) still let the valid rows load. The run reports `rejected N` and stores each bad row in `staging.rejected_row` with its reason and raw values.
- **Some problems fail the whole file:** missing required columns, malformed CSV, a file that isn't UTF-8, or more than `ingest.max-rejected-rows` invalid rows. Nothing from that file is loaded, and fixing the file and re-running is allowed.
- **Re-converting the same zip is safe too.** The converter is deterministic, so the CSVs come out byte-identical and the next ingestion skips them.
- **To try it without the real download,** convert the hand-written fixtures: `--convert.source=backend/src/test/resources/cricsheet --convert.output-dir=<any empty folder>`. The source can be the zip or an extracted folder.

**Verify staging data** (open a SQL shell with `docker compose exec postgres psql -U iplytics -d iplytics`):

```sql
-- One row per file ingestion attempt
select id, dataset, file_name, status, rows_read, rows_loaded, rows_rejected, error_summary
  from core.ingestion_run order by id;

-- Staged rows per run
select ingestion_run_id, count(*) from staging.matches group by 1;
select ingestion_run_id, count(*) from staging.deliveries group by 1;
select ingestion_run_id, count(*) from staging.match_players group by 1;

-- Matches per IPL season (season_year is derived from the match date; season is Cricsheet's label)
select season_year, season, count(*) from staging.matches group by 1, 2 order by 1;

-- Why rows were rejected
select ingestion_run_id, source_row_number, reason_code, reason, raw_data
  from staging.rejected_row order by id;
```

| Setting | Default | Meaning |
|---|---|---|
| `ingest.dir` | `data/raw` | Folder to scan (not recursive) |
| `ingest.max-rejected-rows` | `1000` | A file with more invalid rows than this fails as a whole |
| `ingest.stale-run-timeout` | `1h` | Runs still RUNNING after this long (e.g. a killed process) are marked FAILED on the next run |

## REST API

Read-only, under `/api/v1`, documented in Swagger UI and [docs/api.md](docs/api.md). The data must have been
converted, ingested, transformed and refreshed (sections above). A few requests to try:

```bash
curl "http://localhost:8080/api/v1/seasons/2024/points-table"
curl "http://localhost:8080/api/v1/leaderboards/batting?metric=strikeRate&seasonFrom=2020&minimumBalls=500&limit=5"
curl "http://localhost:8080/api/v1/players?search=kohli"
curl "http://localhost:8080/api/v1/matches?season=2023&stage=Final"
curl "http://localhost:8080/api/v1/head-to-head?team1=2&team2=4"   # team ids from /api/v1/teams
```

Responses are `{data, meta}` (collections add `pagination`); errors are RFC 9457 problem details; responses carry
an ETag, and `If-None-Match` returns `304` while the data is unchanged. `IPLYTICS_CORS_ALLOWED_ORIGINS` sets the
browser origins allowed to call the API (default `http://localhost:5173`). Run with `--spring.profiles.active=prod`
to turn off Swagger UI and the OpenAPI endpoint.

## Tests and verification

```bash
# Backend: unit tests only (fast, no Docker)
cd backend && ./mvnw test

# Backend: unit + integration tests (Testcontainers starts PostgreSQL; Docker must be running) + package
cd backend && ./mvnw verify

# Frontend: the same checks CI runs
cd frontend && npm run lint && npm run format:check && npm run typecheck && npm test && npm run build

# Frontend/backend contract check (needs the backend running on :8080 with data loaded)
cd frontend && npm run test:contract

# After a backend API change: regenerate the frontend's API types from backend/target/openapi.json
cd frontend && npm run generate:api
```

## Project layout

```
backend/            Spring Boot API (package-by-feature: com.iplytics.<feature>.{api,service,...})
  src/main/resources/db/migration/   Flyway migrations (V<n>__*.sql, R__*.sql)
frontend/           React SPA (app/, api/, features/<area>/, components/)
data/               raw/ (local CSVs, git-ignored) and reference/ (curated alias maps)
docs/metrics.md     Cricket metric definitions: the single source of truth for every calculation
docs/adr/           Architecture Decision Records
.github/workflows/  CI pipeline
```

## Documentation

- [Data source and expected CSV format](docs/data-source.md)
- [Metric definitions](docs/metrics.md)
- [Analytics layer](docs/analytics.md)
- [REST API](docs/api.md)
- [Frontend](docs/frontend.md)
- [Architecture decisions](docs/adr/README.md)

## Roadmap

| Version | Scope |
|---|---|
| **V0** ✅ | Foundation: build, migrations baseline, OpenAPI, tests, CI, docs, frontend shell |
| **V1.1** ✅ | Dataset and staging layer: Cricsheet JSON → converter → CSV → validated staging tables, run audit, rejected rows |
| **V1.2** ✅ | Core data model: seasons, franchises, venues, players (Cricsheet ids), matches, innings, deliveries, wickets, playing lists; idempotent set-based transform with reconciliation |
| **V1.3** ✅ | Analytics layer: batting/bowling/innings/team-match views, formula functions mirrored by CricketMetrics, phases, concurrent audited refresh, team/player/venue/head-to-head/leaderboard queries |
| **V1.4** ✅ | REST API: seasons, points tables, teams, venues, players, matches with scorecards and progression, head-to-head, comparisons, matchups, leaderboards; OpenAPI contract, problem-detail errors, ETag caching |
| **V1.5** ✅ | React analytics dashboard: dashboard, teams, players, matches (scorecard, worm, Manhattan), venues, seasons, head-to-head, comparison, matchups, leaderboards; URL filters, generated API types, page tests with MSW, frontend/backend contract check |
| V1.6+ | Hardening |
| V2 | Incremental ETL pipeline, data-quality reports |
| V3+ | Redis, Kafka (live data), AI insights, predictions |
