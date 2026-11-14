package com.iplytics.common.web;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.iplytics.analytics.query.AnalyticsFilter;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.LinkedHashMap;
import java.util.Map;

/** Season filter: one season, or an inclusive range. Bound from query parameters and validated. */
public class SeasonFilterParams {

    @Parameter(description = "One IPL season (year), e.g. 2024. Cannot be combined with seasonFrom/seasonTo.")
    @Min(2008)
    @Max(2100)
    private Integer season;

    @Parameter(description = "First season of a range (inclusive)")
    @Min(2008)
    @Max(2100)
    private Integer seasonFrom;

    @Parameter(description = "Last season of a range (inclusive); must not be before seasonFrom")
    @Min(2008)
    @Max(2100)
    private Integer seasonTo;

    @JsonIgnore
    @AssertTrue(message = "use either 'season' or 'seasonFrom'/'seasonTo', not both")
    public boolean isSeasonOrRange() {
        return season == null || (seasonFrom == null && seasonTo == null);
    }

    @JsonIgnore
    @AssertTrue(message = "'seasonFrom' must not be after 'seasonTo'")
    public boolean isSeasonRangeOrdered() {
        return seasonFrom == null || seasonTo == null || seasonFrom <= seasonTo;
    }

    public Integer from() {
        return season != null ? season : seasonFrom;
    }

    public Integer to() {
        return season != null ? season : seasonTo;
    }

    public AnalyticsFilter toFilter() {
        return AnalyticsFilter.all().seasons(from(), to());
    }

    /** Applied filters for response metadata (non-null values only). */
    public Map<String, Object> describe() {
        Map<String, Object> applied = new LinkedHashMap<>();
        putIfSet(applied, "season", season);
        putIfSet(applied, "seasonFrom", seasonFrom);
        putIfSet(applied, "seasonTo", seasonTo);
        return applied;
    }

    protected static void putIfSet(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    public Integer getSeason() {
        return season;
    }

    public void setSeason(Integer season) {
        this.season = season;
    }

    public Integer getSeasonFrom() {
        return seasonFrom;
    }

    public void setSeasonFrom(Integer seasonFrom) {
        this.seasonFrom = seasonFrom;
    }

    public Integer getSeasonTo() {
        return seasonTo;
    }

    public void setSeasonTo(Integer seasonTo) {
        this.seasonTo = seasonTo;
    }
}
