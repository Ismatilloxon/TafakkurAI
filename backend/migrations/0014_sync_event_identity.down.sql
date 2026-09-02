-- A global event-id key cannot represent the valid same-UUID/different-user rows allowed
-- by the forward schema. Keep the earliest owner deterministically before restoring it.
UPDATE entity_state
SET payload = payload
        - 'entityId'
        - 'localDate'
        - 'timezone'
        - 'source'
        - 'schemaVersion',
    seq = nextval('entity_state_seq'),
    version = version + 1,
    updated_at = now()
WHERE entity = 'activityEvent'
  AND payload ->> 'source' = 'legacy'
  AND payload ->> 'schemaVersion' = '1';

DELETE FROM sync_event_log AS candidate
USING sync_event_log AS keeper
WHERE candidate.event_id = keeper.event_id
  AND (
      candidate.received_at > keeper.received_at OR
      (candidate.received_at = keeper.received_at AND candidate.user_id > keeper.user_id)
  );

ALTER TABLE sync_event_log
    DROP CONSTRAINT sync_event_log_pkey,
    DROP CONSTRAINT sync_event_log_hash_shape,
    ADD CONSTRAINT sync_event_log_pkey PRIMARY KEY (event_id),
    DROP COLUMN event_hash,
    DROP COLUMN entity_id,
    DROP COLUMN feature;

CREATE INDEX sync_event_log_prune ON sync_event_log (received_at);
