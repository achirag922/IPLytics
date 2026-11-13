package com.iplytics.analytics.query;

import com.iplytics.common.metrics.CricketMetrics;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Franchise records from {@code analytics.team_match_result} (docs/metrics.md section 6). Wins include ties won
 * in a super over; win percentage excludes no-results. Scores exclude super overs.
 */
@Repository
public class TeamQueries {

    /** Record subsets: prefix -> condition on team_match_result. */
    private static final Map<String, String> SUBSETS = Map.of(
            "all", "true", "bf", "t.batted_first", "ch", "t.chased", "home", "t.is_home", "away", "not t.is_home");

    static final String AGGREGATES = SUBSETS.entrySet().stream().map(s -> """
            count(*) filter (where %2$s) as %1$s_played,
            count(*) filter (where %2$s and t.won) as %1$s_won,
            count(*) filter (where %2$s and t.lost) as %1$s_lost,
            count(*) filter (where %2$s and t.tied) as %1$s_tied,
            count(*) filter (where %2$s and t.no_result) as %1$s_no_result""".formatted(s.getKey(), s.getValue()))
            .collect(Collectors.joining(",\n")) + """
            ,
            coalesce(sum(t.runs_for), 0) as runs_for, count(t.runs_for) as innings_batted,
            coalesce(sum(t.balls_faced), 0) as balls_faced,
            coalesce(sum(t.runs_against), 0) as runs_against, count(t.runs_against) as innings_bowled,
            coalesce(sum(t.balls_bowled), 0) as balls_bowled""";

    private final NamedParameterJdbcTemplate jdbc;

    public TeamQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public TeamStats summary(int teamId, AnalyticsFilter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = "select " + AGGREGATES + " from analytics.team_match_result t where true"
                + filter.team(teamId).conditions("t", false, params);
        return jdbc.queryForObject(sql, params, (rs, n) -> map(rs));
    }

    public List<SeasonTeamStats> bySeason(int teamId, AnalyticsFilter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = "select t.season_year, " + AGGREGATES + " from analytics.team_match_result t where true"
                + filter.team(teamId).conditions("t", false, params) + " group by t.season_year order by t.season_year";
        return jdbc.query(sql, params, (rs, n) -> new SeasonTeamStats(rs.getInt("season_year"), map(rs)));
    }

    static TeamStats map(ResultSet rs) throws SQLException {
        long runsFor = rs.getLong("runs_for");
        long runsAgainst = rs.getLong("runs_against");
        return new TeamStats(record(rs, "all"), record(rs, "bf"), record(rs, "ch"), record(rs, "home"),
                record(rs, "away"), runsFor, rs.getLong("innings_batted"),
                CricketMetrics.mean(runsFor, rs.getLong("innings_batted")), runsAgainst, rs.getLong("innings_bowled"),
                CricketMetrics.mean(runsAgainst, rs.getLong("innings_bowled")),
                CricketMetrics.runRate(runsFor, rs.getLong("balls_faced")),
                CricketMetrics.runRate(runsAgainst, rs.getLong("balls_bowled")));
    }

    private static Record record(ResultSet rs, String prefix) throws SQLException {
        long played = rs.getLong(prefix + "_played");
        long won = rs.getLong(prefix + "_won");
        long noResult = rs.getLong(prefix + "_no_result");
        return new Record(played, won, rs.getLong(prefix + "_lost"), rs.getLong(prefix + "_tied"), noResult,
                CricketMetrics.winPercentage(won, played, noResult));
    }

    /** {@code tied} counts tied matches; their super-over outcome is also counted in {@code won}/{@code lost}. */
    public record Record(long played, long won, long lost, long tied, long noResult, BigDecimal winPercentage) {
    }

    /**
     * @param averageScore    runs per innings batted (super overs excluded)
     * @param averageConceded runs conceded per innings bowled
     */
    public record TeamStats(Record overall, Record battingFirst, Record chasing, Record home, Record away,
                            long runsFor, long inningsBatted, BigDecimal averageScore, long runsAgainst,
                            long inningsBowled, BigDecimal averageConceded, BigDecimal runRate,
                            BigDecimal opponentRunRate) {
    }

    public record SeasonTeamStats(int seasonYear, TeamStats stats) {
    }
}
