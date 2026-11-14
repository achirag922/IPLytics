package com.iplytics.overview;

import com.iplytics.common.web.ApiMetaFactory;
import com.iplytics.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Overview", description = "Size of the data set")
public class OverviewController {

    private final OverviewRepository overview;
    private final ApiMetaFactory meta;

    public OverviewController(OverviewRepository overview, ApiMetaFactory meta) {
        this.overview = overview;
        this.meta = meta;
    }

    @GetMapping("/api/v1/overview")
    @Operation(summary = "Data-set counts", description = "Seasons, matches, franchises, players, venues and deliveries.")
    public ApiResponse<OverviewDto> overview() {
        return ApiResponse.of(overview.overview(), meta.meta());
    }
}
