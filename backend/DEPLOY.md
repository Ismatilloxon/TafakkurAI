# Deploying the TafakkurAI API

Target stack:

| Piece | Service | Notes |
| --- | --- | --- |
| Go API | Fly.io | Always-on machine, ~$2/month, no cold start |
| Postgres | Neon | Free tier, 0.5 GB, does not expire |
| Media storage | Cloudflare R2 | Free tier, 10 GB, S3-compatible |

The API applies its own migrations on boot, so there is no separate migrate step.

---

## 1. Postgres on Neon

1. Create a project at <https://neon.tech>. Pick a region close to `fra`
   (Frankfurt) so the API is not talking across an ocean.
2. Copy the **pooled** connection string. It looks like:

   ```
   postgres://USER:PASSWORD@ep-xxx-pooler.eu-central-1.aws.neon.tech/tafakkur?sslmode=require
   ```

3. Keep `sslmode=require`. Neon refuses plaintext connections, and the local
   `sslmode=disable` value will fail.

## 2. Media storage on Cloudflare R2

1. In the Cloudflare dashboard: **R2 → Create bucket**, name it `tafakkur-media`.
2. **Settings → Public access → r2.dev subdomain → Enable.** Copy the public URL;
   it becomes `S3_PUBLIC_BASE`.
3. **Manage R2 API Tokens → Create API token**, permission *Object Read & Write*,
   scoped to that bucket. Copy the access key id and secret — the secret is shown
   once.
4. The S3 endpoint is `https://<ACCOUNT_ID>.r2.cloudflarestorage.com` (account id
   is on the R2 overview page).

## 3. Fly.io

Install the CLI and log in:

```bash
brew install flyctl
fly auth signup   # or: fly auth login
```

Create the app without deploying yet (`fly.toml` already exists, so answer "no"
when it offers to overwrite it, and decline the Postgres/Redis offers — we use
Neon):

```bash
cd backend
fly launch --no-deploy --copy-config --name tafakkur-api --region fra
```

If the name `tafakkur-api` is taken, pick another and update `app` in
`fly.toml` to match.

### Secrets

Everything sensitive goes through `fly secrets`, never into `fly.toml`.

Generate a fresh JWT secret — do **not** reuse the one in `backend/.env`, that
file is checked into the working tree and is only meant for local dev:

```bash
openssl rand -hex 32
```

Then:

```bash
fly secrets set \
  DATABASE_URL="postgres://USER:PASSWORD@ep-xxx-pooler.eu-central-1.aws.neon.tech/tafakkur?sslmode=require" \
  JWT_SECRET="<output of openssl rand -hex 32>" \
  S3_ENDPOINT="https://<ACCOUNT_ID>.r2.cloudflarestorage.com" \
  S3_ACCESS_KEY="<r2 access key id>" \
  S3_SECRET_KEY="<r2 secret access key>" \
  S3_PUBLIC_BASE="https://pub-xxxx.r2.dev/tafakkur-media"
```

Optional, add when you actually need the feature:

```bash
fly secrets set ANTHROPIC_API_KEY="sk-ant-..."          # AI coach
fly secrets set GOOGLE_CLIENT_ID="....apps.googleusercontent.com"
fly secrets set APPLE_BUNDLE_ID="com.example.tafakkurai.TafakkurAI"
fly secrets set TWILIO_ACCOUNT_SID="AC..." TWILIO_AUTH_TOKEN="..." TWILIO_FROM="+1..."
```

Without Twilio the API falls back to the dev OTP sender, which prints login
codes into the logs. Fine for testing, not for real users.

### Deploy

```bash
fly deploy
```

Verify:

```bash
curl https://tafakkur-api.fly.dev/healthz     # -> {"status":"ok"}
fly logs                                       # look for "migrations applied"
```

## 4. Point the apps at it

- iOS: `shared/src/iosMain/kotlin/com/example/tafakkurai/MainViewController.kt`
- Android: wherever `initKoin` is called with an `ApiConfig`

Set `baseUrl = "https://tafakkur-api.fly.dev/"` — trailing slash required, see
`ApiConfig.kt`.

Once both platforms point at HTTPS you can drop the dev-only local-network
escape hatches: `NSAppTransportSecurity` in `iosApp/iosApp/Info.plist`, and
`usesCleartextTraffic` in the Android manifest if it is set there.

## Operations

```bash
fly logs                  # stream logs
fly status                # machine state
fly secrets list          # names only, values are write-only
fly ssh console           # shell into the machine
fly scale memory 1024     # if it starts OOMing
```

### Cost

One `shared-cpu-1x` / 512 MB machine running 24/7 is roughly $2/month. To cut
that to near zero at the price of a cold start on the first request, set in
`fly.toml`:

```toml
auto_stop_machines = "suspend"
min_machines_running = 0
```

Suspend resumes far faster than a cold boot, but the first request after idle
still waits.

### Rolling the JWT secret

Changing `JWT_SECRET` invalidates every issued access and refresh token — all
users are logged out and must sign in again. Do it deliberately, not as part of
a routine deploy.

## Telegram sign-in

The app authenticates through a Telegram bot: no phone number, no SMS cost, and
one round trip. Flow:

1. `POST /v1/auth/telegram/start` → `{code, deep_link}` (code lives `TELEGRAM_LOGIN_TTL`, default 10m).
2. The app opens `https://t.me/<bot>?start=<code>`; the user presses **Start**.
3. The bot binds that Telegram account to the code.
4. `POST /v1/auth/telegram/poll {code}` → `{"status":"pending"}` until approved,
   then `{"status":"authorized","session":{access_token, refresh_token, ...}}`.

The code is single-use and dies on expiry, so a leaked link is worthless minutes
later. After sign-in the app only ever uses the JWT pair — Telegram is not
contacted again.

### Creating the bot

1. In Telegram open **@BotFather** → `/newbot` → pick a name and a `..._bot` username.
2. Copy the token it prints into `TELEGRAM_BOT_TOKEN` (`.env` locally, `fly secrets set` in prod).
3. Optional polish in BotFather: `/setdescription`, `/setuserpic`.

The server resolves the bot's `@username` itself via `getMe`, so no second variable.

### Local dev vs production

- **Local** — leave `TELEGRAM_WEBHOOK_BASE` empty. The server deletes any webhook
  and long-polls `getUpdates`; the consumed offset is persisted, so a restart
  never replays an old `/start`.
- **Production** — set `TELEGRAM_WEBHOOK_BASE=https://<app>.fly.dev` and a random
  `TELEGRAM_WEBHOOK_SECRET`. The server registers the webhook at boot and rejects
  any POST to `/telegram/webhook` without the matching secret-token header.

Only one bot may be attached to one delivery mode at a time — running a local
long-poller against the same token as production steals its updates. Use a
separate dev bot.
