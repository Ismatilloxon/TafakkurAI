// Package sleep is the server half of the Sleep & Rest module.
//
// It is deliberately a thin, durable store rather than a second analytics engine. The client
// holds the user's goals, their rolling baseline and the scoring rules, and it has to compute
// scores offline anyway — so re-deriving them here would mean two implementations of the same
// arithmetic drifting apart, and a device showing one number while the weekly email showed
// another. The server keeps what the client sends, resolves conflicts between devices, and
// aggregates.
package sleep

import (
	"time"

	"github.com/google/uuid"
)

// DateLayout is the wire format for a local calendar day, matching the client's dateKey.
const DateLayout = "2006-01-02"

// Kind distinguishes a night from a nap.
type Kind string

const (
	KindNight Kind = "night"
	KindNap   Kind = "nap"
)

func (k Kind) Valid() bool { return k == KindNight || k == KindNap }

// Source is where a session came from, ordered by trust.
type Source string

const (
	SourceWatch       Source = "watch"
	SourceHealthStore Source = "health_store"
	SourceAutomatic   Source = "automatic"
	SourceManual      Source = "manual"
)

// rank mirrors SleepSource.rank on the client exactly. The two MUST agree: if the server
// resolved a conflict differently from the device that uploaded it, the next pull would undo
// the local decision, the device would re-upload, and the pair would flap forever.
func (s Source) rank() int {
	switch s {
	case SourceWatch:
		return 400
	case SourceHealthStore:
		return 300
	case SourceAutomatic:
		return 200
	default:
		return 100
	}
}

func (s Source) Valid() bool {
	switch s {
	case SourceWatch, SourceHealthStore, SourceAutomatic, SourceManual:
		return true
	}
	return false
}

// Phase is which routine a step belongs to.
type Phase string

const (
	PhaseBedtime Phase = "bedtime"
	PhaseMorning Phase = "morning"
)

func (p Phase) Valid() bool { return p == PhaseBedtime || p == PhaseMorning }

// Session is one stored night or nap.
type Session struct {
	ID       uuid.UUID
	UserID   uuid.UUID
	ClientID string

	ExternalID *string
	Kind       Kind
	Source     Source

	StartAt      time.Time
	EndAt        time.Time
	InBedStartAt time.Time
	InBedEndAt   time.Time

	// Opaque to the server: stored, returned, never interpreted. Keeping them opaque is what
	// lets the client ship a new stage vocabulary without a backend release.
	Segments []byte
	Vitals   []byte

	DateKey       time.Time
	MoodOnWaking  *string
	Note          *string
	Tags          []string
	Score         *int16
	RecoveryScore *int16
	AsleepMinutes int

	CreatedAt time.Time
	UpdatedAt time.Time
	DeletedAt *time.Time
}

// HasStaging reports whether the client sent a real hypnogram. Used only for conflict
// resolution; the payload itself is never parsed.
func (s Session) HasStaging() bool { return len(s.Segments) > 2 }

// hasVitals reports whether the vitals blob carries anything.
func (s Session) hasVitals() bool { return len(s.Vitals) > 2 }

// Strength is the total ordering used to resolve two sessions describing the same night.
//
// Identical to SleepMerge.strength on the client, and it must stay identical — see
// Source.rank. Source dominates, then staging, then vitals, then duration.
func (s Session) Strength() int64 {
	score := int64(s.Source.rank()) * 1_000_000
	if s.HasStaging() {
		score += 500_000
	}
	if s.hasVitals() {
		score += 100_000
	}
	return score + int64(s.AsleepMinutes)
}

// Overlaps reports whether two sessions describe overlapping time in bed. Half-open, so a nap
// ending exactly when another begins is not an overlap.
func (s Session) Overlaps(other Session) bool {
	return s.InBedStartAt.Before(other.InBedEndAt) && other.InBedStartAt.Before(s.InBedEndAt)
}

// RoutineMark is one completed routine step on one day.
type RoutineMark struct {
	ID          uuid.UUID
	UserID      uuid.UUID
	MarkID      string
	StepID      string
	Phase       Phase
	DateKey     time.Time
	CompletedAt time.Time
	UpdatedAt   time.Time
	DeletedAt   *time.Time
}

// Settings is the user's module configuration, mirrored from the client.
type Settings struct {
	Goals                 []byte
	EnabledStepIDs        []string
	IslamicRoutineEnabled bool
	Flags                 []byte
	UpdatedAt             time.Time
}

// DefaultSettings is what a user who has never written settings reads back.
func DefaultSettings() Settings {
	return Settings{
		Goals:          []byte(`{}`),
		EnabledStepIDs: []string{},
		Flags:          []byte(`{}`),
	}
}

// Stats is the server-side aggregate over a window.
//
// A strict subset of what the client computes. Anything needing the user's personal baseline
// (recovery, consistency, sleep debt) is deliberately absent: the baseline lives on the
// device, and a server-side approximation of it would be a second, worse answer to a question
// the client has already answered correctly.
type Stats struct {
	Nights               int       `json:"nights"`
	AverageAsleepMinutes int       `json:"average_asleep_minutes"`
	AverageScore         int       `json:"average_score"`
	TotalAsleepMinutes   int       `json:"total_asleep_minutes"`
	BestScore            int       `json:"best_score"`
	WorstScore           int       `json:"worst_score"`
	Efficiency           float64   `json:"efficiency"`
	Days                 []DayStat `json:"days"`
}

// DayStat is one day in the aggregate series.
type DayStat struct {
	DateKey       string `json:"date_key"`
	AsleepMinutes int    `json:"asleep_minutes"`
	Score         int    `json:"score"`
}

// ResolveConflict decides which of two sessions describing the same night survives.
//
// Returns true when the incoming session should replace the stored one.
//
// The rule is strength first, then recency as the tiebreak. Plain last-write-wins would be
// wrong here in a way that is easy to miss: a phone that infers sleep runs far more often
// than a watch syncs, so LWW would let the weakest source overwrite the strongest one every
// single morning, and the user's history would quietly degrade to phone estimates.
func ResolveConflict(incoming, stored Session) bool {
	if incoming.Strength() != stored.Strength() {
		return incoming.Strength() > stored.Strength()
	}
	return incoming.UpdatedAt.After(stored.UpdatedAt)
}
