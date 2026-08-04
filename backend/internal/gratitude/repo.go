package gratitude

import (
	"context"
	"errors"
	"strconv"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

var ErrNotFound = errors.New("gratitude entry not found")

type Repo struct{ pool *pgxpool.Pool }

func NewRepo(pool *pgxpool.Pool) *Repo { return &Repo{pool: pool} }

const entryCols = `id, user_id, client_id, mode, body, items, why, category, mood, emotions,
	photo_count, has_audio, has_video, location_label, favourite, entry_date,
	created_at, updated_at, deleted_at`

func scanEntry(row pgx.Row) (Entry, error) {
	var e Entry
	err := row.Scan(&e.ID, &e.UserID, &e.ClientID, &e.Mode, &e.Body, &e.Items, &e.Why, &e.Category,
		&e.Mood, &e.Emotions, &e.PhotoCount, &e.HasAudio, &e.HasVideo, &e.LocationLabel,
		&e.Favourite, &e.EntryDate, &e.CreatedAt, &e.UpdatedAt, &e.DeletedAt)
	return e, err
}

// Upsert writes an entry, keyed on (user_id, client_id). A replayed outbox op
// therefore updates the same row instead of creating a duplicate — the write path
// is idempotent, which is what lets the client retry freely after a flaky network.
func (r *Repo) Upsert(ctx context.Context, e Entry) (Entry, error) {
	if e.ID == uuid.Nil {
		e.ID = uuid.New()
	}
	row := r.pool.QueryRow(ctx, `
		INSERT INTO gratitude_entries
			(id, user_id, client_id, mode, body, items, why, category, mood, emotions,
			 photo_count, has_audio, has_video, location_label, favourite, entry_date)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16)
		ON CONFLICT (user_id, client_id) DO UPDATE SET
			mode = EXCLUDED.mode,
			body = EXCLUDED.body,
			items = EXCLUDED.items,
			why = EXCLUDED.why,
			category = EXCLUDED.category,
			mood = EXCLUDED.mood,
			emotions = EXCLUDED.emotions,
			photo_count = EXCLUDED.photo_count,
			has_audio = EXCLUDED.has_audio,
			has_video = EXCLUDED.has_video,
			location_label = EXCLUDED.location_label,
			favourite = EXCLUDED.favourite,
			entry_date = EXCLUDED.entry_date,
			updated_at = now(),
			deleted_at = NULL
		RETURNING `+entryCols,
		e.ID, e.UserID, e.ClientID, e.Mode, e.Body, e.Items, e.Why, e.Category, e.Mood, e.Emotions,
		e.PhotoCount, e.HasAudio, e.HasVideo, e.LocationLabel, e.Favourite, e.EntryDate)
	return scanEntry(row)
}

// Filter narrows a listing. Zero values mean "no constraint".
type Filter struct {
	From      time.Time
	To        time.Time
	Query     string
	Category  string
	Favourite bool
	Limit     int
	Before    time.Time // keyset cursor on created_at
}

func (r *Repo) List(ctx context.Context, userID uuid.UUID, f Filter) ([]Entry, error) {
	var (
		where = []string{"user_id = $1", "deleted_at IS NULL"}
		args  = []any{userID}
	)
	// add appends one argument and substitutes every "?" in clause with its
	// positional placeholder, so a clause may reference the same value repeatedly.
	add := func(clause string, v any) {
		args = append(args, v)
		where = append(where, strings.ReplaceAll(clause, "?", "$"+strconv.Itoa(len(args))))
	}
	if !f.From.IsZero() {
		add("entry_date >= ?", f.From)
	}
	if !f.To.IsZero() {
		add("entry_date <= ?", f.To)
	}
	if f.Category != "" {
		add("category = ?", f.Category)
	}
	if f.Favourite {
		where = append(where, "favourite = true")
	}
	if q := strings.TrimSpace(f.Query); q != "" {
		// Case-insensitive contains over the text, the "why" note and the emotion
		// tags — the same fields the client's local search covers, so results agree.
		add("(body ILIKE ? OR coalesce(why, '') ILIKE ? OR array_to_string(emotions, ' ') ILIKE ?)", "%"+q+"%")
	}
	if !f.Before.IsZero() {
		add("created_at < ?", f.Before)
	}
	limit := f.Limit
	if limit <= 0 || limit > 200 {
		limit = 50
	}

	rows, err := r.pool.Query(ctx,
		`SELECT `+entryCols+` FROM gratitude_entries WHERE `+strings.Join(where, " AND ")+
			` ORDER BY entry_date DESC, created_at DESC LIMIT `+strconv.Itoa(limit), args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return collect(rows)
}

// ChangedSince returns every row touched after [since], deletions included, so a
// client that was offline can converge without re-downloading the whole journal.
func (r *Repo) ChangedSince(ctx context.Context, userID uuid.UUID, since time.Time, limit int) ([]Entry, error) {
	if limit <= 0 || limit > 500 {
		limit = 200
	}
	rows, err := r.pool.Query(ctx,
		`SELECT `+entryCols+` FROM gratitude_entries
		 WHERE user_id = $1 AND updated_at > $2
		 ORDER BY updated_at ASC LIMIT `+strconv.Itoa(limit),
		userID, since)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return collect(rows)
}

func collect(rows pgx.Rows) ([]Entry, error) {
	var out []Entry
	for rows.Next() {
		e, err := scanEntry(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, e)
	}
	return out, rows.Err()
}

func (r *Repo) Get(ctx context.Context, userID, id uuid.UUID) (Entry, error) {
	e, err := scanEntry(r.pool.QueryRow(ctx,
		`SELECT `+entryCols+` FROM gratitude_entries WHERE user_id = $1 AND id = $2 AND deleted_at IS NULL`,
		userID, id))
	if errors.Is(err, pgx.ErrNoRows) {
		return Entry{}, ErrNotFound
	}
	return e, err
}

// Patch applies only the fields the caller set. Everything else keeps its value,
// which is what makes "favourite this entry" a one-field request.
type Patch struct {
	Body      *string
	Why       *string
	Category  *string
	Mood      *string
	Emotions  *[]string
	Favourite *bool
}

func (r *Repo) Update(ctx context.Context, userID, id uuid.UUID, p Patch) (Entry, error) {
	// pgx encodes a typed nil slice pointer inconsistently; hand it an untyped nil
	// so coalesce() sees SQL NULL and the column keeps its current value.
	var emotions any
	if p.Emotions != nil {
		emotions = *p.Emotions
	}
	e, err := scanEntry(r.pool.QueryRow(ctx, `
		UPDATE gratitude_entries SET
			body = coalesce($3, body),
			why = CASE WHEN $4::boolean THEN $5 ELSE why END,
			category = coalesce($6, category),
			mood = CASE WHEN $7::boolean THEN $8 ELSE mood END,
			emotions = coalesce($9, emotions),
			favourite = coalesce($10, favourite),
			updated_at = now()
		WHERE user_id = $1 AND id = $2 AND deleted_at IS NULL
		RETURNING `+entryCols,
		userID, id,
		p.Body,
		p.Why != nil, p.Why,
		p.Category,
		p.Mood != nil, p.Mood,
		emotions,
		p.Favourite))
	if errors.Is(err, pgx.ErrNoRows) {
		return Entry{}, ErrNotFound
	}
	return e, err
}

// ToggleFavourite flips the flag server-side so two devices can't fight over a
// stale local value.
func (r *Repo) ToggleFavourite(ctx context.Context, userID, id uuid.UUID) (Entry, error) {
	e, err := scanEntry(r.pool.QueryRow(ctx,
		`UPDATE gratitude_entries SET favourite = NOT favourite, updated_at = now()
		 WHERE user_id = $1 AND id = $2 AND deleted_at IS NULL RETURNING `+entryCols,
		userID, id))
	if errors.Is(err, pgx.ErrNoRows) {
		return Entry{}, ErrNotFound
	}
	return e, err
}

// SoftDelete keeps the row so a delta pull can tell offline clients to drop it.
func (r *Repo) SoftDelete(ctx context.Context, userID, id uuid.UUID) error {
	tag, err := r.pool.Exec(ctx,
		`UPDATE gratitude_entries SET deleted_at = now(), updated_at = now()
		 WHERE user_id = $1 AND id = $2 AND deleted_at IS NULL`, userID, id)
	if err != nil {
		return err
	}
	if tag.RowsAffected() == 0 {
		return ErrNotFound
	}
	return nil
}

// Random powers the jar's "pick a random memory".
func (r *Repo) Random(ctx context.Context, userID uuid.UUID) (Entry, error) {
	e, err := scanEntry(r.pool.QueryRow(ctx,
		`SELECT `+entryCols+` FROM gratitude_entries
		 WHERE user_id = $1 AND deleted_at IS NULL ORDER BY random() LIMIT 1`, userID))
	if errors.Is(err, pgx.ErrNoRows) {
		return Entry{}, ErrNotFound
	}
	return e, err
}

// Throwback is the "a memory to revisit" card: the oldest-but-still-meaningful
// entry from at least 30 days ago, favourites first.
func (r *Repo) Throwback(ctx context.Context, userID uuid.UUID, before time.Time) (Entry, error) {
	e, err := scanEntry(r.pool.QueryRow(ctx,
		`SELECT `+entryCols+` FROM gratitude_entries
		 WHERE user_id = $1 AND deleted_at IS NULL AND entry_date <= $2
		 ORDER BY favourite DESC, random() LIMIT 1`, userID, before))
	if errors.Is(err, pgx.ErrNoRows) {
		return Entry{}, ErrNotFound
	}
	return e, err
}

// ---- aggregates ----

func (r *Repo) ActiveDays(ctx context.Context, userID uuid.UUID) ([]time.Time, int, error) {
	rows, err := r.pool.Query(ctx,
		`SELECT entry_date, count(*) FROM gratitude_entries
		 WHERE user_id = $1 AND deleted_at IS NULL GROUP BY entry_date ORDER BY entry_date`, userID)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()

	var (
		days  []time.Time
		total int
	)
	for rows.Next() {
		var (
			d time.Time
			n int
		)
		if err := rows.Scan(&d, &n); err != nil {
			return nil, 0, err
		}
		days = append(days, d)
		total += n
	}
	return days, total, rows.Err()
}

func (r *Repo) CategoryCounts(ctx context.Context, userID uuid.UUID, from, to time.Time) (map[string]int, int, error) {
	rows, err := r.pool.Query(ctx,
		`SELECT category, count(*) FROM gratitude_entries
		 WHERE user_id = $1 AND deleted_at IS NULL AND entry_date BETWEEN $2 AND $3
		 GROUP BY category`, userID, from, to)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()

	counts := map[string]int{}
	total := 0
	for rows.Next() {
		var (
			k string
			n int
		)
		if err := rows.Scan(&k, &n); err != nil {
			return nil, 0, err
		}
		counts[k] = n
		total += n
	}
	return counts, total, rows.Err()
}

// MoodByDay returns the dominant (most recent) mood per day in the range.
func (r *Repo) MoodByDay(ctx context.Context, userID uuid.UUID, from, to time.Time) (map[string]string, error) {
	rows, err := r.pool.Query(ctx, `
		SELECT DISTINCT ON (entry_date) entry_date, mood
		FROM gratitude_entries
		WHERE user_id = $1 AND deleted_at IS NULL AND mood IS NOT NULL
		  AND entry_date BETWEEN $2 AND $3
		ORDER BY entry_date, created_at DESC`, userID, from, to)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	out := map[string]string{}
	for rows.Next() {
		var (
			d time.Time
			m string
		)
		if err := rows.Scan(&d, &m); err != nil {
			return nil, err
		}
		out[d.Format(DateLayout)] = m
	}
	return out, rows.Err()
}

func (r *Repo) DaysInRange(ctx context.Context, userID uuid.UUID, from, to time.Time) ([]time.Time, int, error) {
	rows, err := r.pool.Query(ctx,
		`SELECT entry_date, count(*) FROM gratitude_entries
		 WHERE user_id = $1 AND deleted_at IS NULL AND entry_date BETWEEN $2 AND $3
		 GROUP BY entry_date ORDER BY entry_date`, userID, from, to)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()

	var (
		days  []time.Time
		total int
	)
	for rows.Next() {
		var (
			d time.Time
			n int
		)
		if err := rows.Scan(&d, &n); err != nil {
			return nil, 0, err
		}
		days = append(days, d)
		total += n
	}
	return days, total, rows.Err()
}

// ---- challenges ----

type ChallengeState struct {
	ID       string
	Joined   bool
	JoinedAt *time.Time
	Days     []int
}

func (r *Repo) ChallengeStates(ctx context.Context, userID uuid.UUID) (map[string]*ChallengeState, error) {
	states := map[string]*ChallengeState{}

	rows, err := r.pool.Query(ctx,
		`SELECT challenge_id, joined_at FROM gratitude_challenges WHERE user_id = $1 AND left_at IS NULL`, userID)
	if err != nil {
		return nil, err
	}
	for rows.Next() {
		var (
			id string
			at time.Time
		)
		if err := rows.Scan(&id, &at); err != nil {
			rows.Close()
			return nil, err
		}
		joinedAt := at
		states[id] = &ChallengeState{ID: id, Joined: true, JoinedAt: &joinedAt}
	}
	rows.Close()
	if err := rows.Err(); err != nil {
		return nil, err
	}

	dayRows, err := r.pool.Query(ctx,
		`SELECT challenge_id, day_index FROM gratitude_challenge_days WHERE user_id = $1 ORDER BY day_index`, userID)
	if err != nil {
		return nil, err
	}
	defer dayRows.Close()
	for dayRows.Next() {
		var (
			id  string
			day int
		)
		if err := dayRows.Scan(&id, &day); err != nil {
			return nil, err
		}
		st, ok := states[id]
		if !ok {
			st = &ChallengeState{ID: id}
			states[id] = st
		}
		st.Days = append(st.Days, day)
	}
	return states, dayRows.Err()
}

func (r *Repo) JoinChallenge(ctx context.Context, userID uuid.UUID, id string) error {
	_, err := r.pool.Exec(ctx,
		`INSERT INTO gratitude_challenges (user_id, challenge_id) VALUES ($1, $2)
		 ON CONFLICT (user_id, challenge_id) DO UPDATE SET left_at = NULL, joined_at = now()`,
		userID, id)
	return err
}

func (r *Repo) LeaveChallenge(ctx context.Context, userID uuid.UUID, id string) error {
	_, err := r.pool.Exec(ctx,
		`UPDATE gratitude_challenges SET left_at = now() WHERE user_id = $1 AND challenge_id = $2`,
		userID, id)
	return err
}

func (r *Repo) CompleteDay(ctx context.Context, userID uuid.UUID, id string, day int) error {
	_, err := r.pool.Exec(ctx,
		`INSERT INTO gratitude_challenge_days (user_id, challenge_id, day_index) VALUES ($1, $2, $3)
		 ON CONFLICT DO NOTHING`, userID, id, day)
	return err
}

// ---- settings ----

func (r *Repo) Settings(ctx context.Context, userID uuid.UUID) (Settings, error) {
	var s Settings
	err := r.pool.QueryRow(ctx,
		`SELECT reminder_slot, reminder_time, face_id, app_lock, hide_preview, cloud_sync, updated_at
		 FROM gratitude_settings WHERE user_id = $1`, userID,
	).Scan(&s.ReminderSlot, &s.ReminderTime, &s.FaceID, &s.AppLock, &s.HidePreview, &s.CloudSync, &s.UpdatedAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return DefaultSettings(), nil
	}
	return s, err
}

func (r *Repo) PutSettings(ctx context.Context, userID uuid.UUID, s Settings) (Settings, error) {
	err := r.pool.QueryRow(ctx, `
		INSERT INTO gratitude_settings
			(user_id, reminder_slot, reminder_time, face_id, app_lock, hide_preview, cloud_sync, updated_at)
		VALUES ($1, $2, $3, $4, $5, $6, $7, now())
		ON CONFLICT (user_id) DO UPDATE SET
			reminder_slot = EXCLUDED.reminder_slot,
			reminder_time = EXCLUDED.reminder_time,
			face_id = EXCLUDED.face_id,
			app_lock = EXCLUDED.app_lock,
			hide_preview = EXCLUDED.hide_preview,
			cloud_sync = EXCLUDED.cloud_sync,
			updated_at = now()
		RETURNING updated_at`,
		userID, s.ReminderSlot, s.ReminderTime, s.FaceID, s.AppLock, s.HidePreview, s.CloudSync,
	).Scan(&s.UpdatedAt)
	return s, err
}
