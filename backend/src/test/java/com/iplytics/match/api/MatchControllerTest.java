package com.iplytics.match.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iplytics.analytics.headtohead.HeadToHeadController;
import com.iplytics.analytics.headtohead.HeadToHeadService;
import com.iplytics.analytics.matchup.MatchupController;
import com.iplytics.analytics.matchup.MatchupService;
import com.iplytics.analytics.matchup.MatchupService.MatchupDto;
import com.iplytics.analytics.query.AnalyticsFilter;
import com.iplytics.common.exception.ResourceNotFoundException;
import com.iplytics.common.exception.UnprocessableRequestException;
import com.iplytics.common.web.PageSlice;
import com.iplytics.common.web.Refs.PlayerRef;
import com.iplytics.match.repository.MatchCriteria;
import com.iplytics.match.service.MatchService;
import com.iplytics.support.ApiControllerTest;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest({MatchController.class, HeadToHeadController.class, MatchupController.class})
class MatchControllerTest extends ApiControllerTest {

    @MockitoBean
    private MatchService matches;

    @MockitoBean
    private HeadToHeadService headToHead;

    @MockitoBean
    private MatchupService matchups;

    @Test
    void matchFiltersReachTheServiceAndTheMeta() throws Exception {
        when(matches.list(any(), anyInt(), anyInt())).thenReturn(new PageSlice<>(List.of(), 41));

        mvc.perform(get("/api/v1/matches").param("season", "2019").param("team", "6").param("opponent", "1")
                        .param("venue", "30").param("dateFrom", "2019-04-01").param("dateTo", "2019-04-30")
                        .param("stage", "League").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalElements").value(41))
                .andExpect(jsonPath("$.pagination.totalPages").value(3))
                .andExpect(jsonPath("$.meta.filters.dateFrom").value("2019-04-01"))
                .andExpect(jsonPath("$.meta.filters.stage").value("League"));

        ArgumentCaptor<MatchCriteria> criteria = ArgumentCaptor.forClass(MatchCriteria.class);
        verify(matches).list(criteria.capture(), eq(1), eq(20));
        assertThat(criteria.getValue()).isEqualTo(new MatchCriteria(2019, 2019, 6, 1, 30,
                LocalDate.of(2019, 4, 1), LocalDate.of(2019, 4, 30), "League"));
        verify(refs).requireTeamFilter("team", 6);
        verify(refs).requireTeamFilter("opponent", 1);
        verify(refs).requireVenueFilter("venue", 30);
    }

    @Test
    void seasonRangeUsesTheSharedSeasonFilter() throws Exception {
        when(matches.list(any(), anyInt(), anyInt())).thenReturn(new PageSlice<>(List.of(), 0));

        mvc.perform(get("/api/v1/matches").param("seasonFrom", "2020").param("seasonTo", "2022"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.filters.seasonFrom").value(2020));
        verify(matches).list(eq(new MatchCriteria(2020, 2022, null, null, null, null, null, null)), eq(0), eq(20));

        mvc.perform(get("/api/v1/matches").param("season", "2020").param("seasonTo", "2022"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidDatesAreBadRequest() throws Exception {
        mvc.perform(get("/api/v1/matches").param("dateFrom", "2020-05-01").param("dateTo", "2020-04-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("'dateFrom' must not be after 'dateTo'"));
        mvc.perform(get("/api/v1/matches").param("dateFrom", "2020-13-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].parameter").value("dateFrom"));
        verifyNoInteractions(matches);
    }

    @Test
    void unknownFilterIdIsUnprocessable() throws Exception {
        doThrow(new UnprocessableRequestException("Unknown venue id 999 in parameter 'venue'"))
                .when(refs).requireVenueFilter("venue", 999);

        mvc.perform(get("/api/v1/matches").param("venue", "999")).andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(matches);
    }

    @Test
    void unknownMatchIsNotFound() throws Exception {
        when(matches.scorecard(123456)).thenThrow(ResourceNotFoundException.of("Match", 123456));
        when(matches.progression(123456)).thenThrow(ResourceNotFoundException.of("Match", 123456));

        mvc.perform(get("/api/v1/matches/123456/scorecard")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/matches/123456/progression")).andExpect(status().isNotFound());
    }

    @Test
    void headToHeadNeedsBothTeams() throws Exception {
        mvc.perform(get("/api/v1/head-to-head").param("team1", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].parameter").value("team2"));
        verifyNoInteractions(headToHead);
    }

    @Test
    void headToHeadOfATeamWithItselfIsUnprocessable() throws Exception {
        when(headToHead.headToHead(2, 2, null, null, null))
                .thenThrow(new UnprocessableRequestException("'team1' and 'team2' must be different franchises"));

        mvc.perform(get("/api/v1/head-to-head").param("team1", "2").param("team2", "2"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void matchupThatNeverHappenedIsAnEmptyResultNotAnError() throws Exception {
        when(matchups.matchup(eq(10), eq(20), eq(AnalyticsFilter.all().seasons(2015, 2020)))).thenReturn(
                new MatchupDto(new PlayerRef(10, "A"), new PlayerRef(20, "B"), false, 0, 0, 0, 0, 0, 0, 0, null, null,
                        null, null));

        mvc.perform(get("/api/v1/matchups").param("batterId", "10").param("bowlerId", "20")
                        .param("seasonFrom", "2015").param("seasonTo", "2020"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.occurred").value(false))
                .andExpect(jsonPath("$.data.balls").value(0))
                .andExpect(jsonPath("$.meta.filters.seasonFrom").value(2015));
    }

    @Test
    void matchupNeedsBothPlayers() throws Exception {
        mvc.perform(get("/api/v1/matchups").param("batterId", "10")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/matchups").param("batterId", "x").param("bowlerId", "2"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(matchups);
    }
}
