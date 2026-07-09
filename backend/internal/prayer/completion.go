package prayer

import (
	"context"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgxpool"
)

// Completion is one recorded prayer completion, the source row for the
// Prayer Analytics feature (history, streaks, delay stats, charts).
type Completion struct {
	ID            uuid.UUID
	UserID        uuid.UUID
	Prayer        string
	ScheduledTime time.Time
	CompletedAt   time.Time
	DelayMinutes  int
	OnTime        bool
	Late          bool
	Latitude      *float64
	Longitude     *float64
	LocationLabel *string
	CreatedAt     time.Time
}

type CompletionRepo struct{ pool *pgxpool.Pool }

func NewCompletionRepo(pool *pgxpool.Pool) *CompletionRepo { return &CompletionRepo{pool: pool} }

func (r *CompletionRepo) Create(ctx context.Context, c Completion) (Completion, error) {
	c.ID = uuid.New()
	err := r.pool.QueryRow(ctx, `
		INSERT INTO prayer_completions
			(id, user_id, prayer, scheduled_time, completed_at, delay_minutes, on_time, late, latitude, longitude, location_label)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)
		RETURNING created_at`,
		c.ID, c.UserID, c.Prayer, c.ScheduledTime, c.CompletedAt, c.DelayMinutes, c.OnTime, c.Late,
		c.Latitude, c.Longitude, c.LocationLabel,
	).Scan(&c.CreatedAt)
	return c, err
}

func (r *CompletionRepo) List(ctx context.Context, userID uuid.UUID, from, to time.Time) ([]Completion, error) {
	rows, err := r.pool.Query(ctx, `
		SELECT id, user_id, prayer, scheduled_time, completed_at, delay_minutes, on_time, late, latitude, longitude, location_label, created_at
		FROM prayer_completions
		WHERE user_id = $1 AND scheduled_time BETWEEN $2 AND $3
		ORDER BY scheduled_time DESC`,
		userID, from, to)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var out []Completion
	for rows.Next() {
		var c Completion
		if err := rows.Scan(&c.ID, &c.UserID, &c.Prayer, &c.ScheduledTime, &c.CompletedAt, &c.DelayMinutes,
			&c.OnTime, &c.Late, &c.Latitude, &c.Longitude, &c.LocationLabel, &c.CreatedAt); err != nil {
			return nil, err
		}
		out = append(out, c)
	}
	return out, rows.Err()
}
