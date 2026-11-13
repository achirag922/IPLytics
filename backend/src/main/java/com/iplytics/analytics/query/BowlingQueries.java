package com.iplytics.analytics.query;

import com.iplytics.common.metrics.CricketMetrics;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Player bowling statistics from {@code analytics.bowling_innings} (formulas: docs/metrics.md section 5). */
@Repository
public class BowlingQueries {

    static final String AGGREGATES = """
            count(*) as innings,
            coalesce(sum(b.legal_balls), 0) as legal_balls,
            coalesce(sum(b.runs_conceded), 0) as runs_conceded,
            coalesce(sum(b.wickets), 0) as wickets,
            coalesce(sum(b.dot_balls), 0) as dot_balls,
            coalesce(sum(b.fours_conceded), 0) as fours_conceded,
            coalesce(sum(b.sixes_conceded), 0) as sixes_conceded,
            coalesce(sum(b.wides), 0) as wides,
            coalesce(sum(b.no_balls), 0) as no_balls,
            count(*) filter (where b.wickets = 3) as three_wickets,
            count(*) filter (where b.wickets = 4) as four_wickets,
            count(*) filter (where b.wickets >= 5) as five_wickets,
            (array_agg(b.wickets order by b.wickets desc, b.runs_conceded))[1] as best_wickets,
            (array_agg(b.runs_conceded order by b.wickets desc, b.runs_conceded))[1] as best_runs""";

    private final NamedParameterJdbcTemplate jdbc;

    public BowlingQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public BowlingStats career(int playerId, AnalyticsFilter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource("playerId", playerId);
        String sql = "select " + AGGREGATES + " from analytics.bowling_innings b where b.player_id = :playerId"
                + filter.conditions("b", true, params);
        return jdbc.queryForObject(sql, params, (rs, n) -> map(rs));
    }

    public List<SeasonBowlingStats> bySeason(int playerId, AnalyticsFilter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource("playerId", playerId);
        String sql = "select b.season_year, " + AGGREGATES + " from analytics.bowling_innings b"
                + " where b.player_id = :playerId" + filter.conditions("b", true, params)
                + " group by b.season_year order by b.season_year";
        return jdbc.query(sql, params, (rs, n) -> new SeasonBowlingStats(rs.getInt("season_year"), map(rs)));
    }

    static BowlingStats map(ResultSet rs) throws SQLException {
        long legalBalls = rs.getLong("legal_balls");
        long runs = rs.getLong("runs_conceded");
        long wickets = rs.getLong("wickets");
        long dots = rs.getLong("dot_balls");
        return new BowlingStats(rs.getLong("innings"), legalBalls, CricketMetrics.overs(legalBalls), runs, wickets,
                dots, rs.getLong("fours_conceded"), rs.getLong("sixes_conceded"), rs.getLong("wides"),
                rs.getLong("no_balls"), rs.getLong("three_wickets"), rs.getLong("four_wickets"),
                rs.getLong("five_wickets"), (Integer) rs.getObject("best_wickets"), (Integer) rs.getObject("best_runs"),
                CricketMetrics.economy(runs, legalBalls),
                CricketMetrics.bowlingAverage(runs, wickets),
                CricketMetrics.bowlingStrikeRate(legalBalls, wickets),
                CricketMetrics.dotBallPercentage(dots, legalBalls));
    }

    /**
     * Raw counts plus ratios computed by {@link CricketMetrics}. {@code wides}/{@code noBalls} count deliveries;
     * best figures are most wickets, then fewest runs, in one innings.
     */
    public record BowlingStats(long innings, long legalBalls, String overs, long runsConceded, long wickets,
                               long dotBalls, long foursConceded, long sixesConceded, long wides, long noBalls,
                               long threeWicketHauls, long fourWicketHauls, long fiveWicketHauls,
                               Integer bestWickets, Integer bestRuns, BigDecimal economy, BigDecimal average,
                               BigDecimal strikeRate, BigDecimal dotBallPercentage) {
    }

    public record SeasonBowlingStats(int seasonYear, BowlingStats stats) {
    }
}
