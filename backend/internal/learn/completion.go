package learn

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"time"
	_ "time/tzdata" // The production image is distroless and may not carry zoneinfo.

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"

	"github.com/tafakkur/backend/internal/gamify"
)

var (
	ErrInvalidCompletion  = errors.New("invalid lesson completion")
	ErrCompletionConflict = errors.New("lesson completion event belongs to another lesson")
	ErrAwardsUnavailable  = errors.New("gamification awards are unavailable")
)

// CompleteCommand is the immutable identity and civil-time context of one lesson
// completion. LocalDate is deliberately supplied by the client: server UTC is not
// the user's day around midnight.
type CompleteCommand struct {
	EventID   string
	LocalDate string
	Timezone  string
}

// Completion is the durable source fact. Result is nil only in the recoverable gap
// between inserting the source row and storing the idempotent gamification response.
type Completion struct {
	LessonID    uuid.UUID
	EventID     uuid.UUID
	LocalDate   time.Time
	Timezone    string
	CompletedAt time.Time
	Result      *gamify.State
}

type CompleteResult struct {
	Completion   Completion
	Gamification gamify.State
}

type normalizedCompleteCommand struct {
	eventID   uuid.UUID
	localDate time.Time
	timezone  string
}

func normalizeCompleteCommand(command CompleteCommand, now time.Time) (normalizedCompleteCommand, error) {
	eventID, err := uuid.Parse(command.EventID)
	if err != nil || eventID == uuid.Nil {
		return normalizedCompleteCommand{}, fmt.Errorf("%w: event id must be a UUID", ErrInvalidCompletion)
	}
	if command.LocalDate == "" || strings.TrimSpace(command.LocalDate) != command.LocalDate {
		return normalizedCompleteCommand{}, fmt.Errorf("%w: local date must be YYYY-MM-DD", ErrInvalidCompletion)
	}
	localDate, err := time.Parse(time.DateOnly, command.LocalDate)
	if err != nil || localDate.Format(time.DateOnly) != command.LocalDate {
		return normalizedCompleteCommand{}, fmt.Errorf("%w: local date must be YYYY-MM-DD", ErrInvalidCompletion)
	}
	// A completion may retry long after it happened, but a far-future date would pin
	// streak calculations ahead of the user's real calendar indefinitely.
	serverTomorrow := time.Date(now.Year(), now.Month(), now.Day(), 0, 0, 0, 0, time.UTC).AddDate(0, 0, 1)
	if localDate.After(serverTomorrow) {
		return normalizedCompleteCommand{}, fmt.Errorf("%w: local date is too far in the future", ErrInvalidCompletion)
	}
	if command.Timezone == "" || len(command.Timezone) > 64 || strings.TrimSpace(command.Timezone) != command.Timezone {
		return normalizedCompleteCommand{}, fmt.Errorf("%w: timezone must be a 1..64 character IANA id", ErrInvalidCompletion)
	}
	if _, err := time.LoadLocation(command.Timezone); err != nil {
		return normalizedCompleteCommand{}, fmt.Errorf("%w: timezone must be a valid IANA id", ErrInvalidCompletion)
	}
	return normalizedCompleteCommand{
		eventID:   eventID,
		localDate: localDate,
		timezone:  command.Timezone,
	}, nil
}

func (r *Repo) getOrCreateCompletion(
	ctx context.Context,
	userID uuid.UUID,
	lessonID uuid.UUID,
	command normalizedCompleteCommand,
) (Completion, error) {
	_, err := r.pool.Exec(ctx, `
		INSERT INTO lesson_completions (user_id, lesson_id, event_id, local_date, timezone)
		VALUES ($1, $2, $3, $4, $5)
		ON CONFLICT DO NOTHING`,
		userID, lessonID, command.eventID, command.localDate, command.timezone)
	if err != nil {
		return Completion{}, fmt.Errorf("insert lesson completion: %w", err)
	}

	completion, err := r.completion(ctx, userID, lessonID)
	if err == nil {
		// The lesson is complete already. Its first event/date/timezone are canonical,
		// even when a second device generated a different command UUID.
		return completion, nil
	}
	if !errors.Is(err, errCompletionNotFound) {
		return Completion{}, err
	}

	// No row for this lesson means the unique event UUID collided with a different
	// lesson. Treat that as a command conflict, never as another completion.
	var existingLesson uuid.UUID
	err = r.pool.QueryRow(ctx, `
		SELECT lesson_id
		FROM lesson_completions
		WHERE user_id = $1 AND event_id = $2`, userID, command.eventID,
	).Scan(&existingLesson)
	if err == nil {
		return Completion{}, ErrCompletionConflict
	}
	if !errors.Is(err, pgx.ErrNoRows) {
		return Completion{}, fmt.Errorf("resolve lesson completion event conflict: %w", err)
	}
	return Completion{}, fmt.Errorf("read inserted lesson completion: %w", errCompletionNotFound)
}

var errCompletionNotFound = errors.New("lesson completion not found")

func (r *Repo) completion(ctx context.Context, userID, lessonID uuid.UUID) (Completion, error) {
	var (
		completion                Completion
		streak, level, xp, xpNext *int
	)
	err := r.pool.QueryRow(ctx, `
		SELECT lesson_id, event_id, local_date, timezone, completed_at,
		       result_streak, result_level, result_xp, result_xp_next
		FROM lesson_completions
		WHERE user_id = $1 AND lesson_id = $2`, userID, lessonID,
	).Scan(
		&completion.LessonID,
		&completion.EventID,
		&completion.LocalDate,
		&completion.Timezone,
		&completion.CompletedAt,
		&streak,
		&level,
		&xp,
		&xpNext,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return Completion{}, errCompletionNotFound
	}
	if err != nil {
		return Completion{}, fmt.Errorf("get lesson completion: %w", err)
	}
	if streak != nil && level != nil && xp != nil && xpNext != nil {
		completion.Result = &gamify.State{
			Streak: *streak,
			Level:  *level,
			XP:     *xp,
			XPNext: *xpNext,
		}
	}
	return completion, nil
}

// saveCompletionResult fills the response once and then returns the stored row.
// Concurrent retries receive the exact same values, not the user's newer global XP.
func (r *Repo) saveCompletionResult(
	ctx context.Context,
	userID uuid.UUID,
	completion Completion,
	state gamify.State,
) (Completion, error) {
	_, err := r.pool.Exec(ctx, `
		UPDATE lesson_completions
		SET result_streak = $3,
		    result_level = $4,
		    result_xp = $5,
		    result_xp_next = $6
		WHERE user_id = $1
		  AND lesson_id = $2
		  AND result_streak IS NULL`,
		userID, completion.LessonID, state.Streak, state.Level, state.XP, state.XPNext)
	if err != nil {
		return Completion{}, fmt.Errorf("store lesson completion result: %w", err)
	}
	return r.completion(ctx, userID, completion.LessonID)
}

func (s *service) Complete(
	ctx context.Context,
	userID uuid.UUID,
	lessonID uuid.UUID,
	command CompleteCommand,
) (CompleteResult, error) {
	normalized, err := normalizeCompleteCommand(command, s.now())
	if err != nil {
		return CompleteResult{}, err
	}
	if _, err := s.repo.GetLesson(ctx, userID, lessonID); err != nil {
		return CompleteResult{}, err
	}

	completion, err := s.repo.getOrCreateCompletion(ctx, userID, lessonID, normalized)
	if err != nil {
		return CompleteResult{}, err
	}
	if completion.Result != nil {
		return CompleteResult{Completion: completion, Gamification: *completion.Result}, nil
	}
	if s.gamify == nil {
		return CompleteResult{}, ErrAwardsUnavailable
	}

	award, err := s.gamify.AwardOnce(ctx, userID, gamify.AwardCommand{
		Event:          gamify.EventLessonCompleted,
		IdempotencyKey: "lesson:" + completion.EventID.String(),
		LocalDate:      completion.LocalDate,
	})
	if err != nil {
		// The source row remains pending. A retry resumes with the same canonical
		// event ID and AwardOnce either applies once or returns its first result.
		return CompleteResult{}, fmt.Errorf("award lesson completion: %w", err)
	}

	completion, err = s.repo.saveCompletionResult(ctx, userID, completion, award.State)
	if err != nil {
		return CompleteResult{}, err
	}
	if completion.Result == nil {
		return CompleteResult{}, errors.New("lesson completion result was not persisted")
	}
	return CompleteResult{Completion: completion, Gamification: *completion.Result}, nil
}
