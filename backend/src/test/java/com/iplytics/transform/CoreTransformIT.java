package com.iplytics.transform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import com.iplytics.ingestion.FileIngestionResult.Outcome;
import com.iplytics.ingestion.IngestionService;
import com.iplytics.ingestion.cricsheet.CricsheetConverter;
import com.iplytics.ingestion.source.DiscoveredFile;
import com.iplytics.ingestion.source.FileDiscovery;
import com.iplytics.support.AbstractIntegrationTest;
import com.iplytics.transform.TransformReport.CheckResult;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Cricsheet JSON fixtures -> converter -> staging ingestion -> core transformation, on real PostgreSQL.
 * Fixtures (src/test/resources/cricsheet): 335982 normal match with multiple extras, a dismissal and a player
 * who neither batted nor bowled; 1000001 D/L; 1000002 super-over tie with two dismissals on one ball and a
 * substitute fielder; 1000003 no result; 1000004 final spanning two dates. Variants are derived per test.
 */
class CoreTransformIT extends AbstractIntegrationTest {

    @Autowired
    private CricsheetConverter converter;

    @Autowired
    private IngestionService ingestion;

    @Autowired
    private CoreTransformService transformService;

    @Autowired
    private JdbcTemplate jdbc;

    @TempDir
    Path dir;

    private int loads;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("""
                truncate core.wicket_fielder, core.wicket, core.delivery, core.innings, core.match_player, core.match,
                         core.player_alias, core.player, core.venue_alias, core.venue, core.team_name_history, core.team,
                         core.season, core.transform_run, core.ingestion_run restart identity cascade""");
    }

    @Test
    void transformsTheFixturesAndReconciles() throws IOException {
        load(fixtures());

        TransformReport report = transformService.transform();

        assertThat(report.passed()).isTrue();
        assertThat(report.checks()).allMatch(CheckResult::passed).hasSizeGreaterThan(30);
        assertThat(report.coreRowCounts()).containsEntry("match", 5L).containsEntry("innings", 11L)
                .containsEntry("delivery", 14L).containsEntry("wicket", 4L).containsEntry("wicket_fielder", 4L)
                .containsEntry("match_player", 28L).containsEntry("season", 5L);
        assertThat(jdbc.queryForObject("select status from core.transform_run where id = ?", String.class,
                report.transformRunId())).isEqualTo("COMPLETED");
    }

    @Test
    void seasonYearComesFromTheMatchDateAndKeepsTheSourceLabel() throws IOException {
        load(fixtures());
        transformService.transform();

        assertThat(jdbc.queryForList("select year, source_season, name from core.season order by year"))
                .extracting(r -> r.get("year") + "|" + r.get("source_season") + "|" + r.get("name"))
                .containsExactly("2008|2007/08|IPL 2008", "2010|2009/10|IPL 2010", "2017|2017|IPL 2017",
                        "2020|2020/21|IPL 2020", "2023|2023|IPL 2023");
    }

    @Test
    void renamedFranchisesShareOneTeamButKeepTheirHistoricalNames() throws IOException {
        Map<String, String> files = fixtures();
        String dls = files.get("1000001");
        files.put("1000006", dls.replace("Mumbai Indians", "Rising Pune Supergiants")
                .replace("2010-03-20", "2016-04-10").replace("\"2009/10\"", "\"2016\""));
        files.put("1000007", dls.replace("Mumbai Indians", "Rising Pune Supergiant")
                .replace("2010-03-20", "2017-04-10").replace("\"2009/10\"", "\"2017\""));
        load(files);
        transformService.transform();

        assertThat(teamsOf(1000001)).containsExactly("Mumbai Indians|MI", "Delhi Daredevils|DC");
        assertThat(teamsOf(1000002)).containsExactly("Delhi Capitals|DC", "Kings XI Punjab|PBKS");
        assertThat(teamsOf(335982)).containsExactly("Royal Challengers Bangalore|RCB", "Kolkata Knight Riders|KKR");
        assertThat(teamsOf(1000006)).containsExactly("Rising Pune Supergiants|RPS", "Delhi Daredevils|DC");
        assertThat(teamsOf(1000007)).containsExactly("Rising Pune Supergiant|RPS", "Delhi Daredevils|DC");
        assertThat(jdbc.queryForList("select name from core.team where code in ('DC', 'PBKS', 'RCB') order by code",
                String.class)).containsExactly("Delhi Capitals", "Punjab Kings", "Royal Challengers Bengaluru");
        assertThat(jdbc.queryForList("""
                select n.name from core.team_name_history n join core.team t on t.id = n.team_id
                 where t.code = 'DC' order by n.first_season""", String.class))
                .containsExactly("Delhi Daredevils", "Delhi Capitals");
    }

    @Test
    void unknownTeamNameFailsLoudlyAndLeavesCoreUnchanged() throws IOException {
        load(fixtures());
        transformService.transform();

        Map<String, String> files = fixtures();
        files.put("1000001", files.get("1000001").replace("Mumbai Indians", "Mumbai Indianz"));
        load(files);

        assertThatThrownBy(() -> transformService.transform())
                .isInstanceOfSatisfying(TransformException.class, e -> assertThat(e.failedChecks())
                        .extracting(CheckResult::name, CheckResult::detail)
                        .anySatisfy(t -> assertThat(t.toString()).contains("curated reference data", "Mumbai Indianz")));
        assertThat(jdbc.queryForObject("select count(*) from core.match", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForList("select name from core.team_name_history", String.class))
                .doesNotContain("Mumbai Indianz");
        assertThat(jdbc.queryForList("select status from core.transform_run order by id", String.class))
                .containsExactly("COMPLETED", "FAILED");
    }

    @Test
    void venueAliasesResolveAndUnknownVenuesAreReportedNotMerged() throws IOException {
        load(fixtures());
        TransformReport report = transformService.transform();

        Map<String, Object> chinnaswamy = jdbc.queryForMap("""
                select count(distinct m.venue_id) as venues, min(v.name) as name, min(v.city) as city,
                       bool_and(v.is_curated) as curated, string_agg(m.venue_name, ' + ' order by m.source_match_id) as source_names
                  from core.match m join core.venue v on v.id = m.venue_id where m.source_match_id in (335982, 1000003)""");
        assertThat(chinnaswamy).containsEntry("venues", 1L).containsEntry("name", "M Chinnaswamy Stadium")
                .containsEntry("city", "Bengaluru").containsEntry("curated", true)
                .containsEntry("source_names", "M Chinnaswamy Stadium + M.Chinnaswamy Stadium");
        assertThat(jdbc.queryForList("""
                select va.alias from core.venue_alias va join core.venue v on v.id = va.venue_id
                 where v.name = 'M Chinnaswamy Stadium' order by 1""", String.class)).containsExactly(
                "M Chinnaswamy Stadium", "M Chinnaswamy Stadium, Bangalore", "M Chinnaswamy Stadium, Bengaluru",
                "M.Chinnaswamy Stadium");

        assertThat(jdbc.queryForMap("""
                select v.name, v.city, v.is_curated from core.match m join core.venue v on v.id = m.venue_id
                 where m.source_match_id = 1000001""")).containsEntry("name", "Brabourne Stadium")
                .containsEntry("city", "Mumbai").containsEntry("is_curated", false);
        assertThat(jdbc.queryForObject("select count(*) from core.venue", Integer.class)).isEqualTo(4);
        assertThat(report.warnings()).anySatisfy(w -> assertThat(w).contains("not in reference data", "Brabourne Stadium"));
    }

    @Test
    void playerIdentityUsesCricsheetIdsNotNames() throws IOException {
        Map<String, String> files = fixtures();
        files.put("1000005", files.get("1000001").replace("2010-03-20", "2011-04-10").replace("\"2009/10\"", "\"2011\"")
                .replace("Harbhajan Singh", "H Singh") // same player (id-bhajji), new name
                .replace("G Gambhir", "DP Conway"));   // different player (id-gambhir) sharing a name with id-conway
        load(files);
        TransformReport report = transformService.transform();

        assertThat(jdbc.queryForMap("select display_name from core.player where cricsheet_id = 'id-bhajji'"))
                .containsEntry("display_name", "H Singh");
        assertThat(jdbc.queryForList("""
                select a.name || ' ' || a.first_season || '-' || a.last_season from core.player_alias a
                  join core.player p on p.id = a.player_id where p.cricsheet_id = 'id-bhajji' order by a.first_season""",
                String.class)).containsExactly("Harbhajan Singh 2010-2010", "H Singh 2011-2011");
        assertThat(jdbc.queryForList("""
                select p.cricsheet_id from core.player p join core.player_alias a on a.player_id = p.id
                 where a.name = 'DP Conway' order by 1""", String.class)).containsExactly("id-conway", "id-gambhir");
        assertThat(jdbc.queryForObject("""
                select p.cricsheet_id from core.delivery d join core.player p on p.id = d.batter_id
                  join core.innings i on i.id = d.innings_id join core.match m on m.id = i.match_id
                 where m.source_match_id = 1000005 and i.innings_number = 2""", String.class)).isEqualTo("id-gambhir");
        assertThat(jdbc.queryForObject("select count(*) from core.player where cricsheet_id is null", Integer.class))
                .isZero();
        assertThat(report.warnings())
                .anySatisfy(w -> assertThat(w).contains("more than one name", "Harbhajan Singh | H Singh"))
                .anySatisfy(w -> assertThat(w).contains("DP Conway -> 2 players"));
    }

    @Test
    void transformsMatchDetails() throws IOException {
        load(fixtures());
        transformService.transform();

        Map<String, Object> normal = matchRow(335982);
        assertThat(normal).containsEntry("match_date", LocalDate.parse("2008-04-18").toString())
                .containsEntry("end_date", "2008-04-18").containsEntry("stage", "League").containsEntry("match_number", 1)
                .containsEntry("venue_name", "M Chinnaswamy Stadium").containsEntry("city", "Bangalore")
                .containsEntry("toss_winner", "RCB").containsEntry("toss_decision", "field").containsEntry("winner", "KKR")
                .containsEntry("result", "runs").containsEntry("result_margin", 9).containsEntry("method", null)
                .containsEntry("has_super_over", false).containsEntry("player_of_match", "BB McCullum")
                .containsEntry("target_runs", 10).containsEntry("target_overs", new BigDecimal("20.0"))
                .containsEntry("scheduled_overs", 20).containsEntry("umpires", "Asad Rauf|RE Koertzen")
                .containsEntry("tv_umpires", "AM Saheba").containsEntry("source_revision", 2);
        assertThat(matchRow(1000004)).containsEntry("end_date", "2023-05-29").containsEntry("stage", "Final")
                .containsEntry("match_number", null).containsEntry("method", "D/L").containsEntry("result", "wickets")
                .containsEntry("result_margin", 10).containsEntry("winner", "CSK");
        assertThat(matchRow(1000003)).containsEntry("result", "no result").containsEntry("winner", null)
                .containsEntry("player_of_match", null).containsEntry("result_margin", null);
        assertThat(matchRow(1000002)).containsEntry("result", "tie").containsEntry("winner", "DC")
                .containsEntry("super_over_winner", "DC").containsEntry("has_super_over", true)
                .containsEntry("target_runs", 2);
    }

    @Test
    void createsOneInningsPerSourceInningsWithSuperOverFlags() throws IOException {
        load(fixtures());
        transformService.transform();

        assertThat(jdbc.queryForList("""
                select i.innings_number, bt.code as batting, bw.code as bowling, i.is_super_over
                  from core.innings i join core.match m on m.id = i.match_id
                  join core.team bt on bt.id = i.batting_team_id join core.team bw on bw.id = i.bowling_team_id
                 where m.source_match_id = 1000002 order by 1"""))
                .extracting(r -> r.get("innings_number"), r -> r.get("batting"), r -> r.get("bowling"),
                        r -> r.get("is_super_over"))
                .containsExactly(tuple(1, "DC", "PBKS", false), tuple(2, "PBKS", "DC", false),
                        tuple(3, "PBKS", "DC", true), tuple(4, "DC", "PBKS", true));
        assertThat(jdbc.queryForObject("""
                select count(*) from core.innings i join core.match m on m.id = i.match_id
                 where m.source_match_id = 1000003""", Integer.class)).isEqualTo(1);
    }

    @Test
    void deliveriesKeepEveryBallLevelDetail() throws IOException {
        load(fixtures());
        transformService.transform();

        List<Map<String, Object>> overOne = jdbc.queryForList("""
                select d.ball_in_over, d.actual_delivery, d.batter_runs, d.extra_runs, d.total_runs, d.wides, d.noballs,
                       d.byes, d.legbyes, d.non_boundary, d.is_legal_ball, b.cricsheet_id as batter, bw.cricsheet_id as bowler
                  from core.delivery d join core.innings i on i.id = d.innings_id join core.match m on m.id = i.match_id
                  join core.player b on b.id = d.batter_id join core.player bw on bw.id = d.bowler_id
                 where m.source_match_id = 335982 and i.innings_number = 1 order by d.ball_in_over""");
        assertThat(overOne).extracting(r -> r.get("actual_delivery")).containsExactly("0.1", "0.2", "0.3", "0.3");
        assertThat(overOne.get(0)).containsEntry("legbyes", 1).containsEntry("is_legal_ball", true)
                .containsEntry("batter", "id-ganguly").containsEntry("bowler", "id-kumar");
        assertThat(overOne.get(1)).containsEntry("batter_runs", 4).containsEntry("non_boundary", true);
        assertThat(overOne.get(2)).containsEntry("wides", 1).containsEntry("is_legal_ball", false);
        assertThat(overOne.get(3)).containsEntry("noballs", 1).containsEntry("byes", 2)
                .containsEntry("extra_runs", 3).containsEntry("total_runs", 3)
                .containsEntry("is_legal_ball", false);

        assertThat(jdbc.queryForList("""
                select w.wicket_number, po.cricsheet_id as player_out, w.kind,
                       (select string_agg(fp.cricsheet_id || ':' || f.is_substitute, ',' order by f.fielder_number)
                          from core.wicket_fielder f join core.player fp on fp.id = f.player_id where f.wicket_id = w.id) as fielders
                  from core.wicket w join core.delivery d on d.id = w.delivery_id join core.player po on po.id = w.player_out_id
                  join core.innings i on i.id = d.innings_id join core.match m on m.id = i.match_id
                 where m.source_match_id = 1000002 and i.innings_number = 1 order by w.wicket_number"""))
                .extracting(r -> r.get("wicket_number") + " " + r.get("player_out") + " " + r.get("kind") + " "
                        + r.get("fielders"))
                .containsExactly("1 id-iyer run out id-rahul:false,id-sarfaraz:true", "2 id-pant retired out null");
        assertThat(jdbc.queryForObject("""
                select count(*) from core.delivery d join core.innings i on i.id = d.innings_id
                 where d.is_super_over and i.innings_number > 2""", Integer.class)).isEqualTo(2);
    }

    @Test
    void playingListIncludesPlayersWhoDidNotBatBowlOrField() throws IOException {
        load(fixtures());
        transformService.transform();

        assertThat(jdbc.queryForMap("""
                select t.code, mp.list_position from core.match_player mp join core.player p on p.id = mp.player_id
                  join core.team t on t.id = mp.team_id join core.match m on m.id = mp.match_id
                 where p.cricsheet_id = 'id-zkhan' and m.source_match_id = 335982"""))
                .containsEntry("code", "RCB").containsEntry("list_position", 4);
        assertThat(jdbc.queryForObject("""
                select count(*) from core.delivery d join core.player p on p.id in (d.batter_id, d.bowler_id, d.non_striker_id)
                 where p.cricsheet_id = 'id-zkhan'""", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from core.match_player", Integer.class)).isEqualTo(28);
    }

    @Test
    void rerunningTheTransformChangesNothing() throws IOException {
        load(fixtures());
        TransformReport first = transformService.transform();
        List<Integer> matchIds = jdbc.queryForList("select id from core.match order by source_match_id", Integer.class);

        TransformReport second = transformService.transform();

        assertThat(first.totalChanges()).isPositive();
        assertThat(second.totalChanges()).isZero();
        assertThat(second.coreRowCounts()).isEqualTo(first.coreRowCounts());
        assertThat(jdbc.queryForList("select id from core.match order by source_match_id", Integer.class))
                .isEqualTo(matchIds);
    }

    @Test
    void matchesThatLeaveTheSourceArePrunedWithTheirOrphans() throws IOException {
        load(fixtures());
        transformService.transform();

        Map<String, String> files = fixtures();
        files.remove("1000003"); // the only Sunrisers Hyderabad match and the only 2017 match
        load(files);
        TransformReport report = transformService.transform();

        assertThat(report.passed()).isTrue();
        assertThat(report.changes().get("match").deleted()).isEqualTo(1);
        assertThat(report.coreRowCounts()).containsEntry("match", 4L).containsEntry("season", 4L);
        assertThat(jdbc.queryForList("select code from core.team", String.class)).doesNotContain("SRH");
        assertThat(jdbc.queryForObject("select count(*) from core.player where cricsheet_id = 'id-warner'",
                Integer.class)).isZero();
    }

    @Test
    void reconciliationFailureRollsBackEverything() throws IOException {
        load(fixtures());
        jdbc.update("update staging.matches set target_runs = '11' where id = '335982'"); // first innings made 9

        assertThatThrownBy(() -> transformService.transform())
                .isInstanceOfSatisfying(TransformException.class, e -> assertThat(e.failedChecks())
                        .extracting(CheckResult::name).containsExactly("Chase target = first-innings total + 1 (non-D/L)"));
        assertThat(jdbc.queryForObject("select count(*) from core.match", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from core.delivery", Integer.class)).isZero();
        assertThat(jdbc.queryForMap("select status, summary::text as summary from core.transform_run"))
                .containsEntry("status", "FAILED")
                .satisfies(r -> assertThat((String) r.get("summary")).contains("Chase target"));
    }

    @Test
    void orphanStagingRowsFailValidation() throws IOException {
        load(fixtures());
        jdbc.update("""
                insert into staging.deliveries (ingestion_run_id, source_row_number, match_id, inning, is_super_over,
                    batting_team, bowling_team, "over", ball, batter, bowler, non_striker, batsman_runs, extra_runs,
                    total_runs, is_wicket)
                select ingestion_run_id, 99999, '999999', inning, is_super_over, batting_team, bowling_team, "over",
                       ball, batter, bowler, non_striker, batsman_runs, extra_runs, total_runs, is_wicket
                  from staging.deliveries order by source_row_number limit 1""");

        assertThatThrownBy(() -> transformService.transform())
                .isInstanceOfSatisfying(TransformException.class, e -> assertThat(e.failedChecks())
                        .extracting(CheckResult::detail).anySatisfy(d -> assertThat(d).contains("999999")));
        assertThat(jdbc.queryForObject("select count(*) from core.match", Integer.class)).isZero();
    }

    @Test
    void orphansLeftInCoreAreRemovedAndDetected() throws IOException {
        load(fixtures());
        transformService.transform();
        jdbc.update("insert into core.season (year, source_season) values (2099, '2099')");
        jdbc.update("insert into core.player (cricsheet_id, display_name) values ('ghost', 'Ghost Player')");

        TransformReport report = transformService.transform();

        assertThat(report.passed()).isTrue();
        assertThat(report.changes().get("season").deleted()).isEqualTo(1);
        assertThat(report.changes().get("player").deleted()).isEqualTo(1);
        assertThat(report.checks()).filteredOn(c -> c.name().startsWith("No orphan")).hasSize(6)
                .allMatch(CheckResult::passed);
    }

    // --- helpers ------------------------------------------------------------------------------------------

    private void load(Map<String, String> matches) throws IOException {
        int n = ++loads;
        Path source = Files.createDirectories(dir.resolve("source" + n));
        for (Map.Entry<String, String> match : matches.entrySet()) {
            Files.writeString(source.resolve(match.getKey() + ".json"), match.getValue());
        }
        Path raw = dir.resolve("raw" + n);
        assertThat(converter.convert(source, raw).hasProblems()).isFalse();
        for (DiscoveredFile file : new FileDiscovery().discover(raw).supported()) {
            assertThat(ingestion.ingest(file).outcome()).isIn(Outcome.LOADED, Outcome.SKIPPED);
        }
    }

    private static Map<String, String> fixtures() throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        for (String id : List.of("335982", "1000001", "1000002", "1000003", "1000004")) {
            try (InputStream in = CoreTransformIT.class.getResourceAsStream("/cricsheet/" + id + ".json")) {
                files.put(id, new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return files;
    }

    private List<String> teamsOf(long sourceMatchId) {
        Map<String, Object> row = jdbc.queryForMap("""
                select m.team1_name || '|' || t1.code as team1, m.team2_name || '|' || t2.code as team2
                  from core.match m join core.team t1 on t1.id = m.team1_id join core.team t2 on t2.id = m.team2_id
                 where m.source_match_id = ?""", sourceMatchId);
        return List.of((String) row.get("team1"), (String) row.get("team2"));
    }

    private Map<String, Object> matchRow(long sourceMatchId) {
        return jdbc.queryForMap("""
                select m.match_date::text as match_date, m.end_date::text as end_date, m.stage,
                       m.match_number::int as match_number, m.venue_name, m.city, tw.code as toss_winner,
                       m.toss_decision, w.code as winner, sw.code as super_over_winner, m.result,
                       m.result_margin::int as result_margin, m.method, m.has_super_over,
                       pom.display_name as player_of_match, m.target_runs::int as target_runs, m.target_overs,
                       m.scheduled_overs::int as scheduled_overs, array_to_string(m.umpires, '|') as umpires,
                       array_to_string(m.tv_umpires, '|') as tv_umpires, m.source_revision
                  from core.match m
                  left join core.team tw on tw.id = m.toss_winner_team_id
                  left join core.team w on w.id = m.winner_team_id
                  left join core.team sw on sw.id = m.super_over_winner_team_id
                  left join core.player pom on pom.id = m.player_of_match_id
                 where m.source_match_id = ?""", sourceMatchId);
    }
}
