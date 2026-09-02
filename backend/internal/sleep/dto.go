package sleep

import (
	"encoding/json"
	"time"
)

// ---- requests ----

// UpsertSessionInput is the write body. Keyed on client_id, so POSTing it twice is one row.
type UpsertSessionInput struct {
	ClientID   string  `json:"client_id" validate:"required,max=64"`
	ExternalID *string `json:"external_id" validate:"omitempty,max=200"`

	Kind   string `json:"kind" validate:"required,oneof=night nap"`
	Source string `json:"source" validate:"required,oneof=watch health_store automatic manual"`

	StartAt      time.Time `json:"start_at" validate:"required"`
	EndAt        time.Time `json:"end_at" validate:"required"`
	InBedStartAt time.Time `json:"in_bed_start_at"`
	InBedEndAt   time.Time `json:"in_bed_end_at"`

	// Passed through untouched. json.RawMessage rather than a typed struct so a client that
	// ships a new stage or a new vital does not need a backend release to store it.
	Segments json.RawMessage `json:"segments"`
	Vitals   json.RawMessage `json:"vitals"`

	DateKey       string   `json:"date_key" validate:"required,len=10"`
	MoodOnWaking  *string  `json:"mood_on_waking" validate:"omitempty,max=32"`
	Note          *string  `json:"note" validate:"omitempty,max=2000"`
	Tags          []string `json:"tags" validate:"omitempty,max=20,dive,max=40"`
	Score         *int16   `json:"score" validate:"omitempty,min=0,max=100"`
	RecoveryScore *int16   `json:"recovery_score" validate:"omitempty,min=0,max=100"`
	AsleepMinutes int      `json:"asleep_minutes" validate:"min=0,max=1200"`
}

// BatchUpsertInput is the bulk path used by a first-run historical import.
//
// A year of nights is ~365 round trips one at a time; this makes it a handful. Capped at 200
// so one request cannot hold a connection long enough to matter.
type BatchUpsertInput struct {
	Sessions []UpsertSessionInput `json:"sessions" validate:"required,min=1,max=200,dive"`
}

// RoutineMarkInput ticks or un-ticks one step.
type RoutineMarkInput struct {
	MarkID    string `json:"mark_id" validate:"required,max=128"`
	StepID    string `json:"step_id" validate:"required,max=64"`
	Phase     string `json:"phase" validate:"required,oneof=bedtime morning"`
	DateKey   string `json:"date_key" validate:"required,len=10"`
	Completed bool   `json:"completed"`
}

// SettingsInput mirrors the client's SleepSettings.
type SettingsInput struct {
	Goals                 json.RawMessage `json:"goals"`
	EnabledStepIDs        []string        `json:"enabled_step_ids" validate:"omitempty,max=100,dive,max=64"`
	IslamicRoutineEnabled bool            `json:"islamic_routine_enabled"`
	Flags                 json.RawMessage `json:"flags"`
}

// ---- responses ----

// SessionDTO is the wire shape of a session.
type SessionDTO struct {
	ID       string  `json:"id"`
	ClientID string  `json:"client_id"`
	External *string `json:"external_id,omitempty"`

	Kind   string `json:"kind"`
	Source string `json:"source"`

	StartAt      time.Time `json:"start_at"`
	EndAt        time.Time `json:"end_at"`
	InBedStartAt time.Time `json:"in_bed_start_at"`
	InBedEndAt   time.Time `json:"in_bed_end_at"`

	Segments json.RawMessage `json:"segments"`
	Vitals   json.RawMessage `json:"vitals"`

	DateKey       string   `json:"date_key"`
	MoodOnWaking  *string  `json:"mood_on_waking,omitempty"`
	Note          *string  `json:"note,omitempty"`
	Tags          []string `json:"tags"`
	Score         *int16   `json:"score,omitempty"`
	RecoveryScore *int16   `json:"recovery_score,omitempty"`
	AsleepMinutes int      `json:"asleep_minutes"`

	CreatedAt time.Time  `json:"created_at"`
	UpdatedAt time.Time  `json:"updated_at"`
	DeletedAt *time.Time `json:"deleted_at,omitempty"`
}

func toDTO(s Session) SessionDTO {
	segments := json.RawMessage(s.Segments)
	if len(segments) == 0 {
		segments = json.RawMessage(`[]`)
	}
	vitals := json.RawMessage(s.Vitals)
	if len(vitals) == 0 {
		vitals = json.RawMessage(`{}`)
	}
	tags := s.Tags
	if tags == nil {
		tags = []string{}
	}
	return SessionDTO{
		ID:            s.ID.String(),
		ClientID:      s.ClientID,
		External:      s.ExternalID,
		Kind:          string(s.Kind),
		Source:        string(s.Source),
		StartAt:       s.StartAt,
		EndAt:         s.EndAt,
		InBedStartAt:  s.InBedStartAt,
		InBedEndAt:    s.InBedEndAt,
		Segments:      segments,
		Vitals:        vitals,
		DateKey:       s.DateKey.Format(DateLayout),
		MoodOnWaking:  s.MoodOnWaking,
		Note:          s.Note,
		Tags:          tags,
		Score:         s.Score,
		RecoveryScore: s.RecoveryScore,
		AsleepMinutes: s.AsleepMinutes,
		CreatedAt:     s.CreatedAt,
		UpdatedAt:     s.UpdatedAt,
		DeletedAt:     s.DeletedAt,
	}
}

func toDTOs(list []Session) []SessionDTO {
	out := make([]SessionDTO, 0, len(list))
	for _, s := range list {
		out = append(out, toDTO(s))
	}
	return out
}

// RoutineMarkDTO is the wire shape of a completed step.
type RoutineMarkDTO struct {
	MarkID      string     `json:"mark_id"`
	StepID      string     `json:"step_id"`
	Phase       string     `json:"phase"`
	DateKey     string     `json:"date_key"`
	CompletedAt time.Time  `json:"completed_at"`
	DeletedAt   *time.Time `json:"deleted_at,omitempty"`
}

func toMarkDTO(m RoutineMark) RoutineMarkDTO {
	return RoutineMarkDTO{
		MarkID:      m.MarkID,
		StepID:      m.StepID,
		Phase:       string(m.Phase),
		DateKey:     m.DateKey.Format(DateLayout),
		CompletedAt: m.CompletedAt,
		DeletedAt:   m.DeletedAt,
	}
}

// SettingsDTO is the wire shape of the module's settings.
type SettingsDTO struct {
	Goals                 json.RawMessage `json:"goals"`
	EnabledStepIDs        []string        `json:"enabled_step_ids"`
	IslamicRoutineEnabled bool            `json:"islamic_routine_enabled"`
	Flags                 json.RawMessage `json:"flags"`
	UpdatedAt             time.Time       `json:"updated_at"`
}

func toSettingsDTO(s Settings) SettingsDTO {
	goals := json.RawMessage(s.Goals)
	if len(goals) == 0 {
		goals = json.RawMessage(`{}`)
	}
	flags := json.RawMessage(s.Flags)
	if len(flags) == 0 {
		flags = json.RawMessage(`{}`)
	}
	ids := s.EnabledStepIDs
	if ids == nil {
		ids = []string{}
	}
	return SettingsDTO{
		Goals:                 goals,
		EnabledStepIDs:        ids,
		IslamicRoutineEnabled: s.IslamicRoutineEnabled,
		Flags:                 flags,
		UpdatedAt:             s.UpdatedAt,
	}
}

// ChangesResponse is the delta-pull envelope, shaped like the gratitude module's.
type ChangesResponse struct {
	Sessions []SessionDTO     `json:"sessions"`
	Marks    []RoutineMarkDTO `json:"marks"`
	Cursor   string           `json:"cursor"`
	More     bool             `json:"more"`
}

// UpsertResult reports what actually happened to each session in a batch, so a client can
// tell "stored" from "your copy lost a conflict" without a second round trip.
type UpsertResult struct {
	ClientID string `json:"client_id"`
	Status   string `json:"status"` // applied | superseded
	ID       string `json:"id,omitempty"`
}
