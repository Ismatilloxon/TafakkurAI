# TafakkurAI Backend

Production-grade Go service for **TafakkurAI**, an Islamic reflection + personal-growth app (KMP/Compose Android + iOS client over HTTPS/JSON).

Built as a **modular monolith**: one binary, one Postgres database, clean domain boundaries under `internal/<domain>` so any module can later be extracted into its own service without a rewrite.

---

## Build safety

Follow the repository's `AGENTS.md` gate before every build: inspect Gradle and Xcode executable
phases, reject unexplained hooks, and never download and execute build-time payloads. Keep all
runtime secrets in environment variables or the deployment provider's secret store.

---

## Architecture

```
cmd/api/main.go        single entrypoint: config → pool → migrations → modules → router → serve
internal/
  config/     typed env config
  platform/   pgx pool, migration runner, HTTP/JSON/error helpers, ctx, request helpers
  ratelimit/  in-memory token-bucket limiter
  server/     chi router, middleware, route mounting
  auth/       Google/Apple JWKS verify, phone OTP, JWT issue/verify
  user/       users + profiles + interests (owns those tables)
  prayer/     astronomical prayer-time calc + tracker endpoint
  feed/       thoughts: posts, feed, likes
  coach/      Claude streaming proxy (SSE) + conversation persistence
  learn/      subjects + lessons
  tracker/    generic jsonb tracker entries
  gamify/     streak/level/XP engine
  media/      S3/MinIO image upload
migrations/   forward-only SQL applied by the app's checksum-tracked runner
queries/      sqlc queries (typed-query path; repos currently use pgx directly)
```

**Boundary rules enforced:** each domain exposes a `Service` interface; other modules depend on the interface, never on another domain's repo or SQL. No cross-context DB joins — e.g. the feed fetches post authors via `user.Service.PublicProfiles`, not a join on `profiles`.

---

## Prerequisites

- Go 1.23+
- Docker + Docker Compose (for the one-command stack)
- Optional: `sqlc`, `golangci-lint`

## Quick start (Docker)

```bash
cp .env.example .env          # optionally set ANTHROPIC_API_KEY, GOOGLE_CLIENT_ID, APPLE_BUNDLE_ID
docker compose up --build      # brings up postgres + minio (+ bucket) + api
```

- API: http://localhost:8080  · health: `/healthz`, `/readyz`
- The API **auto-applies migrations and the subjects/lessons seed on boot**.
- MinIO console: http://localhost:9001 (minioadmin / minioadmin)

## Local dev (without the API container)

```bash
go mod tidy
docker compose up -d postgres minio   # deps only
make run                              # runs migrations then serves on :8080
make test                             # unit + integration (integration needs Docker)
```

## Public deployment

The primary production-like path is one Oracle Cloud Always Free ARM64 VM running Caddy, this API,
and PostgreSQL on private Docker networks. See [`DEPLOYMENT.md`](DEPLOYMENT.md) for the exact
strict-$0 allocation, firewall, DNS/HTTPS, migration, backup, verification, and operations steps.
[`DEPLOY.md`](DEPLOY.md) remains a separate Render/Neon hosted alternative.

---

## Configuration

All config comes from env vars (`internal/config`). Key ones:

| Var | Default | Notes |
|-----|---------|-------|
| `PORT` / `HTTP_PORT` | `8080` | Uses host-standard `PORT` first; `HTTP_PORT` is a compatibility fallback |
| `DATABASE_URL` | — (required) | Postgres DSN |
| `JWT_SECRET` | — (required) | HS256 signing secret — set a long random value |
| `PUBLIC_BASE_URL` | local API origin | Required HTTPS origin in production; used for OAuth callbacks |
| `ACCESS_TOKEN_TTL` / `REFRESH_TOKEN_TTL` | `15m` / `720h` | token lifetimes |
| `GOOGLE_CLIENT_ID` | — | Google ID-token `aud` (mobile OAuth client id) |
| `APPLE_BUNDLE_ID` | — | Apple identity-token `aud` |
| `TWILIO_ACCOUNT_SID` / `_AUTH_TOKEN` / `_FROM` | — | SMS for OTP; if any are blank, phone OTP is disabled and codes are not logged |
| `ANTHROPIC_API_KEY` | — | Claude key, server-side only |
| `CLAUDE_MODEL` | `claude-sonnet-5` | chat model; `CLAUDE_DEEP_MODEL` for a deeper option |
| `S3_ENDPOINT`/`S3_BUCKET`/`S3_ACCESS_KEY`/`S3_SECRET_KEY` | — | MinIO/S3; if blank, photo upload → 503 |
| `S3_PUBLIC_BASE` | endpoint + bucket | Publicly readable media base URL; required for device photo restoration |
| `RATE_AUTH_PER_MIN` / `RATE_COACH_PER_MIN` / `RATE_POST_PER_MIN` | `20`/`15`/`30` | token-bucket limits |

See `.env.example` for the full list.

---

## Authentication

`POST /v1/auth/session` verifies the provider identity, upserts the user + profile (idempotent on `(provider, provider_subject)`), and returns tokens.

- **google** — `id_token` verified against Google JWKS (`aud = GOOGLE_CLIENT_ID`, issuer + expiry checked).
- **apple** — `id_token` verified against Apple JWKS (`aud = APPLE_BUNDLE_ID`).
- **phone** — two steps: `POST /v1/auth/otp/start { phone }` → `{ verification_id }`; then `POST /v1/auth/session { provider:"phone", verification_id, otp_code }`. Codes are bcrypt-hashed, expire (`OTP_TTL`), and are attempt-limited.

All `/v1/**` except `/v1/auth/*` require `Authorization: Bearer <access_token>`.

> **Note:** the spec's `users` table gained a `provider_subject` column so identity is keyed on the stable provider subject (`sub`) rather than a mutable email.

---

## API reference (all under `/v1`, JSON, snake_case)

### Auth
| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/auth/otp/start` | `{ phone }` | `{ verification_id }` |
| POST | `/auth/session` | `{ provider, id_token?, phone?, otp_code?, verification_id? }` | `{ access_token, refresh_token, token_type, expires_in, user, needs_onboarding }` |
| POST | `/auth/refresh` | `{ refresh_token }` | `{ access_token, token_type, expires_in }` |
| POST | `/auth/logout` | — | `{ ok: true }` |

### Profile
| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/me` | — | `{ user: UserProfile, gamification }` |
| PUT | `/me/profile` | `{ display_name?, handle?, gender?, birth_date?, language? }` | `UserProfile` |
| PUT | `/me/interests` | `{ interests: [] }` | `UserProfile` |
| PUT | `/me/prayer-settings` | `{ city?, country?, madhab, calc_method }` | `UserProfile` |
| POST | `/me/onboarding/complete` | — | `UserProfile` |
| POST | `/me/photo` | multipart `photo` | `{ url }` |

`UserProfile` = `{ uid, display_name, email, phone, photo_url, provider, handle, gender, birth_date, city, country, language_tag, madhab, calc_method, interests, onboarding_complete }` — matches the KMP client model.

### Prayer
| Method | Path | Query / Body | Response |
|---|---|---|---|
| GET | `/prayer/times` | `?lat=&lng=&date=&method=&madhab=&tz=` | `{ date, latitude, longitude, method, madhab, times:[{name,time}], next:{name,time,seconds_remaining} }` |
| POST | `/prayer/track` | `{ prayer, date? }` | `{ ok, gamification }` |

`lat`/`lng` required; `method`/`madhab` fall back to the saved profile; `tz` is an optional IANA name (else derived from longitude). Asr uses shadow factor 2 for Hanafi, 1 otherwise.

### Coach (Murabbiy)
| Method | Path | Body / Query | Response |
|---|---|---|---|
| POST | `/coach/message` | `{ conversation_id?, message }` | **SSE stream** |
| GET | `/coach/history` | `?conversation_id=&cursor=&limit=` | `{ conversation_id, messages:[{id,role,content,created_at}], next_cursor }` |

SSE frames: `event: message` `data: {"type":"delta","text":"..."}` per token; `event: done` `data: {"type":"done","conversation_id":"..."}` at the end; `event: error` `data: {"message":"..."}` on failure. Both user and assistant messages are persisted.

### Feed (Thoughts)
| Method | Path | Body / Query | Response |
|---|---|---|---|
| GET | `/thoughts` | `?cursor=&limit=` | `{ posts:[SocialPost], next_cursor }` |
| POST | `/thoughts` | `{ text }` | `SocialPost` |
| POST | `/thoughts/{id}/like` | — | `{ liked, like_count }` |

`SocialPost` = `{ id, text, author_id, author_name, author_handle, author_photo_url, like_count, liked_by_me, created_at }`.

### Learn
| Method | Path | Response |
|---|---|---|
| GET | `/subjects` | `{ subjects:[{id,title,description,order_index}] }` |
| GET | `/subjects/{id}/lessons` | `{ lessons:[{id,subject_id,title,body,order_index}] }` |
| GET | `/lessons/{id}` | `Lesson` |
| POST | `/lessons/{id}/complete` | `{ ok, gamification }` (awards XP) |

### Trackers
| Method | Path | Body / Query | Response |
|---|---|---|---|
| POST | `/trackers/{kind}` | `{ entry_date?, payload:{} }` | `Entry` |
| GET | `/trackers/{kind}` | `?from=&to=` | `{ entries:[Entry] }` |

`kind` ∈ prayer, journal, gratitude, quran, dhikr, sleep, sport, focus, productivity, finance, career.

### Errors
Every error uses one envelope: `{ "error": { "code", "message" } }` with the appropriate HTTP status.

---

## Gamification

`gamify.Award(userID, event)` is called server-side by qualifying actions (track prayer, publish thought, complete lesson, add tracker entry). It updates streak (consecutive active days), XP, and level (`xp_next = 100 + (level-1)*50`). `GET /me` returns the current values. The engine (`internal/gamify/engine.go`) is pure and unit-tested.

## Testing

```bash
make test
```
- **Unit:** `gamify` engine, `prayer` calc (Tashkent).
- **Integration:** `testcontainers-go` spins up real Postgres for auth upsert, profile update, feed, and coach persistence. Requires a working Docker daemon.

---

## How to split a module into a microservice later

The boundaries are already drawn so extraction is mechanical — do it only when a real pressure appears (e.g. the AI coach needs independent scaling):

1. **Pick the module** (say `coach/`). It already talks to the rest of the app only through interfaces (`gamify.Service`, `user.Service`) and owns its own tables (`coach_conversations`, `coach_messages`).
2. **Give it its own `main.go`** — copy the construction block from `cmd/api/main.go` for that module and stand up its own HTTP server + Postgres pool. The module code moves unchanged.
3. **Replace in-process interface calls with a client.** Where the monolith injected a concrete `user.Service`, inject an HTTP/gRPC client that implements the same interface. Because callers depend on the *interface*, nothing else changes.
4. **Split the data.** The module already avoids cross-context joins, so its tables move to their own database cleanly. Cross-context reads (e.g. author profiles for the feed) already go through a service call, which becomes a network call.
5. **Move the routes.** Unmount the module in `server/` and put its routes behind your gateway/ingress.

Nothing in the other modules needs a rewrite because none of them ever imported the extracted module's repo or SQL.
