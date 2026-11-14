package com.iplytics.analytics.query;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Matches played, from {@code analytics.player_match} (the playing list, so it includes players who neither
 * batted nor bowled). Super overs are irrelevant here: they belong to a match the player already played.
 */
@Repository
public class ParticipationQueries {

    private final NamedParameterJdbcTemplate jdbc;

    public ParticipationQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long matches(int playerId, AnalyticsFilter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource("playerId", playerId);
        Long count = jdbc.queryForObject("select count(*) from analytics.player_match p where p.player_id = :playerId"
                + filter.conditions("p", false, params), params, Long.class);
        return count == null ? 0 : count;
    }

    /** Matches per player for several players in one query; players with none are absent. */
    public Map<Integer, Long> matchesByPlayer(Collection<Integer> playerIds, AnalyticsFilter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource("playerIds", playerIds);
        Map<Integer, Long> result = new HashMap<>();
        jdbc.query("select p.player_id, count(*) as matches from analytics.player_match p where p.player_id in (:playerIds)"
                + filter.conditions("p", false, params) + " group by p.player_id", params,
                rs -> {
                    result.put(rs.getInt("player_id"), rs.getLong("matches"));
                });
        return result;
    }

    /** Matches per season / opponent / venue for one player. */
    public Map<Integer, Long> matchesBy(int playerId, AnalyticsFilter filter, Grouping grouping) {
        MapSqlParameterSource params = new MapSqlParameterSource("playerId", playerId);
        Map<Integer, Long> result = new HashMap<>();
        jdbc.query("select p." + grouping.column + " as group_key, count(*) as matches from analytics.player_match p"
                + " where p.player_id = :playerId" + filter.conditions("p", false, params)
                + " group by p." + grouping.column, params, rs -> {
                    result.put(rs.getInt("group_key"), rs.getLong("matches"));
                });
        return result;
    }
}
