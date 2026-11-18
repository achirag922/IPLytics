# Cricket metric definitions

This file is the **single source of truth** for how IPLytics computes statistics.
Code (SQL views, `CricketMetrics`) must match it, and tests must cite the rule they cover.
Change this file in the same pull request as any change to a calculation.

## 1. Scope rules

| Rule | Decision |
|---|---|
| Super overs | Excluded from all player and team aggregates (`core.delivery.is_super_over`, i.e. innings 3+). Shown only in match detail. |
| No-result / abandoned matches | Deliveries actually bowled **count** toward player stats. The match counts as "no result" for team records. |
| DLS / reduced-overs matches | Included. The result stands as officially recorded. |
| Season | The IPL edition year = calendar year of the match date (`core.season.year`). Cricsheet's label (`2007/08`, `2020/21`) is kept as `source_season` and never parsed. |
| Franchise continuity | Team stats aggregate at franchise level (Delhi Daredevils + Delhi Capitals = one team). Deccan Chargers and Sunrisers Hyderabad are different franchises. See ADR 0003. |
| Rounding | Ratios are computed from raw totals (never an average of averages) and rounded to 2 decimals, HALF_UP. *V1.3 clarification:* the rounding is part of the single formula definition (SQL `analytics.*` functions = Java `CricketMetrics`), so displayed values and leaderboard ranking use the identical rounded number. Rounded ratios are never fed into further calculations. |
| Undefined ratios | Division by zero returns `null` (shown as "–"), never 0 or infinity. |

## 2. Deliveries

| Term | Definition |
|---|---|
| Legal delivery | `wides = 0 and noballs = 0` (stored as `core.delivery.is_legal_ball`). Counts toward the over (6 per over). |
| Ball faced (batter) | Every delivery except a **wide**. No-balls, byes and leg-byes count as balls faced. |
| Runs charged to bowler | `total_runs - byes - legbyes - penalty`. Wides and no-ball runs **are** charged. |
| Four / six | `batter_runs = 4` / `batter_runs = 6` **and** `non_boundary = false` (a four that was run is not a boundary). |
| Over number | Source data is 0-indexed (`over = 0` is the 1st over). Displays are 1-indexed. |

## 3. Phases

| Phase | Overs (1-indexed) | Source `over` (0-indexed) |
|---|---|---|
| Powerplay | 1–6 | 0–5 |
| Middle | 7–15 | 6–14 |
| Death | 16–20 | 15–19 |

*Limitation:* fixed boundaries are used even when a DLS-shortened match had a shorter powerplay.

Implemented as `analytics.phase(over_number, is_super_over)` and `CricketPhase.of`, which are identical. Super-over balls
belong to no regular phase (`super_over`). Overs beyond 19 count as death overs. Shortened innings simply have fewer
balls in the later phases.

## 4. Batting

| Metric | Formula |
|---|---|
| Innings | Distinct innings in which the player appeared as batter **or** non-striker, or was dismissed. |
| Batting position | Order of first appearance at the crease in the innings. On the same ball, the striker comes before the non-striker. |
| Runs | Σ `batter_runs` (extras never count). |
| Balls faced | Count of balls faced (see §2). |
| Dismissals | `core.wicket` rows where the player is `player_out_id` and `kind` is not `retired hurt` / `retired not out`. `retired out` **is** a dismissal. Includes run-outs at the non-striker's end. |
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
| Wickets | `core.wicket.kind` ∈ {bowled, caught, caught and bowled, lbw, stumped, hit wicket}. Run out, retired, obstructing the field: **not** credited. |
| Economy | Runs conceded × 6 ÷ balls bowled. |
| Average | Runs conceded ÷ wickets. `null` if wickets = 0. |
| Strike rate | Balls bowled ÷ wickets. |
| Dot balls | Legal deliveries with 0 runs charged to the bowler. |
| Maiden | A complete over (6 legal balls, single bowler) with 0 runs charged. *Not implemented yet.* |
| Wides / no-balls | Number of wide / no-ball **deliveries**. The extras runs are reported separately as `wide_runs` / `no_ball_runs`. |
| Boundaries conceded | Fours / sixes off the bat that are not `non_boundary`. |
| 3W / 4W / 5W | Innings with exactly 3 / exactly 4 / 5 or more wickets. |
| Best figures | Most wickets in an innings; ties broken by fewest runs conceded. |

## 6. Team

| Metric | Formula |
|---|---|
| Played / Won / Lost / No result | From `core.match`. A tie decided by super over counts as a **win** for the super-over winner (as on the official points table). |
| Win % | Wins × 100 ÷ (played − no results). |
| Batting first | The batting team of innings 1. |
| Toss win → match win % | Matches where the toss winner also won × 100 ÷ matches with a result. |
| Net run rate | (Runs scored ÷ overs faced) − (runs conceded ÷ overs bowled). Overs are real (balls ÷ 6). A side bowled out is charged its full quota of overs (20, or the revised DLS quota). Super overs are excluded. See §6b. |
| Batted first / chased | Batted in innings 1 / innings 2 of the match. |
| Average score / conceded | Runs scored (conceded) in regular innings ÷ number of those innings. |
| Home / away | Home when the venue's city equals the franchise's curated home city; everything else is away. Approximate for second home grounds, relocations and neutral seasons (docs/analytics.md §7). |

## 6a. Venue

| Metric | Formula |
|---|---|
| Decisive matches | Results by runs or wickets (ties and no-results excluded). |
| Batting-first win % / chase success % | Decisive matches won by the side batting first / second × 100 ÷ decisive matches. |
| Average first-innings score | Mean first-innings total over matches with a result and no D/L adjustment. |
| Highest total / lowest completed total | Max regular-innings total / min total of an innings that was all out or used its full scheduled overs. |
| Average run rate | All regular-innings runs × 6 ÷ legal balls at the venue. |

## 6b. Points table (`GET /api/v1/seasons/{year}/points-table`)

| Item | Rule |
|---|---|
| Matches counted | League stage only (`stage = 'League'`); playoffs are not in the table. |
| Points | 2 × wins + 1 × no results. A tie decided by a super over is a win for the super-over winner. |
| Net run rate | Runs for ÷ (balls faced ÷ 6) − runs against ÷ (balls bowled ÷ 6), rounded half-up to 3 places (`CricketMetrics.netRunRate`). Regular innings only (super overs never count). |
| All out | A side bowled out is charged its full allotted overs (scheduled overs, or the revised D/L overs for the chasing side) instead of the balls it faced. |
| D/L results | The side batting first is credited with target − 1 runs off the chasing side's allotted (revised) overs. |
| No results | Count as matches and 1 point, but not towards NRR. |
| Order | Points desc, NRR desc (null last), wins desc, name. |

These rules are the standard IPL ones. Checked against the published tables, NRR matches to 3 places for 2023 and
2024. Known differences come from the source, not the formula:

- **Matches abandoned without a ball bowled are not in Cricsheet**, so their no-result point is missing (2024: KKR
  shows 18 points from 12 matches; the official table has 20 from 14). NRR is unaffected.
- **2008**: four sides differ by up to 0.006 in NRR (e.g. MI 0.575 vs 0.570 published), from rain-reduced matches
  whose official revised overs are not described exactly by the source.


## 7. Leaderboard qualification (defaults, overridable via API parameters)

| Leaderboard | Default minimum |
|---|---|
| Batting strike rate | 100 balls faced |
| Batting average | 5 dismissals |
| Bowling economy | 60 balls bowled (10 overs) |
| Bowling average / strike rate | 5 wickets |

## 8. Source rules and known limitations (Cricsheet, see docs/data-source.md)

- **Matches played** = rows in `core.match_player`: the full playing XI, plus the Impact Player from 2023, so 11–13 per team. This includes players who never batted, bowled or fielded.
- **Catches and run-outs** come from `core.wicket_fielder`. Substitute fielders are flagged with `is_substitute`.
- **Extras are exact per ball,** e.g. a no-ball plus byes.
- **Matches abandoned without a ball bowled are absent from the source.** No-result matches with play are included.
- **Impact Player substitutions are not modelled in core yet.** The source `replacements` are kept in `staging.deliveries.source_unmapped`.

## 9. Insights (V1.6, `/api/v1/insights/*`)

| Metric | Definition |
|---|---|
| Toss winner win % | Matches the toss winner won × 100 ÷ matches with a recorded toss and a result. A tie decided by a super over counts as a win for its winner. |
| Chose to bat | Matches in which the toss winner batted first. |
| Team toss record | Tosses won ÷ matches; win % after winning the toss and after losing it (matches with a result). |
| Batting first / chasing win % | Wins of the side batting first (second) × 100 ÷ decisive matches (won by runs or wickets; ties and no-results excluded). |
| Chase success by target | Target = first-innings total + 1. Bands: under 150, 150–169, 170–189, 190–209, 210 and over. Success % = successful chases × 100 ÷ matches in the band. D/L-adjusted matches are excluded (their target is revised). |
| Phase figures | Over the regular innings in scope: run rate = 6 × runs ÷ legal balls; wickets and runs per innings divide by innings with a ball in the phase; boundary % = (fours + sixes) × 100 ÷ legal balls; dot % = legal balls with no run × 100 ÷ legal balls. All three phases are always returned (zero rows where nothing was bowled). |
| Team phases | With a team: batting = the team's innings; bowling = the innings it bowled (runs it conceded, wickets it took). |
| Season comparison | Per season: average score (runs ÷ regular innings), run rate, fours / sixes / wickets per match, average first-innings score (decisive, no D/L), batting-first win %, toss-winner win %, phase run rates, highest score. `change` = last season − first season, computed by the backend. |

## 10. Player consistency (V1.6, `/api/v1/players/{id}/consistency`)

| Metric | Definition |
|---|---|
| Median score | Middle innings score (`percentile_cont(0.5)`), regular innings in scope. |
| Spread | Sample standard deviation of innings scores; undefined (null) with fewer than two innings. |
| Coefficient of variation | 100 × spread ÷ mean score. Lower is steadier; null when undefined or the mean is 0. |
| 30+ / 50+ / under 10 | Innings in the band × 100 ÷ innings batted (not-out innings count at their score). |
| Score bands | Innings scoring 0, 1–9, 10–29, 30–49, 50–99, 100+. |
| Wicket-taking / 2+ wicket innings | Innings with ≥ 1 / ≥ 2 wickets × 100 ÷ innings bowled. |
| Economy spread | Standard deviation of economy over innings of at least one over (6 legal balls); with its coefficient of variation. |
| Economical innings | Innings of at least one over with economy under 8 × 100 ÷ innings of at least one over. |
| Recent form | The last 10 innings, newest first (date, opponent, figures). |

