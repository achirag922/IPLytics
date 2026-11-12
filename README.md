# IPLytics: IPL Analytics & Intelligence Platform

A full-stack platform for exploring Indian Premier League data: team, player, match, venue and season analytics, head-to-heads, player comparisons and advanced batting and bowling analysis.

> **Status: V0 (foundation).** The skeleton, build, tests, CI and docs are in place. No IPL data or domain features yet. See the [roadmap](#roadmap).

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

- [Metric definitions](docs/metrics.md)
- [Architecture decisions](docs/adr/README.md)

## Roadmap

| Version | Scope |
|---|---|
| **V0** ✅ | Foundation: build, migrations baseline, OpenAPI, tests, CI, docs, frontend shell |
| V1 | CSV ingestion (CLI), core schema + analytics views; teams, players, matches, venues, seasons, leaderboards |
| V1.1 | Head-to-head, player comparison, batter-vs-bowler matchups |
| V1.2 | Phase analytics, dismissal analysis, toss/venue impact, worm and Manhattan charts |
| V1.3 | Hardening: HTTP caching, query tuning, observability, end-to-end tests, Docker images |
| V2 | Incremental ETL pipeline, data-quality reports |
| V3+ | Redis, Kafka (live data), AI insights, predictions |
