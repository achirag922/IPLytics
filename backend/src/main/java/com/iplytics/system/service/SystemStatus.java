package com.iplytics.system.service;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Application build and database schema status")
public record SystemStatus(
        @Schema(example = "iplytics") String application,
        @Schema(example = "0.0.1-SNAPSHOT") String version,
        @Schema(description = "Latest applied Flyway migration version", example = "1") String schemaVersion) {
}
