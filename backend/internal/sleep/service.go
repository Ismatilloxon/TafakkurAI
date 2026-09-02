package sleep

import (
	"context"
	"errors"
	"time"

	"github.com/google/uuid"
)

// Service is the sleep module's behaviour, independent of HTTP.
//
// An interface so the handler can be tested against a fake and so the weekly-digest job can
// depend on the same contract the API does.
type Service interface {
	Upsert(ctx context.Context, userID uuid.UUID, in UpsertSessionInput) (Session, error)
	UpsertBatch(ctx context.Context, userID uuid.UUID, in []UpsertSessionInput) ([]UpsertResult, error)
	List(ctx context.Context, userID uuid.UUID, f Filter) ([]Session, error)
	Get(ctx context.Context, userID, id uuid.UUID) (Session, error)
	Delete(ctx context.Context, userID, id uuid.UUID) error
	Changes(ctx context.Context, userID uuid.UUID, since time.Time, limit int) ([]Session, []RoutineMark, error)
	Stats(ctx context.Context, userID uuid.UUID, from, to time.Time) (Stats, error)

	MarkRoutine(ctx context.Context, userID uuid.UUID, in RoutineMarkInput) error
	Routine(ctx context.Context, userID uuid.UUID, from, to time.Time) ([]RoutineMark, error)

	Settings(ctx context.Context, userID uuid.UUID) (Settings, error)
	SaveSettings(ctx context.Context, userID uuid.UUID, in SettingsInput) (Settings, error)
}

type service struct{ repo *Repo }

func NewService(repo *Repo) Service { return &service{repo: repo} }

// Upsert stores one session.
//
// Two identity questions are answered before the write, in this order:
//
//  1. Does this external_id already exist under a DIFFERENT client_id? That is the same
//     underlying health record uploaded from a second device. Adopting the stored client_id
//     makes the write land on the existing row instead of creating a duplicate night, which
//     is the single most common way a multi-device sleep history goes wrong.
//  2. Is the stored row stronger? Answered atomically inside the SQL upsert, not here — see
//     Repo.Upsert.
func (s *service) Upsert(ctx context.Context, userID uuid.UUID, in UpsertSessionInput) (Session, error) {
	session, err := toSession(userID, in)
	if err != nil {
		return Session{}, err
	}

	if in.ExternalID != nil && *in.ExternalID != "" {
		existing, err := s.repo.FindByExternalID(ctx, userID, *in.ExternalID)
		switch {
		case err == nil:
			session.ClientID = existing.ClientID
			session.ID = existing.ID
		case errors.Is(err, ErrNotFound):
			// First time this health record has been seen. Fall through.
		default:
			return Session{}, err
		}
	}

	return s.repo.Upsert(ctx, session)
}

// UpsertBatch is the historical-import path.
//
// One session failing must not abort the rest: a year of imported nights containing a single
// malformed record should store 364 nights, not zero. Each result reports its own outcome.
func (s *service) UpsertBatch(ctx context.Context, userID uuid.UUID, in []UpsertSessionInput) ([]UpsertResult, error) {
	out := make([]UpsertResult, 0, len(in))
	for _, item := range in {
		stored, err := s.Upsert(ctx, userID, item)
		switch {
		case errors.Is(err, ErrSuperseded):
			out = append(out, UpsertResult{ClientID: item.ClientID, Status: "superseded"})
		case err != nil:
			out = append(out, UpsertResult{ClientID: item.ClientID, Status: "rejected"})
		default:
			out = append(out, UpsertResult{ClientID: item.ClientID, Status: "applied", ID: stored.ID.String()})
		}
	}
	return out, nil
}

func (s *service) List(ctx context.Context, userID uuid.UUID, f Filter) ([]Session, error) {
	return s.repo.List(ctx, userID, f)
}

func (s *service) Get(ctx context.Context, userID, id uuid.UUID) (Session, error) {
	return s.repo.Get(ctx, userID, id)
}

func (s *service) Delete(ctx context.Context, userID, id uuid.UUID) error {
	return s.repo.SoftDelete(ctx, userID, id)
}

// Changes returns both streams a client needs to converge, sharing one cursor.
//
// The cursor is the max updated_at across BOTH streams, and `more` is true if either was
// truncated. Using a single cursor for two tables is safe because it only ever moves to a
// value both have been read up to; using two would double the client's bookkeeping for no
// gain, and getting that bookkeeping subtly wrong loses rows silently.
func (s *service) Changes(ctx context.Context, userID uuid.UUID, since time.Time, limit int) ([]Session, []RoutineMark, error) {
	sessions, err := s.repo.ChangedSince(ctx, userID, since, limit)
	if err != nil {
		return nil, nil, err
	}
	marks, err := s.repo.MarksChangedSince(ctx, userID, since, limit)
	if err != nil {
		return nil, nil, err
	}
	return sessions, marks, nil
}

func (s *service) Stats(ctx context.Context, userID uuid.UUID, from, to time.Time) (Stats, error) {
	return s.repo.Aggregate(ctx, userID, from, to)
}

func (s *service) MarkRoutine(ctx context.Context, userID uuid.UUID, in RoutineMarkInput) error {
	date, err := time.Parse(DateLayout, in.DateKey)
	if err != nil {
		return ErrInvalidInput
	}
	phase := Phase(in.Phase)
	if !phase.Valid() {
		return ErrInvalidInput
	}
	return s.repo.UpsertMark(ctx, userID, RoutineMark{
		MarkID:  in.MarkID,
		StepID:  in.StepID,
		Phase:   phase,
		DateKey: date,
	}, in.Completed)
}

func (s *service) Routine(ctx context.Context, userID uuid.UUID, from, to time.Time) ([]RoutineMark, error) {
	return s.repo.MarksBetween(ctx, userID, from, to)
}

func (s *service) Settings(ctx context.Context, userID uuid.UUID) (Settings, error) {
	return s.repo.Settings(ctx, userID)
}

func (s *service) SaveSettings(ctx context.Context, userID uuid.UUID, in SettingsInput) (Settings, error) {
	settings := Settings{
		Goals:                 in.Goals,
		EnabledStepIDs:        in.EnabledStepIDs,
		IslamicRoutineEnabled: in.IslamicRoutineEnabled,
		Flags:                 in.Flags,
	}
	if len(settings.Goals) == 0 {
		settings.Goals = []byte(`{}`)
	}
	if len(settings.Flags) == 0 {
		settings.Flags = []byte(`{}`)
	}
	if settings.EnabledStepIDs == nil {
		settings.EnabledStepIDs = []string{}
	}
	return s.repo.PutSettings(ctx, userID, settings)
}

// ErrInvalidInput is returned for a body that passed struct validation but is still
// semantically wrong (an unparseable date, an impossible window).
var ErrInvalidInput = errors.New("invalid sleep input")

// toSession converts a request body into a domain entity, applying the invariants the
// database constraints also enforce — so a bad body gets a 422 with a reason rather than a
// 500 from a constraint violation.
func toSession(userID uuid.UUID, in UpsertSessionInput) (Session, error) {
	date, err := time.Parse(DateLayout, in.DateKey)
	if err != nil {
		return Session{}, ErrInvalidInput
	}
	kind := Kind(in.Kind)
	source := Source(in.Source)
	if !kind.Valid() || !source.Valid() {
		return Session{}, ErrInvalidInput
	}
	if in.EndAt.Before(in.StartAt) {
		return Session{}, ErrInvalidInput
	}

	// In-bed defaults to the asleep window when the source did not report it. Clamping rather
	// than trusting the client keeps in_bed ⊇ asleep, which the CHECK constraint requires and
	// which sleep efficiency divides by.
	inBedStart := in.InBedStartAt
	if inBedStart.IsZero() || inBedStart.After(in.StartAt) {
		inBedStart = in.StartAt
	}
	inBedEnd := in.InBedEndAt
	if inBedEnd.IsZero() || inBedEnd.Before(in.EndAt) {
		inBedEnd = in.EndAt
	}

	segments := []byte(in.Segments)
	if len(segments) == 0 {
		segments = []byte(`[]`)
	}
	vitals := []byte(in.Vitals)
	if len(vitals) == 0 {
		vitals = []byte(`{}`)
	}
	tags := in.Tags
	if tags == nil {
		tags = []string{}
	}

	return Session{
		UserID:        userID,
		ClientID:      in.ClientID,
		ExternalID:    in.ExternalID,
		Kind:          kind,
		Source:        source,
		StartAt:       in.StartAt,
		EndAt:         in.EndAt,
		InBedStartAt:  inBedStart,
		InBedEndAt:    inBedEnd,
		Segments:      segments,
		Vitals:        vitals,
		DateKey:       date,
		MoodOnWaking:  in.MoodOnWaking,
		Note:          in.Note,
		Tags:          tags,
		Score:         in.Score,
		RecoveryScore: in.RecoveryScore,
		AsleepMinutes: in.AsleepMinutes,
	}, nil
}
