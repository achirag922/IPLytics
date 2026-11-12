-- Cricsheet-derived source format (see docs/data-source.md): extra columns on the existing staging
-- tables plus the match_players dataset. All new columns are TEXT, validated by the ingestion pipeline.

ALTER TABLE staging.matches
    ADD COLUMN season_year         TEXT, -- derived by the converter from the match date (2007/08 -> 2008)
    ADD COLUMN end_date            TEXT,
    ADD COLUMN event_name          TEXT,
    ADD COLUMN match_number        TEXT,
    ADD COLUMN scheduled_overs     TEXT,
    ADD COLUMN super_over_winner   TEXT,
    ADD COLUMN player_of_match_ids TEXT,
    ADD COLUMN tv_umpires          TEXT,
    ADD COLUMN match_referees      TEXT,
    ADD COLUMN reserve_umpires     TEXT,
    ADD COLUMN source_data_version TEXT,
    ADD COLUMN source_created      TEXT,
    ADD COLUMN source_revision     TEXT,
    ADD COLUMN source_unmapped     TEXT; -- JSON: every source field not mapped to a column

ALTER TABLE staging.deliveries
    ADD COLUMN is_super_over             TEXT,
    ADD COLUMN batter_id                 TEXT,
    ADD COLUMN bowler_id                 TEXT,
    ADD COLUMN non_striker_id            TEXT,
    ADD COLUMN non_boundary              TEXT,
    ADD COLUMN wides                     TEXT,
    ADD COLUMN noballs                   TEXT,
    ADD COLUMN byes                      TEXT,
    ADD COLUMN legbyes                   TEXT,
    ADD COLUMN penalty                   TEXT,
    ADD COLUMN player_dismissed_id       TEXT,
    ADD COLUMN fielder_ids               TEXT,
    ADD COLUMN other_player_dismissed    TEXT,
    ADD COLUMN other_player_dismissed_id TEXT,
    ADD COLUMN other_dismissal_kind      TEXT,
    ADD COLUMN other_fielder             TEXT,
    ADD COLUMN other_fielder_ids         TEXT,
    ADD COLUMN wickets_json              TEXT, -- JSON: the exact source wickets array
    ADD COLUMN source_unmapped           TEXT; -- JSON: every delivery field not mapped to a column

CREATE TABLE staging.match_players (
    ingestion_run_id  BIGINT  NOT NULL REFERENCES core.ingestion_run (id) ON DELETE CASCADE,
    source_row_number INTEGER NOT NULL CHECK (source_row_number > 0),
    match_id          TEXT,
    team              TEXT,
    player            TEXT,
    player_id         TEXT,
    list_position     TEXT,
    extra_data        JSONB,
    PRIMARY KEY (ingestion_run_id, source_row_number)
);

COMMENT ON TABLE staging.match_players IS 'Raw rows from match_players.csv: the playing XI (and squad list) per match';

-- Audit metadata only (not a domain table): allow the new dataset.
ALTER TABLE core.ingestion_run
    DROP CONSTRAINT ingestion_run_dataset_check,
    ADD CONSTRAINT ingestion_run_dataset_check CHECK (dataset IN ('MATCHES', 'DELIVERIES', 'MATCH_PLAYERS'));
