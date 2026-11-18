package com.iplytics.analytics.leaderboard.api;

import com.iplytics.analytics.query.AnalyticsFilter;
import com.iplytics.common.web.RefLookup;
import com.iplytics.common.web.SeasonFilterParams;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Positive;
import java.util.Map;

/** Season filter plus the player's franchise. */
public class LeaderboardFilterParams extends SeasonFilterParams {

    @Parameter(description = "Only performances for this franchise (team id)")
    @Positive
    private Integer team;

    @Parameter(description = "Only performances at this venue (venue id)")
    @Positive
    private Integer venue;

    @Override
    public AnalyticsFilter toFilter() {
        return super.toFilter().team(team).venue(venue);
    }

    @Override
    public Map<String, Object> describe() {
        Map<String, Object> applied = super.describe();
        putIfSet(applied, "team", team);
        putIfSet(applied, "venue", venue);
        return applied;
    }

    public void requireKnownReferences(RefLookup refs) {
        refs.requireTeamFilter("team", team);
        refs.requireVenueFilter("venue", venue);
    }

    public Integer getTeam() {
        return team;
    }

    public void setTeam(Integer team) {
        this.team = team;
    }

    public Integer getVenue() {
        return venue;
    }

    public void setVenue(Integer venue) {
        this.venue = venue;
    }
}
