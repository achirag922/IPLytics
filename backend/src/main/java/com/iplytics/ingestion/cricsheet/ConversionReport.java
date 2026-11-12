package com.iplytics.ingestion.cricsheet;

import java.util.List;

/**
 * Validation report of one conversion, also written as {@code cricsheet-conversion-report.json}.
 * Counts are always what was actually generated; nothing is compared with expected totals.
 */
public record ConversionReport(
        String source,
        String sourceSha256,
        String generatedAt,
        int jsonFilesProcessed,
        int matchesGenerated,
        long deliveriesGenerated,
        long matchPlayersGenerated,
        List<SeasonSummary> matchesBySeason,
        List<String> skippedEntries,
        List<RejectedFile> rejectedFiles,
        List<DuplicateMatchId> duplicateMatchIds,
        List<MissingField> missingRequiredFields,
        List<String> warnings) {

    /** True when anything needs a human look before the output is ingested. */
    public boolean hasProblems() {
        return matchesGenerated == 0 || !rejectedFiles.isEmpty() || !duplicateMatchIds.isEmpty()
                || !missingRequiredFields.isEmpty();
    }

    /** @param seasonYear the IPL year (from the match date); {@code sourceSeasons} are Cricsheet's labels */
    public record SeasonSummary(int seasonYear, List<String> sourceSeasons, int matches, long deliveries,
                                long matchPlayers) {
    }

    public record RejectedFile(String file, String reason) {
    }

    public record DuplicateMatchId(String matchId, String keptFile, String duplicateFile) {
    }

    public record MissingField(String file, String matchId, String field) {
    }
}
