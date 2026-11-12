package com.iplytics.ingestion.source;

import com.iplytics.ingestion.schema.Dataset;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/** Finds supported source files directly inside the ingestion folder (not recursive). */
@Component
public class FileDiscovery {

    public DiscoveryResult discover(Path directory) {
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Ingestion directory does not exist: " + directory);
        }
        List<DiscoveredFile> supported = new ArrayList<>();
        List<Path> ignored = new ArrayList<>();
        try (Stream<Path> entries = Files.list(directory)) {
            entries.filter(Files::isRegularFile).sorted().forEach(file -> {
                String name = file.getFileName().toString();
                Optional<Dataset> dataset = Dataset.forFileName(name);
                if (dataset.isPresent()) {
                    supported.add(new DiscoveredFile(file, dataset.get()));
                } else if (name.toLowerCase(Locale.ROOT).endsWith(".csv")) {
                    ignored.add(file);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list " + directory, e);
        }
        supported.sort(Comparator.comparing(DiscoveredFile::dataset));
        return new DiscoveryResult(List.copyOf(supported), List.copyOf(ignored));
    }
}
