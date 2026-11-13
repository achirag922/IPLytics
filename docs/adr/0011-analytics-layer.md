# 0011 Analytics layer

**Status:** Accepted (V1.3). Refines ADR 0005.

## Context
V1.4 APIs need fast, consistent cricket statistics over 295K deliveries. Formulas must be defined once, super
overs must not leak into normal statistics, and analytics must stay consistent with core after every transform.

## Decision
1. **Choose the object type per object.**
   - Materialized views for the three expensive per-innings aggregations: `innings_summary`, `batting_innings`
     and `bowling_innings`.
   - Plain views for cheap joins: `innings_context` and `team_match_result`, the latter being 2 rows per match.
2. **Formulas live in SQL functions** in the `analytics` schema (`strike_rate`, `economy`, `phase`, `bowler_runs`,
   `is_bowler_wicket`, …), used by the views and the leaderboard ranking.
   - Java `CricketMetrics` / `CricketPhase` mirror them, and tests compare the two exhaustively.
   - Ratios are computed from raw totals, rounded to 2 decimals half-up, and are `NULL` when the denominator is 0.
3. **SQL returns raw counts; Java computes displayed ratios.** SQL computes a ratio only to rank, and only through
   the functions.
4. **Refresh is explicit and audited.**
   - `AnalyticsRefreshService` refreshes the views `CONCURRENTLY` in a fixed order and reconciles them against
     core, all in one transaction, then records `analytics.refresh_run` with the source transform run.
   - It's run by the `analytics` profile, typically as `ingest,transform,analytics`. There is no scheduler or cache yet.
5. **Super-over rows stay in every object, flagged** with `is_super_over` and with zeroed phase values. The query
   layer excludes them by default (`AnalyticsFilter.withSuperOvers()` includes them).
6. **Home/away uses a curated franchise `home_city`** (reference data), because the source has no notion of home.

## Consequences
- **One place to change a formula per language,** with a test that fails if the two drift apart.
- **Readers are never blocked by a refresh,** and a failed refresh leaves the previous analytics in place.
- **Analytics can lag core** until the refresh runs. `refresh_run.transform_run_id` makes that lag visible.
- **Home/away is approximate** for second homes, relocations and neutral seasons (docs/analytics.md §7).
