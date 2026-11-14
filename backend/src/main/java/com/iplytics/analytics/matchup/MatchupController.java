package com.iplytics.analytics.matchup;

import com.iplytics.analytics.matchup.MatchupService.MatchupDto;
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
@RequestMapping("/api/v1/matchups")
@Tag(name = "Matchups", description = "Batter versus bowler")
public class MatchupController {

    private final MatchupService service;
    private final ApiMetaFactory meta;

    public MatchupController(MatchupService service, ApiMetaFactory meta) {
        this.service = service;
        this.meta = meta;
    }

    @GetMapping
    @Operation(summary = "Batter versus bowler", description = """
            Every ball the batter faced from the bowler. A pair that never met is a normal response with
            occurred = false. Unknown players are 404; the same player twice is 422.""")
    public ApiResponse<MatchupDto> matchup(
            @Parameter(description = "Batter's player id", required = true) @RequestParam @Positive int batterId,
            @Parameter(description = "Bowler's player id", required = true) @RequestParam @Positive int bowlerId,
            @ParameterObject @Valid SeasonFilterParams seasons) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("batterId", batterId);
        filters.put("bowlerId", bowlerId);
        filters.putAll(seasons.describe());
        return ApiResponse.of(service.matchup(batterId, bowlerId, seasons.toFilter()), meta.meta(filters));
    }
}
