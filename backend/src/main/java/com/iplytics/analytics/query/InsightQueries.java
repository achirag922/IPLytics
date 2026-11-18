package com.iplytics.analytics.query;

import com.iplytics.common.metrics.CricketMetrics;
import com.iplytics.common.metrics.CricketPhase;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * League-wide insights (docs/metrics.md section 9): toss impact, batting first versus chasing, phase scoring and
 * season comparison. Match-level questions read analytics.team_match_result (one row per team per match), phase
 * questions read analytics.innings_phase. A team filter selects the matches the franchise played in; with an
 * opponent too, the meetings of the two.
 */
@Repository
public class InsightQueries {

    private final NamedParameterJdbcTemplate jdbc;

    public InsightQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---- toss --------------------------------------------------------------------------------------------------

    /** Toss winners' results: overall, by decision and by season (matches with a recorded toss). */
    public TossImpact toss(AnalyticsFilter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String from = """
                 from analytics.team_match_result t join core.match m on m.id = t.match_id
                where t.toss_won""" + matchScope(filter, params);
        String aggregates = """
                count(*) as matches, count(*) filter (where not t.no_result) as decided,
                count(*) filter (where t.won) as toss_winner_wins,
                count(*) filter (where t.batted_first) as chose_to_bat_first""";
        TossRow overall = jdbc.queryForObject("select " + aggregates + from, params, (rs, n) -> tossRow(rs));
        List<TossDecision> byDecision = jdbc.query("select m.toss_decision as decision, " + aggregates + from
                + " group by m.toss_decision order by m.toss_decision", params,
                (rs, n) -> new TossDecision(rs.getString("decision"), tossRow(rs)));
        List<TossSeason> bySeason = jdbc.query("select t.season_year, " + aggregates + from
                + " group by t.season_year order by t.season_year", params,
                (rs, n) -> new TossSeason(rs.getInt("season_year"), tossRow(rs)));
        return new TossImpact(overall, byDecision, bySeason);
    }

    /** One franchise's tosses: how often it won them, what it chose and how it fared either way. */
    public TeamToss teamToss(int teamId, AnalyticsFilter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = """
                select count(*) as matches,
                       count(*) filter (where t.toss_won) as tosses_won,
                       count(*) filter (where t.toss_won and m.toss_decision = 'bat') as chose_bat,
                       count(*) filter (where t.toss_won and m.toss_decision = 'field') as chose_field,
                       count(*) filter (where t.toss_won and not t.no_result) as decided_after_toss_win,
                       count(*) filter (where t.toss_won and t.won) as wins_after_toss_win,
                       count(*) filter (where not t.toss_won and m.toss_winner_team_id is not null and not t.no_result)
                           as decided_after_toss_loss,
                       count(*) filter (where not t.toss_won and m.toss_winner_team_id is not null and t.won)
                           as wins_after_toss_loss
                  from analytics.team_match_result t join core.match m on m.id = t.match_id
                 where true""" + filter.team(teamId).conditions("t", false, params);
        return jdbc.queryForObject(sql, params, (rs, n) -> new TeamToss(rs.getLong("matches"),
                rs.getLong("tosses_won"), CricketMetrics.percentage(rs.getLong("tosses_won"), rs.getLong("matches")),
                rs.getLong("chose_bat"), rs.getLong("chose_field"), rs.getLong("decided_after_toss_win"),
                rs.getLong("wins_after_toss_win"),
                CricketMetrics.percentage(rs.getLong("wins_after_toss_win"), rs.getLong("decided_after_toss_win")),
                rs.getLong("decided_after_toss_loss"), rs.getLong("wins_after_toss_loss"),
                CricketMetrics.percentage(rs.getLong("wins_after_toss_loss"), rs.getLong("decided_after_toss_loss"))));
    }

    // ---- batting first versus chasing ---------------------------------------------------------------------------

    /** Results of the side batting first, overall, by season and by the target set (matches with two innings). */
    public BattingFirst battingFirst(AnalyticsFilter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String from = """
                 from analytics.team_match_result t
                where t.batted_first and t.runs_against is not null""" + matchScope(filter, params);
        String aggregates = """
                count(*) filter (where t.result in ('runs', 'wickets')) as decisive,
                count(*) filter (where t.result in ('runs', 'wickets') and t.won) as batting_first_wins,
                count(*) filter (where t.result in ('runs', 'wickets') and t.lost) as chasing_wins,
                coalesce(sum(t.runs_for) filter (where t.result in ('runs', 'wickets') and t.method is null), 0)
                    as first_innings_runs,
                count(*) filter (where t.result in ('runs', 'wickets') and t.method is null) as first_innings""";
        BattingFirstRow overall = jdbc.queryForObject("select " + aggregates + from, params,
                (rs, n) -> battingFirstRow(rs));
        List<BattingFirstSeason> bySeason = jdbc.query("select t.season_year, " + aggregates + from
                + " group by t.season_year order by t.season_year", params,
                (rs, n) -> new BattingFirstSeason(rs.getInt("season_year"), battingFirstRow(rs)));
        Map<Integer, long[]> bands = new TreeMap<>();
        jdbc.query("""
                select width_bucket(t.runs_for + 1, 150, 210, 3) as band, count(*) as matches,
                       count(*) filter (where t.lost) as chased""" + from
                + " and t.result in ('runs', 'wickets') and t.method is null group by 1", params, rs -> {
                    bands.put(rs.getInt("band"), new long[] {rs.getLong("matches"), rs.getLong("chased")});
                });
        List<TargetBand> byTarget = new ArrayList<>();
        for (int band = 0; band <= 4; band++) {
            long[] counts = bands.getOrDefault(band, new long[] {0, 0});
            byTarget.add(new TargetBand(band == 0 ? null : 150 + 20 * (band - 1), band == 4 ? null : 149 + 20 * band,
                    counts[0], counts[1], CricketMetrics.percentage(counts[1], counts[0])));
        }
        return new BattingFirst(overall, bySeason, byTarget);
    }

    // ---- phases ------------------------------------------------------------------------------------------------

    /**
     * Scoring by phase for the innings in scope (regular innings). With a team the rows are that team's batting;
     * {@link #phasesConceded} gives its bowling.
     */
    public List<PhaseTotals> phases(AnalyticsFilter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        return jdbc.query("select p.phase, " + PHASE_AGGREGATES + " from analytics.innings_phase p where true"
                + filter.conditions("p", true, params) + " group by p.phase", params, (rs, n) -> phaseTotals(rs))
                .stream().sorted((a, b) -> a.phase().compareTo(b.phase())).toList();
    }

    /** Phases of the innings bowled by the team (runs and wickets it conceded / took). */
    public List<PhaseTotals> phasesConceded(int teamId, AnalyticsFilter filter) {
        AnalyticsFilter bowling = new AnalyticsFilter(filter.seasonFrom(), filter.seasonTo(), filter.opponentTeamId(),
                teamId, filter.venueId(), false);
        return phases(bowling);
    }

    /** Phase run rates per season, for trend charts. */
    public Map<Integer, List<PhaseTotals>> phasesBySeason(AnalyticsFilter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        Map<Integer, List<PhaseTotals>> result = new TreeMap<>();
        jdbc.query("select p.season_year, p.phase, " + PHASE_AGGREGATES + " from analytics.innings_phase p where true"
                + filter.conditions("p", true, params) + " group by p.season_year, p.phase", params, rs -> {
                    result.computeIfAbsent(rs.getInt("season_year"), k -> new ArrayList<>()).add(phaseTotals(rs));
                });
        result.values().forEach(list -> list.sort((a, b) -> a.phase().compareTo(b.phase())));
        return result;
    }

    private static final String PHASE_AGGREGATES = """
            count(distinct p.innings_id) as innings, sum(p.runs) as runs, sum(p.legal_balls) as legal_balls,
            sum(p.wickets) as wickets, sum(p.fours) as fours, sum(p.sixes) as sixes, sum(p.dot_balls) as dot_balls""";

    private static PhaseTotals phaseTotals(ResultSet rs) throws SQLException {
        long innings = rs.getLong("innings");
        long runs = rs.getLong("runs");
        long balls = rs.getLong("legal_balls");
        long wickets = rs.getLong("wickets");
        long fours = rs.getLong("fours");
        long sixes = rs.getLong("sixes");
        long dots = rs.getLong("dot_balls");
        return new PhaseTotals(CricketPhase.fromSqlName(rs.getString("phase")), innings, runs, balls,
                CricketMetrics.overs(balls), CricketMetrics.runRate(runs, balls), wickets,
                CricketMetrics.mean(wickets, innings), CricketMetrics.mean(runs, innings), fours, sixes,
                CricketMetrics.boundaryPercentage(fours + sixes, balls), CricketMetrics.dotBallPercentage(dots, balls));
    }

    // ---- seasons -----------------------------------------------------------------------------------------------

    /** Season-level figures for comparison and trends, for the seasons in the range (inclusive). */
    public List<SeasonInsight> seasons(Integer seasonFrom, Integer seasonTo) {
        MapSqlParameterSource params = new MapSqlParameterSource();

        Map<Integer, Map<String, Object>> totals = keyed(jdbc.queryForList("""
                select s.season_year, count(distinct s.match_id) as matches, count(*) as innings,
                       sum(s.total_runs) as runs, sum(s.legal_balls) as legal_balls, sum(s.wickets) as wickets,
                       sum(s.fours) as fours, sum(s.sixes) as sixes, max(s.total_runs) as highest_score
                  from analytics.innings_summary s where not s.is_super_over""" + range("s.season_year", seasonFrom, seasonTo, params)
                + " group by s.season_year", params));
        Map<Integer, Map<String, Object>> results = keyed(jdbc.queryForList("""
                select t.season_year,
                       count(*) filter (where t.batted_first and t.result in ('runs', 'wickets')) as decisive,
                       count(*) filter (where t.batted_first and t.result in ('runs', 'wickets') and t.won) as bat_first_wins,
                       count(*) filter (where t.toss_won and not t.no_result) as toss_decided,
                       count(*) filter (where t.toss_won and t.won) as toss_winner_wins,
                       coalesce(sum(t.runs_for) filter (where t.batted_first and t.result in ('runs', 'wickets')
                           and t.method is null), 0) as first_innings_runs,
                       count(*) filter (where t.batted_first and t.result in ('runs', 'wickets') and t.method is null)
                           as first_innings
                  from analytics.team_match_result t where true""" + range("t.season_year", seasonFrom, seasonTo, params)
                + " group by t.season_year", params));
        Map<Integer, List<PhaseTotals>> phases = phasesBySeason(AnalyticsFilter.all().seasons(seasonFrom, seasonTo));

        List<SeasonInsight> rows = new ArrayList<>();
        totals.forEach((year, t) -> {
            Map<String, Object> r = results.getOrDefault(year, Map.of());
            long matches = number(t, "matches");
            long runs = number(t, "runs");
            Map<CricketPhase, BigDecimal> phaseRunRates = new LinkedHashMap<>();
            phases.getOrDefault(year, List.of()).forEach(p -> phaseRunRates.put(p.phase(), p.runRate()));
            rows.add(new SeasonInsight(year, matches, runs, CricketMetrics.mean(runs, number(t, "innings")),
                    CricketMetrics.runRate(runs, number(t, "legal_balls")),
                    CricketMetrics.mean(number(t, "fours"), matches), CricketMetrics.mean(number(t, "sixes"), matches),
                    CricketMetrics.mean(number(t, "wickets"), matches),
                    CricketMetrics.mean(number(r, "first_innings_runs"), number(r, "first_innings")),
                    CricketMetrics.percentage(number(r, "bat_first_wins"), number(r, "decisive")),
                    CricketMetrics.percentage(number(r, "toss_winner_wins"), number(r, "toss_decided")),
                    phaseRunRates.get(CricketPhase.POWERPLAY), phaseRunRates.get(CricketPhase.MIDDLE),
                    phaseRunRates.get(CricketPhase.DEATH), (Integer) t.get("highest_score")));
        });
        return rows;
    }

    // ---- shared ------------------------------------------------------------------------------------------------

    /** Seasons and venue on t, plus "the team played" / "the two met" for team and opponent. */
    private static String matchScope(AnalyticsFilter filter, MapSqlParameterSource params) {
        StringBuilder sql = new StringBuilder(range("t.season_year", filter.seasonFrom(), filter.seasonTo(), params));
        if (filter.venueId() != null) {
            sql.append(" and t.venue_id = :venueId");
            params.addValue("venueId", filter.venueId());
        }
        if (filter.teamId() != null) {
            sql.append(" and :teamId in (t.team_id, t.opponent_team_id)");
            params.addValue("teamId", filter.teamId());
        }
        if (filter.opponentTeamId() != null) {
            sql.append(" and :opponentId in (t.team_id, t.opponent_team_id)");
            params.addValue("opponentId", filter.opponentTeamId());
        }
        return sql.toString();
    }

    private static String range(String column, Integer from, Integer to, MapSqlParameterSource params) {
        StringBuilder sql = new StringBuilder();
        if (from != null) {
            sql.append(" and ").append(column).append(" >= :seasonFrom");
            params.addValue("seasonFrom", from);
        }
        if (to != null) {
            sql.append(" and ").append(column).append(" <= :seasonTo");
            params.addValue("seasonTo", to);
        }
        return sql.toString();
    }

    private static Map<Integer, Map<String, Object>> keyed(List<Map<String, Object>> rows) {
        Map<Integer, Map<String, Object>> result = new TreeMap<>();
        rows.forEach(row -> result.put(((Number) row.get("season_year")).intValue(), row));
        return result;
    }

    private static long number(Map<String, Object> row, String column) {
        Object value = row.get(column);
        return value == null ? 0 : ((Number) value).longValue();
    }

    private static TossRow tossRow(ResultSet rs) throws SQLException {
        long decided = rs.getLong("decided");
        long wins = rs.getLong("toss_winner_wins");
        return new TossRow(rs.getLong("matches"), decided, wins, CricketMetrics.percentage(wins, decided),
                rs.getLong("chose_to_bat_first"));
    }

    private static BattingFirstRow battingFirstRow(ResultSet rs) throws SQLException {
        long decisive = rs.getLong("decisive");
        long first = rs.getLong("batting_first_wins");
        long chasing = rs.getLong("chasing_wins");
        return new BattingFirstRow(decisive, first, chasing, CricketMetrics.percentage(first, decisive),
                CricketMetrics.percentage(chasing, decisive),
                CricketMetrics.mean(rs.getLong("first_innings_runs"), rs.getLong("first_innings")));
    }

    /**
     * @param decided matches with a result (no-results excluded); a tie decided by super over counts as a win
     * @param choseToBatFirst matches in which the toss winner batted first
     */
    public record TossRow(long matches, long decided, long tossWinnerWins, BigDecimal tossWinnerWinPercentage,
                          long choseToBatFirst) {
    }

    public record TossDecision(String decision, TossRow row) {
    }

    public record TossSeason(int seasonYear, TossRow row) {
    }

    public record TossImpact(TossRow overall, List<TossDecision> byDecision, List<TossSeason> bySeason) {
    }

    public record TeamToss(long matches, long tossesWon, BigDecimal tossWinPercentage, long choseToBat,
                           long choseToField, long decidedAfterTossWin, long winsAfterTossWin,
                           BigDecimal winPercentageAfterTossWin, long decidedAfterTossLoss, long winsAfterTossLoss,
                           BigDecimal winPercentageAfterTossLoss) {
    }

    /** @param averageFirstInningsScore over decisive matches without a D/L adjustment */
    public record BattingFirstRow(long decisiveMatches, long battingFirstWins, long chasingWins,
                                  BigDecimal battingFirstWinPercentage, BigDecimal chasingWinPercentage,
                                  BigDecimal averageFirstInningsScore) {
    }

    public record BattingFirstSeason(int seasonYear, BattingFirstRow row) {
    }

    /** Chases of a target in [from, to] (null: unbounded); D/L-adjusted matches are left out. */
    public record TargetBand(Integer from, Integer to, long matches, long chasedSuccessfully,
                             BigDecimal successPercentage) {
    }

    public record BattingFirst(BattingFirstRow overall, List<BattingFirstSeason> bySeason, List<TargetBand> byTarget) {
    }

    public record PhaseTotals(CricketPhase phase, long innings, long runs, long legalBalls, String overs,
                              BigDecimal runRate, long wickets, BigDecimal wicketsPerInnings,
                              BigDecimal runsPerInnings, long fours, long sixes, BigDecimal boundaryPercentage,
                              BigDecimal dotBallPercentage) {
    }

    public record SeasonInsight(int seasonYear, long matches, long runs, BigDecimal averageScore, BigDecimal runRate,
                                BigDecimal foursPerMatch, BigDecimal sixesPerMatch, BigDecimal wicketsPerMatch,
                                BigDecimal averageFirstInningsScore, BigDecimal battingFirstWinPercentage,
                                BigDecimal tossWinnerWinPercentage, BigDecimal powerplayRunRate,
                                BigDecimal middleRunRate, BigDecimal deathRunRate, Integer highestScore) {
    }
}
