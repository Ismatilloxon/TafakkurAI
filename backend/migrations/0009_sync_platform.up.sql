-- Generic event-sync platform.
--
-- One table holds the synced state of EVERY feature — present and future. Adding
-- "meditation" or "water" tomorrow needs no migration, no Go code and no new table:
-- the client starts emitting events with a new `entity` and they land here.
--
-- Two tables, one job each:
--
--   sync_event_log  append-only idempotency ledger. The client event id is the primary
--                   key, so replaying a batch after a flaky network is free: the second
--                   insert conflicts and the event is skipped instead of re-applied.
--
--   entity_state    the projection every read path uses. One row per
--                   (user, entity, entity_id) holding the latest payload. Bounded by
--                   what the user owns, NOT by how many times they touched it — a
--                   thousand dhikr taps collapse into one row.
--
-- Deliberately NOT event-sourced-with-replay: we keep the log only for idempotency and
-- prune it, because replaying millions of events to answer "what is today's dhikr count"
-- would be absurd. The projection is the source of truth for reads.

CREATE TABLE sync_event_log (
    event_id    uuid PRIMARY KEY,
    user_id     uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    entity      text NOT NULL,
    received_at timestamptz NOT NULL DEFAULT now()
);

-- Pruning index. Rows older than the client's longest plausible offline window
-- (30 days) can be deleted: an event that old will never be retried.
CREATE INDEX sync_event_log_prune ON sync_event_log (received_at);

-- Monotonic pull cursor. A sequence rather than a timestamp on purpose: two rows
-- written in the same microsecond get distinct cursors, so a delta pull can never
-- skip a row that shares a timestamp with the last one it saw, and no clock skew
-- between app servers can move the cursor backwards.
CREATE SEQUENCE entity_state_seq;

CREATE TABLE entity_state (
    user_id     uuid    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- Free-form, client-declared. The server never validates them against a list:
    -- that is exactly what would force a backend change per new feature.
    feature     text    NOT NULL,
    entity      text    NOT NULL,
    entity_id   text    NOT NULL,
    payload     jsonb   NOT NULL DEFAULT '{}'::jsonb,
    -- Tombstone rather than DELETE, so a delta pull can tell other devices the row
    -- is gone. A hard DELETE would simply vanish and the peer would keep its copy.
    deleted     boolean NOT NULL DEFAULT false,
    -- The device clock at the moment of the local write. Drives last-write-wins, and
    -- is deliberately separate from updated_at (our clock) so an event that spent a
    -- week in an offline queue cannot overwrite a newer change made meanwhile.
    device_time timestamptz NOT NULL,
    timezone    text    NOT NULL DEFAULT 'UTC',
    seq         bigint  NOT NULL,
    version     integer NOT NULL DEFAULT 1,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, entity, entity_id)
);

-- The delta-pull index. Every "what changed for me since cursor X" query is a range
-- scan on this and nothing else.
CREATE INDEX entity_state_pull ON entity_state (user_id, seq);

-- Feature read models ("give me this user's prayer rows") without scanning the user's
-- whole state.
CREATE INDEX entity_state_feature ON entity_state (user_id, feature, entity)
    WHERE deleted = false;

-- Conflict policy is DATA, not code. Changing how dhikr merges is an UPDATE here, not
-- a backend deploy, and an unknown entity falls back to last-write-wins — which is why
-- a brand-new feature works without anybody adding a row.
CREATE TABLE sync_policy (
    entity     text PRIMARY KEY,
    strategy   text NOT NULL DEFAULT 'lww'
        CHECK (strategy IN ('lww', 'merge', 'union', 'increment', 'max')),
    updated_at timestamptz NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Conflict-resolution primitives.
--
-- These live in SQL so the bulk UPSERT can apply them inside the single statement
-- that writes the batch. Doing it in Go would mean read-modify-write per row: a
-- SELECT and an UPDATE per event instead of one statement per batch.
-- ---------------------------------------------------------------------------

-- 'union': array-valued keys become the set union of both sides; other keys take the
-- incoming value. For bookmarks, tags, completed-item lists — two devices that each
-- added a different bookmark offline must end up with both, never with one lost.
CREATE OR REPLACE FUNCTION sync_union(a jsonb, b jsonb) RETURNS jsonb AS $$
DECLARE
    k   text;
    v   jsonb;
    res jsonb := COALESCE(a, '{}'::jsonb);
BEGIN
    FOR k, v IN SELECT key, value FROM jsonb_each(COALESCE(b, '{}'::jsonb)) LOOP
        IF jsonb_typeof(v) = 'array' AND jsonb_typeof(COALESCE(a -> k, 'null'::jsonb)) = 'array' THEN
            res := jsonb_set(res, ARRAY[k], (
                SELECT COALESCE(jsonb_agg(e), '[]'::jsonb)
                FROM (
                    SELECT jsonb_array_elements(a -> k) AS e
                    UNION
                    SELECT jsonb_array_elements(v)
                ) u
            ), true);
        ELSE
            res := jsonb_set(res, ARRAY[k], v, true);
        END IF;
    END LOOP;
    RETURN res;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- 'increment': numeric keys are added. Only for entities whose client genuinely sends
-- deltas. Our counters send absolute totals (see 'max'), because a delta that gets
-- retried after a half-delivered batch double-counts and there is no way to detect it.
CREATE OR REPLACE FUNCTION sync_increment(a jsonb, b jsonb) RETURNS jsonb AS $$
DECLARE
    k   text;
    v   jsonb;
    res jsonb := COALESCE(a, '{}'::jsonb);
BEGIN
    FOR k, v IN SELECT key, value FROM jsonb_each(COALESCE(b, '{}'::jsonb)) LOOP
        IF jsonb_typeof(v) = 'number' AND jsonb_typeof(COALESCE(a -> k, 'null'::jsonb)) = 'number' THEN
            res := jsonb_set(res, ARRAY[k],
                to_jsonb(((a ->> k)::numeric) + ((v #>> '{}')::numeric)), true);
        ELSE
            res := jsonb_set(res, ARRAY[k], v, true);
        END IF;
    END LOOP;
    RETURN res;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- 'max': numeric keys take the greater value. The right strategy for absolute counters
-- (dhikr totals, ayahs read, steps): two devices offline on the same day converge on
-- the larger count, and an out-of-order replay can never lower a total.
CREATE OR REPLACE FUNCTION sync_max(a jsonb, b jsonb) RETURNS jsonb AS $$
DECLARE
    k   text;
    v   jsonb;
    res jsonb := COALESCE(a, '{}'::jsonb);
BEGIN
    FOR k, v IN SELECT key, value FROM jsonb_each(COALESCE(b, '{}'::jsonb)) LOOP
        IF jsonb_typeof(v) = 'number' AND jsonb_typeof(COALESCE(a -> k, 'null'::jsonb)) = 'number' THEN
            res := jsonb_set(res, ARRAY[k],
                to_jsonb(GREATEST((a ->> k)::numeric, (v #>> '{}')::numeric)), true);
        ELSE
            res := jsonb_set(res, ARRAY[k], v, true);
        END IF;
    END LOOP;
    RETURN res;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- Seed the policies that differ from the last-write-wins default. Everything absent
-- from this table — including every feature that does not exist yet — is 'lww'.
INSERT INTO sync_policy (entity, strategy) VALUES
    -- Absolute daily counters: the larger count wins, so a stale replay cannot undo taps.
    ('dhikrCount',   'max'),
    ('quranRead',    'max'),
    -- Set-shaped rows: two devices each completing a different adhkar item must keep both.
    ('adhkarMark',   'union'),
    ('gratitudeChallengeDay', 'union'),
    -- Field-wise merge: the reader updates lastRead while the settings screen updates the
    -- reciter; a whole-object overwrite would silently revert whichever landed first.
    ('appPref',      'merge')
ON CONFLICT (entity) DO NOTHING;
