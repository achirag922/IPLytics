package com.iplytics.analytics.leaderboard.api;

import com.fasterxml.jackson.annotation.JsonValue;
import com.iplytics.analytics.query.LeaderboardQueries.BowlingMetric;
import com.iplytics.common.web.ApiEnum;

/** Bowling leaderboard metrics accepted by the API (definitions: docs/metrics.md section 5). */
public enum BowlingLeaderboardMetric implements ApiEnum {
    WICKETS("wickets", BowlingMetric.WICKETS),
    ECONOMY("economy", BowlingMetric.ECONOMY),
    AVERAGE("average", BowlingMetric.AVERAGE),
    STRIKE_RATE("strikeRate", BowlingMetric.STRIKE_RATE),
    DOT_BALLS("dotBalls", BowlingMetric.DOT_BALLS);

    private final String apiName;
    private final BowlingMetric query;

    BowlingLeaderboardMetric(String apiName, BowlingMetric query) {
        this.apiName = apiName;
        this.query = query;
    }

    @Override
    @JsonValue
    public String apiName() {
        return apiName;
    }

    public BowlingMetric query() {
        return query;
    }
}
