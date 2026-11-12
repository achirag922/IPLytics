# 0008 REST API conventions

**Status:** Accepted (V0)

## Decision
- All endpoints live under `/api/v1`. A breaking change means `/api/v2`.
- The API is read-only in V1, with no authentication. Ingestion is not exposed (ADR 0004).
- **OpenAPI from day one:**
  - springdoc generates `/v3/api-docs` and serves Swagger UI at `/swagger-ui.html`.
  - Every controller has a `@Tag` and every operation an `@Operation` summary.
- Errors are RFC 9457 `ProblemDetail` (`application/problem+json`). Stack traces are never returned.
- Responses are Java `record` DTOs. JPA entities are never serialized.
- Collections use a `PageResponse<T>` envelope with `page`, `size`, `totalElements`, `totalPages` and `content`.
- The same filter parameters mean the same thing everywhere: `seasonFrom`, `seasonTo`, `teamId`, `opponentId`, `venueId`, `phase`, `stage`.
- Resources use numeric surrogate ids. Teams also expose a stable `code` (e.g. `CSK`) for readable frontend URLs.
- Actuator exposes only `health` and `info`.

## Consequences
- Frontend types can be generated from the OpenAPI document, and API drift fails the build.
