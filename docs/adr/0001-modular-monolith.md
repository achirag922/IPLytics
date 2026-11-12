# 0001 Modular monolith in a monorepo

**Status:** Accepted (V0)

## Context
IPLytics is a read-heavy analytics product built by a small team. The data changes only when an ingestion runs. Kafka, Redis, AI and predictions are planned for later, not now.

## Decision
- One Spring Boot application (`backend/`), one React SPA (`frontend/`), one PostgreSQL database, all in a single Git repository.
- The backend is **packaged by feature** (`team`, `player`, `match`, `venue`, `season`, `analytics`, `ingestion`, `system`). Each feature has `api/` (controllers, DTOs), `service/`, and as needed `domain/` (JPA entities), `repository/`, `query/` (analytics SQL).
- `common/` holds only cross-cutting code (config, error handling, shared filters, metric formulas).
- Features talk to each other through services, never by reaching into another feature's repositories.

## Consequences
- One build, one deployment, one transaction boundary. Simple to run and debug.
- Feature packages are the seams where a service could later be extracted, for example ingestion as a separate ETL job in V2.
- Boundary discipline is a convention for now. ArchUnit tests can enforce it once there are enough features.
