package com.iplytics.ingestion.cricsheet;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The entries of a Cricsheet download: the official zip (e.g. {@code ipl_json.zip}) or a folder it was
 * extracted to. Entries are returned sorted by numeric match id, so output is identical for identical input.
 */
final class CricsheetSource implements Closeable {

    /** @param matchId the file name without {@code .json} when it is numeric, otherwise null */
    record Entry(String name, String matchId, boolean json) {

        static Entry of(String name) {
            String fileName = name.substring(name.lastIndexOf('/') + 1);
            boolean json = fileName.toLowerCase(Locale.ROOT).endsWith(".json");
            String stem = json ? fileName.substring(0, fileName.length() - 5) : null;
            return new Entry(name, stem != null && stem.matches("\\d{1,9}") ? stem : null, json);
        }

        private long sortKey() {
            return matchId == null ? Long.MAX_VALUE : Long.parseLong(matchId);
        }
    }

    private static final Comparator<Entry> ORDER =
            Comparator.comparingLong(Entry::sortKey).thenComparing(Entry::name);

    private final Path path;
    private final ZipFile zip;
    private final List<Entry> entries;

    private CricsheetSource(Path path, ZipFile zip, List<Entry> entries) {
        this.path = path;
        this.zip = zip;
        this.entries = entries;
    }

    static CricsheetSource open(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (Stream<Path> files = Files.walk(path)) {
                List<Entry> entries = files.filter(Files::isRegularFile)
                        .map(file -> Entry.of(path.relativize(file).toString().replace('\\', '/')))
                        .sorted(ORDER).toList();
                return new CricsheetSource(path, null, entries);
            }
        }
        ZipFile zip = new ZipFile(path.toFile());
        List<Entry> entries = zip.stream().filter(e -> !e.isDirectory())
                .map(e -> Entry.of(e.getName())).sorted(ORDER).toList();
        return new CricsheetSource(path, zip, entries);
    }

    List<Entry> entries() {
        return entries;
    }

    InputStream open(Entry entry) throws IOException {
        if (zip == null) {
            return Files.newInputStream(path.resolve(entry.name()));
        }
        ZipEntry zipEntry = zip.getEntry(entry.name());
        return zip.getInputStream(zipEntry);
    }

    @Override
    public void close() throws IOException {
        if (zip != null) {
            zip.close();
        }
    }
}
