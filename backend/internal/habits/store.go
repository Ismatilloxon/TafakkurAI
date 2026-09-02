package habits

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
)

// Store is this package's whole database surface: reads and writes against the shared
// `entity_state` projection, scoped by the four habit `entity` names.
//
// It intentionally reuses the sync platform's storage rather than owning tables. The
// write statement below is the same shape as sync.Repo.Apply — same conflict target, same
// `nextval` cursor stamp, same device-clock guard — because a habit written by this
// package must be indistinguishable from one written by the phone. If it were not, a
// device pulling deltas would either miss the row (no fresh seq) or overwrite it with
// stale local state (no clock guard).
type storeDB interface {
	Exec(context.Context, string, ...any) (pgconn.CommandTag, error)
	Query(context.Context, string, ...any) (pgx.Rows, error)
	QueryRow(context.Context, string, ...any) pgx.Row
}

type Store struct {
	pool *pgxpool.Pool
	db   storeDB
}

func NewStore(pool *pgxpool.Pool) *Store { return &Store{pool: pool, db: pool} }

func (s *Store) begin(ctx context.Context) (pgx.Tx, error) {
	return s.pool.BeginTx(ctx, pgx.TxOptions{})
}

func (s *Store) withTx(tx pgx.Tx) *Store {
	return &Store{pool: s.pool, db: tx}
}

// mergeExpr is the payload expression used when a row already exists, per entity. It
// mirrors the rows migration 0011 writes into sync_policy: keeping the two in step is
// what stops a REST write and a synced write from converging differently.
//
// `habit`, `habitEntry` and `habitNote` are written whole by this package (read, patch,
// write back — see patch below), so plain replacement is correct and preserves any field
// a newer client added, because it was carried through the read. `habitAchievement` uses
// sync_max so an unlock can never be reversed by an out-of-order write.
var mergeExpr = map[string]string{
	EntityHabit:       "EXCLUDED.payload",
	EntityEntry:       "EXCLUDED.payload",
	EntityNote:        "EXCLUDED.payload",
	EntityAchievement: "sync_max(es.payload, EXCLUDED.payload)",
}

// ---------------------------------------------------------------------------
// Reads
// ---------------------------------------------------------------------------

type rawRow struct {
	EntityID string
	Payload  json.RawMessage
}

func (s *Store) query(ctx context.Context, sql string, args ...any) ([]rawRow, error) {
	rows, err := s.db.Query(ctx, sql, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := make([]rawRow, 0, 32)
	for rows.Next() {
		var r rawRow
		if err := rows.Scan(&r.EntityID, &r.Payload); err != nil {
			return nil, err
		}
		out = append(out, r)
	}
	return out, rows.Err()
}

// selectPrefix filters on `entity` alone. See migration 0011 for why `feature` is not a
// safe predicate: the device derives it from the table name unless a local syncPolicy row
// overrides it, so it varies by client build while `entity` never does.
const selectPrefix = `SELECT entity_id, payload FROM entity_state
	WHERE user_id = $1 AND deleted = false AND entity = $2`

// Habits returns every habit row the user owns, including paused and archived ones.
// Filtering happens in the service: statistics still count an archived habit's history,
// and the home list needs to know a paused habit exists to offer "resume".
func (s *Store) Habits(ctx context.Context, userID uuid.UUID) ([]Habit, error) {
	rows, err := s.query(ctx, selectPrefix, userID, EntityHabit)
	if err != nil {
		return nil, err
	}
	out := make([]Habit, 0, len(rows))
	for _, r := range rows {
		var h Habit
		if err := json.Unmarshal(r.Payload, &h); err != nil {
			// A payload this build cannot parse belongs to a client version that is ahead
			// of us. Dropping one habit from a dashboard is survivable; failing the whole
			// request because of it is not.
			continue
		}
		if h.ID == "" {
			h.ID = r.EntityID
		}
		h.Normalize()
		if bool(h.Deleted) {
			continue
		}
		out = append(out, h)
	}
	return out, nil
}

func (s *Store) Habit(ctx context.Context, userID uuid.UUID, id string) (Habit, error) {
	raw, ok, err := s.get(ctx, userID, EntityHabit, id)
	return decodeHabit(raw, ok, err, id)
}

// habitForUpdate serializes completion commands for one habit. The habit definition is
// the existing, user-owned lock row, so concurrent retries cannot both observe a missing
// daily entry and then replace its original completion timestamps.
func (s *Store) habitForUpdate(ctx context.Context, userID uuid.UUID, id string) (Habit, error) {
	var raw json.RawMessage
	err := s.db.QueryRow(ctx, `
		SELECT payload FROM entity_state
		WHERE user_id = $1 AND entity = $2 AND entity_id = $3 AND deleted = false
		FOR UPDATE`, userID, EntityHabit, id).Scan(&raw)
	if errors.Is(err, pgx.ErrNoRows) {
		return Habit{}, ErrNotFound
	}
	return decodeHabit(raw, err == nil, err, id)
}

func decodeHabit(raw json.RawMessage, ok bool, err error, id string) (Habit, error) {
	if err != nil {
		return Habit{}, err
	}
	if !ok {
		return Habit{}, ErrNotFound
	}
	var h Habit
	if err := json.Unmarshal(raw, &h); err != nil {
		return Habit{}, ErrNotFound
	}
	if h.ID == "" {
		h.ID = id
	}
	h.Normalize()
	if bool(h.Deleted) {
		return Habit{}, ErrNotFound
	}
	return h, nil
}

// Entry returns the current, non-tombstoned daily entry. It lets the command service
// preserve the first completion timestamps and avoid advancing the sync cursor for a
// semantic replay of the same operation.
func (s *Store) Entry(ctx context.Context, userID uuid.UUID, id string) (Entry, bool, error) {
	raw, ok, err := s.get(ctx, userID, EntityEntry, id)
	if err != nil || !ok {
		return Entry{}, ok, err
	}
	var entry Entry
	if err := json.Unmarshal(raw, &entry); err != nil {
		return Entry{}, false, err
	}
	if entry.ID == "" {
		entry.ID = id
	}
	if entry.HabitID == "" || entry.DateKey == "" || !ValidStatus(entry.Status) {
		return Entry{}, false, nil
	}
	return entry, true, nil
}

// EntriesBetween loads entries whose dateKey falls in [from, to] inclusive. The window is
// what keeps a dashboard request bounded: a three-year-old account has thousands of
// entries and the home screen needs the last month of them.
func (s *Store) EntriesBetween(ctx context.Context, userID uuid.UUID, from, to string) ([]Entry, error) {
	rows, err := s.query(ctx,
		selectPrefix+` AND payload ->> 'dateKey' BETWEEN $3 AND $4`,
		userID, EntityEntry, from, to)
	if err != nil {
		return nil, err
	}
	return decodeEntries(rows), nil
}

// EntriesForHabit loads one habit's whole history, for the detail screen.
func (s *Store) EntriesForHabit(ctx context.Context, userID uuid.UUID, habitID string) ([]Entry, error) {
	rows, err := s.query(ctx,
		selectPrefix+` AND payload ->> 'habitId' = $3`,
		userID, EntityEntry, habitID)
	if err != nil {
		return nil, err
	}
	return decodeEntries(rows), nil
}

func decodeEntries(rows []rawRow) []Entry {
	out := make([]Entry, 0, len(rows))
	for _, r := range rows {
		var e Entry
		if err := json.Unmarshal(r.Payload, &e); err != nil {
			continue
		}
		if e.ID == "" {
			e.ID = r.EntityID
		}
		if e.HabitID == "" || e.DateKey == "" || !ValidStatus(e.Status) {
			continue
		}
		out = append(out, e)
	}
	return out
}

func (s *Store) Notes(ctx context.Context, userID uuid.UUID, habitID string) ([]Note, error) {
	sql := selectPrefix
	args := []any{userID, EntityNote}
	if habitID != "" {
		sql += ` AND payload ->> 'habitId' = $3`
		args = append(args, habitID)
	}
	rows, err := s.query(ctx, sql, args...)
	if err != nil {
		return nil, err
	}
	out := make([]Note, 0, len(rows))
	for _, r := range rows {
		var n Note
		if err := json.Unmarshal(r.Payload, &n); err != nil {
			continue
		}
		if n.ID == "" {
			n.ID = r.EntityID
		}
		if bool(n.Deleted) {
			continue
		}
		out = append(out, n)
	}
	return out, nil
}

func (s *Store) Achievements(ctx context.Context, userID uuid.UUID) (map[string]AchievementState, error) {
	rows, err := s.query(ctx, selectPrefix, userID, EntityAchievement)
	if err != nil {
		return nil, err
	}
	out := make(map[string]AchievementState, len(rows))
	for _, r := range rows {
		var a AchievementState
		if err := json.Unmarshal(r.Payload, &a); err != nil {
			continue
		}
		if a.ID == "" {
			a.ID = r.EntityID
		}
		out[a.ID] = a
	}
	return out, nil
}

func (s *Store) get(ctx context.Context, userID uuid.UUID, entity, entityID string) (json.RawMessage, bool, error) {
	var payload json.RawMessage
	err := s.db.QueryRow(ctx, `
		SELECT payload FROM entity_state
		WHERE user_id = $1 AND entity = $2 AND entity_id = $3 AND deleted = false`,
		userID, entity, entityID).Scan(&payload)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, false, nil
	}
	if err != nil {
		return nil, false, err
	}
	return payload, true, nil
}

// ---------------------------------------------------------------------------
// Writes
// ---------------------------------------------------------------------------

// Put upserts one row. `deviceTime` is the logical clock the last-write-wins guard
// compares — for a server-originated write that is simply now(), which means a REST write
// always beats state the phone recorded earlier and always loses to state it recorded
// later. That is the correct answer in both directions.
//
// Returns false when the guard rejected the write because a newer version already exists.
func (s *Store) Put(ctx context.Context, userID uuid.UUID, entity, entityID string, payload any, deviceTime time.Time) (bool, error) {
	blob, err := json.Marshal(payload)
	if err != nil {
		return false, err
	}
	expr, ok := mergeExpr[entity]
	if !ok {
		expr = "EXCLUDED.payload"
	}
	// `expr` comes from a fixed map keyed by a constant defined in this package — never
	// from a request — so interpolating it is not an injection surface. Every value is
	// a bound parameter.
	sql := fmt.Sprintf(`
		INSERT INTO entity_state AS es
			(user_id, feature, entity, entity_id, payload, deleted, device_time, timezone, seq)
		VALUES ($1, '%s', $2, $3, $4::jsonb, false, $5, 'UTC', nextval('entity_state_seq'))
		ON CONFLICT (user_id, entity, entity_id) DO UPDATE SET
			feature     = EXCLUDED.feature,
			payload     = %s,
			deleted     = false,
			device_time = EXCLUDED.device_time,
			seq         = nextval('entity_state_seq'),
			version     = es.version + 1,
			updated_at  = now()
		WHERE EXCLUDED.device_time >= es.device_time
		RETURNING 1`, Feature, expr)

	var one int
	err = s.db.QueryRow(ctx, sql, userID, entity, entityID, string(blob), deviceTime).Scan(&one)
	if errors.Is(err, pgx.ErrNoRows) {
		return false, nil
	}
	if err != nil {
		return false, err
	}
	return true, nil
}

// Tombstone marks a row deleted. A hard DELETE is wrong here: the row would simply vanish
// from the delta feed and every other device would keep its copy forever.
func (s *Store) Tombstone(ctx context.Context, userID uuid.UUID, entity, entityID string, deviceTime time.Time) error {
	_, err := s.db.Exec(ctx, `
		INSERT INTO entity_state AS es
			(user_id, feature, entity, entity_id, payload, deleted, device_time, timezone, seq)
		VALUES ($1, $2, $3, $4, '{}'::jsonb, true, $5, 'UTC', nextval('entity_state_seq'))
		ON CONFLICT (user_id, entity, entity_id) DO UPDATE SET
			deleted     = true,
			device_time = EXCLUDED.device_time,
			seq         = nextval('entity_state_seq'),
			version     = es.version + 1,
			updated_at  = now()
		WHERE EXCLUDED.device_time >= es.device_time
		  AND es.deleted = false`,
		userID, Feature, entity, entityID, deviceTime)
	return err
}

// Patch is read-modify-write over the raw payload.
//
// It exists so a partial update ("set reminderTime") cannot drop a field this server
// build has never heard of. Decoding into Habit and re-encoding would silently erase any
// column a newer app added, on every single edit — the classic older-server-eats-newer-
// client bug. Applying the change onto the raw map keeps those fields intact.
func (s *Store) Patch(
	ctx context.Context,
	userID uuid.UUID,
	entity, entityID string,
	deviceTime time.Time,
	mutate func(fields map[string]json.RawMessage) error,
) error {
	raw, ok, err := s.get(ctx, userID, entity, entityID)
	if err != nil {
		return err
	}
	fields := map[string]json.RawMessage{}
	if ok {
		if err := json.Unmarshal(raw, &fields); err != nil {
			fields = map[string]json.RawMessage{}
		}
	} else if entity == EntityHabit {
		return ErrNotFound
	}
	if err := mutate(fields); err != nil {
		return err
	}
	_, err = s.Put(ctx, userID, entity, entityID, fields, deviceTime)
	return err
}

// PutMany writes several rows of one entity in a single statement. Used by reorder, which
// touches every habit at once: N round trips for a drag-and-drop would be visible.
func (s *Store) PutMany(ctx context.Context, userID uuid.UUID, entity string, payloads map[string]any, deviceTime time.Time) error {
	if len(payloads) == 0 {
		return nil
	}
	ids := make([]string, 0, len(payloads))
	blobs := make([]string, 0, len(payloads))
	for id, p := range payloads {
		blob, err := json.Marshal(p)
		if err != nil {
			return err
		}
		ids = append(ids, id)
		blobs = append(blobs, string(blob))
	}
	expr, ok := mergeExpr[entity]
	if !ok {
		expr = "EXCLUDED.payload"
	}
	sql := fmt.Sprintf(`
		INSERT INTO entity_state AS es
			(user_id, feature, entity, entity_id, payload, deleted, device_time, timezone, seq)
		SELECT $1, '%s', $2, t.id, t.payload::jsonb, false, $5, 'UTC', nextval('entity_state_seq')
		FROM unnest($3::text[], $4::text[]) AS t(id, payload)
		ON CONFLICT (user_id, entity, entity_id) DO UPDATE SET
			payload     = %s,
			deleted     = false,
			device_time = EXCLUDED.device_time,
			seq         = nextval('entity_state_seq'),
			version     = es.version + 1,
			updated_at  = now()
		WHERE EXCLUDED.device_time >= es.device_time`, Feature, expr)

	_, err := s.db.Exec(ctx, sql, userID, entity, ids, blobs, deviceTime)
	return err
}
