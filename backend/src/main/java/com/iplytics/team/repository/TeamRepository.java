package com.iplytics.team.repository;

import com.iplytics.team.api.TeamDtos.TeamDto;
import com.iplytics.team.api.TeamDtos.TeamNameDto;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Franchises with their historical names and match span (two queries, regardless of the number of teams). */
@Repository
public class TeamRepository {

    private static final String TEAMS = """
            select t.id, t.code, t.name, t.is_active, t.home_city, count(m.id) as matches,
                   min(s.year) as first_season, max(s.year) as last_season
              from core.team t
              left join core.match m on t.id in (m.team1_id, m.team2_id)
              left join core.season s on s.id = m.season_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public TeamRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<TeamDto> findAll() {
        return query(TEAMS + " group by t.id order by t.name", new MapSqlParameterSource());
    }

    public Optional<TeamDto> find(int id) {
        return query(TEAMS + " where t.id = :id group by t.id", new MapSqlParameterSource("id", id)).stream().findFirst();
    }

    private List<TeamDto> query(String sql, MapSqlParameterSource params) {
        Map<Integer, List<TeamNameDto>> names = new HashMap<>();
        jdbc.query("select team_id, name, first_season, last_season from core.team_name_history order by first_season, name",
                rs -> {
                    names.computeIfAbsent(rs.getInt("team_id"), k -> new ArrayList<>()).add(new TeamNameDto(
                            rs.getString("name"), rs.getInt("first_season"), (Integer) rs.getObject("last_season")));
                });
        return jdbc.query(sql, params, (rs, n) -> new TeamDto(rs.getInt("id"), rs.getString("code"),
                rs.getString("name"), rs.getBoolean("is_active"), rs.getString("home_city"),
                (Integer) rs.getObject("first_season"), (Integer) rs.getObject("last_season"), rs.getLong("matches"),
                names.getOrDefault(rs.getInt("id"), List.of())));
    }
}
