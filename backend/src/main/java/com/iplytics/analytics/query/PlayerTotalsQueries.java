package com.iplytics.analytics.query;

import com.iplytics.common.web.PageSlice;
import com.iplytics.common.web.Pagination;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Paged player list with batting and bowling totals inside a scope (super overs excluded). */
@Repository
public class PlayerTotalsQueries {

    /** Which players to keep. */
    public enum Requires { ANY, BATTED, BOWLED }

    /** Fixed orders; every order ends with name and id so pages are stable. */
    public enum Order {
        NAME("r.display_name, r.id"),
        MATCHES("r.matches desc, r.display_name, r.id"),
        RUNS("r.runs desc, r.display_name, r.id"),
        WICKETS("r.wickets desc, r.display_name, r.id");

        private final String sql;

        Order(String sql) {
            this.sql = sql;
        }
    }

    private final NamedParameterJdbcTemplate jdbc;

    public PlayerTotalsQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Players who played inside the scope, with their batting and bowling totals there. One query; its window count
     * gives the total. Search matches the display name or any alias, with wildcards matched literally.
     */
    public PageSlice<PlayerTotalsRow> list(String term, AnalyticsFilter filter, Requires requires, Order order,
                                           int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", size).addValue("offset", Pagination.offset(page, size));
        String rows = """
                with pm as (
                    select x.player_id, count(*) as matches, min(x.season_year) as first_season,
                           max(x.season_year) as last_season,
                           (array_agg(x.team_id order by x.match_date desc, x.match_id desc))[1] as team_id
                      from analytics.player_match x where true%s
                     group by x.player_id),
                bat as (
                    select b.player_id, count(*) as innings, sum(b.runs) as runs, sum(b.balls) as balls,
                           count(*) filter (where b.is_out) as dismissals
                      from analytics.batting_innings b where true%s
                     group by b.player_id),
                bowl as (
                    select w.player_id, count(*) as innings, sum(w.legal_balls) as legal_balls,
                           sum(w.runs_conceded) as runs_conceded, sum(w.wickets) as wickets
                      from analytics.bowling_innings w where true%s
                     group by w.player_id),
                rows as (
                    select p.id, p.display_name, p.cricsheet_id, pm.matches, pm.first_season, pm.last_season, pm.team_id,
                           coalesce(bat.innings, 0) as batting_innings, coalesce(bat.runs, 0) as runs,
                           coalesce(bat.balls, 0) as balls, coalesce(bat.dismissals, 0) as dismissals,
                           coalesce(bowl.innings, 0) as bowling_innings, coalesce(bowl.legal_balls, 0) as legal_balls,
                           coalesce(bowl.runs_conceded, 0) as runs_conceded, coalesce(bowl.wickets, 0) as wickets
                      from core.player p
                      join pm on pm.player_id = p.id
                      left join bat on bat.player_id = p.id
                      left join bowl on bowl.player_id = p.id
                     where true%s)
                """.formatted(filter.conditions("x", false, params), filter.conditions("b", true, params),
                filter.conditions("w", true, params), scope(term, requires, params));
        List<PlayerTotalsRow> result = new ArrayList<>();
        long[] total = {0};
        jdbc.query(rows + "select r.*, count(*) over () as total from rows r order by " + order.sql
                + " limit :limit offset :offset", params, rs -> {
                    total[0] = rs.getLong("total");
                    result.add(totals(rs));
                });
        if (result.isEmpty() && page > 0) {
            Long count = jdbc.queryForObject(rows + "select count(*) from rows", params, Long.class);
            total[0] = count == null ? 0 : count;
        }
        return new PageSlice<>(result, total[0]);
    }

    private static String scope(String term, Requires requires, MapSqlParameterSource params) {
        StringBuilder sql = new StringBuilder();
        if (term != null && !term.isBlank()) {
            params.addValue("pattern", "%" + escapeLike(term.trim()) + "%");
            sql.append(" and (p.display_name ilike :pattern escape '\\'")
                    .append(" or exists (select 1 from core.player_alias a where a.player_id = p.id")
                    .append(" and a.name ilike :pattern escape '\\'))");
        }
        if (requires == Requires.BATTED) {
            sql.append(" and bat.innings > 0");
        } else if (requires == Requires.BOWLED) {
            sql.append(" and bowl.innings > 0");
        }
        return sql.toString();
    }

    private static PlayerTotalsRow totals(ResultSet rs) throws SQLException {
        return new PlayerTotalsRow(rs.getInt("id"), rs.getString("display_name"), rs.getString("cricsheet_id"),
                (Integer) rs.getObject("team_id"), rs.getLong("matches"), (Integer) rs.getObject("first_season"),
                (Integer) rs.getObject("last_season"), rs.getLong("batting_innings"), rs.getLong("runs"),
                rs.getLong("balls"), rs.getLong("dismissals"), rs.getLong("bowling_innings"),
                rs.getLong("legal_balls"), rs.getLong("runs_conceded"), rs.getLong("wickets"));
    }

    /** Raw totals; ratios are computed by callers with CricketMetrics. */
    public record PlayerTotalsRow(int id, String name, String sourceId, Integer teamId, long matches,
                                  Integer firstSeason, Integer lastSeason, long battingInnings, long runs, long balls,
                                  long dismissals, long bowlingInnings, long legalBalls, long runsConceded,
                                  long wickets) {
    }

    static String escapeLike(String term) {
        return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
