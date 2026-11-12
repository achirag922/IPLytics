package com.iplytics.ingestion.source;

import com.iplytics.ingestion.schema.Dataset;
import java.nio.file.Path;

public record DiscoveredFile(Path path, Dataset dataset) {

    public String fileName() {
        return path.getFileName().toString();
    }
}
