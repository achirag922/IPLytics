package com.iplytics.ingestion.validation;

import com.iplytics.ingestion.schema.RejectionReason;

/** One problem found in a source row. {@code column} is null for row-level problems. */
public record Violation(RejectionReason reason, String column, String message) {
}
