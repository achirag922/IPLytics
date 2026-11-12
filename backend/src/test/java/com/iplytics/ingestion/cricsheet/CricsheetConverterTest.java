package com.iplytics.ingestion.cricsheet;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iplytics.ingestion.cricsheet.ConversionReport.DuplicateMatchId;
import com.iplytics.ingestion.cricsheet.ConversionReport.MissingField;
import com.iplytics.ingestion.cricsheet.ConversionReport.RejectedFile;
import com.iplytics.ingestion.cricsheet.ConversionReport.SeasonSummary;
import com.iplytics.ingestion.schema.Dataset;
import com.iplytics.ingestion.source.FileHasher;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CricsheetConverterTest {

    static final List<String> FIXTURES = List.of("335982", "1000001", "1000002", "1000003", "1000004");

    private final ObjectMapper json = new ObjectMapper();
    private final FileHasher hasher = new FileHasher();
    private final CricsheetConverter converter = new CricsheetConverter(json, new CricsheetMatchMapper(json), hasher);

    @TempDir
    Path dir;

    @Test
    void convertsTheZipAndReportsWhatWasActuallyGenerated() throws IOException {
        Map<String, byte[]> entries = fixtureEntries();
        entries.put("README.txt", bytes("Cricsheet readme"));
        entries.put("extra/335982.json", entries.get("335982.json")); // duplicate match id
        entries.put("notes.json", bytes("{}"));                       // not a match id
        entries.put("9000001.json", bytes(fixtureText("335982").replace("Indian Premier League", "Big Bash League")));
        entries.put("9000002.json", bytes("{\"info\": {\"event\": {\"name\": \"Indian Premier League\"}, "
                + "\"dates\": [\"2024-03-22\"], \"teams\": [\"A\", \"B\"]}, \"innings\": []}"));
        entries.put("9000003.json", bytes("{ not json"));
        Path zip = zip(entries);
        Path out = dir.resolve("raw");

        ConversionReport report = converter.convert(zip, out);

        assertThat(report.sourceSha256()).isEqualTo(hasher.sha256(zip));
        assertThat(report.jsonFilesProcessed()).isEqualTo(10);
        assertThat(report.matchesGenerated()).isEqualTo(5);
        assertThat(report.deliveriesGenerated()).isEqualTo(14);
        assertThat(report.matchPlayersGenerated()).isEqualTo(28);
        assertThat(report.matchesBySeason())
                .extracting(SeasonSummary::seasonYear, s -> s.sourceSeasons().get(0), SeasonSummary::matches,
                        SeasonSummary::deliveries)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(2008, "2007/08", 1, 5L),
                        org.assertj.core.groups.Tuple.tuple(2010, "2009/10", 1, 2L),
                        org.assertj.core.groups.Tuple.tuple(2017, "2017", 1, 1L),
                        org.assertj.core.groups.Tuple.tuple(2020, "2020/21", 1, 4L),
                        org.assertj.core.groups.Tuple.tuple(2023, "2023", 1, 2L));
        assertThat(report.skippedEntries()).containsExactly("README.txt");
        assertThat(report.rejectedFiles()).extracting(RejectedFile::file)
                .containsExactly("9000001.json", "9000002.json", "9000003.json", "notes.json");
        assertThat(report.rejectedFiles()).extracting(RejectedFile::reason).satisfiesExactly(
                r -> assertThat(r).contains("not an Indian Premier League match"),
                r -> assertThat(r).contains("missing required fields"),
                r -> assertThat(r).contains("invalid JSON"),
                r -> assertThat(r).contains("not a numeric Cricsheet match id"));
        assertThat(report.duplicateMatchIds())
                .containsExactly(new DuplicateMatchId("335982", "335982.json", "extra/335982.json"));
        assertThat(report.missingRequiredFields())
                .containsExactly(new MissingField("9000002.json", "9000002", "info.season"),
                        new MissingField("9000002.json", "9000002", "info.venue"));
        assertThat(report.hasProblems()).isTrue();
        assertThat(out.resolve(CricsheetConverter.REPORT_FILE)).exists();
    }

    @Test
    void cleanSourceProducesACleanReport() throws IOException {
        ConversionReport report = converter.convert(fixtureFolder(), dir.resolve("raw"));

        assertThat(report.hasProblems()).isFalse();
        assertThat(report.warnings()).isEmpty();
        assertThat(report.sourceSha256()).isNull(); // folder source: no single file to hash
    }

    @Test
    void writesTheStagingColumnsInOrderAndLeavesNoTemporaryFiles() throws IOException {
        Path out = dir.resolve("raw");
        ConversionReport report = converter.convert(fixtureFolder(), out);

        Map<Dataset, Long> expectedRows = Map.of(Dataset.MATCHES, (long) report.matchesGenerated(),
                Dataset.DELIVERIES, report.deliveriesGenerated(), Dataset.MATCH_PLAYERS, report.matchPlayersGenerated());
        for (Dataset dataset : Dataset.values()) {
            List<String> lines = Files.readAllLines(out.resolve(dataset.fileName()));
            assertThat(lines.get(0)).isEqualTo(String.join(",", dataset.columnNames()));
            assertThat(lines).hasSize((int) (expectedRows.get(dataset) + 1));
        }
        try (var files = Files.list(out)) {
            assertThat(files.map(p -> p.getFileName().toString())).noneMatch(n -> n.endsWith(".tmp"));
        }
    }

    @Test
    void identicalInputProducesByteIdenticalCsvFiles() throws IOException {
        Path source = fixtureFolder();
        converter.convert(source, dir.resolve("first"));
        converter.convert(source, dir.resolve("second"));

        for (Dataset dataset : Dataset.values()) {
            assertThat(Files.mismatch(dir.resolve("first").resolve(dataset.fileName()),
                    dir.resolve("second").resolve(dataset.fileName()))).isEqualTo(-1L);
        }
    }

    private Path fixtureFolder() throws IOException {
        Path folder = Files.createDirectories(dir.resolve("cricsheet"));
        for (Map.Entry<String, byte[]> entry : fixtureEntries().entrySet()) {
            Files.write(folder.resolve(entry.getKey()), entry.getValue());
        }
        return folder;
    }

    private Path zip(Map<String, byte[]> entries) throws IOException {
        Path zip = dir.resolve("ipl_json.zip");
        try (OutputStream file = Files.newOutputStream(zip); ZipOutputStream out = new ZipOutputStream(file)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
        return zip;
    }

    static Map<String, byte[]> fixtureEntries() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        for (String id : FIXTURES) {
            entries.put(id + ".json", bytes(fixtureText(id)));
        }
        return entries;
    }

    static String fixtureText(String id) throws IOException {
        try (InputStream in = CricsheetConverterTest.class.getResourceAsStream("/cricsheet/" + id + ".json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
