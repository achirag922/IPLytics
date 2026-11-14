package com.iplytics.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iplytics.analytics.AnalyticsRefreshService;
import com.iplytics.ingestion.IngestionService;
import com.iplytics.ingestion.cricsheet.CricsheetConverter;
import com.iplytics.support.AbstractIntegrationTest;
import com.iplytics.support.CricsheetPipeline;
import com.iplytics.transform.CoreTransformService;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Every V1.4 endpoint against the six fixture matches, end to end (Flyway schema, transform, analytics refresh,
 * web layer). Expected values are worked out by hand from src/test/resources/cricsheet/2000001.json (MI 30/3 in
 * 2.2 overs beat CSK 20/2 in 1.2 overs by 10 runs, IPL 2019) unless stated otherwise.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CricsheetConverter converter;
    @Autowired
    private IngestionService ingestion;
    @Autowired
    private CoreTransformService transform;
    @Autowired
    private AnalyticsRefreshService refresh;

    @TempDir
    static Path dir;

    private int mi;
    private int csk;
    private int rohit;
    private int dhoni;
    private int chahar;
    private int bumrah;
    private int match2019;
    private int wankhede;

    @BeforeAll
    void loadFixtures() throws IOException {
        jdbc.execute("""
                truncate core.wicket_fielder, core.wicket, core.delivery, core.innings, core.match_player, core.match,
                         core.player_alias, core.player, core.venue_alias, core.venue, core.team_name_history, core.team,
                         core.season, core.transform_run, core.ingestion_run, analytics.refresh_run restart identity cascade""");
        new CricsheetPipeline(converter, ingestion, dir).load(CricsheetPipeline.fixtures(CricsheetPipeline.ALL_FIXTURES));
        transform.transform();
        refresh.refresh();
        mi = id("select id from core.team where code = 'MI'");
        csk = id("select id from core.team where code = 'CSK'");
        rohit = player("id-rohit");
        dhoni = player("id-dhoni");
        chahar = player("id-chahar");
        bumrah = player("id-bumrah");
        match2019 = id("select id from core.match where source_match_id = 2000001");
        wankhede = id("select venue_id from core.match where source_match_id = 2000001");
    }

    // ---- reference -------------------------------------------------------------------------------------------

    @Test
    void referenceListsAreOneFullPageWithDataVersion() throws Exception {
        call("/api/v1/seasons")
                .andExpect(jsonPath("$.data[*].year", contains(2008, 2010, 2017, 2019, 2020, 2023)))
                .andExpect(jsonPath("$.pagination.totalElements").value(6))
                .andExpect(jsonPath("$.meta.dataVersion").value("t1-a1"))
                .andExpect(header().string(HttpHeaders.ETAG, "W/\"t1-a1\""));
        call("/api/v1/teams").andExpect(jsonPath("$.data[*].code", hasItem("MI")));
        call("/api/v1/venues").andExpect(jsonPath("$.data[*].id", hasItem(wankhede)));
        call("/api/v1/teams/" + mi).andExpect(jsonPath("$.data.name").value("Mumbai Indians"));
        call("/api/v1/venues/" + wankhede).andExpect(jsonPath("$.data.matches").value(1));
    }

    @Test
    void playerSearchIsPagedAndMatchesAnyName() throws Exception {
        call("/api/v1/players?search=sharma")
                .andExpect(jsonPath("$.data[0].id").value(rohit))
                .andExpect(jsonPath("$.pagination.totalElements").value(1));
        call("/api/v1/players?size=3&page=1")
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.pagination.page").value(1))
                .andExpect(jsonPath("$.pagination.totalPages").value(greaterThanOrEqualTo(2)));
        call("/api/v1/players/" + rohit)
                .andExpect(jsonPath("$.data.sourceId").value("id-rohit"))
                .andExpect(jsonPath("$.data.teams[0].team.code").value("MI"));
    }

    // ---- matches ---------------------------------------------------------------------------------------------

    @Test
    void matchesAreNewestFirstAndFilterable() throws Exception {
        call("/api/v1/matches?size=4")
                .andExpect(jsonPath("$.data[0].season").value(2023))
                .andExpect(jsonPath("$.pagination.totalElements").value(6))
                .andExpect(jsonPath("$.pagination.totalPages").value(2));
        call("/api/v1/matches?team=" + mi).andExpect(jsonPath("$.pagination.totalElements").value(2));
        call("/api/v1/matches?team=" + mi + "&opponent=" + csk)
                .andExpect(jsonPath("$.data[0].id").value(match2019))
                .andExpect(jsonPath("$.data[0].resultText").value("Mumbai Indians won by 10 runs"))
                .andExpect(jsonPath("$.data[0].innings[0].runs").value(30))
                .andExpect(jsonPath("$.data[0].innings[0].overs").value("2.2"))
                .andExpect(jsonPath("$.data[0].innings[1].wickets").value(2));
        call("/api/v1/matches?dateFrom=2019-04-10&dateTo=2019-04-10").andExpect(jsonPath("$.data.length()").value(1));
        call("/api/v1/matches?stage=final").andExpect(jsonPath("$.data[0].season").value(2023));
        call("/api/v1/matches?season=2017").andExpect(jsonPath("$.data[0].resultText").value("No result"));
        call("/api/v1/matches?seasonFrom=2017&seasonTo=2020")
                .andExpect(jsonPath("$.data[*].season", contains(2020, 2019, 2017)));
        call("/api/v1/matches?season=2020")
                .andExpect(jsonPath("$.data[0].resultText").value("Match tied (Delhi Capitals won the super over)"))
                .andExpect(jsonPath("$.data[0].innings.length()").value(4));
    }

    @Test
    void matchListRejectsUnknownFilterIds() throws Exception {
        mvc.perform(get("/api/v1/matches?team=999")).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/matches?venue=999")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void matchDetailHasTossResultTargetAndPlayerOfTheMatch() throws Exception {
        call("/api/v1/matches/" + match2019)
                .andExpect(jsonPath("$.data.toss.winner.code").value("CSK"))
                .andExpect(jsonPath("$.data.toss.decision").value("field"))
                .andExpect(jsonPath("$.data.winner.code").value("MI"))
                .andExpect(jsonPath("$.data.result").value("runs"))
                .andExpect(jsonPath("$.data.resultMargin").value(10))
                .andExpect(jsonPath("$.data.playerOfMatch.id").value(rohit))
                .andExpect(jsonPath("$.data.target.runs").value(31))
                .andExpect(jsonPath("$.data.officials.umpires.length()").value(2))
                .andExpect(jsonPath("$.data.venue.id").value(wankhede));
        mvc.perform(get("/api/v1/matches/999999")).andExpect(status().isNotFound());
    }

    @Test
    void scorecardMatchesTheBallByBallData() throws Exception {
        String first = "$.data.innings[0]";
        call("/api/v1/matches/" + match2019 + "/scorecard")
                .andExpect(jsonPath("$.data.match.id").value(match2019))
                .andExpect(jsonPath(first + ".battingTeam.code").value("MI"))
                .andExpect(jsonPath(first + ".runs").value(30))
                .andExpect(jsonPath(first + ".wickets").value(3))
                .andExpect(jsonPath(first + ".overs").value("2.2"))
                .andExpect(jsonPath(first + ".runRate").value(12.86))
                .andExpect(jsonPath(first + ".extras.total").value(10))
                .andExpect(jsonPath(first + ".extras.wides").value(1))
                .andExpect(jsonPath(first + ".extras.noBalls").value(1))
                .andExpect(jsonPath(first + ".extras.byes").value(2))
                .andExpect(jsonPath(first + ".extras.legByes").value(1))
                .andExpect(jsonPath(first + ".extras.penalty").value(5))
                // batting, in order of arrival
                .andExpect(jsonPath(first + ".batting[*].player.name",
                        contains("RG Sharma", "Q de Kock", "SA Yadav", "KA Pollard", "JJ Bumrah")))
                .andExpect(jsonPath(first + ".batting[0].dismissal").value("c MS Dhoni b DL Chahar"))
                .andExpect(jsonPath(first + ".batting[0].runs").value(13))
                .andExpect(jsonPath(first + ".batting[0].balls").value(6))
                .andExpect(jsonPath(first + ".batting[0].strikeRate").value(216.67))
                .andExpect(jsonPath(first + ".batting[0].bowler.id").value(chahar))
                .andExpect(jsonPath(first + ".batting[1].dismissal").value("retired hurt"))
                .andExpect(jsonPath(first + ".batting[1].out").value(false))
                .andExpect(jsonPath(first + ".batting[2].dismissal").value("st MS Dhoni b Imran Tahir"))
                .andExpect(jsonPath(first + ".batting[2].runs").value(4))
                .andExpect(jsonPath(first + ".batting[2].fours").value(0))
                .andExpect(jsonPath(first + ".batting[3].dismissal").value("run out (F du Plessis/SR Watson)"))
                .andExpect(jsonPath(first + ".batting[3].bowler").doesNotExist())
                .andExpect(jsonPath(first + ".batting[4].dismissal").value("not out"))
                .andExpect(jsonPath(first + ".didNotBat", empty()))
                // bowling, in bowling order
                .andExpect(jsonPath(first + ".bowling[*].player.name", contains("DL Chahar", "Imran Tahir")))
                .andExpect(jsonPath(first + ".bowling[0].overs").value("1.2"))
                .andExpect(jsonPath(first + ".bowling[0].runsConceded").value(16))
                .andExpect(jsonPath(first + ".bowling[0].wickets").value(1))
                .andExpect(jsonPath(first + ".bowling[0].economy").value(12.00))
                .andExpect(jsonPath(first + ".bowling[0].wides").value(1))
                .andExpect(jsonPath(first + ".bowling[0].noBalls").value(1))
                .andExpect(jsonPath(first + ".bowling[1].runsConceded").value(6))
                .andExpect(jsonPath(first + ".bowling[1].wickets").value(1))
                // fall of wickets: the retirement is not one
                .andExpect(jsonPath(first + ".fallOfWickets[*].score", contains(16, 22, 25)))
                .andExpect(jsonPath(first + ".fallOfWickets[*].over", contains("0.6", "7.3", "16.1")))
                .andExpect(jsonPath(first + ".fallOfWickets[2].player.name").value("KA Pollard"))
                // second innings
                .andExpect(jsonPath("$.data.innings[1].target").value(31))
                .andExpect(jsonPath("$.data.innings[1].didNotBat[*].name", contains("DL Chahar", "Imran Tahir")))
                .andExpect(jsonPath("$.data.innings[1].batting[0].dismissal").value("b JJ Bumrah"))
                .andExpect(jsonPath("$.data.innings[1].batting[2].dismissal").value("c RG Sharma b KA Pollard"))
                .andExpect(jsonPath("$.data.innings[1].fallOfWickets[*].over", contains("0.3", "19.2")));
    }

    @Test
    void progressionGivesManhattanAndWormSeries() throws Exception {
        String overs = "$.data.innings[0].overs";
        call("/api/v1/matches/" + match2019 + "/progression")
                .andExpect(jsonPath(overs + "[*].over", contains(1, 8, 17)))
                .andExpect(jsonPath(overs + "[*].runs", contains(16, 8, 6)))
                .andExpect(jsonPath(overs + "[*].wickets", contains(1, 1, 1)))
                .andExpect(jsonPath(overs + "[*].extras", contains(3, 2, 5)))
                .andExpect(jsonPath(overs + "[*].cumulativeRuns", contains(16, 24, 30)))
                .andExpect(jsonPath(overs + "[2].runRate").value(12.86))
                .andExpect(jsonPath("$.data.innings[1].target").value(31))
                .andExpect(jsonPath("$.data.innings[1].overs[*].cumulativeWickets", contains(1, 2)));
    }

    // ---- players ---------------------------------------------------------------------------------------------

    @Test
    void playerFiguresComeFromTheAnalyticsLayer() throws Exception {
        call("/api/v1/players/" + rohit + "/batting?season=2019")
                .andExpect(jsonPath("$.data.summary.matches").value(1))
                .andExpect(jsonPath("$.data.summary.runs").value(13))
                .andExpect(jsonPath("$.data.summary.balls").value(6))
                .andExpect(jsonPath("$.data.summary.strikeRate").value(216.67))
                .andExpect(jsonPath("$.data.bySeason[0].season").value(2019));
        call("/api/v1/players/" + chahar + "/bowling?season=2019")
                .andExpect(jsonPath("$.data.summary.legalBalls").value(8))
                .andExpect(jsonPath("$.data.summary.runsConceded").value(16))
                .andExpect(jsonPath("$.data.summary.wickets").value(1))
                .andExpect(jsonPath("$.data.summary.economy").value(12.00))
                .andExpect(jsonPath("$.data.summary.bestFigures").value("1/16"));
        call("/api/v1/players/" + rohit + "/phases?season=2019").andExpect(jsonPath("$.data.batting[0].phase").value("powerplay"));
        call("/api/v1/players/" + rohit + "/vs-teams?season=2019")
                .andExpect(jsonPath("$.data.opponents[0].opponent.code").value("CSK"))
                .andExpect(jsonPath("$.data.opponents[0].batting.runs").value(13));
        call("/api/v1/players/" + rohit + "/venues?season=2019")
                .andExpect(jsonPath("$.data.venues[0].venue.id").value(wankhede));
        mvc.perform(get("/api/v1/players/999999/batting")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/players/" + rohit + "/batting?opponent=999")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void comparisonKeepsRequestOrderAndReportsEmptyDisciplines() throws Exception {
        call("/api/v1/players/compare?ids=" + dhoni + "," + rohit + "&season=2019")
                .andExpect(jsonPath("$.data.discipline").value("batting"))
                .andExpect(jsonPath("$.data.players[*].player.id", contains(dhoni, rohit)))
                .andExpect(jsonPath("$.data.players[0].batting.runs").value(4))
                .andExpect(jsonPath("$.data.players[1].batting.runs").value(13))
                .andExpect(jsonPath("$.data.players[0].bowling").doesNotExist());
        call("/api/v1/players/compare?ids=" + rohit + "," + chahar + "&discipline=bowling&season=2019")
                .andExpect(jsonPath("$.data.players[0].bowling.wickets").value(0))
                .andExpect(jsonPath("$.data.players[0].bowling.economy").doesNotExist())
                .andExpect(jsonPath("$.data.players[1].bowling.wickets").value(1));
        mvc.perform(get("/api/v1/players/compare?ids=" + rohit + ",999999")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/players/compare?ids=" + rohit + "," + rohit)).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void matchupsCountOnlyTheBatterAgainstTheBowler() throws Exception {
        call("/api/v1/matchups?batterId=" + rohit + "&bowlerId=" + chahar)
                .andExpect(jsonPath("$.data.occurred").value(true))
                .andExpect(jsonPath("$.data.balls").value(6))
                .andExpect(jsonPath("$.data.runs").value(13))
                .andExpect(jsonPath("$.data.dismissals").value(1))
                .andExpect(jsonPath("$.data.fours").value(1))
                .andExpect(jsonPath("$.data.sixes").value(1))
                .andExpect(jsonPath("$.data.strikeRate").value(216.67))
                .andExpect(jsonPath("$.data.dotBallPercentage").value(33.33))
                .andExpect(jsonPath("$.data.boundaryPercentage").value(33.33));
        call("/api/v1/matchups?batterId=" + rohit + "&bowlerId=" + bumrah)
                .andExpect(jsonPath("$.data.occurred").value(false))
                .andExpect(jsonPath("$.data.balls").value(0))
                .andExpect(jsonPath("$.data.strikeRate").doesNotExist());
        mvc.perform(get("/api/v1/matchups?batterId=" + rohit + "&bowlerId=999999")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/matchups?batterId=" + rohit + "&bowlerId=" + rohit))
                .andExpect(status().isUnprocessableEntity());
    }

    // ---- teams, venues, seasons, rivalries, leaderboards -----------------------------------------------------

    @Test
    void teamRecordsAndTopPerformers() throws Exception {
        call("/api/v1/teams/" + mi + "/stats")
                .andExpect(jsonPath("$.data.overall.played").value(2))
                .andExpect(jsonPath("$.data.overall.won").value(2))
                .andExpect(jsonPath("$.data.battingFirst.won").value(2));
        call("/api/v1/teams/" + mi + "/seasons").andExpect(jsonPath("$.data[*].season", contains(2010, 2019)));
        call("/api/v1/teams/" + mi + "/top-performers?season=2019&limit=1")
                .andExpect(jsonPath("$.data.batting[0].player.id").value(rohit))
                .andExpect(jsonPath("$.data.batting[0].runs").value(13));
        mvc.perform(get("/api/v1/teams/999/stats")).andExpect(status().isNotFound());
    }

    @Test
    void venueStats() throws Exception {
        call("/api/v1/venues/" + wankhede + "/stats")
                .andExpect(jsonPath("$.data.matches").value(1))
                .andExpect(jsonPath("$.data.battingFirstWins").value(1))
                .andExpect(jsonPath("$.data.averageFirstInningsScore").value(30.00))
                .andExpect(jsonPath("$.data.highestScore").value(30))
                .andExpect(jsonPath("$.data.wicketsPerInnings").value(2.50));
    }

    @Test
    void seasonSummaryAndPointsTable() throws Exception {
        call("/api/v1/seasons/2019/summary")
                .andExpect(jsonPath("$.data.matches").value(1))
                .andExpect(jsonPath("$.data.totalRuns").value(50))
                .andExpect(jsonPath("$.data.wickets").value(5))
                .andExpect(jsonPath("$.data.highestTeamScore.runs").value(30))
                .andExpect(jsonPath("$.data.topRunScorer.player.id").value(rohit))
                .andExpect(jsonPath("$.data.topRunScorer.value").value(13))
                .andExpect(jsonPath("$.data.topWicketTaker.value").value(1));
        // MI: 30 off 14 balls, conceded 20 off 8 balls (CSK not all out): 12.857 - 15.000 = -2.143
        call("/api/v1/seasons/2019/points-table")
                .andExpect(jsonPath("$.data.rows[*].team.code", contains("MI", "CSK")))
                .andExpect(jsonPath("$.data.rows[0].points").value(2))
                .andExpect(jsonPath("$.data.rows[0].netRunRate").value(-2.143))
                .andExpect(jsonPath("$.data.rows[0].oversFaced").value("2.2"))
                .andExpect(jsonPath("$.data.rows[1].netRunRate").value(2.143))
                .andExpect(jsonPath("$.data.rows[1].losses").value(1));
        mvc.perform(get("/api/v1/seasons/2011/summary")).andExpect(status().isNotFound());
    }

    @Test
    void headToHead() throws Exception {
        call("/api/v1/head-to-head?team1=" + mi + "&team2=" + csk)
                .andExpect(jsonPath("$.data.matches").value(1))
                .andExpect(jsonPath("$.data.team1.team.code").value("MI"))
                .andExpect(jsonPath("$.data.team1.wins").value(1))
                .andExpect(jsonPath("$.data.team1.winsBattingFirst").value(1))
                .andExpect(jsonPath("$.data.team1.averageScore").value(30.00))
                .andExpect(jsonPath("$.data.team2.wins").value(0))
                .andExpect(jsonPath("$.data.team2.averageScore").value(20.00))
                .andExpect(jsonPath("$.data.bySeason[0].season").value(2019))
                .andExpect(jsonPath("$.data.recentMatches[0].id").value(match2019));
        call("/api/v1/head-to-head?team1=" + mi + "&team2=" + csk + "&seasonTo=2018")
                .andExpect(jsonPath("$.data.matches").value(0))
                .andExpect(jsonPath("$.data.recentMatches", empty()));
        mvc.perform(get("/api/v1/head-to-head?team1=" + mi + "&team2=999")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/head-to-head?team1=" + mi + "&team2=" + mi)).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void leaderboards() throws Exception {
        call("/api/v1/leaderboards/batting?season=2019&limit=2")
                .andExpect(jsonPath("$.data[0].player.id").value(rohit))
                .andExpect(jsonPath("$.data[0].value").value(13))
                .andExpect(jsonPath("$.data[*].rank", contains(1, 2)));
        call("/api/v1/leaderboards/bowling?metric=economy&season=2019&minimumOvers=1")
                .andExpect(jsonPath("$.data[0].player.name").value("Imran Tahir"))
                .andExpect(jsonPath("$.data[0].value").value(6.00))
                .andExpect(jsonPath("$.meta.minimumOvers").value(1));
        call("/api/v1/leaderboards/batting?metric=strikeRate&season=2019")
                .andExpect(jsonPath("$.data", empty())); // nobody faced the default 100 balls
        call("/api/v1/leaderboards/batting?team=" + csk + "&season=2019")
                .andExpect(jsonPath("$.data[*].player.name", everyItem(org.hamcrest.Matchers.oneOf(
                        "F du Plessis", "SR Watson", "MS Dhoni"))));
        mvc.perform(get("/api/v1/leaderboards/batting?metric=catches")).andExpect(status().isBadRequest());
    }

    private ResultActions call(String uri) throws Exception {
        return mvc.perform(get(uri)).andExpect(status().isOk());
    }

    private int id(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private int player(String sourceId) {
        Integer value = jdbc.queryForObject("select id from core.player where cricsheet_id = ?", Integer.class, sourceId);
        return value == null ? 0 : value;
    }
}
