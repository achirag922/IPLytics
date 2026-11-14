package com.iplytics.season.api;

import com.iplytics.common.web.ApiMetaFactory;
import com.iplytics.common.web.ApiResponse;
import com.iplytics.season.api.SeasonDtos.PointsTableDto;
import com.iplytics.season.api.SeasonDtos.SeasonDto;
import com.iplytics.season.api.SeasonDtos.SeasonSummaryDto;
import com.iplytics.season.service.SeasonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/seasons")
@Tag(name = "Seasons", description = "IPL seasons by calendar year")
public class SeasonController {

    private final SeasonService service;
    private final ApiMetaFactory meta;

    public SeasonController(SeasonService service, ApiMetaFactory meta) {
        this.service = service;
        this.meta = meta;
    }

    @GetMapping
    @Operation(summary = "All seasons", description = "With match counts and the champion (winner of the Final).")
    public ApiResponse<List<SeasonDto>> list() {
        return ApiResponse.list(service.list(), meta.meta());
    }

    @GetMapping("/{year}/summary")
    @Operation(summary = "Season summary", description = "Totals, extreme team scores, champion and season leaders.")
    public ApiResponse<SeasonSummaryDto> summary(
            @PathVariable @Min(2008) @Max(2100) int year,
            @Parameter(description = "Minimum balls faced for the strike-rate leader")
            @RequestParam(defaultValue = "100") @Min(0) @Max(10_000) int minimumBalls,
            @Parameter(description = "Minimum overs bowled for the economy leader")
            @RequestParam(defaultValue = "10") @Min(0) @Max(1_000) int minimumOvers) {
        return ApiResponse.of(service.summary(year, minimumBalls, minimumOvers),
                meta.meta(Map.of(), Map.of("season", year)));
    }

    @GetMapping("/{year}/points-table")
    @Operation(summary = "League points table", description = """
            League-stage matches only. 2 points per win (a super-over win counts), 1 per no result. Net run rate:
            runs scored per over faced minus runs conceded per over bowled; a side bowled out is charged its full
            allotted overs; in a D/L result the side batting first is credited with (target - 1) runs off the chasing
            side's allotted overs; no-results and super overs are excluded (docs/metrics.md 6b).""")
    public ApiResponse<PointsTableDto> pointsTable(@PathVariable @Min(2008) @Max(2100) int year) {
        return ApiResponse.of(service.pointsTable(year), meta.meta(Map.of(), Map.of("season", year)));
    }
}
