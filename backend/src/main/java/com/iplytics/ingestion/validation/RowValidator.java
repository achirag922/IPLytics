package com.iplytics.ingestion.validation;

import com.iplytics.ingestion.schema.ColumnSpec;
import com.iplytics.ingestion.schema.Dataset;
import com.iplytics.ingestion.schema.RejectionReason;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

/**
 * Validates source headers and rows against a {@link Dataset} definition. Validation never changes values:
 * it only decides whether a row may enter staging. Rules are listed in docs/data-source.md.
 */
@Component
public class RowValidator {

    /** Token the source uses for "no value". Stored verbatim, but counts as missing. */
    public static final String MISSING_TOKEN = "NA";

    private static final int MAX_VALUE_IN_MESSAGE = 60;

    public HeaderMapping mapHeader(Dataset dataset, List<String> rawHeader) {
        List<String> names = IntStream.range(0, rawHeader.size())
                .mapToObj(i -> normalizeHeader(rawHeader.get(i), i))
                .toList();

        Map<String, Integer> firstIndex = new HashMap<>();
        Set<String> duplicates = new LinkedHashSet<>();
        for (int i = 0; i < names.size(); i++) {
            if (firstIndex.putIfAbsent(names.get(i), i) != null) {
                duplicates.add(names.get(i));
            }
        }

        List<ColumnSpec> columns = dataset.columns();
        int[] fileIndexByColumn = new int[columns.size()];
        Set<Integer> mapped = new HashSet<>();
        List<String> missingRequired = new ArrayList<>();
        for (int c = 0; c < columns.size(); c++) {
            Integer index = firstIndex.get(columns.get(c).name());
            fileIndexByColumn[c] = index == null ? -1 : index;
            if (index != null) {
                mapped.add(index);
            } else if (columns.get(c).headerRequired()) {
                missingRequired.add(columns.get(c).name());
            }
        }
        List<Integer> extras = IntStream.range(0, names.size()).filter(i -> !mapped.contains(i)).boxed().toList();

        return new HeaderMapping(dataset, names, fileIndexByColumn, extras,
                List.copyOf(missingRequired), List.copyOf(duplicates));
    }

    /** @return every violation in the row; empty when the row is valid */
    public List<Violation> validateRow(HeaderMapping mapping, List<String> values) {
        if (values.stream().allMatch(RowValidator::isBlank)) {
            return List.of(new Violation(RejectionReason.EMPTY_ROW, null, "Row is empty"));
        }
        if (values.size() != mapping.width()) {
            return List.of(new Violation(RejectionReason.COLUMN_COUNT_MISMATCH, null,
                    "Expected %d values but found %d".formatted(mapping.width(), values.size())));
        }

        List<Violation> violations = new ArrayList<>();
        List<ColumnSpec> columns = mapping.dataset().columns();
        for (int c = 0; c < columns.size(); c++) {
            int index = mapping.fileIndexOf(c);
            if (index < 0) {
                continue; // optional column absent from this file
            }
            ColumnSpec column = columns.get(c);
            String value = values.get(index);
            if (isMissing(value)) {
                if (column.valueRequired()) {
                    violations.add(new Violation(RejectionReason.MISSING_VALUE, column.name(),
                            "'%s' is required".formatted(column.name())));
                }
            } else if (!column.type().accepts(value.strip())) {
                violations.add(new Violation(column.type().rejectionReason(), column.name(),
                        "'%s' must be %s but was '%s'".formatted(column.name(), column.type().description(),
                                abbreviate(value))));
            }
        }
        return violations;
    }

    /** Empty, whitespace-only or the {@link #MISSING_TOKEN}. */
    public static boolean isMissing(String value) {
        return isBlank(value) || value.strip().equalsIgnoreCase(MISSING_TOKEN);
    }

    static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    static String normalizeHeader(String raw, int index) {
        String name = raw == null ? "" : raw.replace("﻿", "").strip().toLowerCase(Locale.ROOT);
        return name.isEmpty() ? "_column_" + (index + 1) : name;
    }

    private static String abbreviate(String value) {
        return value.length() <= MAX_VALUE_IN_MESSAGE ? value : value.substring(0, MAX_VALUE_IN_MESSAGE) + "...";
    }
}
