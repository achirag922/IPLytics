# 0012 API envelope, validation, errors and caching (V1.4)

**Status:** Accepted (V1.4). Amends 0008 where they differ.

## Context
V1.4 exposes the analytics layer over REST for the React frontend. ADR 0008 set the base conventions in V0, before
the V1.4 requirements fixed the response shape and filter names.

## Decision
- **Envelope** `{data, meta}`; collections add `pagination {page, size, totalElements, totalPages}` (replaces 0008's
  `PageResponse.content`). `meta` carries `generatedAt`, `dataVersion` and the applied `filters`, so a client can
  show what it is looking at and detect stale data.
- **Filter names** follow the V1.4 spec: `season`, `seasonFrom`, `seasonTo`, `team`, `opponent`, `venue`, `stage`,
  `dateFrom`, `dateTo` (0008's `teamId` / `opponentId` / `venueId` are not used).
- **Status codes**: 400 for invalid input (bean validation on query objects and parameters, enum names with
  `allowedValues`), 404 for an unknown resource or request subject, 422 for a well-formed request that cannot be
  answered (unknown filter id, same team or player twice). 500 bodies carry only an `errorId`.
- **Package by feature**: `team`, `player`, `venue`, `season`, `match` and `analytics.{leaderboard, headtohead,
  comparison, matchup}`, each with thin controllers and services over the V1.3 query classes. No controller runs
  SQL or computes statistics.
- **HTTP caching by data version**: weak ETag `W/"t{transform}-a{refresh}"`, `Cache-Control: public, max-age=60`,
  and a 304 short-circuit in a `HandlerInterceptor` before the controller runs. No server-side cache yet.
- **Actuator** exposes `health` (no details), `info` and `metrics` (0008 had only health and info).
- **No Spring Security** in V1.4: the API is read-only and unauthenticated; the baseline is input validation,
  parameterized SQL, CORS limited to known origins and no internal details in responses.

## Consequences
- Generated TypeScript clients see one envelope shape and one problem schema on every operation.
- A refresh invalidates every ETag at once, which is coarse but always correct.
- Adding authentication later (V2+) means adding Spring Security without changing the envelope or error model.
