package com.iplytics.ingestion.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iplytics.ingestion.schema.Dataset;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileDiscoveryTest {

    private final FileDiscovery discovery = new FileDiscovery();

    @TempDir
    Path dir;

    @Test
    void findsSupportedFilesCaseInsensitivelyInDatasetOrder() throws IOException {
        Files.writeString(dir.resolve("DELIVERIES.csv"), "x");
        Files.writeString(dir.resolve("Matches.CSV"), "x");

        DiscoveryResult result = discovery.discover(dir);

        assertThat(result.supported()).extracting(DiscoveredFile::dataset)
                .containsExactly(Dataset.MATCHES, Dataset.DELIVERIES);
        assertThat(result.supported()).extracting(DiscoveredFile::fileName)
                .containsExactly("Matches.CSV", "DELIVERIES.csv");
        assertThat(result.ignored()).isEmpty();
    }

    @Test
    void reportsUnsupportedCsvFilesAndSkipsEverythingElse() throws IOException {
        Files.writeString(dir.resolve("matches.csv"), "x");
        Files.writeString(dir.resolve("players.csv"), "x");
        Files.writeString(dir.resolve(".gitkeep"), "");
        Files.writeString(dir.resolve("notes.txt"), "x");
        Files.createDirectories(dir.resolve("archive"));
        Files.writeString(dir.resolve("archive").resolve("deliveries.csv"), "x"); // not recursive

        DiscoveryResult result = discovery.discover(dir);

        assertThat(result.supported()).extracting(DiscoveredFile::fileName).containsExactly("matches.csv");
        assertThat(result.ignored()).extracting(p -> p.getFileName().toString()).containsExactly("players.csv");
    }

    @Test
    void failsWhenTheDirectoryDoesNotExist() {
        assertThatThrownBy(() -> discovery.discover(dir.resolve("missing")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist");
    }
}
