-- Adds FIFO ordering and optimistic locking to operations.
-- Apply once before starting an app version that uses @Version.

ALTER TABLE operations
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE operations
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
