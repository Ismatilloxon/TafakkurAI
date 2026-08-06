DROP FUNCTION IF EXISTS sync_max(jsonb, jsonb);
DROP FUNCTION IF EXISTS sync_increment(jsonb, jsonb);
DROP FUNCTION IF EXISTS sync_union(jsonb, jsonb);
DROP TABLE IF EXISTS sync_policy;
DROP TABLE IF EXISTS entity_state;
DROP SEQUENCE IF EXISTS entity_state_seq;
DROP TABLE IF EXISTS sync_event_log;
