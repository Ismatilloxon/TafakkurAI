package gamify

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
)

const (
	MaxAwardBatch          = 128
	MaxIdempotencyKeyBytes = 200
)

var (
	ErrInvalidAwardCommand = errors.New("invalid gamification award command")
)

// AwardCommand carries the stable identity and civil date of one qualifying
// activity. LocalDate is a calendar value, not an instant; its Year/Month/Day
// must already be interpreted in the user's timezone.
type AwardCommand struct {
	Event          Event
	IdempotencyKey string
	LocalDate      time.Time
}

// AwardResult reports the state produced by the first execution of a command.
// Applied is false on replay; State remains the original semantic response.
type AwardResult struct {
	State   State
	Applied bool
}

// Service is the public surface other modules depend on. Every write requires a
// source entity/event ID and an explicit civil date; there is intentionally no
// keyless award method that could silently double-count a retry.
type Service interface {
	EnsureExists(ctx context.Context, userID uuid.UUID) error
	AwardOnce(ctx context.Context, userID uuid.UUID, command AwardCommand) (AwardResult, error)
	AwardBatch(ctx context.Context, userID uuid.UUID, commands []AwardCommand) (State, error)
	Get(ctx context.Context, userID uuid.UUID) (State, error)
}

type service struct{ repo *Repo }

func NewService(repo *Repo) Service {
	return &service{repo: repo}
}

func (s *service) EnsureExists(ctx context.Context, userID uuid.UUID) error {
	return s.repo.EnsureExists(ctx, userID)
}

func (s *service) AwardOnce(
	ctx context.Context,
	userID uuid.UUID,
	command AwardCommand,
) (AwardResult, error) {
	commands, err := normalizedCommands(userID, []AwardCommand{command})
	if err != nil {
		return AwardResult{}, err
	}
	result, err := s.repo.awardBatch(ctx, userID, commands)
	if err != nil {
		return AwardResult{}, err
	}
	return result.Awards[0], nil
}

func (s *service) AwardBatch(
	ctx context.Context,
	userID uuid.UUID,
	commands []AwardCommand,
) (State, error) {
	if len(commands) == 0 {
		return s.Get(ctx, userID)
	}
	normalized, err := normalizedCommands(userID, commands)
	if err != nil {
		return State{}, err
	}
	result, err := s.repo.awardBatch(ctx, userID, normalized)
	if err != nil {
		return State{}, err
	}
	return result.State, nil
}

// AwardBatchTx is the atomic integration boundary for workflows that already own a
// PostgreSQL transaction. It is intentionally additive to Service: ordinary feature
// callers cannot accidentally hold database transactions, while the sync orchestrator
// can require this capability without weakening existing consumer interfaces.
func (s *service) AwardBatchTx(
	ctx context.Context,
	tx pgx.Tx,
	userID uuid.UUID,
	commands []AwardCommand,
) (State, error) {
	if tx == nil {
		return State{}, fmt.Errorf("%w: transaction is required", ErrInvalidAwardCommand)
	}
	normalized, err := normalizedCommands(userID, commands)
	if err != nil {
		return State{}, err
	}
	result, err := s.repo.awardBatchTx(ctx, tx, userID, normalized)
	if err != nil {
		return State{}, err
	}
	return result.State, nil
}

func normalizedCommands(userID uuid.UUID, commands []AwardCommand) ([]AwardCommand, error) {
	if userID == uuid.Nil {
		return nil, fmt.Errorf("%w: user id is required", ErrInvalidAwardCommand)
	}
	if len(commands) == 0 || len(commands) > MaxAwardBatch {
		return nil, fmt.Errorf(
			"%w: batch size must be between 1 and %d",
			ErrInvalidAwardCommand,
			MaxAwardBatch,
		)
	}

	normalized := make([]AwardCommand, len(commands))
	for i, command := range commands {
		switch {
		case !command.Event.valid():
			return nil, fmt.Errorf(
				"%w: command %d has unknown event %q",
				ErrInvalidAwardCommand,
				i,
				command.Event,
			)
		case command.IdempotencyKey == "":
			return nil, fmt.Errorf(
				"%w: command %d idempotency key is required",
				ErrInvalidAwardCommand,
				i,
			)
		case len(command.IdempotencyKey) > MaxIdempotencyKeyBytes:
			return nil, fmt.Errorf(
				"%w: command %d idempotency key exceeds %d bytes",
				ErrInvalidAwardCommand,
				i,
				MaxIdempotencyKeyBytes,
			)
		case strings.TrimSpace(command.IdempotencyKey) != command.IdempotencyKey:
			return nil, fmt.Errorf(
				"%w: command %d idempotency key has surrounding whitespace",
				ErrInvalidAwardCommand,
				i,
			)
		case command.LocalDate.IsZero():
			return nil, fmt.Errorf(
				"%w: command %d local date is required",
				ErrInvalidAwardCommand,
				i,
			)
		}
		command.LocalDate = civilDate(command.LocalDate)
		normalized[i] = command
	}
	return normalized, nil
}

func (s *service) Get(ctx context.Context, userID uuid.UUID) (State, error) {
	st, err := s.repo.Get(ctx, userID)
	if errors.Is(err, ErrNotFound) {
		return NewState(), nil
	}
	return st, err
}
