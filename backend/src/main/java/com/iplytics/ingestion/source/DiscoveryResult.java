package com.iplytics.ingestion.source;

import java.nio.file.Path;
import java.util.List;

/**
 * @param supported files to ingest, ordered by dataset (matches before deliveries)
 * @param ignored   CSV files whose name is not a supported dataset
 */
public record DiscoveryResult(List<DiscoveredFile> supported, List<Path> ignored) {
}
