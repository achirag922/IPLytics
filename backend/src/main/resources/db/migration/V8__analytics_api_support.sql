-- V1.4 support for the REST API (docs/api.md). Extends the V1.3 analytics layer; formulas are unchanged.
--  * batting_innings: + dismissal_wicket_id (the wicket row behind dismissal_kind, for scorecard descriptions)
--  * bowling_innings: + wickets per phase
--  * player_match: player x match participation with match context (matches played, with filters)
--  * overs_to_balls: '9.2' overs notation -> 56 balls (net run rate)
--  * match_id indexes on the per-innings views (scorecard lookups)

CREATE FUNCTION analytics.overs_to_balls(overs numeric) RETURNS integer LANGUAGE sql IMMUTABLE PARALLEL SAFE AS
$$ SELECT (floor(overs) * 6 + round((overs - floor(overs)) * 10))::integer $$;

CREATE VIEW analytics.player_match AS
SELECT mp.player_id, mp.match_id, mp.team_id,
       CASE WHEN mp.team_id = m.team1_id THEN m.team2_id ELSE m.team1_id END AS opponent_team_id,
       m.season_id, s.year AS season_year, m.venue_id, m.match_date
  FROM core.match_player mp
  JOIN core.match m ON m.id = mp.match_id
  JOIN core.season s ON s.id = m.season_id;

DROP MATERIALIZED VIEW analytics.batting_innings;

CREATE MATERIALIZED VIEW analytics.batting_innings AS
WITH appearance AS (
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
    SELECT DISTINCT ON (d.innings_id, w.player_out_id)
           d.innings_id, w.player_out_id AS player_id, w.id AS wicket_id, w.kind,
           analytics.is_dismissal(w.kind) AS is_out
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
       dm.wicket_id AS dismissal_wicket_id,
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
CREATE INDEX ix_batting_innings_match ON analytics.batting_innings (match_id);

DROP MATERIALIZED VIEW analytics.bowling_innings;

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
    SELECT d.innings_id, d.bowler_id AS player_id, count(*) AS wickets,
           count(*) FILTER (WHERE analytics.phase(d.over_number, d.is_super_over) = 'powerplay') AS powerplay_wickets,
           count(*) FILTER (WHERE analytics.phase(d.over_number, d.is_super_over) = 'middle') AS middle_wickets,
           count(*) FILTER (WHERE analytics.phase(d.over_number, d.is_super_over) = 'death') AS death_wickets
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
       coalesce(b.death_runs, 0)::int AS death_runs,
       coalesce(w.powerplay_wickets, 0)::int AS powerplay_wickets,
       coalesce(w.middle_wickets, 0)::int AS middle_wickets,
       coalesce(w.death_wickets, 0)::int AS death_wickets
  FROM bowled b
  JOIN analytics.innings_context c ON c.innings_id = b.innings_id
  LEFT JOIN credited w ON w.innings_id = b.innings_id AND w.player_id = b.player_id;

CREATE UNIQUE INDEX ux_bowling_innings ON analytics.bowling_innings (innings_id, player_id);
CREATE INDEX ix_bowling_innings_player ON analytics.bowling_innings (player_id);
CREATE INDEX ix_bowling_innings_match ON analytics.bowling_innings (match_id);
