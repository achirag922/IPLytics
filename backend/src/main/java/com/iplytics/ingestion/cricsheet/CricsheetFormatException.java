package com.iplytics.ingestion.cricsheet;

import java.util.List;

/** A Cricsheet file that cannot be converted: not an IPL match, or missing match-level required fields. */
public class CricsheetFormatException extends RuntimeException {

    private final transient List<String> missingFields;

    public CricsheetFormatException(String message, List<String> missingFields) {
        super(missingFields.isEmpty() ? message : message + " " + missingFields);
        this.missingFields = List.copyOf(missingFields);
    }

    public List<String> missingFields() {
        return missingFields;
    }
}
