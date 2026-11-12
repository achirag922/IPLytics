package com.iplytics.ingestion.schema;

import static com.iplytics.ingestion.schema.ColumnSpec.nullable;
import static com.iplytics.ingestion.schema.ColumnSpec.optional;
import static com.iplytics.ingestion.schema.ColumnSpec.required;
import static com.iplytics.ingestion.schema.ValueType.DATE;
import static com.iplytics.ingestion.schema.ValueType.DECIMAL;
import static com.iplytics.ingestion.schema.ValueType.FLAG;
import static com.iplytics.ingestion.schema.ValueType.INTEGER;
import static com.iplytics.ingestion.schema.ValueType.JSON;
import static com.iplytics.ingestion.schema.ValueType.SEASON;
import static com.iplytics.ingestion.schema.ValueType.TEXT;
import static com.iplytics.ingestion.schema.ValueType.TOSS_DECISION;
import static com.iplytics.ingestion.schema.ValueType.YES_NO;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Supported source files and their columns (documented in docs/data-source.md).
 *
 * <p>This is the single source of truth for both sides of the pipeline: the Cricsheet converter writes
 * exactly these columns in this order, and staging ingestion validates and loads them. Columns added for
 * Cricsheet-derived data are {@code optional} so older Kaggle-style files still validate.
 */
public enum Dataset {

    MATCHES("matches.csv", "staging.matches", List.of(
            required("id", INTEGER),
            required("season", SEASON),
            optional("season_year", INTEGER),
            optional("city", TEXT),
            required("date", DATE),
            optional("end_date", DATE),
            optional("event_name", TEXT),
            optional("match_number", INTEGER),
            optional("match_type", TEXT),
            optional("player_of_match", TEXT),
            optional("player_of_match_ids", TEXT),
            required("venue", TEXT),
            required("team1", TEXT),
            required("team2", TEXT),
            nullable("toss_winner", TEXT),
            nullable("toss_decision", TOSS_DECISION),
            nullable("winner", TEXT),
            optional("super_over_winner", TEXT),
            nullable("result", TEXT),
            optional("result_margin", DECIMAL),
            optional("target_runs", DECIMAL),
            optional("target_overs", DECIMAL),
            optional("super_over", YES_NO),
            optional("method", TEXT),
            optional("scheduled_overs", DECIMAL),
            optional("umpire1", TEXT),
            optional("umpire2", TEXT),
            optional("tv_umpires", TEXT),
            optional("match_referees", TEXT),
            optional("reserve_umpires", TEXT),
            optional("source_data_version", TEXT),
            optional("source_created", DATE),
            optional("source_revision", INTEGER),
            optional("source_unmapped", JSON))),

    DELIVERIES("deliveries.csv", "staging.deliveries", List.of(
            required("match_id", INTEGER),
            required("inning", INTEGER),
            optional("is_super_over", FLAG),
            required("batting_team", TEXT),
            required("bowling_team", TEXT),
            required("over", INTEGER),
            required("ball", INTEGER),
            optional("actual_delivery", DECIMAL),
            required("batter", TEXT),
            optional("batter_id", TEXT),
            required("bowler", TEXT),
            optional("bowler_id", TEXT),
            required("non_striker", TEXT),
            optional("non_striker_id", TEXT),
            required("batsman_runs", INTEGER),
            required("extra_runs", INTEGER),
            required("total_runs", INTEGER),
            optional("non_boundary", FLAG),
            nullable("extras_type", TEXT),
            optional("wides", INTEGER),
            optional("noballs", INTEGER),
            optional("byes", INTEGER),
            optional("legbyes", INTEGER),
            optional("penalty", INTEGER),
            required("is_wicket", FLAG),
            nullable("player_dismissed", TEXT),
            optional("player_dismissed_id", TEXT),
            nullable("dismissal_kind", TEXT),
            optional("fielder", TEXT),
            optional("fielder_ids", TEXT),
            optional("other_player_dismissed", TEXT),
            optional("other_player_dismissed_id", TEXT),
            optional("other_dismissal_kind", TEXT),
            optional("other_fielder", TEXT),
            optional("other_fielder_ids", TEXT),
            optional("wickets_json", JSON),
            optional("source_unmapped", JSON))),

    MATCH_PLAYERS("match_players.csv", "staging.match_players", List.of(
            required("match_id", INTEGER),
            required("team", TEXT),
            required("player", TEXT),
            nullable("player_id", TEXT),
            required("list_position", INTEGER)));

    private final String fileName;
    private final String stagingTable;
    private final List<ColumnSpec> columns;

    Dataset(String fileName, String stagingTable, List<ColumnSpec> columns) {
        this.fileName = fileName;
        this.stagingTable = stagingTable;
        this.columns = columns;
    }

    public String fileName() {
        return fileName;
    }

    public String stagingTable() {
        return stagingTable;
    }

    public List<ColumnSpec> columns() {
        return columns;
    }

    public List<String> columnNames() {
        return columns.stream().map(ColumnSpec::name).toList();
    }

    /** Matches a file name case-insensitively, e.g. {@code Matches.CSV} is {@link #MATCHES}. */
    public static Optional<Dataset> forFileName(String fileName) {
        return Arrays.stream(values()).filter(d -> d.fileName.equalsIgnoreCase(fileName)).findFirst();
    }

    public static List<String> supportedFileNames() {
        return Arrays.stream(values()).map(Dataset::fileName).toList();
    }
}
