package com.iplytics.ingestion.cricsheet;

import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Converter settings ({@code convert.*}).
 *
 * @param source    the Cricsheet IPL JSON zip, or a folder it was extracted to
 * @param outputDir where matches.csv, deliveries.csv, match_players.csv and the report are written
 */
@Validated
@ConfigurationProperties(prefix = "convert")
public record ConvertProperties(
        @NotNull @DefaultValue("data/cricsheet/ipl_json.zip") Path source,
        @NotNull @DefaultValue("data/raw") Path outputDir) {
}
