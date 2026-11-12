package com.iplytics.ingestion;

import com.iplytics.ingestion.schema.Dataset;

/**
 * What happened to one source file.
 *
 * @param runId the ingestion run that loaded (or failed to load) the file; for SKIPPED, the earlier run
 *              that already loaded it, if known
 */
public record FileIngestionResult(
        String fileName,
        Dataset dataset,
        Outcome outcome,
        Long runId,
        int rowsRead,
        int rowsLoaded,
        int rowsRejected,
        String message,
        long elapsedMillis) {

    public enum Outcome {
        LOADED,
        SKIPPED,
        FAILED
    }

    public static FileIngestionResult loaded(String fileName, Dataset dataset, long runId, int rowsRead,
                                             int rowsLoaded, int rowsRejected, long elapsedMillis) {
        return new FileIngestionResult(fileName, dataset, Outcome.LOADED, runId, rowsRead, rowsLoaded, rowsRejected,
                null, elapsedMillis);
    }

    public static FileIngestionResult skipped(String fileName, Dataset dataset, Long previousRunId, String message,
                                              long elapsedMillis) {
        return new FileIngestionResult(fileName, dataset, Outcome.SKIPPED, previousRunId, 0, 0, 0, message,
                elapsedMillis);
    }

    public static FileIngestionResult failed(String fileName, Dataset dataset, Long runId, int rowsRead,
                                             int rowsRejected, String message, long elapsedMillis) {
        return new FileIngestionResult(fileName, dataset, Outcome.FAILED, runId, rowsRead, 0, rowsRejected, message,
                elapsedMillis);
    }
}
