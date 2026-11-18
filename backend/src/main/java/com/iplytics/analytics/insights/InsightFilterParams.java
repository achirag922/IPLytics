package com.iplytics.analytics.insights;

import com.iplytics.analytics.query.AnalyticsFilter;
import com.iplytics.common.web.RefLookup;
import com.iplytics.common.web.StatsFilterParams;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Positive;
import java.util.Map;

/**
 * Insight scope: seasons, venue, a franchise and an opponent. For match-level insights (toss, batting first) team
 * and opponent select the matches they played in (both: their meetings); for phases, the team's batting and
 * bowling.
 */
public class InsightFilterParams extends StatsFilterParams {

    @Parameter(description = "Franchise (team id)")
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

    @Override
    public void requireKnownReferences(RefLookup refs) {
        super.requireKnownReferences(refs);
        refs.requireTeamFilter("team", team);
    }

    public Integer getTeam() {
        return team;
    }

    public void setTeam(Integer team) {
        this.team = team;
    }
}
