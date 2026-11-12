package com.iplytics.ingestion.cricsheet;

import com.iplytics.ingestion.schema.Dataset;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

/**
 * Writes rows of one dataset as RFC 4180 CSV (UTF-8, no BOM, {@code \n} line endings) with the header and
 * column order taken from {@link Dataset}, so converter output always matches what staging expects.
 */
final class CsvRowWriter implements Closeable {

    private static final CSVFormat FORMAT = CSVFormat.RFC4180.builder().setRecordSeparator("\n").get();

    private final Dataset dataset;
    private final List<String> columns;
    private final Set<String> known;
    private final CSVPrinter printer;
    private long rows;

    CsvRowWriter(Dataset dataset, Path file) throws IOException {
        this.dataset = dataset;
        this.columns = dataset.columnNames();
        this.known = new HashSet<>(columns);
        this.printer = FORMAT.print(Files.newBufferedWriter(file, StandardCharsets.UTF_8));
        printer.printRecord(columns);
    }

    void write(Map<String, String> row) throws IOException {
        if (!known.containsAll(row.keySet())) {
            Set<String> unknown = new HashSet<>(row.keySet());
            unknown.removeAll(known);
            throw new IllegalStateException("Columns " + unknown + " are not defined for " + dataset);
        }
        printer.printRecord(columns.stream().map(row::get).toList());
        rows++;
    }

    long rows() {
        return rows;
    }

    @Override
    public void close() throws IOException {
        printer.close();
    }
}
