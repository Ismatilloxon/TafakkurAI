-- One row per "sign in with Google" attempt. The app creates it and polls it;
-- Google's redirect back to /v1/auth/google/callback approves it. Mirrors
-- telegram_auth_codes: short-lived, single-use, and useless once consumed.
CREATE TABLE oauth_login_states (
    state         text PRIMARY KEY,
    provider      text NOT NULL,
    -- PKCE verifier. The exchange is done server-side with a client secret, so
    -- this is defence in depth against an intercepted authorization code.
    code_verifier text NOT NULL,
    user_id       uuid REFERENCES users(id) ON DELETE CASCADE,
    approved_at   timestamptz,
    consumed_at   timestamptz,
    expires_at    timestamptz NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX oauth_login_states_expires_idx ON oauth_login_states (expires_at);

-- Attaching a contact in the profile checks nobody else already holds it
-- (see user.Repo.SetPhone / SetEmail). Deliberately not UNIQUE: the same person
-- signing in with Apple and with Google is two rows with one email, and a
-- Telegram account and a phone-OTP account legitimately share a number.
CREATE INDEX users_phone_idx ON users (phone) WHERE phone IS NOT NULL;
CREATE INDEX users_email_lower_idx ON users (lower(email)) WHERE email IS NOT NULL;
