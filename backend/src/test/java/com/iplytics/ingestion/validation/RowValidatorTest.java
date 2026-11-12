package com.iplytics.ingestion.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.iplytics.ingestion.schema.Dataset;
import com.iplytics.ingestion.schema.RejectionReason;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RowValidatorTest {

    private static final List<String> MATCHES_HEADER = List.of("id", "season", "city", "date", "match_type",
            "player_of_match", "venue", "team1", "team2", "toss_winner", "toss_decision", "winner", "result",
            "result_margin", "target_runs", "target_overs", "super_over", "method", "umpire1", "umpire2");

    private static final List<String> VALID_MATCH = List.of("335982", "2007/08", "Bangalore", "2008-04-18",
            "League", "BB McCullum", "M Chinnaswamy Stadium", "Royal Challengers Bangalore", "Kolkata Knight Riders",
            "Royal Challengers Bangalore", "field", "Kolkata Knight Riders", "runs", "140.0", "223.0", "20.0", "N",
            "NA", "Asad Rauf", "RE Koertzen");

    private final RowValidator validator = new RowValidator();
    private final HeaderMapping matches = validator.mapHeader(Dataset.MATCHES, MATCHES_HEADER);

    // --- header -------------------------------------------------------------------------------------------

    @Test
    void mapsHeaderIgnoringCaseWhitespaceAndByteOrderMark() {
        List<String> header = new ArrayList<>(MATCHES_HEADER);
        header.set(0, "﻿ID");
        header.set(1, "  Season ");

        HeaderMapping mapping = validator.mapHeader(Dataset.MATCHES, header);

        assertThat(mapping.isValid()).isTrue();
        assertThat(mapping.columnNames()).startsWith("id", "season");
        assertThat(mapping.fileIndexOf(0)).isZero();
    }

    @Test
    void reportsMissingRequiredColumns() {
        List<String> header = new ArrayList<>(MATCHES_HEADER);
        header.removeAll(List.of("date", "venue"));

        HeaderMapping mapping = validator.mapHeader(Dataset.MATCHES, header);

        assertThat(mapping.isValid()).isFalse();
        assertThat(mapping.missingRequiredColumns()).containsExactly("date", "venue");
        assertThat(mapping.problem()).contains("Missing required columns [date, venue]");
    }

    @Test
    void allowsOptionalColumnsToBeAbsent() {
        List<String> header = new ArrayList<>(MATCHES_HEADER);
        header.removeAll(List.of("city", "method", "umpire1", "umpire2"));

        HeaderMapping mapping = validator.mapHeader(Dataset.MATCHES, header);

        assertThat(mapping.isValid()).isTrue();
        assertThat(mapping.fileIndexOf(Dataset.MATCHES.columns().indexOf(
                Dataset.MATCHES.columns().stream().filter(c -> c.name().equals("city")).findFirst().orElseThrow())))
                .isEqualTo(-1);
    }

    @Test
    void reportsDuplicateColumns() {
        List<String> header = new ArrayList<>(MATCHES_HEADER);
        header.add("Venue");

        HeaderMapping mapping = validator.mapHeader(Dataset.MATCHES, header);

        assertThat(mapping.isValid()).isFalse();
        assertThat(mapping.duplicateColumns()).containsExactly("venue");
    }

    @Test
    void keepsUnknownColumnsAsExtras() {
        List<String> header = new ArrayList<>(MATCHES_HEADER);
        header.add("neutral_venue");
        header.add("");

        HeaderMapping mapping = validator.mapHeader(Dataset.MATCHES, header);

        assertThat(mapping.isValid()).isTrue();
        assertThat(mapping.extraColumns()).containsExactly("neutral_venue", "_column_22");
    }

    // --- rows ---------------------------------------------------------------------------------------------

    @Test
    void acceptsAValidRow() {
        assertThat(validator.validateRow(matches, VALID_MATCH)).isEmpty();
    }

    @Test
    void acceptsMissingValuesInNullableAndOptionalColumns() {
        List<String> row = with(with(with(VALID_MATCH, "winner", "NA"), "result_margin", ""), "city", " ");

        assertThat(validator.validateRow(matches, row)).isEmpty();
    }

    @Test
    void rejectsEmptyRows() {
        assertThat(validator.validateRow(matches, List.of(""))).extracting(Violation::reason)
                .containsExactly(RejectionReason.EMPTY_ROW);
        assertThat(validator.validateRow(matches, MATCHES_HEADER.stream().map(c -> " ").toList()))
                .extracting(Violation::reason).containsExactly(RejectionReason.EMPTY_ROW);
    }

    @Test
    void rejectsRowsWithTheWrongNumberOfValues() {
        List<Violation> violations = validator.validateRow(matches, VALID_MATCH.subList(0, 5));

        assertThat(violations).extracting(Violation::reason).containsExactly(RejectionReason.COLUMN_COUNT_MISMATCH);
        assertThat(violations.get(0).message()).isEqualTo("Expected 20 values but found 5");
    }

    @Test
    void treatsEmptyAndNaAsMissingForRequiredColumns() {
        List<Violation> violations = validator.validateRow(matches, with(with(VALID_MATCH, "id", ""), "venue", "NA"));

        assertThat(violations).extracting(Violation::reason, Violation::column).containsExactly(
                org.assertj.core.groups.Tuple.tuple(RejectionReason.MISSING_VALUE, "id"),
                org.assertj.core.groups.Tuple.tuple(RejectionReason.MISSING_VALUE, "venue"));
    }

    @ParameterizedTest(name = "{0} = ''{1}'' -> {2}")
    @CsvSource({
            "id,            12a,         INVALID_INTEGER",
            "id,            -5,          INVALID_INTEGER",
            "date,          18/04/2008,  INVALID_DATE",
            "date,          2024-02-30,  INVALID_DATE",
            "result_margin, ten,         INVALID_DECIMAL",
            "season,        08,          INVALID_VALUE",
            "toss_decision, bowl,        INVALID_VALUE",
            "super_over,    yes,         INVALID_VALUE"
    })
    void rejectsInvalidValues(String column, String value, RejectionReason expected) {
        List<Violation> violations = validator.validateRow(matches, with(VALID_MATCH, column, value));

        assertThat(violations).singleElement().satisfies(v -> {
            assertThat(v.reason()).isEqualTo(expected);
            assertThat(v.column()).isEqualTo(column);
            assertThat(v.message()).contains(value);
        });
    }

    @Test
    void reportsEveryViolationInARow() {
        List<String> row = with(with(with(VALID_MATCH, "id", "x"), "date", "2008-13-01"), "team1", "");

        assertThat(validator.validateRow(matches, row)).extracting(Violation::reason).containsExactly(
                RejectionReason.INVALID_INTEGER, RejectionReason.INVALID_DATE, RejectionReason.MISSING_VALUE);
    }

    @Test
    void validatesDeliveriesFlagsAndCounts() {
        List<String> header = List.of("match_id", "inning", "batting_team", "bowling_team", "over", "ball", "batter",
                "bowler", "non_striker", "batsman_runs", "extra_runs", "total_runs", "extras_type", "is_wicket",
                "player_dismissed", "dismissal_kind", "fielder");
        HeaderMapping deliveries = validator.mapHeader(Dataset.DELIVERIES, header);
        List<String> row = List.of("335982", "1", "KKR", "RCB", "0", "1", "SC Ganguly", "P Kumar", "BB McCullum",
                "0", "1", "1", "legbyes", "2", "NA", "NA", "NA");

        assertThat(validator.validateRow(deliveries, row)).extracting(Violation::column).containsExactly("is_wicket");
    }

    private static List<String> with(List<String> row, String column, String value) {
        List<String> copy = new ArrayList<>(row);
        copy.set(MATCHES_HEADER.indexOf(column), value);
        return copy;
    }
}
