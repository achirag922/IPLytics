package com.iplytics.ingestion.staging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iplytics.ingestion.IngestionException;
import com.iplytics.ingestion.InvalidFileException;
import com.iplytics.ingestion.TooManyRejectedRowsException;
import com.iplytics.ingestion.schema.ColumnSpec;
import com.iplytics.ingestion.schema.Dataset;
import com.iplytics.ingestion.source.CsvFileReader;
import com.iplytics.ingestion.validation.HeaderMapping;
import com.iplytics.ingestion.validation.RowValidator;
import com.iplytics.ingestion.validation.Violation;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.QuoteMode;
import org.postgresql.PGConnection;
import org.postgresql.copy.PGCopyOutputStream;
import org.springframework.stereotype.Component;

/**
 * Streams a source file into its staging table with PostgreSQL {@code COPY}: each row is validated in
 * Java, valid rows are written to the COPY stream, invalid rows are collected for {@code staging.rejected_row}.
 * Memory use is constant apart from the rejected rows, which are capped by {@code maxRejectedRows}.
 *
 * <p>Runs on the caller's connection and transaction; the caller commits or rolls back.
 */
@Component
public class StagingLoader {

    /** Non-null values are always quoted and nulls are bare, so COPY tells NULL apart from empty text. */
    private static final CSVFormat COPY_FORMAT = CSVFormat.DEFAULT.builder()
            .setQuoteMode(QuoteMode.ALL_NON_NULL)
            .setRecordSeparator("\n")
            .get();
    private static final int COPY_BUFFER_BYTES = 1 << 16;

    private final RowValidator validator;
    private final ObjectMapper objectMapper;

    public StagingLoader(RowValidator validator, ObjectMapper objectMapper) {
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    public StagingLoad load(Connection connection, Dataset dataset, long runId, Path file, int maxRejectedRows) {
        try (CsvFileReader csv = CsvFileReader.open(file)) {
            List<String> header = csv.next();
            if (header == null) {
                throw new InvalidFileException(file.getFileName() + " is empty: no header row");
            }
            HeaderMapping mapping = validator.mapHeader(dataset, header);
            if (!mapping.isValid()) {
                throw new InvalidFileException(mapping.problem());
            }
            return copyRows(connection, csv, mapping, runId, maxRejectedRows);
        } catch (IOException e) {
            throw new IngestionException("Cannot load " + file.getFileName() + ": " + e.getMessage(), e);
        }
    }

    private StagingLoad copyRows(Connection connection, CsvFileReader csv, HeaderMapping mapping, long runId,
                                 int maxRejectedRows) throws IOException {
        Dataset dataset = mapping.dataset();
        PGCopyOutputStream copy = openCopy(connection, dataset);
        List<RejectedRow> rejected = new ArrayList<>();
        int rowsRead = 0;
        try {
            CSVPrinter printer = COPY_FORMAT.print(new OutputStreamWriter(copy, StandardCharsets.UTF_8));
            List<String> values;
            while ((values = csv.next()) != null) {
                rowsRead++;
                List<Violation> violations = validator.validateRow(mapping, values);
                if (violations.isEmpty()) {
                    printer.printRecord(stagingRecord(mapping, runId, rowsRead, values));
                } else {
                    rejected.add(RejectedRow.of(rowsRead, violations, rawDataJson(mapping, values)));
                    if (rejected.size() > maxRejectedRows) {
                        throw new TooManyRejectedRowsException(maxRejectedRows, rowsRead, rejected);
                    }
                }
            }
            printer.flush();
            long copied = copy.endCopy();
            int valid = rowsRead - rejected.size();
            if (copied != valid) {
                throw new IngestionException("COPY reported %d rows but %d valid rows were sent".formatted(copied, valid));
            }
            return new StagingLoad(rowsRead, valid, List.copyOf(rejected));
        } catch (SQLException e) {
            throw new IngestionException("COPY into %s failed at row %d: %s"
                    .formatted(dataset.stagingTable(), rowsRead, e.getMessage()), e);
        } finally {
            cancelIfActive(copy);
        }
    }

    private static PGCopyOutputStream openCopy(Connection connection, Dataset dataset) {
        try {
            return new PGCopyOutputStream(connection.unwrap(PGConnection.class), copySql(dataset), COPY_BUFFER_BYTES);
        } catch (SQLException e) {
            throw new IngestionException("Cannot start COPY into " + dataset.stagingTable() + ": " + e.getMessage(), e);
        }
    }

    static String copySql(Dataset dataset) {
        String columns = Stream.of(
                        Stream.of("ingestion_run_id", "source_row_number"),
                        dataset.columns().stream().map(ColumnSpec::name),
                        Stream.of("extra_data"))
                .flatMap(s -> s)
                .map(name -> '"' + name + '"')
                .collect(Collectors.joining(", "));
        return "COPY " + dataset.stagingTable() + " (" + columns + ") FROM STDIN WITH (FORMAT csv)";
    }

    private List<Object> stagingRecord(HeaderMapping mapping, long runId, int rowNumber, List<String> values)
            throws JsonProcessingException {
        List<ColumnSpec> columns = mapping.dataset().columns();
        List<Object> record = new ArrayList<>(columns.size() + 3);
        record.add(runId);
        record.add(rowNumber);
        for (int c = 0; c < columns.size(); c++) {
            int index = mapping.fileIndexOf(c);
            record.add(index < 0 ? null : blankToNull(values.get(index)));
        }
        record.add(extraDataJson(mapping, values));
        return record;
    }

    /** Source columns outside the dataset definition, so nothing in the file is dropped. */
    private String extraDataJson(HeaderMapping mapping, List<String> values) throws JsonProcessingException {
        Map<String, String> extra = new LinkedHashMap<>();
        for (int index : mapping.extraIndexes()) {
            String value = blankToNull(values.get(index));
            if (value != null) {
                extra.put(mapping.columnNames().get(index), value);
            }
        }
        return extra.isEmpty() ? null : objectMapper.writeValueAsString(extra);
    }

    /** The row exactly as read, keyed by header name; surplus values go under {@code _unmapped}. */
    private String rawDataJson(HeaderMapping mapping, List<String> values) throws JsonProcessingException {
        Map<String, Object> raw = new LinkedHashMap<>();
        int mapped = Math.min(mapping.width(), values.size());
        for (int i = 0; i < mapped; i++) {
            raw.put(mapping.columnNames().get(i), values.get(i));
        }
        if (values.size() > mapping.width()) {
            raw.put("_unmapped", values.subList(mapping.width(), values.size()));
        }
        return objectMapper.writeValueAsString(raw);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static void cancelIfActive(PGCopyOutputStream copy) {
        if (copy.isActive()) {
            try {
                copy.cancelCopy();
            } catch (SQLException ignored) {
                // the transaction is rolled back by the caller either way
            }
        }
    }
}
