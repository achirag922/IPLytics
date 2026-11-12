-- Audit record for every ingestion attempt of one source file (see docs/adr/0004).
-- This is pipeline metadata, not a domain table.

CREATE TABLE core.ingestion_run (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dataset         TEXT        NOT NULL CHECK (dataset IN ('MATCHES', 'DELIVERIES')),
    file_name       TEXT        NOT NULL,
    file_hash       TEXT        NOT NULL CHECK (file_hash ~ '^[0-9a-f]{64}$'), -- SHA-256, lowercase hex
    file_size_bytes BIGINT      NOT NULL CHECK (file_size_bytes >= 0),
    status          TEXT        NOT NULL CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED')),
    rows_read       INTEGER     NOT NULL DEFAULT 0 CHECK (rows_read >= 0),
    rows_loaded     INTEGER     NOT NULL DEFAULT 0 CHECK (rows_loaded >= 0),
    rows_rejected   INTEGER     NOT NULL DEFAULT 0 CHECK (rows_rejected >= 0),
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    error_summary   TEXT,

    CONSTRAINT ingestion_run_completed_at_matches_status
        CHECK ((status = 'RUNNING') = (completed_at IS NULL)),
    CONSTRAINT ingestion_run_completed_counts_reconcile
        CHECK (status <> 'COMPLETED' OR rows_loaded + rows_rejected = rows_read),
    CONSTRAINT ingestion_run_failed_loads_nothing
        CHECK (status <> 'FAILED' OR rows_loaded = 0)
);

COMMENT ON TABLE core.ingestion_run IS 'One row per attempt to ingest a source file into staging';

-- Idempotency: the same file content can be RUNNING or COMPLETED at most once.
-- FAILED attempts do not block a retry.
CREATE UNIQUE INDEX ux_ingestion_run_active_file_hash
    ON core.ingestion_run (file_hash)
    WHERE status IN ('RUNNING', 'COMPLETED');

CREATE INDEX ix_ingestion_run_dataset_started ON core.ingestion_run (dataset, started_at DESC);
