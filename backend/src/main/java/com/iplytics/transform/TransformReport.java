package com.iplytics.transform;

import java.util.List;
import java.util.Map;

/**
 * Outcome of one staging -> core transformation (also stored as {@code core.transform_run.summary}).
 *
 * @param sourceRuns    staging batches used, by dataset (core.ingestion_run ids)
 * @param coreRowCounts rows in each core table after the transformation
 * @param changes       rows inserted / updated / deleted per core table by this run (all zero on a rerun)
 * @param checks        validation and reconciliation results; the run fails if any did not pass
 * @param warnings      observations that need a human look but do not fail the run
 */
public record TransformReport(
        long transformRunId,
        Map<String, Long> sourceRuns,
        Map<String, Long> coreRowCounts,
        Map<String, TableChange> changes,
        List<CheckResult> checks,
        List<String> warnings,
        long elapsedMillis) {

    public boolean passed() {
        return checks.stream().allMatch(CheckResult::passed);
    }

    public long totalChanges() {
        return changes.values().stream().mapToLong(c -> c.inserted() + c.updated() + c.deleted()).sum();
    }

    public record TableChange(long inserted, long updated, long deleted) {

        TableChange plus(TableChange other) {
            return new TableChange(inserted + other.inserted, updated + other.updated, deleted + other.deleted);
        }
    }

    /** @param phase {@code validation} (before loading) or {@code reconciliation} (after loading) */
    public record CheckResult(String phase, String name, boolean passed, String detail) {
    }
}
