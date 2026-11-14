package com.iplytics.analytics.leaderboard.service;

import com.iplytics.analytics.leaderboard.api.BattingLeaderboardMetric;
import com.iplytics.analytics.leaderboard.api.BowlingLeaderboardMetric;
import com.iplytics.analytics.leaderboard.api.LeaderboardDtos.BattingLeaderDto;
import com.iplytics.analytics.leaderboard.api.LeaderboardDtos.BowlingLeaderDto;
import com.iplytics.analytics.query.AnalyticsFilter;
import com.iplytics.analytics.query.LeaderboardQueries;
import com.iplytics.common.metrics.CricketMetrics;
import com.iplytics.common.web.Refs.PlayerRef;
import java.util.List;
import org.springframework.stereotype.Service;

/** Leaderboards from the V1.3 query layer; this class only maps rows to DTOs. */
@Service
public class LeaderboardService {

    private final LeaderboardQueries queries;

    public LeaderboardService(LeaderboardQueries queries) {
        this.queries = queries;
    }

    /** @param minimumBalls null applies the metric's documented default */
    public List<BattingLeaderDto> batting(BattingLeaderboardMetric metric, AnalyticsFilter filter, Integer minimumBalls,
                                          int limit) {
        return queries.batting(metric.query(), filter, minimumBalls, limit).stream()
                .map(e -> new BattingLeaderDto(e.rank(), new PlayerRef(e.playerId(), e.playerName()), e.innings(),
                        e.runs(), e.balls(), e.dismissals(), CricketMetrics.battingAverage(e.runs(), e.dismissals()),
                        CricketMetrics.strikeRate(e.runs(), e.balls()), e.fours(), e.sixes(), e.fifties(),
                        e.hundreds(), e.value()))
                .toList();
    }

    /** @param minimumOvers whole overs; null applies the metric's documented default */
    public List<BowlingLeaderDto> bowling(BowlingLeaderboardMetric metric, AnalyticsFilter filter, Integer minimumOvers,
                                          int limit) {
        Integer minimumLegalBalls = minimumOvers == null ? null : minimumOvers * 6;
        return queries.bowling(metric.query(), filter, minimumLegalBalls, limit).stream()
                .map(e -> new BowlingLeaderDto(e.rank(), new PlayerRef(e.playerId(), e.playerName()), e.innings(),
                        e.legalBalls(), CricketMetrics.overs(e.legalBalls()), e.runsConceded(), e.wickets(),
                        CricketMetrics.economy(e.runsConceded(), e.legalBalls()),
                        CricketMetrics.bowlingAverage(e.runsConceded(), e.wickets()),
                        CricketMetrics.bowlingStrikeRate(e.legalBalls(), e.wickets()), e.dotBalls(), e.value()))
                .toList();
    }

    public static int effectiveMinimumBalls(BattingLeaderboardMetric metric, Integer requested) {
        return requested != null ? requested : metric.query().defaultMinimumBalls();
    }

    public static int effectiveMinimumOvers(BowlingLeaderboardMetric metric, Integer requested) {
        return requested != null ? requested : metric.query().defaultMinimumLegalBalls() / 6;
    }
}
