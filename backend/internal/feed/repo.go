// Package feed owns the "thoughts" social feed: posts, the newest-first feed,
// and likes. It owns the thoughts and thought_likes tables and joins between
// them, but fetches post-author display data through user.Service.
package feed

import (
	"context"
	"errors"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

var ErrNotFound = errors.New("thought not found")

type Repo struct{ pool *pgxpool.Pool }

func NewRepo(pool *pgxpool.Pool) *Repo { return &Repo{pool: pool} }

type postRow struct {
	ID        uuid.UUID
	AuthorID  uuid.UUID
	Text      string
	LikeCount int
	CreatedAt time.Time
	LikedByMe bool
}

func (r *Repo) Insert(ctx context.Context, authorID uuid.UUID, text string) (postRow, error) {
	p := postRow{ID: uuid.New(), AuthorID: authorID, Text: text}
	err := r.pool.QueryRow(ctx, `
		INSERT INTO thoughts (id, user_id, text)
		VALUES ($1, $2, $3)
		RETURNING like_count, created_at`,
		p.ID, authorID, text,
	).Scan(&p.LikeCount, &p.CreatedAt)
	return p, err
}

// Feed returns up to limit posts newest-first. When hasCursor is set, only rows
// strictly older than (cursorTime, cursorID) are returned.
func (r *Repo) Feed(ctx context.Context, viewerID uuid.UUID, cursorTime time.Time, cursorID uuid.UUID, hasCursor bool, limit int) ([]postRow, error) {
	var rows pgx.Rows
	var err error
	if hasCursor {
		rows, err = r.pool.Query(ctx, `
			SELECT t.id, t.user_id, t.text, t.like_count, t.created_at,
			       (l.user_id IS NOT NULL) AS liked
			FROM thoughts t
			LEFT JOIN thought_likes l ON l.thought_id = t.id AND l.user_id = $1
			WHERE (t.created_at, t.id) < ($2, $3::uuid)
			ORDER BY t.created_at DESC, t.id DESC
			LIMIT $4`,
			viewerID, cursorTime, cursorID.String(), limit)
	} else {
		rows, err = r.pool.Query(ctx, `
			SELECT t.id, t.user_id, t.text, t.like_count, t.created_at,
			       (l.user_id IS NOT NULL) AS liked
			FROM thoughts t
			LEFT JOIN thought_likes l ON l.thought_id = t.id AND l.user_id = $1
			ORDER BY t.created_at DESC, t.id DESC
			LIMIT $2`,
			viewerID, limit)
	}
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var out []postRow
	for rows.Next() {
		var p postRow
		if err := rows.Scan(&p.ID, &p.AuthorID, &p.Text, &p.LikeCount, &p.CreatedAt, &p.LikedByMe); err != nil {
			return nil, err
		}
		out = append(out, p)
	}
	return out, rows.Err()
}

// ToggleLike flips the viewer's like on a thought and returns the new state.
func (r *Repo) ToggleLike(ctx context.Context, thoughtID, userID uuid.UUID) (liked bool, count int, err error) {
	tx, err := r.pool.Begin(ctx)
	if err != nil {
		return false, 0, err
	}
	defer tx.Rollback(ctx)

	var exists bool
	if err = tx.QueryRow(ctx,
		`SELECT EXISTS(SELECT 1 FROM thought_likes WHERE thought_id=$1 AND user_id=$2)`,
		thoughtID, userID).Scan(&exists); err != nil {
		return false, 0, err
	}

	if exists {
		if _, err = tx.Exec(ctx,
			`DELETE FROM thought_likes WHERE thought_id=$1 AND user_id=$2`, thoughtID, userID); err != nil {
			return false, 0, err
		}
		err = tx.QueryRow(ctx,
			`UPDATE thoughts SET like_count = GREATEST(like_count - 1, 0) WHERE id=$1 RETURNING like_count`,
			thoughtID).Scan(&count)
		liked = false
	} else {
		if _, err = tx.Exec(ctx,
			`INSERT INTO thought_likes (thought_id, user_id) VALUES ($1,$2) ON CONFLICT DO NOTHING`,
			thoughtID, userID); err != nil {
			return false, 0, err
		}
		err = tx.QueryRow(ctx,
			`UPDATE thoughts SET like_count = like_count + 1 WHERE id=$1 RETURNING like_count`,
			thoughtID).Scan(&count)
		liked = true
	}
	if errors.Is(err, pgx.ErrNoRows) {
		return false, 0, ErrNotFound
	}
	if err != nil {
		return false, 0, err
	}
	return liked, count, tx.Commit(ctx)
}
