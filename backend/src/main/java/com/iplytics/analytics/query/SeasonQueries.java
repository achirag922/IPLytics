package com.iplytics.analytics.query;

import com.iplytics.common.metrics.CricketMetrics;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Season totals, extreme team scores and points-table inputs (docs/metrics.md sections 6 and 6b). */
@Repository
public class SeasonQueries {

    private static final String TEAM_SCORE = """
            select s.match_id, s.batting_team_id, s.bowling_team_id, s.total_runs, s.wickets, s.legal_balls, s.match_date
              from analytics.innings_summary s join core.match m on m.id = s.match_id
             where s.season_year = :year and not s.is_super_over""";

    /**
     * Net-run-rate inputs per team over LEAGUE matches with a result (docs/metrics.md 6b): a side bowled out is
     * charged its full allotted overs; in a D/L result the side batting first is credited with target - 1 runs off
     * the chasing side's allotted overs. No-results count as matches but not towards NRR; super overs never count.
     */
    private static final String POINTS_TABLE = """
            with lm as (
                select m.id, m.result, m.method, m.winner_team_id, m.team1_id, m.team2_id, m.target_runs, m.target_overs,
                       coalesce(m.target_overs, m.scheduled_overs, 20) as allotted_overs
                  from core.match m join core.season s on s.id = m.season_id
                 where s.year = :year and m.stage = 'League'),
            inn as (
                select s.match_id, s.innings_number, s.batting_team_id, s.total_runs, s.legal_balls, s.wickets
                  from analytics.innings_summary s join lm on lm.id = s.match_id
                 where not s.is_super_over and s.innings_number in (1, 2)),
            nrr as (
                select lm.id as match_id, i.batting_team_id as team_id,
                       case when i.innings_number = 1 and lm.method is not null and lm.target_runs is not null
                            then lm.target_runs - 1 else i.total_runs end as runs,
                       case when i.innings_number = 1 and lm.method is not null and lm.target_overs is not null
                            then analytics.overs_to_balls(lm.target_overs)
                            when i.wickets >= 10 then analytics.overs_to_balls(lm.allotted_overs)
                            else i.legal_balls end as balls
                  from lm join inn i on i.match_id = lm.id
                 where lm.result <> 'no result'
                   and exists (select 1 from inn i2 where i2.match_id = lm.id and i2.innings_number = 2)),
            sides as (
                select id as match_id, team1_id as team_id, team2_id as opponent_id, result, winner_team_id from lm
                union all
                select id, team2_id, team1_id, result, winner_team_id from lm)
            select sd.team_id, count(*) as matches,
                   count(*) filter (where sd.winner_team_id = sd.team_id) as wins,
                   count(*) filter (where sd.winner_team_id = sd.opponent_id) as losses,
                   count(*) filter (where sd.result = 'tie') as ties,
                   count(*) filter (where sd.result = 'no result') as no_results,
                   coalesce(sum(f.runs), 0) as runs_for, coalesce(sum(f.balls), 0) as balls_faced,
                   coalesce(sum(a.runs), 0) as runs_against, coalesce(sum(a.balls), 0) as balls_bowled
              from sides sd
              left join nrr f on f.match_id = sd.match_id and f.team_id = sd.team_id
              left join nrr a on a.match_id = sd.match_id and a.team_id = sd.opponent_id
             group by sd.team_id""";

    private final NamedParameterJdbcTemplate jdbc;

    public SeasonQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public SeasonTotals totals(int year) {
        MapSqlParameterSource params = new MapSqlParameterSource("year", year);
        return jdbc.queryForObject("""
                select count(distinct s.match_id) as matches, count(*) as innings,
                       coalesce(sum(s.total_runs), 0) as runs, coalesce(sum(s.wickets), 0) as wickets,
                       coalesce(sum(s.fours), 0) as fours, coalesce(sum(s.sixes), 0) as sixes,
                       coalesce(sum(s.legal_balls), 0) as legal_balls
                  from analytics.innings_summary s where s.season_year = :year and not s.is_super_over""", params,
                (rs, n) -> new SeasonTotals(rs.getLong("matches"), rs.getLong("innings"), rs.getLong("runs"),
                        rs.getLong("wickets"), rs.getLong("fours"), rs.getLong("sixes"),
                        CricketMetrics.mean(rs.getLong("runs"), rs.getLong("innings")),
                        CricketMetrics.runRate(rs.getLong("runs"), rs.getLong("legal_balls"))));
    }

    public Optional<TeamScore> highestTeamScore(int year) {
        return teamScore(TEAM_SCORE + " order by s.total_runs desc, s.match_date, s.match_id limit 1", year);
    }

    /** Lowest total of an innings that was all out or used its full scheduled overs. */
    public Optional<TeamScore> lowestCompletedTeamScore(int year) {
        return teamScore(TEAM_SCORE + " " + """
                 and (s.wickets >= 10 or s.legal_balls >= coalesce(m.scheduled_overs, 20) * 6)
                 order by s.total_runs, s.match_date, s.match_id limit 1""", year);
    }

    /** Winner of the season's Final, if there was one with a result. */
    public Optional<Integer> champion(int year) {
        return jdbc.query("""
                select m.winner_team_id from core.match m join core.season s on s.id = m.season_id
                 where s.year = :year and m.stage = 'Final' and m.winner_team_id is not null""",
                new MapSqlParameterSource("year", year), (rs, n) -> rs.getInt(1)).stream().findFirst();
    }

    public List<PointsRow> pointsTable(int year) {
        return jdbc.query(POINTS_TABLE, new MapSqlParameterSource("year", year), (rs, n) -> new PointsRow(
                rs.getInt("team_id"), rs.getLong("matches"), rs.getLong("wins"), rs.getLong("losses"),
                rs.getLong("ties"), rs.getLong("no_results"), rs.getLong("runs_for"), rs.getLong("balls_faced"),
                rs.getLong("runs_against"), rs.getLong("balls_bowled")));
    }

    private Optional<TeamScore> teamScore(String sql, int year) {
        return jdbc.query(sql, new MapSqlParameterSource("year", year), (rs, n) -> teamScore(rs)).stream().findFirst();
    }

    private static TeamScore teamScore(ResultSet rs) throws SQLException {
        return new TeamScore(rs.getInt("match_id"), rs.getInt("batting_team_id"), rs.getInt("bowling_team_id"),
                rs.getInt("total_runs"), rs.getInt("wickets"), rs.getInt("legal_balls"),
                rs.getObject("match_date", java.time.LocalDate.class));
    }

    public record SeasonTotals(long matches, long innings, long runs, long wickets, long fours, long sixes,
                               BigDecimal averageScore, BigDecimal runRate) {
    }

    public record TeamScore(int matchId, int teamId, int opponentId, int runs, int wickets, int legalBalls,
                            java.time.LocalDate date) {
    }

    /** Raw inputs; points and NRR are computed by the caller with {@code CricketMetrics}. */
    public record PointsRow(int teamId, long matches, long wins, long losses, long ties, long noResults, long runsFor,
                            long ballsFaced, long runsAgainst, long ballsBowled) {
    }
}
