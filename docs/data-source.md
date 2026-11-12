# IPL data source

> **Status:** the real Cricsheet download (IPL 2008–2026) has been converted and loaded into staging with
> 0 rejected files and 0 rejected rows. Section 7 records the actual counts from that run.

## 1. Canonical source: Cricsheet

- **Source:** [Cricsheet](https://cricsheet.org/), ball-by-ball data for every IPL match, one JSON file per match.
- **Download:** the *Indian Premier League* JSON zip from <https://cricsheet.org/downloads/> (currently
  `ipl_json.zip`). Use this one complete download only. Do not mix in other IPL datasets.
- **Target coverage:** IPL 2008 to 2026, i.e. whatever the downloaded zip contains. The converter reports
  the actual matches per season.
- **Licence:** Cricsheet data is made available under the
  [Open Data Commons Attribution License (ODC-By) v1.0](https://opendatacommons.org/licenses/by/1-0/).
  Any public use of IPLytics must credit it, e.g. *"Ball-by-ball data from Cricsheet (cricsheet.org),
  licensed under ODC-By 1.0."* The UI footer will carry this attribution once pages show data.
- **Coverage caveat:** Cricsheet only publishes matches where at least one ball was bowled. Matches
  abandoned without a ball are absent, so counts can differ slightly from the official fixture list.

## 2. Pipeline

```
data/cricsheet/ipl_json.zip      (official download, git-ignored, never edited)
        │  java -jar iplytics.jar --spring.profiles.active=convert      (no database needed)
        ▼
data/raw/matches.csv, deliveries.csv, match_players.csv, cricsheet-conversion-report.json
        │  java -jar iplytics.jar --spring.profiles.active=ingest
        ▼
staging.matches / staging.deliveries / staging.match_players   (+ core.ingestion_run, staging.rejected_row)
```

The converter is deterministic: the same zip always produces byte-identical CSVs, so re-running ingestion
skips them by hash. Column names and order come from `Dataset.java`, the same definition staging validates
against.

## 3. Generated `matches.csv` (one row per match)

| Column | Cricsheet source | Notes |
|---|---|---|
| `id` | file name, e.g. `335982.json` | **stable source identifier** |
| `season` | `info.season` | **verbatim source label**, e.g. `2007/08`, `2020/21`, `2026` |
| `season_year` | year of `info.dates[0]` | **derived**. IPL season shown to users: `2007/08` → 2008, `2009/10` → 2010, `2020/21` → 2020. Never taken from the label. |
| `city` | `info.city` | may be empty |
| `date` / `end_date` | first / last of `info.dates` | `end_date` differs for matches that spanned more than one date (reserve days) |
| `event_name` | `info.event.name` | must be `Indian Premier League`, otherwise the file is rejected |
| `match_number` | `info.event.match_number` | empty for playoffs |
| `match_type` | `info.event.stage`, otherwise `League` | e.g. `Final`, `Qualifier 1`, `Eliminator` |
| `player_of_match` / `player_of_match_ids` | `info.player_of_match` + registry ids | lists joined with `\|` |
| `venue`, `team1`, `team2` | `info.venue`, `info.teams` | verbatim, so renamed teams keep their name at the time |
| `toss_winner`, `toss_decision` | `info.toss` | |
| `winner` | `outcome.winner`, or `outcome.eliminator` for super-over ties | the official winner |
| `super_over_winner` | `outcome.eliminator` | |
| `result`, `result_margin` | `runs` / `wickets` from `outcome.by`, otherwise `outcome.result` | `tie`, `no result` |
| `target_runs`, `target_overs` | `target` of the first non-super-over innings that has one | the (revised) chase target |
| `super_over` | `Y` if any innings has `super_over: true` | |
| `method` | `outcome.method` | e.g. `D/L` |
| `scheduled_overs` | `info.overs` | |
| `umpire1`, `umpire2`, `tv_umpires`, `match_referees`, `reserve_umpires` | `info.officials` | lists joined with `\|` |
| `source_data_version`, `source_created`, `source_revision` | `meta` | **source revision** of the Cricsheet file |
| `source_unmapped` | everything not listed above | JSON, see section 6 |

## 4. Generated `deliveries.csv` (one row per ball bowled)

| Column | Cricsheet source | Notes |
|---|---|---|
| `match_id` | file name | |
| `inning` | position in `innings` (1-based) | super overs are innings 3+ |
| `is_super_over` | `innings[].super_over` | `0`/`1` |
| `batting_team` / `bowling_team` | `innings[].team` / the other team | |
| `over` | `overs[].over` | **0-indexed** as in the source |
| `ball` | position in `overs[].deliveries` (1-based) | counts wides and no-balls |
| `actual_delivery` | `actual_delivery` | Cricsheet's scorer label `over.ball`, counting **legal balls only** (a wide at `0.5` is followed by another `0.5`). Verbatim, empty if absent. |
| `batter`, `bowler`, `non_striker` (+ `_id`) | delivery + `info.registry.people` | **Cricsheet player ids** |
| `batsman_runs`, `extra_runs`, `total_runs` | `runs.batter`, `runs.extras`, `runs.total` | |
| `non_boundary` | `runs.non_boundary` | `1` = a four (or six) that was run, not a boundary |
| `wides`, `noballs`, `byes`, `legbyes`, `penalty` | `extras.*` | **exact breakdown**, `0` when absent |
| `extras_type` | names of the extras present, `\|`-joined | convenience only, e.g. `noballs\|byes` |
| `is_wicket` | `wickets` non-empty | `0`/`1` (includes retirements, as recorded by Cricsheet) |
| `player_dismissed` (+ `_id`), `dismissal_kind`, `fielder`, `fielder_ids` | `wickets[0]` | fielders `\|`-joined, ids aligned |
| `other_player_dismissed` (+ `_id`), `other_dismissal_kind`, `other_fielder`, `other_fielder_ids` | `wickets[1]` | rare second dismissal on one ball |
| `wickets_json` | `wickets` | **exact source array**, incl. substitute-fielder flags and any further wickets |
| `source_unmapped` | everything not listed above | JSON, e.g. `review`, `replacements` (impact players) |

## 5. Generated `match_players.csv` (one row per listed player per match)

| Column | Cricsheet source | Notes |
|---|---|---|
| `match_id` | file name | |
| `team` | key of `info.players` | |
| `player`, `player_id` | `info.players[team][]` + registry | includes players who did not bat, bowl or field |
| `list_position` | position in the team list (1-based) | preserves source order |

## 6. Fields without their own column (kept, not dropped)

Every source field that is not mapped above is copied verbatim into `source_unmapped` (JSON) on the
match or delivery row. Nothing from a Cricsheet file is silently discarded. Fields known to land there:

| Level | Fields |
|---|---|
| Match `info` | `balls_per_over`, `gender`, `match_type` (format, `T20`), `team_type`, `missing`, `supersubs`, `event` extras (`group`, `sub_name`), `outcome.by.innings`, `bowl_out`, more than two on-field umpires |
| Registry | ids of people not in the playing lists (umpires, referees, substitutes) and **`registry.venues`** (Cricsheet venue ids, useful for venue de-duplication in V1.2) |
| Innings (under `source_unmapped.innings`, keyed by `inning`) | `powerplays`, `penalty_runs`, `absent_hurt`, `miscounted_overs`, `declared`, `forfeited`, super-over `target` |
| Delivery | `review` (DRS), `replacements` (incl. impact players), any other key. (`actual_delivery` was found on every ball in the first real run and was promoted to its own column in migration V5.) |

Any field Cricsheet adds in future also lands in `source_unmapped` automatically.

## 7. Actual dataset (first real conversion, 2026-11-12)

| Item | Value |
|---|---|
| Download | `ipl_json.zip` from <https://cricsheet.org/downloads/>, saved 2026-11-12 |
| Zip SHA-256 | `46ff2f5e41147510a55502e52ea8761b216a319974cad907d6ff2d8c2d4be495` |
| Zip contents | 1,245 entries: 1,243 match JSON files + `README.txt` + `LICENSE.txt` (ODC-By) |
| Generated | **1,243** matches, **295,732** deliveries, **27,909** match-player rows |
| Converter problems | 0 rejected files, 0 duplicate match ids, 0 missing required fields, 0 warnings |
| Staging load | 0 rejected rows. CSV hashes in `core.ingestion_run`; re-converting gives byte-identical files and re-ingesting skips them. After `actual_delivery` became a column (V5), deliveries were re-converted and loaded as run #4. Every ball has a well-formed label, and only 1,573 balls (DRS reviews and replacements) still carry `source_unmapped`. |
| IPL seasons covered | **19 seasons, 2008–2026** (first match 2008-04-18, last 2026-05-31) |

Matches per season (`season_year` derived from the match date; `season` is Cricsheet's label):

| Year | Label | Matches | | Year | Label | Matches |
|---|---|---|---|---|---|---|
| 2008 | 2007/08 | 58 | | 2018 | 2018 | 60 |
| 2009 | 2009 | 57 | | 2019 | 2019 | 60 |
| 2010 | 2009/10 | 60 | | 2020 | 2020/21 | 60 |
| 2011 | 2011 | 73 | | 2021 | 2021 | 60 |
| 2012 | 2012 | 74 | | 2022 | 2022 | 74 |
| 2013 | 2013 | 76 | | 2023 | 2023 | 74 |
| 2014 | 2014 | 60 | | 2024 | 2024 | 71 |
| 2015 | 2015 | 59 | | 2025 | 2025 | 74 |
| 2016 | 2016 | 60 | | 2026 | 2026 | 74 |
| 2017 | 2017 | 59 | | | | |

Verified in staging:
- **No duplicates or orphans:** no duplicate match ids or delivery keys, and no orphan deliveries or match players.
- **Arithmetic holds:** `total_runs = batsman_runs + extra_runs` and `extra_runs = wides + noballs + byes + legbyes + penalty` on every ball.
- **Every player has a Cricsheet id.**
- **Matches:** 16 with a super over (175 super-over balls), 23 decided by D/L, 9 no results, 74 playoff matches, and 2 that spanned two dates.
- **Deliveries:** 27 no-balls that also produced byes or leg-byes, 532 wickets with several fielders, 990 DRS reviews, and 585 replacement records.
- **Playing lists:** 11 per team up to 2022. From 2023 (Impact Player era) usually 12, sometimes 11 or 13.

## 8. Validation and known limitations

- **Converter checks:**
  - Files that aren't IPL, have invalid JSON, have a non-numeric file name, or lack required match fields (`info.season`, `info.dates`, `info.teams`, `info.venue`, `innings`) are **rejected** and listed in the report.
  - Duplicate match ids are reported; the first file is kept.
  - Missing required delivery fields are reported, and the row is still emitted so staging records it in `rejected_row`.
- **The converter exits with code 1** when the report lists any problem. Review `cricsheet-conversion-report.json` before ingesting.
- **Staging validation** (unchanged from V1.1): required values, numbers, dates, flags and JSON columns are checked per row. Invalid rows go to `staging.rejected_row`.
- **Not resolved in staging (V1.2):**
  - franchise renames (Delhi Daredevils → Delhi Capitals, Kings XI Punjab → Punjab Kings, Royal Challengers Bangalore → Royal Challengers Bengaluru)
  - venue name variants
  - checking that every `match_id` in deliveries and match_players exists in matches

## 9. Older Kaggle-style files

The original two-file Kaggle layout (`matches.csv` + `deliveries.csv` with `NA` for missing values) still
validates, because every Cricsheet-only column is optional. It is **not** the project's data source.
`backend/src/test/resources/ingestion/sample/` (Kaggle-style) and `backend/src/test/resources/cricsheet/`
(Cricsheet-style) contain **hand-written illustrative fixtures** for tests. They are not real data.
