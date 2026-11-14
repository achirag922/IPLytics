package com.iplytics.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iplytics.support.AbstractIntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Cross-cutting behaviour of the API: HTTP caching, CORS, actuator exposure and the OpenAPI contract. */
class ApiPlatformIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    @Test
    void unchangedDataIsAnsweredWithNotModified() throws Exception {
        MvcResult first = mvc.perform(get("/api/v1/teams"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=60, public"))
                .andReturn();
        String etag = first.getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(etag).matches("W/\"t\\d+-a\\d+\"");

        mvc.perform(get("/api/v1/seasons").header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified())
                .andExpect(content().string(""));
        mvc.perform(get("/api/v1/teams").header(HttpHeaders.IF_NONE_MATCH, "W/\"t0-a999\""))
                .andExpect(status().isOk());
    }

    @Test
    void errorsAreNotCached() throws Exception {
        mvc.perform(get("/api/v1/matches/999999"))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist(HttpHeaders.ETAG));
    }

    @Test
    void requestIdIsEchoedOrGenerated() throws Exception {
        mvc.perform(get("/api/v1/teams").header("X-Request-Id", "abc-123"))
                .andExpect(header().string("X-Request-Id", "abc-123"));
        mvc.perform(get("/api/v1/teams")).andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void corsAllowsTheFrontendOriginOnly() throws Exception {
        mvc.perform(options("/api/v1/teams")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
        mvc.perform(options("/api/v1/teams")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
        mvc.perform(options("/api/v1/teams")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "DELETE"))
                .andExpect(status().isForbidden());
    }

    @Test
    void actuatorExposesHealthInfoAndMetricsButNoConfiguration() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
        mvc.perform(get("/actuator/info")).andExpect(status().isOk()).andExpect(jsonPath("$.java").exists());
        mvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names", hasItems("jvm.memory.used", "hikaricp.connections.active")));
        for (String hidden : new String[] {"env", "configprops", "beans", "heapdump", "loggers", "mappings"}) {
            mvc.perform(get("/actuator/" + hidden)).andExpect(status().isNotFound());
        }
    }

    /**
     * The published document describes exactly the /api endpoints the application serves, with the shared error
     * responses, so clients generated from it (target/openapi.json) match the server.
     */
    @Test
    void openApiDocumentMatchesTheServedEndpoints() throws Exception {
        String body = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Path target = Path.of("target", "openapi.json");
        Files.createDirectories(target.getParent());
        Files.writeString(target, body);
        JsonNode doc = json.readTree(body);

        Set<String> documented = new TreeSet<>();
        doc.get("paths").fieldNames().forEachRemaining(documented::add);
        Set<String> served = new TreeSet<>();
        mappings.getHandlerMethods().keySet().forEach(info -> info.getPatternValues().stream()
                .filter(p -> p.startsWith("/api/")).forEach(served::add));
        assertThat(documented).isEqualTo(served);

        doc.get("paths").properties().forEach(path -> {
            if (!path.getKey().startsWith("/api/v1/system")) {
                JsonNode responses = path.getValue().get("get").get("responses");
                assertThat(responses.has("200") && responses.has("304") && responses.has("400")
                        && responses.has("404") && responses.has("422") && responses.has("500"))
                        .as("standard responses of %s", path.getKey()).isTrue();
            }
        });
        assertThat(doc.at("/components/schemas/ApiProblem/properties").has("allowedValues")).isTrue();
        JsonNode metric = parameter(doc, "/api/v1/leaderboards/batting", "metric");
        assertThat(metric.at("/schema/enum").toString())
                .contains("runs", "average", "strikeRate", "fours", "sixes", "hundreds", "fifties");
        assertThat(parameter(doc, "/api/v1/players", "size").at("/schema/maximum").asInt()).isEqualTo(100);
        assertThat(doc.at("/info/description").asText()).contains("Envelope", "Errors", "Caching", "Metrics");
    }

    private static JsonNode parameter(JsonNode doc, String path, String name) {
        for (JsonNode p : doc.get("paths").get(path).get("get").get("parameters")) {
            if (name.equals(p.get("name").asText())) {
                return p;
            }
        }
        throw new AssertionError("No parameter " + name + " on " + path);
    }
}
