package com.iplytics.common.web;

import com.iplytics.common.exception.UnprocessableRequestException;
import com.iplytics.common.web.Refs.PlayerRef;
import com.iplytics.common.web.Refs.TeamRef;
import com.iplytics.common.web.Refs.VenueRef;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Bulk name lookups for embedding references (one query per kind, never one per row) and existence checks for
 * ids passed as filters (an unknown filter id is a 422).
 */
@Repository
public class RefLookup {

    private final NamedParameterJdbcTemplate jdbc;

    public RefLookup(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** All franchises (a few dozen rows at most). */
    public Map<Integer, TeamRef> teams() {
        Map<Integer, TeamRef> teams = new HashMap<>();
        jdbc.query("select id, code, name from core.team", rs -> {
            teams.put(rs.getInt("id"), new TeamRef(rs.getInt("id"), rs.getString("code"), rs.getString("name")));
        });
        return teams;
    }

    /** All venues (a few dozen rows). */
    public Map<Integer, VenueRef> venues() {
        Map<Integer, VenueRef> venues = new HashMap<>();
        jdbc.query("select id, name, city from core.venue", rs -> {
            venues.put(rs.getInt("id"), new VenueRef(rs.getInt("id"), rs.getString("name"), rs.getString("city")));
        });
        return venues;
    }

    public Map<Integer, PlayerRef> players(Collection<Integer> ids) {
        Map<Integer, PlayerRef> players = new HashMap<>();
        if (ids.isEmpty()) {
            return players;
        }
        jdbc.query("select id, display_name from core.player where id in (:ids)", new MapSqlParameterSource("ids", ids),
                rs -> {
                    players.put(rs.getInt("id"), new PlayerRef(rs.getInt("id"), rs.getString("display_name")));
                });
        return players;
    }

    public boolean teamExists(int id) {
        return exists("select exists (select 1 from core.team where id = :id)", id);
    }

    public boolean venueExists(int id) {
        return exists("select exists (select 1 from core.venue where id = :id)", id);
    }

    public boolean playerExists(int id) {
        return exists("select exists (select 1 from core.player where id = :id)", id);
    }

    /** @throws UnprocessableRequestException when a filter refers to a team that does not exist */
    public void requireTeamFilter(String parameter, Integer id) {
        if (id != null && !teamExists(id)) {
            throw new UnprocessableRequestException("Unknown team id %d in parameter '%s'".formatted(id, parameter));
        }
    }

    /** @throws UnprocessableRequestException when a filter refers to a venue that does not exist */
    public void requireVenueFilter(String parameter, Integer id) {
        if (id != null && !venueExists(id)) {
            throw new UnprocessableRequestException("Unknown venue id %d in parameter '%s'".formatted(id, parameter));
        }
    }

    private boolean exists(String sql, int id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, new MapSqlParameterSource("id", id), Boolean.class));
    }
}
