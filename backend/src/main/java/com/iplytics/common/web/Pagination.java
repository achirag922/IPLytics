package com.iplytics.common.web;

import io.swagger.v3.oas.annotations.media.Schema;

/** Zero-based page information of a collection response. */
public record Pagination(
        @Schema(example = "0") int page,
        @Schema(example = "20") int size,
        @Schema(example = "100") long totalElements,
        @Schema(example = "5") int totalPages) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public static Pagination of(int page, int size, long totalElements) {
        return new Pagination(page, size, totalElements, (int) ((totalElements + size - 1) / size));
    }

    static Pagination single(int elements) {
        return new Pagination(0, elements, elements, elements == 0 ? 0 : 1);
    }

    public static long offset(int page, int size) {
        return (long) page * size;
    }
}
