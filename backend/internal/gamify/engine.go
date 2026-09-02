// Package gamify owns the streak / level / XP engine. The engine (this file) is
// pure and deterministic so it can be unit-tested without a database. Other
// modules award progress by calling the Service interface, never the repo.
package gamify

import "time"

// Event is a qualifying user action that earns XP.
type Event string

const (
	EventPrayerTracked    Event = "prayer_tracked"
	EventPrayerOnTime     Event = "prayer_on_time"
	EventThoughtPublished Event = "thought_published"
	EventLessonCompleted  Event = "lesson_completed"
	EventTrackerEntry     Event = "tracker_entry"
	EventDailyCheckin     Event = "daily_checkin"
	EventAyahMemorized    Event = "ayah_memorized"
)

func (e Event) valid() bool {
	switch e {
	case EventPrayerTracked,
		EventPrayerOnTime,
		EventThoughtPublished,
		EventLessonCompleted,
		EventTrackerEntry,
		EventDailyCheckin,
		EventAyahMemorized:
		return true
	default:
		return false
	}
}

// State mirrors the gamification row.
type State struct {
	Streak         int       `json:"streak"`
	Level          int       `json:"level"`
	XP             int       `json:"xp"`
	XPNext         int       `json:"xp_next"`
	LastActiveDate time.Time `json:"-"`
}

func xpFor(ev Event) int {
	switch ev {
	case EventLessonCompleted:
		return 20
	// An ayah held in memory is worth a lesson: it is the same order of effort, and it is
	// awarded once per ayah — when the card graduates — not once per review.
	case EventAyahMemorized:
		return 20
	case EventPrayerTracked:
		return 10
	case EventPrayerOnTime:
		return 5
	case EventThoughtPublished, EventTrackerEntry:
		return 5
	case EventDailyCheckin:
		return 3
	default:
		return 1
	}
}

// xpNextFor returns the XP threshold to advance out of the given level.
func xpNextFor(level int) int {
	if level < 1 {
		level = 1
	}
	return 100 + (level-1)*50
}

// NewState is the starting state for a brand-new user.
func NewState() State {
	return State{Streak: 0, Level: 1, XP: 0, XPNext: xpNextFor(1)}
}

// civilDate retains the calendar date represented by t and deliberately discards
// its instant and location. Callers must first put an instant in the user's IANA
// timezone; converting to UTC before calling this function would change the day
// around midnight.
func civilDate(t time.Time) time.Time {
	return time.Date(t.Year(), t.Month(), t.Day(), 0, 0, 0, 0, time.UTC)
}

// dayDiff returns the number of civil days from a to b. Negative if b precedes a.
func dayDiff(a, b time.Time) int {
	a = civilDate(a)
	b = civilDate(b)
	return int(b.Sub(a).Hours() / 24)
}

// Apply returns the new state after an event occurring on `today`. It is pure:
// same inputs always yield the same output.
func Apply(s State, ev Event, today time.Time) State {
	if s.Level < 1 {
		s = NewState()
	}

	s.XP += xpFor(ev)
	for s.XP >= s.XPNext {
		s.XP -= s.XPNext
		s.Level++
		s.XPNext = xpNextFor(s.Level)
	}

	switch {
	case s.LastActiveDate.IsZero():
		s.Streak = 1
	default:
		diff := dayDiff(s.LastActiveDate, today)
		switch {
		case diff == 0:
			// already active today — streak unchanged
		case diff == 1:
			s.Streak++
		case diff > 1:
			s.Streak = 1
		default:
			// Offline events can arrive out of order. A historical activity earns
			// XP, but it must never move the current streak's anchor backwards.
			return s
		}
	}
	s.LastActiveDate = civilDate(today)
	return s
}
