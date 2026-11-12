package com.iplytics.ingestion.run;

import com.iplytics.ingestion.schema.Dataset;
import java.time.OffsetDateTime;

/** A row of {@code core.ingestion_run}. */
public record IngestionRun(
        long id,
        Dataset dataset,
        String fileName,
        String fileHash,
        long fileSizeBytes,
        RunStatus status,
        int rowsRead,
        int rowsLoaded,
        int rowsRejected,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        String errorSummary) {
}
