#!/usr/bin/env bash
set -Eeuo pipefail

umask 077

readonly BACKEND_DIR="/opt/tafakkur/backend"
readonly ENV_FILE="${BACKEND_DIR}/.env.production"
readonly COMPOSE_FILE="${BACKEND_DIR}/compose.production.yml"
readonly LOCK_FILE="/run/lock/tafakkur-deploy.lock"

if ((EUID != 0)); then
  echo "deployment failed: run this script with sudo" >&2
  exit 1
fi
if [[ ! -f "$ENV_FILE" ]]; then
  echo "deployment failed: production environment file is missing" >&2
  exit 1
fi

command -v docker >/dev/null
command -v flock >/dev/null

exec 9>"$LOCK_FILE"
if ! flock --wait 600 9; then
  echo "deployment failed: another deployment still holds the host lock" >&2
  exit 1
fi

cd "$BACKEND_DIR"
compose=(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE")

"${compose[@]}" config --quiet </dev/null
"${compose[@]}" run --rm --no-deps caddy \
  caddy adapt --validate --config /etc/caddy/Caddyfile --adapter caddyfile \
  </dev/null >/dev/null

if "${compose[@]}" ps --status running --services postgres </dev/null | grep -qx postgres && \
   systemctl cat tafakkur-backup.service >/dev/null 2>&1; then
  systemctl start tafakkur-backup.service
fi

"${compose[@]}" build --pull api </dev/null
"${compose[@]}" up -d --wait postgres </dev/null
"${compose[@]}" run --rm --no-deps migrate </dev/null
"${compose[@]}" up -d --no-deps --wait api </dev/null
"${compose[@]}" up -d --no-deps --force-recreate --wait caddy </dev/null

# A run --rm deployment leaves no migrator behind. Remove an older stopped
# service container as well so its privileged environment cannot be inspected.
"${compose[@]}" rm --force migrate </dev/null
"${compose[@]}" ps </dev/null
