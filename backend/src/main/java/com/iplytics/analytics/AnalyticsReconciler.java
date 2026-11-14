package com.iplytics.analytics;

import com.iplytics.analytics.AnalyticsRefreshReport.Check;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Verifies the refreshed analytics against core (docs/analytics.md, "Reconciliation"). Every expected value is
 * recomputed independently from core tables, so nothing is hard-coded.
 */
@Component
public class AnalyticsReconciler {

    /** (expected, actual) pairs recomputed from core. */
    private static final List<String[]> EQUALITIES = List.of(
            new String[] {"Innings summaries = core innings",
                    "select count(*) from core.innings", "select count(*) from analytics.innings_summary"},
            new String[] {"Innings summary runs, extras, legal balls and wickets = core",
                    """
                    select concat_ws('/', sum(total_runs), sum(extra_runs), count(*) filter (where is_legal_ball),
                                     (select count(*) from core.wicket where analytics.is_dismissal(kind)))
                      from core.delivery""",
                    "select concat_ws('/', sum(total_runs), sum(extras), sum(legal_balls), sum(wickets)) from analytics.innings_summary"},
            new String[] {"Batting innings = players at the crease per innings (batter, non-striker or dismissed)",
                    """
                    select count(*) from (select innings_id, batter_id from core.delivery
                                           union select innings_id, non_striker_id from core.delivery
                                           union select d.innings_id, w.player_out_id
                                                   from core.wicket w join core.delivery d on d.id = w.delivery_id) x""",
                    "select count(*) from analytics.batting_innings"},
            new String[] {"Batting runs, balls faced, boundaries and dismissals = core",
                    """
                    select concat_ws('/', sum(batter_runs), count(*) filter (where wides = 0),
                                     count(*) filter (where batter_runs = 4 and not non_boundary),
                                     count(*) filter (where batter_runs = 6 and not non_boundary),
                                     (select count(*) from (select distinct d.innings_id, w.player_out_id
                                                              from core.wicket w join core.delivery d on d.id = w.delivery_id
                                                             where analytics.is_dismissal(w.kind)) o))
                      from core.delivery""",
                    "select concat_ws('/', sum(runs), sum(balls), sum(fours), sum(sixes), count(*) filter (where is_out)) from analytics.batting_innings"},
            new String[] {"Bowling innings = distinct bowlers per innings",
                    "select count(*) from (select distinct innings_id, bowler_id from core.delivery) x",
                    "select count(*) from analytics.bowling_innings"},
            new String[] {"Bowling legal balls, runs conceded and credited wickets = core",
                    """
                    select concat_ws('/', count(*) filter (where is_legal_ball),
                                     sum(analytics.bowler_runs(total_runs, byes, legbyes, penalty)),
                                     (select count(*) from core.wicket where analytics.is_bowler_wicket(kind)))
                      from core.delivery""",
                    "select concat_ws('/', sum(legal_balls), sum(runs_conceded), sum(wickets)) from analytics.bowling_innings"},
            new String[] {"Team-match rows = 2 per match",
                    "select 2 * count(*) from core.match", "select count(*) from analytics.team_match_result"},
            new String[] {"Team-match wins, no-results and ties = core",
                    """
                    select concat_ws('/', count(*) filter (where winner_team_id is not null),
                                     2 * count(*) filter (where result = 'no result'), 2 * count(*) filter (where result = 'tie'))
                      from core.match""",
                    """
                    select concat_ws('/', count(*) filter (where won), count(*) filter (where no_result),
                                     count(*) filter (where tied))
                      from analytics.team_match_result"""},
            new String[] {"Super-over rows = super-over innings",
                    "select count(*) from core.innings where is_super_over",
                    "select count(*) from analytics.innings_summary where is_super_over"});

    /** Queries whose rows are problems. */
    private static final List<String[]> ZERO_PROBLEMS = List.of(
            new String[] {"No duplicate analytical grain", """
                    select 'batting ' || innings_id || '/' || player_id from analytics.batting_innings group by innings_id, player_id having count(*) > 1
                    union all select 'bowling ' || innings_id || '/' || player_id from analytics.bowling_innings group by innings_id, player_id having count(*) > 1
                    union all select 'innings ' || innings_id from analytics.innings_summary group by innings_id having count(*) > 1
                    union all select 'team ' || match_id || '/' || team_id from analytics.team_match_result group by match_id, team_id having count(*) > 1"""},
            new String[] {"No orphan keys in analytics", """
                    select 'batting player ' || b.player_id from analytics.batting_innings b
                     where not exists (select 1 from core.player p where p.id = b.player_id)
                        or not exists (select 1 from core.innings i where i.id = b.innings_id)
                        or not exists (select 1 from core.team t where t.id = b.team_id)
                        or not exists (select 1 from core.venue v where v.id = b.venue_id)
                        or not exists (select 1 from core.season s where s.id = b.season_id)
                    union all
                    select 'bowling player ' || b.player_id from analytics.bowling_innings b
                     where not exists (select 1 from core.player p where p.id = b.player_id)
                        or not exists (select 1 from core.innings i where i.id = b.innings_id)
                        or not exists (select 1 from core.team t where t.id = b.team_id)
                    union all
                    select 'innings ' || s.innings_id from analytics.innings_summary s
                     where not exists (select 1 from core.innings i where i.id = s.innings_id)
                        or not exists (select 1 from core.match m where m.id = s.match_id)"""},
            new String[] {"Batters and bowlers are in their match playing list", """
                    select 'batter ' || b.player_id || ' match ' || b.match_id from analytics.batting_innings b
                     where not exists (select 1 from core.match_player mp where mp.match_id = b.match_id and mp.player_id = b.player_id)
                    union all
                    select 'bowler ' || b.player_id || ' match ' || b.match_id from analytics.bowling_innings b
                     where not exists (select 1 from core.match_player mp where mp.match_id = b.match_id and mp.player_id = b.player_id)"""},
            new String[] {"Super overs are isolated from regular phases", """
                    select 'batting ' || innings_id from analytics.batting_innings
                     where (is_super_over and powerplay_runs + middle_runs + death_runs + powerplay_balls + middle_balls + death_balls > 0)
                        or (not is_super_over and (powerplay_runs + middle_runs + death_runs <> runs
                                                   or powerplay_balls + middle_balls + death_balls <> balls))
                    union all
                    select 'bowling ' || innings_id from analytics.bowling_innings
                     where (is_super_over and powerplay_balls + middle_balls + death_balls
                                              + powerplay_wickets + middle_wickets + death_wickets > 0)
                        or (not is_super_over and (powerplay_balls + middle_balls + death_balls <> legal_balls
                                                   or powerplay_runs + middle_runs + death_runs <> runs_conceded
                                                   or powerplay_wickets + middle_wickets + death_wickets <> wickets))
                    union all
                    select 'innings ' || innings_id from analytics.innings_summary
                     where (is_super_over and powerplay_runs + middle_runs + death_runs > 0)
                        or (not is_super_over and (powerplay_runs + middle_runs + death_runs <> total_runs
                                                   or powerplay_wickets + middle_wickets + death_wickets <> wickets))"""},
            new String[] {"Team runs for/against equal the regular innings totals", """
                    select t.match_id || '/' || t.team_id from analytics.team_match_result t
                     where t.runs_for is distinct from (select s.total_runs from analytics.innings_summary s
                                                         where s.match_id = t.match_id and s.batting_team_id = t.team_id and not s.is_super_over)"""},
            new String[] {"Every dismissal kind points at its wicket", """
                    select innings_id || '/' || player_id from analytics.batting_innings
                     where (dismissal_kind is null) <> (dismissal_wicket_id is null)
                        or (dismissal_wicket_id is not null
                            and not exists (select 1 from core.wicket w where w.id = dismissal_wicket_id
                                                                        and w.player_out_id = player_id))"""},
            new String[] {"Each batting position is used once per innings", """
                    select innings_id || '/' || batting_position from analytics.batting_innings
                     group by innings_id, batting_position having count(*) > 1"""});

    public List<Check> check(JdbcTemplate jdbc) {
        List<Check> checks = new ArrayList<>();
        for (String[] equality : EQUALITIES) {
            Map<String, Object> row = jdbc.queryForMap(
                    "select (" + equality[1] + ")::text as expected, (" + equality[2] + ")::text as actual");
            Object expected = row.get("expected");
            boolean passed = expected != null && expected.equals(row.get("actual"));
            checks.add(new Check(equality[0], passed,
                    passed ? String.valueOf(expected) : "core " + expected + " vs analytics " + row.get("actual")));
        }
        for (String[] zero : ZERO_PROBLEMS) {
            Map<String, Object> row = jdbc.queryForMap(
                    "select count(*) as problems, left(string_agg(item, '; '), 300) as sample from ("
                            + zero[1] + ") q(item)");
            long problems = ((Number) row.get("problems")).longValue();
            checks.add(new Check(zero[0], problems == 0,
                    problems == 0 ? "0 problems" : problems + " problem(s): " + row.get("sample")));
        }
        return checks;
    }

    /** Source observations worth knowing about; they do not fail the refresh. */
    public List<String> warnings(JdbcTemplate jdbc) {
        List<String> warnings = new ArrayList<>();
        Long labelMismatches = jdbc.queryForObject("""
                select count(*) from (
                    select d.over_number, d.actual_delivery,
                           1 + count(*) filter (where d.is_legal_ball) over (
                               partition by d.innings_id, d.over_number order by d.ball_in_over
                               rows between unbounded preceding and 1 preceding) as legal_ball_number
                      from core.delivery d where d.actual_delivery is not null) x
                 where split_part(x.actual_delivery, '.', 1)::int <> x.over_number
                    or split_part(x.actual_delivery, '.', 2)::int <> x.legal_ball_number""", Long.class);
        if (labelMismatches != null && labelMismatches > 0) {
            warnings.add(labelMismatches + " deliveries whose actual_delivery label differs from the legal-ball count"
                    + " (source labelling; analytics count legal balls from wides/no-balls)");
        }
        Long longOvers = jdbc.queryForObject("""
                select count(*) from (select innings_id, over_number from core.delivery where is_legal_ball
                                       group by 1, 2 having count(*) > 6) x""", Long.class);
        if (longOvers != null && longOvers > 0) {
            warnings.add(longOvers + " overs with more than 6 legal balls (umpire miscounts recorded by the source)");
        }
        return warnings;
    }
}
