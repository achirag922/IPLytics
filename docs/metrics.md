# Cricket metric definitions

This file is the **single source of truth** for how IPLytics computes statistics.
Code (SQL views, `CricketMetrics`) must match it, and tests must cite the rule they cover.
Change this file in the same pull request as any change to a calculation.

## 1. Scope rules

| Rule | Decision |
|---|---|
| Super overs | Excluded from all player and team aggregates. Shown only in match detail. |
| No-result / abandoned matches | Deliveries actually bowled **count** toward player stats. The match counts as "no result" for team records. |
| DLS / reduced-overs matches | Included. The result stands as officially recorded. |
| Season | The IPL edition year (a source label like `2007/08` maps to 2008). |
| Franchise continuity | Team stats aggregate at franchise level (Delhi Daredevils + Delhi Capitals = one team). Deccan Chargers and Sunrisers Hyderabad are different franchises. See ADR 0003. |
| Rounding | Ratios are computed from raw totals (never an average of averages) and rounded to 2 decimals, HALF_UP, only when displayed. |
| Undefined ratios | Division by zero returns `null` (shown as "–"), never 0 or infinity. |

## 2. Deliveries

| Term | Definition |
|---|---|
| Legal delivery | `extras_type` is not `wides` or `noballs`. Counts toward the over (6 per over). |
| Ball faced (batter) | Every delivery except a **wide**. No-balls, byes and leg-byes count as balls faced. |
| Runs charged to bowler | `total_runs` minus extras of type `byes`, `legbyes` or `penalty`. Wides and no-ball runs **are** charged. |
| Four / six | `batter_runs = 4` / `batter_runs = 6`. *Limitation:* the source cannot separate all-run fours from boundaries. |
| Over number | Source data is 0-indexed (`over = 0` is the 1st over). Displays are 1-indexed. |

## 3. Phases

| Phase | Overs (1-indexed) | Source `over` (0-indexed) |
|---|---|---|
| Powerplay | 1–6 | 0–5 |
| Middle | 7–15 | 6–14 |
| Death | 16–20 | 15–19 |

*Limitation:* fixed boundaries are used even when a DLS-shortened match had a shorter powerplay.

## 4. Batting

| Metric | Formula |
|---|---|
| Innings | Distinct innings in which the player appeared as batter **or** non-striker. |
| Runs | Σ `batter_runs` (extras never count). |
| Balls faced | Count of balls faced (see §2). |
| Dismissals | Times the player is `player_dismissed` with a kind other than `retired hurt` / `retired not out`. `retired out` **is** a dismissal. Includes run-outs at the non-striker's end. |
| Not outs | Innings − dismissals. |
| Average | Runs ÷ dismissals. `null` if dismissals = 0. |
| Strike rate | Runs × 100 ÷ balls faced. |
| Dot-ball % | Balls faced with `batter_runs = 0` × 100 ÷ balls faced. |
| Boundary % | (Fours + sixes) × 100 ÷ balls faced. |
| Balls per boundary | Balls faced ÷ (fours + sixes). |
| 50s / 100s | Innings score 50–99 / ≥ 100 (a hundred is not also a fifty). |
| Duck | Innings score 0 **and** dismissed. |
| Highest score | Max innings runs. Suffix `*` if not out. |

## 5. Bowling

| Metric | Formula |
|---|---|
| Balls bowled | Count of legal deliveries. |
| Overs | `balls / 6` "." `balls % 6` (e.g. 22 balls = `3.4`). Display only, never used in arithmetic. |
| Runs conceded | Σ runs charged to bowler (see §2). |
| Wickets | `dismissal_kind` ∈ {bowled, caught, caught and bowled, lbw, stumped, hit wicket}. Run out, retired, obstructing the field: **not** credited. |
| Economy | Runs conceded × 6 ÷ balls bowled. |
| Average | Runs conceded ÷ wickets. `null` if wickets = 0. |
| Strike rate | Balls bowled ÷ wickets. |
| Dot balls | Legal deliveries with 0 runs charged to the bowler. |
| Maiden | A complete over (6 legal balls, single bowler) with 0 runs charged. |
| 3W / 4W / 5W | Innings with exactly 3 / exactly 4 / 5 or more wickets. |
| Best figures | Most wickets in an innings; ties broken by fewest runs conceded. |

## 6. Team

| Metric | Formula |
|---|---|
| Played / Won / Lost / No result | From `core.match`. A tie decided by super over counts as a **win** for the super-over winner (as on the official points table). |
| Win % | Wins × 100 ÷ (played − no results). |
| Batting first | The batting team of innings 1. |
| Toss win → match win % | Matches where the toss winner also won × 100 ÷ matches with a result. |
| Net run rate | (Runs scored ÷ overs faced) − (runs conceded ÷ overs bowled). Overs are real (balls ÷ 6). A side bowled out is charged its full quota of overs (20, or the revised DLS quota). Super overs are excluded. |

## 7. Leaderboard qualification (defaults, overridable via API parameters)

| Leaderboard | Default minimum |
|---|---|
| Batting strike rate | 100 balls faced |
| Batting average | 5 dismissals |
| Bowling economy | 60 balls bowled (10 overs) |
| Bowling average / strike rate | 5 wickets |

## 8. Known data limitations (Kaggle-style source)

- Players who neither batted, bowled nor fielded in a match are missing from that match's appearances, so "matches played" can be understated.
- Impact Player substitutions (2023+) are not identified.
- A single `extras_type` per delivery: a no-ball that also produced byes is recorded only as `noballs`.
