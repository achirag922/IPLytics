-- V1.6: one row per innings and phase (powerplay overs 1-6, middle 7-15, death 16-20; super overs as 'super_over').
-- Feeds the phase insights (team batting and bowling by phase, season and venue phase trends). Columns follow the
-- analytics convention: team_id is the batting side, opponent_team_id the bowling side.
CREATE MATERIALIZED VIEW analytics.innings_phase AS
WITH dismissals AS (
    SELECT w.delivery_id, count(*) AS wickets
      FROM core.wicket w
     WHERE analytics.is_dismissal(w.kind)
     GROUP BY w.delivery_id)
SELECT c.match_id, c.innings_id, c.season_id, c.season_year, c.batting_team_id AS team_id,
       c.bowling_team_id AS opponent_team_id, c.venue_id, c.match_date, c.innings_number, c.is_super_over,
       analytics.phase(d.over_number, d.is_super_over) AS phase,
       sum(d.total_runs)::int AS runs,
       sum(d.extra_runs)::int AS extras,
       count(*) FILTER (WHERE d.is_legal_ball)::int AS legal_balls,
       coalesce(sum(x.wickets), 0)::int AS wickets,
       count(*) FILTER (WHERE d.batter_runs = 4 AND NOT d.non_boundary)::int AS fours,
       count(*) FILTER (WHERE d.batter_runs = 6 AND NOT d.non_boundary)::int AS sixes,
       count(*) FILTER (WHERE d.is_legal_ball AND d.total_runs = 0)::int AS dot_balls
  FROM core.delivery d
  JOIN analytics.innings_context c ON c.innings_id = d.innings_id
  LEFT JOIN dismissals x ON x.delivery_id = d.id
 GROUP BY c.match_id, c.innings_id, c.season_id, c.season_year, c.batting_team_id, c.bowling_team_id, c.venue_id,
          c.match_date, c.innings_number, c.is_super_over, analytics.phase(d.over_number, d.is_super_over);

CREATE UNIQUE INDEX ux_innings_phase ON analytics.innings_phase (innings_id, phase);
CREATE INDEX ix_innings_phase_team ON analytics.innings_phase (team_id);
CREATE INDEX ix_innings_phase_opponent ON analytics.innings_phase (opponent_team_id);
CREATE INDEX ix_innings_phase_venue ON analytics.innings_phase (venue_id);
