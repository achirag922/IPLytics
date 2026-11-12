package com.iplytics.ingestion.cricsheet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iplytics.ingestion.cricsheet.ConversionReport.DuplicateMatchId;
import com.iplytics.ingestion.cricsheet.ConversionReport.MissingField;
import com.iplytics.ingestion.cricsheet.ConversionReport.RejectedFile;
import com.iplytics.ingestion.cricsheet.ConversionReport.SeasonSummary;
import com.iplytics.ingestion.schema.Dataset;
import com.iplytics.ingestion.source.FileHasher;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/**
 * Converts a Cricsheet IPL JSON download into matches.csv, deliveries.csv and match_players.csv
 * (the staging ingestion input) plus a validation report. Streams one match at a time.
 *
 * <p>Outputs are written to temporary files and moved into place only when the conversion finishes, so an
 * interrupted run never leaves a half-written CSV behind. Identical input gives byte-identical CSVs, which
 * keeps hash-based ingestion idempotent.
 */
@Component
public class CricsheetConverter {

    public static final String REPORT_FILE = "cricsheet-conversion-report.json";

    private final ObjectMapper objectMapper;
    private final CricsheetMatchMapper mapper;
    private final FileHasher fileHasher;

    public CricsheetConverter(ObjectMapper objectMapper, CricsheetMatchMapper mapper, FileHasher fileHasher) {
        this.objectMapper = objectMapper;
        this.mapper = mapper;
        this.fileHasher = fileHasher;
    }

    public ConversionReport convert(Path source, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        ReportBuilder report = new ReportBuilder(source.toString(),
                Files.isRegularFile(source) ? fileHasher.sha256(source) : null);
        Map<Dataset, Path> temporary = new HashMap<>();
        for (Dataset dataset : Dataset.values()) {
            temporary.put(dataset, outputDir.resolve(dataset.fileName() + ".tmp"));
        }

        try (CricsheetSource input = CricsheetSource.open(source);
             CsvRowWriter matches = new CsvRowWriter(Dataset.MATCHES, temporary.get(Dataset.MATCHES));
             CsvRowWriter deliveries = new CsvRowWriter(Dataset.DELIVERIES, temporary.get(Dataset.DELIVERIES));
             CsvRowWriter players = new CsvRowWriter(Dataset.MATCH_PLAYERS, temporary.get(Dataset.MATCH_PLAYERS))) {
            Map<String, String> seen = new HashMap<>();
            for (CricsheetSource.Entry entry : input.entries()) {
                if (!entry.json()) {
                    report.skipped.add(entry.name()); // e.g. README.txt shipped in Cricsheet zips
                    continue;
                }
                report.jsonFiles++;
                if (entry.matchId() == null) {
                    report.rejected.add(new RejectedFile(entry.name(), "file name is not a numeric Cricsheet match id"));
                    continue;
                }
                String kept = seen.putIfAbsent(entry.matchId(), entry.name());
                if (kept != null) {
                    report.duplicates.add(new DuplicateMatchId(entry.matchId(), kept, entry.name()));
                    continue;
                }
                JsonNode document;
                try (InputStream in = input.open(entry)) {
                    document = objectMapper.readTree(in);
                } catch (JsonProcessingException e) {
                    report.rejected.add(new RejectedFile(entry.name(), "invalid JSON: " + e.getOriginalMessage()));
                    continue;
                }
                try {
                    MappedMatch match = mapper.map(entry.matchId(), document);
                    matches.write(match.match());
                    for (Map<String, String> row : match.deliveries()) {
                        deliveries.write(row);
                    }
                    for (Map<String, String> row : match.players()) {
                        players.write(row);
                    }
                    report.add(entry.name(), match);
                } catch (CricsheetFormatException e) {
                    report.rejected.add(new RejectedFile(entry.name(), e.getMessage()));
                    e.missingFields().forEach(f -> report.missing.add(new MissingField(entry.name(), entry.matchId(), f)));
                }
            }
        } catch (IOException | RuntimeException e) {
            for (Path file : temporary.values()) {
                Files.deleteIfExists(file);
            }
            throw e;
        }

        for (Dataset dataset : Dataset.values()) {
            move(temporary.get(dataset), outputDir.resolve(dataset.fileName()));
        }
        ConversionReport result = report.build();
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputDir.resolve(REPORT_FILE).toFile(), result);
        return result;
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static final class ReportBuilder {

        private final String source;
        private final String sourceSha256;
        private int jsonFiles;
        private int matches;
        private long deliveries;
        private long players;
        private final List<String> skipped = new ArrayList<>();
        private final List<RejectedFile> rejected = new ArrayList<>();
        private final List<DuplicateMatchId> duplicates = new ArrayList<>();
        private final List<MissingField> missing = new ArrayList<>();
        private final Map<Integer, SeasonTotals> seasons = new TreeMap<>();
        private final Map<String, Set<Integer>> yearsByLabel = new TreeMap<>();
        private long deliveriesWithoutPlayerIds;

        ReportBuilder(String source, String sourceSha256) {
            this.source = source;
            this.sourceSha256 = sourceSha256;
        }

        void add(String file, MappedMatch match) {
            matches++;
            deliveries += match.deliveries().size();
            players += match.players().size();
            SeasonTotals totals = seasons.computeIfAbsent(match.seasonYear(), y -> new SeasonTotals());
            totals.labels.add(match.sourceSeason());
            totals.matches++;
            totals.deliveries += match.deliveries().size();
            totals.players += match.players().size();
            yearsByLabel.computeIfAbsent(match.sourceSeason(), l -> new TreeSet<>()).add(match.seasonYear());
            match.missingFields().forEach(f -> missing.add(new MissingField(file, match.matchId(), f)));
            deliveriesWithoutPlayerIds += match.deliveries().stream()
                    .filter(d -> d.get("batter_id") == null || d.get("bowler_id") == null).count();
        }

        ConversionReport build() {
            List<SeasonSummary> bySeason = seasons.entrySet().stream()
                    .map(e -> new SeasonSummary(e.getKey(), List.copyOf(e.getValue().labels), e.getValue().matches,
                            e.getValue().deliveries, e.getValue().players))
                    .toList();
            List<String> warnings = new ArrayList<>();
            yearsByLabel.forEach((label, years) -> {
                if (years.size() > 1) {
                    warnings.add("Source season '%s' spans match years %s".formatted(label, years));
                }
            });
            bySeason.stream().filter(s -> s.sourceSeasons().size() > 1).forEach(s -> warnings.add(
                    "Season %d has several source labels %s".formatted(s.seasonYear(), s.sourceSeasons())));
            if (deliveriesWithoutPlayerIds > 0) {
                warnings.add(deliveriesWithoutPlayerIds + " deliveries lack a Cricsheet registry id for batter or bowler");
            }
            return new ConversionReport(source, sourceSha256, Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
                    jsonFiles, matches, deliveries, players, bySeason, List.copyOf(skipped), List.copyOf(rejected),
                    List.copyOf(duplicates), List.copyOf(missing), List.copyOf(warnings));
        }

        private static final class SeasonTotals {
            private final Set<String> labels = new TreeSet<>();
            private int matches;
            private long deliveries;
            private long players;
        }
    }
}
