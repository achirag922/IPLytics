package com.iplytics.venue.api;

import com.iplytics.common.web.ApiMetaFactory;
import com.iplytics.common.web.ApiResponse;
import com.iplytics.common.web.SeasonFilterParams;
import com.iplytics.venue.api.VenueDtos.ScoreBandDto;
import com.iplytics.venue.api.VenueDtos.VenueDto;
import com.iplytics.venue.api.VenueDtos.VenueSeasonStatsDto;
import com.iplytics.venue.api.VenueDtos.VenueStatsDto;
import com.iplytics.venue.service.VenueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/venues")
@Tag(name = "Venues", description = "Grounds (renamed grounds are one venue) and their scoring characteristics")
public class VenueController {

    private final VenueService service;
    private final ApiMetaFactory meta;

    public VenueController(VenueService service, ApiMetaFactory meta) {
        this.service = service;
        this.meta = meta;
    }

    @GetMapping
    @Operation(summary = "All venues", description = "Ordered by number of matches.")
    public ApiResponse<List<VenueDto>> list() {
        return ApiResponse.list(service.list(), meta.meta());
    }

    @GetMapping("/{id}")
    @Operation(summary = "One venue")
    public ApiResponse<VenueDto> get(@PathVariable @Positive int id) {
        return ApiResponse.of(service.get(id), meta.meta());
    }

    @GetMapping("/{id}/stats")
    @Operation(summary = "Venue characteristics", description = """
            Matches, average first-innings score, highest and lowest completed scores, batting-first and chasing
            win percentages, average run rate, wickets and boundaries per innings.""")
    public ApiResponse<VenueStatsDto> stats(@PathVariable @Positive int id, @ParameterObject @Valid SeasonFilterParams filter) {
        return ApiResponse.of(service.stats(id, filter), meta.meta(filter.describe()));
    }

    @GetMapping("/{id}/seasons")
    @Operation(summary = "Venue characteristics per season", description = "The same figures as /stats for each season played at the venue.")
    public ApiResponse<List<VenueSeasonStatsDto>> seasons(@PathVariable @Positive int id,
                                                          @ParameterObject @Valid SeasonFilterParams filter) {
        return ApiResponse.list(service.seasons(id, filter), meta.meta(filter.describe()));
    }

    @GetMapping("/{id}/score-distribution")
    @Operation(summary = "First-innings score distribution", description = """
            First-innings totals in 20-run bands (under 120, 120-139, ..., 220 and over) over matches with a result
            and no D/L adjustment. Every band is returned.""")
    public ApiResponse<List<ScoreBandDto>> scoreDistribution(@PathVariable @Positive int id,
                                                             @ParameterObject @Valid SeasonFilterParams filter) {
        return ApiResponse.list(service.scoreDistribution(id, filter), meta.meta(filter.describe()));
    }
}
