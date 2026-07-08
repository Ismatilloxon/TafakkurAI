-- Core schema for TafakkurAI. UUIDs are generated in the application layer, so
-- no uuid extension is required here.

CREATE TABLE users (
    id               uuid PRIMARY KEY,
    provider         text NOT NULL,
    provider_subject text NOT NULL,
    email            text,
    phone            text,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_subject)
);

CREATE TABLE profiles (
    user_id         uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    display_name    text NOT NULL,
    handle          text NOT NULL UNIQUE,
    gender          text,
    birth_date      date,
    language        text NOT NULL DEFAULT 'en',
    city            text,
    country         text,
    madhab          text NOT NULL DEFAULT 'hanafi',
    calc_method     text NOT NULL DEFAULT 'MWL',
    photo_url       text,
    onboarding_done boolean NOT NULL DEFAULT false,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE user_interests (
    user_id  uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    interest text NOT NULL,
    PRIMARY KEY (user_id, interest)
);

CREATE TABLE thoughts (
    id         uuid PRIMARY KEY,
    user_id    uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    text       text NOT NULL,
    like_count int NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_thoughts_feed ON thoughts (created_at DESC, id DESC);

CREATE TABLE thought_likes (
    thought_id uuid NOT NULL REFERENCES thoughts(id) ON DELETE CASCADE,
    user_id    uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (thought_id, user_id)
);

CREATE TABLE coach_conversations (
    id         uuid PRIMARY KEY,
    user_id    uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE coach_messages (
    id              uuid PRIMARY KEY,
    conversation_id uuid NOT NULL REFERENCES coach_conversations(id) ON DELETE CASCADE,
    role            text NOT NULL,
    content         text NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_coach_messages_conv ON coach_messages (conversation_id, created_at);

CREATE TABLE subjects (
    id          uuid PRIMARY KEY,
    title       text NOT NULL,
    description text NOT NULL DEFAULT '',
    order_index int NOT NULL DEFAULT 0
);

CREATE TABLE lessons (
    id          uuid PRIMARY KEY,
    subject_id  uuid NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    title       text NOT NULL,
    body        text NOT NULL,
    order_index int NOT NULL DEFAULT 0
);
CREATE INDEX idx_lessons_subject ON lessons (subject_id, order_index);

CREATE TABLE tracker_entries (
    id         uuid PRIMARY KEY,
    user_id    uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind       text NOT NULL,
    payload    jsonb NOT NULL DEFAULT '{}'::jsonb,
    entry_date date NOT NULL DEFAULT CURRENT_DATE,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_tracker_user_kind_date ON tracker_entries (user_id, kind, entry_date DESC);

CREATE TABLE gamification (
    user_id          uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    streak           int NOT NULL DEFAULT 0,
    level            int NOT NULL DEFAULT 1,
    xp               int NOT NULL DEFAULT 0,
    xp_next          int NOT NULL DEFAULT 100,
    last_active_date date
);

CREATE TABLE otp_challenges (
    id         uuid PRIMARY KEY,
    phone      text NOT NULL,
    code_hash  text NOT NULL,
    expires_at timestamptz NOT NULL,
    attempts   int NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_otp_phone ON otp_challenges (phone);
