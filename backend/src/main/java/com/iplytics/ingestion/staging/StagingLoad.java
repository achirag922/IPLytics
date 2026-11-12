package com.iplytics.ingestion.staging;

import java.util.List;

/**
 * Outcome of loading one file into staging.
 *
 * @param rowsRead   data rows read (header excluded)
 * @param rowsLoaded rows written to the staging table
 * @param rejected   rows that failed validation (not yet persisted)
 */
public record StagingLoad(int rowsRead, int rowsLoaded, List<RejectedRow> rejected) {
}
