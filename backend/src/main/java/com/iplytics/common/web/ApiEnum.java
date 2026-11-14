package com.iplytics.common.web;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * An enum accepted as a query parameter by its documented API name (e.g. {@code strikeRate}). Unknown values are a
 * 400 listing the allowed names. Implementations annotate {@link #apiName()} with {@code @JsonValue} so the
 * OpenAPI document shows the API names.
 */
public interface ApiEnum {

    String apiName();

    static <E extends Enum<E> & ApiEnum> E parse(Class<E> type, String value) {
        String normalized = value == null ? "" : value.trim();
        return Arrays.stream(type.getEnumConstants())
                .filter(e -> e.apiName().equalsIgnoreCase(normalized)
                        || e.name().equalsIgnoreCase(normalized.replace('-', '_').toUpperCase(Locale.ROOT)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown value '" + value + "'"));
    }

    static List<String> allowed(Class<?> type) {
        Object[] constants = type.getEnumConstants();
        return constants == null ? List.of()
                : Arrays.stream(constants).map(c -> ((ApiEnum) c).apiName()).toList();
    }
}
