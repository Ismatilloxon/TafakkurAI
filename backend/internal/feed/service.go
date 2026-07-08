package feed

import (
	"context"
	"encoding/base64"
	"fmt"
	"strconv"
	"strings"
	"time"

	"github.com/google/uuid"

	"github.com/tafakkur/backend/internal/gamify"
	"github.com/tafakkur/backend/internal/user"
)

const (
	DefaultLimit = 20
	MaxLimit     = 50
)

type SocialPostDTO struct {
	ID             string  `json:"id"`
	Text           string  `json:"text"`
	AuthorID       string  `json:"author_id"`
	AuthorName     string  `json:"author_name"`
	AuthorHandle   string  `json:"author_handle"`
	AuthorPhotoURL *string `json:"author_photo_url"`
	LikeCount      int     `json:"like_count"`
	LikedByMe      bool    `json:"liked_by_me"`
	CreatedAt      string  `json:"created_at"`
}

type FeedPage struct {
	Posts      []SocialPostDTO `json:"posts"`
	NextCursor *string         `json:"next_cursor"`
}

type Service interface {
	Publish(ctx context.Context, authorID uuid.UUID, text string) (SocialPostDTO, error)
	Feed(ctx context.Context, viewerID uuid.UUID, cursor string, limit int) (FeedPage, error)
	ToggleLike(ctx context.Context, userID, thoughtID uuid.UUID) (bool, int, error)
}

type service struct {
	repo   *Repo
	users  user.Service
	gamify gamify.Service
}

func NewService(repo *Repo, users user.Service, g gamify.Service) Service {
	return &service{repo: repo, users: users, gamify: g}
}

func (s *service) Publish(ctx context.Context, authorID uuid.UUID, text string) (SocialPostDTO, error) {
	p, err := s.repo.Insert(ctx, authorID, strings.TrimSpace(text))
	if err != nil {
		return SocialPostDTO{}, err
	}
	_, _ = s.gamify.Award(ctx, authorID, gamify.EventThoughtPublished)

	authors, err := s.users.PublicProfiles(ctx, []uuid.UUID{authorID})
	if err != nil {
		return SocialPostDTO{}, err
	}
	return toDTO(p, authors[authorID]), nil
}

func (s *service) Feed(ctx context.Context, viewerID uuid.UUID, cursor string, limit int) (FeedPage, error) {
	if limit <= 0 || limit > MaxLimit {
		limit = DefaultLimit
	}

	cTime, cID, hasCursor, err := decodeCursor(cursor)
	if err != nil {
		return FeedPage{}, err
	}

	rows, err := s.repo.Feed(ctx, viewerID, cTime, cID, hasCursor, limit+1)
	if err != nil {
		return FeedPage{}, err
	}

	var next *string
	if len(rows) > limit {
		last := rows[limit-1]
		c := encodeCursor(last.CreatedAt, last.ID)
		next = &c
		rows = rows[:limit]
	}

	ids := make([]uuid.UUID, 0, len(rows))
	for _, r := range rows {
		ids = append(ids, r.AuthorID)
	}
	authors, err := s.users.PublicProfiles(ctx, ids)
	if err != nil {
		return FeedPage{}, err
	}

	posts := make([]SocialPostDTO, 0, len(rows))
	for _, r := range rows {
		posts = append(posts, toDTO(r, authors[r.AuthorID]))
	}
	return FeedPage{Posts: posts, NextCursor: next}, nil
}

func (s *service) ToggleLike(ctx context.Context, userID, thoughtID uuid.UUID) (bool, int, error) {
	return s.repo.ToggleLike(ctx, thoughtID, userID)
}

func toDTO(p postRow, author user.PublicProfile) SocialPostDTO {
	dto := SocialPostDTO{
		ID:        p.ID.String(),
		Text:      p.Text,
		AuthorID:  p.AuthorID.String(),
		LikeCount: p.LikeCount,
		LikedByMe: p.LikedByMe,
		CreatedAt: p.CreatedAt.Format(time.RFC3339),
	}
	if author.DisplayName != "" {
		dto.AuthorName = author.DisplayName
		dto.AuthorHandle = author.Handle
		dto.AuthorPhotoURL = author.PhotoURL
	} else {
		dto.AuthorName = "TafakkurAI user"
	}
	return dto
}

// Cursor: base64url("<unixNano>|<uuid>"), newest-first keyset pagination.
func encodeCursor(t time.Time, id uuid.UUID) string {
	raw := fmt.Sprintf("%d|%s", t.UnixNano(), id.String())
	return base64.RawURLEncoding.EncodeToString([]byte(raw))
}

func decodeCursor(cursor string) (time.Time, uuid.UUID, bool, error) {
	if cursor == "" {
		return time.Time{}, uuid.Nil, false, nil
	}
	raw, err := base64.RawURLEncoding.DecodeString(cursor)
	if err != nil {
		return time.Time{}, uuid.Nil, false, fmt.Errorf("bad cursor")
	}
	parts := strings.SplitN(string(raw), "|", 2)
	if len(parts) != 2 {
		return time.Time{}, uuid.Nil, false, fmt.Errorf("bad cursor")
	}
	nanos, err := strconv.ParseInt(parts[0], 10, 64)
	if err != nil {
		return time.Time{}, uuid.Nil, false, fmt.Errorf("bad cursor")
	}
	id, err := uuid.Parse(parts[1])
	if err != nil {
		return time.Time{}, uuid.Nil, false, fmt.Errorf("bad cursor")
	}
	return time.Unix(0, nanos).UTC(), id, true, nil
}
