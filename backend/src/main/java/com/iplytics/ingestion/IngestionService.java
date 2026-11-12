package com.iplytics.ingestion;

import com.iplytics.ingestion.run.IngestionRunRepository;
import com.iplytics.ingestion.schema.Dataset;
import com.iplytics.ingestion.source.DiscoveredFile;
import com.iplytics.ingestion.source.FileHasher;
import com.iplytics.ingestion.staging.RejectedRowRepository;
import com.iplytics.ingestion.staging.StagingLoad;
import com.iplytics.ingestion.staging.StagingLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ingests one source file into staging:
 * hash → skip if already loaded → record RUNNING run → (COPY rows + store rejects + mark COMPLETED) in one
 * transaction. Any failure rolls the load back and marks the run FAILED, so staging never holds a partial file.
 */
@Service
public class IngestionService {

    static final int MAX_ERROR_SUMMARY_LENGTH = 2000;

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final FileHasher fileHasher;
    private final StagingLoader stagingLoader;
    private final IngestionRunRepository runs;
    private final RejectedRowRepository rejectedRows;
    private final DataSource dataSource;
    private final IngestProperties properties;
    private final TransactionTemplate transaction;

    public IngestionService(FileHasher fileHasher, StagingLoader stagingLoader, IngestionRunRepository runs,
                            RejectedRowRepository rejectedRows, DataSource dataSource, IngestProperties properties) {
        this.fileHasher = fileHasher;
        this.stagingLoader = stagingLoader;
        this.runs = runs;
        this.rejectedRows = rejectedRows;
        this.dataSource = dataSource;
        this.properties = properties;
        // Plain JDBC transactions: COPY needs the raw connection, and no JPA entities are involved.
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    /** Fails runs left RUNNING by a killed process so their files can be ingested again. */
    public int recoverStaleRuns() {
        return runs.failStaleRuns(properties.staleRunTimeout());
    }

    public FileIngestionResult ingest(DiscoveredFile file) {
        return ingest(file, properties.maxRejectedRows());
    }

    public FileIngestionResult ingest(DiscoveredFile file, int maxRejectedRows) {
        long started = System.nanoTime();
        String fileName = file.fileName();
        Dataset dataset = file.dataset();

        String hash;
        long size;
        try {
            hash = fileHasher.sha256(file.path());
            size = Files.size(file.path());
        } catch (IOException e) {
            return FileIngestionResult.failed(fileName, dataset, null, 0, 0,
                    "Cannot read file: " + e.getMessage(), elapsedMillis(started));
        }

        Optional<Long> previousRun = runs.findCompletedRunId(hash);
        if (previousRun.isPresent()) {
            return FileIngestionResult.skipped(fileName, dataset, previousRun.get(),
                    "identical file already loaded by run #" + previousRun.get(), elapsedMillis(started));
        }
        Optional<Long> startedRun = runs.start(dataset, fileName, hash, size);
        if (startedRun.isEmpty()) {
            return FileIngestionResult.skipped(fileName, dataset, null,
                    "identical file is being loaded by another run", elapsedMillis(started));
        }
        long runId = startedRun.get();

        try {
            StagingLoad load = Objects.requireNonNull(transaction.execute(status -> {
                StagingLoad result = stagingLoader.load(
                        DataSourceUtils.getConnection(dataSource), dataset, runId, file.path(), maxRejectedRows);
                rejectedRows.insertAll(runId, fileName, result.rejected());
                runs.complete(runId, result.rowsRead(), result.rowsLoaded(), result.rejected().size());
                return result;
            }));
            return FileIngestionResult.loaded(fileName, dataset, runId, load.rowsRead(), load.rowsLoaded(),
                    load.rejected().size(), elapsedMillis(started));
        } catch (TooManyRejectedRowsException e) {
            String summary = truncate(e.getMessage());
            transaction.executeWithoutResult(status -> {
                rejectedRows.insertAll(runId, fileName, e.rejected());
                runs.fail(runId, summary, e.rowsRead(), e.rejected().size());
            });
            return FileIngestionResult.failed(fileName, dataset, runId, e.rowsRead(), e.rejected().size(), summary,
                    elapsedMillis(started));
        } catch (RuntimeException e) {
            if (!(e instanceof IngestionException)) {
                log.error("Unexpected error while ingesting {} (run #{})", fileName, runId, e);
            }
            String summary = truncate(e instanceof IngestionException ? e.getMessage() : "Unexpected error: " + e);
            runs.fail(runId, summary, 0, 0);
            return FileIngestionResult.failed(fileName, dataset, runId, 0, 0, summary, elapsedMillis(started));
        }
    }

    private static String truncate(String message) {
        return message.length() <= MAX_ERROR_SUMMARY_LENGTH
                ? message
                : message.substring(0, MAX_ERROR_SUMMARY_LENGTH - 3) + "...";
    }

    private static long elapsedMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
