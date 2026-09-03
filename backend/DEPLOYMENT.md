# Oracle Cloud Always Free deployment

This is the production-like deployment path for TafakkurAI. It uses one Oracle Cloud
Always Free ARM VM and does not require a load balancer, managed database, Kubernetes,
NAT gateway, or a public application/database port.

```text
Internet :80/:443
        |
      Caddy
        |
  private Docker network
        |
      Go API ---- private Docker network ---- PostgreSQL
```

Only Caddy publishes host ports. The API listens on `8080` only inside Docker and
PostgreSQL listens on `5432` only inside its internal Docker network.

## Oracle allocation and network

- Home region: the tenancy's home region (currently `me-jeddah-1`).
- Shape: `VM.Standard.A1.Flex`, at most 2 OCPUs and 12 GB RAM across Always Free A1
  instances in this tenancy.
- Image: Ubuntu 24.04 ARM64.
- Boot/block storage: keep total tenancy use within the Always Free 200 GB allowance.
- Public subnet route: `0.0.0.0/0` to an Internet Gateway.
- Public IPv4: enabled on the primary VNIC.
- OCI ingress: TCP 80 and 443 from `0.0.0.0/0`; TCP 22 from the operator's current
  public IP `/32` only.
- Never open 5432, 8080, 9000, 9001, or an admin dashboard.

The VM does not use the wizard-created private subnet, NAT gateway, or Service Gateway.
Remove the unused NAT gateway and its route rather than relying on it.

Oracle can reclaim an Always Free A1 instance it considers idle. Oracle currently
describes an instance as reclaimable when CPU, network, and memory usage are all below
its published thresholds during a seven-day window. Monitor the tenancy notices and keep
tested backups outside the database volume.

## Host bootstrap

Run these commands on the trusted Ubuntu VM. They install only packages signed by the
Ubuntu archive; this deployment never pipes a downloaded script into a shell.

```bash
sudo apt-get update
sudo DEBIAN_FRONTEND=noninteractive apt-get install --yes --no-install-recommends \
  ca-certificates dnsutils docker.io docker-compose-v2
sudo systemctl enable --now docker
sudo install -d -o ubuntu -g ubuntu -m 0750 /opt/tafakkur/backend
sudo docker version
sudo docker compose version
```

Before every build, follow the repository build-safety gate and inspect Gradle and Xcode
executable phases for unexplained hooks. Do not build a source tree that contains an
unexpected executable hook.

## Transfer the backend

Transfer only `backend/`; never copy a local `.env`, SSH key, build cache, or Git metadata.
Replace the placeholders locally without putting a private key or host in chat:

```bash
COPYFILE_DISABLE=1 tar -C backend \
  --exclude='.env*' \
  --exclude='.git' \
  --exclude='._*' \
  --exclude='*.key' \
  --exclude='*.pem' \
  -czf - . | \
ssh -i /path/to/private-key ubuntu@SERVER_HOST \
  'tar -xzf - -C /opt/tafakkur/backend'
```

For later deploys, take a database backup before replacing the source tree.

## Production environment

Create `/opt/tafakkur/backend/.env.production` on the VM with mode `0600`. Generate
`POSTGRES_PASSWORD` and `JWT_SECRET` independently on the VM with a cryptographically
secure generator. `JWT_SECRET` must contain at least 32 characters. Never print or copy
their values into logs, commits, screenshots, documentation, or chat.

Required names:

```text
API_DOMAIN
ACME_EMAIL
POSTGRES_DB
POSTGRES_USER
POSTGRES_PASSWORD
APP_DB_USER
APP_DB_PASSWORD
JWT_SECRET
```

Use distinct lowercase/underscore identifiers for `POSTGRES_USER` and `APP_DB_USER`, and
generate both passwords as independent URL-safe hexadecimal strings of at least 32
characters. The short-lived `migrate` container receives the database-owner credential.
After every migration run it creates or rotates the application role, removes inherited
role memberships and any roles that could `SET ROLE` into it, reapplies current and future
table/sequence grants, revokes schema
creation, and denies access to `schema_migrations`. The role is marked as TafakkurAI-managed;
an unrelated or PostgreSQL-reserved existing role is rejected before it can be changed.
This reconciliation also runs for an existing PostgreSQL volume. The long-running API
receives only that restricted credential and cannot create or drop schema objects.

`POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD` are initialization values and must
be treated as immutable after the `postgres_data` volume is created. Changing the Compose
environment alone does not rotate the stored PostgreSQL owner password. A reviewed owner
rotation is a two-phase operation: first authenticate with the current credential and run
PostgreSQL's interactive `\password` command for the exact owner role; then update
`.env.production` and immediately recreate PostgreSQL-dependent containers. Take and verify
a backup first. `APP_DB_PASSWORD` is different: the one-shot migrator safely rotates it on
every reviewed deployment.

The Compose file sets `ENV=production`, `PORT=8080`, the internal `DATABASE_URL`,
`AUTO_MIGRATE=false`, and `PUBLIC_BASE_URL=https://${API_DOMAIN}`. Optional integrations
stay disabled when their complete credential set is absent. See `.env.example` for every
optional variable.

Native iOS and Android clients normally send no browser `Origin`; keep
`CORS_ALLOWED_ORIGINS` empty unless an owned web origin is actually deployed. Never use
`*` in production.

## DNS and HTTPS

Point an owned hostname, or one DuckDNS hostname, at the VM's public IPv4 address before
starting Caddy. Put only the hostname in `API_DOMAIN`, with no scheme, path, query, or
trailing slash. Caddy obtains a public Let's Encrypt certificate and redirects HTTP to
HTTPS. Do not use a self-signed certificate and do not weaken iOS ATS or Android network
security.

Confirm DNS from two resolvers before certificate issuance:

```bash
dig +short API_DOMAIN @1.1.1.1
dig +short API_DOMAIN @8.8.8.8
```

Both results must be the VM public IPv4 address.

## Build and start

From `/opt/tafakkur/backend`:

```bash
chmod 0600 .env.production
sudo docker compose --env-file .env.production -f compose.production.yml config --quiet
sudo docker compose --env-file .env.production -f compose.production.yml \
  run --rm --no-deps caddy caddy adapt --validate \
  --config /etc/caddy/Caddyfile --adapter caddyfile
sudo chmod 0755 ops/deploy-production.sh
sudo ops/deploy-production.sh
sudo docker compose --env-file .env.production -f compose.production.yml ps
```

The one-shot migration container applies all unapplied `*.up.sql` files, reconciles the
restricted API role, and is removed before the API starts. A PostgreSQL session advisory
lock spans both operations so concurrent deploys cannot reconcile the role simultaneously.
The deployment script also holds a host-level lock across backup, build, migration, and API
restart, preventing two deployments with different runtime passwords from racing.
Each migration runs in its own transaction and its SHA-256 checksum is recorded. Never edit
an applied migration; add a new forward migration instead. A deployment fails closed if
the API role owns database objects, because owner rights cannot be safely reduced with
grants alone.

Production base images are pinned by multi-architecture manifest digest. Dependency
updates are explicit repository changes: review the new upstream tag and ARM64 manifest,
update its digest, rebuild, and rerun every verification check.

## Backups

Install the checked-in daily backup service:

```bash
sudo chmod 0755 /opt/tafakkur/backend/ops/backup-postgres.sh
sudo install -d -m 0700 /var/backups/tafakkur
sudo install -m 0644 ops/tafakkur-backup.service /etc/systemd/system/tafakkur-backup.service
sudo install -m 0644 ops/tafakkur-backup.timer /etc/systemd/system/tafakkur-backup.timer
sudo systemctl daemon-reload
sudo systemctl enable --now tafakkur-backup.timer
sudo systemctl start tafakkur-backup.service
sudo systemctl status --no-pager tafakkur-backup.timer
```

Backups are private, compressed custom-format archives under
`/var/backups/tafakkur`, retained for seven days by default. Configure the optional
`/etc/tafakkur/backup.env` only when a private OCI Object Storage bucket and an instance
principal policy are available. Do not put OCI user API keys on the VM.

Test an archive without overwriting production data by restoring into a temporary
database, validating it, and deleting that temporary database:

```bash
cd /opt/tafakkur/backend
COMPOSE='sudo docker compose --env-file .env.production -f compose.production.yml'
$COMPOSE exec -T postgres sh -ceu \
  'export PGPASSWORD="$POSTGRES_PASSWORD"; createdb --host=127.0.0.1 --username "$POSTGRES_USER" tafakkur_restore_test'
$COMPOSE exec -T postgres sh -ceu \
  'export PGPASSWORD="$POSTGRES_PASSWORD"; exec pg_restore --host=127.0.0.1 --username "$POSTGRES_USER" --dbname=tafakkur_restore_test --no-owner --no-privileges' \
  < /path/to/backup.dump
$COMPOSE exec -T postgres sh -ceu \
  'export PGPASSWORD="$POSTGRES_PASSWORD"; psql --host=127.0.0.1 --username "$POSTGRES_USER" --dbname=tafakkur_restore_test -c "SELECT count(*) FROM schema_migrations;"'
$COMPOSE exec -T postgres sh -ceu \
  'export PGPASSWORD="$POSTGRES_PASSWORD"; dropdb --host=127.0.0.1 --username "$POSTGRES_USER" tafakkur_restore_test'
```

For a real disaster restore, stop the API, take a final copy of the damaged database,
restore into a newly created database, verify migrations and row counts, then switch the
application to it. Never run `--clean` against production without an explicit reviewed
restore plan.

## Verification

Do not claim success until all checks pass:

```bash
curl --fail --show-error --max-time 15 https://API_DOMAIN/health
curl --fail --show-error --max-time 15 https://API_DOMAIN/ready
curl --silent --show-error --output /dev/null --write-out '%{http_code}\n' \
  -H 'Authorization: Bearer invalid' https://API_DOMAIN/v1/me
curl --silent --show-error --output /dev/null --write-out '%{http_code} %{redirect_url}\n' \
  http://API_DOMAIN/health
```

Expected results are `{"status":"ok"}`, `{"status":"ready"}`, HTTP 401 for the
invalid token, and an HTTP-to-HTTPS redirect. Also verify a harmless database write/read
inside a transaction:

```bash
printf '%s\n' "BEGIN; CREATE TEMP TABLE deployment_probe(v text); INSERT INTO deployment_probe VALUES ('ok'); SELECT v FROM deployment_probe; ROLLBACK;" | \
  sudo docker compose --env-file .env.production -f compose.production.yml exec -T postgres \
  sh -ceu 'export PGPASSWORD="$POSTGRES_PASSWORD"; exec psql --host=127.0.0.1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -v ON_ERROR_STOP=1'
```

Then restart the API and require readiness to recover:

```bash
sudo docker compose --env-file .env.production -f compose.production.yml restart api
sudo docker compose --env-file .env.production -f compose.production.yml ps
sudo docker compose --env-file .env.production -f compose.production.yml logs --tail=100 api caddy
```

Logs must show migration completion and normal startup without secret values.

## Routine operations

```bash
# Status
sudo docker compose --env-file .env.production -f compose.production.yml ps

# Logs
sudo docker compose --env-file .env.production -f compose.production.yml logs --tail=200 api caddy postgres

# Restart
sudo docker compose --env-file .env.production -f compose.production.yml restart api

# Deploy a reviewed source update
sudo ops/deploy-production.sh

# Immediate backup
sudo systemctl start tafakkur-backup.service
sudo journalctl -u tafakkur-backup.service --since today --no-pager
```

Keep the previous reviewed source and image until the new HTTPS, readiness, migration,
restart, and database checks pass. Application rollback is a source/image rollback;
database schema rollback is fix-forward unless a separately reviewed data-safe down
migration exists.
