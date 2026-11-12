package com.iplytics.ingestion;

import com.iplytics.ingestion.staging.RejectedRow;
import java.util.List;

/**
 * More rows failed validation than {@code ingest.max-rejected-rows} allows. This usually means the file
 * has an unexpected format, so the run fails, but the rejected rows collected so far are kept for diagnosis.
 */
public class TooManyRejectedRowsException extends IngestionException {

    private final int rowsRead;
    private final transient List<RejectedRow> rejected;

    public TooManyRejectedRowsException(int limit, int rowsRead, List<RejectedRow> rejected) {
        super("More than %d rows rejected (limit ingest.max-rejected-rows) after reading %d rows; first: row %d %s"
                .formatted(limit, rowsRead, rejected.get(0).sourceRowNumber(), rejected.get(0).reason()));
        this.rowsRead = rowsRead;
        this.rejected = List.copyOf(rejected);
    }

    public int rowsRead() {
        return rowsRead;
    }

    public List<RejectedRow> rejected() {
        return rejected;
    }
}
