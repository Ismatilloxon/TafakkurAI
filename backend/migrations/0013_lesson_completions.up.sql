-- Per-user source of truth for lesson completion.
--
-- A completion is one immutable command. The client-generated event UUID is kept
-- alongside the user's civil date and IANA timezone so retries do not invent a new
-- streak day or award XP twice. Result columns preserve the semantic response of the
-- first successful award, including when a retry arrives after later activity.

CREATE TABLE lesson_completions (
    user_id      uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    lesson_id    uuid NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    event_id     uuid NOT NULL,
    local_date   date NOT NULL,
    timezone     text NOT NULL CHECK (char_length(timezone) BETWEEN 1 AND 64),
    completed_at timestamptz NOT NULL DEFAULT now(),
    result_streak  integer CHECK (result_streak IS NULL OR result_streak >= 0),
    result_level   integer CHECK (result_level IS NULL OR result_level >= 1),
    result_xp      integer CHECK (result_xp IS NULL OR result_xp >= 0),
    result_xp_next integer CHECK (result_xp_next IS NULL OR result_xp_next > 0),
    PRIMARY KEY (user_id, lesson_id),
    UNIQUE (user_id, event_id),
    CHECK (
        (result_streak IS NULL AND result_level IS NULL AND result_xp IS NULL AND result_xp_next IS NULL)
        OR
        (result_streak IS NOT NULL AND result_level IS NOT NULL AND result_xp IS NOT NULL AND result_xp_next IS NOT NULL)
    )
);

CREATE INDEX lesson_completions_user_completed
    ON lesson_completions (user_id, completed_at DESC);

-- Migration 0012 already made the legacy endpoint idempotent with keys shaped as
-- "lesson:<lesson UUID>". Preserve those completions and their original award response.
-- Their timezone was never collected, so UTC is recorded explicitly rather than guessed.
INSERT INTO lesson_completions (
    user_id,
    lesson_id,
    event_id,
    local_date,
    timezone,
    completed_at,
    result_streak,
    result_level,
    result_xp,
    result_xp_next
)
SELECT
    award.user_id,
    lesson.id,
    lesson.id,
    award.local_date,
    'UTC',
    award.created_at,
    award.result_streak,
    award.result_level,
    award.result_xp,
    award.result_xp_next
FROM gamification_awards AS award
JOIN lessons AS lesson
  ON award.idempotency_key = 'lesson:' || lesson.id::text
WHERE award.event = 'lesson_completed'
ON CONFLICT DO NOTHING;
