package com.iplytics.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.iplytics.analytics.AnalyticsRefreshReport.Check;
import com.iplytics.analytics.query.AnalyticsFilter;
import com.iplytics.analytics.query.BattingQueries;
import com.iplytics.analytics.query.BattingQueries.BattingStats;
import com.iplytics.analytics.query.BowlingQueries;
import com.iplytics.analytics.query.BowlingQueries.BowlingStats;
import com.iplytics.analytics.query.HeadToHeadQueries;
import com.iplytics.analytics.query.HeadToHeadQueries.HeadToHead;
import com.iplytics.analytics.query.LeaderboardQueries;
import com.iplytics.analytics.query.LeaderboardQueries.BattingEntry;
import com.iplytics.analytics.query.LeaderboardQueries.BattingMetric;
import com.iplytics.analytics.query.LeaderboardQueries.BowlingEntry;
import com.iplytics.analytics.query.LeaderboardQueries.BowlingMetric;
import com.iplytics.analytics.query.TeamQueries;
import com.iplytics.analytics.query.TeamQueries.TeamStats;
import com.iplytics.analytics.query.VenueQueries;
import com.iplytics.analytics.query.VenueQueries.VenueStats;
import com.iplytics.common.metrics.CricketMetrics;
import com.iplytics.common.metrics.CricketPhase;
import com.iplytics.ingestion.IngestionService;
import com.iplytics.ingestion.cricsheet.CricsheetConverter;
import com.iplytics.support.AbstractIntegrationTest;
import com.iplytics.support.CricsheetPipeline;
import com.iplytics.transform.CoreTransformService;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Analytics on real PostgreSQL. Fixture 2000001 (MI 30/3 beat CSK 20/2 by 10 runs at Wankhede, IPL 2019) has
 * hand-computed expected values: a wide, a no-ball with runs off the bat, leg-byes, byes, penalty runs, a run
 * (non-boundary) four, caught / stumped / bowled, a non-striker run out with two fielders and a retired hurt,
 * across powerplay (over 0), middle (over 7) and death (overs 16, 19). Other fixtures add the super-over tie
 * (1000002), D/L (1000001, 1000004), no result (1000003) and a no-ball with byes (335982).
 */
class AnalyticsIT extends AbstractIntegrationTest {

    @Autowired
    private CricsheetConverter converter;
    @Autowired
    private IngestionService ingestion;
    @Autowired
    private CoreTransformService transform;
    @Autowired
    private AnalyticsRefreshService refresh;
    @Autowired
    private AnalyticsReconciler reconciler;
    @Autowired
    private BattingQueries batting;
    @Autowired
    private BowlingQueries bowling;
    @Autowired
    private TeamQueries teams;
    @Autowired
    private VenueQueries venues;
    @Autowired
    private HeadToHeadQueries headToHead;
    @Autowired
    private LeaderboardQueries leaderboards;
    @Autowired
    private JdbcTemplate jdbc;

    @TempDir
    Path dir;

    private CricsheetPipeline pipeline;
    private AnalyticsRefreshReport report;

    @BeforeEach
    void loadFixtures() throws IOException {
        jdbc.execute("""
                truncate core.wicket_fielder, core.wicket, core.delivery, core.innings, core.match_player, core.match,
                         core.player_alias, core.player, core.venue_alias, core.venue, core.team_name_history, core.team,
                         core.season, core.transform_run, core.ingestion_run, analytics.refresh_run restart identity cascade""");
        pipeline = new CricsheetPipeline(converter, ingestion, dir);
        pipeline.load(CricsheetPipeline.fixtures(CricsheetPipeline.ALL_FIXTURES));
        transform.transform();
        report = refresh.refresh();
    }

    @Test
    void refreshReconcilesEveryObjectAgainstCore() {
        assertThat(report.passed()).isTrue();
        assertThat(report.checks()).hasSizeGreaterThanOrEqualTo(15).allMatch(Check::passed);
        assertThat(report.rowCounts())
                .containsEntry("analytics.innings_summary", count("core.innings"))
                .containsEntry("analytics.team_match_result", 2 * count("core.match"));
        assertThat(jdbc.queryForMap("select status, transform_run_id from analytics.refresh_run"))
                .containsEntry("status", "COMPLETED").containsEntry("transform_run_id", 1L);
    }

    @Test
    void battingInningsApplyTheScoringRules() {
        // wide not faced; no-ball faced with its 2 runs off the bat
        assertThat(battingRow(2000001, 1, "id-rohit")).containsEntry("batting_position", 1).containsEntry("runs", 13)
                .containsEntry("balls", 6).containsEntry("fours", 1).containsEntry("sixes", 1)
                .containsEntry("dot_balls", 2).containsEntry("is_out", true).containsEntry("dismissal_kind", "caught")
                .containsEntry("powerplay_runs", 13).containsEntry("powerplay_balls", 6);
        assertThat((BigDecimal) battingRow(2000001, 1, "id-rohit").get("strike_rate")).isEqualByComparingTo("216.67");
        // leg-bye counts as a ball faced, not as runs; retired hurt is not a dismissal
        assertThat(battingRow(2000001, 1, "id-dekock")).containsEntry("batting_position", 2).containsEntry("runs", 0)
                .containsEntry("balls", 2).containsEntry("is_out", false).containsEntry("dismissal_kind", "retired hurt")
                .containsEntry("powerplay_balls", 1).containsEntry("middle_balls", 1);
        // a run four is not a boundary; byes are not the batter's runs
        assertThat(battingRow(2000001, 1, "id-sky")).containsEntry("batting_position", 3).containsEntry("runs", 4)
                .containsEntry("balls", 3).containsEntry("fours", 0).containsEntry("dismissal_kind", "stumped")
                .containsEntry("middle_runs", 4).containsEntry("middle_balls", 3);
        // run out at the non-striker's end is a dismissal of the batter
        assertThat(battingRow(2000001, 1, "id-pollard")).containsEntry("batting_position", 4).containsEntry("runs", 2)
                .containsEntry("is_out", true).containsEntry("dismissal_kind", "run out");
        // first appeared as non-striker; penalty runs are not the batter's
        assertThat(battingRow(2000001, 1, "id-bumrah")).containsEntry("batting_position", 5).containsEntry("runs", 1)
                .containsEntry("balls", 2).containsEntry("is_out", false).containsEntry("death_runs", 1)
                .containsEntry("death_balls", 2);
        // retired OUT is a dismissal
        assertThat(battingRow(1000002, 1, "id-pant")).containsEntry("is_out", true)
                .containsEntry("dismissal_kind", "retired out");
    }

    @Test
    void bowlingInningsApplyTheScoringRules() {
        // wide and no-ball are not legal balls but are charged; penalty runs are not charged; run out not credited
        Map<String, Object> chahar = bowlingRow(2000001, 1, "id-chahar");
        assertThat(chahar).containsEntry("legal_balls", 8).containsEntry("runs_conceded", 16)
                .containsEntry("wickets", 1).containsEntry("dot_balls", 4).containsEntry("fours_conceded", 1)
                .containsEntry("sixes_conceded", 1).containsEntry("wides", 1).containsEntry("wide_runs", 1)
                .containsEntry("no_balls", 1).containsEntry("no_ball_runs", 1)
                .containsEntry("powerplay_balls", 6).containsEntry("powerplay_runs", 15)
                .containsEntry("death_balls", 2).containsEntry("death_runs", 1);
        assertThat((BigDecimal) chahar.get("economy")).isEqualByComparingTo("12.00");
        // byes not charged (a dot ball for the bowler); stumping credited; retired hurt not credited; run four conceded but not a boundary
        assertThat(bowlingRow(2000001, 1, "id-tahir")).containsEntry("legal_balls", 6).containsEntry("runs_conceded", 6)
                .containsEntry("wickets", 1).containsEntry("dot_balls", 3).containsEntry("fours_conceded", 0)
                .containsEntry("middle_balls", 6).containsEntry("middle_runs", 6);
        // multiple extras on one ball (no-ball + 2 byes): only the no-ball run is charged; leg-bye not charged
        assertThat(bowlingRow(335982, 1, "id-kumar")).containsEntry("legal_balls", 2).containsEntry("runs_conceded", 6)
                .containsEntry("wides", 1).containsEntry("no_balls", 1).containsEntry("dot_balls", 1)
                .containsEntry("fours_conceded", 0);
    }

    @Test
    void inningsSummaryAndPhases() {
        Map<String, Object> first = inningsRow(2000001, 1);
        assertThat(first).containsEntry("total_runs", 30).containsEntry("extras", 10).containsEntry("wickets", 3)
                .containsEntry("legal_balls", 14).containsEntry("overs", "2.2").containsEntry("fours", 1)
                .containsEntry("sixes", 1).containsEntry("dot_balls", 4)
                .containsEntry("powerplay_runs", 16).containsEntry("middle_runs", 8).containsEntry("death_runs", 6)
                .containsEntry("powerplay_wickets", 1).containsEntry("middle_wickets", 1).containsEntry("death_wickets", 1)
                .containsEntry("is_super_over", false);
        assertThat((BigDecimal) first.get("run_rate")).isEqualByComparingTo("12.86");
        assertThat(inningsRow(2000001, 2)).containsEntry("total_runs", 20).containsEntry("wickets", 2)
                .containsEntry("fours", 3).containsEntry("powerplay_runs", 16).containsEntry("death_runs", 4);
        assertThat(inningsRow(1000002, 3)).containsEntry("is_super_over", true).containsEntry("powerplay_runs", 0)
                .containsEntry("death_runs", 0);
    }

    @Test
    void teamMatchResults() {
        assertThat(teamRow(2000001, "MI")).containsEntry("won", true).containsEntry("lost", false)
                .containsEntry("batted_first", true).containsEntry("chased", false).containsEntry("toss_won", false)
                .containsEntry("is_home", true).containsEntry("runs_for", 30).containsEntry("runs_against", 20)
                .containsEntry("wickets_lost", 3).containsEntry("wickets_taken", 2);
        assertThat(teamRow(2000001, "CSK")).containsEntry("lost", true).containsEntry("chased", true)
                .containsEntry("toss_won", true).containsEntry("is_home", false);
        assertThat(teamRow(1000003, "SRH")).containsEntry("no_result", true).containsEntry("won", false)
                .containsEntry("lost", false).containsEntry("runs_against", null);
        assertThat(teamRow(1000001, "MI")).containsEntry("won", true).containsEntry("method", "D/L");
        // super-over tie: tied for both, won by the super-over winner; runs exclude the super overs
        assertThat(teamRow(1000002, "DC")).containsEntry("tied", true).containsEntry("won", true).containsEntry("runs_for", 1);
        assertThat(teamRow(1000002, "PBKS")).containsEntry("tied", true).containsEntry("lost", true).containsEntry("runs_for", 1);
    }

    @Test
    void superOversAreExcludedUnlessRequested() {
        int rahul = playerId("id-rahul");
        BattingStats regular = batting.career(rahul, AnalyticsFilter.all());
        BattingStats withSuperOvers = batting.career(rahul, AnalyticsFilter.all().withSuperOvers());
        assertThat(regular.innings()).isEqualTo(1);
        assertThat(regular.dismissals()).isZero();
        assertThat(withSuperOvers.innings()).isEqualTo(2);
        assertThat(withSuperOvers.dismissals()).isEqualTo(1);
        assertThat(bowling.career(playerId("id-rabada"), AnalyticsFilter.all()).legalBalls()).isEqualTo(1);
        assertThat(bowling.career(playerId("id-rabada"), AnalyticsFilter.all().withSuperOvers()).legalBalls()).isEqualTo(2);
    }

    @Test
    void playerQueriesComputeRatiosWithCricketMetrics() {
        BattingStats rohit = batting.career(playerId("id-rohit"), AnalyticsFilter.all());
        assertThat(rohit.runs()).isEqualTo(13);
        assertThat(rohit.average()).isEqualByComparingTo("13.00");
        assertThat(rohit.strikeRate()).isEqualByComparingTo("216.67");
        assertThat(rohit.dotBallPercentage()).isEqualByComparingTo("33.33");
        assertThat(rohit.boundaryPercentage()).isEqualByComparingTo("33.33");
        assertThat(rohit.ballsPerBoundary()).isEqualByComparingTo("3.00");
        assertThat(rohit.highestScore()).isEqualTo(13);
        assertThat(rohit.highestScoreNotOut()).isFalse();
        assertThat(batting.bySeason(playerId("id-rohit"), AnalyticsFilter.all())).extracting(s -> s.seasonYear())
                .containsExactly(2019);
        assertThat(batting.career(playerId("id-rohit"), AnalyticsFilter.all().season(2020)).average()).isNull();

        BowlingStats chahar = bowling.career(playerId("id-chahar"), AnalyticsFilter.all());
        assertThat(chahar.overs()).isEqualTo("1.2");
        assertThat(chahar.economy()).isEqualByComparingTo("12.00");
        assertThat(chahar.average()).isEqualByComparingTo("16.00");
        assertThat(chahar.strikeRate()).isEqualByComparingTo("8.00");
        assertThat(chahar.dotBallPercentage()).isEqualByComparingTo("50.00");
        assertThat(chahar.bestWickets()).isEqualTo(1);
        assertThat(chahar.bestRuns()).isEqualTo(16);
    }

    @Test
    void teamVenueAndHeadToHeadQueries() {
        TeamStats mi = teams.summary(teamId("MI"), AnalyticsFilter.all());
        assertThat(mi.overall().played()).isEqualTo(2);
        assertThat(mi.overall().winPercentage()).isEqualByComparingTo("100.00");
        assertThat(mi.battingFirst().won()).isEqualTo(2);
        assertThat(mi.chasing().played()).isZero();
        assertThat(mi.chasing().winPercentage()).isNull();
        assertThat(mi.home().played()).isEqualTo(2); // Wankhede and Brabourne are both in Mumbai
        assertThat(mi.averageScore()).isEqualByComparingTo("18.00");
        assertThat(mi.averageConceded()).isEqualByComparingTo("10.50");
        TeamStats srh = teams.summary(teamId("SRH"), AnalyticsFilter.all());
        assertThat(srh.overall().noResult()).isEqualTo(1);
        assertThat(srh.overall().winPercentage()).isNull(); // no-results excluded from win %

        VenueStats wankhede = venues.summary(venueIdOf(2000001), AnalyticsFilter.all());
        assertThat(wankhede.matches()).isEqualTo(1);
        assertThat(wankhede.battingFirstWinPercentage()).isEqualByComparingTo("100.00");
        assertThat(wankhede.chaseSuccessPercentage()).isEqualByComparingTo("0.00");
        assertThat(wankhede.averageFirstInningsScore()).isEqualByComparingTo("30.00");
        assertThat(wankhede.highestTotal()).isEqualTo(30);
        assertThat(wankhede.lowestCompletedTotal()).isNull(); // neither innings was all out or ran its full 20 overs
        assertThat(wankhede.averageRunRate()).isEqualByComparingTo("13.64");

        HeadToHead miCsk = headToHead.overall(teamId("MI"), teamId("CSK"), AnalyticsFilter.all());
        assertThat(miCsk.matches()).isEqualTo(1);
        assertThat(miCsk.teamAWins()).isEqualTo(1);
        assertThat(miCsk.teamAWinsBattingFirst()).isEqualTo(1);
        assertThat(headToHead.overall(teamId("CSK"), teamId("MI"), AnalyticsFilter.all()).teamBWinsBattingFirst())
                .isEqualTo(1);
        assertThat(headToHead.bySeason(teamId("MI"), teamId("CSK"), AnalyticsFilter.all()))
                .extracting(HeadToHeadQueries.Grouped::key).containsExactly(2019);
        HeadToHead dcPbks = headToHead.overall(teamId("DC"), teamId("PBKS"), AnalyticsFilter.all());
        assertThat(dcPbks.ties()).isEqualTo(1);
        assertThat(dcPbks.teamAWins()).isEqualTo(1);
    }

    @Test
    void leaderboardsUseMinimumSamplesAndTheCricketMetricsFormulas() {
        List<BattingEntry> runs = leaderboards.batting(BattingMetric.RUNS, AnalyticsFilter.all(), null, 3);
        assertThat(runs.get(0).playerName()).isEqualTo("RG Sharma");
        assertThat(runs).extracting(BattingEntry::rank).containsExactly(1, 2, 3);
        // default minimum for strike rate is 100 balls: no fixture player qualifies
        assertThat(leaderboards.batting(BattingMetric.STRIKE_RATE, AnalyticsFilter.all(), null, 10)).isEmpty();
        List<BattingEntry> strikeRates = leaderboards.batting(BattingMetric.STRIKE_RATE, AnalyticsFilter.all(), 3, 10);
        assertThat(strikeRates).isNotEmpty().allSatisfy(e -> {
            assertThat(e.balls()).isGreaterThanOrEqualTo(3);
            assertThat(e.value()).isEqualByComparingTo(CricketMetrics.strikeRate(e.runs(), e.balls()));
        });
        assertThat(strikeRates).extracting(BattingEntry::value).isSortedAccordingTo((a, b) -> b.compareTo(a));

        assertThat(leaderboards.bowling(BowlingMetric.ECONOMY, AnalyticsFilter.all(), null, 10)).isEmpty();
        List<BowlingEntry> economies = leaderboards.bowling(BowlingMetric.ECONOMY, AnalyticsFilter.all(), 6, 10);
        assertThat(economies).isNotEmpty().allSatisfy(e ->
                assertThat(e.value()).isEqualByComparingTo(CricketMetrics.economy(e.runsConceded(), e.legalBalls())));
        assertThat(economies).extracting(BowlingEntry::value).isSorted();
        assertThat(leaderboards.bowling(BowlingMetric.WICKETS, AnalyticsFilter.all(), null, 1).get(0).wickets())
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void sqlFormulasAndPhasesMatchCricketMetricsExactly() {
        List<Map<String, Object>> grid = jdbc.queryForList("""
                select a, b, analytics.strike_rate(a, b) sr, analytics.batting_average(a, b) avg, analytics.economy(a, b) eco,
                       analytics.run_rate(a, b) rr, analytics.bowling_average(a, b) bavg, analytics.bowling_strike_rate(a, b) bsr,
                       analytics.percentage(a, b) pct, analytics.balls_per_boundary(a, b) bpb, analytics.overs(a) overs
                  from generate_series(0, 60) a, generate_series(0, 40) b""");
        assertThat(grid).hasSize(61 * 41).allSatisfy(r -> {
            long a = ((Number) r.get("a")).longValue();
            long b = ((Number) r.get("b")).longValue();
            assertSame(r.get("sr"), CricketMetrics.strikeRate(a, b));
            assertSame(r.get("avg"), CricketMetrics.battingAverage(a, b));
            assertSame(r.get("eco"), CricketMetrics.economy(a, b));
            assertSame(r.get("rr"), CricketMetrics.runRate(a, b));
            assertSame(r.get("bavg"), CricketMetrics.bowlingAverage(a, b));
            assertSame(r.get("bsr"), CricketMetrics.bowlingStrikeRate(a, b));
            assertSame(r.get("pct"), CricketMetrics.percentage(a, b));
            assertSame(r.get("bpb"), CricketMetrics.ballsPerBoundary(a, b));
            assertThat(r.get("overs")).isEqualTo(CricketMetrics.overs(a));
        });
        jdbc.queryForList("select w, p, n, analytics.win_percentage(w, p, n) v from generate_series(0, 5) w,"
                + " generate_series(0, 8) p, generate_series(0, 3) n where w <= p").forEach(r -> assertSame(r.get("v"),
                CricketMetrics.winPercentage(((Number) r.get("w")).longValue(), ((Number) r.get("p")).longValue(),
                        ((Number) r.get("n")).longValue())));
        for (int over = 0; over <= 25; over++) {
            for (boolean superOver : new boolean[] {false, true}) {
                assertThat(jdbc.queryForObject("select analytics.phase(?, ?)", String.class, over, superOver))
                        .isEqualTo(CricketPhase.of(over, superOver).sqlName());
            }
        }
        // every stored ratio equals CricketMetrics applied to the stored raw counts
        jdbc.queryForList("select runs, balls, strike_rate from analytics.batting_innings").forEach(r -> assertSame(
                r.get("strike_rate"), CricketMetrics.strikeRate((Integer) r.get("runs"), (Integer) r.get("balls"))));
        jdbc.queryForList("select runs_conceded, legal_balls, economy from analytics.bowling_innings").forEach(r ->
                assertSame(r.get("economy"),
                        CricketMetrics.economy((Integer) r.get("runs_conceded"), (Integer) r.get("legal_balls"))));
        jdbc.queryForList("select total_runs, legal_balls, run_rate from analytics.innings_summary").forEach(r ->
                assertSame(r.get("run_rate"),
                        CricketMetrics.runRate((Integer) r.get("total_runs"), (Integer) r.get("legal_balls"))));
    }

    @Test
    void refreshPicksUpCoreChanges() throws IOException {
        pipeline.load(CricsheetPipeline.fixtures(CricsheetPipeline.ALL_FIXTURES.subList(0, 5))); // 2000001 leaves
        transform.transform();
        assertThat(battingRowCount(2000001)).isPositive(); // stale until refreshed

        AnalyticsRefreshReport second = refresh.refresh();

        assertThat(second.passed()).isTrue();
        assertThat(battingRowCount(2000001)).isZero();
        assertThat(second.rowCounts()).containsEntry("analytics.innings_summary", count("core.innings"));
    }

    @Test
    void reconcilerDetectsAnalyticsThatNoLongerMatchCore() {
        jdbc.update("delete from core.wicket where kind = 'stumped'");

        assertThat(reconciler.check(jdbc)).filteredOn(c -> !c.passed()).extracting(Check::name)
                .contains("Batting runs, balls faced, boundaries and dismissals = core",
                        "Bowling legal balls, runs conceded and credited wickets = core");
        assertThat(refresh.refresh().passed()).isTrue(); // a refresh brings analytics back in line with core
    }

    // --- helpers ------------------------------------------------------------------------------------------

    private static void assertSame(Object sqlValue, BigDecimal javaValue) {
        if (javaValue == null) {
            assertThat(sqlValue).isNull();
        } else {
            assertThat((BigDecimal) sqlValue).isEqualByComparingTo(javaValue);
        }
    }

    private Map<String, Object> battingRow(long sourceMatchId, int innings, String cricsheetId) {
        return jdbc.queryForMap("""
                select b.* from analytics.batting_innings b join core.player p on p.id = b.player_id
                  join core.match m on m.id = b.match_id
                 where m.source_match_id = ? and b.innings_number = ? and p.cricsheet_id = ?""",
                sourceMatchId, innings, cricsheetId);
    }

    private Map<String, Object> bowlingRow(long sourceMatchId, int innings, String cricsheetId) {
        return jdbc.queryForMap("""
                select b.* from analytics.bowling_innings b join core.player p on p.id = b.player_id
                  join core.match m on m.id = b.match_id
                 where m.source_match_id = ? and b.innings_number = ? and p.cricsheet_id = ?""",
                sourceMatchId, innings, cricsheetId);
    }

    private Map<String, Object> inningsRow(long sourceMatchId, int innings) {
        return jdbc.queryForMap("""
                select s.* from analytics.innings_summary s join core.match m on m.id = s.match_id
                 where m.source_match_id = ? and s.innings_number = ?""", sourceMatchId, innings);
    }

    private Map<String, Object> teamRow(long sourceMatchId, String teamCode) {
        return jdbc.queryForMap("""
                select t.* from analytics.team_match_result t join core.match m on m.id = t.match_id
                  join core.team tm on tm.id = t.team_id where m.source_match_id = ? and tm.code = ?""",
                sourceMatchId, teamCode);
    }

    private long battingRowCount(long sourceMatchId) {
        return jdbc.queryForObject("""
                select count(*) from analytics.batting_innings b where not exists (
                    select 1 from core.match m where m.id = b.match_id and m.source_match_id <> ?)""",
                Long.class, sourceMatchId);
    }

    private int playerId(String cricsheetId) {
        return jdbc.queryForObject("select id from core.player where cricsheet_id = ?", Integer.class, cricsheetId);
    }

    private int teamId(String code) {
        return jdbc.queryForObject("select id from core.team where code = ?", Integer.class, code);
    }

    private int venueIdOf(long sourceMatchId) {
        return jdbc.queryForObject("select venue_id from core.match where source_match_id = ?", Integer.class,
                sourceMatchId);
    }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }
}
