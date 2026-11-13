-- V1.3 analytics layer on top of core (see docs/analytics.md and docs/adr/0011-analytics-layer.md).
-- Materialized views hold expensive per-innings aggregations; plain views hold cheap joins.
-- Every cricket formula lives in exactly one SQL function below and is mirrored by
-- com.iplytics.common.metrics.CricketMetrics / CricketPhase (consistency-tested).

-- Curated home city per franchise (reference data), used for home/away splits.
ALTER TABLE core.team ADD COLUMN home_city TEXT;

-- ---- formulas and rules (docs/metrics.md) ------------------------------------------------------------------

-- Phase of a delivery; over_number is 0-indexed. Super-over balls never belong to a regular phase.
CREATE FUNCTION analytics.phase(over_number integer, is_super_over boolean) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE AS
$$ SELECT CASE WHEN is_super_over THEN 'super_over'
               WHEN over_number < 6 THEN 'powerplay'
               WHEN over_number < 15 THEN 'middle'
               ELSE 'death' END $$;

-- Runs charged to the bowler: byes, leg-byes and penalty runs are not.
CREATE FUNCTION analytics.bowler_runs(total_runs integer, byes integer, legbyes integer, penalty integer)
    RETURNS integer LANGUAGE sql IMMUTABLE PARALLEL SAFE AS
$$ SELECT total_runs - byes - legbyes - penalty $$;

-- A wicket credited to the bowler (run outs, retirements, obstructing the field are not).
CREATE FUNCTION analytics.is_bowler_wicket(kind text) RETURNS boolean LANGUAGE sql IMMUTABLE PARALLEL SAFE AS
$$ SELECT kind IN ('bowled', 'caught', 'caught and bowled', 'lbw', 'stumped', 'hit wicket') $$;

-- A dismissal of the batter (retired hurt / retired not out are not; retired out is).
CREATE FUNCTION analytics.is_dismissal(kind text) RETURNS boolean LANGUAGE sql IMMUTABLE PARALLEL SAFE AS
$$ SELECT kind NOT IN ('retired hurt', 'retired not out') $$;

-- Ratios: computed from raw totals, NULL when the denominator is 0, rounded to 2 decimals half-up.
CREATE FUNCTION analytics.batting_average(runs bigint, dismissals bigint) RETURNS numeric
    LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$ SELECT round(runs::numeric / nullif(dismissals, 0), 2) $$;

CREATE FUNCTION analytics.strike_rate(runs bigint, balls bigint) RETURNS numeric
    LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$ SELECT round(100.0 * runs / nullif(balls, 0), 2) $$;

CREATE FUNCTION analytics.economy(runs_conceded bigint, legal_balls bigint) RETURNS numeric
    LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$ SELECT round(6.0 * runs_conceded / nullif(legal_balls, 0), 2) $$;

CREATE FUNCTION analytics.run_rate(runs bigint, legal_balls bigint) RETURNS numeric
    LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$ SELECT round(6.0 * runs / nullif(legal_balls, 0), 2) $$;

CREATE FUNCTION analytics.bowling_average(runs_conceded bigint, wickets bigint) RETURNS numeric
    LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$ SELECT round(runs_conceded::numeric / nullif(wickets, 0), 2) $$;

CREATE FUNCTION analytics.bowling_strike_rate(legal_balls bigint, wickets bigint) RETURNS numeric
    LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$ SELECT round(legal_balls::numeric / nullif(wickets, 0), 2) $$;

CREATE FUNCTION analytics.percentage(part bigint, whole bigint) RETURNS numeric
    LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$ SELECT round(100.0 * part / nullif(whole, 0), 2) $$;

CREATE FUNCTION analytics.balls_per_boundary(balls bigint, boundaries bigint) RETURNS numeric
    LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$ SELECT round(balls::numeric / nullif(boundaries, 0), 2) $$;

CREATE FUNCTION analytics.win_percentage(wins bigint, played bigint, no_results bigint) RETURNS numeric
    LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$ SELECT round(100.0 * wins / nullif(played - no_results, 0), 2) $$;

-- Overs notation for display only (22 legal balls -> '3.4'); never use it in arithmetic.
CREATE FUNCTION analytics.overs(legal_balls bigint) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$ SELECT (legal_balls / 6) || '.' || (legal_balls % 6) $$;

-- ---- shared context (plain view: a cheap join) -----------------------------------------------------------

CREATE VIEW analytics.innings_context AS
SELECT i.id AS innings_id, i.match_id, i.innings_number, i.is_super_over, i.batting_team_id, i.bowling_team_id,
       m.season_id, s.year AS season_year, m.venue_id, m.match_date
  FROM core.innings i
  JOIN core.match m ON m.id = i.match_id
  JOIN core.season s ON s.id = m.season_id;

-- ---- batting: one player x one innings (materialized) ----------------------------------------------------

CREATE MATERIALIZED VIEW analytics.batting_innings AS
WITH appearance AS (
    -- order of first appearance at the crease; on the same ball the striker comes before the non-striker
    SELECT innings_id, player_id, min(seq) AS first_seq
      FROM (SELECT d.innings_id, d.batter_id AS player_id, (d.over_number * 1000 + d.ball_in_over) * 2 AS seq
              FROM core.delivery d
            UNION ALL
            SELECT d.innings_id, d.non_striker_id, (d.over_number * 1000 + d.ball_in_over) * 2 + 1
              FROM core.delivery d
            UNION ALL
            SELECT d.innings_id, w.player_out_id, (d.over_number * 1000 + d.ball_in_over) * 2 + 1
              FROM core.wicket w JOIN core.delivery d ON d.id = w.delivery_id) a
     GROUP BY innings_id, player_id),
faced AS (
    SELECT d.innings_id, d.batter_id AS player_id,
           sum(d.batter_runs) AS runs,
           count(*) FILTER (WHERE d.wides = 0) AS balls,
           count(*) FILTER (WHERE d.batter_runs = 4 AND NOT d.non_boundary) AS fours,
           count(*) FILTER (WHERE d.batter_runs = 6 AND NOT d.non_boundary) AS sixes,
           count(*) FILTER (WHERE d.wides = 0 AND d.batter_runs = 0) AS dot_balls,
           sum(d.batter_runs) FILTER (WHERE analytics.phase(d.over_number, d.is_super_over) = 'powerplay') AS powerplay_runs,
           sum(d.batter_runs) FILTER (WHERE analytics.phase(d.over_number, d.is_super_over) = 'middle') AS middle_runs,
           sum(d.batter_runs) FILTER (WHERE analytics.phase(d.over_number, d.is_super_over) = 'death') AS death_runs,
           count(*) FILTER (WHERE d.wides = 0 AND analytics.phase(d.over_number, d.is_super_over) = 'powerplay') AS powerplay_balls,
           count(*) FILTER (WHERE d.wides = 0 AND analytics.phase(d.over_number, d.is_super_over) = 'middle') AS middle_balls,
           count(*) FILTER (WHERE d.wides = 0 AND analytics.phase(d.over_number, d.is_super_over) = 'death') AS death_balls
      FROM core.delivery d
     GROUP BY d.innings_id, d.batter_id),
dismissal AS (
    -- a counted dismissal wins over a retirement (a player can retire hurt, return and be out)
    SELECT DISTINCT ON (d.innings_id, w.player_out_id)
           d.innings_id, w.player_out_id AS player_id, w.kind, analytics.is_dismissal(w.kind) AS is_out
      FROM core.wicket w JOIN core.delivery d ON d.id = w.delivery_id
     ORDER BY d.innings_id, w.player_out_id, analytics.is_dismissal(w.kind) DESC, d.over_number DESC, d.ball_in_over DESC)
SELECT c.match_id, a.innings_id, c.season_id, c.season_year, a.player_id,
       c.batting_team_id AS team_id, c.bowling_team_id AS opponent_team_id, c.venue_id, c.match_date,
       c.innings_number, c.is_super_over,
       (row_number() OVER (PARTITION BY a.innings_id ORDER BY a.first_seq, a.player_id))::smallint AS batting_position,
       coalesce(f.runs, 0)::int AS runs,
       coalesce(f.balls, 0)::int AS balls,
       coalesce(f.fours, 0)::int AS fours,
       coalesce(f.sixes, 0)::int AS sixes,
       coalesce(f.dot_balls, 0)::int AS dot_balls,
       analytics.strike_rate(coalesce(f.runs, 0), coalesce(f.balls, 0)) AS strike_rate,
       coalesce(dm.is_out, false) AS is_out,
       dm.kind AS dismissal_kind,
       coalesce(f.powerplay_runs, 0)::int AS powerplay_runs,
       coalesce(f.middle_runs, 0)::int AS middle_runs,
       coalesce(f.death_runs, 0)::int AS death_runs,
       coalesce(f.powerplay_balls, 0)::int AS powerplay_balls,
       coalesce(f.middle_balls, 0)::int AS middle_balls,
       coalesce(f.death_balls, 0)::int AS death_balls
  FROM appearance a
  JOIN analytics.innings_context c ON c.innings_id = a.innings_id
  LEFT JOIN faced f ON f.innings_id = a.innings_id AND f.player_id = a.player_id
  LEFT JOIN dismissal dm ON dm.innings_id = a.innings_id AND dm.player_id = a.player_id;

CREATE UNIQUE INDEX ux_batting_innings ON analytics.batting_innings (innings_id, player_id);
CREATE INDEX ix_batting_innings_player ON analytics.batting_innings (player_id);

-- ---- bowling: one player x one innings (materialized) ----------------------------------------------------

CREATE MATERIALIZED VIEW analytics.bowling_innings AS
WITH bowled AS (
    SELECT d.innings_id, d.bowler_id AS player_id,
           count(*) FILTER (WHERE d.is_legal_ball) AS legal_balls,
           sum(analytics.bowler_runs(d.total_runs, d.byes, d.legbyes, d.penalty)) AS runs_conceded,
           count(*) FILTER (WHERE d.is_legal_ball
                              AND analytics.bowler_runs(d.total_runs, d.byes, d.legbyes, d.penalty) = 0) AS dot_balls,
           count(*) FILTER (WHERE d.batter_runs = 4 AND NOT d.non_boundary) AS fours_conceded,
           count(*) FILTER (WHERE d.batter_runs = 6 AND NOT d.non_boundary) AS sixes_conceded,
           count(*) FILTER (WHERE d.wides > 0) AS wides,
           sum(d.wides) AS wide_runs,
           count(*) FILTER (WHERE d.noballs > 0) AS no_balls,
           sum(d.noballs) AS no_ball_runs,
           count(*) FILTER (WHERE d.is_legal_ball AND analytics.phase(d.over_number, d.is_super_over) = 'powerplay') AS powerplay_balls,
           count(*) FILTER (WHERE d.is_legal_ball AND analytics.phase(d.over_number, d.is_super_over) = 'middle') AS middle_balls,
           count(*) FILTER (WHERE d.is_legal_ball AND analytics.phase(d.over_number, d.is_super_over) = 'death') AS death_balls,
           sum(analytics.bowler_runs(d.total_runs, d.byes, d.legbyes, d.penalty))
               FILTER (WHERE analytics.phase(d.over_number, d.is_super_over) = 'powerplay') AS powerplay_runs,
           sum(analytics.bowler_runs(d.total_runs, d.byes, d.legbyes, d.penalty))
               FILTER (WHERE analytics.phase(d.over_number, d.is_super_over) = 'middle') AS middle_runs,
           sum(analytics.bowler_runs(d.total_runs, d.byes, d.legbyes, d.penalty))
               FILTER (WHERE analytics.phase(d.over_number, d.is_super_over) = 'death') AS death_runs
      FROM core.delivery d
     GROUP BY d.innings_id, d.bowler_id),
credited AS (
    SELECT d.innings_id, d.bowler_id AS player_id, count(*) AS wickets
      FROM core.wicket w JOIN core.delivery d ON d.id = w.delivery_id
     WHERE analytics.is_bowler_wicket(w.kind)
     GROUP BY d.innings_id, d.bowler_id)
SELECT c.match_id, b.innings_id, c.season_id, c.season_year, b.player_id,
       c.bowling_team_id AS team_id, c.batting_team_id AS opponent_team_id, c.venue_id, c.match_date,
       c.innings_number, c.is_super_over,
       b.legal_balls::int AS legal_balls,
       b.runs_conceded::int AS runs_conceded,
       coalesce(w.wickets, 0)::int AS wickets,
       b.dot_balls::int AS dot_balls,
       b.fours_conceded::int AS fours_conceded,
       b.sixes_conceded::int AS sixes_conceded,
       b.wides::int AS wides,
       b.wide_runs::int AS wide_runs,
       b.no_balls::int AS no_balls,
       b.no_ball_runs::int AS no_ball_runs,
       analytics.economy(b.runs_conceded, b.legal_balls) AS economy,
       b.powerplay_balls::int AS powerplay_balls,
       b.middle_balls::int AS middle_balls,
       b.death_balls::int AS death_balls,
       coalesce(b.powerplay_runs, 0)::int AS powerplay_runs,
       coalesce(b.middle_runs, 0)::int AS middle_runs,
       coalesce(b.death_runs, 0)::int AS death_runs
  FROM bowled b
  JOIN analytics.innings_context c ON c.innings_id = b.innings_id
  LEFT JOIN credited w ON w.innings_id = b.innings_id AND w.player_id = b.player_id;

CREATE UNIQUE INDEX ux_bowling_innings ON analytics.bowling_innings (innings_id, player_id);
CREATE INDEX ix_bowling_innings_player ON analytics.bowling_innings (player_id);

-- ---- innings: one innings (materialized) ----------------------------------------------------------------

CREATE MATERIALIZED VIEW analytics.innings_summary AS
WITH balls AS (
    SELECT d.innings_id,
           sum(d.total_runs) AS total_runs,
           sum(d.extra_runs) AS extras,
           count(*) FILTER (WHERE d.is_legal_ball) AS legal_balls,
           count(*) FILTER (WHERE d.batter_runs = 4 AND NOT d.non_boundary) AS fours,
           count(*) FILTER (WHERE d.batter_runs = 6 AND NOT d.non_boundary) AS sixes,
           count(*) FILTER (WHERE d.is_legal_ball AND d.total_runs = 0) AS dot_balls,
           sum(d.total_runs) FILTER (WHERE analytics.phase(d.over_number, d.is_super_over) = 'powerplay') AS powerplay_runs,
           sum(d.total_runs) FILTER (WHERE analytics.phase(d.over_number, d.is_super_over) = 'middle') AS middle_runs,
           sum(d.total_runs) FILTER (WHERE analytics.phase(d.over_number, d.is_super_over) = 'death') AS death_runs
      FROM core.delivery d
     GROUP BY d.innings_id),
falls AS (
    SELECT d.innings_id,
           count(*) AS wickets,
           count(*) FILTER (WHERE analytics.phase(d.over_number, d.is_super_over) = 'powerplay') AS powerplay_wickets,
           count(*) FILTER (WHERE analytics.phase(d.over_number, d.is_super_over) = 'middle') AS middle_wickets,
           count(*) FILTER (WHERE analytics.phase(d.over_number, d.is_super_over) = 'death') AS death_wickets
      FROM core.wicket w JOIN core.delivery d ON d.id = w.delivery_id
     WHERE analytics.is_dismissal(w.kind)
     GROUP BY d.innings_id)
SELECT c.match_id, c.innings_id, c.season_id, c.season_year, c.batting_team_id, c.bowling_team_id, c.venue_id,
       c.match_date, c.innings_number, c.is_super_over,
       b.total_runs::int AS total_runs,
       b.extras::int AS extras,
       coalesce(f.wickets, 0)::int AS wickets,
       b.legal_balls::int AS legal_balls,
       analytics.overs(b.legal_balls) AS overs,
       analytics.run_rate(b.total_runs, b.legal_balls) AS run_rate,
       b.fours::int AS fours,
       b.sixes::int AS sixes,
       b.dot_balls::int AS dot_balls,
       coalesce(b.powerplay_runs, 0)::int AS powerplay_runs,
       coalesce(b.middle_runs, 0)::int AS middle_runs,
       coalesce(b.death_runs, 0)::int AS death_runs,
       coalesce(f.powerplay_wickets, 0)::int AS powerplay_wickets,
       coalesce(f.middle_wickets, 0)::int AS middle_wickets,
       coalesce(f.death_wickets, 0)::int AS death_wickets
  FROM analytics.innings_context c
  JOIN balls b ON b.innings_id = c.innings_id
  LEFT JOIN falls f ON f.innings_id = c.innings_id;

CREATE UNIQUE INDEX ux_innings_summary ON analytics.innings_summary (innings_id);
CREATE INDEX ix_innings_summary_match ON analytics.innings_summary (match_id);

-- ---- team: one team x one match (plain view over innings_summary + core) ---------------------------------

CREATE VIEW analytics.team_match_result AS
WITH sides AS (
    SELECT m.id AS match_id, m.team1_id AS team_id, m.team2_id AS opponent_team_id FROM core.match m
    UNION ALL
    SELECT m.id, m.team2_id, m.team1_id FROM core.match m)
SELECT x.match_id, m.season_id, s.year AS season_year, x.team_id, x.opponent_team_id, m.venue_id, m.match_date,
       m.result, m.method,
       coalesce(m.winner_team_id = x.team_id, false) AS won,
       coalesce(m.winner_team_id = x.opponent_team_id, false) AS lost,
       m.result = 'no result' AS no_result,
       m.result = 'tie' AS tied,
       coalesce(i1.batting_team_id = x.team_id, false) AS batted_first,
       coalesce(i2.batting_team_id = x.team_id, false) AS chased,
       coalesce(m.toss_winner_team_id = x.team_id, false) AS toss_won,
       coalesce(v.city = t.home_city, false) AS is_home,
       mine.total_runs AS runs_for,
       theirs.total_runs AS runs_against,
       mine.legal_balls AS balls_faced,
       theirs.legal_balls AS balls_bowled,
       mine.wickets AS wickets_lost,
       theirs.wickets AS wickets_taken,
       analytics.run_rate(mine.total_runs, mine.legal_balls) AS run_rate,
       analytics.run_rate(theirs.total_runs, theirs.legal_balls) AS opponent_run_rate
  FROM sides x
  JOIN core.match m ON m.id = x.match_id
  JOIN core.season s ON s.id = m.season_id
  JOIN core.team t ON t.id = x.team_id
  JOIN core.venue v ON v.id = m.venue_id
  LEFT JOIN analytics.innings_summary i1 ON i1.match_id = x.match_id AND i1.innings_number = 1
  LEFT JOIN analytics.innings_summary i2 ON i2.match_id = x.match_id AND i2.innings_number = 2
  LEFT JOIN analytics.innings_summary mine
         ON mine.match_id = x.match_id AND mine.batting_team_id = x.team_id AND NOT mine.is_super_over
  LEFT JOIN analytics.innings_summary theirs
         ON theirs.match_id = x.match_id AND theirs.batting_team_id = x.opponent_team_id AND NOT theirs.is_super_over;

-- ---- refresh audit -----------------------------------------------------------------------------------------

CREATE TABLE analytics.refresh_run (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    status           TEXT        NOT NULL CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED')),
    transform_run_id BIGINT, -- latest COMPLETED core.transform_run the analytics were built from
    started_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at     TIMESTAMPTZ,
    summary          JSONB,
    error_summary    TEXT,
    CHECK ((status = 'RUNNING') = (completed_at IS NULL))
);
