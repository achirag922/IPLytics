package com.iplytics.analytics.insights;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iplytics.analytics.insights.InsightDtos.SeasonComparisonDto;
import com.iplytics.analytics.insights.InsightDtos.TossInsightsDto;
import com.iplytics.analytics.insights.InsightDtos.TossRowDto;
import com.iplytics.common.exception.UnprocessableRequestException;
import com.iplytics.support.ApiControllerTest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(InsightsController.class)
class InsightsControllerTest extends ApiControllerTest {

    @MockitoBean
    private InsightsService service;

    @Test
    void tossInsightsAreEnvelopedWithTheirFilters() throws Exception {
        when(service.toss(any())).thenReturn(new TossInsightsDto(
                new TossRowDto(70, 68, 36, new BigDecimal("52.94"), 20), List.of(), List.of(), null));

        mvc.perform(get("/api/v1/insights/toss").param("seasonFrom", "2020").param("team", "4").param("venue", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overall.tossWinnerWinPercentage").value(52.94))
                .andExpect(jsonPath("$.data.team").doesNotExist())
                .andExpect(jsonPath("$.meta.filters.seasonFrom").value(2020))
                .andExpect(jsonPath("$.meta.filters.team").value(4))
                .andExpect(jsonPath("$.meta.filters.venue").value(6));
    }

    @Test
    void invalidFiltersAreBadRequest() throws Exception {
        mvc.perform(get("/api/v1/insights/phases").param("seasonFrom", "2025").param("seasonTo", "2020"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/insights/batting-first").param("team", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/insights/toss").param("season", "2007")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void unknownReferencesAreUnprocessable() throws Exception {
        when(service.phases(any())).thenThrow(new UnprocessableRequestException("Unknown team id 99 in parameter 'team'"));

        mvc.perform(get("/api/v1/insights/phases").param("team", "99")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void seasonComparisonTakesTwoToTenSeasons() throws Exception {
        when(service.seasons(any(), any(), eq(List.of(2016, 2026)))).thenReturn(new SeasonComparisonDto(List.of(), null));

        mvc.perform(get("/api/v1/insights/seasons").param("seasons", "2016,2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.filters.seasons[1]").value(2026));
        mvc.perform(get("/api/v1/insights/seasons").param("seasons", "2016")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/insights/seasons").param("seasons", "2008,2009,2010,2011,2012,2013,2014,2015,2016,2017,2018"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/insights/seasons").param("seasons", "2016,1999")).andExpect(status().isBadRequest());
    }
}
