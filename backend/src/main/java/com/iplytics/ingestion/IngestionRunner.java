package com.iplytics.ingestion;

import com.iplytics.ingestion.FileIngestionResult.Outcome;
import com.iplytics.ingestion.schema.Dataset;
import com.iplytics.ingestion.source.DiscoveredFile;
import com.iplytics.ingestion.source.DiscoveryResult;
import com.iplytics.ingestion.source.FileDiscovery;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * CLI entry point, active only with the {@value #PROFILE} profile:
 * {@code java -jar iplytics.jar --spring.profiles.active=ingest --ingest.dir=data/raw}.
 * Exit code 0 when every file was loaded or skipped, 1 otherwise.
 */
@Component
@Profile(IngestionRunner.PROFILE)
@org.springframework.core.annotation.Order(10) // before TransformRunner when both profiles are active
public class IngestionRunner implements ApplicationRunner, ExitCodeGenerator {

    public static final String PROFILE = "ingest";

    private static final Logger log = LoggerFactory.getLogger(IngestionRunner.class);

    private final FileDiscovery fileDiscovery;
    private final IngestionService ingestionService;
    private final IngestProperties properties;
    private int exitCode;

    public IngestionRunner(FileDiscovery fileDiscovery, IngestionService ingestionService,
                           IngestProperties properties) {
        this.fileDiscovery = fileDiscovery;
        this.ingestionService = ingestionService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        Path directory = properties.dir().toAbsolutePath().normalize();
        log.info("Scanning {} for {}", directory, Dataset.supportedFileNames());

        DiscoveryResult discovery;
        try {
            discovery = fileDiscovery.discover(directory);
        } catch (IllegalArgumentException | UncheckedIOException e) {
            log.error(e.getMessage());
            exitCode = 1;
            return;
        }
        discovery.ignored().forEach(file -> log.warn("Ignoring {}: not a supported file name", file.getFileName()));
        if (discovery.supported().isEmpty()) {
            log.error("No supported files found. Place {} in {}", Dataset.supportedFileNames(), directory);
            exitCode = 1;
            return;
        }

        int recovered = ingestionService.recoverStaleRuns();
        if (recovered > 0) {
            log.warn("Marked {} abandoned RUNNING run(s) as FAILED", recovered);
        }

        List<FileIngestionResult> results = new ArrayList<>();
        for (DiscoveredFile file : discovery.supported()) {
            FileIngestionResult result = ingestSafely(file);
            report(result);
            results.add(result);
        }

        long loaded = count(results, Outcome.LOADED);
        long skipped = count(results, Outcome.SKIPPED);
        long failed = count(results, Outcome.FAILED);
        log.info("Ingestion finished: {} loaded, {} skipped, {} failed", loaded, skipped, failed);
        exitCode = failed > 0 ? 1 : 0;
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    private FileIngestionResult ingestSafely(DiscoveredFile file) {
        try {
            return ingestionService.ingest(file);
        } catch (RuntimeException e) {
            log.error("Ingestion of {} failed unexpectedly", file.fileName(), e);
            return FileIngestionResult.failed(file.fileName(), file.dataset(), null, 0, 0, e.toString(), 0);
        }
    }

    private static void report(FileIngestionResult r) {
        switch (r.outcome()) {
            case LOADED -> {
                log.info("{} -> run #{} COMPLETED: read {}, loaded {}, rejected {} ({} ms)", r.fileName(), r.runId(),
                        format(r.rowsRead()), format(r.rowsLoaded()), format(r.rowsRejected()), r.elapsedMillis());
                if (r.rowsRejected() > 0) {
                    log.warn("{} -> inspect rejected rows: select * from staging.rejected_row where ingestion_run_id = {};",
                            r.fileName(), r.runId());
                }
            }
            case SKIPPED -> log.info("{} -> SKIPPED: {}", r.fileName(), r.message());
            case FAILED -> log.error("{} -> run #{} FAILED: {}", r.fileName(), r.runId(), r.message());
        }
    }

    private static String format(int count) {
        return "%,d".formatted(count);
    }

    private static long count(List<FileIngestionResult> results, Outcome outcome) {
        return results.stream().filter(r -> r.outcome() == outcome).count();
    }
}
