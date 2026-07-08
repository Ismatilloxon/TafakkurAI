package auth

import (
	"context"
	"errors"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

type Repo struct {
	pool *pgxpool.Pool
}

func NewRepo(pool *pgxpool.Pool) *Repo { return &Repo{pool: pool} }

type challenge struct {
	ID        uuid.UUID
	Phone     string
	CodeHash  string
	ExpiresAt time.Time
	Attempts  int
}

func (r *Repo) CreateChallenge(ctx context.Context, phone, codeHash string, expiresAt time.Time) (uuid.UUID, error) {
	id := uuid.New()
	_, err := r.pool.Exec(ctx, `
		INSERT INTO otp_challenges (id, phone, code_hash, expires_at, attempts)
		VALUES ($1, $2, $3, $4, 0)`,
		id, phone, codeHash, expiresAt)
	return id, err
}

func (r *Repo) GetChallenge(ctx context.Context, id uuid.UUID) (challenge, error) {
	var c challenge
	err := r.pool.QueryRow(ctx, `
		SELECT id, phone, code_hash, expires_at, attempts
		FROM otp_challenges WHERE id = $1`, id,
	).Scan(&c.ID, &c.Phone, &c.CodeHash, &c.ExpiresAt, &c.Attempts)
	if errors.Is(err, pgx.ErrNoRows) {
		return challenge{}, ErrOTPNotFound
	}
	return c, err
}

func (r *Repo) IncrAttempts(ctx context.Context, id uuid.UUID) error {
	_, err := r.pool.Exec(ctx,
		`UPDATE otp_challenges SET attempts = attempts + 1 WHERE id = $1`, id)
	return err
}

func (r *Repo) DeleteChallenge(ctx context.Context, id uuid.UUID) error {
	_, err := r.pool.Exec(ctx, `DELETE FROM otp_challenges WHERE id = $1`, id)
	return err
}
