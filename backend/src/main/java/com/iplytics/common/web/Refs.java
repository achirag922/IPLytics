package com.iplytics.common.web;

import io.swagger.v3.oas.annotations.media.Schema;

/** Compact references to other resources, embedded in responses. */
public final class Refs {

    private Refs() {
    }

    @Schema(description = "A franchise (renamed teams share one franchise)")
    public record TeamRef(@Schema(example = "6") int id, @Schema(example = "MI") String code,
                          @Schema(example = "Mumbai Indians") String name) {
    }

    public record PlayerRef(@Schema(example = "412") int id, @Schema(example = "V Kohli") String name) {
    }

    public record VenueRef(@Schema(example = "30") int id, @Schema(example = "Wankhede Stadium") String name,
                           @Schema(example = "Mumbai") String city) {
    }
}
