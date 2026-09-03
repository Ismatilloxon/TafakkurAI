#!/usr/bin/env bash
set -Eeuo pipefail

umask 077

readonly BACKEND_DIR="/opt/tafakkur/backend"
readonly ENV_FILE="${BACKEND_DIR}/.env.production"
readonly COMPOSE_FILE="${BACKEND_DIR}/compose.production.yml"
readonly BACKUP_DIR="/var/backups/tafakkur"
readonly RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-7}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "backup failed: production environment file is missing" >&2
  exit 1
fi
if [[ ! "$RETENTION_DAYS" =~ ^[1-9][0-9]*$ ]]; then
  echo "backup failed: BACKUP_RETENTION_DAYS must be a positive integer" >&2
  exit 1
fi

install -d -m 0700 "$BACKUP_DIR"

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
final_path="${BACKUP_DIR}/tafakkur-${timestamp}.dump"
temporary_path="$(mktemp "${BACKUP_DIR}/.tafakkur-${timestamp}.XXXXXX")"

cleanup() {
  if [[ -f "$temporary_path" ]]; then
    rm -f -- "$temporary_path"
  fi
}
trap cleanup EXIT

compose=(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE")

"${compose[@]}" exec -T postgres sh -ceu \
  'export PGPASSWORD="$POSTGRES_PASSWORD"; exec pg_dump --host=127.0.0.1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --format=custom --compress=9 --no-owner --no-privileges' \
  >"$temporary_path"

if [[ ! -s "$temporary_path" ]]; then
  echo "backup failed: pg_dump produced an empty archive" >&2
  exit 1
fi

chmod 0600 "$temporary_path"
mv -- "$temporary_path" "$final_path"

# The directory is fixed rather than caller-controlled so retention can never
# broaden into an unsafe recursive deletion target.
find "$BACKUP_DIR" -maxdepth 1 -type f -name 'tafakkur-*.dump' \
  -mtime "+${RETENTION_DAYS}" -delete

if [[ -n "${OCI_BACKUP_BUCKET:-}" ]]; then
  if ! command -v oci >/dev/null 2>&1; then
    echo "backup warning: OCI_BACKUP_BUCKET is set but OCI CLI is unavailable" >&2
    exit 1
  fi

  oci_args=(os object put
    --bucket-name "$OCI_BACKUP_BUCKET"
    --file "$final_path"
    --name "postgres/$(basename "$final_path")"
    --force
    --verify-checksum)
  if [[ -n "${OCI_BACKUP_NAMESPACE:-}" ]]; then
    oci_args+=(--namespace-name "$OCI_BACKUP_NAMESPACE")
  fi
  if [[ -n "${OCI_BACKUP_REGION:-}" ]]; then
    oci_args+=(--region "$OCI_BACKUP_REGION")
  fi
  if [[ "${OCI_CLI_AUTH:-instance_principal}" == "instance_principal" ]]; then
    oci_args+=(--auth instance_principal)
  fi

  oci "${oci_args[@]}" >/dev/null
fi

echo "backup completed: $(basename "$final_path")"
