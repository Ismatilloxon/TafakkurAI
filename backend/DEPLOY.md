# Deploy TafakkurAI on a strict-$0 starter stack

The repository's primary public deployment target is:

| Component | Service | Free allowance and important constraint |
| --- | --- | --- |
| Go API | Render Free web service | 0.1 CPU, 512 MB, and 750 free instance-hours per workspace each month. It spins down after 15 minutes without inbound traffic and can take about one minute to wake. |
| PostgreSQL | Neon Free | Use the pooled TLS connection string. Free compute scales to zero after an idle period and the free plan currently includes 0.5 GB of project storage. |
| Profile photos | Backblaze B2 | The first 10 GB of storage is free. TafakkurAI uses its S3-compatible API. Downloads/transactions also have free limits, so set account caps and monitor usage. |

These are provider allowances, not an unlimited free SLA. Keep only one Render Free service in
the workspace, stay inside every provider's quota, and configure usage caps/alerts where the
provider offers them. Optional Twilio and Anthropic integrations are not part of the $0 stack.

The official references used for this configuration are [Render Free services](https://render.com/docs/free),
[Render Blueprints](https://render.com/docs/blueprint-spec), [Render port binding](https://render.com/docs/web-services#port-binding),
[Neon pooled connections](https://neon.com/docs/connect/connection-pooling), [Neon scale to zero](https://neon.com/docs/introduction/scale-to-zero),
[Backblaze S3 endpoints](https://www.backblaze.com/docs/cloud-storage-call-the-s3-compatible-api),
[Backblaze bucket access](https://www.backblaze.com/docs/cloud-storage-create-and-manage-buckets), and
[Backblaze pricing](https://www.backblaze.com/cloud-storage/pricing).

The API applies the checked-in migrations on boot. Render's local filesystem is ephemeral, so all
durable state belongs in Neon or B2; do not add SQLite, uploaded files, or generated data to the
container filesystem.

## 1. Create the Neon database

1. Create a Neon Free project in a region close to Render's Frankfurt region.
2. Open **Connect**, enable **Pooled connection**, and copy the complete connection string.
3. Keep Neon's TLS parameters. A typical value has this shape:

   ```text
   postgres://USER:PASSWORD@ep-example-pooler.eu-central-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require
   ```

4. Save it for Render as `DATABASE_URL`. Never paste it into a repository file, issue, log, or
   screenshot.

The Blueprint deliberately does not create a Render Postgres database: Render's Free Postgres
instances expire after 30 days, whereas this deployment needs durable application data.

## 2. Create the Backblaze B2 media bucket

The current app downloads profile photos from the returned public object URL. Therefore the bucket
must be publicly readable, or `S3_PUBLIC_BASE` must point at another publicly readable CDN in front
of it. Upload, list, and delete operations still require the private application key.

1. Create a globally unique **Public** B2 bucket. Use lowercase letters, numbers, and hyphens only;
   do not put personal data in its name and avoid dots so HTTPS virtual-host URLs work.
2. Create a B2 application key restricted to only this bucket with read/write access. The backend
   needs `PutObject` and `DeleteObject`; it never needs an account-wide master key.
3. Copy the bucket's S3 endpoint and region. For a bucket named `tafakkur-media-example` in
   `us-east-005`, enter:

   ```text
   S3_ENDPOINT=https://s3.us-east-005.backblazeb2.com
   S3_REGION=us-east-005
   S3_BUCKET=tafakkur-media-example
   S3_ACCESS_KEY=<application key ID>
   S3_SECRET_KEY=<application key>
   S3_USE_PATH_STYLE=true
   S3_PUBLIC_BASE=https://tafakkur-media-example.s3.us-east-005.backblazeb2.com
   ```

`S3_PUBLIC_BASE` has no trailing slash. The backend appends an opaque
`avatars/<user-id>/<random-id>.<extension>` object key. The mobile downloader does not send the API
bearer token to B2.

Backblaze documents that activating a first public bucket can require a verified email plus either
payment history or a small fee credited back to the account. Check that account-level condition
before treating B2 as a no-card service. Usage beyond the published free allowances can incur cost;
set caps before inviting real users.

## 3. Create the Render service from the Blueprint

The repository-root [`render.yaml`](../render.yaml) is the source of truth. It selects Docker,
`plan: free`, `rootDir: backend`, `/healthz`, the copied `/app/migrations` directory, and a non-root
runtime image. It contains variable names only—never secret values.

1. Push the intended revision to a private or public Git provider repository that Render can read.
2. In Render choose **New → Blueprint**, connect that repository, and select `render.yaml`.
3. Fill every value marked `sync: false` with the Neon and B2 values above.
4. Let Render generate `JWT_SECRET`; do not replace it with the local development secret.
5. For the default service name, set both of these to the actual HTTPS URL shown by Render:

   ```text
   PUBLIC_BASE_URL=https://tafakkur-api.onrender.com
   CORS_ALLOWED_ORIGINS=https://tafakkur-api.onrender.com
   ```

   If Render changes the hostname because the name is unavailable, use the assigned hostname. Both
   values must be HTTPS origins with no path, query, fragment, credentials, or trailing content.

6. Deploy. A successful log contains `migrations applied` followed by
   `http server listening`. The service listens on Render's `PORT` (`10000` by default); the
   Blueprint also sets the compatible `HTTP_PORT=10000` override.

Render stores `sync: false` values only when the Blueprint is first created. If a later revision
adds another secret variable, add it manually in **Service → Environment** before redeploying.

## 4. Optional authentication and AI providers

The API boots without these integrations, but the corresponding server feature stays disabled:

| Feature | Environment variables | Cost note |
| --- | --- | --- |
| Telegram login | `TELEGRAM_BOT_TOKEN`, `TELEGRAM_WEBHOOK_BASE`, `TELEGRAM_WEBHOOK_SECRET` | Set the webhook base to the Render HTTPS origin. Use a random webhook secret. |
| Phone OTP | `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM` | Without all three, phone OTP fails closed; codes are not logged. Twilio is outside the strict-$0 stack. |
| Google browser login | `GOOGLE_OAUTH_CLIENT_ID`, `GOOGLE_OAUTH_CLIENT_SECRET` | Register `<PUBLIC_BASE_URL>/v1/auth/google/callback` exactly. |
| Native Google ID tokens | `GOOGLE_CLIENT_ID` | Comma-separated iOS/Android client IDs. |
| Apple sign-in | `APPLE_BUNDLE_ID` | The Blueprint sets the current bundle ID. |
| Cloud AI coach | `ANTHROPIC_API_KEY` and optional model variables | Omit this to keep the deployment strictly $0. On-device actions do not require it. |

For Telegram production delivery:

```text
TELEGRAM_WEBHOOK_BASE=https://tafakkur-api.onrender.com
TELEGRAM_WEBHOOK_SECRET=<independently generated random secret>
```

Do not run a local long-poller with the same Telegram bot token while production webhooks are
enabled; use a separate development bot.

## 5. Verify the deployment

The first request after idle can take about one minute while Render wakes the container. Wait for
the response instead of repeatedly submitting a login or mutation.

```bash
curl --fail --show-error --max-time 90 https://tafakkur-api.onrender.com/healthz
curl --fail --show-error --max-time 90 https://tafakkur-api.onrender.com/readyz
```

Expected bodies:

```json
{"status":"ok"}
{"status":"ready"}
```

`/healthz` proves the process is serving. `/readyz` also checks PostgreSQL. Then inspect Render logs
for migration or media-configuration errors without printing environment values.

The mobile client performs one bounded `GET /healthz` warm-up before interactive repository calls.
Only that safe GET is retried; the subsequent POST, PUT, PATCH, or DELETE is issued once. Offline
ActivityEvents remain queued with stable IDs and the sync scheduler can replay them idempotently.

## 6. Point iOS and Android at HTTPS

The base URL must end with `/` because Ktor resolves relative paths against it.

- iOS reads `TafakkurAPIBaseURL` from `iosApp/iosApp/Info.plist` (a
  `TAFAKKUR_API_BASE_URL` launch environment value can override it for development).
- Android passes `ApiConfig(baseUrl = ...)` from
  `androidApp/src/main/kotlin/com/example/tafakkurai/MainActivity.kt`.

Use the assigned Render URL:

```text
https://tafakkur-api.onrender.com/
```

Rebuild and reinstall the mobile app after changing it. Do not ship the local `.local`,
`localhost`, or Android-emulator address in a device build.

## Operations and rollback

- Use the Render **Logs** and **Events** pages for startup/runtime failures and rollbacks.
- Use Neon monitoring for storage/compute usage and database connection errors.
- Use Backblaze caps/alerts for storage, downloads, and API transactions.
- Back up user data and practice restore before calling this production-ready. A free deployment is
  not a backup policy.
- Rotate storage/provider credentials independently. Rotating `JWT_SECRET` invalidates every
  refresh/access token and signs out all users, so do it deliberately.
- If a migration fails, fix it forward and redeploy. Do not edit an already-applied migration.

`fly.toml` and `deploy.sh` remain only as a legacy paid Fly.io alternative. They are not used by
the strict-$0 Blueprint and should not be run for this deployment.
