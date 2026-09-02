-- Sleep & Rest.
--
-- Mirrors the client's SQLDelight schema (SleepSession.sq, SleepRoutineMark.sq) so the two
-- halves of an offline-first feature agree on what a night is.

-- ---------------------------------------------------------------------------
-- sleep_strength: how much a session is worth, as one comparable number.
--
-- The exact counterpart of Session.Strength() in Go and SleepMerge.strength() in Kotlin.
-- All THREE must agree. If they diverge, a device and the server resolve the same conflict
-- differently: the server rejects the device's copy, the device re-uploads it, and the pair
-- flaps forever while the user watches their history change under them.
--
-- It lives in SQL so the resolution can ride inside the INSERT ... ON CONFLICT DO UPDATE
-- WHERE clause and therefore be atomic. Doing it as read-then-write in Go would let two
-- devices uploading the same night concurrently both observe "no conflict" and both write.
--
-- IMMUTABLE so the planner can use it freely; it reads only its arguments.
CREATE OR REPLACE FUNCTION sleep_strength(
    p_source         text,
    p_segments       jsonb,
    p_vitals         jsonb,
    p_asleep_minutes integer
) RETURNS bigint
LANGUAGE sql
IMMUTABLE
PARALLEL SAFE
AS $$
    SELECT (CASE p_source
                WHEN 'watch'        THEN 400
                WHEN 'health_store' THEN 300
                WHEN 'automatic'    THEN 200
                ELSE 100
            END)::bigint * 1000000
         -- "Has staging" means a non-empty segment array, matching the clients' len > 2 test
         -- on the encoded blob.
         + CASE WHEN p_segments IS NOT NULL
                 AND jsonb_typeof(p_segments) = 'array'
                 AND jsonb_array_length(p_segments) > 0
                THEN 500000 ELSE 0 END
         + CASE WHEN p_vitals IS NOT NULL
                 AND jsonb_typeof(p_vitals) = 'object'
                 AND p_vitals <> '{}'::jsonb
                THEN 100000 ELSE 0 END
         + coalesce(p_asleep_minutes, 0)::bigint;
$$;

CREATE TABLE IF NOT EXISTS sleep_sessions (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- The client-generated id. Together with user_id it is the idempotency key: a replayed
    -- outbox event updates this row instead of creating a second night.
    client_id          text NOT NULL,

    -- The HealthKit / Health Connect record id, when the session came from one. Distinct from
    -- client_id because the SAME underlying night imported on a phone and a tablet arrives
    -- with two different client ids but one external id — see the partial unique index below.
    external_id        text,

    kind               text NOT NULL DEFAULT 'night'
                       CHECK (kind IN ('night', 'nap')),
    source             text NOT NULL DEFAULT 'manual'
                       CHECK (source IN ('watch', 'health_store', 'automatic', 'manual')),

    start_at           timestamptz NOT NULL,
    end_at             timestamptz NOT NULL,
    in_bed_start_at    timestamptz NOT NULL,
    in_bed_end_at      timestamptz NOT NULL,

    -- Read and written whole, never queried into, exactly as on the client. jsonb rather than
    -- json so a future analytical query can index into it without a rewrite.
    segments           jsonb NOT NULL DEFAULT '[]'::jsonb,
    vitals             jsonb NOT NULL DEFAULT '{}'::jsonb,

    -- The local WAKE day. Stored as a date, not derived from start_at, because the device
    -- knows the user's timezone and the server must not guess it.
    date_key           date NOT NULL,

    mood_on_waking     text,
    note               text,
    tags               text[] NOT NULL DEFAULT '{}',

    -- Denormalised so leaderboards, trends and the weekly digest do not re-run the scoring
    -- engine over a year of nights per request. The client is the authority: it holds the
    -- user's goals and baseline, which the server deliberately does not duplicate.
    score              smallint,
    recovery_score     smallint,
    asleep_minutes     integer NOT NULL DEFAULT 0,

    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    deleted_at         timestamptz,

    CONSTRAINT sleep_sessions_window CHECK (end_at >= start_at),
    CONSTRAINT sleep_sessions_bed_window CHECK (in_bed_end_at >= in_bed_start_at)
);

CREATE UNIQUE INDEX IF NOT EXISTS sleep_sessions_client
    ON sleep_sessions (user_id, client_id);

-- Cross-device import dedupe. Partial because external_id is NULL for manual entries, and
-- several manual nights per user must remain legal.
CREATE UNIQUE INDEX IF NOT EXISTS sleep_sessions_external
    ON sleep_sessions (user_id, external_id)
    WHERE external_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS sleep_sessions_date
    ON sleep_sessions (user_id, date_key DESC)
    WHERE deleted_at IS NULL;

-- The delta-pull index. Covers `WHERE user_id = $1 AND updated_at > $2 ORDER BY updated_at`,
-- which is the hottest query in the module: every client runs it on every foreground.
CREATE INDEX IF NOT EXISTS sleep_sessions_changes
    ON sleep_sessions (user_id, updated_at);

-- ---------------------------------------------------------------------------
-- Routine completions. One row per completed step per day; absence means not done.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sleep_routine_marks (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- "yyyy-MM-dd|step.id", the client's composite primary key flattened.
    mark_id      text NOT NULL,
    step_id      text NOT NULL,
    phase        text NOT NULL CHECK (phase IN ('bedtime', 'morning')),
    date_key     date NOT NULL,
    completed_at timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    deleted_at   timestamptz
);

CREATE UNIQUE INDEX IF NOT EXISTS sleep_routine_marks_key
    ON sleep_routine_marks (user_id, mark_id);

CREATE INDEX IF NOT EXISTS sleep_routine_marks_date
    ON sleep_routine_marks (user_id, date_key DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS sleep_routine_marks_changes
    ON sleep_routine_marks (user_id, updated_at);

-- ---------------------------------------------------------------------------
-- Per-user settings. One row per user, created on first write.
--
-- goals and flags are jsonb rather than columns: this shape changes most releases, and a
-- blob absorbs a new toggle without a migration on a table every client writes to.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sleep_settings (
    user_id                 uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    goals                   jsonb NOT NULL DEFAULT '{}'::jsonb,
    enabled_step_ids        text[] NOT NULL DEFAULT '{}',
    islamic_routine_enabled boolean NOT NULL DEFAULT false,
    flags                   jsonb NOT NULL DEFAULT '{}'::jsonb,
    updated_at              timestamptz NOT NULL DEFAULT now()
);
