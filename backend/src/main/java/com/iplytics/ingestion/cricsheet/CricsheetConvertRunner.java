package com.iplytics.ingestion.cricsheet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * CLI entry point, active only with the {@value #PROFILE} profile (no database needed):
 * {@code java -jar iplytics.jar --spring.profiles.active=convert --convert.source=data/cricsheet/ipl_json.zip}.
 * Exit code 0 when the report is clean, 1 when it lists problems or the conversion failed.
 */
@Component
@Profile(CricsheetConvertRunner.PROFILE)
public class CricsheetConvertRunner implements ApplicationRunner, ExitCodeGenerator {

    public static final String PROFILE = "convert";

    private static final Logger log = LoggerFactory.getLogger(CricsheetConvertRunner.class);

    private final CricsheetConverter converter;
    private final ConvertProperties properties;
    private int exitCode;

    public CricsheetConvertRunner(CricsheetConverter converter, ConvertProperties properties) {
        this.converter = converter;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        Path source = properties.source().toAbsolutePath().normalize();
        Path outputDir = properties.outputDir().toAbsolutePath().normalize();
        if (!Files.exists(source)) {
            log.error("Cricsheet source not found: {}. Download the IPL JSON zip from "
                    + "https://cricsheet.org/downloads/ (see docs/data-source.md).", source);
            exitCode = 1;
            return;
        }
        log.info("Converting {} -> {}", source, outputDir);
        ConversionReport report;
        try {
            report = converter.convert(source, outputDir);
        } catch (IOException | RuntimeException e) {
            log.error("Conversion failed: {}", e.toString());
            exitCode = 1;
            return;
        }
        print(report, outputDir);
        exitCode = report.hasProblems() ? 1 : 0;
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    private static void print(ConversionReport r, Path outputDir) {
        log.info("JSON files processed: {}", r.jsonFilesProcessed());
        log.info("Generated: matches.csv {} rows, deliveries.csv {} rows, match_players.csv {} rows",
                "%,d".formatted(r.matchesGenerated()), "%,d".formatted(r.deliveriesGenerated()),
                "%,d".formatted(r.matchPlayersGenerated()));
        log.info("Matches by season:");
        log.info("  Season  Source label(s)      Matches  Deliveries  Players");
        r.matchesBySeason().forEach(s -> log.info("  {}    {} {} {} {}", s.seasonYear(),
                "%-20s".formatted(String.join(", ", s.sourceSeasons())), "%7d".formatted(s.matches()),
                "%,11d".formatted(s.deliveries()), "%,8d".formatted(s.matchPlayers())));
        if (!r.skippedEntries().isEmpty()) {
            log.info("Skipped non-JSON entries: {}", r.skippedEntries());
        }
        log.info("Rejected/unsupported files: {}", r.rejectedFiles().size());
        r.rejectedFiles().forEach(f -> log.warn("  {}: {}", f.file(), f.reason()));
        log.info("Duplicate match ids: {}", r.duplicateMatchIds().size());
        r.duplicateMatchIds().forEach(d -> log.warn("  {}: kept {}, ignored {}", d.matchId(), d.keptFile(),
                d.duplicateFile()));
        log.info("Missing required fields: {}", r.missingRequiredFields().size());
        r.missingRequiredFields().stream().limit(20).forEach(m -> log.warn("  {} (match {}): {}", m.file(),
                m.matchId(), m.field()));
        r.warnings().forEach(w -> log.warn("Warning: {}", w));
        log.info("Report: {}", outputDir.resolve(CricsheetConverter.REPORT_FILE));
        if (r.hasProblems()) {
            log.warn("Conversion finished WITH PROBLEMS: review the report before ingesting.");
        } else {
            log.info("Conversion finished cleanly. Next: run ingestion (--spring.profiles.active=ingest).");
        }
    }
}
