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

    /** Career figures of several players in one query; players who never bowled are absent from the map. */
    public Map<Integer, BowlingStats> careers(Collection<Integer> playerIds, AnalyticsFilter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource("playerIds", playerIds);
        String sql = "select b.player_id, " + AGGREGATES + " from analytics.bowling_innings b"
                + " where b.player_id in (:playerIds)" + filter.conditions("b", true, params) + " group by b.player_id";
        Map<Integer, BowlingStats> result = new LinkedHashMap<>();
        jdbc.query(sql, params, rs -> {
            result.put(rs.getInt("player_id"), map(rs));
        });
        return result;
    }

    public List<SeasonBowlingStats> bySeason(int playerId, AnalyticsFilter filter) {
        return grouped(playerId, filter, Grouping.SEASON).stream()
                .map(g -> new SeasonBowlingStats(g.key(), g.stats())).toList();
    }

    /** Breakdown by season, opponent or venue, ordered by the key. */
    public List<Grouped> grouped(int playerId, AnalyticsFilter filter, Grouping grouping) {
        MapSqlParameterSource params = new MapSqlParameterSource("playerId", playerId);
        String sql = "select b." + grouping.column + " as group_key, " + AGGREGATES + " from analytics.bowling_innings b"
                + " where b.player_id = :playerId" + filter.conditions("b", true, params)
                + " group by b." + grouping.column + " order by b." + grouping.column;
        return jdbc.query(sql, params, (rs, n) -> new Grouped(rs.getInt("group_key"), map(rs)));
    }

    /** Legal balls, runs conceded and wickets per phase; super overs never belong to a phase. */
    public List<PhaseBowling> phases(int playerId, AnalyticsFilter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource("playerId", playerId);
        StringBuilder columns = new StringBuilder();
        for (CricketPhase phase : List.of(CricketPhase.POWERPLAY, CricketPhase.MIDDLE, CricketPhase.DEATH)) {
            String p = phase.sqlName();
            columns.append(columns.isEmpty() ? "" : ", ").append("coalesce(sum(b.%1$s_balls), 0) as %1$s_balls, "
                    .formatted(p)).append("coalesce(sum(b.%1$s_runs), 0) as %1$s_runs, ".formatted(p))
                    .append("coalesce(sum(b.%1$s_wickets), 0) as %1$s_wickets".formatted(p));
        }
        String sql = "select " + columns + " from analytics.bowling_innings b where b.player_id = :playerId"
                + filter.conditions("b", true, params);
        return jdbc.queryForObject(sql, params, (rs, n) -> List.of(
                phase(rs, CricketPhase.POWERPLAY), phase(rs, CricketPhase.MIDDLE), phase(rs, CricketPhase.DEATH)));
    }

    private static PhaseBowling phase(ResultSet rs, CricketPhase phase) throws SQLException {
        long balls = rs.getLong(phase.sqlName() + "_balls");
        long runs = rs.getLong(phase.sqlName() + "_runs");
        return new PhaseBowling(phase, balls, runs, rs.getLong(phase.sqlName() + "_wickets"),
                CricketMetrics.economy(runs, balls));
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

        /** Figures of a player with no innings in the filter. */
        public static BowlingStats none() {
            return new BowlingStats(0, 0, "0.0", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null, null, null, null, null, null);
        }
    }

    public record SeasonBowlingStats(int seasonYear, BowlingStats stats) {
    }

    /** @param key season year, opponent team id or venue id, depending on the {@link Grouping} */
    public record Grouped(int key, BowlingStats stats) {
    }

    public record PhaseBowling(CricketPhase phase, long legalBalls, long runsConceded, long wickets,
                               BigDecimal economy) {
    }
}
