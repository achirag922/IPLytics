# 0005 Analytics query strategy

**Status:** Accepted (V0, implemented in V1)

## Context
Most endpoints are aggregations with optional filters (season range, team, opponent, venue, phase, stage). JPA/JPQL handles entity reads well but handles dynamic aggregation poorly.

## Decision
- **JPA** (Spring Data) for entity lookups and reference data.
- **`NamedParameterJdbcTemplate`** with parameterized SQL for analytics queries. Results map to Java records. Every filter is a bind parameter, never string concatenation of user input.
- Materialized views in `analytics` at two grains: player per innings (batting and bowling) and team per match. Career, season, opponent, venue and leaderboard stats are `GROUP BY` queries over them. Phase and matchup queries read `core.delivery` directly.
- SQL returns **raw counts**. Ratios are computed in one place (`CricketMetrics`), following `docs/metrics.md`. When a leaderboard must sort by a ratio in SQL, a test checks that the SQL expression matches `CricketMetrics`.
- No external cache. Data changes only on ingestion, so V1.3 adds HTTP `ETag` / `Cache-Control` keyed on the latest ingestion run.

## Consequences
- Queries are explicit and reviewable, and their plans can be explained.
- There is no jOOQ code generation step. Revisit if the SQL grows hard to maintain.
