package com.iplytics.venue.repository;

import com.iplytics.venue.api.VenueDtos.VenueDto;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Venues with their source spellings and match span (two queries). */
@Repository
public class VenueRepository {

    private static final String VENUES = """
            select v.id, v.name, v.city, v.is_curated, count(m.id) as matches,
                   min(s.year) as first_season, max(s.year) as last_season
              from core.venue v
              left join core.match m on m.venue_id = v.id
              left join core.season s on s.id = m.season_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public VenueRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<VenueDto> findAll() {
        return query(VENUES + " group by v.id order by count(m.id) desc, v.name", new MapSqlParameterSource());
    }

    public Optional<VenueDto> find(int id) {
        return query(VENUES + " where v.id = :id group by v.id", new MapSqlParameterSource("id", id)).stream().findFirst();
    }

    private List<VenueDto> query(String sql, MapSqlParameterSource params) {
        Map<Integer, List<String>> aliases = new HashMap<>();
        jdbc.query("select venue_id, alias from core.venue_alias order by alias", rs -> {
            aliases.computeIfAbsent(rs.getInt("venue_id"), k -> new ArrayList<>()).add(rs.getString("alias"));
        });
        return jdbc.query(sql, params, (rs, n) -> new VenueDto(rs.getInt("id"), rs.getString("name"),
                rs.getString("city"), rs.getBoolean("is_curated"), rs.getLong("matches"),
                (Integer) rs.getObject("first_season"), (Integer) rs.getObject("last_season"),
                aliases.getOrDefault(rs.getInt("id"), List.of())));
    }
}
