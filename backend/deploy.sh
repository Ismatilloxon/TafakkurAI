#!/usr/bin/env bash
#
# One-shot Fly.io deploy for the TafakkurAI API. The manual version of these steps,
# with the reasoning behind each service choice, is in DEPLOY.md.
#
# Prerequisites you have to do yourself, in a browser:
#   1. fly auth login                       (this script refuses to run without it)
#   2. A Neon Postgres project              -> DATABASE_URL
#   3. A Telegram bot from @BotFather       -> TELEGRAM_BOT_TOKEN
#      Without it /v1/auth/telegram/start returns 503 and nobody can sign in,
#      because the app's login screen leads with Telegram.
#   4. Optional, only for profile photos: a Cloudflare R2 bucket -> S3_* values
#
# Secrets are read interactively and never echoed, never written to a file, and
# never put in fly.toml.
set -euo pipefail

APP="$(awk -F'"' '/^app *=/{print $2}' fly.toml)"
REGION="$(awk -F'"' '/^primary_region *=/{print $2}' fly.toml)"
: "${APP:?could not read app name from fly.toml}"

command -v fly >/dev/null || { echo "flyctl not installed: brew install flyctl"; exit 1; }
fly auth whoami >/dev/null 2>&1 || { echo "Not logged in. Run: fly auth login"; exit 1; }
echo "Deploying '$APP' to '$REGION' as $(fly auth whoami)"

# --- app ---------------------------------------------------------------------
if fly apps list 2>/dev/null | awk '{print $1}' | grep -qx "$APP"; then
  echo "App '$APP' already exists, reusing it."
else
  echo "Creating app '$APP'…"
  fly apps create "$APP" --org personal
fi

# --- secrets -----------------------------------------------------------------
ask() { # ask VAR "prompt" [optional]
  local __var="$1" __prompt="$2" __optional="${3:-}" __val
  read -rsp "$__prompt: " __val; echo
  if [ -z "$__val" ] && [ -z "$__optional" ]; then
    echo "  required — aborting"; exit 1
  fi
  printf -v "$__var" '%s' "$__val"
}

echo
echo "Secrets (input is hidden; press Enter to skip the optional ones)"
ask DATABASE_URL       "  DATABASE_URL (Neon, keep ?sslmode=require)"
ask TELEGRAM_BOT_TOKEN "  TELEGRAM_BOT_TOKEN (@BotFather)"
ask ANTHROPIC_API_KEY  "  ANTHROPIC_API_KEY (AI coach, optional)" optional
ask S3_ENDPOINT        "  S3_ENDPOINT (R2, optional)"             optional
ask S3_ACCESS_KEY      "  S3_ACCESS_KEY (optional)"               optional
ask S3_SECRET_KEY      "  S3_SECRET_KEY (optional)"               optional
ask S3_PUBLIC_BASE     "  S3_PUBLIC_BASE (optional)"              optional

# Fresh secret rather than the dev one in .env, which lives in the working tree.
JWT_SECRET="$(openssl rand -hex 32)"

# The bot has to reach this host to confirm logins, and the app has to know where
# to send the user — both are just the app's own public URL.
BASE="https://${APP}.fly.dev"
TELEGRAM_WEBHOOK_SECRET="$(openssl rand -hex 16)"

set -- \
  DATABASE_URL="$DATABASE_URL" \
  JWT_SECRET="$JWT_SECRET" \
  TELEGRAM_BOT_TOKEN="$TELEGRAM_BOT_TOKEN" \
  TELEGRAM_WEBHOOK_BASE="$BASE" \
  TELEGRAM_WEBHOOK_SECRET="$TELEGRAM_WEBHOOK_SECRET" \
  APPLE_BUNDLE_ID="com.example.tafakkurai.TafakkurAI" \
  CORS_ALLOWED_ORIGINS="$BASE"
[ -n "$ANTHROPIC_API_KEY" ] && set -- "$@" ANTHROPIC_API_KEY="$ANTHROPIC_API_KEY"
[ -n "$S3_ENDPOINT" ]       && set -- "$@" S3_ENDPOINT="$S3_ENDPOINT" S3_ACCESS_KEY="$S3_ACCESS_KEY" \
                                          S3_SECRET_KEY="$S3_SECRET_KEY" S3_PUBLIC_BASE="$S3_PUBLIC_BASE"

echo "Setting $# secrets…"
fly secrets set --app "$APP" --stage "$@" >/dev/null
echo "  done (staged; they apply on the next deploy)"

# --- deploy ------------------------------------------------------------------
# --remote-only: this machine has no Docker, and Fly's builder is fine for a
# two-stage Go build.
echo
echo "Building and deploying…"
fly deploy --app "$APP" --remote-only

# --- verify ------------------------------------------------------------------
echo
echo "Verifying…"
for i in $(seq 1 30); do
  code=$(curl -s -o /dev/null -m 10 -w '%{http_code}' "$BASE/healthz" || true)
  [ "$code" = "200" ] && break
  sleep 5
done
echo "  GET $BASE/healthz  -> ${code:-no response}"
echo "  GET $BASE/readyz   -> $(curl -s -m 10 "$BASE/readyz" || echo 'no response')"
echo
echo "Next: point the apps at $BASE/ (trailing slash required) —"
echo "  shared/src/iosMain/kotlin/com/example/tafakkurai/MainViewController.kt"
echo "  androidApp/src/main/kotlin/com/example/tafakkurai/MainActivity.kt"
echo "Then: fly logs --app $APP   # look for \"migrations applied\""
