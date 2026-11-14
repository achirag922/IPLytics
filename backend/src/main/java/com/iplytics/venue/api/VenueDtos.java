package com.iplytics.venue.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/** Venue resources. Super overs never count; definitions in docs/metrics.md section 6a. */
public final class VenueDtos {

    private VenueDtos() {
    }

    public record VenueDto(@Schema(example = "30") int id, @Schema(example = "Wankhede Stadium") String name,
                           @Schema(example = "Mumbai") String city,
                           @Schema(description = "false: created from a source name missing from the reference data")
                           boolean curated, long matches, Integer firstSeason, Integer lastSeason,
                           @Schema(description = "Every spelling used by the source") List<String> aliases) {
    }

    @Schema(description = """
            Decisive matches exclude ties and no-results. averageFirstInningsScore uses matches with a result and no
            D/L adjustment. lowestScore is the lowest innings that was all out or used its full scheduled overs.""")
    public record VenueStatsDto(long matches, long decisiveMatches, long battingFirstWins, long chasingWins,
                                BigDecimal battingFirstWinPercentage, BigDecimal chasingWinPercentage,
                                BigDecimal averageFirstInningsScore, Integer highestScore, Integer lowestScore,
                                BigDecimal averageRunRate, long innings, BigDecimal wicketsPerInnings,
                                BigDecimal boundariesPerInnings) {
    }

    public record VenueSeasonStatsDto(int season, VenueStatsDto stats) {
    }

    @Schema(description = "Band of first-innings totals: from and to are inclusive; null means unbounded")
    public record ScoreBandDto(Integer from, Integer to, long innings) {
    }
}
