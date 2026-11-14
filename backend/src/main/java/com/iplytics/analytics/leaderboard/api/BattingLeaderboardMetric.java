package com.iplytics.analytics.leaderboard.api;

import com.fasterxml.jackson.annotation.JsonValue;
import com.iplytics.analytics.query.LeaderboardQueries.BattingMetric;
import com.iplytics.common.web.ApiEnum;

/** Batting leaderboard metrics accepted by the API (definitions: docs/metrics.md section 4). */
public enum BattingLeaderboardMetric implements ApiEnum {
    RUNS("runs", BattingMetric.RUNS),
    AVERAGE("average", BattingMetric.AVERAGE),
    STRIKE_RATE("strikeRate", BattingMetric.STRIKE_RATE),
    FOURS("fours", BattingMetric.FOURS),
    SIXES("sixes", BattingMetric.SIXES),
    HUNDREDS("hundreds", BattingMetric.HUNDREDS),
    FIFTIES("fifties", BattingMetric.FIFTIES);

    private final String apiName;
    private final BattingMetric query;

    BattingLeaderboardMetric(String apiName, BattingMetric query) {
        this.apiName = apiName;
        this.query = query;
    }

    @Override
    @JsonValue
    public String apiName() {
        return apiName;
    }

    public BattingMetric query() {
        return query;
    }
}
