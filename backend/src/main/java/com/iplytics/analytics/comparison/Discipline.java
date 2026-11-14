package com.iplytics.analytics.comparison;

import com.fasterxml.jackson.annotation.JsonValue;
import com.iplytics.common.web.ApiEnum;

/** Which figures a player comparison returns. */
public enum Discipline implements ApiEnum {
    BATTING("batting"),
    BOWLING("bowling");

    private final String apiName;

    Discipline(String apiName) {
        this.apiName = apiName;
    }

    @Override
    @JsonValue
    public String apiName() {
        return apiName;
    }
}
