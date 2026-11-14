package com.iplytics.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.Map;
import java.util.Optional;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI document (/v3/api-docs, UI /swagger-ui.html; both disabled in the prod profile). Every /api/v1 operation
 * gets the shared error responses (RFC 9457 problem details) and the conditional-request 304, so generated clients
 * see the full contract.
 */
@Configuration
public class OpenApiConfig {

    static final String PROBLEM_SCHEMA = "ApiProblem";
    static final String PROBLEM_MEDIA_TYPE = "application/problem+json";

    private static final String DESCRIPTION = """
            Read-only IPL analytics (2008 onwards, Cricsheet ball-by-ball data). Figures come from the analytics
            layer; this API does not compute cricket statistics of its own.

            **Envelope.** Single resources are `{"data": ..., "meta": ...}`; collections add
            `"pagination": {"page", "size", "totalElements", "totalPages"}`. Pages are zero-based; `size` is at most
            100 (default 20). Unpaginated collections are returned as one page. `meta` holds `generatedAt` (UTC),
            `dataVersion` (the transform and analytics-refresh runs the figures come from) and the applied `filters`.

            **Filters.** `season` or `seasonFrom`/`seasonTo` (inclusive, 2008 onwards; not both). Super overs are
            excluded from player and team figures. Unknown filter ids (team, opponent, venue) are 422; unknown
            resources in the path or the subject of a request are 404.

            **Errors** are `application/problem+json` (RFC 9457): 400 invalid parameters (with `errors[]` and, for
            enumerations, `allowedValues`), 404 unknown resource, 422 valid but unprocessable combination, 500
            unexpected (with an `errorId` to quote; no internals are exposed).

            **Caching.** Responses carry a weak `ETag` tied to the data version and `Cache-Control: public,
            max-age=60`. Send `If-None-Match` to get `304 Not Modified` while the data is unchanged.

            **Metrics** (docs/metrics.md): batting average = runs / dismissals; strike rate = 100 x runs / balls
            faced (wides excluded); economy = 6 x runs conceded / legal balls; bowling average = runs conceded /
            wickets; bowling strike rate = legal balls / wickets; dot-ball % and boundary % are of balls faced
            (bowled). Runs conceded exclude byes, leg byes and penalties; bowler wickets exclude run outs and
            retirements. Ratios are rounded half-up to 2 places and are null when the denominator is zero. Net run
            rate = runs for / overs faced - runs against / overs bowled, with an all-out side charged its full
            allotted overs (3 places). Overs are shown as overs.balls ("19.4").""";

    @Bean
    public OpenAPI iplyticsOpenApi(Optional<BuildProperties> buildProperties) {
        return new OpenAPI()
                .info(new Info()
                        .title("IPLytics API")
                        .description(DESCRIPTION)
                        .version(buildProperties.map(BuildProperties::getVersion).orElse("dev")));
    }

    /** Adds the shared 304/400/404/422/500 responses to every versioned API operation. */
    @Bean
    public OpenApiCustomizer standardResponses() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            openApi.getComponents().addSchemas(PROBLEM_SCHEMA, problemSchema());
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, item) -> {
                if (path.startsWith("/api/v1/") && !path.startsWith("/api/v1/system")) {
                    item.readOperations().forEach(OpenApiConfig::addStandardResponses);
                }
            });
        };
    }

    private static void addStandardResponses(Operation operation) {
        ApiResponses responses = operation.getResponses() == null ? new ApiResponses() : operation.getResponses();
        responses.computeIfAbsent("304", k -> new ApiResponse()
                .description("Not modified: the If-None-Match ETag still matches the data version")
                .headers(Map.of("ETag", new Header().description("Weak entity tag of the data version")
                        .schema(new StringSchema().example("W/\"t12-a7\"")))));
        responses.computeIfAbsent("400", k -> problem("Invalid parameter value, type or combination"));
        responses.computeIfAbsent("404", k -> problem("Unknown resource"));
        responses.computeIfAbsent("422", k -> problem("Well-formed but unprocessable request, e.g. an unknown filter id"));
        responses.computeIfAbsent("500", k -> problem("Unexpected error; quote errorId when reporting it"));
        operation.setResponses(responses);
    }

    private static ApiResponse problem(String description) {
        return new ApiResponse().description(description).content(new Content().addMediaType(PROBLEM_MEDIA_TYPE,
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM_SCHEMA))));
    }

    private static Schema<?> problemSchema() {
        Schema<?> fieldError = new ObjectSchema()
                .addProperty("parameter", new StringSchema().example("seasonFrom"))
                .addProperty("message", new StringSchema().example("must be greater than or equal to 2008"));
        return new ObjectSchema()
                .description("RFC 9457 problem details")
                .addProperty("type", new StringSchema().example("about:blank"))
                .addProperty("title", new StringSchema().example("Invalid request parameters"))
                .addProperty("status", new IntegerSchema().example(400))
                .addProperty("detail", new StringSchema().example("One or more request parameters are invalid"))
                .addProperty("instance", new StringSchema().example("/api/v1/leaderboards/batting"))
                .addProperty("errors", new ArraySchema().items(fieldError).description("400 only"))
                .addProperty("allowedValues", new ArraySchema().items(new StringSchema())
                        .description("400 for an unknown enumeration value: the accepted names"))
                .addProperty("errorId", new StringSchema().description("500 only: identifier of the logged error"));
    }
}
