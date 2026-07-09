-- Rich per-prayer completion records for analytics (streaks, delay averages,
-- charts). Complements tracker_entries(kind='prayer'), which stays as the
-- lightweight generic log; this table is written by the same Track call and
-- is the source for the Prayer Analytics feature.

CREATE TABLE prayer_completions (
    id              uuid PRIMARY KEY,
    user_id         uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    prayer          text NOT NULL CHECK (prayer IN ('fajr', 'dhuhr', 'asr', 'maghrib', 'isha')),
    scheduled_time  timestamptz NOT NULL,
    completed_at    timestamptz NOT NULL,
    delay_minutes   int NOT NULL,
    on_time         boolean NOT NULL,
    late            boolean NOT NULL,
    latitude        double precision,
    longitude       double precision,
    location_label  text,
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_prayer_completions_user_time ON prayer_completions (user_id, scheduled_time DESC);
CREATE INDEX idx_prayer_completions_user_prayer ON prayer_completions (user_id, prayer, scheduled_time DESC);
