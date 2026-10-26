-- Phase 3 migration: destination fields and new outcome states.
-- No Flyway yet, so run this once against the local database before starting
-- the app. ddl-auto=update adds the columns but does not reliably update the
-- enum check constraints.

ALTER TABLE operations ADD COLUMN IF NOT EXISTS target_url TEXT;
ALTER TABLE operations ADD COLUMN IF NOT EXISTS http_method VARCHAR(255);
ALTER TABLE operations ADD COLUMN IF NOT EXISTS request_body TEXT;

-- Backfill is only needed if rows exist from Phase 2. The local demo DB is empty.
UPDATE operations SET target_url = 'http://localhost:8080/operations' WHERE target_url IS NULL;
UPDATE operations SET http_method = 'POST' WHERE http_method IS NULL;

ALTER TABLE operations ALTER COLUMN target_url SET NOT NULL;
ALTER TABLE operations ALTER COLUMN http_method SET NOT NULL;

ALTER TABLE operations DROP CONSTRAINT IF EXISTS operations_state_check;
ALTER TABLE operations ADD CONSTRAINT operations_state_check
    CHECK (state IN ('PENDING', 'PROCESSING', 'RETRY_WAITING', 'SUCCESS', 'FAILED'));

ALTER TABLE attempts DROP CONSTRAINT IF EXISTS attempts_state_check;
ALTER TABLE attempts ADD CONSTRAINT attempts_state_check
    CHECK (state IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'UNKNOWN'));
