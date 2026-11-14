package com.iplytics.match.repository;

import com.iplytics.common.web.PageSlice;
import com.iplytics.common.web.Pagination;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Match reads. Lists are paged in SQL; a scorecard is a fixed set of queries (match, innings, extras, batting,
 * fielders, bowling, fall of wickets, did-not-bat) whatever the number of players. Batting and bowling figures
 * come from the V1.3 analytics views.
 */
@Repository
public class MatchRepository {

    private static final String MATCH_COLUMNS = """
            select m.id, m.source_match_id, s.year as season, m.match_date, m.end_date, m.stage, m.match_number,
                   m.venue_id, m.city, m.team1_id, m.team1_name, m.team2_id, m.team2_name, m.toss_winner_team_id,
                   m.toss_decision, m.winner_team_id, m.super_over_winner_team_id, m.result, m.result_margin, m.method,
                   m.has_super_over, m.player_of_match_id, pom.display_name as player_of_match_name, m.target_runs,
                   m.target_overs, m.scheduled_overs, m.umpires, m.tv_umpires, m.match_referees, m.reserve_umpires
              from core.match m
              join core.season s on s.id = m.season_id
              left join core.player pom on pom.id = m.player_of_match_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public MatchRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Newest first. */
    public PageSlice<MatchRow> find(MatchCriteria criteria, int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = where(criteria, params);
        params.addValue("limit", size).addValue("offset", Pagination.offset(page, size));
        List<MatchRow> rows = jdbc.query(MATCH_COLUMNS + where + " order by m.match_date desc, m.id desc limit :limit offset :offset",
                params, (rs, n) -> matchRow(rs));
        Long total = jdbc.queryForObject("select count(*) from core.match m join core.season s on s.id = m.season_id"
                + where, params, Long.class);
        return new PageSlice<>(rows, total == null ? 0 : total);
    }

    public Optional<MatchRow> find(int id) {
        return jdbc.query(MATCH_COLUMNS + " where m.id = :id", new MapSqlParameterSource("id", id),
                (rs, n) -> matchRow(rs)).stream().findFirst();
    }

    /** Innings scores of the given matches in one query (super overs included, flagged). */
    public List<InningsRow> innings(Collection<Integer> matchIds) {
        if (matchIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                select s.match_id, s.innings_id, s.innings_number, s.is_super_over, s.batting_team_id, s.bowling_team_id,
                       s.total_runs, s.wickets, s.legal_balls, s.run_rate, s.extras
                  from analytics.innings_summary s where s.match_id in (:ids)
                 order by s.match_id, s.innings_number""", new MapSqlParameterSource("ids", matchIds),
                (rs, n) -> new InningsRow(rs.getInt("match_id"), rs.getInt("innings_id"), rs.getInt("innings_number"),
                        rs.getBoolean("is_super_over"), rs.getInt("batting_team_id"), rs.getInt("bowling_team_id"),
                        rs.getInt("total_runs"), rs.getInt("wickets"), rs.getInt("legal_balls"),
                        rs.getBigDecimal("run_rate"), rs.getInt("extras")));
    }

    public List<ExtrasRow> extras(int matchId) {
        return jdbc.query("""
                select d.innings_id, sum(d.wides) as wides, sum(d.noballs) as noballs, sum(d.byes) as byes,
                       sum(d.legbyes) as legbyes, sum(d.penalty) as penalty
                  from core.delivery d join core.innings i on i.id = d.innings_id
                 where i.match_id = :id group by d.innings_id""", new MapSqlParameterSource("id", matchId),
                (rs, n) -> new ExtrasRow(rs.getInt("innings_id"), rs.getInt("wides"), rs.getInt("noballs"),
                        rs.getInt("byes"), rs.getInt("legbyes"), rs.getInt("penalty")));
    }

    /** Batting order from analytics.batting_innings with the dismissing bowler, if any. */
    public List<BattingRow> batting(int matchId) {
        return jdbc.query("""
                select b.innings_id, b.batting_position, b.player_id, p.display_name, b.runs, b.balls, b.fours, b.sixes,
                       b.strike_rate, b.is_out, b.dismissal_kind, b.dismissal_wicket_id, bd.bowler_id,
                       bp.display_name as bowler_name
                  from analytics.batting_innings b
                  join core.player p on p.id = b.player_id
                  left join core.wicket w on w.id = b.dismissal_wicket_id
                  left join core.delivery bd on bd.id = w.delivery_id
                  left join core.player bp on bp.id = bd.bowler_id
                 where b.match_id = :id order by b.innings_number, b.batting_position""",
                new MapSqlParameterSource("id", matchId), (rs, n) -> new BattingRow(rs.getInt("innings_id"),
                        rs.getInt("batting_position"), rs.getInt("player_id"), rs.getString("display_name"),
                        rs.getInt("runs"), rs.getInt("balls"), rs.getInt("fours"), rs.getInt("sixes"),
                        rs.getBigDecimal("strike_rate"), rs.getBoolean("is_out"), rs.getString("dismissal_kind"),
                        (Long) rs.getObject("dismissal_wicket_id"), (Integer) rs.getObject("bowler_id"),
                        rs.getString("bowler_name")));
    }

    /** Fielders of every dismissal in the match. */
    public List<FielderRow> fielders(int matchId) {
        return jdbc.query("""
                select f.wicket_id, f.fielder_number, f.is_substitute, f.player_id, p.display_name
                  from core.wicket_fielder f
                  join core.player p on p.id = f.player_id
                  join core.wicket w on w.id = f.wicket_id
                  join core.delivery d on d.id = w.delivery_id
                  join core.innings i on i.id = d.innings_id
                 where i.match_id = :id order by f.wicket_id, f.fielder_number""",
                new MapSqlParameterSource("id", matchId), (rs, n) -> new FielderRow(rs.getLong("wicket_id"),
                        rs.getInt("player_id"), rs.getString("display_name"), rs.getBoolean("is_substitute")));
    }

    /** Bowlers in the order they first bowled, from analytics.bowling_innings. */
    public List<BowlingRow> bowling(int matchId) {
        return jdbc.query("""
                with overs as (
                    select d.innings_id, d.over_number, min(d.bowler_id) as bowler_id
                      from core.delivery d join core.innings i on i.id = d.innings_id
                     where i.match_id = :id
                     group by d.innings_id, d.over_number
                    having count(distinct d.bowler_id) = 1 and count(*) filter (where d.is_legal_ball) >= 6
                       and sum(analytics.bowler_runs(d.total_runs, d.byes, d.legbyes, d.penalty)) = 0),
                maidens as (select innings_id, bowler_id, count(*) as maidens from overs group by innings_id, bowler_id)
                select b.innings_id, b.player_id, p.display_name, b.legal_balls, b.runs_conceded, b.wickets, b.economy,
                       b.dot_balls, b.wides, b.no_balls, b.fours_conceded, b.sixes_conceded,
                       coalesce(m.maidens, 0) as maidens
                  from analytics.bowling_innings b join core.player p on p.id = b.player_id
                  left join maidens m on m.innings_id = b.innings_id and m.bowler_id = b.player_id
                 where b.match_id = :id
                 order by b.innings_number,
                          (select min(d.over_number * 1000 + d.ball_in_over) from core.delivery d
                            where d.innings_id = b.innings_id and d.bowler_id = b.player_id)""",
                new MapSqlParameterSource("id", matchId), (rs, n) -> new BowlingRow(rs.getInt("innings_id"),
                        rs.getInt("player_id"), rs.getString("display_name"), rs.getInt("legal_balls"),
                        rs.getInt("runs_conceded"), rs.getInt("wickets"), rs.getBigDecimal("economy"),
                        rs.getInt("dot_balls"), rs.getInt("wides"), rs.getInt("no_balls"), rs.getInt("fours_conceded"),
                        rs.getInt("sixes_conceded"), rs.getInt("maidens")));
    }

    /** Score and over (overs.legal balls, as on a scorecard) at each dismissal; retirements are not dismissals. */
    public List<FallOfWicketRow> fallOfWickets(int matchId) {
        return jdbc.query("""
                with balls as (
                    select d.id, d.innings_id, d.over_number, d.ball_in_over,
                           sum(d.total_runs) over (partition by d.innings_id order by d.over_number, d.ball_in_over) as score,
                           count(*) filter (where d.is_legal_ball)
                               over (partition by d.innings_id, d.over_number order by d.ball_in_over) as legal_in_over
                      from core.delivery d join core.innings i on i.id = d.innings_id where i.match_id = :id)
                select b.innings_id, b.score, b.over_number || '.' || b.legal_in_over as ball,
                       w.player_out_id, p.display_name,
                       row_number() over (partition by b.innings_id order by b.over_number, b.ball_in_over, w.wicket_number)
                           as wicket_number
                  from balls b join core.wicket w on w.delivery_id = b.id join core.player p on p.id = w.player_out_id
                 where analytics.is_dismissal(w.kind)
                 order by b.innings_id, wicket_number""", new MapSqlParameterSource("id", matchId),
                (rs, n) -> new FallOfWicketRow(rs.getInt("innings_id"), rs.getInt("wicket_number"), rs.getInt("score"),
                        rs.getString("ball"), rs.getInt("player_out_id"), rs.getString("display_name")));
    }

    /** Playing-list members who did not bat in their side's regular innings. */
    public List<DidNotBatRow> didNotBat(int matchId) {
        return jdbc.query("""
                select mp.team_id, mp.player_id, p.display_name
                  from core.match_player mp join core.player p on p.id = mp.player_id
                 where mp.match_id = :id
                   and not exists (select 1 from analytics.batting_innings b
                                    where b.match_id = mp.match_id and b.player_id = mp.player_id and not b.is_super_over)
                 order by mp.team_id, mp.list_position""", new MapSqlParameterSource("id", matchId),
                (rs, n) -> new DidNotBatRow(rs.getInt("team_id"), rs.getInt("player_id"), rs.getString("display_name")));
    }

    /** Runs, extras, legal balls and wickets per over with running totals (worm / Manhattan data). */
    public List<OverRow> progression(int matchId) {
        return jdbc.query("""
                with overs as (
                    select i.id as innings_id, i.innings_number, i.is_super_over, i.batting_team_id, d.over_number,
                           sum(d.total_runs) as runs, sum(d.extra_runs) as extras,
                           count(*) filter (where d.is_legal_ball) as legal_balls
                      from core.delivery d join core.innings i on i.id = d.innings_id
                     where i.match_id = :id
                     group by i.id, i.innings_number, i.is_super_over, i.batting_team_id, d.over_number),
                falls as (
                    select d.innings_id, d.over_number, count(*) as wickets
                      from core.wicket w join core.delivery d on d.id = w.delivery_id
                      join core.innings i on i.id = d.innings_id
                     where i.match_id = :id and analytics.is_dismissal(w.kind)
                     group by d.innings_id, d.over_number)
                select o.innings_number, o.is_super_over, o.batting_team_id, o.over_number, o.runs, o.extras,
                       o.legal_balls, coalesce(f.wickets, 0) as wickets,
                       sum(o.runs) over w as cumulative_runs,
                       sum(coalesce(f.wickets, 0)) over w as cumulative_wickets,
                       sum(o.legal_balls) over w as cumulative_legal_balls
                  from overs o left join falls f on f.innings_id = o.innings_id and f.over_number = o.over_number
                window w as (partition by o.innings_id order by o.over_number)
                 order by o.innings_number, o.over_number""", new MapSqlParameterSource("id", matchId),
                (rs, n) -> new OverRow(rs.getInt("innings_number"), rs.getBoolean("is_super_over"),
                        rs.getInt("batting_team_id"), rs.getInt("over_number"), rs.getInt("runs"), rs.getInt("extras"),
                        rs.getInt("legal_balls"), rs.getInt("wickets"), rs.getInt("cumulative_runs"),
                        rs.getInt("cumulative_wickets"), rs.getInt("cumulative_legal_balls")));
    }

    private static String where(MatchCriteria c, MapSqlParameterSource params) {
        StringBuilder sql = new StringBuilder(" where true");
        add(sql, params, c.seasonFrom(), "s.year >= :seasonFrom", "seasonFrom");
        add(sql, params, c.seasonTo(), "s.year <= :seasonTo", "seasonTo");
        add(sql, params, c.team(), ":team in (m.team1_id, m.team2_id)", "team");
        add(sql, params, c.opponent(), ":opponent in (m.team1_id, m.team2_id)", "opponent");
        add(sql, params, c.venue(), "m.venue_id = :venue", "venue");
        add(sql, params, c.dateFrom(), "m.match_date >= :dateFrom", "dateFrom");
        add(sql, params, c.dateTo(), "m.match_date <= :dateTo", "dateTo");
        add(sql, params, c.stage(), "lower(m.stage) = lower(:stage)", "stage");
        return sql.toString();
    }

    private static void add(StringBuilder sql, MapSqlParameterSource params, Object value, String condition, String name) {
        if (value != null) {
            sql.append(" and ").append(condition);
            params.addValue(name, value);
        }
    }

    private static MatchRow matchRow(ResultSet rs) throws SQLException {
        return new MatchRow(rs.getInt("id"), rs.getLong("source_match_id"), rs.getInt("season"),
                rs.getObject("match_date", LocalDate.class), rs.getObject("end_date", LocalDate.class),
                rs.getString("stage"), (Integer) rs.getObject("match_number"), rs.getInt("venue_id"),
                rs.getString("city"), rs.getInt("team1_id"), rs.getString("team1_name"), rs.getInt("team2_id"),
                rs.getString("team2_name"), (Integer) rs.getObject("toss_winner_team_id"),
                rs.getString("toss_decision"), (Integer) rs.getObject("winner_team_id"),
                (Integer) rs.getObject("super_over_winner_team_id"), rs.getString("result"),
                (Integer) rs.getObject("result_margin"), rs.getString("method"), rs.getBoolean("has_super_over"),
                (Integer) rs.getObject("player_of_match_id"), rs.getString("player_of_match_name"),
                (Integer) rs.getObject("target_runs"), rs.getBigDecimal("target_overs"),
                (Integer) rs.getObject("scheduled_overs"), strings(rs.getArray("umpires")),
                strings(rs.getArray("tv_umpires")), strings(rs.getArray("match_referees")),
                strings(rs.getArray("reserve_umpires")));
    }

    private static List<String> strings(Array array) throws SQLException {
        return array == null ? List.of() : Arrays.asList((String[]) array.getArray());
    }

    public record MatchRow(int id, long sourceMatchId, int season, LocalDate date, LocalDate endDate, String stage,
                           Integer matchNumber, int venueId, String city, int team1Id, String team1Name, int team2Id,
                           String team2Name, Integer tossWinnerId, String tossDecision, Integer winnerId,
                           Integer superOverWinnerId, String result, Integer resultMargin, String method,
                           boolean superOver, Integer playerOfMatchId, String playerOfMatchName, Integer targetRuns,
                           BigDecimal targetOvers, Integer scheduledOvers, List<String> umpires,
                           List<String> tvUmpires, List<String> matchReferees, List<String> reserveUmpires) {
    }

    public record InningsRow(int matchId, int inningsId, int inningsNumber, boolean superOver, int battingTeamId,
                             int bowlingTeamId, int runs, int wickets, int legalBalls, BigDecimal runRate, int extras) {
    }

    public record ExtrasRow(int inningsId, int wides, int noBalls, int byes, int legByes, int penalty) {
    }

    public record BattingRow(int inningsId, int position, int playerId, String playerName, int runs, int balls,
                             int fours, int sixes, BigDecimal strikeRate, boolean out, String dismissalKind,
                             Long wicketId, Integer bowlerId, String bowlerName) {
    }

    public record FielderRow(long wicketId, int playerId, String playerName, boolean substitute) {
    }

    public record BowlingRow(int inningsId, int playerId, String playerName, int legalBalls, int runsConceded,
                             int wickets, BigDecimal economy, int dotBalls, int wides, int noBalls, int foursConceded,
                             int sixesConceded, int maidens) {
    }

    public record FallOfWicketRow(int inningsId, int wicket, int score, String ball, int playerId, String playerName) {
    }

    public record DidNotBatRow(int teamId, int playerId, String playerName) {
    }

    public record OverRow(int inningsNumber, boolean superOver, int teamId, int overNumber, int runs, int extras,
                          int legalBalls, int wickets, int cumulativeRuns, int cumulativeWickets,
                          int cumulativeLegalBalls) {
    }
}
