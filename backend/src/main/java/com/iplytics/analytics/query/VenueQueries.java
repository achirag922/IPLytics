package com.iplytics.analytics.query;

import com.iplytics.common.metrics.CricketMetrics;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Venue characteristics (docs/analytics.md, "Venue queries"). Decisive matches are those won by runs or wickets
 * (ties and no-results excluded). Super overs never count.
 */
@Repository
public class VenueQueries {

    private static final String MATCH_LEVEL = """
            select count(*) as matches,
                   count(*) filter (where m.result in ('runs', 'wickets')) as decisive,
                   count(*) filter (where m.result in ('runs', 'wickets') and m.winner_team_id = i1.batting_team_id) as batting_first_wins,
                   count(*) filter (where m.result in ('runs', 'wickets') and m.winner_team_id = i2.batting_team_id) as chasing_wins,
                   coalesce(sum(i1.total_runs) filter (where m.result <> 'no result' and m.method is null), 0) as first_innings_runs,
                   count(i1.innings_id) filter (where m.result <> 'no result' and m.method is null) as first_innings
              from core.match m
              join core.season s on s.id = m.season_id
              left join analytics.innings_summary i1 on i1.match_id = m.id and i1.innings_number = 1
              left join analytics.innings_summary i2 on i2.match_id = m.id and i2.innings_number = 2
             where m.venue_id = :venueId""";

    private static final String INNINGS_LEVEL = """
            select max(i.total_runs) as highest_total,
                   min(i.total_runs) filter (where i.wickets = 10 or i.legal_balls >= coalesce(m.scheduled_overs, 20) * 6)
                       as lowest_completed_total,
                   coalesce(sum(i.total_runs), 0) as runs,
                   coalesce(sum(i.legal_balls), 0) as legal_balls,
                   count(*) as innings,
                   coalesce(sum(i.wickets), 0) as wickets,
                   coalesce(sum(i.fours + i.sixes), 0) as boundaries
              from analytics.innings_summary i
              join core.match m on m.id = i.match_id
             where i.venue_id = :venueId and not i.is_super_over""";

    private final NamedParameterJdbcTemplate jdbc;

    public VenueQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public VenueStats summary(int venueId, AnalyticsFilter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource("venueId", venueId);
        Map<String, Object> matches = jdbc.queryForMap(MATCH_LEVEL + seasons("s.year", filter, params), params);
        Map<String, Object> innings = jdbc.queryForMap(INNINGS_LEVEL + seasons("i.season_year", filter, params), params);
        return build(matches, innings);
    }

    /** {@link #summary} per season, in season order: two grouped queries. */
    public List<SeasonVenueStats> bySeason(int venueId, AnalyticsFilter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource("venueId", venueId);
        Map<Integer, Map<String, Object>> matches = bySeasonKey(jdbc.queryForList(
                MATCH_LEVEL.replaceFirst("select ", "select s.year as season_year, ")
                        + seasons("s.year", filter, params) + " group by s.year", params));
        Map<Integer, Map<String, Object>> innings = bySeasonKey(jdbc.queryForList(
                INNINGS_LEVEL.replaceFirst("select ", "select i.season_year, ")
                        + seasons("i.season_year", filter, params) + " group by i.season_year", params));
        return matches.entrySet().stream().map(e -> new SeasonVenueStats(e.getKey(),
                build(e.getValue(), innings.getOrDefault(e.getKey(), Map.of())))).toList();
    }

    /**
     * First-innings totals in 20-run bands (under 120, 120-139, ..., 220 and over), over matches with a result and
     * no D/L adjustment. Every band is returned, empty ones with 0.
     */
    public List<ScoreBand> scoreDistribution(int venueId, AnalyticsFilter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource("venueId", venueId);
        Map<Integer, Long> counts = new HashMap<>();
        jdbc.query("""
                select width_bucket(i.total_runs, 120, 220, 5) as band, count(*) as innings
                  from analytics.innings_summary i join core.match m on m.id = i.match_id
                 where i.venue_id = :venueId and i.innings_number = 1 and not i.is_super_over
                   and m.result <> 'no result' and m.method is null""" + seasons("i.season_year", filter, params)
                + " group by 1", params, rs -> {
                    counts.put(rs.getInt("band"), rs.getLong("innings"));
                });
        List<ScoreBand> bands = new ArrayList<>();
        for (int band = 0; band <= 6; band++) {
            Integer from = band == 0 ? null : 120 + 20 * (band - 1);
            Integer to = band == 6 ? null : 119 + 20 * band;
            bands.add(new ScoreBand(from, to, counts.getOrDefault(band, 0L)));
        }
        return bands;
    }

    private static Map<Integer, Map<String, Object>> bySeasonKey(List<Map<String, Object>> rows) {
        Map<Integer, Map<String, Object>> result = new TreeMap<>();
        rows.forEach(row -> result.put(((Number) row.get("season_year")).intValue(), row));
        return result;
    }

    private static VenueStats build(Map<String, Object> matches, Map<String, Object> innings) {
        long decisive = number(matches, "decisive");
        long battingFirstWins = number(matches, "batting_first_wins");
        long chasingWins = number(matches, "chasing_wins");
        long runs = number(innings, "runs");
        long legalBalls = number(innings, "legal_balls");
        long inningsCount = number(innings, "innings");
        return new VenueStats(number(matches, "matches"), decisive, battingFirstWins, chasingWins,
                CricketMetrics.percentage(battingFirstWins, decisive), CricketMetrics.percentage(chasingWins, decisive),
                CricketMetrics.mean(number(matches, "first_innings_runs"), number(matches, "first_innings")),
                (Integer) innings.get("highest_total"), (Integer) innings.get("lowest_completed_total"),
                runs, legalBalls, CricketMetrics.runRate(runs, legalBalls), inningsCount,
                CricketMetrics.mean(number(innings, "wickets"), inningsCount),
                CricketMetrics.mean(number(innings, "boundaries"), inningsCount));
    }

    private static String seasons(String column, AnalyticsFilter filter, MapSqlParameterSource params) {
        StringBuilder sql = new StringBuilder();
        if (filter.seasonFrom() != null) {
            sql.append(" and ").append(column).append(" >= :seasonFrom");
            params.addValue("seasonFrom", filter.seasonFrom());
        }
        if (filter.seasonTo() != null) {
            sql.append(" and ").append(column).append(" <= :seasonTo");
            params.addValue("seasonTo", filter.seasonTo());
        }
        return sql.toString();
    }

    private static long number(Map<String, Object> row, String column) {
        Object value = row.get(column);
        return value == null ? 0 : ((Number) value).longValue();
    }

    /**
     * @param averageFirstInningsScore over matches with a result and no D/L adjustment
     * @param lowestCompletedTotal     lowest total of an innings that was all out or used its full scheduled overs
     * @param averageRunRate           all regular-innings runs per 6 legal balls at the venue
     * @param wicketsPerInnings        dismissals per regular innings
     * @param boundariesPerInnings     fours plus sixes (not run fours) per regular innings
     */
    public record VenueStats(long matches, long decisiveMatches, long battingFirstWins, long chasingWins,
                             BigDecimal battingFirstWinPercentage, BigDecimal chaseSuccessPercentage,
                             BigDecimal averageFirstInningsScore, Integer highestTotal, Integer lowestCompletedTotal,
                             long runs, long legalBalls, BigDecimal averageRunRate, long innings,
                             BigDecimal wicketsPerInnings, BigDecimal boundariesPerInnings) {
    }

    public record SeasonVenueStats(int seasonYear, VenueStats stats) {
    }

    /** @param from lowest total in the band (null: no lower bound) @param to highest (null: no upper bound) */
    public record ScoreBand(Integer from, Integer to, long innings) {
    }
}
