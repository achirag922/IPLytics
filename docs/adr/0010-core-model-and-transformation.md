# 0010 Core model and staging → core transformation

**Status:** Accepted (V1.2)

## Context
Staging holds the Cricsheet-derived rows as text, one batch per ingestion run. Analytics need typed,
normalized data with stable identities: franchises across renames, venues across spelling changes, and
players across name changes. The transformation must be repeatable, fast at about 300K deliveries, and
must never leave core partly updated or silently wrong.

## Decision
1. **Core is a projection of the latest completed staging batch of each dataset**
   (`MATCHES`, `DELIVERIES`, `MATCH_PLAYERS`, read from `core.ingestion_run`).
   - It is built in **one database transaction** with set-based SQL:
     - typed temporary copies of staging;
     - `INSERT … SELECT … ON CONFLICT (natural key) DO UPDATE … WHERE IS DISTINCT FROM`;
     - pruning of rows that left the source.
   - There is no JPA and no row-by-row work.
   - Natural keys: `match.source_match_id`, `(match, innings_number)`, `(innings, over, ball_in_over)`,
     `(delivery, wicket_number)`, `(wicket, fielder_number)`, `(match, player)`, `season.year`, `team.code`,
     `team_name_history.name`, `venue_alias.alias`, `player.cricsheet_id`.
   - Consequences: surrogate ids stay stable across reruns, and a rerun changes **zero rows**.
2. **A reconciliation gate inside the same transaction.**
   - Validation runs before loading: unknown teams, season mapping, ambiguous names, staging orphans.
   - Reconciliation runs after loading: counts, checksums, orphans, referential consistency, and cricket arithmetic.
   - Any failure rolls everything back. Each run, including its check results, is audited in `core.transform_run`.
3. **Reference data is versioned with the code** (`backend/src/main/resources/reference/*.csv`) and validated on load.
   - Only franchises and venues actually used by the data are materialized in core, so curated rows never become orphans.
   - **Unknown team names fail** the transform.
   - **Unknown venue names are created as separate, non-curated venues and reported**, never merged.
4. **Player identity is the Cricsheet registry id.**
   - The display name is the one used in the player's most recent match.
   - Every name used becomes a `player_alias` with its season range.
   - A name without an id gets a separate, name-identified player and is never merged into an id-bearing player.
5. **Dismissals are modelled as child tables** `core.wicket` and `core.wicket_fielder`.
   - These go beyond the originally listed tables. A ball can carry more than one wicket, and a wicket several fielders, including substitutes.
   - Columns on `delivery` would either lose this or need unenforceable arrays.
   - `delivery.is_wicket` is kept as a reconciled convenience flag.
6. **Season year = calendar year of the match date.** Cricsheet's label is kept as `season.source_season`.

## Consequences
- **Bad source data can't slip through:** the transform either fully succeeds or leaves core untouched, with the reason recorded.
- **Changes are cheap:** adding a renamed team or venue alias is a CSV change plus a rerun.
- **Speed:** a full reload of 19 seasons takes about 20 s, dominated by comparing 295K deliveries.
- **Analytics views (V1.3) can rely on** stable ids, typed columns, and the constraints enforced by the schema:
  - `total = batter + extras` and `extras = breakdown`;
  - super overs are innings 3+;
  - winners and toss winners are teams in the match.
