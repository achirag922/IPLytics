package com.iplytics.ingestion.schema;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** The format a non-missing source value must have. Values stay text in staging; this only validates. */
public enum ValueType {
    TEXT(null, "any text", value -> true),
    INTEGER(RejectionReason.INVALID_INTEGER, "a non-negative whole number", matching("\\d{1,9}")),
    DECIMAL(RejectionReason.INVALID_DECIMAL, "a non-negative number", matching("\\d{1,9}(\\.\\d+)?")),
    DATE(RejectionReason.INVALID_DATE, "a date in yyyy-MM-dd format", ValueType::isIsoDate),
    SEASON(RejectionReason.INVALID_VALUE, "a season such as 2008 or 2007/08", matching("\\d{4}(/\\d{2})?")),
    FLAG(RejectionReason.INVALID_VALUE, "0 or 1", matching("[01]")),
    YES_NO(RejectionReason.INVALID_VALUE, "Y or N", matching("(?i)[YN]")),
    TOSS_DECISION(RejectionReason.INVALID_VALUE, "bat or field", matching("(?i)bat|field")),
    JSON(RejectionReason.INVALID_VALUE, "valid JSON", ValueType::isJson);

    private static final DateTimeFormatter ISO_DATE =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    private final RejectionReason rejectionReason;
    private final String description;
    private final Predicate<String> validator;

    ValueType(RejectionReason rejectionReason, String description, Predicate<String> validator) {
        this.rejectionReason = rejectionReason;
        this.description = description;
        this.validator = validator;
    }

    /** @param value a present (non-missing), trimmed value */
    public boolean accepts(String value) {
        return validator.test(value);
    }

    public RejectionReason rejectionReason() {
        return rejectionReason;
    }

    public String description() {
        return description;
    }

    private static Predicate<String> matching(String regex) {
        Pattern pattern = Pattern.compile(regex);
        return value -> pattern.matcher(value).matches();
    }

    private static boolean isJson(String value) {
        try (JsonParser parser = JsonFactoryHolder.FACTORY.createParser(value)) {
            while (parser.nextToken() != null) {
                // consume every token; a syntax error throws
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static final class JsonFactoryHolder {
        static final JsonFactory FACTORY = new JsonFactory();
    }

    private static boolean isIsoDate(String value) {
        try {
            LocalDate.parse(value, ISO_DATE);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
