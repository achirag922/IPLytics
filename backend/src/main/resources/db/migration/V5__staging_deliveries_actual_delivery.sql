-- Cricsheet's scorer-style ball label (over.ball counting legal balls only), present on every delivery.
-- Previously preserved inside source_unmapped; promoted to its own column (see docs/data-source.md).
ALTER TABLE staging.deliveries ADD COLUMN actual_delivery TEXT;
