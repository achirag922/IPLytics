package com.iplytics.player.repository;

import com.iplytics.player.api.PlayerDtos.PlayerAliasDto;
import com.iplytics.player.api.PlayerDtos.PlayerSummaryDto;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Player reference data: paged search (display name or any alias), detail, aliases and franchises. */
@Repository
public class PlayerRepository {

    private static final String SUMMARY = """
            select p.id, p.display_name, p.cricsheet_id, coalesce(pm.matches, 0) as matches,
                   pm.first_season, pm.last_season
              from core.player p
              left join (select player_id, count(*) as matches, min(season_year) as first_season,
                                max(season_year) as last_season
                           from analytics.player_match group by player_id) pm on pm.player_id = p.id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public PlayerRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<PlayerSummaryDto> find(int id) {
        return jdbc.query(SUMMARY + " where p.id = :id", new MapSqlParameterSource("id", id), (rs, n) -> summary(rs))
                .stream().findFirst();
    }

    public List<PlayerAliasDto> aliases(int id) {
        return jdbc.query("""
                select name, first_season, last_season from core.player_alias where player_id = :id
                 order by first_season, name""", new MapSqlParameterSource("id", id),
                (rs, n) -> new PlayerAliasDto(rs.getString("name"), rs.getInt("first_season"), rs.getInt("last_season")));
    }

    /** Franchises the player appeared for: (team id, matches, first season, last season). */
    public List<TeamSpell> teams(int id) {
        return jdbc.query("""
                select team_id, count(*) as matches, min(season_year) as first_season, max(season_year) as last_season
                  from analytics.player_match where player_id = :id
                 group by team_id order by min(season_year), team_id""", new MapSqlParameterSource("id", id),
                (rs, n) -> new TeamSpell(rs.getInt("team_id"), rs.getLong("matches"), rs.getInt("first_season"),
                        rs.getInt("last_season")));
    }

    public record TeamSpell(int teamId, long matches, int firstSeason, int lastSeason) {
    }

    private static PlayerSummaryDto summary(ResultSet rs) throws SQLException {
        return new PlayerSummaryDto(rs.getInt("id"), rs.getString("display_name"), rs.getString("cricsheet_id"),
                rs.getLong("matches"), (Integer) rs.getObject("first_season"), (Integer) rs.getObject("last_season"));
    }
}
