# Go backend engineering rules

These rules apply under `backend/` and supplement the repository rules.

## Sources and priorities

The Go rules are based on Google's canonical Go style guide and supporting best practices:

- https://google.github.io/styleguide/go/guide
- https://google.github.io/styleguide/go/decisions
- https://google.github.io/styleguide/go/best-practices

Operational rules apply the AWS Well-Architected pillars and design principles:

- https://docs.aws.amazon.com/wellarchitected/latest/framework/the-pillars-of-the-framework.html
- https://docs.aws.amazon.com/wellarchitected/latest/framework/sec-design.html
- https://docs.aws.amazon.com/wellarchitected/latest/framework/rel-dp.html
- https://docs.aws.amazon.com/wellarchitected/latest/framework/oe-design-principles.html

When rules compete, protect correctness, security, user data, and operability first; then prefer
clarity, simplicity, maintainability, consistency, and measured performance.

## Architecture

- Keep one modular-monolith binary. A domain owns its tables, repository, service/use cases,
  handlers, and events. Other domains depend on small consumer-owned interfaces or application
  orchestration, never another domain's repository or SQL.
- Dependency direction is transport -> application -> domain <- infrastructure. Domain types and
  pure rules do not import `net/http`, pgx, AWS SDKs, environment config, or other domain repos.
- HTTP handlers decode/validate/authorize/map only. Business workflows live in application
  services. Repositories contain persistence only. SQL never appears in handlers.
- Cross-feature behavior is coordinated through a versioned activity-event application service.
  Use a database transaction or transactional outbox so the source write, idempotency ledger,
  XP award, and projections cannot partially succeed.
- Keep `context.Context` as the first parameter for request-scoped work. Propagate cancellation and
  deadlines to SQL and outbound HTTP. Never store a context in a struct.
- Define interfaces where they are consumed and only when substitution or a boundary is real.
  Return concrete types by default. Avoid `util`, `common`, and speculative generic abstractions.

## Go readability and correctness

- Run `gofmt`; use idiomatic MixedCaps names, short lowercase package names, and names that do not
  repeat their package. Comments explain why, invariants, units, ownership, and non-obvious risk.
- Prefer straightforward control flow and early error returns. Wrap errors with operation context
  using `%w`; use `errors.Is`/`errors.As`; never branch on error strings.
- Validate every external input at the boundary and again in domain constructors for invariants.
  Reject unknown JSON fields, limit request bodies, normalize dates/timezones explicitly, and use
  typed enums instead of unchecked strings where the set is closed.
- Never log tokens, OTPs, authorization headers, journal/health/finance payloads, or raw AI prompts.
  Use structured `slog` fields with request ID, operation, duration, outcome, and safe identifiers.
- Goroutines need explicit ownership, cancellation, bounded concurrency, and a tested shutdown path.
  Close response bodies and database rows immediately with `defer`; do not leak timers or tickers.
- Use UTC instants for storage and carry the user's IANA timezone/local date for streaks and plans.
  Never derive a user's daily streak from the server's UTC date.

## API, persistence, and integration events

- Keep public JSON stable and explicitly tagged. This KMP client uses `kotlinx.serialization`;
  compatibility changes require versioning or an additive migration.
- All commands accept an idempotency key. Enforce uniqueness per user and command type. A replay
  returns the original semantic result and never duplicates XP, plan items, habits, or posts.
- Use cursor/keyset pagination for unbounded lists. Cap limits and payload sizes. Avoid `SELECT *`,
  N+1 queries, unbounded scans, per-row network calls, and read-modify-write races.
- Mutating workflows that touch multiple rows use a transaction. Lock or atomically update counters;
  never implement XP as an unlocked read followed by a save.
- Migrations are forward-safe, deterministic, and reversible where data safety permits. Add indexes
  for real access paths and verify queries with `EXPLAIN (ANALYZE, BUFFERS)` before claiming a gain.
- The generic sync store is transport/state synchronization, not the product integration model.
  Meaningful `ActivityEvent` fields are validated and projected; opaque JSON alone is insufficient
  for today's plan, balance, habits, XP, quizzes, or auditability.

## AWS Well-Architected application rules

- Security: least privilege; strong identity; authorization on every owned resource; encryption in
  transit and at rest; secrets only from a secret store/environment; defense in depth; auditable
  changes; dependency and security tests in CI.
- Reliability: set explicit client/server/DB timeouts; make retries bounded with exponential backoff
  and jitter; retry only safe/idempotent work; degrade soft dependencies; expose liveness/readiness;
  test backup/restore and failure behavior.
- Operational excellence: emit actionable metrics for rate, errors, latency, saturation, queue age,
  sync conflicts, projection lag, and AI tool failures. Add runbooks for migrations and recovery.
- Performance efficiency: measure before optimizing; benchmark critical pure logic and hot handlers;
  size the pgx pool; batch writes; stream bounded responses; cache only with ownership and invalidation.
- Cost/sustainability: bound AI context/output, storage retention, background work, and high-cardinality
  telemetry. Prefer the modular monolith until measured scaling pressure justifies extraction.

## Verification gate

For each change run `gofmt`, focused unit tests, `go test ./...`, `go test -race ./...` where the
environment supports it, and `go vet ./...`. Repository/integration changes need real PostgreSQL
tests for authorization, idempotency, transaction rollback, concurrent updates, and pagination.

