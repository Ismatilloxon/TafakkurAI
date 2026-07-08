package coach

import (
	"context"
	"encoding/base64"
	"errors"
	"strconv"
	"strings"
	"time"

	"github.com/google/uuid"
)

var ErrConversationNotFound = errors.New("conversation not found")

const contextLimit = 20

type Service struct {
	repo   *Repo
	claude *Client
}

func NewService(repo *Repo, claude *Client) *Service {
	return &Service{repo: repo, claude: claude}
}

type MessageDTO struct {
	ID        string `json:"id"`
	Role      string `json:"role"`
	Content   string `json:"content"`
	CreatedAt string `json:"created_at"`
}

type HistoryPage struct {
	ConversationID string       `json:"conversation_id"`
	Messages       []MessageDTO `json:"messages"`
	NextCursor     *string      `json:"next_cursor"`
}

// StreamMessage persists the user's message, streams Claude's reply through
// onDelta, then persists the assistant's reply. It returns the conversation id
// (created here if the caller passed none).
func (s *Service) StreamMessage(ctx context.Context, userID uuid.UUID, convID *uuid.UUID, text string, onDelta func(string) error) (uuid.UUID, error) {
	var cid uuid.UUID
	if convID != nil {
		ok, err := s.repo.ConversationOwnedBy(ctx, *convID, userID)
		if err != nil {
			return uuid.Nil, err
		}
		if !ok {
			return uuid.Nil, ErrConversationNotFound
		}
		cid = *convID
	} else {
		var err error
		if cid, err = s.repo.CreateConversation(ctx, userID); err != nil {
			return uuid.Nil, err
		}
	}

	if _, err := s.repo.AddMessage(ctx, cid, "user", text); err != nil {
		return cid, err
	}

	ctxMsgs, err := s.repo.ContextMessages(ctx, cid, contextLimit)
	if err != nil {
		return cid, err
	}
	msgs := make([]Message, 0, len(ctxMsgs))
	for _, m := range ctxMsgs {
		msgs = append(msgs, Message{Role: m.Role, Content: m.Content})
	}

	full, err := s.claude.Stream(ctx, msgs, onDelta)
	if err != nil {
		return cid, err
	}
	if strings.TrimSpace(full) != "" {
		_, _ = s.repo.AddMessage(ctx, cid, "assistant", full)
	}
	return cid, nil
}

func (s *Service) History(ctx context.Context, userID, convID uuid.UUID, cursor string, limit int) (HistoryPage, error) {
	ok, err := s.repo.ConversationOwnedBy(ctx, convID, userID)
	if err != nil {
		return HistoryPage{}, err
	}
	if !ok {
		return HistoryPage{}, ErrConversationNotFound
	}
	if limit <= 0 || limit > 100 {
		limit = 30
	}

	before, has, err := decodeCursor(cursor)
	if err != nil {
		return HistoryPage{}, err
	}
	rows, err := s.repo.History(ctx, convID, before, has, limit+1)
	if err != nil {
		return HistoryPage{}, err
	}

	var next *string
	if len(rows) > limit {
		c := encodeCursor(rows[limit-1].CreatedAt)
		next = &c
		rows = rows[:limit]
	}

	msgs := make([]MessageDTO, 0, len(rows))
	for _, m := range rows {
		msgs = append(msgs, MessageDTO{
			ID:        m.ID.String(),
			Role:      m.Role,
			Content:   m.Content,
			CreatedAt: m.CreatedAt.Format(time.RFC3339),
		})
	}
	return HistoryPage{ConversationID: convID.String(), Messages: msgs, NextCursor: next}, nil
}

func encodeCursor(t time.Time) string {
	return base64.RawURLEncoding.EncodeToString([]byte(strconv.FormatInt(t.UnixNano(), 10)))
}

func decodeCursor(cursor string) (time.Time, bool, error) {
	if cursor == "" {
		return time.Time{}, false, nil
	}
	raw, err := base64.RawURLEncoding.DecodeString(cursor)
	if err != nil {
		return time.Time{}, false, errors.New("bad cursor")
	}
	nanos, err := strconv.ParseInt(string(raw), 10, 64)
	if err != nil {
		return time.Time{}, false, errors.New("bad cursor")
	}
	return time.Unix(0, nanos).UTC(), true, nil
}
