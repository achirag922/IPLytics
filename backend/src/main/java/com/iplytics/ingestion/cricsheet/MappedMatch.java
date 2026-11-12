package com.iplytics.ingestion.cricsheet;

import java.util.List;
import java.util.Map;

/**
 * One Cricsheet match converted to CSV rows, keyed by column name (see {@code Dataset}).
 *
 * @param missingFields JSON paths of required delivery-level fields that were absent; the rows are still
 *                      emitted (with blanks) so staging validation records them in {@code rejected_row}
 */
public record MappedMatch(
        String matchId,
        String sourceSeason,
        int seasonYear,
        Map<String, String> match,
        List<Map<String, String>> deliveries,
        List<Map<String, String>> players,
        List<String> missingFields) {
}
