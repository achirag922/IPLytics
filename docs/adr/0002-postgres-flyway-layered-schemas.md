# 0002 PostgreSQL + Flyway with staging / core / analytics schemas

**Status:** Accepted (V0)

## Context
Raw CSV data is messy (renamed teams, venue spelling variants, 0-indexed overs). Analytics need clean facts plus fast pre-aggregated read models. Everything must be reproducible from the source files.

## Decision
- PostgreSQL 16 for every environment (Docker Compose locally, Testcontainers in tests).
- Flyway owns the schema. Hibernate runs with `ddl-auto: validate` and never creates or alters tables.
- Three schemas with a one-way data flow:
  - `staging`: raw rows as loaded (text columns), tagged with the ingestion run.
  - `core`: normalized, validated reference data and ball-by-ball facts.
  - `analytics`: derived read models (materialized views) refreshed after each ingestion.
- Migration naming:
  - `V<n>__<description>.sql` for versioned DDL.
  - `R__<description>.sql` for views, which Flyway re-applies whenever their checksum changes.
- An applied versioned migration is never edited. Add a new one instead.

## Consequences
- Bad source data never reaches `core` unvalidated, and `analytics` can be rebuilt from `core` at any time.
- The API reads only `core` and `analytics`.
- V0 creates only the three schemas (`V1__create_schemas.sql`). Domain tables arrive in V1.
