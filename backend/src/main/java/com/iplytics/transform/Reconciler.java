package com.iplytics.transform;

import com.iplytics.transform.TransformReport.CheckResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Checks around the transformation (rules in docs/data-source.md section 10):
 * <ul>
 *   <li>{@link #validate} runs on the typed staging copies before anything is written;</li>
 *   <li>{@link #reconcile} compares core with staging after loading: counts, checksums, orphans,
 *       referential integrity and cricket arithmetic (deliveries -> innings totals -> match result);</li>
 *   <li>{@link #warnings} lists observations that need a human look but are not errors.</li>
 * </ul>
 * Each "zero" check is a query whose rows are problems; it passes when there are none.
 */
@Component
public class Reconciler {

    static final String VALIDATION = "validation";
    static final String RECONCILIATION = "reconciliation";

    /** Innings totals computed from core deliveries; wickets exclude retirements that are not dismissals. */
    private static final String INNINGS_TOTALS = """
            with runs as (select innings_id, sum(total_runs) as runs from core.delivery group by 1),
            wk as (select cd.innings_id, count(*) as wickets
                     from core.wicket cw join core.delivery cd on cd.id = cw.delivery_id
                    where cw.kind not in ('retired hurt', 'retired not out') group by 1),
            inn as (select ci.match_id, ci.innings_number, ci.batting_team_id,
                           coalesce(r.runs, 0) as runs, coalesce(w.wickets, 0) as wickets
                      from core.innings ci left join runs r on r.innings_id = ci.id left join wk w on w.innings_id = ci.id)
            """;

    private static final String REGULAR_INNINGS = """
              from core.match m
              join inn i1 on i1.match_id = m.id and i1.innings_number = 1
              join inn i2 on i2.match_id = m.id and i2.innings_number = 2
            """;

    private static final String CHECKSUM = """
            concat_ws('/', sum(total_runs), sum(batter_runs), sum(wides), sum(noballs), sum(byes), sum(legbyes),
                      sum(penalty), count(*) filter (where non_boundary), count(*) filter (where is_wicket), %s)""";

    public List<CheckResult> validate(JdbcTemplate jdbc) {
        List<CheckResult> results = new ArrayList<>();
        zero(jdbc, results, VALIDATION, "Team names are in the curated reference data", """
                select distinct name as item from t_team_usage u
                 where not exists (select 1 from t_ref_team_name r where r.name = u.name)""");
        zero(jdbc, results, VALIDATION, "Team names are used within their curated seasons", """
                select distinct u.name || ' in ' || u.season_year || ' (curated '
                       || r.first_season || '-' || coalesce(r.last_season::text, 'now') || ')' as item
                  from t_team_usage u join t_ref_team_name r on r.name = u.name
                 where u.season_year < r.first_season or u.season_year > coalesce(r.last_season, 32767)""");
        zero(jdbc, results, VALIDATION, "Each season year has exactly one source season label", """
                select season_year || ' has labels ' || string_agg(distinct source_season, ', ') as item
                  from t_match group by season_year having count(distinct source_season) > 1
                union all
                select source_season || ' spans years ' || string_agg(distinct season_year::text, ', ')
                  from t_match group by source_season having count(distinct season_year) > 1""");
        zero(jdbc, results, VALIDATION, "Converter season_year matches the match-date year", """
                select source_match_id || ': ' || staged_season_year || ' vs ' || season_year as item
                  from t_match where staged_season_year is not null and staged_season_year::smallint <> season_year""");
        zero(jdbc, results, VALIDATION, "No duplicate source match ids", """
                select source_match_id::text as item from t_match group by source_match_id having count(*) > 1""");
        zero(jdbc, results, VALIDATION, "Every staged delivery and playing-list row has a staged match", """
                select 'deliveries for ' || d.source_match_id as item
                  from (select distinct source_match_id from t_delivery) d
                 where not exists (select 1 from t_match m where m.source_match_id = d.source_match_id)
                union all
                select 'playing list for ' || p.source_match_id
                  from (select distinct source_match_id from t_match_player) p
                 where not exists (select 1 from t_match m where m.source_match_id = p.source_match_id)""");
        zero(jdbc, results, VALIDATION, "Every staged match has deliveries", """
                select source_match_id::text as item from t_match m
                 where not exists (select 1 from t_delivery d where d.source_match_id = m.source_match_id)""");
        zero(jdbc, results, VALIDATION, "A player name maps to one Cricsheet id within a match", """
                select source_match_id || ': ' || name as item from t_person where id_count > 1""");
        zero(jdbc, results, VALIDATION, "Each innings has one batting team, one bowling team and one super-over flag", """
                select source_match_id || ' innings ' || innings_number as item from t_innings_src
                 where batting_team_count > 1 or bowling_team_count > 1 or not consistent_super_over""");
        zero(jdbc, results, VALIDATION, "Every dismissal and fielder names a player", """
                select source_match_id || ' innings ' || innings_number || ' over ' || over_number as item
                  from t_wicket_src where player_out is null
                union all
                select source_match_id || ' innings ' || innings_number || ' over ' || over_number
                  from t_fielder_src where name is null""");
        return results;
    }

    public List<CheckResult> reconcile(JdbcTemplate jdbc) {
        List<CheckResult> results = new ArrayList<>();
        // Counts and checksums: staging (latest batches) == core
        equal(jdbc, results, "Matches: staging = core",
                "select count(*) from t_match", "select count(*) from core.match");
        equal(jdbc, results, "Innings: staging = core",
                "select count(*) from t_innings_src", "select count(*) from core.innings");
        equal(jdbc, results, "Deliveries: staging = core",
                "select count(*) from t_delivery", "select count(*) from core.delivery");
        equal(jdbc, results, "Wickets: staging = core",
                "select count(*) from t_wicket_src", "select count(*) from core.wicket");
        equal(jdbc, results, "Wicket fielders: staging = core",
                "select count(*) from t_fielder_src", "select count(*) from core.wicket_fielder");
        equal(jdbc, results, "Match players: staging = core",
                "select count(*) from t_match_player", "select count(*) from core.match_player");
        equal(jdbc, results, "Seasons: staging = core",
                "select count(distinct season_year) from t_match", "select count(*) from core.season");
        equal(jdbc, results, "Players: distinct source identities = core",
                "select count(distinct coalesce(cricsheet_id, 'name:' || name)) from t_person",
                "select count(*) from core.player");
        equal(jdbc, results, "Runs, extras, boundaries, wickets and legal balls: staging = core",
                "select " + CHECKSUM.formatted("count(*) filter (where wides = 0 and noballs = 0)") + " from t_delivery",
                "select " + CHECKSUM.formatted("count(*) filter (where is_legal_ball)") + " from core.delivery");
        zero(jdbc, results, RECONCILIATION, "Innings totals: staging = core", """
                select coalesce(s.source_match_id, c.source_match_id) || ' innings '
                       || coalesce(s.innings_number, c.innings_number) as item
                  from (select source_match_id, innings_number, sum(total_runs) as runs, count(*) as balls
                          from t_delivery group by 1, 2) s
                  full join (select cm.source_match_id, ci.innings_number, sum(cd.total_runs) as runs, count(*) as balls
                               from core.delivery cd join core.innings ci on ci.id = cd.innings_id
                               join core.match cm on cm.id = ci.match_id group by 1, 2) c
                    on c.source_match_id = s.source_match_id and c.innings_number = s.innings_number
                 where (s.runs, s.balls) is distinct from (c.runs, c.balls)""");

        // Duplicates and orphans
        zero(jdbc, results, RECONCILIATION, "No duplicate source match ids in core", """
                select source_match_id::text as item from core.match group by 1 having count(*) > 1""");
        zero(jdbc, results, RECONCILIATION, "No orphan innings", """
                select ci.id::text as item from core.innings ci
                 where not exists (select 1 from core.match m where m.id = ci.match_id)""");
        zero(jdbc, results, RECONCILIATION, "No orphan deliveries", """
                select cd.id::text as item from core.delivery cd
                 where not exists (select 1 from core.innings ci where ci.id = cd.innings_id)""");
        zero(jdbc, results, RECONCILIATION, "No orphan players", """
                select p.display_name as item from core.player p
                 where not exists (select 1 from core.match_player x where x.player_id = p.id)
                   and not exists (select 1 from core.delivery x where x.batter_id = p.id)
                   and not exists (select 1 from core.delivery x where x.non_striker_id = p.id)
                   and not exists (select 1 from core.delivery x where x.bowler_id = p.id)
                   and not exists (select 1 from core.wicket x where x.player_out_id = p.id)
                   and not exists (select 1 from core.wicket_fielder x where x.player_id = p.id)
                   and not exists (select 1 from core.match x where x.player_of_match_id = p.id)""");
        zero(jdbc, results, RECONCILIATION, "No orphan teams", """
                select t.code as item from core.team t
                 where not exists (select 1 from core.match m where t.id in (m.team1_id, m.team2_id))""");
        zero(jdbc, results, RECONCILIATION, "No orphan venues", """
                select v.name as item from core.venue v where not exists (select 1 from core.match m where m.venue_id = v.id)""");
        zero(jdbc, results, RECONCILIATION, "No orphan seasons", """
                select s.year::text as item from core.season s
                 where not exists (select 1 from core.match m where m.season_id = s.id)""");

        // Referential consistency
        zero(jdbc, results, RECONCILIATION, "Every match has innings and playing lists for both teams", """
                select m.source_match_id::text as item from core.match m
                 where not exists (select 1 from core.innings i where i.match_id = m.id)
                    or (select count(distinct mp.team_id) from core.match_player mp where mp.match_id = m.id) <> 2""");
        zero(jdbc, results, RECONCILIATION, "Innings and playing-list teams are the match teams", """
                select m.source_match_id || ' innings ' || i.innings_number as item
                  from core.innings i join core.match m on m.id = i.match_id
                 where i.batting_team_id not in (m.team1_id, m.team2_id) or i.bowling_team_id not in (m.team1_id, m.team2_id)
                union all
                select m.source_match_id || ' player ' || mp.player_id
                  from core.match_player mp join core.match m on m.id = mp.match_id
                 where mp.team_id not in (m.team1_id, m.team2_id)""");
        zero(jdbc, results, RECONCILIATION, "Innings 1 and 2 are batted by different teams", """
                select m.source_match_id::text as item from core.match m
                  join core.innings i1 on i1.match_id = m.id and i1.innings_number = 1
                  join core.innings i2 on i2.match_id = m.id and i2.innings_number = 2
                 where i1.batting_team_id = i2.batting_team_id""");
        zero(jdbc, results, RECONCILIATION, "Delivery super-over and wicket flags agree with innings and wickets", """
                select cd.id::text as item from core.delivery cd join core.innings ci on ci.id = cd.innings_id
                 where cd.is_super_over <> ci.is_super_over
                    or cd.is_wicket <> exists (select 1 from core.wicket w where w.delivery_id = cd.id)""");
        zero(jdbc, results, RECONCILIATION, "Batters, non-strikers and bowlers are in the match playing list", """
                select m.source_match_id || ' delivery ' || cd.id as item
                  from core.delivery cd join core.innings ci on ci.id = cd.innings_id join core.match m on m.id = ci.match_id
                 where not exists (select 1 from core.match_player mp where mp.match_id = m.id and mp.player_id = cd.batter_id)
                    or not exists (select 1 from core.match_player mp where mp.match_id = m.id and mp.player_id = cd.non_striker_id)
                    or not exists (select 1 from core.match_player mp where mp.match_id = m.id and mp.player_id = cd.bowler_id)""");
        zero(jdbc, results, RECONCILIATION, "Historical team names belong to the match franchise and season", """
                select m.source_match_id || ': ' || n.name as item
                  from core.match m join core.season s on s.id = m.season_id
                  join core.team_name_history n on n.name in (m.team1_name, m.team2_name)
                 where n.team_id not in (m.team1_id, m.team2_id)
                    or s.year < n.first_season or s.year > coalesce(n.last_season, 32767)""");
        zero(jdbc, results, RECONCILIATION, "Super-over flag matches super-over innings", """
                select m.source_match_id::text as item from core.match m
                 where m.has_super_over <> exists (select 1 from core.innings i where i.match_id = m.id and i.is_super_over)""");

        // Cricket arithmetic: delivery totals -> innings totals -> match result
        zero(jdbc, results, RECONCILIATION, "Chase target = first-innings total + 1 (non-D/L)", INNINGS_TOTALS + """
                select m.source_match_id::text as item""" + REGULAR_INNINGS + """
                 where m.method is null and m.target_runs is not null and m.target_runs <> i1.runs + 1""");
        zero(jdbc, results, RECONCILIATION, "Wins by runs: side batting first won by the run difference (non-D/L)",
                INNINGS_TOTALS + "select m.source_match_id::text as item" + REGULAR_INNINGS + """
                 where m.result = 'runs' and m.method is null
                   and (m.winner_team_id <> i1.batting_team_id or m.result_margin <> i1.runs - i2.runs)""");
        zero(jdbc, results, RECONCILIATION, "Wins by wickets: chasing side won by 10 minus wickets lost",
                INNINGS_TOTALS + "select m.source_match_id::text as item" + REGULAR_INNINGS + """
                 where m.result = 'wickets'
                   and (m.winner_team_id <> i2.batting_team_id or m.result_margin <> 10 - i2.wickets)""");
        zero(jdbc, results, RECONCILIATION, "Ties: both innings totals equal (non-D/L)",
                INNINGS_TOTALS + "select m.source_match_id::text as item" + REGULAR_INNINGS + """
                 where m.result = 'tie' and m.method is null and i1.runs <> i2.runs""");
        zero(jdbc, results, RECONCILIATION, "Super-over matches are ties with a super-over winner", """
                select m.source_match_id::text as item from core.match m
                 where m.has_super_over and (m.result <> 'tie' or m.super_over_winner_team_id is null)""");
        return results;
    }

    public List<String> warnings(JdbcTemplate jdbc) {
        List<String> warnings = new ArrayList<>();
        list(jdbc, warnings, "Venue names not in reference data (created as separate, non-curated venues)", """
                select alias || coalesce(' (' || venue_city || ')', '') from t_venue_resolved where not is_curated order by 1""");
        list(jdbc, warnings, "Cricsheet venue ids shared by several core venues (probably a missing alias)", """
                select va.cricsheet_venue_id || ': ' || string_agg(distinct v.name, ' | ')
                  from core.venue_alias va join core.venue v on v.id = va.venue_id
                 where va.cricsheet_venue_id is not null
                 group by va.cricsheet_venue_id having count(distinct va.venue_id) > 1 order by 1""");
        list(jdbc, warnings, "Players recorded under more than one name", """
                select coalesce(p.cricsheet_id, '-') || ': ' || string_agg(a.name, ' | ' order by a.first_season, a.name)
                  from core.player_alias a join core.player p on p.id = a.player_id
                 group by p.id, p.cricsheet_id having count(*) > 1 order by 1""");
        list(jdbc, warnings, "Names shared by different players (resolved by Cricsheet id)", """
                select a.name || ' -> ' || count(distinct a.player_id) || ' players'
                  from core.player_alias a group by a.name having count(distinct a.player_id) > 1 order by 1""");
        list(jdbc, warnings, "D/L matches won by runs by the side batting second (ahead of par)", INNINGS_TOTALS + """
                select m.source_match_id::text""" + REGULAR_INNINGS + """
                 where m.result = 'runs' and m.method is not null and m.winner_team_id = i2.batting_team_id order by 1""");
        list(jdbc, warnings, "Matches without a second innings", """
                select m.source_match_id || ' (' || m.result || ')' from core.match m
                 where not exists (select 1 from core.innings i where i.match_id = m.id and i.innings_number = 2)
                 order by 1""");
        return warnings;
    }

    private static void zero(JdbcTemplate jdbc, List<CheckResult> results, String phase, String name, String itemsSql) {
        Map<String, Object> row = jdbc.queryForMap(
                "select count(*) as problems, left(string_agg(item, '; '), 400) as sample from (" + itemsSql + ") q");
        long problems = ((Number) row.get("problems")).longValue();
        results.add(new CheckResult(phase, name, problems == 0,
                problems == 0 ? "0 problems" : problems + " problem(s): " + row.get("sample")));
    }

    private static void equal(JdbcTemplate jdbc, List<CheckResult> results, String name, String expectedSql,
                              String actualSql) {
        Map<String, Object> row = jdbc.queryForMap(
                "select (" + expectedSql + ")::text as expected, (" + actualSql + ")::text as actual");
        Object expected = row.get("expected");
        Object actual = row.get("actual");
        boolean passed = expected != null && expected.equals(actual);
        results.add(new CheckResult(RECONCILIATION, name, passed,
                passed ? String.valueOf(actual) : "staging " + expected + " vs core " + actual));
    }

    private static void list(JdbcTemplate jdbc, List<String> warnings, String title, String sql) {
        List<String> items = jdbc.queryForList(sql, String.class);
        if (!items.isEmpty()) {
            String shown = String.join("; ", items.subList(0, Math.min(items.size(), 15)));
            warnings.add("%s: %d - %s%s".formatted(title, items.size(), shown, items.size() > 15 ? "; ..." : ""));
        }
    }
}
