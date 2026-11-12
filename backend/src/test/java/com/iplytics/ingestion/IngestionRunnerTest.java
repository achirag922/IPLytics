package com.iplytics.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.iplytics.ingestion.schema.Dataset;
import com.iplytics.ingestion.source.DiscoveredFile;
import com.iplytics.ingestion.source.FileDiscovery;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class IngestionRunnerTest {

    @TempDir
    Path dir;

    @Mock
    private IngestionService ingestionService;

    @Test
    void ingestsEverySupportedFileInOrderAndExitsZero() throws IOException {
        Files.writeString(dir.resolve("deliveries.csv"), "x");
        Files.writeString(dir.resolve("matches.csv"), "x");
        Files.writeString(dir.resolve("players.csv"), "x");
        when(ingestionService.ingest(any(DiscoveredFile.class))).thenAnswer(call -> {
            DiscoveredFile file = call.getArgument(0);
            return FileIngestionResult.loaded(file.fileName(), file.dataset(), 1L, 2, 2, 0, 5);
        });

        IngestionRunner runner = runner(dir);
        runner.run(new DefaultApplicationArguments());

        ArgumentCaptor<DiscoveredFile> files = ArgumentCaptor.forClass(DiscoveredFile.class);
        verify(ingestionService, times(2)).ingest(files.capture());
        assertThat(files.getAllValues()).extracting(DiscoveredFile::dataset)
                .containsExactly(Dataset.MATCHES, Dataset.DELIVERIES);
        verify(ingestionService).recoverStaleRuns();
        assertThat(runner.getExitCode()).isZero();
    }

    @Test
    void skippedFilesStillExitZero() throws IOException {
        Files.writeString(dir.resolve("matches.csv"), "x");
        when(ingestionService.ingest(any(DiscoveredFile.class)))
                .thenReturn(FileIngestionResult.skipped("matches.csv", Dataset.MATCHES, 1L, "already loaded", 1));

        IngestionRunner runner = runner(dir);
        runner.run(new DefaultApplicationArguments());

        assertThat(runner.getExitCode()).isZero();
    }

    @Test
    void exitsNonZeroWhenAnyFileFails() throws IOException {
        Files.writeString(dir.resolve("matches.csv"), "x");
        Files.writeString(dir.resolve("deliveries.csv"), "x");
        when(ingestionService.ingest(any(DiscoveredFile.class)))
                .thenReturn(FileIngestionResult.loaded("matches.csv", Dataset.MATCHES, 1L, 1, 1, 0, 1))
                .thenReturn(FileIngestionResult.failed("deliveries.csv", Dataset.DELIVERIES, 2L, 0, 0, "bad", 1));

        IngestionRunner runner = runner(dir);
        runner.run(new DefaultApplicationArguments());

        assertThat(runner.getExitCode()).isEqualTo(1);
    }

    @Test
    void exitsNonZeroWhenNoSupportedFilesExist() throws IOException {
        Files.writeString(dir.resolve("players.csv"), "x");

        IngestionRunner runner = runner(dir);
        runner.run(new DefaultApplicationArguments());

        assertThat(runner.getExitCode()).isEqualTo(1);
        verifyNoInteractions(ingestionService);
    }

    @Test
    void exitsNonZeroWhenTheDirectoryIsMissing() {
        IngestionRunner runner = runner(dir.resolve("missing"));
        runner.run(new DefaultApplicationArguments());

        assertThat(runner.getExitCode()).isEqualTo(1);
        verifyNoInteractions(ingestionService);
    }

    private IngestionRunner runner(Path directory) {
        return new IngestionRunner(new FileDiscovery(), ingestionService,
                new IngestProperties(directory, 1000, Duration.ofHours(1)));
    }
}
