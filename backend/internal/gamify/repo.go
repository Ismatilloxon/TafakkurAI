package gamify

import (
	"context"
	"errors"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

var ErrNotFound = errors.New("gamification row not found")

type Repo struct {
	pool *pgxpool.Pool
}

func NewRepo(pool *pgxpool.Pool) *Repo { return &Repo{pool: pool} }

func (r *Repo) EnsureExists(ctx context.Context, userID uuid.UUID) error {
	s := NewState()
	_, err := r.pool.Exec(ctx, `
		INSERT INTO gamification (user_id, streak, level, xp, xp_next, last_active_date)
		VALUES ($1, $2, $3, $4, $5, NULL)
		ON CONFLICT (user_id) DO NOTHING`,
		userID, s.Streak, s.Level, s.XP, s.XPNext)
	return err
}

func (r *Repo) Get(ctx context.Context, userID uuid.UUID) (State, error) {
	var s State
	var last *time.Time
	err := r.pool.QueryRow(ctx, `
		SELECT streak, level, xp, xp_next, last_active_date
		FROM gamification WHERE user_id = $1`, userID,
	).Scan(&s.Streak, &s.Level, &s.XP, &s.XPNext, &last)
	if errors.Is(err, pgx.ErrNoRows) {
		return State{}, ErrNotFound
	}
	if err != nil {
		return State{}, err
	}
	if last != nil {
		s.LastActiveDate = *last
	}
	return s, nil
}

func (r *Repo) Save(ctx context.Context, userID uuid.UUID, s State) error {
	_, err := r.pool.Exec(ctx, `
		INSERT INTO gamification (user_id, streak, level, xp, xp_next, last_active_date)
		VALUES ($1, $2, $3, $4, $5, $6)
		ON CONFLICT (user_id) DO UPDATE SET
			streak = EXCLUDED.streak,
			level = EXCLUDED.level,
			xp = EXCLUDED.xp,
			xp_next = EXCLUDED.xp_next,
			last_active_date = EXCLUDED.last_active_date`,
		userID, s.Streak, s.Level, s.XP, s.XPNext, s.LastActiveDate)
	return err
}
