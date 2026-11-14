package com.iplytics.season.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Seasons with match counts and the Final's winner (one query). */
@Repository
public class SeasonRepository {

    private static final String SEASONS = """
            select s.year, s.name, s.source_season, count(m.id) as matches,
                   max(m.winner_team_id) filter (where m.stage = 'Final') as champion_id
              from core.season s left join core.match m on m.season_id = s.id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public SeasonRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SeasonRow> findAll() {
        return jdbc.query(SEASONS + " group by s.id order by s.year", new MapSqlParameterSource(), (rs, n) -> row(rs));
    }

    public Optional<SeasonRow> find(int year) {
        return jdbc.query(SEASONS + " where s.year = :year group by s.id", new MapSqlParameterSource("year", year),
                (rs, n) -> row(rs)).stream().findFirst();
    }

    private static SeasonRow row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new SeasonRow(rs.getInt("year"), rs.getString("name"), rs.getString("source_season"),
                rs.getLong("matches"), (Integer) rs.getObject("champion_id"));
    }

    public record SeasonRow(int year, String name, String sourceSeason, long matches, Integer championId) {
    }
}
