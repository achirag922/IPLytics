package com.iplytics.ingestion.cricsheet;

import static org.assertj.core.api.Assertions.assertThat;

import com.iplytics.ingestion.FileIngestionResult;
import com.iplytics.ingestion.FileIngestionResult.Outcome;
import com.iplytics.ingestion.IngestionService;
import com.iplytics.ingestion.source.DiscoveredFile;
import com.iplytics.ingestion.source.FileDiscovery;
import com.iplytics.support.AbstractIntegrationTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Cricsheet JSON -> converter -> CSV -> existing staging ingestion, against a real PostgreSQL. */
class CricsheetIngestionIT extends AbstractIntegrationTest {

    @Autowired
    private CricsheetConverter converter;

    @Autowired
    private IngestionService ingestionService;

    @Autowired
    private JdbcTemplate jdbc;

    @TempDir
    Path dir;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("truncate core.ingestion_run restart identity cascade");
    }

    @Test
    void convertedFilesLoadIntoStagingWithoutRejectionsAndRerunsAreSkipped() throws IOException {
        Path source = Files.createDirectories(dir.resolve("cricsheet"));
        for (Map.Entry<String, byte[]> entry : CricsheetConverterTest.fixtureEntries().entrySet()) {
            Files.write(source.resolve(entry.getKey()), entry.getValue());
        }
        Path raw = dir.resolve("raw");
        ConversionReport report = converter.convert(source, raw);

        List<FileIngestionResult> results = ingestAll(raw);

        assertThat(results).extracting(FileIngestionResult::fileName, FileIngestionResult::outcome,
                FileIngestionResult::rowsLoaded, FileIngestionResult::rowsRejected).containsExactly(
                org.assertj.core.groups.Tuple.tuple("matches.csv", Outcome.LOADED, report.matchesGenerated(), 0),
                org.assertj.core.groups.Tuple.tuple("deliveries.csv", Outcome.LOADED,
                        (int) report.deliveriesGenerated(), 0),
                org.assertj.core.groups.Tuple.tuple("match_players.csv", Outcome.LOADED,
                        (int) report.matchPlayersGenerated(), 0));

        assertThat(jdbc.queryForMap("select season, season_year, end_date from staging.matches where id = '1000004'"))
                .containsEntry("season", "2023").containsEntry("season_year", "2023")
                .containsEntry("end_date", "2023-05-29");
        assertThat(jdbc.queryForMap("""
                select actual_delivery, wides, noballs, byes, extras_type, batter_id
                  from staging.deliveries where match_id = '335982' and inning = '1' and ball = '4'"""))
                .containsEntry("actual_delivery", "0.3")
                .containsEntry("noballs", "1").containsEntry("byes", "2").containsEntry("wides", "0")
                .containsEntry("extras_type", "noballs|byes").containsEntry("batter_id", "id-mccullum");
        assertThat(jdbc.queryForObject(
                "select count(*) from staging.match_players where player = 'Z Khan' and match_id = '335982'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from staging.deliveries where source_unmapped is not null", Integer.class))
                .isEqualTo(2); // the review and the impact-player replacement

        // Converting the same source again yields identical files, so ingestion skips them.
        converter.convert(source, raw);
        assertThat(ingestAll(raw)).extracting(FileIngestionResult::outcome).containsOnly(Outcome.SKIPPED);
    }

    private List<FileIngestionResult> ingestAll(Path raw) {
        return new FileDiscovery().discover(raw).supported().stream()
                .map((DiscoveredFile file) -> ingestionService.ingest(file))
                .toList();
    }
}
