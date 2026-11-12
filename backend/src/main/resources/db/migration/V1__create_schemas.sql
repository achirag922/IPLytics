-- V0 baseline: the three logical layers of the data platform (see docs/adr/0002).
-- Domain tables are added by later versioned migrations (V2__..., V3__...).
-- Derived views will live in repeatable migrations (R__...).

CREATE SCHEMA IF NOT EXISTS staging;
COMMENT ON SCHEMA staging IS 'Raw CSV rows as loaded (text columns), tagged with the ingestion run';

CREATE SCHEMA IF NOT EXISTS core;
COMMENT ON SCHEMA core IS 'Normalized, validated IPL reference data and ball-by-ball facts';

CREATE SCHEMA IF NOT EXISTS analytics;
COMMENT ON SCHEMA analytics IS 'Derived read models (materialized views) refreshed after each ingestion';
