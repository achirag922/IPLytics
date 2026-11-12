# 0004 CLI-triggered, set-based ingestion

**Status:** Accepted (V0, implemented in V1)

## Context
V1 loads a static CSV dataset. We want a small, verified dataset flowing end to end early, and we want room for incremental or chunked loads later without building a framework now.

## Decision
- Ingestion runs only as a CLI: `java -jar iplytics.jar --spring.profiles.active=ingest --ingest.dir=...`. There is **no REST endpoint** for it.
- Pipeline, as plain Java services:
  1. Stage: PostgreSQL `COPY` loads the CSVs into `staging`.
  2. Validate the staged rows.
  3. Resolve team, venue and player aliases.
  4. Transform with set-based `INSERT … SELECT … ON CONFLICT DO UPDATE` into `core`.
  5. Refresh the `analytics` views.
  6. Reconcile totals.
- Each run is recorded in `core.ingestion_run`. A file whose SHA-256 hash was already loaded is skipped.
- Upserts are keyed on the source match id, so re-running is idempotent.
- **Ready for chunking:** each step takes a set of match ids or a season as its unit of work. V1 passes "all"; V2 can pass one season or one batch. No Spring Batch in V1.

## Consequences
- A full reload takes seconds and does not depend on JPA batch performance.
- Unknown team or venue names fail the run loudly instead of silently creating duplicates.
- Scheduling and incremental sources (V2) slot in by calling the same services.
