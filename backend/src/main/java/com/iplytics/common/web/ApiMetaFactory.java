package com.iplytics.common.web;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Builds the {@code meta} block: generatedAt, dataVersion, the applied filters and any endpoint context. */
@Component
public class ApiMetaFactory {

    private final DataVersionService versions;

    public ApiMetaFactory(DataVersionService versions) {
        this.versions = versions;
    }

    public Map<String, Object> meta() {
        return meta(Map.of());
    }

    public Map<String, Object> meta(Map<String, Object> filters) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("generatedAt", OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
        DataVersionService.DataVersion version = versions.current();
        meta.put("dataVersion", version == null ? null : version.tag());
        if (!filters.isEmpty()) {
            meta.put("filters", filters);
        }
        return meta;
    }

    /** {@link #meta(Map)} plus extra endpoint-specific entries (e.g. metric, minimumBalls). */
    public Map<String, Object> meta(Map<String, Object> filters, Map<String, Object> context) {
        Map<String, Object> meta = meta(filters);
        meta.putAll(context);
        return meta;
    }
}
