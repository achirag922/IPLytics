package com.iplytics.ingestion.schema;

/** Why a source row was rejected. Stored as text in {@code staging.rejected_row.reason_code}. */
public enum RejectionReason {
    EMPTY_ROW,
    COLUMN_COUNT_MISMATCH,
    MISSING_VALUE,
    INVALID_INTEGER,
    INVALID_DECIMAL,
    INVALID_DATE,
    INVALID_VALUE
}
