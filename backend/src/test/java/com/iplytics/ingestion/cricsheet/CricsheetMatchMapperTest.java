package com.iplytics.ingestion.cricsheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** One test per scenario, against hand-written Cricsheet-format fixtures in src/test/resources/cricsheet. */
class CricsheetMatchMapperTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final CricsheetMatchMapper mapper = new CricsheetMatchMapper(JSON);

    @Test
    void normalMatchMapsMatchLevelFields() throws IOException {
        MappedMatch m = map("335982");

        assertThat(m.match())
                .containsEntry("id", "335982")
                .containsEntry("season", "2007/08")
                .containsEntry("season_year", "2008")
                .containsEntry("date", "2008-04-18")
                .containsEntry("end_date", "2008-04-18")
                .containsEntry("event_name", "Indian Premier League")
                .containsEntry("match_number", "1")
                .containsEntry("match_type", "League")
                .containsEntry("venue", "M Chinnaswamy Stadium")
                .containsEntry("city", "Bangalore")
                .containsEntry("team1", "Royal Challengers Bangalore")
                .containsEntry("team2", "Kolkata Knight Riders")
                .containsEntry("toss_winner", "Royal Challengers Bangalore")
                .containsEntry("toss_decision", "field")
                .containsEntry("winner", "Kolkata Knight Riders")
                .containsEntry("result", "runs")
                .containsEntry("result_margin", "140")
                .containsEntry("target_runs", "223")
                .containsEntry("target_overs", "20")
                .containsEntry("super_over", "N")
                .containsEntry("scheduled_overs", "20")
                .containsEntry("player_of_match", "BB McCullum")
                .containsEntry("player_of_match_ids", "id-mccullum")
                .containsEntry("umpire1", "Asad Rauf")
                .containsEntry("umpire2", "RE Koertzen")
                .containsEntry("tv_umpires", "AM Saheba")
                .containsEntry("match_referees", "J Srinath")
                .containsEntry("source_data_version", "1.1.0")
                .containsEntry("source_created", "2011-05-06")
                .containsEntry("source_revision", "2");
        assertThat(m.match().get("method")).isNull();
        assertThat(m.seasonYear()).isEqualTo(2008);
        assertThat(m.sourceSeason()).isEqualTo("2007/08");
        assertThat(m.missingFields()).isEmpty();
    }

    @Test
    void preservesUnmappedSourceFieldsInsteadOfDroppingThem() throws IOException {
        MappedMatch m = map("335982");

        JsonNode unmapped = JSON.readTree(m.match().get("source_unmapped"));
        assertThat(unmapped.at("/info/balls_per_over").asInt()).isEqualTo(6);
        assertThat(unmapped.at("/info/gender").asText()).isEqualTo("male");
        assertThat(unmapped.at("/info/team_type").asText()).isEqualTo("club");
        assertThat(unmapped.at("/info/match_type").asText()).isEqualTo("T20");
        assertThat(unmapped.at("/info/registry/people/Asad Rauf").asText()).isEqualTo("id-rauf"); // officials' ids
        assertThat(unmapped.at("/info/registry/people").has("SC Ganguly")).isFalse(); // consumed by match_players
        assertThat(unmapped.at("/info").has("outcome")).isFalse();
        assertThat(unmapped.at("/innings/0/inning").asInt()).isEqualTo(1);
        assertThat(unmapped.at("/innings/0/powerplays/0/type").asText()).isEqualTo("mandatory");

        JsonNode deliveryUnmapped = JSON.readTree(m.deliveries().get(4).get("source_unmapped"));
        assertThat(deliveryUnmapped.at("/review/decision").asText()).isEqualTo("upheld");
        assertThat(m.deliveries().get(0).get("source_unmapped")).isNull();
    }

    @Test
    void mapsDeliveriesWithExactExtrasNonBoundaryAndPlayerIds() throws IOException {
        List<Map<String, String>> d = map("335982").deliveries();

        assertThat(d).hasSize(5);
        assertThat(d.get(0))
                .containsEntry("match_id", "335982").containsEntry("inning", "1").containsEntry("is_super_over", "0")
                .containsEntry("over", "0").containsEntry("ball", "1")
                .containsEntry("batting_team", "Kolkata Knight Riders")
                .containsEntry("bowling_team", "Royal Challengers Bangalore")
                .containsEntry("batter", "SC Ganguly").containsEntry("batter_id", "id-ganguly")
                .containsEntry("bowler_id", "id-kumar").containsEntry("non_striker_id", "id-mccullum")
                .containsEntry("legbyes", "1").containsEntry("wides", "0").containsEntry("extras_type", "legbyes")
                .containsEntry("extra_runs", "1").containsEntry("total_runs", "1")
                .containsEntry("non_boundary", "0").containsEntry("is_wicket", "0");
        assertThat(d.get(1)).containsEntry("batsman_runs", "4").containsEntry("non_boundary", "1");
        assertThat(d.get(2)).containsEntry("ball", "3").containsEntry("wides", "1").containsEntry("extras_type", "wides");
    }

    @Test
    void mapsActualDeliveryLabelWhichCountsLegalBallsOnly() throws IOException {
        List<Map<String, String>> d = map("335982").deliveries();

        // The wide (ball 3) does not count, so the next delivery is labelled 0.3 as well.
        assertThat(d.subList(0, 4)).extracting(r -> r.get("ball") + "=" + r.get("actual_delivery"))
                .containsExactly("1=0.1", "2=0.2", "3=0.3", "4=0.3");
        assertThat(d.subList(0, 4)).allSatisfy(r -> assertThat(r.get("source_unmapped")).isNull());
        assertThat(d.get(4).get("actual_delivery")).isNull(); // absent in the source -> empty, not invented
    }

    @Test
    void preservesMultipleExtrasOnOneDelivery() throws IOException {
        Map<String, String> noBallWithByes = map("335982").deliveries().get(3);

        assertThat(noBallWithByes)
                .containsEntry("ball", "4").containsEntry("noballs", "1").containsEntry("byes", "2")
                .containsEntry("wides", "0").containsEntry("legbyes", "0").containsEntry("penalty", "0")
                .containsEntry("extras_type", "noballs|byes").containsEntry("extra_runs", "3");
    }

    @Test
    void mapsADismissalWithItsFielder() throws IOException {
        Map<String, String> wicket = map("335982").deliveries().get(4);

        assertThat(wicket)
                .containsEntry("inning", "2").containsEntry("is_wicket", "1")
                .containsEntry("player_dismissed", "R Dravid").containsEntry("player_dismissed_id", "id-dravid")
                .containsEntry("dismissal_kind", "caught")
                .containsEntry("fielder", "RT Ponting").containsEntry("fielder_ids", "id-ponting");
        assertThat(wicket.get("other_player_dismissed")).isNull();
        assertThat(JSON.readTree(wicket.get("wickets_json")).get(0).get("kind").asText()).isEqualTo("caught");
    }

    @Test
    void listsPlayersInTheXiWhoNeitherBatNorBowl() throws IOException {
        MappedMatch m = map("335982");

        assertThat(m.players()).hasSize(8);
        assertThat(m.players()).filteredOn(p -> "Z Khan".equals(p.get("player"))).singleElement()
                .satisfies(p -> assertThat(p)
                        .containsEntry("match_id", "335982")
                        .containsEntry("team", "Royal Challengers Bangalore")
                        .containsEntry("player_id", "id-zkhan")
                        .containsEntry("list_position", "4"));
        assertThat(m.deliveries()).noneMatch(d -> d.containsValue("Z Khan"));
    }

    @Test
    void dlsMatchKeepsMethodAndRevisedTargetAndUsesDateForSeasonYear() throws IOException {
        MappedMatch m = map("1000001");

        assertThat(m.match())
                .containsEntry("method", "D/L")
                .containsEntry("target_runs", "110").containsEntry("target_overs", "12")
                .containsEntry("result", "runs").containsEntry("result_margin", "7")
                .containsEntry("season", "2009/10").containsEntry("season_year", "2010");
    }

    @Test
    void superOverTieRecordsWinnerAndFlagsSuperOverDeliveries() throws IOException {
        MappedMatch m = map("1000002");

        assertThat(m.match())
                .containsEntry("result", "tie")
                .containsEntry("winner", "Delhi Capitals")
                .containsEntry("super_over_winner", "Delhi Capitals")
                .containsEntry("super_over", "Y")
                .containsEntry("target_runs", "158") // the regular chase, not the super over
                .containsEntry("season", "2020/21").containsEntry("season_year", "2020");
        assertThat(m.match().get("result_margin")).isNull();
        assertThat(m.deliveries()).extracting(d -> d.get("inning") + ":" + d.get("is_super_over"))
                .containsExactly("1:0", "2:0", "3:1", "4:1");

        JsonNode unmapped = JSON.readTree(m.match().get("source_unmapped"));
        assertThat(unmapped.at("/innings/0/inning").asInt()).isEqualTo(4);
        assertThat(unmapped.at("/innings/0/target/runs").asInt()).isEqualTo(3); // super-over target kept
    }

    @Test
    void preservesMultipleDismissalsAndFielders() throws IOException {
        Map<String, String> d = map("1000002").deliveries().get(0);

        assertThat(d)
                .containsEntry("over", "19")
                .containsEntry("player_dismissed", "SS Iyer").containsEntry("dismissal_kind", "run out")
                .containsEntry("fielder", "KL Rahul|Sarfaraz Khan")
                .containsEntry("fielder_ids", "id-rahul|id-sarfaraz")
                .containsEntry("other_player_dismissed", "RR Pant")
                .containsEntry("other_player_dismissed_id", "id-pant")
                .containsEntry("other_dismissal_kind", "retired out");
        assertThat(d.get("other_fielder")).isNull();
        assertThat(JSON.readTree(d.get("wickets_json")).at("/0/fielders/1/substitute").asBoolean()).isTrue();
    }

    @Test
    void noResultMatch() throws IOException {
        MappedMatch m = map("1000003");

        assertThat(m.match()).containsEntry("result", "no result").containsEntry("season", "2017")
                .containsEntry("season_year", "2017");
        assertThat(m.match().get("winner")).isNull();
        assertThat(m.match().get("result_margin")).isNull();
        assertThat(m.match().get("player_of_match")).isNull();
        assertThat(m.match().get("target_runs")).isNull();
        assertThat(m.deliveries()).hasSize(1);
    }

    @Test
    void matchSpanningMultipleDatesKeepsStartAndEndDate() throws IOException {
        MappedMatch m = map("1000004");

        assertThat(m.match())
                .containsEntry("date", "2023-05-28").containsEntry("end_date", "2023-05-29")
                .containsEntry("match_type", "Final")
                .containsEntry("result", "wickets").containsEntry("result_margin", "5")
                .containsEntry("reserve_umpires", "Umpire G");
        assertThat(m.match().get("match_number")).isNull();
        JsonNode replacement = JSON.readTree(m.deliveries().get(0).get("source_unmapped"));
        assertThat(replacement.at("/replacements/match/0/reason").asText()).isEqualTo("impact_player");
    }

    @Test
    void rejectsMatchesFromOtherEvents() throws IOException {
        JsonNode document = fixture("335982");
        ((ObjectNode) document.at("/info/event")).put("name", "Big Bash League");

        assertThatThrownBy(() -> mapper.map("335982", document))
                .isInstanceOf(CricsheetFormatException.class)
                .hasMessageContaining("not an Indian Premier League match");
    }

    @Test
    void rejectsMatchesMissingRequiredFields() throws IOException {
        ObjectNode info = (ObjectNode) fixture("335982").get("info");
        info.remove(List.of("season", "venue"));
        JsonNode document = JSON.createObjectNode().set("info", info);

        assertThatThrownBy(() -> mapper.map("335982", document))
                .isInstanceOfSatisfying(CricsheetFormatException.class, e -> assertThat(e.missingFields())
                        .containsExactly("info.season", "info.venue", "innings"));
    }

    @Test
    void recordsMissingDeliveryFieldsButStillEmitsTheRow() throws IOException {
        JsonNode document = fixture("335982");
        ((ObjectNode) document.at("/innings/0/overs/0/deliveries/0")).remove("batter");

        MappedMatch m = mapper.map("335982", document);

        assertThat(m.missingFields()).containsExactly("innings[0].overs[0].deliveries[0].batter");
        assertThat(m.deliveries()).hasSize(5);
        assertThat(m.deliveries().get(0).get("batter")).isNull();
    }

    private MappedMatch map(String id) throws IOException {
        return mapper.map(id, fixture(id));
    }

    static JsonNode fixture(String id) throws IOException {
        try (InputStream in = CricsheetMatchMapperTest.class.getResourceAsStream("/cricsheet/" + id + ".json")) {
            return JSON.readTree(in);
        }
    }
}
