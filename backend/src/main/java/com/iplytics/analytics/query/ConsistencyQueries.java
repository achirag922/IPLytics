package com.iplytics.analytics.query;

import com.iplytics.common.metrics.CricketMetrics;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * How consistent a player is (docs/metrics.md section 10): the spread of innings scores and figures rather than
 * their totals, plus the most recent innings as a form guide. Regular innings only, unless the filter says otherwise.
 */
@Repository
public class ConsistencyQueries {

    /** Recent innings returned by the form guides. */
    public static final int FORM_INNINGS = 10;

    private final NamedParameterJdbcTemplate jdbc;

    public ConsistencyQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public BattingConsistency batting(int playerId, AnalyticsFilter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource("playerId", playerId);
        String where = " from analytics.batting_innings b where b.player_id = :playerId"
                + filter.conditions("b", true, params);
        BattingConsistency stats = jdbc.queryForObject("""
                select count(*) as innings, coalesce(sum(b.runs), 0) as runs,
                       count(*) filter (where b.is_out) as dismissals,
                       percentile_cont(0.5) within group (order by b.runs) as median,
                       avg(b.runs) as mean, stddev_samp(b.runs) as sd,
                       count(*) filter (where b.runs >= 30) as thirty_plus,
                       count(*) filter (where b.runs >= 50) as fifty_plus,
                       count(*) filter (where b.runs < 10) as under_ten,
                       count(*) filter (where b.runs = 0) as band_0,
                       count(*) filter (where b.runs between 1 and 9) as band_1,
                       count(*) filter (where b.runs between 10 and 29) as band_2,
                       count(*) filter (where b.runs between 30 and 49) as band_3,
                       count(*) filter (where b.runs between 50 and 99) as band_4,
                       count(*) filter (where b.runs >= 100) as band_5""" + where, params, (rs, n) -> {
            long innings = rs.getLong("innings");
            return new BattingConsistency(innings, CricketMetrics.battingAverage(rs.getLong("runs"),
                    rs.getLong("dismissals")), CricketMetrics.round((Number) rs.getObject("median")),
                    CricketMetrics.round((Number) rs.getObject("sd")),
                    CricketMetrics.coefficientOfVariation((Number) rs.getObject("sd"), (Number) rs.getObject("mean")),
                    CricketMetrics.percentage(rs.getLong("thirty_plus"), innings),
                    CricketMetrics.percentage(rs.getLong("fifty_plus"), innings),
                    CricketMetrics.percentage(rs.getLong("under_ten"), innings),
                    List.of(new ScoreBand("0", rs.getLong("band_0")), new ScoreBand("1-9", rs.getLong("band_1")),
                            new ScoreBand("10-29", rs.getLong("band_2")), new ScoreBand("30-49", rs.getLong("band_3")),
                            new ScoreBand("50-99", rs.getLong("band_4")), new ScoreBand("100+", rs.getLong("band_5"))),
                    List.of());
        });
        List<BattingInnings> form = jdbc.query("""
                select b.match_id, b.match_date, b.opponent_team_id, b.runs, b.balls, not b.is_out as not_out,
                       b.strike_rate""" + where + " order by b.match_date desc, b.match_id desc, b.innings_number desc"
                + " limit " + FORM_INNINGS, params, (rs, n) -> new BattingInnings(rs.getInt("match_id"),
                rs.getObject("match_date", LocalDate.class), rs.getInt("opponent_team_id"), rs.getInt("runs"),
                rs.getInt("balls"), rs.getBoolean("not_out"), rs.getBigDecimal("strike_rate")));
        return stats.withForm(form);
    }

    public BowlingConsistency bowling(int playerId, AnalyticsFilter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource("playerId", playerId);
        String where = " from analytics.bowling_innings w where w.player_id = :playerId"
                + filter.conditions("w", true, params);
        BowlingConsistency stats = jdbc.queryForObject("""
                select count(*) as innings,
                       count(*) filter (where w.wickets >= 1) as wicket_innings,
                       count(*) filter (where w.wickets >= 2) as multi_wicket_innings,
                       count(*) filter (where w.legal_balls >= 6) as over_innings,
                       count(*) filter (where w.legal_balls >= 6 and w.economy < 8) as economical_innings,
                       avg(w.economy) filter (where w.legal_balls >= 6) as mean_economy,
                       stddev_samp(w.economy) filter (where w.legal_balls >= 6) as sd_economy,
                       percentile_cont(0.5) within group (order by w.wickets) as median_wickets""" + where, params,
                (rs, n) -> {
                    long innings = rs.getLong("innings");
                    long overInnings = rs.getLong("over_innings");
                    return new BowlingConsistency(innings,
                            CricketMetrics.percentage(rs.getLong("wicket_innings"), innings),
                            CricketMetrics.percentage(rs.getLong("multi_wicket_innings"), innings),
                            CricketMetrics.round((Number) rs.getObject("median_wickets")),
                            CricketMetrics.round((Number) rs.getObject("sd_economy")),
                            CricketMetrics.coefficientOfVariation((Number) rs.getObject("sd_economy"),
                                    (Number) rs.getObject("mean_economy")),
                            CricketMetrics.percentage(rs.getLong("economical_innings"), overInnings), List.of());
                });
        List<BowlingInnings> form = jdbc.query("""
                select w.match_id, w.match_date, w.opponent_team_id, w.legal_balls, w.runs_conceded, w.wickets,
                       w.economy""" + where + " order by w.match_date desc, w.match_id desc, w.innings_number desc"
                + " limit " + FORM_INNINGS, params, (rs, n) -> new BowlingInnings(rs.getInt("match_id"),
                rs.getObject("match_date", LocalDate.class), rs.getInt("opponent_team_id"),
                CricketMetrics.overs(rs.getLong("legal_balls")), rs.getInt("runs_conceded"), rs.getInt("wickets"),
                rs.getBigDecimal("economy")));
        return stats.withForm(form);
    }

    public record ScoreBand(String label, long innings) {
    }

    /**
     * @param standardDeviation       sample standard deviation of innings scores (null with fewer than 2 innings)
     * @param coefficientOfVariation  100 x standard deviation / mean score; lower is steadier
     * @param thirtyPlusPercentage    innings of 30 or more, % of innings
     * @param fiftyPlusPercentage     innings of 50 or more, % of innings
     * @param underTenPercentage      innings under 10 (not-out starts included), % of innings
     */
    public record BattingConsistency(long innings, BigDecimal average, BigDecimal medianScore,
                                     BigDecimal standardDeviation, BigDecimal coefficientOfVariation,
                                     BigDecimal thirtyPlusPercentage, BigDecimal fiftyPlusPercentage,
                                     BigDecimal underTenPercentage, List<ScoreBand> scoreBands,
                                     List<BattingInnings> recentInnings) {

        BattingConsistency withForm(List<BattingInnings> form) {
            return new BattingConsistency(innings, average, medianScore, standardDeviation, coefficientOfVariation,
                    thirtyPlusPercentage, fiftyPlusPercentage, underTenPercentage, scoreBands, form);
        }
    }

    public record BattingInnings(int matchId, LocalDate date, int opponentId, int runs, int balls, boolean notOut,
                                 BigDecimal strikeRate) {
    }

    /**
     * @param wicketInningsPercentage      innings with at least one wicket, % of innings bowled
     * @param multiWicketInningsPercentage innings with two or more wickets, % of innings bowled
     * @param economyStandardDeviation     spread of economy over innings of at least one over
     * @param economicalInningsPercentage  innings of at least one over conceding under 8 an over, % of those
     */
    public record BowlingConsistency(long innings, BigDecimal wicketInningsPercentage,
                                     BigDecimal multiWicketInningsPercentage, BigDecimal medianWickets,
                                     BigDecimal economyStandardDeviation, BigDecimal economyCoefficientOfVariation,
                                     BigDecimal economicalInningsPercentage, List<BowlingInnings> recentInnings) {

        BowlingConsistency withForm(List<BowlingInnings> form) {
            return new BowlingConsistency(innings, wicketInningsPercentage, multiWicketInningsPercentage,
                    medianWickets, economyStandardDeviation, economyCoefficientOfVariation,
                    economicalInningsPercentage, form);
        }
    }

    public record BowlingInnings(int matchId, LocalDate date, int opponentId, String overs, int runsConceded,
                                 int wickets, BigDecimal economy) {
    }
}
