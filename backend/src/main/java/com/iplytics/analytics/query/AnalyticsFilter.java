package com.iplytics.analytics.query;

import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/**
 * Common filters for analytics queries. Every field is optional. Super overs are EXCLUDED unless
 * {@link #withSuperOvers()} is requested (docs/metrics.md section 1).
 *
 * @param seasonFrom     first IPL season year (inclusive)
 * @param seasonTo       last IPL season year (inclusive)
 * @param teamId         the player's / team's own franchise
 * @param opponentTeamId the opposing franchise
 * @param venueId        canonical venue
 */
public record AnalyticsFilter(Integer seasonFrom, Integer seasonTo, Integer teamId, Integer opponentTeamId,
                              Integer venueId, boolean includeSuperOvers) {

    public static AnalyticsFilter all() {
        return new AnalyticsFilter(null, null, null, null, null, false);
    }

    public AnalyticsFilter seasons(Integer from, Integer to) {
        return new AnalyticsFilter(from, to, teamId, opponentTeamId, venueId, includeSuperOvers);
    }

    public AnalyticsFilter season(int year) {
        return seasons(year, year);
    }

    public AnalyticsFilter team(Integer id) {
        return new AnalyticsFilter(seasonFrom, seasonTo, id, opponentTeamId, venueId, includeSuperOvers);
    }

    public AnalyticsFilter opponent(Integer id) {
        return new AnalyticsFilter(seasonFrom, seasonTo, teamId, id, venueId, includeSuperOvers);
    }

    public AnalyticsFilter venue(Integer id) {
        return new AnalyticsFilter(seasonFrom, seasonTo, teamId, opponentTeamId, id, includeSuperOvers);
    }

    public AnalyticsFilter withSuperOvers() {
        return new AnalyticsFilter(seasonFrom, seasonTo, teamId, opponentTeamId, venueId, true);
    }

    /**
     * SQL conditions (joined with {@code and}, starting with {@code and}) for a relation exposing the standard
     * analytics columns {@code season_year, team_id, opponent_team_id, venue_id} and, if {@code hasSuperOverFlag},
     * {@code is_super_over}. Values are bound as parameters, never concatenated.
     */
    String conditions(String alias, boolean hasSuperOverFlag, MapSqlParameterSource params) {
        List<String> conditions = new ArrayList<>();
        add(conditions, params, alias + ".season_year >= :seasonFrom", "seasonFrom", seasonFrom);
        add(conditions, params, alias + ".season_year <= :seasonTo", "seasonTo", seasonTo);
        add(conditions, params, alias + ".team_id = :teamId", "teamId", teamId);
        add(conditions, params, alias + ".opponent_team_id = :opponentTeamId", "opponentTeamId", opponentTeamId);
        add(conditions, params, alias + ".venue_id = :venueId", "venueId", venueId);
        if (hasSuperOverFlag && !includeSuperOvers) {
            conditions.add("not " + alias + ".is_super_over");
        }
        return conditions.stream().map(c -> " and " + c).reduce("", String::concat);
    }

    private static void add(List<String> conditions, MapSqlParameterSource params, String condition, String name,
                            Integer value) {
        if (value != null) {
            conditions.add(condition);
            params.addValue(name, value);
        }
    }
}
