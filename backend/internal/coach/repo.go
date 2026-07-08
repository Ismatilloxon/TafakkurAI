package coach

import (
	"context"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

type Repo struct{ pool *pgxpool.Pool }

func NewRepo(pool *pgxpool.Pool) *Repo { return &Repo{pool: pool} }

type MsgRow struct {
	ID        uuid.UUID
	Role      string
	Content   string
	CreatedAt time.Time
}

func (r *Repo) CreateConversation(ctx context.Context, userID uuid.UUID) (uuid.UUID, error) {
	id := uuid.New()
	_, err := r.pool.Exec(ctx,
		`INSERT INTO coach_conversations (id, user_id) VALUES ($1, $2)`, id, userID)
	return id, err
}

func (r *Repo) ConversationOwnedBy(ctx context.Context, convID, userID uuid.UUID) (bool, error) {
	var ok bool
	err := r.pool.QueryRow(ctx,
		`SELECT EXISTS(SELECT 1 FROM coach_conversations WHERE id=$1 AND user_id=$2)`,
		convID, userID).Scan(&ok)
	return ok, err
}

func (r *Repo) AddMessage(ctx context.Context, convID uuid.UUID, role, content string) (uuid.UUID, error) {
	id := uuid.New()
	_, err := r.pool.Exec(ctx,
		`INSERT INTO coach_messages (id, conversation_id, role, content) VALUES ($1,$2,$3,$4)`,
		id, convID, role, content)
	return id, err
}

// ContextMessages returns the last `limit` messages of a conversation in
// chronological order, for feeding back to Claude.
func (r *Repo) ContextMessages(ctx context.Context, convID uuid.UUID, limit int) ([]MsgRow, error) {
	rows, err := r.pool.Query(ctx, `
		SELECT id, role, content, created_at FROM (
			SELECT id, role, content, created_at
			FROM coach_messages WHERE conversation_id = $1
			ORDER BY created_at DESC LIMIT $2
		) sub ORDER BY created_at ASC`, convID, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return scanMsgs(rows)
}

// History returns messages newest-first, optionally older than `before`.
func (r *Repo) History(ctx context.Context, convID uuid.UUID, before time.Time, hasCursor bool, limit int) ([]MsgRow, error) {
	var rows pgx.Rows
	var err error
	if hasCursor {
		rows, err = r.pool.Query(ctx, `
			SELECT id, role, content, created_at FROM coach_messages
			WHERE conversation_id = $1 AND created_at < $2
			ORDER BY created_at DESC LIMIT $3`, convID, before, limit)
	} else {
		rows, err = r.pool.Query(ctx, `
			SELECT id, role, content, created_at FROM coach_messages
			WHERE conversation_id = $1
			ORDER BY created_at DESC LIMIT $2`, convID, limit)
	}
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return scanMsgs(rows)
}

func scanMsgs(rows pgx.Rows) ([]MsgRow, error) {
	var out []MsgRow
	for rows.Next() {
		var m MsgRow
		if err := rows.Scan(&m.ID, &m.Role, &m.Content, &m.CreatedAt); err != nil {
			return nil, err
		}
		out = append(out, m)
	}
	return out, rows.Err()
}
