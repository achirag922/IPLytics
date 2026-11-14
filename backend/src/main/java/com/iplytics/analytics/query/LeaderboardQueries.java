package com.iplytics.analytics.query;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Batting and bowling leaderboards. Ranking happens in SQL; ratio metrics use the {@code analytics.*} functions,
 * which are identical to {@code CricketMetrics} (consistency-tested). Ratio boards require a minimum sample
 * (defaults from docs/metrics.md section 7) so tiny samples cannot top the table. Super overs are excluded unless
 * the filter asks for them. Ties are broken deterministically.
 */
@Repository
public class LeaderboardQueries {

    /** Batting metrics: value expression, sort direction, default minimum balls and minimum dismissals. */
    public enum BattingMetric {
        RUNS("sum(b.runs)", "desc", 0, 0),
        AVERAGE("analytics.batting_average(sum(b.runs), count(*) filter (where b.is_out))", "desc", 0, 5),
        STRIKE_RATE("analytics.strike_rate(sum(b.runs), sum(b.balls))", "desc", 100, 0),
        FOURS("sum(b.fours)", "desc", 0, 0),
        SIXES("sum(b.sixes)", "desc", 0, 0),
        HUNDREDS("count(*) filter (where b.runs >= 100)", "desc", 0, 0),
        FIFTIES("count(*) filter (where b.runs between 50 and 99)", "desc", 0, 0);

        final String value;
        final String direction;
        final int defaultMinBalls;

        public int defaultMinimumBalls() {
            return defaultMinBalls;
        }
        final int defaultMinDismissals;

        BattingMetric(String value, String direction, int defaultMinBalls, int defaultMinDismissals) {
            this.value = value;
            this.direction = direction;
            this.defaultMinBalls = defaultMinBalls;
            this.defaultMinDismissals = defaultMinDismissals;
        }
    }

    /** Bowling metrics: value expression, sort direction, default minimum legal balls and minimum wickets. */
    public enum BowlingMetric {
        WICKETS("sum(b.wickets)", "desc", 0, 0),
        ECONOMY("analytics.economy(sum(b.runs_conceded), sum(b.legal_balls))", "asc", 60, 0),
        AVERAGE("analytics.bowling_average(sum(b.runs_conceded), sum(b.wickets))", "asc", 0, 5),
        STRIKE_RATE("analytics.bowling_strike_rate(sum(b.legal_balls), sum(b.wickets))", "asc", 0, 5),
        DOT_BALLS("sum(b.dot_balls)", "desc", 0, 0);

        final String value;
        final String direction;
        final int defaultMinLegalBalls;

        public int defaultMinimumLegalBalls() {
            return defaultMinLegalBalls;
        }
        final int defaultMinWickets;

        BowlingMetric(String value, String direction, int defaultMinLegalBalls, int defaultMinWickets) {
            this.value = value;
            this.direction = direction;
            this.defaultMinLegalBalls = defaultMinLegalBalls;
            this.defaultMinWickets = defaultMinWickets;
        }
    }

    private final NamedParameterJdbcTemplate jdbc;

    public LeaderboardQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @param minBalls minimum balls faced; null uses the metric's documented default */
    public List<BattingEntry> batting(BattingMetric metric, AnalyticsFilter filter, Integer minBalls, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("minBalls", minBalls != null ? minBalls : metric.defaultMinBalls)
                .addValue("minDismissals", metric.defaultMinDismissals)
                .addValue("limit", limit);
        String sql = """
                select b.player_id, p.display_name, count(*) as innings, sum(b.runs) as runs, sum(b.balls) as balls,
                       count(*) filter (where b.is_out) as dismissals, sum(b.fours) as fours, sum(b.sixes) as sixes,
                       count(*) filter (where b.runs between 50 and 99) as fifties,
                       count(*) filter (where b.runs >= 100) as hundreds,
                       %s as metric_value
                  from analytics.batting_innings b join core.player p on p.id = b.player_id
                 where true %s
                 group by b.player_id, p.display_name
                having sum(b.balls) >= :minBalls and count(*) filter (where b.is_out) >= :minDismissals
                 order by metric_value %s nulls last, sum(b.runs) desc, sum(b.balls), p.display_name, b.player_id
                 limit :limit""".formatted(metric.value, filter.conditions("b", true, params), metric.direction);
        return jdbc.query(sql, params, (rs, n) -> new BattingEntry(n + 1, rs.getInt("player_id"),
                rs.getString("display_name"), rs.getLong("innings"), rs.getLong("runs"), rs.getLong("balls"),
                rs.getLong("dismissals"), rs.getLong("fours"), rs.getLong("sixes"), rs.getLong("fifties"),
                rs.getLong("hundreds"), rs.getBigDecimal("metric_value")));
    }

    /** @param minLegalBalls minimum legal balls bowled; null uses the metric's documented default */
    public List<BowlingEntry> bowling(BowlingMetric metric, AnalyticsFilter filter, Integer minLegalBalls, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("minLegalBalls", minLegalBalls != null ? minLegalBalls : metric.defaultMinLegalBalls)
                .addValue("minWickets", metric.defaultMinWickets)
                .addValue("limit", limit);
        String sql = """
                select b.player_id, p.display_name, count(*) as innings, sum(b.legal_balls) as legal_balls,
                       sum(b.runs_conceded) as runs_conceded, sum(b.wickets) as wickets, sum(b.dot_balls) as dot_balls,
                       %s as metric_value
                  from analytics.bowling_innings b join core.player p on p.id = b.player_id
                 where true %s
                 group by b.player_id, p.display_name
                having sum(b.legal_balls) >= :minLegalBalls and sum(b.wickets) >= :minWickets
                 order by metric_value %s nulls last, sum(b.wickets) desc, sum(b.runs_conceded), p.display_name, b.player_id
                 limit :limit""".formatted(metric.value, filter.conditions("b", true, params), metric.direction);
        return jdbc.query(sql, params, (rs, n) -> new BowlingEntry(n + 1, rs.getInt("player_id"),
                rs.getString("display_name"), rs.getLong("innings"), rs.getLong("legal_balls"),
                rs.getLong("runs_conceded"), rs.getLong("wickets"), rs.getLong("dot_balls"),
                rs.getBigDecimal("metric_value")));
    }

    /** @param value the ranked metric (a count, or a ratio identical to CricketMetrics) */
    public record BattingEntry(int rank, int playerId, String playerName, long innings, long runs, long balls,
                               long dismissals, long fours, long sixes, long fifties, long hundreds,
                               BigDecimal value) {
    }

    public record BowlingEntry(int rank, int playerId, String playerName, long innings, long legalBalls,
                               long runsConceded, long wickets, long dotBalls, BigDecimal value) {
    }
}
