# Analytics layer (V1.3)

Reusable analytics built on the normalized `core` schema. Formulas follow [metrics.md](metrics.md); the design is
in [ADR 0011](adr/0011-analytics-layer.md).

```
core (V1.2)  →  analytics schema: SQL formula functions + views + materialized views (V7 migration)
             →  query layer: com.iplytics.analytics.query (raw counts from SQL, ratios via CricketMetrics)
             →  V1.4 REST APIs (not built yet)
```

## 1. Objects

| Object | Type | Grain | Why this type |
|---|---|---|---|
| `analytics.innings_context` | view | one innings | Cheap 3-table join (innings + match + season) shared by everything else |
| `analytics.innings_summary` | **materialized** | one innings | Aggregates all deliveries and wickets |
| `analytics.batting_innings` | **materialized** | one player × one innings | Aggregates all deliveries, computes batting order, picks the dismissal |
| `analytics.bowling_innings` | **materialized** | one player × one innings | Aggregates all deliveries and credited wickets |
| `analytics.team_match_result` | view | one team × one match | Only 2 rows per match, read from `innings_summary` + `core.match`; cheap to compute on demand |
| `analytics.refresh_run` | table | one refresh | Audit: status, the transform run used, row counts, checks, warnings |

Rows on real data (IPL 2008–2026): 2,514 innings summaries, 19,196 batting innings, 14,734 bowling innings and
2,486 team-match rows.

All analytical objects carry `match_id`, `season_id`, `season_year`, `venue_id`, `match_date`, `innings_number` and
`is_super_over`, so every query can filter and exclude super overs without joins.

### `innings_summary`

One row per innings, super overs included (`is_super_over`).

| Column | Rule |
|---|---|
| `total_runs` | Σ `total_runs`, including extras and penalty runs |
| `extras` | Σ `extra_runs` |
| `wickets` | Dismissals in the innings (`analytics.is_dismissal`: retired hurt and retired not out excluded) |
| `legal_balls` | Balls with no wide and no no-ball |
| `overs` | `analytics.overs(legal_balls)`, e.g. `19.4`. Display only |
| `run_rate` | `analytics.run_rate(total_runs, legal_balls)` |
| `fours` / `sixes` | Batter scored 4 / 6 **and** `non_boundary = false` |
| `dot_balls` | Legal balls with `total_runs = 0` (from the batting side's view, a leg-bye is not a dot) |
| `*_runs`, `*_wickets` by phase | By `analytics.phase` of the delivery. All 0 for super overs |

### `batting_innings`

One row per player who was at the crease in an innings: as striker, as non-striker, or by being dismissed.

| Column | Rule |
|---|---|
| `team_id` / `opponent_team_id` | Batting / bowling franchise |
| `batting_position` | Order of first appearance at the crease (striker before non-striker on the same ball) |
| `runs` | Σ batter runs. Extras are never the batter's |
| `balls` | Balls faced: every delivery except **wides**. No-balls, byes and leg-byes count |
| `fours` / `sixes` | 4 / 6 off the bat and not `non_boundary` (a run four is not a boundary) |
| `dot_balls` | Balls faced with 0 batter runs |
| `strike_rate` | `analytics.strike_rate(runs, balls)`, null if 0 balls |
| `is_out` / `dismissal_kind` | `is_out` means a dismissal of this player (retired hurt / not out are **not**; retired out **is**; includes non-striker run outs). `dismissal_kind` shows the dismissal, or the retirement if that's all there was |
| phase runs / balls | Per `analytics.phase`. All 0 for super-over innings |

### `bowling_innings`

One row per bowler per innings.

| Column | Rule |
|---|---|
| `team_id` / `opponent_team_id` | Bowling / batting franchise |
| `legal_balls` | Balls with no wide and no no-ball |
| `runs_conceded` | `analytics.bowler_runs` = total − byes − leg-byes − penalty (wides and no-balls are charged) |
| `wickets` | `analytics.is_bowler_wicket`: bowled, caught, caught and bowled, lbw, stumped, hit wicket. Run outs, retirements and obstructing are not credited |
| `dot_balls` | Legal balls with 0 runs charged (a bye or leg-bye ball is a dot for the bowler) |
| `fours_conceded` / `sixes_conceded` | Boundaries off the bat (not `non_boundary`) |
| `wides` / `no_balls` | Number of wide / no-ball **deliveries**. `wide_runs` / `no_ball_runs` hold the extras runs |
| `economy` | `analytics.economy(runs_conceded, legal_balls)` |
| phase balls / runs | Legal balls and charged runs per phase. All 0 for super overs |

### `team_match_result`

Two rows per match, one from each team's side.

| Column | Rule |
|---|---|
| `won` / `lost` | Official result. **Includes ties decided by a super over** |
| `tied` | The match was tied (true for both teams, whatever the super-over result) |
| `no_result` | No result; it doesn't count in win percentage |
| `batted_first` / `chased` | Batted in innings 1 / innings 2. Both false for the side that never batted in a washout |
| `toss_won` | Won the toss |
| `is_home` | The venue's curated city equals the franchise's curated `home_city`. See limitations |
| `runs_for` / `runs_against`, `balls_faced` / `balls_bowled`, `wickets_lost` / `wickets_taken` | From the two **regular** innings. Super overs excluded |
| `run_rate` / `opponent_run_rate` | `analytics.run_rate` on those totals |
| `result`, `method` | Copied from the match, e.g. `D/L` |

## 2. Formulas: a single definition in each language

Every ratio and rule is one `IMMUTABLE` SQL function in the `analytics` schema, mirrored by
`com.iplytics.common.metrics.CricketMetrics` / `CricketPhase`. All ratios are computed from **raw totals**,
are `NULL` when the denominator is 0, and are rounded to 2 decimals half-up.

| SQL function | Java | Formula |
|---|---|---|
| `strike_rate(runs, balls)` | `strikeRate` | 100 × runs ÷ balls |
| `batting_average(runs, dismissals)` | `battingAverage` | runs ÷ dismissals |
| `economy(runs_conceded, legal_balls)` | `economy` | 6 × runs ÷ legal balls |
| `run_rate(runs, legal_balls)` | `runRate` | 6 × runs ÷ legal balls |
| `bowling_average(runs_conceded, wickets)` | `bowlingAverage` | runs ÷ wickets |
| `bowling_strike_rate(legal_balls, wickets)` | `bowlingStrikeRate` | legal balls ÷ wickets |
| `percentage(part, whole)` | `percentage`, `dotBallPercentage`, `boundaryPercentage` | 100 × part ÷ whole |
| `balls_per_boundary(balls, boundaries)` | `ballsPerBoundary` | balls ÷ boundaries |
| `win_percentage(wins, played, no_results)` | `winPercentage` | 100 × wins ÷ (played − no results) |
| `overs(legal_balls)` | `overs` | `legal_balls / 6` "." `legal_balls % 6` |
| `phase(over_number, is_super_over)` | `CricketPhase.of` | Super over → `super_over`; overs 0–5 `powerplay`, 6–14 `middle`, 15+ `death` |
| `bowler_runs`, `is_bowler_wicket`, `is_dismissal` | (SQL only) | See the tables above |

**Rule:** SQL returns raw counts; the query layer computes displayed ratios with `CricketMetrics`. SQL uses a
ratio only to **rank** (leaderboards), and then always through these functions. `AnalyticsIT` checks:
- every function against Java over a 61×41 input grid;
- every stored ratio in the materialized views;
- every leaderboard value.

## 3. Refresh

- **How to run it:**
  - `java -jar iplytics.jar --spring.profiles.active=analytics`, run after the transform;
  - or the whole pipeline: `--spring.profiles.active=ingest,transform,analytics`.
- **What happens:** `AnalyticsRefreshService` runs, in **one transaction** holding an advisory lock:
  1. `REFRESH MATERIALIZED VIEW CONCURRENTLY`, in the fixed order innings_summary → batting_innings → bowling_innings.
  2. Reconciliation.
  3. Commit, or roll back if any check fails, leaving the previous analytics in place.
- **Readers are never blocked** (`CONCURRENTLY`, so a unique grain index exists on every materialized view).
- **Deterministic:** the same core data always gives the same analytics.
- **Audit:** each run is recorded in `analytics.refresh_run` with the `core.transform_run` it was built from, so a
  consumer can tell when analytics are stale (a newer completed transform exists). Analytics change only when this
  refresh runs; there is no scheduler.
- **Timing on real data:** about 4 s for the three views (innings summary 0.5 s, batting 1.9 s, bowling 1.4 s), and
  6–9 s in total including the checks.

## 4. Reconciliation (any failure rolls the refresh back)

Expected values are always recomputed from core; nothing is hard-coded.

- **Counts:**
  - innings summaries equal core innings;
  - batting innings equal the distinct (innings, player) pairs at the crease;
  - bowling innings equal the distinct (innings, bowler) pairs;
  - team-match rows equal 2 × matches;
  - super-over rows equal super-over innings.
- **Checksums, analytics = core:**
  - runs, extras, legal balls and dismissals;
  - batter runs, balls faced, fours, sixes and dismissals;
  - bowler legal balls, charged runs and credited wickets;
  - wins, no-results and ties.
- **Grain and keys:**
  - no duplicate grain in any object;
  - no orphan player, innings, team, venue, season or match keys;
  - every batter and bowler is in the match playing list.
- **Isolation:**
  - super-over rows have zero phase values;
  - for regular rows, the phases add up exactly to the totals;
  - `runs_for` equals the regular innings total;
  - batting positions are unique per innings.
- **Warnings (do not fail):**
  - `actual_delivery` labels that disagree with legal-ball counting: 0 on real data;
  - overs with more than 6 legal balls: 4 on real data (umpire miscounts recorded by Cricsheet).

## 5. Query layer (`com.iplytics.analytics.query`): intended consumers are the V1.4 REST APIs

All queries accept an `AnalyticsFilter`:
- `seasonFrom` / `seasonTo`
- `teamId`, `opponentTeamId`
- `venueId`
- `withSuperOvers()`. **Super overs are excluded unless requested.**

All parameters are bound, never concatenated.

| Class | Methods | Returns |
|---|---|---|
| `BattingQueries` | `career`, `bySeason` | Innings, runs, balls, not outs, dismissals, 4s, 6s, dots, 50s, 100s, ducks, highest (not-out flag), plus average, strike rate, dot %, boundary %, balls per boundary |
| `BowlingQueries` | `career`, `bySeason` | Innings, legal balls, overs, runs, wickets, dots, 4s and 6s conceded, wides, no-balls, 3W/4W/5W hauls, best figures, plus economy, average, strike rate, dot % |
| `TeamQueries` | `summary`, `bySeason` | Overall, batting-first, chasing, home and away records (played / won / lost / tied / NR / win %), average score and average conceded per innings, run rates |
| `VenueQueries` | `summary` | Matches, decisive matches, batting-first and chasing wins and %, average first-innings score, highest total, lowest completed total, average run rate |
| `HeadToHeadQueries` | `overall`, `bySeason`, `byVenue` | Matches, team A / team B wins, ties, no results, wins batting first and chasing for each side |
| `LeaderboardQueries` | `batting(metric, filter, minBalls, limit)`, `bowling(metric, filter, minLegalBalls, limit)` | Ranked entries with raw counts and the ranked value |

**Leaderboards:**
- Batting metrics: `RUNS`, `AVERAGE`, `STRIKE_RATE`, `FOURS`, `SIXES`, `HUNDREDS`, `FIFTIES`.
- Bowling metrics: `WICKETS`, `ECONOMY`, `AVERAGE`, `STRIKE_RATE`, `DOT_BALLS`.
- **Ratio boards need a minimum sample**, so tiny samples can't top the table. Defaults come from metrics.md §7:
  - strike rate: 100 balls;
  - batting average: 5 dismissals;
  - economy: 60 legal balls;
  - bowling average and strike rate: 5 wickets.
  The ball thresholds can be overridden.
- **Ties are broken deterministically:** runs or wickets, then balls or runs, then name, then id.

**Venue definitions:**
- *Decisive* means won by runs or wickets; ties and no-results are excluded.
- The *average first-innings score* uses matches with a result and no D/L adjustment.
- The *lowest completed total* is the lowest innings that was all out or used its full scheduled overs.

## 6. Performance (real data, PostgreSQL 16 in Docker on a laptop)

| Query | Time |
|---|---|
| Full rebuild of the three materialized views | about 4 s |
| All-time top run-scorers / wicket-takers (aggregates 19K / 15K rows) | 30 ms / 11 ms |
| Strike-rate leaderboard with a minimum of 500 balls | 12 ms |
| All franchise records (`team_match_result` view) | 30 ms |
| Head-to-head (MI vs CSK, 41 matches) | 16 ms |
| One venue summary | 3 ms |
| One player's career (index on `player_id`) | 1.1 ms (2.1 ms without the index) |

**Indexes:**
- **Unique grain indexes** on the materialized views (required for concurrent refresh).
- **`player_id` indexes** on `batting_innings` and `bowling_innings`, plus **`match_id`** on `innings_summary`
  (used by the `team_match_result` joins). The `player_id` indexes give a modest gain at today's size; they're cheap
  and serve per-player API lookups.
- **No new core indexes** were needed: the view builds are full aggregations, where sequential scans are optimal.

## 7. Known limitations

- **Home/away:** `is_home` compares the venue city with the franchise's single curated home city
  (`reference/franchises.csv`). Second home grounds (e.g. Guwahati for RR, Dharamsala for PBKS), temporary
  relocations (CSK in Pune in 2018) and neutral seasons (UAE 2014/2020/2021, South Africa 2009) count as away.
  Matches at a new venue missing from the reference data are never home.
- **Phases:** use fixed over boundaries even when D/L shortened the powerplay.
- **Not modelled yet:** maidens, net run rate and partnerships.
- **Impact Player** substitutions aren't modelled (players appear through the playing list).
- **Super overs** exist in every object with `is_super_over = true`. They're excluded only by the query-layer
  default and by explicit filters, so direct SQL users must filter them themselves.
