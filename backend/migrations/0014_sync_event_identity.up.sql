-- Scope sync idempotency to the authenticated owner and retain enough semantic
-- identity to distinguish a real replay from a conflicting reuse of the same UUID.
--
-- The old global primary key let one account reserve an event UUID for every other
-- account. UUID v4 collisions are vanishingly rare, but user-supplied identifiers must
-- not be a cross-tenant denial-of-service boundary. Existing rows keep a NULL hash and
-- are treated as legacy duplicates; every new row records a SHA-256 semantic hash.

ALTER TABLE sync_event_log
    ADD COLUMN feature text,
    ADD COLUMN entity_id text,
    ADD COLUMN event_hash text;

ALTER TABLE sync_event_log
    DROP CONSTRAINT sync_event_log_pkey,
    ADD CONSTRAINT sync_event_log_pkey PRIMARY KEY (user_id, event_id),
    ADD CONSTRAINT sync_event_log_hash_shape
        CHECK (event_hash IS NULL OR event_hash ~ '^[0-9a-f]{64}$');

-- Immutable local actions can be retried after a restore or long offline period. The
-- ledger therefore has the same retention as the account instead of a time window.
DROP INDEX IF EXISTS sync_event_log_prune;

-- Complete immutable facts written by the pre-envelope client and publish the repaired
-- row through the normal delta cursor. The event's original type/properties/at survive;
-- only missing stable envelope fields are supplied from the outer persisted contract.
UPDATE entity_state
SET payload = payload || jsonb_build_object(
        'at', COALESCE(
            payload -> 'at',
            to_jsonb((extract(epoch FROM device_time) * 1000)::bigint)
        ),
        'entityId', COALESCE(payload ->> 'entityId', entity_id),
        'localDate', COALESCE(
            payload ->> 'localDate',
            to_char(
                device_time AT TIME ZONE CASE
                    WHEN timezone IN (SELECT name FROM pg_timezone_names()) THEN timezone
                    ELSE 'UTC'
                END,
                'YYYY-MM-DD'
            )
        ),
        'timezone', CASE
            WHEN timezone IN (SELECT name FROM pg_timezone_names()) THEN timezone
            ELSE 'UTC'
        END,
        'source', 'legacy',
        'schemaVersion', 1
    ),
    timezone = CASE
        WHEN timezone IN (SELECT name FROM pg_timezone_names()) THEN timezone
        ELSE 'UTC'
    END,
    seq = nextval('entity_state_seq'),
    version = version + 1,
    updated_at = now()
WHERE entity = 'activityEvent'
  AND jsonb_typeof(payload -> 'type') = 'string'
  AND NOT (payload ? 'schemaVersion');
