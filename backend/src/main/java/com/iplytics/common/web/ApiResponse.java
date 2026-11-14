package com.iplytics.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/**
 * Response envelope of every /api/v1 endpoint (docs/api.md): {@code {data, meta}} for a single resource and
 * {@code {data, pagination, meta}} for a collection.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        @Schema(description = "The resource or the collection of resources") T data,
        @Schema(description = "Present on collections") Pagination pagination,
        @Schema(description = "generatedAt, dataVersion, applied filters and endpoint-specific context")
        Map<String, Object> meta) {

    public static <T> ApiResponse<T> of(T data, Map<String, Object> meta) {
        return new ApiResponse<>(data, null, meta);
    }

    public static <T> ApiResponse<List<T>> page(List<T> data, Pagination pagination, Map<String, Object> meta) {
        return new ApiResponse<>(data, pagination, meta);
    }

    /** An unpaginated collection is returned as one page holding every element. */
    public static <T> ApiResponse<List<T>> list(List<T> data, Map<String, Object> meta) {
        return new ApiResponse<>(data, Pagination.single(data.size()), meta);
    }
}
