package com.iplytics.analytics.query;

import com.iplytics.common.metrics.CricketMetrics;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
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

    public List<SeasonBattingStats> bySeason(int playerId, AnalyticsFilter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource("playerId", playerId);
        String sql = "select b.season_year, " + AGGREGATES + " from analytics.batting_innings b"
                + " where b.player_id = :playerId" + filter.conditions("b", true, params)
                + " group by b.season_year order by b.season_year";
        return jdbc.query(sql, params, (rs, n) -> new SeasonBattingStats(rs.getInt("season_year"), map(rs)));
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
    }

    public record SeasonBattingStats(int seasonYear, BattingStats stats) {
    }
}
