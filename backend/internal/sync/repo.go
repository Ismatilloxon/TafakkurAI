package sync

import (
	"context"
	"fmt"
	"sync"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

// Repo is the only place that touches Postgres. Every write path here is bulk:
// one statement per strategy group per batch, never one statement per event.
type Repo struct {
	pool *pgxpool.Pool

	// Policies change rarely and are read on every batch, so they are cached with a
	// short TTL. A stale strategy for one minute is harmless — merging a row the old
	// way once cannot lose data, because the payload is still written in full.
	policyMu   sync.RWMutex
	policies   map[string]Strategy
	policyTill time.Time
}

func NewRepo(pool *pgxpool.Pool) *Repo { return &Repo{pool: pool} }

const policyTTL = time.Minute

// Policies returns entity → strategy. An entity with no row is StrategyLWW, which is
// what makes a brand-new feature work with nothing configured anywhere.
func (r *Repo) Policies(ctx context.Context) (map[string]Strategy, error) {
	r.policyMu.RLock()
	if time.Now().Before(r.policyTill) && r.policies != nil {
		p := r.policies
		r.policyMu.RUnlock()
		return p, nil
	}
	r.policyMu.RUnlock()

	rows, err := r.pool.Query(ctx, `SELECT entity, strategy FROM sync_policy`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	out := make(map[string]Strategy)
	for rows.Next() {
		var entity string
		var strategy Strategy
		if err := rows.Scan(&entity, &strategy); err != nil {
			return nil, err
		}
		if strategy.valid() {
			out[entity] = strategy
		}
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}

	r.policyMu.Lock()
	r.policies, r.policyTill = out, time.Now().Add(policyTTL)
	r.policyMu.Unlock()
	return out, nil
}

// Begin opens the single transaction a whole batch is applied in. One transaction per
// batch, never one per event: 100 events become one commit and one fsync.
func (r *Repo) Begin(ctx context.Context) (pgx.Tx, error) { return r.pool.Begin(ctx) }

// ClaimEvents inserts the batch's ids into the idempotency ledger and returns the set
// that was actually new. A replayed batch returns an empty set and applies nothing,
// which is what makes at-least-once delivery from the device safe.
func (r *Repo) ClaimEvents(ctx context.Context, tx pgx.Tx, userID uuid.UUID, events []Event) (map[string]bool, error) {
	ids := make([]string, 0, len(events))
	entities := make([]string, 0, len(events))
	for i := range events {
		ids = append(ids, events[i].ID)
		entities = append(entities, events[i].Entity)
	}

	rows, err := tx.Query(ctx, `
		INSERT INTO sync_event_log (event_id, user_id, entity)
		SELECT t.id::uuid, $1, t.entity
		FROM unnest($2::text[], $3::text[]) AS t(id, entity)
		ON CONFLICT (event_id) DO NOTHING
		RETURNING event_id`,
		userID, ids, entities)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	fresh := make(map[string]bool, len(events))
	for rows.Next() {
		var id uuid.UUID
		if err := rows.Scan(&id); err != nil {
			return nil, err
		}
		fresh[id.String()] = true
	}
	return fresh, rows.Err()
}

// applyKey identifies a row written by Apply, so results can be matched back to events.
type applyKey struct{ entity, entityID string }

// Apply bulk-upserts one strategy group into the projection.
//
// Shape of the statement, and why:
//
//   - unnest(...) turns N events into N rows inside ONE statement, so a 100-event
//     batch is one round trip and one index maintenance pass, not 100 of each.
//   - nextval() stamps a fresh cursor on every written row, so any device pulling
//     with `seq > cursor` sees it exactly once and in write order.
//   - the WHERE on DO UPDATE drops stale events: an event whose device clock predates
//     what is already stored is ignored rather than applied, which is last-write-wins
//     implemented where it cannot race.
//   - RETURNING tells us which rows survived that filter, so the client only marks
//     genuinely applied events as synced.
func (r *Repo) Apply(ctx context.Context, tx pgx.Tx, userID uuid.UUID, strategy Strategy, events []Event, now time.Time) (map[applyKey]bool, error) {
	if len(events) == 0 {
		return nil, nil
	}
	expr, ok := setPayloadExpr[strategy]
	if !ok {
		expr = setPayloadExpr[StrategyLWW]
	}

	n := len(events)
	features := make([]string, n)
	entities := make([]string, n)
	entityIDs := make([]string, n)
	payloads := make([]string, n)
	deletes := make([]bool, n)
	deviceTimes := make([]time.Time, n)
	zones := make([]string, n)

	for i := range events {
		e := &events[i]
		features[i] = e.Feature
		entities[i] = e.Entity
		entityIDs[i] = e.EntityID
		payloads[i] = e.payloadOrEmpty()
		deletes[i] = e.Operation == OpDelete
		deviceTimes[i] = e.deviceTimeUTC(now)
		zones[i] = e.Timezone
		if zones[i] == "" {
			zones[i] = "UTC"
		}
	}

	// `expr` is looked up from a fixed map keyed by a CHECK-constrained column; it is
	// never user input, so interpolating it is not an injection surface. Every value
	// below is a bound parameter.
	q := fmt.Sprintf(`
		INSERT INTO entity_state AS es
			(user_id, feature, entity, entity_id, payload, deleted, device_time, timezone, seq)
		SELECT $1, t.feature, t.entity, t.entity_id, t.payload::jsonb, t.deleted,
		       t.device_time, t.timezone, nextval('entity_state_seq')
		FROM unnest($2::text[], $3::text[], $4::text[], $5::text[],
		            $6::boolean[], $7::timestamptz[], $8::text[])
		     AS t(feature, entity, entity_id, payload, deleted, device_time, timezone)
		ON CONFLICT (user_id, entity, entity_id) DO UPDATE SET
			feature     = EXCLUDED.feature,
			payload     = CASE WHEN EXCLUDED.deleted THEN es.payload ELSE %s END,
			deleted     = EXCLUDED.deleted,
			device_time = EXCLUDED.device_time,
			timezone    = EXCLUDED.timezone,
			seq         = nextval('entity_state_seq'),
			version     = es.version + 1,
			updated_at  = now()
		WHERE EXCLUDED.device_time >= es.device_time
		RETURNING entity, entity_id`, expr)

	rows, err := tx.Query(ctx, q, userID, features, entities, entityIDs, payloads, deletes, deviceTimes, zones)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	written := make(map[applyKey]bool, n)
	for rows.Next() {
		var k applyKey
		if err := rows.Scan(&k.entity, &k.entityID); err != nil {
			return nil, err
		}
		written[k] = true
	}
	return written, rows.Err()
}

// Changes is the delta pull: everything this user's other devices changed after the
// cursor, in write order. One index range scan, no joins, no per-feature query.
func (r *Repo) Changes(ctx context.Context, userID uuid.UUID, cursor int64, limit int) ([]Change, int64, error) {
	if limit <= 0 || limit > MaxPullSize {
		limit = DefaultPullSize
	}
	rows, err := r.pool.Query(ctx, `
		SELECT feature, entity, entity_id, payload, deleted, seq, updated_at
		FROM entity_state
		WHERE user_id = $1 AND seq > $2
		ORDER BY seq
		LIMIT $3`, userID, cursor, limit)
	if err != nil {
		return nil, cursor, err
	}
	defer rows.Close()

	out := make([]Change, 0, limit)
	next := cursor
	for rows.Next() {
		var c Change
		if err := rows.Scan(&c.Feature, &c.Entity, &c.EntityID, &c.Payload, &c.Deleted, &c.Seq, &c.UpdatedAt); err != nil {
			return nil, cursor, err
		}
		out = append(out, c)
		next = c.Seq
	}
	return out, next, rows.Err()
}

// HeadSeq is the newest cursor for this user. A fresh device calls it to learn where
// the feed ends before deciding whether a full backfill is worth doing eagerly.
func (r *Repo) HeadSeq(ctx context.Context, userID uuid.UUID) (int64, error) {
	var seq *int64
	err := r.pool.QueryRow(ctx,
		`SELECT max(seq) FROM entity_state WHERE user_id = $1`, userID).Scan(&seq)
	if err != nil || seq == nil {
		return 0, err
	}
	return *seq, nil
}

// PruneEventLog drops idempotency rows older than the retention window. An event that
// old will never be retried by any client, so keeping it only costs storage.
func (r *Repo) PruneEventLog(ctx context.Context, olderThan time.Duration) (int64, error) {
	tag, err := r.pool.Exec(ctx,
		`DELETE FROM sync_event_log WHERE received_at < now() - $1::interval`,
		fmt.Sprintf("%d seconds", int(olderThan.Seconds())))
	return tag.RowsAffected(), err
}
