package com.iplytics.ingestion;

/** An ingestion step failed; the message is written to {@code ingestion_run.error_summary}. */
public class IngestionException extends RuntimeException {

    public IngestionException(String message) {
        super(message);
    }

    public IngestionException(String message, Throwable cause) {
        super(message, cause);
    }
}
