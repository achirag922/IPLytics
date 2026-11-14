package com.iplytics.player.api;

import com.iplytics.analytics.query.BattingQueries.BattingStats;
import com.iplytics.analytics.query.BowlingQueries.BowlingStats;
import com.iplytics.player.api.PlayerDtos.BattingSummaryDto;
import com.iplytics.player.api.PlayerDtos.BowlingSummaryDto;

/** Maps V1.3 query results to API DTOs; no cricket calculation happens here (ratios come with the stats). */
public final class PlayerStatsMapper {

    private PlayerStatsMapper() {
    }

    public static BattingSummaryDto batting(BattingStats s, long matches) {
        return new BattingSummaryDto(matches, s.innings(), s.notOuts(), s.runs(), s.balls(), s.average(),
                s.strikeRate(), s.fours(), s.sixes(), s.fifties(), s.hundreds(), s.ducks(), s.highestScore(),
                s.highestScoreNotOut(), s.dotBalls(), s.dotBallPercentage(), s.boundaryPercentage(),
                s.ballsPerBoundary());
    }

    public static BowlingSummaryDto bowling(BowlingStats s, long matches) {
        String best = s.bestWickets() == null ? null : s.bestWickets() + "/" + s.bestRuns();
        return new BowlingSummaryDto(matches, s.innings(), s.legalBalls(), s.overs(), s.runsConceded(), s.wickets(),
                s.economy(), s.average(), s.strikeRate(), s.dotBalls(), s.dotBallPercentage(), s.wides(), s.noBalls(),
                s.foursConceded(), s.sixesConceded(), s.threeWicketHauls(), s.fourWicketHauls(), s.fiveWicketHauls(),
                best);
    }
}
