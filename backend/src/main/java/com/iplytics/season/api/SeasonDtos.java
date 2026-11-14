package com.iplytics.season.api;

import com.iplytics.common.web.Refs.PlayerRef;
import com.iplytics.common.web.Refs.TeamRef;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Season resources (season = calendar year of the matches; IPL 2008 is labelled 2007/08 by the source). */
public final class SeasonDtos {

    private SeasonDtos() {
    }

    public record SeasonDto(@Schema(example = "2024") int year, @Schema(example = "IPL 2024") String name,
                            @Schema(description = "Label used by the source", example = "2024") String sourceSeason,
                            long matches, @Schema(description = "Winner of the Final; null if unknown") TeamRef champion) {
    }

    public record TeamScoreDto(TeamRef team, TeamRef opponent, int runs, int wickets,
                               @Schema(example = "20.0") String overs, int matchId, LocalDate date) {
    }

    public record SeasonLeaderDto(PlayerRef player, BigDecimal value) {
    }

    @Schema(description = """
            Season totals over regular innings (super overs excluded). averageScore = runs per innings.
            lowestTeamScore is the lowest innings that was all out or used its full overs.""")
    public record SeasonSummaryDto(int year, String name, long matches, long totalRuns, long wickets, long fours,
                                   long sixes, BigDecimal averageScore, BigDecimal runRate, TeamScoreDto highestTeamScore,
                                   TeamScoreDto lowestTeamScore, TeamRef champion, SeasonLeaderDto topRunScorer,
                                   SeasonLeaderDto topWicketTaker,
                                   @Schema(description = "Best strike rate with at least strikeRateMinimumBalls balls faced")
                                   SeasonLeaderDto highestStrikeRate, int strikeRateMinimumBalls,
                                   @Schema(description = "Best economy with at least economyMinimumOvers overs bowled")
                                   SeasonLeaderDto bestEconomy, int economyMinimumOvers) {
    }

    @Schema(description = """
            League-stage standings. points = 2 x wins + no results. netRunRate (3 decimals) = runs scored per over
            faced - runs conceded per over bowled (docs/metrics.md 6b).""")
    public record PointsTableRowDto(int position, TeamRef team, long matches, long wins, long losses, long ties,
                                    long noResults, long points, BigDecimal netRunRate, long runsFor,
                                    @Schema(example = "273.4") String oversFaced, long runsAgainst,
                                    @Schema(example = "270.1") String oversBowled) {
    }

    public record PointsTableDto(int year, List<PointsTableRowDto> rows) {
    }
}
