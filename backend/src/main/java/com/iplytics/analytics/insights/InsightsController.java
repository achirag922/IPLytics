package com.iplytics.analytics.insights;

import com.iplytics.analytics.insights.InsightDtos.BattingFirstInsightsDto;
import com.iplytics.analytics.insights.InsightDtos.PhaseInsightsDto;
import com.iplytics.analytics.insights.InsightDtos.SeasonComparisonDto;
import com.iplytics.analytics.insights.InsightDtos.TossInsightsDto;
import com.iplytics.common.web.ApiMetaFactory;
import com.iplytics.common.web.ApiResponse;
import com.iplytics.common.web.SeasonFilterParams;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/insights")
@Tag(name = "Insights", description = "Toss impact, batting first vs chasing, phase scoring and season comparison")
public class InsightsController {

    static final int MAX_COMPARED_SEASONS = 10;

    private final InsightsService service;
    private final ApiMetaFactory meta;

    public InsightsController(InsightsService service, ApiMetaFactory meta) {
        this.service = service;
        this.meta = meta;
    }

    @GetMapping("/toss")
    @Operation(summary = "Toss impact", description = """
            How often the toss winner wins, by decision and by season. With team, also the franchise's own toss
            record. team and opponent select the matches they played (both: their meetings).""")
    public ApiResponse<TossInsightsDto> toss(@ParameterObject @Valid InsightFilterParams filter) {
        return ApiResponse.of(service.toss(filter), meta.meta(filter.describe()));
    }

    @GetMapping("/batting-first")
    @Operation(summary = "Batting first vs chasing", description = """
            Results of the side batting first overall and by season, average first-innings score, and chase success
            by target band (under 150, 150-169, 170-189, 190-209, 210 and over).""")
    public ApiResponse<BattingFirstInsightsDto> battingFirst(@ParameterObject @Valid InsightFilterParams filter) {
        return ApiResponse.of(service.battingFirst(filter), meta.meta(filter.describe()));
    }

    @GetMapping("/phases")
    @Operation(summary = "Phase scoring", description = """
            Powerplay, middle and death overs: run rate, wickets, boundary and dot-ball percentages, plus run rate
            by season. With team: the team's batting and, separately, its bowling.""")
    public ApiResponse<PhaseInsightsDto> phases(@ParameterObject @Valid InsightFilterParams filter) {
        return ApiResponse.of(service.phases(filter), meta.meta(filter.describe()));
    }

    @GetMapping("/seasons")
    @Operation(summary = "Season comparison", description = """
            Season-level figures (scoring, boundaries, phases, toss and batting-first results) for a season range or
            for a list of 2 to 10 seasons, with the change from the first to the last season returned.""")
    public ApiResponse<SeasonComparisonDto> seasons(
            @ParameterObject @Valid SeasonFilterParams range,
            @Parameter(description = "Comma-separated seasons to compare (instead of a range)", example = "2016,2026")
            @RequestParam(required = false) @Size(min = 2, max = MAX_COMPARED_SEASONS)
            List<@NotNull @Min(2008) @Max(2100) Integer> seasons) {
        List<Integer> only = seasons == null ? List.of() : seasons;
        Map<String, Object> filters = new LinkedHashMap<>(range.describe());
        if (!only.isEmpty()) {
            filters.put("seasons", only);
        }
        return ApiResponse.of(service.seasons(range.from(), range.to(), only), meta.meta(filters));
    }
}
