package com.iplytics.ingestion.staging;

import com.iplytics.ingestion.schema.RejectionReason;
import com.iplytics.ingestion.validation.Violation;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A source row that failed validation, as stored in {@code staging.rejected_row}.
 *
 * @param reasonCode  the first violation
 * @param reason      every violation, human readable
 * @param rawDataJson the source row as a JSON object of header to value
 */
public record RejectedRow(int sourceRowNumber, RejectionReason reasonCode, String reason, String rawDataJson) {

    public static RejectedRow of(int sourceRowNumber, List<Violation> violations, String rawDataJson) {
        String reason = violations.stream().map(Violation::message).collect(Collectors.joining("; "));
        return new RejectedRow(sourceRowNumber, violations.get(0).reason(), reason, rawDataJson);
    }
}
