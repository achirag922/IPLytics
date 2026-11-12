# IPLytics: IPL Analytics & Intelligence Platform

A full-stack platform for exploring Indian Premier League data: team, player, match, venue and season analytics, head-to-heads, player comparisons and advanced batting and bowling analysis.

> **Status: V1.1 (dataset and staging layer).** CSV files load into validated PostgreSQL staging tables through a CLI. Core domain tables, analytics and UI pages come next. See the [roadmap](#roadmap).

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3.5, Spring Data JPA, Flyway, springdoc-openapi |
| Database | PostgreSQL 16 (Docker Compose locally, Testcontainers in tests) |
| Frontend | React 19, TypeScript, Vite, React Router, TanStack Query, ECharts, Tailwind CSS |
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
| http://localhost:8080/actuator/health | Health (includes database) |

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

## Tests and verification

```bash
# Backend: unit tests only (fast, no Docker)
cd backend && ./mvnw test

# Backend: unit + integration tests (Testcontainers starts PostgreSQL; Docker must be running) + package
cd backend && ./mvnw verify

# Frontend: the same checks CI runs
cd frontend && npm run lint && npm run typecheck && npm test && npm run build
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
- [Architecture decisions](docs/adr/README.md)

## Roadmap

| Version | Scope |
|---|---|
| **V0** ✅ | Foundation: build, migrations baseline, OpenAPI, tests, CI, docs, frontend shell |
| **V1.1** ✅ | Dataset and staging layer: Cricsheet JSON → converter → CSV → validated staging tables, run audit, rejected rows |
| V1.2+ | Core domain tables (staging → core), analytics views, REST APIs and UI pages; then head-to-head, comparisons, phase analytics, hardening |
| V2 | Incremental ETL pipeline, data-quality reports |
| V3+ | Redis, Kafka (live data), AI insights, predictions |
