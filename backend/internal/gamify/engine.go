// Package gamify owns the streak / level / XP engine. The engine (this file) is
// pure and deterministic so it can be unit-tested without a database. Other
// modules award progress by calling the Service interface, never the repo.
package gamify

import "time"

// Event is a qualifying user action that earns XP.
type Event string

const (
	EventPrayerTracked    Event = "prayer_tracked"
	EventThoughtPublished Event = "thought_published"
	EventLessonCompleted  Event = "lesson_completed"
	EventTrackerEntry     Event = "tracker_entry"
	EventDailyCheckin     Event = "daily_checkin"
)

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
	case EventPrayerTracked:
		return 10
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

// dayDiff returns the number of civil days from a to b (both interpreted at UTC
// midnight). Negative if b precedes a.
func dayDiff(a, b time.Time) int {
	a = time.Date(a.Year(), a.Month(), a.Day(), 0, 0, 0, 0, time.UTC)
	b = time.Date(b.Year(), b.Month(), b.Day(), 0, 0, 0, 0, time.UTC)
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
		switch dayDiff(s.LastActiveDate, today) {
		case 0:
			// already active today — streak unchanged
		case 1:
			s.Streak++
		default:
			s.Streak = 1
		}
	}
	s.LastActiveDate = time.Date(today.Year(), today.Month(), today.Day(), 0, 0, 0, 0, time.UTC)
	return s
}
