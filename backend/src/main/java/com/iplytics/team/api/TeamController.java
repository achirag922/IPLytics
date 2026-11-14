package com.iplytics.team.api;

import com.iplytics.common.web.ApiMetaFactory;
import com.iplytics.common.web.ApiResponse;
import com.iplytics.common.web.StatsFilterParams;
import com.iplytics.team.api.TeamDtos.TeamDto;
import com.iplytics.team.api.TeamDtos.TeamOpponentStatsDto;
import com.iplytics.team.api.TeamDtos.TeamVenueStatsDto;
import com.iplytics.team.api.TeamDtos.TeamSeasonStatsDto;
import com.iplytics.team.api.TeamDtos.TeamStatsDto;
import com.iplytics.team.api.TeamDtos.TopPerformersDto;
import com.iplytics.team.service.TeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/teams")
@Tag(name = "Teams", description = "Franchises (renamed teams are one franchise) and their records")
public class TeamController {

    private final TeamService service;
    private final ApiMetaFactory meta;

    public TeamController(TeamService service, ApiMetaFactory meta) {
        this.service = service;
        this.meta = meta;
    }

    @GetMapping
    @Operation(summary = "All franchises", description = "Includes defunct franchises and every historical name.")
    public ApiResponse<List<TeamDto>> list() {
        return ApiResponse.list(service.list(), meta.meta());
    }

    @GetMapping("/{id}")
    @Operation(summary = "One franchise")
    public ApiResponse<TeamDto> get(@PathVariable @Positive int id) {
        return ApiResponse.of(service.get(id), meta.meta());
    }

    @GetMapping("/{id}/stats")
    @Operation(summary = "Franchise record", description = """
            Overall, batting-first, chasing, home and away records with average scores and run rates. Home means the
            venue is in the franchise's curated home city (docs/analytics.md section 7).""")
    public ApiResponse<TeamStatsDto> stats(@PathVariable @Positive int id, @ParameterObject @Valid StatsFilterParams filter) {
        return ApiResponse.of(service.stats(id, filter), meta.meta(filter.describe()));
    }

    @GetMapping("/{id}/seasons")
    @Operation(summary = "Season-by-season record")
    public ApiResponse<List<TeamSeasonStatsDto>> seasons(@PathVariable @Positive int id,
                                                         @ParameterObject @Valid StatsFilterParams filter) {
        return ApiResponse.list(service.seasons(id, filter), meta.meta(filter.describe()));
    }

    @GetMapping("/{id}/venues")
    @Operation(summary = "Record per venue", description = "Results, scores and run rates of the franchise at each venue, most matches first.")
    public ApiResponse<List<TeamVenueStatsDto>> venues(@PathVariable @Positive int id,
                                                       @ParameterObject @Valid StatsFilterParams filter) {
        return ApiResponse.list(service.venues(id, filter), meta.meta(filter.describe()));
    }

    @GetMapping("/{id}/opponents")
    @Operation(summary = "Record against each opponent", description = "Head-to-head records of the franchise against every opponent, most matches first.")
    public ApiResponse<List<TeamOpponentStatsDto>> opponents(@PathVariable @Positive int id,
                                                             @ParameterObject @Valid StatsFilterParams filter) {
        return ApiResponse.list(service.opponents(id, filter), meta.meta(filter.describe()));
    }

    @GetMapping("/{id}/top-performers")
    @Operation(summary = "Leading run scorers and wicket takers for the franchise")
    public ApiResponse<TopPerformersDto> topPerformers(
            @PathVariable @Positive int id, @ParameterObject @Valid StatsFilterParams filter,
            @Parameter(description = "Players per list") @RequestParam(defaultValue = "5") @Min(1) @Max(25) int limit) {
        return ApiResponse.of(service.topPerformers(id, filter, limit), meta.meta(filter.describe(), Map.of("limit", limit)));
    }
}
