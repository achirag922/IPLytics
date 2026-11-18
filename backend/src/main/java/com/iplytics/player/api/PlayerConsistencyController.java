package com.iplytics.player.api;

import com.iplytics.common.web.ApiMetaFactory;
import com.iplytics.common.web.ApiResponse;
import com.iplytics.common.web.StatsFilterParams;
import com.iplytics.player.api.ConsistencyDtos.PlayerConsistencyDto;
import com.iplytics.player.service.PlayerConsistencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Players")
public class PlayerConsistencyController {

    private final PlayerConsistencyService service;
    private final ApiMetaFactory meta;

    public PlayerConsistencyController(PlayerConsistencyService service, ApiMetaFactory meta) {
        this.service = service;
        this.meta = meta;
    }

    @GetMapping("/api/v1/players/{id}/consistency")
    @Operation(summary = "Consistency and form", description = """
            How steady the player is: median score, spread (standard deviation, coefficient of variation), share of
            30+ / 50+ / single-figure innings and a score distribution; for bowling, share of wicket-taking innings
            and economy spread. Includes the last 10 innings of each. Filters as for the other player statistics.""")
    public ApiResponse<PlayerConsistencyDto> consistency(@PathVariable @Positive int id,
                                                         @ParameterObject @Valid StatsFilterParams filter) {
        return ApiResponse.of(service.consistency(id, filter), meta.meta(filter.describe()));
    }
}
