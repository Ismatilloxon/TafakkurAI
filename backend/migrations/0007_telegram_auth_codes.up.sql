-- Replaces the deep-link/poll flow with a confirmation code the bot hands to the
-- user after they share their contact. The app never sees the Telegram account
-- until the code is typed in, so nothing is bound to a device that did not ask.
DROP TABLE IF EXISTS telegram_login_codes;

CREATE TABLE telegram_auth_codes (
    code        text PRIMARY KEY,
    telegram_id bigint NOT NULL,
    phone       text NOT NULL,
    username    text,
    first_name  text,
    last_name   text,
    consumed_at timestamptz,
    expires_at  timestamptz NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now()
);

-- One live code per Telegram account: pressing Start twice replaces the old code
-- instead of leaving a second valid one behind.
CREATE UNIQUE INDEX telegram_auth_codes_live_idx
    ON telegram_auth_codes (telegram_id) WHERE consumed_at IS NULL;
