# REST API (V1.4)

Read-only JSON API over the analytics layer (docs/analytics.md). Base path `/api/v1`. No authentication yet.
The OpenAPI document at `/v3/api-docs` is the contract (Swagger UI at `/swagger-ui.html`; both are off in the
`prod` profile). `mvn verify` writes it to `backend/target/openapi.json`, ready for TypeScript client generation.

**The API computes no cricket statistics.** Controllers validate and map; services assemble DTOs; every figure
comes from the V1.3 views, SQL formula functions or `CricketMetrics` (docs/metrics.md).

## Endpoints

| Endpoint | Returns | Paged |
|---|---|---|
| `GET /seasons` | Seasons with match count and champion | single page |
| `GET /seasons/{year}/summary` | Matches, runs, wickets, average score, highest / lowest completed team score, champion, top run scorer, top wicket taker, best strike rate (`minimumBalls`, default 100), best economy (`minimumOvers`, default 10) | – |
| `GET /seasons/{year}/points-table` | League-stage table: matches, wins, losses, ties, no results, points, net run rate | – |
| `GET /teams` | Franchises with every historical name | single page |
| `GET /teams/{id}` | One franchise | – |
| `GET /teams/{id}/stats` | Overall, batting-first, chasing, home, away records; average scores and run rates | – |
| `GET /teams/{id}/seasons` | The same, per season | single page |
| `GET /teams/{id}/top-performers` | Leading run scorers and wicket takers (`limit` 1–25, default 5) | – |
| `GET /venues` | Venues with aliases and match count | single page |
| `GET /venues/{id}` | One venue | – |
| `GET /venues/{id}/stats` | Matches, average first-innings score, highest / lowest, bat-first and chasing win %, average run rate, wickets and boundaries per innings | – |
| `GET /players` | Search by any name the player used (`search`, `page`, `size`) | yes |
| `GET /players/{id}` | Profile: names over time, franchises | – |
| `GET /players/{id}/batting` | Career batting and per season | – |
| `GET /players/{id}/bowling` | Career bowling and per season | – |
| `GET /players/{id}/phases` | Powerplay / middle / death batting and bowling | – |
| `GET /players/{id}/vs-teams` | Batting and bowling per opponent | – |
| `GET /players/{id}/venues` | Batting and bowling per venue | – |
| `GET /players/compare` | 2–5 players side by side (`ids=1,2`, `discipline=batting\|bowling`) | – |
| `GET /matches` | Results, newest first (`season` or `seasonFrom`/`seasonTo`, `team`, `opponent`, `venue`, `dateFrom`, `dateTo`, `stage`) | yes |
| `GET /matches/{id}` | Teams, toss, result, target, officials, innings scores, player of the match | – |
| `GET /matches/{id}/scorecard` | Per innings: batting with dismissals, did not bat, bowling, extras, fall of wickets | – |
| `GET /matches/{id}/progression` | Per over: runs, wickets, extras (Manhattan) and running totals with run rate (worm) | – |
| `GET /head-to-head` | `team1`, `team2`: wins, ties, no results, batting-first / chasing wins, average scores, by season, five latest meetings | – |
| `GET /matchups` | `batterId`, `bowlerId`: balls, runs, dismissals, 4s, 6s, strike rate, average, dot %, boundary % | – |
| `GET /leaderboards/batting` | `metric` = runs, average, strikeRate, fours, sixes, hundreds, fifties; `minimumBalls`; `team`; `limit` | single page |
| `GET /leaderboards/bowling` | `metric` = wickets, economy, average, strikeRate, dotBalls; `minimumOvers`; `team`; `limit` | single page |
| `GET /system/status` | App version, schema version (V0, not enveloped) | – |

Common filters on statistics endpoints: `season`, or `seasonFrom` / `seasonTo` (inclusive; not combined with
`season`), and where listed `opponent`, `venue`, `team`. Super overs are excluded from player and team figures,
included (flagged) in match scorecards and progression.

## Response envelope

```json
{
  "data": { "...": "the resource" },
  "meta": { "generatedAt": "2026-11-13T14:05:09Z", "dataVersion": "t1-a3", "filters": { "season": 2024 } }
}
```

Collections add `"pagination": {"page": 0, "size": 20, "totalElements": 1243, "totalPages": 63}`. Pages are
zero-based; `size` is 1–100 (default 20). Short unpaginated collections (teams, venues, seasons, leaderboards) are
returned as one page holding every element. `meta.dataVersion` names the latest completed transform (`t`) and
analytics refresh (`a`) the figures come from; endpoint context such as `metric` or `minimumBalls` (the effective
value, defaults included) is added to `meta`.

## Errors

RFC 9457 problem details, `application/problem+json`:

| Status | When | Extra members |
|---|---|---|
| 400 | Malformed or out-of-range parameter, missing required parameter, `seasonFrom` > `seasonTo`, `season` with a range, `dateFrom` > `dateTo`, unknown `metric` / `discipline`, `size` > 100, fewer than 2 or more than 5 compared players | `errors[]` (`parameter`, `message`); `allowedValues` for enumerations |
| 404 | Unknown resource in the path (`/players/999999`) or unknown subject (`team1`, `batterId`, compared `ids`); unknown route | – |
| 422 | Well-formed but unprocessable: unknown filter id (`team`, `opponent`, `venue`), `team1` = `team2`, `batterId` = `bowlerId`, repeated compare id | – |
| 500 | Unexpected failure. The body never contains exception messages, SQL, paths or stack traces | `errorId` (also in the server log) |

```json
{
  "type": "about:blank", "title": "Invalid request parameters", "status": 400,
  "detail": "Invalid value 'catches' for parameter 'metric'", "instance": "/api/v1/leaderboards/batting",
  "errors": [{ "parameter": "metric", "message": "Invalid value 'catches' for parameter 'metric'" }],
  "allowedValues": ["runs", "average", "strikeRate", "fours", "sixes", "hundreds", "fifties"]
}
```

## Caching

Data changes only when a transform or analytics refresh completes, so every successful `GET /api/v1/**` response
(except `/system`) carries a weak `ETag` of the data version (`W/"t1-a3"`) and `Cache-Control: public, max-age=60`.
A request with a matching `If-None-Match` is answered `304 Not Modified` by an interceptor **before any query
runs**. Errors carry no ETag. The data version itself is one indexed query per request (memoized per request).

## Cross-cutting

- **CORS**: `/api/**` allows `GET`, `HEAD`, `OPTIONS` from `iplytics.cors.allowed-origins`
  (`IPLYTICS_CORS_ALLOWED_ORIGINS`, default `http://localhost:5173`) and exposes `ETag` and `X-Request-Id`.
- **Request logging**: one line per `/api/**` request on logger `iplytics.http` (method, path, status, duration);
  `X-Request-Id` is accepted or generated, echoed, and put in the log pattern (`[requestId]`).
- **Actuator**: `/actuator/health` (status only, no details), `/actuator/info` (build, Java), `/actuator/metrics`.
  `env`, `configprops`, `beans`, `heapdump`, `loggers` and `mappings` are not exposed.
- **Security baseline**: every parameter is validated; SQL is parameterized and only fixed fragments chosen by
  enums are ever concatenated (sort columns, metric expressions); `LIKE` wildcards in search terms are escaped; DTOs
  expose ids and names only (no audit columns, source revisions or ingestion run ids).

## Query path per endpoint

Every endpoint runs a fixed number of set-based queries, independent of the result size (no N+1). Pagination and
aggregation happen in PostgreSQL. Counts below were measured on the real data set (JdbcTemplate debug log, per
request id) and exclude the one data-version query every request makes; a `304` runs only that query.
Existence checks of filter ids (`team`, `opponent`, `venue`) add one cheap query each.

| Endpoint | Reads | Queries |
|---|---|---|
| seasons list | `core.season`, `core.match`; team refs | 2 |
| season summary | totals, highest / lowest score (`analytics.innings_summary`), champion, 4 leaderboard queries, season and team refs | 9 |
| points table | season check, team refs, one CTE query over `core.match` + `analytics.innings_summary` | 3 |
| teams / venues lists, team / venue detail | `core.*` reference tables, names / aliases in one query | 2 |
| team stats / seasons | `analytics.team_match_result` (grouped) + existence | 2 |
| team top performers | `analytics.batting_innings`, `analytics.bowling_innings` | 3 |
| venue stats | `analytics.innings_summary`, `analytics.team_match_result` | 4 |
| players search | page + count over `core.player` with `analytics.player_match` | 2 |
| player batting / bowling / vs-teams / venues | analytics view aggregates (career + grouped) + `analytics.player_match` | 5 |
| player phases | `analytics.batting_innings`, `analytics.bowling_innings` phase columns | 3 |
| players compare | `player_id in (...)` for refs, careers, matches | 3 |
| matches list | `core.match` page + count, `analytics.innings_summary` for the page's ids, team and venue refs | 5 |
| match detail | `core.match`, `analytics.innings_summary`, refs | 4 |
| scorecard | match, innings, extras, batting (+ dismissing bowler), fielders, bowling, fall of wickets (window sum), did not bat, refs | 10 |
| progression | `core.delivery` grouped by over with window running totals; match, innings, refs | 4 |
| head-to-head | `analytics.team_match_result` overall + by season; latest five via the match list | 8 |
| matchup | player refs + one query on `core.delivery` for the pair (indexed by batter) | 2 |
| leaderboards | `analytics.batting_innings` / `bowling_innings` grouped, `HAVING` minimum, `ORDER BY` metric, `LIMIT` | 1 |

Real-data response times (1,243 matches, warm, local Docker PostgreSQL): 18–65 ms for every endpoint above; a
`304` takes under 10 ms.

## Definitions specific to the API

- **Net run rate** (points table): runs for ÷ overs faced − runs against ÷ overs bowled, overs as balls ÷ 6,
  rounded half-up to 3 places. A side bowled out is charged its full allotted overs. In a D/L result the side
  batting first is credited with target − 1 runs off the chasing side's allotted overs. No-results and super overs
  do not count towards NRR. League stage only (playoffs are not in the table). See docs/metrics.md §6b.
- **Points**: 2 per win (a tie decided by super over is a win for its winner) and 1 per no result.
- **Result text**: names as used at the time (`Delhi Daredevils won by 7 runs`, `... (D/L)`,
  `Match tied (Delhi Capitals won the super over)`, `No result`).
- **Dismissal text**: `c Fielder b Bowler`, `c & b Bowler`, `c sub (Fielder) b Bowler`, `st Keeper b Bowler`,
  `lbw b Bowler`, `b Bowler`, `hit wicket b Bowler`, `run out (A/B)`, `retired hurt`, `not out`. `bowler` is set
  only when the bowler is credited.
- **Fall of wickets** over notation is overs.legal balls bowled so far in the over (`7.3`); retirements are not
  wickets.
- **Matchup dismissals** are wickets credited to the bowler; run outs are not.
- **Comparison**: players are returned in the requested order; a player with no figures in the filter gets zero
  counts and null ratios rather than an error.
