package auth

import (
	"context"
	"errors"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/tafakkur/backend/internal/telegram"
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

// ---- Google browser sign-in states ----

type oauthState struct {
	State        string
	CodeVerifier string
	UserID       *uuid.UUID
	ApprovedAt   *time.Time
	ConsumedAt   *time.Time
	ExpiresAt    time.Time
}

func (r *Repo) CreateOAuthState(ctx context.Context, state, provider, codeVerifier string, expiresAt time.Time) error {
	_, err := r.pool.Exec(ctx, `
		INSERT INTO oauth_login_states (state, provider, code_verifier, expires_at)
		VALUES ($1, $2, $3, $4)`,
		state, provider, codeVerifier, expiresAt)
	return err
}

func (r *Repo) GetOAuthState(ctx context.Context, state string) (oauthState, error) {
	var s oauthState
	err := r.pool.QueryRow(ctx, `
		SELECT state, code_verifier, user_id, approved_at, consumed_at, expires_at
		FROM oauth_login_states WHERE state = $1`, state,
	).Scan(&s.State, &s.CodeVerifier, &s.UserID, &s.ApprovedAt, &s.ConsumedAt, &s.ExpiresAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return oauthState{}, ErrOAuthStateInvalid
	}
	return s, err
}

func (r *Repo) ApproveOAuthState(ctx context.Context, state string, userID uuid.UUID) error {
	tag, err := r.pool.Exec(ctx, `
		UPDATE oauth_login_states SET user_id = $2, approved_at = now()
		WHERE state = $1 AND approved_at IS NULL AND consumed_at IS NULL`, state, userID)
	if err != nil {
		return err
	}
	if tag.RowsAffected() != 1 {
		return ErrOAuthStateInvalid
	}
	return nil
}

// ConsumeOAuthState returns false when another poll redeemed it first.
func (r *Repo) ConsumeOAuthState(ctx context.Context, state string) (bool, error) {
	tag, err := r.pool.Exec(ctx, `
		UPDATE oauth_login_states SET consumed_at = now()
		WHERE state = $1 AND consumed_at IS NULL AND approved_at IS NOT NULL`, state)
	if err != nil {
		return false, err
	}
	return tag.RowsAffected() == 1, nil
}

// ---- Telegram confirmation codes ----

type authCode struct {
	Code       string
	TelegramID int64
	Phone      string
	Username   string
	FirstName  string
	LastName   string
	ConsumedAt *time.Time
	ExpiresAt  time.Time
}

// ReplaceAuthCode drops this account's unused code before inserting the new one,
// so pressing Start twice cannot leave two live codes behind.
func (r *Repo) ReplaceAuthCode(ctx context.Context, code string, from telegram.User, phone string, expiresAt time.Time) error {
	tx, err := r.pool.Begin(ctx)
	if err != nil {
		return err
	}
	defer tx.Rollback(ctx)

	if _, err := tx.Exec(ctx,
		`DELETE FROM telegram_auth_codes WHERE telegram_id = $1 AND consumed_at IS NULL`, from.ID); err != nil {
		return err
	}
	if _, err := tx.Exec(ctx, `
		INSERT INTO telegram_auth_codes
			(code, telegram_id, phone, username, first_name, last_name, expires_at)
		VALUES ($1, $2, $3, $4, $5, $6, $7)`,
		code, from.ID, phone, from.Username, from.FirstName, from.LastName, expiresAt); err != nil {
		return err
	}
	return tx.Commit(ctx)
}

func (r *Repo) GetAuthCode(ctx context.Context, code string) (authCode, error) {
	var c authCode
	var username, first, last *string
	err := r.pool.QueryRow(ctx, `
		SELECT code, telegram_id, phone, username, first_name, last_name, consumed_at, expires_at
		FROM telegram_auth_codes WHERE code = $1`, code,
	).Scan(&c.Code, &c.TelegramID, &c.Phone, &username, &first, &last, &c.ConsumedAt, &c.ExpiresAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return authCode{}, ErrTGCodeInvalid
	}
	if err != nil {
		return authCode{}, err
	}
	c.Username, c.FirstName, c.LastName = deref(username), deref(first), deref(last)
	return c, nil
}

// ConsumeAuthCode returns false when another request redeemed it first.
func (r *Repo) ConsumeAuthCode(ctx context.Context, code string) (bool, error) {
	tag, err := r.pool.Exec(ctx, `
		UPDATE telegram_auth_codes SET consumed_at = now()
		WHERE code = $1 AND consumed_at IS NULL`, code)
	if err != nil {
		return false, err
	}
	return tag.RowsAffected() == 1, nil
}

func deref(s *string) string {
	if s == nil {
		return ""
	}
	return *s
}
