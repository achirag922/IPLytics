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

    @Override
    public AnalyticsFilter toFilter() {
        return super.toFilter().team(team);
    }

    @Override
    public Map<String, Object> describe() {
        Map<String, Object> applied = super.describe();
        putIfSet(applied, "team", team);
        return applied;
    }

    public void requireKnownReferences(RefLookup refs) {
        refs.requireTeamFilter("team", team);
    }

    public Integer getTeam() {
        return team;
    }

    public void setTeam(Integer team) {
        this.team = team;
    }
}
