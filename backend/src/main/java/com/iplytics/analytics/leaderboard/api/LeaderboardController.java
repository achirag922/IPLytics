package com.iplytics.analytics.leaderboard.api;

import com.iplytics.analytics.leaderboard.api.LeaderboardDtos.BattingLeaderDto;
import com.iplytics.analytics.leaderboard.api.LeaderboardDtos.BowlingLeaderDto;
import com.iplytics.analytics.leaderboard.service.LeaderboardService;
import com.iplytics.common.web.ApiMetaFactory;
import com.iplytics.common.web.ApiResponse;
import com.iplytics.common.web.RefLookup;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/leaderboards")
@Tag(name = "Leaderboards", description = "Ranked batting and bowling performances. Super overs are excluded.")
public class LeaderboardController {

    private final LeaderboardService service;
    private final RefLookup refs;
    private final ApiMetaFactory meta;

    public LeaderboardController(LeaderboardService service, RefLookup refs, ApiMetaFactory meta) {
        this.service = service;
        this.refs = refs;
        this.meta = meta;
    }

    @GetMapping("/batting")
    @Operation(summary = "Batting leaderboard", description = """
            Ranks players by a batting metric. Ratio metrics need a minimum sample so tiny samples cannot top the
            table: strikeRate defaults to 100 balls faced, average to 5 dismissals (docs/metrics.md section 7).
            average = runs / dismissals; strikeRate = 100 x runs / balls faced (wides are not balls faced).""")
    public ApiResponse<List<BattingLeaderDto>> batting(
            @Parameter(description = "Metric to rank by") @RequestParam(defaultValue = "runs") BattingLeaderboardMetric metric,
            @ParameterObject @Valid LeaderboardFilterParams filter,
            @Parameter(description = "Minimum balls faced (default depends on the metric)")
            @RequestParam(required = false) @Min(0) @Max(100_000) Integer minimumBalls,
            @Parameter(description = "Number of rows") @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        filter.requireKnownReferences(refs);
        List<BattingLeaderDto> rows = service.batting(metric, filter.toFilter(), minimumBalls, limit);
        return ApiResponse.list(rows, meta.meta(filter.describe(), Map.of("metric", metric.apiName(),
                "minimumBalls", LeaderboardService.effectiveMinimumBalls(metric, minimumBalls), "limit", limit)));
    }

    @GetMapping("/bowling")
    @Operation(summary = "Bowling leaderboard", description = """
            Ranks bowlers by a bowling metric. economy (lower is better) defaults to a minimum of 10 overs; average and
            strikeRate need 5 wickets. economy = 6 x runs conceded / legal balls; byes, leg-byes and penalty runs are
            not charged to the bowler; run outs are not bowler wickets.""")
    public ApiResponse<List<BowlingLeaderDto>> bowling(
            @Parameter(description = "Metric to rank by") @RequestParam(defaultValue = "wickets") BowlingLeaderboardMetric metric,
            @ParameterObject @Valid LeaderboardFilterParams filter,
            @Parameter(description = "Minimum whole overs bowled (default depends on the metric)")
            @RequestParam(required = false) @Min(0) @Max(10_000) Integer minimumOvers,
            @Parameter(description = "Number of rows") @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        filter.requireKnownReferences(refs);
        List<BowlingLeaderDto> rows = service.bowling(metric, filter.toFilter(), minimumOvers, limit);
        return ApiResponse.list(rows, meta.meta(filter.describe(), Map.of("metric", metric.apiName(),
                "minimumOvers", LeaderboardService.effectiveMinimumOvers(metric, minimumOvers), "limit", limit)));
    }
}
