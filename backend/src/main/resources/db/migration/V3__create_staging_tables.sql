-- Staging: source rows exactly as read from the CSV files (see docs/data-source.md).
-- Every source column is TEXT on purpose: values are validated explicitly by the ingestion
-- pipeline and typed in the staging -> core step, never silently cast by PostgreSQL.
-- Empty values are stored as NULL; everything else (including the token 'NA') is verbatim.

CREATE TABLE staging.matches (
    ingestion_run_id  BIGINT  NOT NULL REFERENCES core.ingestion_run (id) ON DELETE CASCADE,
    source_row_number INTEGER NOT NULL CHECK (source_row_number > 0), -- 1 = first row after the header
    id                TEXT,
    season            TEXT,
    city              TEXT,
    "date"            TEXT,
    match_type        TEXT,
    player_of_match   TEXT,
    venue             TEXT,
    team1             TEXT,
    team2             TEXT,
    toss_winner       TEXT,
    toss_decision     TEXT,
    winner            TEXT,
    result            TEXT,
    result_margin     TEXT,
    target_runs       TEXT,
    target_overs      TEXT,
    super_over        TEXT,
    method            TEXT,
    umpire1           TEXT,
    umpire2           TEXT,
    extra_data        JSONB, -- source columns not listed above, preserved as {"column": "value"}
    PRIMARY KEY (ingestion_run_id, source_row_number)
);

CREATE TABLE staging.deliveries (
    ingestion_run_id  BIGINT  NOT NULL REFERENCES core.ingestion_run (id) ON DELETE CASCADE,
    source_row_number INTEGER NOT NULL CHECK (source_row_number > 0),
    match_id          TEXT,
    inning            TEXT,
    batting_team      TEXT,
    bowling_team      TEXT,
    "over"            TEXT,
    ball              TEXT,
    batter            TEXT,
    bowler            TEXT,
    non_striker       TEXT,
    batsman_runs      TEXT,
    extra_runs        TEXT,
    total_runs        TEXT,
    extras_type       TEXT,
    is_wicket         TEXT,
    player_dismissed  TEXT,
    dismissal_kind    TEXT,
    fielder           TEXT,
    extra_data        JSONB,
    PRIMARY KEY (ingestion_run_id, source_row_number)
);

CREATE TABLE staging.rejected_row (
    id                BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ingestion_run_id  BIGINT      NOT NULL REFERENCES core.ingestion_run (id) ON DELETE CASCADE,
    source_file       TEXT        NOT NULL,
    source_row_number INTEGER     NOT NULL CHECK (source_row_number > 0),
    reason_code       TEXT        NOT NULL, -- first violation, e.g. INVALID_DATE (see RejectionReason)
    reason            TEXT        NOT NULL, -- every violation found in the row, human readable
    raw_data          JSONB       NOT NULL, -- the source row as {"header": "value"}
    rejected_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (ingestion_run_id, source_row_number)
);

COMMENT ON TABLE staging.matches IS 'Raw rows from matches.csv, one batch per ingestion run';
COMMENT ON TABLE staging.deliveries IS 'Raw rows from deliveries.csv, one batch per ingestion run';
COMMENT ON TABLE staging.rejected_row IS 'Source rows that failed validation, with the reason and the raw values';
