package com.iplytics.common.web;

import com.iplytics.analytics.query.AnalyticsFilter;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Positive;
import java.util.Map;

/** Season filter plus opponent franchise and venue, for team and player statistics. */
public class StatsFilterParams extends SeasonFilterParams {

    @Parameter(description = "Only matches against this franchise (team id)")
    @Positive
    private Integer opponent;

    @Parameter(description = "Only matches at this venue (venue id)")
    @Positive
    private Integer venue;

    @Override
    public AnalyticsFilter toFilter() {
        return super.toFilter().opponent(opponent).venue(venue);
    }

    @Override
    public Map<String, Object> describe() {
        Map<String, Object> applied = super.describe();
        putIfSet(applied, "opponent", opponent);
        putIfSet(applied, "venue", venue);
        return applied;
    }

    /** @throws com.iplytics.common.exception.UnprocessableRequestException for unknown opponent / venue ids */
    public void requireKnownReferences(RefLookup refs) {
        refs.requireTeamFilter("opponent", opponent);
        refs.requireVenueFilter("venue", venue);
    }

    public Integer getOpponent() {
        return opponent;
    }

    public void setOpponent(Integer opponent) {
        this.opponent = opponent;
    }

    public Integer getVenue() {
        return venue;
    }

    public void setVenue(Integer venue) {
        this.venue = venue;
    }
}
