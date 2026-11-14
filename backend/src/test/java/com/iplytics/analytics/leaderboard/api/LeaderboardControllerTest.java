package com.iplytics.analytics.leaderboard.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iplytics.analytics.leaderboard.api.LeaderboardDtos.BattingLeaderDto;
import com.iplytics.analytics.leaderboard.service.LeaderboardService;
import com.iplytics.analytics.query.AnalyticsFilter;
import com.iplytics.common.exception.UnprocessableRequestException;
import com.iplytics.common.web.Refs.PlayerRef;
import com.iplytics.support.ApiControllerTest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(LeaderboardController.class)
class LeaderboardControllerTest extends ApiControllerTest {

    @MockitoBean
    private LeaderboardService service;

    @Test
    void battingLeaderboardIsEnvelopedWithMetaAndCacheHeaders() throws Exception {
        when(service.batting(eq(BattingLeaderboardMetric.STRIKE_RATE), eq(AnalyticsFilter.all().seasons(2024, 2024)),
                eq(200), eq(3))).thenReturn(List.of(new BattingLeaderDto(1, new PlayerRef(9, "A Batter"), 10, 500, 250,
                5, new BigDecimal("100.00"), new BigDecimal("200.00"), 40, 30, 4, 1, new BigDecimal("200.00"))));

        mvc.perform(get("/api/v1/leaderboards/batting")
                        .param("metric", "strikeRate").param("season", "2024").param("minimumBalls", "200")
                        .param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, ETAG))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=60, public"))
                .andExpect(jsonPath("$.data[0].rank").value(1))
                .andExpect(jsonPath("$.data[0].player.name").value("A Batter"))
                .andExpect(jsonPath("$.data[0].value").value(200.00))
                .andExpect(jsonPath("$.pagination.totalElements").value(1))
                .andExpect(jsonPath("$.meta.dataVersion").value("t3-a5"))
                .andExpect(jsonPath("$.meta.generatedAt").exists())
                .andExpect(jsonPath("$.meta.filters.season").value(2024))
                .andExpect(jsonPath("$.meta.metric").value("strikeRate"))
                .andExpect(jsonPath("$.meta.minimumBalls").value(200));
    }

    @Test
    void metricNamesAreCaseInsensitive() throws Exception {
        when(service.batting(eq(BattingLeaderboardMetric.SIXES), any(), isNull(), anyInt())).thenReturn(List.of());

        mvc.perform(get("/api/v1/leaderboards/batting").param("metric", "SIXES")).andExpect(status().isOk());
        verify(service).batting(eq(BattingLeaderboardMetric.SIXES), any(), isNull(), eq(10));
    }

    @Test
    void unknownMetricIsBadRequestListingAllowedValues() throws Exception {
        mvc.perform(get("/api/v1/leaderboards/bowling").param("metric", "maidens"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid request parameters"))
                .andExpect(jsonPath("$.errors[0].parameter").value("metric"))
                .andExpect(jsonPath("$.allowedValues", hasItems("wickets", "economy", "average", "strikeRate",
                        "dotBalls")));
        verifyNoInteractions(service);
    }

    @Test
    void reversedSeasonRangeIsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/leaderboards/batting").param("seasonFrom", "2020").param("seasonTo", "2010"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].message", hasItem("'seasonFrom' must not be after 'seasonTo'")));
    }

    @Test
    void seasonAndRangeTogetherIsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/leaderboards/batting").param("season", "2020").param("seasonFrom", "2010"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void outOfRangeOrMalformedNumbersAreBadRequest() throws Exception {
        mvc.perform(get("/api/v1/leaderboards/batting").param("limit", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/leaderboards/batting").param("limit", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/leaderboards/batting").param("limit", "ten"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].parameter").value("limit"));
        mvc.perform(get("/api/v1/leaderboards/bowling").param("minimumOvers", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/leaderboards/batting").param("season", "2007")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void unknownTeamFilterIsUnprocessable() throws Exception {
        doThrow(new UnprocessableRequestException("Unknown team id 99 in parameter 'team'"))
                .when(refs).requireTeamFilter("team", 99);

        mvc.perform(get("/api/v1/leaderboards/batting").param("team", "99"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Unknown team id 99 in parameter 'team'"))
                .andExpect(jsonPath("$.instance").value("/api/v1/leaderboards/batting"));
    }

    @Test
    void matchingIfNoneMatchIsNotModifiedWithoutQuerying() throws Exception {
        mvc.perform(get("/api/v1/leaderboards/batting").header(HttpHeaders.IF_NONE_MATCH, ETAG))
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.ETAG, ETAG))
                .andExpect(content().string(""));
        verifyNoInteractions(service);
    }
}
