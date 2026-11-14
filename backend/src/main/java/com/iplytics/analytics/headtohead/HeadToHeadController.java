package com.iplytics.analytics.headtohead;

import com.iplytics.analytics.headtohead.HeadToHeadDtos.HeadToHeadDto;
import com.iplytics.common.web.ApiMetaFactory;
import com.iplytics.common.web.ApiResponse;
import com.iplytics.common.web.SeasonFilterParams;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/head-to-head")
@Tag(name = "Head to head", description = "Rivalries between two franchises")
public class HeadToHeadController {

    private final HeadToHeadService service;
    private final ApiMetaFactory meta;

    public HeadToHeadController(HeadToHeadService service, ApiMetaFactory meta) {
        this.service = service;
        this.meta = meta;
    }

    @GetMapping
    @Operation(summary = "Head-to-head record", description = """
            Wins, ties, no results, batting-first and chasing wins, average scores, season split and the latest
            meetings. Unknown teams are 404; the same team twice or an unknown venue is 422.""")
    public ApiResponse<HeadToHeadDto> headToHead(
            @Parameter(description = "First franchise id", required = true) @RequestParam @Positive int team1,
            @Parameter(description = "Second franchise id", required = true) @RequestParam @Positive int team2,
            @ParameterObject @Valid SeasonFilterParams seasons,
            @Parameter(description = "Venue id") @RequestParam(required = false) @Positive Integer venue) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("team1", team1);
        filters.put("team2", team2);
        filters.putAll(seasons.describe());
        if (venue != null) {
            filters.put("venue", venue);
        }
        return ApiResponse.of(service.headToHead(team1, team2, seasons.from(), seasons.to(), venue), meta.meta(filters));
    }
}
