package com.iplytics.ingestion.validation;

import com.iplytics.ingestion.schema.Dataset;
import java.util.ArrayList;
import java.util.List;

/** How a file's header lines up with a {@link Dataset} definition. Built by {@link RowValidator#mapHeader}. */
public final class HeaderMapping {

    private final Dataset dataset;
    private final List<String> columnNames;
    private final int[] fileIndexByColumn;
    private final List<Integer> extraIndexes;
    private final List<String> missingRequiredColumns;
    private final List<String> duplicateColumns;

    HeaderMapping(Dataset dataset, List<String> columnNames, int[] fileIndexByColumn, List<Integer> extraIndexes,
                  List<String> missingRequiredColumns, List<String> duplicateColumns) {
        this.dataset = dataset;
        this.columnNames = columnNames;
        this.fileIndexByColumn = fileIndexByColumn;
        this.extraIndexes = extraIndexes;
        this.missingRequiredColumns = missingRequiredColumns;
        this.duplicateColumns = duplicateColumns;
    }

    public Dataset dataset() {
        return dataset;
    }

    /** Normalized header names, in file order. */
    public List<String> columnNames() {
        return columnNames;
    }

    /** Number of columns in the file. */
    public int width() {
        return columnNames.size();
    }

    /** @return the file column index of {@code dataset().columns().get(position)}, or -1 if the file lacks it */
    public int fileIndexOf(int position) {
        return fileIndexByColumn[position];
    }

    /** File column indexes that are not part of the dataset definition. */
    public List<Integer> extraIndexes() {
        return extraIndexes;
    }

    public List<String> extraColumns() {
        return extraIndexes.stream().map(columnNames::get).toList();
    }

    public List<String> missingRequiredColumns() {
        return missingRequiredColumns;
    }

    public List<String> duplicateColumns() {
        return duplicateColumns;
    }

    public boolean isValid() {
        return missingRequiredColumns.isEmpty() && duplicateColumns.isEmpty();
    }

    /** @return a description of every header problem, or an empty string if valid */
    public String problem() {
        List<String> problems = new ArrayList<>();
        if (!missingRequiredColumns.isEmpty()) {
            problems.add("Missing required columns " + missingRequiredColumns);
        }
        if (!duplicateColumns.isEmpty()) {
            problems.add("Duplicate columns " + duplicateColumns);
        }
        if (!problems.isEmpty()) {
            problems.add("found header " + columnNames + " (see docs/data-source.md)");
        }
        return String.join("; ", problems);
    }
}
