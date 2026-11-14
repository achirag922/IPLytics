package com.iplytics.team.api;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iplytics.common.exception.ResourceNotFoundException;
import com.iplytics.season.api.SeasonController;
import com.iplytics.season.service.SeasonService;
import com.iplytics.support.ApiControllerTest;
import com.iplytics.team.api.TeamDtos.TeamDto;
import com.iplytics.team.service.TeamService;
import com.iplytics.venue.api.VenueController;
import com.iplytics.venue.service.VenueService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest({TeamController.class, VenueController.class, SeasonController.class})
class ReferenceControllersTest extends ApiControllerTest {

    @MockitoBean
    private TeamService teams;

    @MockitoBean
    private VenueService venues;

    @MockitoBean
    private SeasonService seasons;

    @Test
    void unpaginatedListsAreOneFullPage() throws Exception {
        when(teams.list()).thenReturn(List.of(
                new TeamDto(1, "CSK", "Chennai Super Kings", true, "Chennai", 2008, 2026, 250, List.of(), null, null),
                new TeamDto(2, "MI", "Mumbai Indians", true, "Mumbai", 2008, 2026, 270, List.of(), null, null)));

        mvc.perform(get("/api/v1/teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.pagination.page").value(0))
                .andExpect(jsonPath("$.pagination.size").value(2))
                .andExpect(jsonPath("$.pagination.totalElements").value(2))
                .andExpect(jsonPath("$.pagination.totalPages").value(1))
                .andExpect(jsonPath("$.meta.filters").doesNotExist());
    }

    @Test
    void seasonsOutsideTheIplRangeAreBadRequest() throws Exception {
        mvc.perform(get("/api/v1/seasons/2007/summary")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/seasons/2101/points-table")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/seasons/2019/summary").param("minimumOvers", "-5")).andExpect(status().isBadRequest());
        verifyNoInteractions(seasons);
    }

    @Test
    void seasonWithoutMatchesIsNotFound() throws Exception {
        when(seasons.pointsTable(2011)).thenThrow(ResourceNotFoundException.of("Season", 2011));

        mvc.perform(get("/api/v1/seasons/2011/points-table"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Season 2011 not found"));
    }

    @Test
    void teamAndVenueIdsAndLimitsAreValidated() throws Exception {
        mvc.perform(get("/api/v1/teams/0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/teams/3/top-performers").param("limit", "26")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/teams/3/stats").param("venue", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/venues/x/stats")).andExpect(status().isBadRequest());
        verifyNoInteractions(teams, venues);
    }

    @Test
    void unknownVenueIsNotFound() throws Exception {
        when(venues.get(anyInt())).thenThrow(ResourceNotFoundException.of("Venue", 404));

        mvc.perform(get("/api/v1/venues/404")).andExpect(status().isNotFound());
    }
}
