package com.iplytics.match.api;

import com.iplytics.common.web.ApiMetaFactory;
import com.iplytics.common.web.ApiResponse;
import com.iplytics.common.web.PageSlice;
import com.iplytics.common.web.Pagination;
import com.iplytics.common.web.RefLookup;
import com.iplytics.match.api.MatchDtos.MatchDetailDto;
import com.iplytics.match.api.MatchDtos.MatchProgressionDto;
import com.iplytics.match.api.MatchDtos.MatchSummaryDto;
import com.iplytics.match.api.MatchDtos.ScorecardDto;
import com.iplytics.match.service.MatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/matches")
@Tag(name = "Matches", description = "Results, scorecards and over-by-over progression")
public class MatchController {

    private final MatchService service;
    private final RefLookup refs;
    private final ApiMetaFactory meta;

    public MatchController(MatchService service, RefLookup refs, ApiMetaFactory meta) {
        this.service = service;
        this.refs = refs;
        this.meta = meta;
    }

    @GetMapping
    @Operation(summary = "Matches, newest first", description = """
            Paged. 'team' and 'opponent' each mean the franchise played; together they select the meetings of the
            two. Unknown team or venue ids are rejected with 422.""")
    public ApiResponse<List<MatchSummaryDto>> list(
            @ParameterObject @Valid MatchFilterParams filter,
            @Parameter(description = "Zero-based page") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (max 100)") @RequestParam(defaultValue = "20") @Min(1) @Max(Pagination.MAX_SIZE) int size) {
        filter.requireKnownReferences(refs);
        PageSlice<MatchSummaryDto> slice = service.list(filter.toCriteria(), page, size);
        return ApiResponse.page(slice.items(), Pagination.of(page, size, slice.total()), meta.meta(filter.describe()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Match details", description = "Teams, toss, result, target, officials and innings scores.")
    public ApiResponse<MatchDetailDto> get(@PathVariable @Positive int id) {
        return ApiResponse.of(service.detail(id), meta.meta());
    }

    @GetMapping("/{id}/scorecard")
    @Operation(summary = "Full scorecard", description = """
            Per innings (super overs included and flagged): batting with dismissals, did not bat, bowling in bowling
            order, extras breakdown and fall of wickets.""")
    public ApiResponse<ScorecardDto> scorecard(@PathVariable @Positive int id) {
        return ApiResponse.of(service.scorecard(id), meta.meta());
    }

    @GetMapping("/{id}/progression")
    @Operation(summary = "Over-by-over progression", description = """
            Per innings and over: runs, wickets, extras and legal balls (Manhattan), plus running totals and run rate
            (worm). Overs are 1-based.""")
    public ApiResponse<MatchProgressionDto> progression(@PathVariable @Positive int id) {
        return ApiResponse.of(service.progression(id), meta.meta());
    }
}
