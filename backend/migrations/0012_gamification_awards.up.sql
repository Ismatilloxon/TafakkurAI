-- Atomic, idempotent progression.
--
-- `gamification_awards` is the command ledger: the same feature command may be
-- retried after a timeout without adding XP twice.  Its result columns preserve
-- the semantic response of the first successful execution.
--
-- `gamification_activity_days` is the streak read model.  Keeping civil dates as
-- rows lets an offline event fill a historical gap without moving
-- `last_active_date` backwards or deriving a user's day from server UTC.

CREATE TABLE gamification_activity_days (
    user_id          uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    local_date       date NOT NULL,
    first_awarded_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, local_date)
);

-- Preserve the streak already shown to existing users.  The old schema retained
-- only its length and last date, so the only lossless reconstruction is the
-- contiguous range ending on `last_active_date`.
INSERT INTO gamification_activity_days (user_id, local_date)
SELECT g.user_id, activity_day.local_date::date
FROM gamification AS g
CROSS JOIN LATERAL generate_series(
    g.last_active_date - GREATEST(g.streak - 1, 0),
    g.last_active_date,
    interval '1 day'
) AS activity_day(local_date)
WHERE g.last_active_date IS NOT NULL
  AND g.streak > 0
ON CONFLICT DO NOTHING;

CREATE TABLE gamification_awards (
    user_id                uuid NOT NULL,
    event                  text NOT NULL CHECK (char_length(event) BETWEEN 1 AND 64),
    idempotency_key        text NOT NULL CHECK (char_length(idempotency_key) BETWEEN 1 AND 200),
    local_date             date NOT NULL,
    result_streak          integer NOT NULL CHECK (result_streak >= 0),
    result_level           integer NOT NULL CHECK (result_level >= 1),
    result_xp              integer NOT NULL CHECK (result_xp >= 0),
    result_xp_next         integer NOT NULL CHECK (result_xp_next > 0),
    result_last_active_date date NOT NULL,
    created_at             timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, event, idempotency_key),
    FOREIGN KEY (user_id, local_date)
        REFERENCES gamification_activity_days(user_id, local_date)
        ON DELETE CASCADE
);

CREATE INDEX gamification_awards_user_created
    ON gamification_awards (user_id, created_at DESC);
