package com.iplytics.player.api;

import com.iplytics.common.web.ApiMetaFactory;
import com.iplytics.common.web.ApiResponse;
import com.iplytics.common.web.PageSlice;
import com.iplytics.common.web.Pagination;
import com.iplytics.common.web.StatsFilterParams;
import com.iplytics.player.api.PlayerDtos.PlayerBattingDto;
import com.iplytics.player.api.PlayerDtos.PlayerBowlingDto;
import com.iplytics.player.api.PlayerDtos.PlayerDetailDto;
import com.iplytics.player.api.PlayerDtos.PlayerPhasesDto;
import com.iplytics.player.api.PlayerDtos.PlayerSummaryDto;
import com.iplytics.player.api.PlayerDtos.PlayerVenuesDto;
import com.iplytics.player.api.PlayerDtos.PlayerVsTeamsDto;
import com.iplytics.player.service.PlayerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/players")
@Tag(name = "Players", description = "Players identified by Cricsheet registry id; statistics exclude super overs")
public class PlayerController {

    private final PlayerService service;
    private final ApiMetaFactory meta;

    public PlayerController(PlayerService service, ApiMetaFactory meta) {
        this.service = service;
        this.meta = meta;
    }

    @GetMapping
    @Operation(summary = "Search players", description = "Case-insensitive match on the display name or any name the player has used.")
    public ApiResponse<List<PlayerSummaryDto>> list(
            @Parameter(description = "Part of a player name") @RequestParam(required = false) @Size(max = 60) String search,
            @Parameter(description = "Zero-based page") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (max 100)") @RequestParam(defaultValue = "20") @Min(1) @Max(Pagination.MAX_SIZE) int size) {
        PageSlice<PlayerSummaryDto> slice = service.search(search, page, size);
        return ApiResponse.page(slice.items(), Pagination.of(page, size, slice.total()),
                meta.meta(search == null ? Map.of() : Map.of("search", search)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Player profile", description = "Names used over time and franchises played for.")
    public ApiResponse<PlayerDetailDto> get(@PathVariable @Positive int id) {
        return ApiResponse.of(service.detail(id), meta.meta());
    }

    @GetMapping("/{id}/batting")
    @Operation(summary = "Batting statistics", description = "Career (filtered) figures and a season-by-season breakdown.")
    public ApiResponse<PlayerBattingDto> batting(@PathVariable @Positive int id,
                                                 @ParameterObject @Valid StatsFilterParams filter) {
        return ApiResponse.of(service.batting(id, filter), meta.meta(filter.describe()));
    }

    @GetMapping("/{id}/bowling")
    @Operation(summary = "Bowling statistics", description = "Career (filtered) figures and a season-by-season breakdown.")
    public ApiResponse<PlayerBowlingDto> bowling(@PathVariable @Positive int id,
                                                 @ParameterObject @Valid StatsFilterParams filter) {
        return ApiResponse.of(service.bowling(id, filter), meta.meta(filter.describe()));
    }

    @GetMapping("/{id}/phases")
    @Operation(summary = "Batting and bowling by phase", description = "Powerplay (overs 1-6), middle (7-15), death (16-20).")
    public ApiResponse<PlayerPhasesDto> phases(@PathVariable @Positive int id,
                                               @ParameterObject @Valid StatsFilterParams filter) {
        return ApiResponse.of(service.phases(id, filter), meta.meta(filter.describe()));
    }

    @GetMapping("/{id}/vs-teams")
    @Operation(summary = "Batting and bowling against each franchise")
    public ApiResponse<PlayerVsTeamsDto> vsTeams(@PathVariable @Positive int id,
                                                 @ParameterObject @Valid StatsFilterParams filter) {
        return ApiResponse.of(service.vsTeams(id, filter), meta.meta(filter.describe()));
    }

    @GetMapping("/{id}/venues")
    @Operation(summary = "Batting and bowling at each venue")
    public ApiResponse<PlayerVenuesDto> venues(@PathVariable @Positive int id,
                                               @ParameterObject @Valid StatsFilterParams filter) {
        return ApiResponse.of(service.venues(id, filter), meta.meta(filter.describe()));
    }
}
