-- Gratitude (Shukr) module storage. Complements tracker_entries(kind='gratitude'),
-- which stays the lightweight generic log; these tables are the rich journal the
-- Gratitude feature reads: entries with mood/emotions/media, guided journeys and
-- per-user settings.
--
-- Rows are soft-deleted (deleted_at) and carry updated_at so the mobile client can
-- pull deltas with ?since= and reconcile an offline-first local database.

CREATE TABLE gratitude_entries (
    id              uuid PRIMARY KEY,
    user_id         uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- Client-generated id. Unique per user so an at-least-once outbox replay is a
    -- no-op instead of a duplicate entry.
    client_id       text NOT NULL,
    mode            text NOT NULL DEFAULT 'quick' CHECK (mode IN ('quick', 'three', 'reflect')),
    body            text NOT NULL,
    items           text[] NOT NULL DEFAULT '{}',
    why             text,
    category        text NOT NULL DEFAULT 'moments',
    mood            text CHECK (mood IS NULL OR mood IN ('low', 'difficult', 'okay', 'good', 'wonderful')),
    emotions        text[] NOT NULL DEFAULT '{}',
    photo_count     int NOT NULL DEFAULT 0,
    has_audio       boolean NOT NULL DEFAULT false,
    has_video       boolean NOT NULL DEFAULT false,
    location_label  text,
    favourite       boolean NOT NULL DEFAULT false,
    entry_date      date NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    deleted_at      timestamptz,
    UNIQUE (user_id, client_id)
);

CREATE INDEX idx_gratitude_entries_user_date ON gratitude_entries (user_id, entry_date DESC, created_at DESC);
CREATE INDEX idx_gratitude_entries_user_updated ON gratitude_entries (user_id, updated_at DESC);
CREATE INDEX idx_gratitude_entries_user_cat ON gratitude_entries (user_id, category);

-- Guided journeys ("7-Day Gratitude Journey", "30-Day"). The catalogue itself is
-- static in code; only the user's participation is stored.
CREATE TABLE gratitude_challenges (
    user_id      uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    challenge_id text NOT NULL,
    joined_at    timestamptz NOT NULL DEFAULT now(),
    left_at      timestamptz,
    PRIMARY KEY (user_id, challenge_id)
);

CREATE TABLE gratitude_challenge_days (
    user_id      uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    challenge_id text NOT NULL,
    day_index    int NOT NULL CHECK (day_index >= 1),
    completed_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, challenge_id, day_index)
);

CREATE TABLE gratitude_settings (
    user_id       uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    reminder_slot text NOT NULL DEFAULT 'evening' CHECK (reminder_slot IN ('off', 'morning', 'afternoon', 'evening')),
    reminder_time text NOT NULL DEFAULT '19:30',
    face_id       boolean NOT NULL DEFAULT false,
    app_lock      boolean NOT NULL DEFAULT false,
    hide_preview  boolean NOT NULL DEFAULT true,
    cloud_sync    boolean NOT NULL DEFAULT true,
    updated_at    timestamptz NOT NULL DEFAULT now()
);
