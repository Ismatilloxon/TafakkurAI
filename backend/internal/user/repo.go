package user

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
)

var (
	ErrNotFound    = errors.New("user not found")
	ErrHandleTaken = errors.New("handle already taken")
	ErrPhoneTaken  = errors.New("phone already linked to another account")
	ErrEmailTaken  = errors.New("email already linked to another account")
)

type Repo struct {
	pool *pgxpool.Pool
}

func NewRepo(pool *pgxpool.Pool) *Repo { return &Repo{pool: pool} }

func isUniqueViolation(err error) bool {
	var pgErr *pgconn.PgError
	return errors.As(err, &pgErr) && pgErr.Code == "23505"
}

// UpsertUser inserts or updates a user by (provider, provider_subject) and
// reports whether the row was newly inserted.
func (r *Repo) UpsertUser(ctx context.Context, ident ProviderIdentity) (uuid.UUID, bool, error) {
	var id uuid.UUID
	var inserted bool
	err := r.pool.QueryRow(ctx, `
		INSERT INTO users (id, provider, provider_subject, email, phone)
		VALUES ($1, $2, $3, $4, $5)
		ON CONFLICT (provider, provider_subject) DO UPDATE SET
			email = COALESCE(EXCLUDED.email, users.email),
			phone = COALESCE(EXCLUDED.phone, users.phone),
			updated_at = now()
		RETURNING id, (xmax = 0) AS inserted`,
		uuid.New(), ident.Provider, ident.Subject, ident.Email, ident.Phone,
	).Scan(&id, &inserted)
	if err != nil {
		return uuid.Nil, false, fmt.Errorf("upsert user: %w", err)
	}
	return id, inserted, nil
}

// CreateProfileIfAbsent seeds a default profile for a freshly created user.
func (r *Repo) CreateProfileIfAbsent(ctx context.Context, userID uuid.UUID, ident ProviderIdentity) error {
	display := "Seeker"
	if ident.DisplayName != nil && *ident.DisplayName != "" {
		display = *ident.DisplayName
	}
	handle := "u" + strings.ReplaceAll(userID.String(), "-", "")[:12]
	_, err := r.pool.Exec(ctx, `
		INSERT INTO profiles
			(user_id, display_name, handle, language, madhab, calc_method, photo_url, onboarding_done)
		VALUES ($1, $2, $3, 'en', 'hanafi', 'MWL', $4, false)
		ON CONFLICT (user_id) DO NOTHING`,
		userID, display, handle, ident.PhotoURL)
	return err
}

func (r *Repo) GetAccount(ctx context.Context, userID uuid.UUID) (Account, error) {
	var a Account
	err := r.pool.QueryRow(ctx, `
		SELECT id, provider, email, phone, created_at, updated_at
		FROM users WHERE id = $1`, userID,
	).Scan(&a.User.ID, &a.User.Provider, &a.User.Email, &a.User.Phone,
		&a.User.CreatedAt, &a.User.UpdatedAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return Account{}, ErrNotFound
	}
	if err != nil {
		return Account{}, err
	}

	err = r.pool.QueryRow(ctx, `
		SELECT display_name, handle, gender, birth_date, language, city, country,
		       madhab, calc_method, photo_url, onboarding_done
		FROM profiles WHERE user_id = $1`, userID,
	).Scan(&a.Profile.DisplayName, &a.Profile.Handle, &a.Profile.Gender,
		&a.Profile.BirthDate, &a.Profile.Language, &a.Profile.City, &a.Profile.Country,
		&a.Profile.Madhab, &a.Profile.CalcMethod, &a.Profile.PhotoURL, &a.Profile.OnboardingDone)
	if err != nil && !errors.Is(err, pgx.ErrNoRows) {
		return Account{}, err
	}
	a.Profile.UserID = userID

	rows, err := r.pool.Query(ctx,
		`SELECT interest FROM user_interests WHERE user_id = $1 ORDER BY interest`, userID)
	if err != nil {
		return Account{}, err
	}
	defer rows.Close()
	for rows.Next() {
		var s string
		if err := rows.Scan(&s); err != nil {
			return Account{}, err
		}
		a.Interests = append(a.Interests, s)
	}
	return a, rows.Err()
}

func (r *Repo) UpdateProfile(ctx context.Context, userID uuid.UUID, in UpdateProfileInput, birth *time.Time) error {
	_, err := r.pool.Exec(ctx, `
		UPDATE profiles SET
			display_name = COALESCE($2, display_name),
			handle       = COALESCE($3, handle),
			gender       = COALESCE($4, gender),
			birth_date   = COALESCE($5, birth_date),
			language     = COALESCE($6, language),
			updated_at   = now()
		WHERE user_id = $1`,
		userID, in.DisplayName, in.Handle, in.Gender, birth, in.Language)
	if isUniqueViolation(err) {
		return ErrHandleTaken
	}
	return err
}

func (r *Repo) UpdatePrayerSettings(ctx context.Context, userID uuid.UUID, in PrayerSettingsInput) error {
	_, err := r.pool.Exec(ctx, `
		UPDATE profiles SET
			city = $2, country = $3, madhab = $4, calc_method = $5, updated_at = now()
		WHERE user_id = $1`,
		userID, in.City, in.Country, in.Madhab, in.CalcMethod)
	return err
}

func (r *Repo) ReplaceInterests(ctx context.Context, userID uuid.UUID, interests []string) error {
	tx, err := r.pool.Begin(ctx)
	if err != nil {
		return err
	}
	defer tx.Rollback(ctx)

	if _, err := tx.Exec(ctx, `DELETE FROM user_interests WHERE user_id = $1`, userID); err != nil {
		return err
	}
	for _, it := range interests {
		if _, err := tx.Exec(ctx,
			`INSERT INTO user_interests (user_id, interest) VALUES ($1, $2)
			 ON CONFLICT DO NOTHING`, userID, it); err != nil {
			return err
		}
	}
	return tx.Commit(ctx)
}

func (r *Repo) SetOnboardingDone(ctx context.Context, userID uuid.UUID) error {
	_, err := r.pool.Exec(ctx,
		`UPDATE profiles SET onboarding_done = true, updated_at = now() WHERE user_id = $1`, userID)
	return err
}

func (r *Repo) SetPhoto(ctx context.Context, userID uuid.UUID, url string) (*string, error) {
	tx, err := r.pool.Begin(ctx)
	if err != nil {
		return nil, fmt.Errorf("begin photo replacement: %w", err)
	}
	defer tx.Rollback(ctx)

	var previousURL *string
	if err := tx.QueryRow(ctx,
		`SELECT photo_url FROM profiles WHERE user_id = $1 FOR UPDATE`, userID,
	).Scan(&previousURL); err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, ErrNotFound
		}
		return nil, fmt.Errorf("read previous photo: %w", err)
	}
	if _, err := tx.Exec(ctx,
		`UPDATE profiles SET photo_url = $2, updated_at = now() WHERE user_id = $1`, userID, url,
	); err != nil {
		return nil, fmt.Errorf("replace photo: %w", err)
	}
	if err := tx.Commit(ctx); err != nil {
		return nil, fmt.Errorf("commit photo replacement: %w", err)
	}
	return previousURL, nil
}

// SetPhone attaches an OTP-verified number to an account that signed in some
// other way (Google/Apple). The NOT EXISTS guard is inside the UPDATE so the
// check and the write cannot interleave with a second account claiming the same
// number; a taken number leaves zero rows affected.
func (r *Repo) SetPhone(ctx context.Context, userID uuid.UUID, phone string) error {
	tag, err := r.pool.Exec(ctx, `
		UPDATE users SET phone = $2, updated_at = now()
		WHERE id = $1
		  AND NOT EXISTS (SELECT 1 FROM users o WHERE o.phone = $2 AND o.id <> $1)`,
		userID, phone)
	if err != nil {
		return err
	}
	if tag.RowsAffected() == 1 {
		return nil
	}
	return r.whyNoRows(ctx, userID, ErrPhoneTaken)
}

// SetEmail is the mirror of SetPhone for an account that signed in by phone or
// Telegram. Compared case-insensitively, stored lowercased.
func (r *Repo) SetEmail(ctx context.Context, userID uuid.UUID, email string) error {
	tag, err := r.pool.Exec(ctx, `
		UPDATE users SET email = $2, updated_at = now()
		WHERE id = $1
		  AND NOT EXISTS (SELECT 1 FROM users o WHERE lower(o.email) = lower($2) AND o.id <> $1)`,
		userID, email)
	if err != nil {
		return err
	}
	if tag.RowsAffected() == 1 {
		return nil
	}
	return r.whyNoRows(ctx, userID, ErrEmailTaken)
}

// PhoneAvailable reports whether the number is free for this user to claim. It
// is advisory — SetPhone re-checks atomically — but it saves sending an SMS for
// a number that could never be attached.
func (r *Repo) PhoneAvailable(ctx context.Context, userID uuid.UUID, phone string) (bool, error) {
	var taken bool
	err := r.pool.QueryRow(ctx,
		`SELECT EXISTS(SELECT 1 FROM users WHERE phone = $1 AND id <> $2)`, phone, userID).Scan(&taken)
	return !taken, err
}

// whyNoRows separates "no such user" from "the guard rejected it", which are a
// 404 and a 409 to the client.
func (r *Repo) whyNoRows(ctx context.Context, userID uuid.UUID, taken error) error {
	var exists bool
	if err := r.pool.QueryRow(ctx,
		`SELECT EXISTS(SELECT 1 FROM users WHERE id = $1)`, userID).Scan(&exists); err != nil {
		return err
	}
	if !exists {
		return ErrNotFound
	}
	return taken
}

func (r *Repo) PublicProfiles(ctx context.Context, ids []uuid.UUID) (map[uuid.UUID]PublicProfile, error) {
	out := make(map[uuid.UUID]PublicProfile, len(ids))
	if len(ids) == 0 {
		return out, nil
	}
	strIDs := make([]string, len(ids))
	for i, id := range ids {
		strIDs[i] = id.String()
	}
	rows, err := r.pool.Query(ctx, `
		SELECT user_id, display_name, handle, photo_url
		FROM profiles WHERE user_id = ANY($1::uuid[])`, strIDs)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	for rows.Next() {
		var p PublicProfile
		if err := rows.Scan(&p.UserID, &p.DisplayName, &p.Handle, &p.PhotoURL); err != nil {
			return nil, err
		}
		out[p.UserID] = p
	}
	return out, rows.Err()
}
