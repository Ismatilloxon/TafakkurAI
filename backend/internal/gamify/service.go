package gamify

import (
	"context"
	"time"

	"github.com/google/uuid"
)

// Service is the public surface other modules depend on. They call Award when a
// qualifying action happens and Get to read current progress.
type Service interface {
	EnsureExists(ctx context.Context, userID uuid.UUID) error
	Award(ctx context.Context, userID uuid.UUID, ev Event) (State, error)
	Get(ctx context.Context, userID uuid.UUID) (State, error)
}

type service struct {
	repo *Repo
	now  func() time.Time
}

func NewService(repo *Repo) Service {
	return &service{repo: repo, now: func() time.Time { return time.Now().UTC() }}
}

func (s *service) EnsureExists(ctx context.Context, userID uuid.UUID) error {
	return s.repo.EnsureExists(ctx, userID)
}

func (s *service) Award(ctx context.Context, userID uuid.UUID, ev Event) (State, error) {
	if err := s.repo.EnsureExists(ctx, userID); err != nil {
		return State{}, err
	}
	cur, err := s.repo.Get(ctx, userID)
	if err != nil {
		return State{}, err
	}
	next := Apply(cur, ev, s.now())
	if err := s.repo.Save(ctx, userID, next); err != nil {
		return State{}, err
	}
	return next, nil
}

func (s *service) Get(ctx context.Context, userID uuid.UUID) (State, error) {
	st, err := s.repo.Get(ctx, userID)
	if err == ErrNotFound {
		return NewState(), nil
	}
	return st, err
}
