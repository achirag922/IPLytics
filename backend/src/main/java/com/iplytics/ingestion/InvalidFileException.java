package com.iplytics.ingestion;

/** The file as a whole cannot be ingested (empty, bad header, malformed CSV, wrong encoding). */
public class InvalidFileException extends IngestionException {

    public InvalidFileException(String message) {
        super(message);
    }
}
