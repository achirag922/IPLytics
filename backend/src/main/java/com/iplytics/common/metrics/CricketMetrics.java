package com.iplytics.common.metrics;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The single Java definition of every cricket ratio (docs/metrics.md). Each method mirrors an SQL function in
 * the {@code analytics} schema (V7 migration) exactly, and consistency tests compare the two:
 * computed from raw totals, {@code null} when the denominator is 0, rounded to 2 decimals half-up.
 */
public final class CricketMetrics {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private CricketMetrics() {
    }

    /** Runs per dismissal. SQL: {@code analytics.batting_average}. */
    public static BigDecimal battingAverage(long runs, long dismissals) {
        return ratio(runs, dismissals);
    }

    /** Runs per 100 balls faced. SQL: {@code analytics.strike_rate}. */
    public static BigDecimal strikeRate(long runs, long balls) {
        return ratio(100 * runs, balls);
    }

    /** Runs conceded per 6 legal balls. SQL: {@code analytics.economy}. */
    public static BigDecimal economy(long runsConceded, long legalBalls) {
        return ratio(6 * runsConceded, legalBalls);
    }

    /** Runs per 6 legal balls. SQL: {@code analytics.run_rate}. */
    public static BigDecimal runRate(long runs, long legalBalls) {
        return ratio(6 * runs, legalBalls);
    }

    /** Runs conceded per wicket. SQL: {@code analytics.bowling_average}. */
    public static BigDecimal bowlingAverage(long runsConceded, long wickets) {
        return ratio(runsConceded, wickets);
    }

    /** Legal balls per wicket. SQL: {@code analytics.bowling_strike_rate}. */
    public static BigDecimal bowlingStrikeRate(long legalBalls, long wickets) {
        return ratio(legalBalls, wickets);
    }

    /** Part as a percentage of whole. SQL: {@code analytics.percentage}. */
    public static BigDecimal percentage(long part, long whole) {
        return ratio(100 * part, whole);
    }

    /** Dot balls as a percentage of balls (faced or bowled). */
    public static BigDecimal dotBallPercentage(long dotBalls, long balls) {
        return percentage(dotBalls, balls);
    }

    /** Fours plus sixes as a percentage of balls faced. */
    public static BigDecimal boundaryPercentage(long boundaries, long balls) {
        return percentage(boundaries, balls);
    }

    /** Balls faced per boundary. SQL: {@code analytics.balls_per_boundary}. */
    public static BigDecimal ballsPerBoundary(long balls, long boundaries) {
        return ratio(balls, boundaries);
    }

    /** Wins as a percentage of matches with a result. SQL: {@code analytics.win_percentage}. */
    public static BigDecimal winPercentage(long wins, long played, long noResults) {
        return ratio(100 * wins, played - noResults);
    }

    /** Plain average, e.g. runs per innings batted. */
    public static BigDecimal mean(long total, long count) {
        return ratio(total, count);
    }

    /** Overs notation for display: 22 legal balls is {@code "3.4"}. SQL: {@code analytics.overs}. */
    public static String overs(long legalBalls) {
        return (legalBalls / 6) + "." + (legalBalls % 6);
    }

    /** Mirrors SQL {@code round(numerator / nullif(denominator, 0), 2)}: null only when the denominator is 0. */
    private static BigDecimal ratio(long numerator, long denominator) {
        if (denominator == 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), SCALE, ROUNDING);
    }
}
