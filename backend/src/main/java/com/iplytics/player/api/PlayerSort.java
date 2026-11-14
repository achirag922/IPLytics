package com.iplytics.player.api;

import com.fasterxml.jackson.annotation.JsonValue;
import com.iplytics.analytics.query.PlayerTotalsQueries.Order;
import com.iplytics.common.web.ApiEnum;

/** Orders of the player list (descending for the counts, then by name). */
public enum PlayerSort implements ApiEnum {
    NAME("name", Order.NAME),
    MATCHES("matches", Order.MATCHES),
    RUNS("runs", Order.RUNS),
    WICKETS("wickets", Order.WICKETS);

    private final String apiName;
    private final Order order;

    PlayerSort(String apiName, Order order) {
        this.apiName = apiName;
        this.order = order;
    }

    @Override
    @JsonValue
    public String apiName() {
        return apiName;
    }

    public Order order() {
        return order;
    }
}
