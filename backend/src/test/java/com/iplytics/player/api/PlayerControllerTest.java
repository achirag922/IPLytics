package com.iplytics.player.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iplytics.analytics.comparison.ComparisonController;
import com.iplytics.analytics.comparison.ComparisonService;
import com.iplytics.analytics.comparison.ComparisonService.ComparisonDto;
import com.iplytics.analytics.comparison.Discipline;
import com.iplytics.common.exception.ResourceNotFoundException;
import com.iplytics.common.exception.UnprocessableRequestException;
import com.iplytics.common.web.PageSlice;
import com.iplytics.player.api.PlayerDtos.PlayerSummaryDto;
import com.iplytics.player.service.PlayerService;
import com.iplytics.support.ApiControllerTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest({PlayerController.class, ComparisonController.class})
class PlayerControllerTest extends ApiControllerTest {

    @MockitoBean
    private PlayerService players;

    @MockitoBean
    private ComparisonService comparison;

    @Test
    void searchIsPaged() throws Exception {
        when(players.search("sharma", 2, 5)).thenReturn(new PageSlice<>(
                List.of(new PlayerSummaryDto(4, "RG Sharma", "740742ef", 250, 2008, 2026)), 11));

        mvc.perform(get("/api/v1/players").param("search", "sharma").param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("RG Sharma"))
                .andExpect(jsonPath("$.pagination.page").value(2))
                .andExpect(jsonPath("$.pagination.size").value(5))
                .andExpect(jsonPath("$.pagination.totalElements").value(11))
                .andExpect(jsonPath("$.pagination.totalPages").value(3))
                .andExpect(jsonPath("$.meta.filters.search").value("sharma"));
    }

    @Test
    void pageAndSizeAreBounded() throws Exception {
        mvc.perform(get("/api/v1/players").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].parameter").value("size"));
        mvc.perform(get("/api/v1/players").param("size", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/players").param("page", "-1")).andExpect(status().isBadRequest());
        verifyNoInteractions(players);
    }

    @Test
    void nonNumericOrNonPositiveIdIsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/players/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].parameter").value("id"));
        mvc.perform(get("/api/v1/players/0/batting")).andExpect(status().isBadRequest());
    }

    @Test
    void unknownPlayerIsNotFound() throws Exception {
        when(players.detail(77)).thenThrow(ResourceNotFoundException.of("Player", 77));

        mvc.perform(get("/api/v1/players/77"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Player 77 not found"))
                .andExpect(jsonPath("$.instance").value("/api/v1/players/77"));
    }

    @Test
    void unexpectedErrorsDoNotLeakInternals() throws Exception {
        when(players.detail(5)).thenThrow(new IllegalStateException("C:\\data\\secret.csv password=hunter2"));

        mvc.perform(get("/api/v1/players/5"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorId").isNotEmpty())
                .andExpect(jsonPath("$.detail", not(containsString("secret"))))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @Test
    void compareRoutesToComparisonNotToPlayerById() throws Exception {
        when(comparison.compare(eq(List.of(3, 1)), eq(Discipline.BOWLING), any()))
                .thenReturn(new ComparisonDto(Discipline.BOWLING, List.of()));

        mvc.perform(get("/api/v1/players/compare").param("ids", "3,1").param("discipline", "bowling"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.discipline").value("bowling"))
                .andExpect(jsonPath("$.meta.filters.ids[0]").value(3))
                .andExpect(jsonPath("$.meta.filters.discipline").value("bowling"));
    }

    @Test
    void compareNeedsTwoToFivePlayers() throws Exception {
        mvc.perform(get("/api/v1/players/compare").param("ids", "1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/players/compare").param("ids", "1,2,3,4,5,6")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/players/compare")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/players/compare").param("ids", "1,-2")).andExpect(status().isBadRequest());
        verifyNoInteractions(comparison);
    }

    @Test
    void unknownDisciplineListsAllowedValues() throws Exception {
        mvc.perform(get("/api/v1/players/compare").param("ids", "1,2").param("discipline", "fielding"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.allowedValues", hasItems("batting", "bowling")));
    }

    @Test
    void repeatedPlayerIsUnprocessable() throws Exception {
        when(comparison.compare(eq(List.of(1, 1)), any(), any()))
                .thenThrow(new UnprocessableRequestException("'ids' must not repeat a player"));

        mvc.perform(get("/api/v1/players/compare").param("ids", "1,1")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void playerStatsRejectInvalidSeasonFilters() throws Exception {
        mvc.perform(get("/api/v1/players/4/batting").param("seasonFrom", "2030").param("seasonTo", "2020"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/players/4/vs-teams").param("opponent", "0")).andExpect(status().isBadRequest());
        verifyNoInteractions(players);
    }
}
