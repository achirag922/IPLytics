package com.iplytics.analytics.leaderboard.api;

import com.iplytics.common.web.Refs.PlayerRef;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** Leaderboard rows. {@code value} is the ranked metric; ratios use the CricketMetrics formulas. */
public final class LeaderboardDtos {

    private LeaderboardDtos() {
    }

    public record BattingLeaderDto(@Schema(example = "1") int rank, PlayerRef player, long innings, long runs,
                                   long balls, long dismissals, BigDecimal average, BigDecimal strikeRate, long fours,
                                   long sixes, long fifties, long hundreds,
                                   @Schema(description = "The ranked metric value", example = "9336") BigDecimal value) {
    }

    public record BowlingLeaderDto(@Schema(example = "1") int rank, PlayerRef player, long innings, long legalBalls,
                                   @Schema(example = "120.4") String overs, long runsConceded, long wickets,
                                   BigDecimal economy, BigDecimal average, BigDecimal strikeRate, long dotBalls,
                                   @Schema(description = "The ranked metric value", example = "233") BigDecimal value) {
    }
}
