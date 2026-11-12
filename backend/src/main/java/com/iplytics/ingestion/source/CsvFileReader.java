package com.iplytics.ingestion.source;

import com.iplytics.ingestion.InvalidFileException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Streams the records of a source CSV file: UTF-8 (a leading byte-order mark is skipped), RFC 4180.
 * Empty lines are returned as records rather than skipped, so validation can report them.
 * The first record returned is the header.
 */
public final class CsvFileReader implements AutoCloseable {

    private static final CSVFormat FORMAT = CSVFormat.RFC4180; // RFC4180 keeps empty lines
    private static final int BYTE_ORDER_MARK = '﻿';

    private final Path file;
    private final CSVParser parser;
    private final Iterator<CSVRecord> records;

    private CsvFileReader(Path file, CSVParser parser) {
        this.file = file;
        this.parser = parser;
        this.records = parser.iterator();
    }

    public static CsvFileReader open(Path file) throws IOException {
        BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8);
        try {
            reader.mark(1);
            if (reader.read() != BYTE_ORDER_MARK) {
                reader.reset();
            }
            return new CsvFileReader(file, FORMAT.parse(reader));
        } catch (IOException | RuntimeException e) {
            reader.close();
            throw translate(file, e, 1);
        }
    }

    /** @return the next record's values, or {@code null} at end of file */
    public List<String> next() {
        try {
            return records.hasNext() ? records.next().toList() : null;
        } catch (UncheckedIOException e) {
            throw translate(file, e.getCause(), parser.getCurrentLineNumber());
        }
    }

    @Override
    public void close() throws IOException {
        parser.close();
    }

    private static InvalidFileException translate(Path file, Throwable error, long line) {
        Throwable cause = error;
        while (cause.getCause() != null && !(cause instanceof CharacterCodingException)) {
            cause = cause.getCause();
        }
        if (cause instanceof CharacterCodingException) {
            return new InvalidFileException("%s is not valid UTF-8 (near line %d)".formatted(file.getFileName(), line));
        }
        return new InvalidFileException("Malformed CSV in %s near line %d: %s"
                .formatted(file.getFileName(), line, cause.getMessage()));
    }
}
