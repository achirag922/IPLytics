package com.iplytics.overview;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Data-set counts in one query. */
@Repository
public class OverviewRepository {

    private final JdbcTemplate jdbc;

    public OverviewRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public OverviewDto overview() {
        return jdbc.queryForObject("""
                select (select count(distinct m.season_id) from core.match m) as seasons,
                       (select min(s.year) from core.season s where exists
                            (select 1 from core.match m where m.season_id = s.id)) as first_season,
                       (select max(s.year) from core.season s where exists
                            (select 1 from core.match m where m.season_id = s.id)) as last_season,
                       (select count(*) from core.match) as matches,
                       (select count(*) from core.team t where exists
                            (select 1 from core.match m where t.id in (m.team1_id, m.team2_id))) as teams,
                       (select count(distinct mp.player_id) from core.match_player mp) as players,
                       (select count(distinct m.venue_id) from core.match m) as venues,
                       (select count(*) from core.delivery) as deliveries""",
                (rs, n) -> new OverviewDto(rs.getLong("seasons"), (Integer) rs.getObject("first_season"),
                        (Integer) rs.getObject("last_season"), rs.getLong("matches"), rs.getLong("teams"),
                        rs.getLong("players"), rs.getLong("venues"), rs.getLong("deliveries")));
    }
}
