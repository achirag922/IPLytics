package com.iplytics.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.iplytics.ingestion.FileIngestionResult.Outcome;
import com.iplytics.ingestion.run.IngestionRun;
import com.iplytics.ingestion.run.IngestionRunRepository;
import com.iplytics.ingestion.run.RunStatus;
import com.iplytics.ingestion.schema.Dataset;
import com.iplytics.ingestion.source.DiscoveredFile;
import com.iplytics.ingestion.source.FileHasher;
import com.iplytics.support.AbstractIntegrationTest;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Ingestion into staging against a real PostgreSQL (Testcontainers). */
class StagingIngestionIT extends AbstractIntegrationTest {

    private static final String MATCHES_HEADER = "id,season,city,date,match_type,player_of_match,venue,team1,team2,"
            + "toss_winner,toss_decision,winner,result,result_margin,target_runs,target_overs,super_over,method,"
            + "umpire1,umpire2";

    @Autowired
    private IngestionService ingestionService;

    @Autowired
    private IngestionRunRepository runs;

    @Autowired
    private FileHasher fileHasher;

    @Autowired
    private JdbcTemplate jdbc;

    @TempDir
    Path tempDir;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("truncate core.ingestion_run restart identity cascade"); // cascades to staging tables
    }

    @Test
    void loadsTheSampleFilesAndRecordsEachRun() throws Exception {
        Path matchesFile = sample("matches.csv");

        FileIngestionResult matches = ingest(matchesFile);
        FileIngestionResult deliveries = ingest(sample("deliveries.csv"));

        assertThat(matches.outcome()).isEqualTo(Outcome.LOADED);
        assertThat(matches).extracting(FileIngestionResult::rowsRead, FileIngestionResult::rowsLoaded,
                FileIngestionResult::rowsRejected).containsExactly(3, 3, 0);
        assertThat(deliveries).extracting(FileIngestionResult::outcome, FileIngestionResult::rowsLoaded)
                .containsExactly(Outcome.LOADED, 8);
        assertThat(countRows("staging.matches", matches.runId())).isEqualTo(3);
        assertThat(countRows("staging.deliveries", deliveries.runId())).isEqualTo(8);

        IngestionRun run = runs.findById(matches.runId()).orElseThrow();
        assertThat(run.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(run.dataset()).isEqualTo(Dataset.MATCHES);
        assertThat(run.fileName()).isEqualTo("matches.csv");
        assertThat(run.fileHash()).isEqualTo(fileHasher.sha256(matchesFile));
        assertThat(run.fileSizeBytes()).isEqualTo(Files.size(matchesFile));
        assertThat(run).extracting(IngestionRun::rowsRead, IngestionRun::rowsLoaded, IngestionRun::rowsRejected)
                .containsExactly(3, 3, 0);
        assertThat(run.completedAt()).isNotNull().isAfterOrEqualTo(run.startedAt());
        assertThat(run.errorSummary()).isNull();
    }

    @Test
    void preservesSourceValuesVerbatimAndStoresBlanksAsNull() throws Exception {
        long runId = ingest(sample("matches.csv")).runId();

        Map<String, Object> row = jdbc.queryForMap("""
                select id, season, "date", venue, method, result_margin
                  from staging.matches where ingestion_run_id = ? and source_row_number = 2""", runId);
        assertThat(row).containsEntry("id", "335983")
                .containsEntry("season", "2007/08")
                .containsEntry("date", "2008-04-19")
                .containsEntry("venue", "Punjab Cricket Association Stadium, Mohali")
                .containsEntry("method", "NA")
                .containsEntry("result_margin", "33.0");

        Path file = write("blank", "matches.csv", MATCHES_HEADER, match("7", "", "2008-05-01"));
        long blankRunId = ingest(file).runId();
        assertThat(jdbc.queryForObject("select city from staging.matches where ingestion_run_id = ?",
                String.class, blankRunId)).isNull();
    }

    @Test
    void reingestingTheSameFileIsSkippedAndDuplicatesNothing() throws Exception {
        FileIngestionResult first = ingest(sample("matches.csv"));

        FileIngestionResult again = ingest(sample("matches.csv"));
        Path sameContentElsewhere = Files.copy(sample("matches.csv"),
                Files.createDirectories(tempDir.resolve("copy")).resolve("matches.csv"));
        FileIngestionResult copy = ingest(sameContentElsewhere);

        assertThat(List.of(again, copy)).allSatisfy(result -> {
            assertThat(result.outcome()).isEqualTo(Outcome.SKIPPED);
            assertThat(result.runId()).isEqualTo(first.runId());
            assertThat(result.message()).contains("already loaded by run #" + first.runId());
        });
        assertThat(jdbc.queryForObject("select count(*) from core.ingestion_run", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from staging.matches", Integer.class)).isEqualTo(3);
    }

    @Test
    void invalidRowsAreCapturedInRejectedRowAndValidRowsStillLoad() throws Exception {
        Path file = write("errors", "matches.csv",
                MATCHES_HEADER,
                match("1", "Mumbai", "2008-04-20"),   // row 1: valid
                match("2", "Mumbai", "18/04/2008"),   // row 2: invalid date
                match("", "Mumbai", "2008-04-22"),    // row 3: missing id
                "",                                   // row 4: empty row
                "4,2008,Mumbai",                      // row 5: too few values
                match("5", "Mumbai", "2008-04-24"));  // row 6: valid

        FileIngestionResult result = ingest(file);

        assertThat(result).extracting(FileIngestionResult::outcome, FileIngestionResult::rowsRead,
                FileIngestionResult::rowsLoaded, FileIngestionResult::rowsRejected)
                .containsExactly(Outcome.LOADED, 6, 2, 4);
        assertThat(jdbc.queryForList("select source_row_number from staging.matches order by 1", Integer.class))
                .containsExactly(1, 6);

        List<Map<String, Object>> rejected = jdbc.queryForList("""
                select source_file, source_row_number, reason_code, reason, raw_data ->> 'date' as raw_date
                  from staging.rejected_row where ingestion_run_id = ? order by source_row_number""", result.runId());
        assertThat(rejected).extracting(r -> r.get("source_row_number"), r -> r.get("reason_code")).containsExactly(
                org.assertj.core.groups.Tuple.tuple(2, "INVALID_DATE"),
                org.assertj.core.groups.Tuple.tuple(3, "MISSING_VALUE"),
                org.assertj.core.groups.Tuple.tuple(4, "EMPTY_ROW"),
                org.assertj.core.groups.Tuple.tuple(5, "COLUMN_COUNT_MISMATCH"));
        assertThat(rejected.get(0)).containsEntry("source_file", "matches.csv").containsEntry("raw_date", "18/04/2008");
        assertThat((String) rejected.get(0).get("reason")).contains("'date' must be a date in yyyy-MM-dd format");

        IngestionRun run = runs.findById(result.runId()).orElseThrow();
        assertThat(run.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(run.rowsLoaded() + run.rowsRejected()).isEqualTo(run.rowsRead());
    }

    @Test
    void missingRequiredColumnFailsTheRunAndLoadsNothing() throws Exception {
        Path file = write("old-format", "deliveries.csv",
                "match_id,inning,batting_team,bowling_team,over,ball,batsman,bowler,non_striker,batsman_runs,"
                        + "extra_runs,total_runs,extras_type,is_wicket,player_dismissed,dismissal_kind,fielder",
                "335982,1,KKR,RCB,0,1,SC Ganguly,P Kumar,BB McCullum,0,1,1,legbyes,0,NA,NA,NA");

        FileIngestionResult result = ingest(file);

        assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
        assertThat(result.message()).contains("Missing required columns [batter]");
        IngestionRun run = runs.findById(result.runId()).orElseThrow();
        assertThat(run.status()).isEqualTo(RunStatus.FAILED);
        assertThat(run.errorSummary()).contains("batter");
        assertThat(run.completedAt()).isNotNull();
        assertThat(run.rowsLoaded()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from staging.deliveries", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from staging.rejected_row", Integer.class)).isZero();
    }

    @Test
    void malformedCsvFailsTheRunWithoutPartialData() throws Exception {
        Path file = write("malformed", "matches.csv",
                MATCHES_HEADER, match("1", "Mumbai", "2008-04-20"), "\"2,2008,unterminated");

        FileIngestionResult result = ingest(file);

        assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
        assertThat(result.message()).contains("Malformed CSV");
        assertThat(jdbc.queryForObject("select count(*) from staging.matches", Integer.class)).isZero();
    }

    @Test
    void tooManyRejectedRowsFailsTheRunButKeepsRejectsAndAllowsARetry() throws Exception {
        Path file = write("bad-format", "matches.csv",
                MATCHES_HEADER,
                match("1", "Mumbai", "20/04/2008"),
                match("2", "Mumbai", "21/04/2008"),
                match("3", "Mumbai", "22/04/2008"),
                match("4", "Mumbai", "2008-04-23"));

        FileIngestionResult failed = ingestionService.ingest(discovered(file), 1);

        assertThat(failed.outcome()).isEqualTo(Outcome.FAILED);
        assertThat(failed.message()).contains("More than 1 rows rejected");
        IngestionRun failedRun = runs.findById(failed.runId()).orElseThrow();
        assertThat(failedRun).extracting(IngestionRun::status, IngestionRun::rowsRead, IngestionRun::rowsLoaded,
                IngestionRun::rowsRejected).containsExactly(RunStatus.FAILED, 2, 0, 2);
        assertThat(countRows("staging.rejected_row", failed.runId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from staging.matches", Integer.class)).isZero();

        // A FAILED run does not block the same file from being ingested again.
        FileIngestionResult retry = ingest(file);
        assertThat(retry).extracting(FileIngestionResult::outcome, FileIngestionResult::rowsLoaded,
                FileIngestionResult::rowsRejected).containsExactly(Outcome.LOADED, 1, 3);
    }

    @Test
    void theDatabaseAllowsOnlyOneActiveRunPerFileHash() {
        String hash = "a".repeat(64);

        assertThat(runs.start(Dataset.MATCHES, "matches.csv", hash, 10)).isPresent();
        assertThat(runs.start(Dataset.MATCHES, "matches.csv", hash, 10)).isEmpty();
    }

    @Test
    void staleRunningRunsAreMarkedFailed() {
        long runId = runs.start(Dataset.MATCHES, "matches.csv", "b".repeat(64), 10).orElseThrow();
        jdbc.update("update core.ingestion_run set started_at = now() - interval '2 hours' where id = ?", runId);
        long freshRunId = runs.start(Dataset.DELIVERIES, "deliveries.csv", "c".repeat(64), 10).orElseThrow();

        assertThat(ingestionService.recoverStaleRuns()).isEqualTo(1);

        IngestionRun stale = runs.findById(runId).orElseThrow();
        assertThat(stale.status()).isEqualTo(RunStatus.FAILED);
        assertThat(stale.errorSummary()).startsWith("Abandoned");
        assertThat(runs.findById(freshRunId).orElseThrow().status()).isEqualTo(RunStatus.RUNNING);
    }

    @Test
    void unknownSourceColumnsArePreservedInExtraData() throws Exception {
        Path file = write("extra", "deliveries.csv",
                "match_id,inning,batting_team,bowling_team,over,ball,batter,bowler,non_striker,batsman_runs,"
                        + "extra_runs,total_runs,extras_type,is_wicket,player_dismissed,dismissal_kind,scorer_note",
                "335982,1,KKR,RCB,0,5,BB McCullum,P Kumar,SC Ganguly,4,0,4,NA,0,NA,NA,edged");

        FileIngestionResult result = ingest(file);

        assertThat(result.outcome()).isEqualTo(Outcome.LOADED);
        Map<String, Object> row = jdbc.queryForMap("""
                select fielder, extra_data ->> 'scorer_note' as scorer_note
                  from staging.deliveries where ingestion_run_id = ?""", result.runId());
        assertThat(row.get("fielder")).isNull();
        assertThat(row).containsEntry("scorer_note", "edged");
    }

    // --- helpers ------------------------------------------------------------------------------------------

    private FileIngestionResult ingest(Path file) {
        return ingestionService.ingest(discovered(file));
    }

    private static DiscoveredFile discovered(Path file) {
        return new DiscoveredFile(file, Dataset.forFileName(file.getFileName().toString()).orElseThrow());
    }

    private static Path sample(String name) throws URISyntaxException {
        return Path.of(StagingIngestionIT.class.getResource("/ingestion/sample/" + name).toURI());
    }

    private Path write(String folder, String fileName, String... lines) throws IOException {
        Path dir = Files.createDirectories(tempDir.resolve(folder));
        return Files.writeString(dir.resolve(fileName), String.join("\n", lines) + "\n");
    }

    private static String match(String id, String city, String date) {
        return String.join(",", id, "2008", city, date, "League", "SR Tendulkar", "\"Wankhede Stadium, Mumbai\"",
                "Mumbai Indians", "Deccan Chargers", "Mumbai Indians", "bat", "Deccan Chargers", "wickets", "5.0",
                "166.0", "20.0", "N", "NA", "Umpire One", "Umpire Two");
    }

    private int countRows(String table, long runId) {
        return jdbc.queryForObject("select count(*) from " + table + " where ingestion_run_id = ?", Integer.class,
                runId);
    }
}
