package com.iplytics.analytics.comparison;

import com.iplytics.analytics.comparison.ComparisonService.ComparisonDto;
import com.iplytics.common.web.ApiMetaFactory;
import com.iplytics.common.web.ApiResponse;
import com.iplytics.common.web.SeasonFilterParams;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Players")
public class ComparisonController {

    static final int MAX_PLAYERS = 5;

    private final ComparisonService service;
    private final ApiMetaFactory meta;

    public ComparisonController(ComparisonService service, ApiMetaFactory meta) {
        this.service = service;
        this.meta = meta;
    }

    @GetMapping("/api/v1/players/compare")
    @Operation(summary = "Compare players", description = """
            Batting or bowling figures of 2 to 5 players side by side, in the order requested. Unknown players are
            404; a repeated id is 422.""")
    public ApiResponse<ComparisonDto> compare(
            @Parameter(description = "Comma-separated player ids, 2 to 5", required = true, example = "1,2")
            @RequestParam @NotNull @Size(min = 2, max = MAX_PLAYERS) List<@NotNull @Positive Integer> ids,
            @Parameter(description = "Figures to compare") @RequestParam(defaultValue = "batting") Discipline discipline,
            @ParameterObject @Valid SeasonFilterParams seasons) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("ids", ids);
        filters.put("discipline", discipline.apiName());
        filters.putAll(seasons.describe());
        return ApiResponse.of(service.compare(ids, discipline, seasons.toFilter()), meta.meta(filters));
    }
}
