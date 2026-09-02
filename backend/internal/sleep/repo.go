package sleep

import (
	"context"
	"errors"
	"strconv"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

var ErrNotFound = errors.New("sleep session not found")

type Repo struct{ pool *pgxpool.Pool }

func NewRepo(pool *pgxpool.Pool) *Repo { return &Repo{pool: pool} }

const sessionCols = `id, user_id, client_id, external_id, kind, source,
	start_at, end_at, in_bed_start_at, in_bed_end_at, segments, vitals, date_key,
	mood_on_waking, note, tags, score, recovery_score, asleep_minutes,
	created_at, updated_at, deleted_at`

func scanSession(row pgx.Row) (Session, error) {
	var s Session
	err := row.Scan(&s.ID, &s.UserID, &s.ClientID, &s.ExternalID, &s.Kind, &s.Source,
		&s.StartAt, &s.EndAt, &s.InBedStartAt, &s.InBedEndAt, &s.Segments, &s.Vitals, &s.DateKey,
		&s.MoodOnWaking, &s.Note, &s.Tags, &s.Score, &s.RecoveryScore, &s.AsleepMinutes,
		&s.CreatedAt, &s.UpdatedAt, &s.DeletedAt)
	return s, err
}

func collect(rows pgx.Rows) ([]Session, error) {
	var out []Session
	for rows.Next() {
		s, err := scanSession(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, s)
	}
	return out, rows.Err()
}

// Upsert writes a session keyed on (user_id, client_id).
//
// The WHERE clause on the DO UPDATE is the conflict resolution, pushed into SQL so it is
// atomic. Without it, two devices uploading the same night concurrently would both read
// "no conflict", both write, and the last one to land would win regardless of source —
// exactly the degradation ResolveConflict exists to prevent.
//
// The expression mirrors Session.Strength(): source rank, then staging, then vitals, then
// duration, with updated_at as the final tiebreak.
func (r *Repo) Upsert(ctx context.Context, s Session) (Session, error) {
	if s.ID == uuid.Nil {
		s.ID = uuid.New()
	}
	row := r.pool.QueryRow(ctx, `
		INSERT INTO sleep_sessions
			(id, user_id, client_id, external_id, kind, source,
			 start_at, end_at, in_bed_start_at, in_bed_end_at, segments, vitals, date_key,
			 mood_on_waking, note, tags, score, recovery_score, asleep_minutes)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17, $18, $19)
		ON CONFLICT (user_id, client_id) DO UPDATE SET
			external_id     = EXCLUDED.external_id,
			kind            = EXCLUDED.kind,
			source          = EXCLUDED.source,
			start_at        = EXCLUDED.start_at,
			end_at          = EXCLUDED.end_at,
			in_bed_start_at = EXCLUDED.in_bed_start_at,
			in_bed_end_at   = EXCLUDED.in_bed_end_at,
			segments        = EXCLUDED.segments,
			vitals          = EXCLUDED.vitals,
			date_key        = EXCLUDED.date_key,
			mood_on_waking  = EXCLUDED.mood_on_waking,
			note            = EXCLUDED.note,
			tags            = EXCLUDED.tags,
			score           = EXCLUDED.score,
			recovery_score  = EXCLUDED.recovery_score,
			asleep_minutes  = EXCLUDED.asleep_minutes,
			updated_at      = now(),
			deleted_at      = NULL
		WHERE sleep_strength(EXCLUDED.source, EXCLUDED.segments, EXCLUDED.vitals, EXCLUDED.asleep_minutes)
		      >= sleep_strength(sleep_sessions.source, sleep_sessions.segments, sleep_sessions.vitals, sleep_sessions.asleep_minutes)
		RETURNING `+sessionCols,
		s.ID, s.UserID, s.ClientID, s.ExternalID, s.Kind, s.Source,
		s.StartAt, s.EndAt, s.InBedStartAt, s.InBedEndAt, s.Segments, s.Vitals, s.DateKey,
		s.MoodOnWaking, s.Note, s.Tags, s.Score, s.RecoveryScore, s.AsleepMinutes)

	out, err := scanSession(row)
	if errors.Is(err, pgx.ErrNoRows) {
		// The DO UPDATE's WHERE filtered it out: a stronger record is already stored. Not an
		// error — the client is told "superseded" and stops re-sending it.
		return Session{}, ErrSuperseded
	}
	return out, err
}

// ErrSuperseded means a stronger record for the same night is already stored.
var ErrSuperseded = errors.New("a stronger session is already stored")

// FindByExternalID resolves the cross-device import case: the same underlying health record
// uploaded from two devices arrives with two client ids but one external id.
func (r *Repo) FindByExternalID(ctx context.Context, userID uuid.UUID, external string) (Session, error) {
	s, err := scanSession(r.pool.QueryRow(ctx,
		`SELECT `+sessionCols+` FROM sleep_sessions
		 WHERE user_id = $1 AND external_id = $2`, userID, external))
	if errors.Is(err, pgx.ErrNoRows) {
		return Session{}, ErrNotFound
	}
	return s, err
}

// Filter narrows a listing. Zero values mean "no constraint".
type Filter struct {
	From   time.Time
	To     time.Time
	Kind   Kind
	Limit  int
	Before time.Time // keyset cursor on start_at
}

func (r *Repo) List(ctx context.Context, userID uuid.UUID, f Filter) ([]Session, error) {
	where := []string{"user_id = $1", "deleted_at IS NULL"}
	args := []any{userID}
	add := func(clause string, v any) {
		args = append(args, v)
		where = append(where, replacePlaceholder(clause, len(args)))
	}
	if !f.From.IsZero() {
		add("date_key >= ?", f.From)
	}
	if !f.To.IsZero() {
		add("date_key <= ?", f.To)
	}
	if f.Kind != "" {
		add("kind = ?", string(f.Kind))
	}
	if !f.Before.IsZero() {
		add("start_at < ?", f.Before)
	}
	limit := f.Limit
	if limit <= 0 || limit > 200 {
		limit = 50
	}

	rows, err := r.pool.Query(ctx,
		`SELECT `+sessionCols+` FROM sleep_sessions WHERE `+joinAnd(where)+
			` ORDER BY start_at DESC LIMIT `+strconv.Itoa(limit), args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return collect(rows)
}

func (r *Repo) Get(ctx context.Context, userID, id uuid.UUID) (Session, error) {
	s, err := scanSession(r.pool.QueryRow(ctx,
		`SELECT `+sessionCols+` FROM sleep_sessions
		 WHERE user_id = $1 AND id = $2 AND deleted_at IS NULL`, userID, id))
	if errors.Is(err, pgx.ErrNoRows) {
		return Session{}, ErrNotFound
	}
	return s, err
}

// SoftDelete keeps the row so a delta pull can tell offline clients to drop it.
func (r *Repo) SoftDelete(ctx context.Context, userID, id uuid.UUID) error {
	tag, err := r.pool.Exec(ctx,
		`UPDATE sleep_sessions SET deleted_at = now(), updated_at = now()
		 WHERE user_id = $1 AND id = $2 AND deleted_at IS NULL`, userID, id)
	if err != nil {
		return err
	}
	if tag.RowsAffected() == 0 {
		return ErrNotFound
	}
	return nil
}

// ChangedSince is the pull half of offline sync: everything touched after [since],
// deletions included.
func (r *Repo) ChangedSince(ctx context.Context, userID uuid.UUID, since time.Time, limit int) ([]Session, error) {
	if limit <= 0 || limit > 500 {
		limit = 200
	}
	rows, err := r.pool.Query(ctx,
		`SELECT `+sessionCols+` FROM sleep_sessions
		 WHERE user_id = $1 AND updated_at > $2
		 ORDER BY updated_at ASC LIMIT `+strconv.Itoa(limit),
		userID, since)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return collect(rows)
}

// ---- aggregates ----

// Aggregate rolls a window up in SQL rather than pulling rows into Go.
//
// A year of nights is only ~365 rows, so this is not about volume — it is that the weekly
// digest job runs it for every active user at once, and the difference between one query and
// one query plus a round trip per user is the difference between a job that finishes and one
// that does not.
func (r *Repo) Aggregate(ctx context.Context, userID uuid.UUID, from, to time.Time) (Stats, error) {
	var st Stats
	err := r.pool.QueryRow(ctx, `
		SELECT
			count(*),
			coalesce(round(avg(asleep_minutes)), 0),
			coalesce(round(avg(score) FILTER (WHERE score IS NOT NULL)), 0),
			coalesce(sum(asleep_minutes), 0),
			coalesce(max(score), 0),
			coalesce(min(score), 0),
			coalesce(avg(
				CASE WHEN extract(epoch FROM (in_bed_end_at - in_bed_start_at)) > 0
				THEN (asleep_minutes * 60.0) / extract(epoch FROM (in_bed_end_at - in_bed_start_at))
				ELSE NULL END
			), 0)
		FROM sleep_sessions
		WHERE user_id = $1 AND deleted_at IS NULL AND kind = 'night'
		  AND date_key BETWEEN $2 AND $3`,
		userID, from, to,
	).Scan(&st.Nights, &st.AverageAsleepMinutes, &st.AverageScore, &st.TotalAsleepMinutes,
		&st.BestScore, &st.WorstScore, &st.Efficiency)
	if err != nil {
		return Stats{}, err
	}

	rows, err := r.pool.Query(ctx, `
		SELECT date_key, sum(asleep_minutes), coalesce(round(avg(score)), 0)
		FROM sleep_sessions
		WHERE user_id = $1 AND deleted_at IS NULL AND kind = 'night'
		  AND date_key BETWEEN $2 AND $3
		GROUP BY date_key ORDER BY date_key`, userID, from, to)
	if err != nil {
		return Stats{}, err
	}
	defer rows.Close()

	st.Days = []DayStat{}
	for rows.Next() {
		var (
			d       time.Time
			minutes int
			score   int
		)
		if err := rows.Scan(&d, &minutes, &score); err != nil {
			return Stats{}, err
		}
		st.Days = append(st.Days, DayStat{
			DateKey:       d.Format(DateLayout),
			AsleepMinutes: minutes,
			Score:         score,
		})
	}
	return st, rows.Err()
}

// ---- routine marks ----

// UpsertMark ticks a step. Un-ticking tombstones rather than deleting, so the removal
// reaches other devices on their next pull.
func (r *Repo) UpsertMark(ctx context.Context, userID uuid.UUID, m RoutineMark, completed bool) error {
	if completed {
		_, err := r.pool.Exec(ctx, `
			INSERT INTO sleep_routine_marks (user_id, mark_id, step_id, phase, date_key, completed_at)
			VALUES ($1, $2, $3, $4, $5, now())
			ON CONFLICT (user_id, mark_id) DO UPDATE SET
				completed_at = now(), updated_at = now(), deleted_at = NULL`,
			userID, m.MarkID, m.StepID, m.Phase, m.DateKey)
		return err
	}
	_, err := r.pool.Exec(ctx,
		`UPDATE sleep_routine_marks SET deleted_at = now(), updated_at = now()
		 WHERE user_id = $1 AND mark_id = $2`, userID, m.MarkID)
	return err
}

func (r *Repo) MarksBetween(ctx context.Context, userID uuid.UUID, from, to time.Time) ([]RoutineMark, error) {
	rows, err := r.pool.Query(ctx,
		`SELECT id, user_id, mark_id, step_id, phase, date_key, completed_at, updated_at, deleted_at
		 FROM sleep_routine_marks
		 WHERE user_id = $1 AND deleted_at IS NULL AND date_key BETWEEN $2 AND $3
		 ORDER BY date_key`, userID, from, to)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return collectMarks(rows)
}

func (r *Repo) MarksChangedSince(ctx context.Context, userID uuid.UUID, since time.Time, limit int) ([]RoutineMark, error) {
	if limit <= 0 || limit > 500 {
		limit = 200
	}
	rows, err := r.pool.Query(ctx,
		`SELECT id, user_id, mark_id, step_id, phase, date_key, completed_at, updated_at, deleted_at
		 FROM sleep_routine_marks
		 WHERE user_id = $1 AND updated_at > $2
		 ORDER BY updated_at ASC LIMIT `+strconv.Itoa(limit), userID, since)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return collectMarks(rows)
}

func collectMarks(rows pgx.Rows) ([]RoutineMark, error) {
	var out []RoutineMark
	for rows.Next() {
		var m RoutineMark
		if err := rows.Scan(&m.ID, &m.UserID, &m.MarkID, &m.StepID, &m.Phase, &m.DateKey,
			&m.CompletedAt, &m.UpdatedAt, &m.DeletedAt); err != nil {
			return nil, err
		}
		out = append(out, m)
	}
	return out, rows.Err()
}

// ---- settings ----

func (r *Repo) Settings(ctx context.Context, userID uuid.UUID) (Settings, error) {
	var s Settings
	err := r.pool.QueryRow(ctx,
		`SELECT goals, enabled_step_ids, islamic_routine_enabled, flags, updated_at
		 FROM sleep_settings WHERE user_id = $1`, userID,
	).Scan(&s.Goals, &s.EnabledStepIDs, &s.IslamicRoutineEnabled, &s.Flags, &s.UpdatedAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return DefaultSettings(), nil
	}
	return s, err
}

func (r *Repo) PutSettings(ctx context.Context, userID uuid.UUID, s Settings) (Settings, error) {
	err := r.pool.QueryRow(ctx, `
		INSERT INTO sleep_settings (user_id, goals, enabled_step_ids, islamic_routine_enabled, flags, updated_at)
		VALUES ($1, $2, $3, $4, $5, now())
		ON CONFLICT (user_id) DO UPDATE SET
			goals                   = EXCLUDED.goals,
			enabled_step_ids        = EXCLUDED.enabled_step_ids,
			islamic_routine_enabled = EXCLUDED.islamic_routine_enabled,
			flags                   = EXCLUDED.flags,
			updated_at              = now()
		RETURNING updated_at`,
		userID, s.Goals, s.EnabledStepIDs, s.IslamicRoutineEnabled, s.Flags,
	).Scan(&s.UpdatedAt)
	return s, err
}

// ---- small helpers ----

func replacePlaceholder(clause string, n int) string {
	out := make([]byte, 0, len(clause)+4)
	for i := 0; i < len(clause); i++ {
		if clause[i] == '?' {
			out = append(out, '$')
			out = append(out, []byte(strconv.Itoa(n))...)
			continue
		}
		out = append(out, clause[i])
	}
	return string(out)
}

func joinAnd(parts []string) string {
	out := ""
	for i, p := range parts {
		if i > 0 {
			out += " AND "
		}
		out += p
	}
	return out
}
