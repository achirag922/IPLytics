package com.iplytics.analytics;

import java.util.List;
import java.util.Map;

/**
 * Outcome of one analytics refresh (also stored as {@code analytics.refresh_run.summary}).
 *
 * @param transformRunId latest completed core.transform_run the analytics were built from (null if none)
 * @param rowCounts      rows per analytics object after the refresh
 * @param refreshMillis  time taken to refresh each materialized view
 * @param checks         reconciliation results; the refresh is rolled back if any did not pass
 * @param warnings       data observations that do not fail the refresh
 */
public record AnalyticsRefreshReport(
        long refreshRunId,
        Long transformRunId,
        Map<String, Long> rowCounts,
        Map<String, Long> refreshMillis,
        List<Check> checks,
        List<String> warnings,
        long elapsedMillis) {

    public boolean passed() {
        return checks.stream().allMatch(Check::passed);
    }

    public record Check(String name, boolean passed, String detail) {
    }
}
