package com.iplytics.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.iplytics.ingestion.FileIngestionResult.Outcome;
import com.iplytics.ingestion.IngestionService;
import com.iplytics.ingestion.cricsheet.CricsheetConverter;
import com.iplytics.ingestion.source.DiscoveredFile;
import com.iplytics.ingestion.source.FileDiscovery;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Test helper: Cricsheet JSON fixtures -> converter -> staging ingestion, into a temp folder. */
public final class CricsheetPipeline {

    public static final List<String> ALL_FIXTURES =
            List.of("335982", "1000001", "1000002", "1000003", "1000004", "2000001");

    private final CricsheetConverter converter;
    private final IngestionService ingestion;
    private final Path workDir;
    private int loads;

    public CricsheetPipeline(CricsheetConverter converter, IngestionService ingestion, Path workDir) {
        this.converter = converter;
        this.ingestion = ingestion;
        this.workDir = workDir;
    }

    /** Converts and ingests the given match JSON documents (id -> JSON) as the latest staging batches. */
    public void load(Map<String, String> matches) throws IOException {
        int n = ++loads;
        Path source = Files.createDirectories(workDir.resolve("source" + n));
        for (Map.Entry<String, String> match : matches.entrySet()) {
            Files.writeString(source.resolve(match.getKey() + ".json"), match.getValue());
        }
        Path raw = workDir.resolve("raw" + n);
        assertThat(converter.convert(source, raw).hasProblems()).isFalse();
        for (DiscoveredFile file : new FileDiscovery().discover(raw).supported()) {
            assertThat(ingestion.ingest(file).outcome()).isIn(Outcome.LOADED, Outcome.SKIPPED);
        }
    }

    public static Map<String, String> fixtures(List<String> ids) throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        for (String id : ids) {
            try (InputStream in = CricsheetPipeline.class.getResourceAsStream("/cricsheet/" + id + ".json")) {
                files.put(id, new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return files;
    }
}
