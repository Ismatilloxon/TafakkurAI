-- One row per "sign in with Telegram" attempt. The client creates it, the bot
-- approves it when the user presses Start, and the client redeems it exactly once.
CREATE TABLE telegram_login_codes (
    code           text PRIMARY KEY,
    telegram_id    bigint,
    username       text,
    first_name     text,
    last_name      text,
    approved_at    timestamptz,
    consumed_at    timestamptz,
    expires_at     timestamptz NOT NULL,
    created_at     timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX telegram_login_codes_expires_idx ON telegram_login_codes (expires_at);

-- Offset the bot has consumed, so a restart of the long-poller does not replay
-- old /start messages. Single row, id = 1.
CREATE TABLE telegram_bot_state (
    id          int PRIMARY KEY,
    last_update bigint NOT NULL DEFAULT 0
);
INSERT INTO telegram_bot_state (id, last_update) VALUES (1, 0);
