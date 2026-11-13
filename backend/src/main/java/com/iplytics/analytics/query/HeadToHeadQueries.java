package com.iplytics.analytics.query;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Team A vs team B from {@code analytics.team_match_result}, seen from team A's side: overall, by season and by
 * venue, with batting-first / chasing splits. Franchise ids, so renamed teams are one side.
 */
@Repository
public class HeadToHeadQueries {

    private static final String AGGREGATES = """
            count(*) as matches,
            count(*) filter (where t.won) as team_a_wins,
            count(*) filter (where t.lost) as team_b_wins,
            count(*) filter (where t.tied) as ties,
            count(*) filter (where t.no_result) as no_results,
            count(*) filter (where t.won and t.batted_first) as team_a_wins_batting_first,
            count(*) filter (where t.won and t.chased) as team_a_wins_chasing,
            count(*) filter (where t.lost and t.chased) as team_b_wins_batting_first,
            count(*) filter (where t.lost and t.batted_first) as team_b_wins_chasing""";

    private final NamedParameterJdbcTemplate jdbc;

    public HeadToHeadQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public HeadToHead overall(int teamA, int teamB, AnalyticsFilter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        return jdbc.queryForObject("select " + AGGREGATES + from(teamA, teamB, filter, params), params,
                (rs, n) -> map(rs));
    }

    public List<Grouped> bySeason(int teamA, int teamB, AnalyticsFilter filter) {
        return grouped("t.season_year", teamA, teamB, filter);
    }

    public List<Grouped> byVenue(int teamA, int teamB, AnalyticsFilter filter) {
        return grouped("t.venue_id", teamA, teamB, filter);
    }

    private List<Grouped> grouped(String column, int teamA, int teamB, AnalyticsFilter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = "select " + column + " as group_key, " + AGGREGATES + from(teamA, teamB, filter, params)
                + " group by " + column + " order by " + column;
        return jdbc.query(sql, params, (rs, n) -> new Grouped(rs.getInt("group_key"), map(rs)));
    }

    private static String from(int teamA, int teamB, AnalyticsFilter filter, MapSqlParameterSource params) {
        return " from analytics.team_match_result t where true"
                + filter.team(teamA).opponent(teamB).conditions("t", false, params);
    }

    private static HeadToHead map(ResultSet rs) throws SQLException {
        return new HeadToHead(rs.getLong("matches"), rs.getLong("team_a_wins"), rs.getLong("team_b_wins"),
                rs.getLong("ties"), rs.getLong("no_results"), rs.getLong("team_a_wins_batting_first"),
                rs.getLong("team_a_wins_chasing"), rs.getLong("team_b_wins_batting_first"),
                rs.getLong("team_b_wins_chasing"));
    }

    /** Wins include ties decided by a super over; {@code ties} counts tied matches. */
    public record HeadToHead(long matches, long teamAWins, long teamBWins, long ties, long noResults,
                             long teamAWinsBattingFirst, long teamAWinsChasing, long teamBWinsBattingFirst,
                             long teamBWinsChasing) {
    }

    /** @param key season year or venue id */
    public record Grouped(int key, HeadToHead headToHead) {
    }
}
