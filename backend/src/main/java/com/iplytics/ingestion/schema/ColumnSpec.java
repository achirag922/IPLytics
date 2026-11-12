package com.iplytics.ingestion.schema;

/**
 * One expected source column.
 *
 * @param headerRequired the file is rejected when the header lacks this column
 * @param valueRequired  a row is rejected when this column's value is missing (empty or {@code NA})
 */
public record ColumnSpec(String name, ValueType type, boolean headerRequired, boolean valueRequired) {

    /** Column must exist and every row must have a value. */
    public static ColumnSpec required(String name, ValueType type) {
        return new ColumnSpec(name, type, true, true);
    }

    /** Column must exist; values may be missing. */
    public static ColumnSpec nullable(String name, ValueType type) {
        return new ColumnSpec(name, type, true, false);
    }

    /** Column may be absent from the file; values may be missing. */
    public static ColumnSpec optional(String name, ValueType type) {
        return new ColumnSpec(name, type, false, false);
    }
}
