package com.iplytics.ingestion;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Ingestion settings ({@code ingest.*}).
 *
 * @param dir             folder scanned for source CSV files, relative to the working directory
 * @param maxRejectedRows a file with more invalid rows than this fails as a whole (likely wrong format)
 * @param staleRunTimeout a RUNNING run older than this is treated as abandoned (e.g. the process was killed)
 */
@Validated
@ConfigurationProperties(prefix = "ingest")
public record IngestProperties(
        @NotNull @DefaultValue("data/raw") Path dir,
        @PositiveOrZero @DefaultValue("1000") int maxRejectedRows,
        @NotNull @DefaultValue("1h") Duration staleRunTimeout) {
}
