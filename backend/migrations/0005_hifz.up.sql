-- Qurʼan memorization schedule, one row per (user, ayah).
--
-- The device is the source of truth for this table. FSRS runs on-device so a session works
-- with no connection, and grades arrive here from the client's outbox afterwards. This copy
-- exists for two things only: restoring a schedule onto a new device, and having a server
-- record behind the XP award. It never tells the client when an ayah is due.
--
-- No ayah text is stored. The reference is the whole record — the same rule the client
-- follows, so scripture has exactly one source on both sides of the wire.

CREATE TABLE hifz_cards (
    user_id     uuid    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    surah       int     NOT NULL CHECK (surah BETWEEN 1 AND 114),
    ayah        int     NOT NULL CHECK (ayah >= 1),
    state       int     NOT NULL DEFAULT 0 CHECK (state BETWEEN 0 AND 3),
    stability   double precision NOT NULL DEFAULT 0,
    difficulty  double precision NOT NULL DEFAULT 0,
    due         timestamptz NOT NULL DEFAULT now(),
    last_review timestamptz,
    reps        int     NOT NULL DEFAULT 0,
    lapses      int     NOT NULL DEFAULT 0,
    last_rating int     CHECK (last_rating BETWEEN 1 AND 4),
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, surah, ayah)
);

CREATE INDEX idx_hifz_cards_user_due ON hifz_cards (user_id, due);

-- Per-user pace. One row per user, created on first write.
CREATE TABLE hifz_plans (
    user_id    uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    daily_new  int NOT NULL DEFAULT 2  CHECK (daily_new BETWEEN 1 AND 20),
    review_cap int NOT NULL DEFAULT 40 CHECK (review_cap BETWEEN 5 AND 200),
    updated_at timestamptz NOT NULL DEFAULT now()
);
