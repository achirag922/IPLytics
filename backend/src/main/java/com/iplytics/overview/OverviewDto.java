package com.iplytics.overview;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
        Size of the data set: seasons, matches, franchises and venues with at least one match, players with at least
        one appearance in a playing XI, and every delivery (wides, no-balls and super overs included).""")
public record OverviewDto(@Schema(example = "19") long seasons, @Schema(example = "2008") Integer firstSeason,
                          @Schema(example = "2026") Integer lastSeason, @Schema(example = "1243") long matches,
                          @Schema(example = "15") long teams, @Schema(example = "812") long players,
                          @Schema(example = "59") long venues, @Schema(example = "295732") long deliveries) {
}
