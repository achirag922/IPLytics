package com.iplytics.analytics.query;

import com.iplytics.common.metrics.CricketMetrics;
import com.iplytics.common.metrics.CricketPhase;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Player batting statistics from {@code analytics.batting_innings} (formulas: docs/metrics.md section 4). */
@Repository
public class BattingQueries {

    static final String AGGREGATES = """
            count(*) as innings,
            coalesce(sum(b.runs), 0) as runs,
            coalesce(sum(b.balls), 0) as balls,
            count(*) filter (where b.is_out) as dismissals,
            coalesce(sum(b.fours), 0) as fours,
            coalesce(sum(b.sixes), 0) as sixes,
            coalesce(sum(b.dot_balls), 0) as dot_balls,
            count(*) filter (where b.runs between 50 and 99) as fifties,
            count(*) filter (where b.runs >= 100) as hundreds,
            count(*) filter (where b.runs = 0 and b.is_out) as ducks,
            max(b.runs) as highest_score,
            (array_agg(not b.is_out order by b.runs desc, b.is_out))[1] as highest_not_out""";

    private final NamedParameterJdbcTemplate jdbc;

    public BattingQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public BattingStats career(int playerId, AnalyticsFilter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource("playerId", playerId);
        String sql = "select " + AGGREGATES + " from analytics.batting_innings b where b.player_id = :playerId"
                + filter.conditions("b", true, params);
        return jdbc.queryForObject(sql, params, (rs, n) -> map(rs));
    }

    /** Career figures of several players in one query; players without an innings are absent from the map. */
    public Map<Integer, BattingStats> careers(Collection<Integer> playerIds, AnalyticsFilter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource("playerIds", playerIds);
        String sql = "select b.player_id, " + AGGREGATES + " from analytics.batting_innings b"
                + " where b.player_id in (:playerIds)" + filter.conditions("b", true, params) + " group by b.player_id";
        Map<Integer, BattingStats> result = new LinkedHashMap<>();
        jdbc.query(sql, params, rs -> {
            result.put(rs.getInt("player_id"), map(rs));
        });
        return result;
    }

    public List<SeasonBattingStats> bySeason(int playerId, AnalyticsFilter filter) {
        return grouped(playerId, filter, Grouping.SEASON).stream()
                .map(g -> new SeasonBattingStats(g.key(), g.stats())).toList();
    }

    /** Breakdown by season, opponent or venue, ordered by the key. */
    public List<Grouped> grouped(int playerId, AnalyticsFilter filter, Grouping grouping) {
        MapSqlParameterSource params = new MapSqlParameterSource("playerId", playerId);
        String sql = "select b." + grouping.column + " as group_key, " + AGGREGATES + " from analytics.batting_innings b"
                + " where b.player_id = :playerId" + filter.conditions("b", true, params)
                + " group by b." + grouping.column + " order by b." + grouping.column;
        return jdbc.query(sql, params, (rs, n) -> new Grouped(rs.getInt("group_key"), map(rs)));
    }

    /** Runs and balls faced per phase; super overs never belong to a phase. */
    public List<PhaseBatting> phases(int playerId, AnalyticsFilter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource("playerId", playerId);
        String sql = """
                select coalesce(sum(b.powerplay_runs), 0) as powerplay_runs, coalesce(sum(b.powerplay_balls), 0) as powerplay_balls,
                       coalesce(sum(b.middle_runs), 0) as middle_runs, coalesce(sum(b.middle_balls), 0) as middle_balls,
                       coalesce(sum(b.death_runs), 0) as death_runs, coalesce(sum(b.death_balls), 0) as death_balls
                  from analytics.batting_innings b where b.player_id = :playerId""" + filter.conditions("b", true, params);
        return jdbc.queryForObject(sql, params, (rs, n) -> List.of(
                phase(CricketPhase.POWERPLAY, rs.getLong("powerplay_runs"), rs.getLong("powerplay_balls")),
                phase(CricketPhase.MIDDLE, rs.getLong("middle_runs"), rs.getLong("middle_balls")),
                phase(CricketPhase.DEATH, rs.getLong("death_runs"), rs.getLong("death_balls"))));
    }

    private static PhaseBatting phase(CricketPhase phase, long runs, long balls) {
        return new PhaseBatting(phase, runs, balls, CricketMetrics.strikeRate(runs, balls));
    }

    static BattingStats map(ResultSet rs) throws SQLException {
        long innings = rs.getLong("innings");
        long runs = rs.getLong("runs");
        long balls = rs.getLong("balls");
        long dismissals = rs.getLong("dismissals");
        long fours = rs.getLong("fours");
        long sixes = rs.getLong("sixes");
        long dots = rs.getLong("dot_balls");
        Integer highest = (Integer) rs.getObject("highest_score");
        return new BattingStats(innings, runs, balls, innings - dismissals, dismissals, fours, sixes, dots,
                rs.getLong("fifties"), rs.getLong("hundreds"), rs.getLong("ducks"), highest,
                highest != null && rs.getBoolean("highest_not_out"),
                CricketMetrics.battingAverage(runs, dismissals),
                CricketMetrics.strikeRate(runs, balls),
                CricketMetrics.dotBallPercentage(dots, balls),
                CricketMetrics.boundaryPercentage(fours + sixes, balls),
                CricketMetrics.ballsPerBoundary(balls, fours + sixes));
    }

    /** Raw counts plus ratios computed by {@link CricketMetrics}; ratios are null when undefined. */
    public record BattingStats(long innings, long runs, long balls, long notOuts, long dismissals, long fours,
                               long sixes, long dotBalls, long fifties, long hundreds, long ducks,
                               Integer highestScore, boolean highestScoreNotOut, BigDecimal average,
                               BigDecimal strikeRate, BigDecimal dotBallPercentage, BigDecimal boundaryPercentage,
                               BigDecimal ballsPerBoundary) {

        /** Figures of a player with no innings in the filter. */
        public static BattingStats none() {
            return new BattingStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null, false, null, null, null, null, null);
        }
    }

    public record SeasonBattingStats(int seasonYear, BattingStats stats) {
    }

    /** @param key season year, opponent team id or venue id, depending on the {@link Grouping} */
    public record Grouped(int key, BattingStats stats) {
    }

    public record PhaseBatting(CricketPhase phase, long runs, long balls, BigDecimal strikeRate) {
    }
}
