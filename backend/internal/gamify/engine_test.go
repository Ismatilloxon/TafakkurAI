package gamify

import (
	"errors"
	"strings"
	"testing"
	"time"

	"github.com/google/uuid"
)

func day(y int, m time.Month, d int) time.Time {
	return time.Date(y, m, d, 12, 0, 0, 0, time.UTC)
}

func TestApply_FirstEventStartsStreakAndXP(t *testing.T) {
	got := Apply(NewState(), EventPrayerTracked, day(2026, 1, 10))
	if got.Streak != 1 {
		t.Errorf("streak = %d, want 1", got.Streak)
	}
	if got.XP != 10 {
		t.Errorf("xp = %d, want 10", got.XP)
	}
	if got.Level != 1 {
		t.Errorf("level = %d, want 1", got.Level)
	}
}

func TestApply_LevelUpCarriesRemainder(t *testing.T) {
	s := NewState() // XPNext = 100
	// 5 lessons * 20 XP = 100 -> exactly one level up, 0 remainder.
	for i := 0; i < 5; i++ {
		s = Apply(s, EventLessonCompleted, day(2026, 1, 10))
	}
	if s.Level != 2 {
		t.Fatalf("level = %d, want 2", s.Level)
	}
	if s.XP != 0 {
		t.Errorf("xp = %d, want 0", s.XP)
	}
	if s.XPNext != 150 {
		t.Errorf("xp_next = %d, want 150", s.XPNext)
	}
}

func TestApply_ConsecutiveDaysIncrementStreak(t *testing.T) {
	s := NewState()
	s = Apply(s, EventPrayerTracked, day(2026, 1, 10))
	s = Apply(s, EventPrayerTracked, day(2026, 1, 11))
	s = Apply(s, EventPrayerTracked, day(2026, 1, 12))
	if s.Streak != 3 {
		t.Errorf("streak = %d, want 3", s.Streak)
	}
}

func TestApply_SameDayDoesNotChangeStreak(t *testing.T) {
	s := NewState()
	s = Apply(s, EventPrayerTracked, day(2026, 1, 10))
	s = Apply(s, EventThoughtPublished, day(2026, 1, 10))
	if s.Streak != 1 {
		t.Errorf("streak = %d, want 1", s.Streak)
	}
}

func TestApply_GapResetsStreak(t *testing.T) {
	s := NewState()
	s = Apply(s, EventPrayerTracked, day(2026, 1, 10))
	s = Apply(s, EventPrayerTracked, day(2026, 1, 15))
	if s.Streak != 1 {
		t.Errorf("streak = %d, want 1", s.Streak)
	}
}

func TestApply_HistoricalEventDoesNotMoveStreakBackwards(t *testing.T) {
	s := NewState()
	s = Apply(s, EventPrayerTracked, day(2026, 1, 12))
	s = Apply(s, EventThoughtPublished, day(2026, 1, 10))

	if s.Streak != 1 {
		t.Errorf("streak = %d, want 1", s.Streak)
	}
	if got := s.LastActiveDate.Format(time.DateOnly); got != "2026-01-12" {
		t.Errorf("last active date = %s, want 2026-01-12", got)
	}
	if s.XP != 15 {
		t.Errorf("xp = %d, want 15", s.XP)
	}
}

func TestCivilDatePreservesCalendarDateAcrossUTCBoundary(t *testing.T) {
	local := time.Date(2026, 1, 10, 0, 15, 0, 0, time.FixedZone("UTC+5", 5*60*60))
	if got := civilDate(local).Format(time.DateOnly); got != "2026-01-10" {
		t.Errorf("civil date = %s, want 2026-01-10", got)
	}
	if got := local.UTC().Format(time.DateOnly); got != "2026-01-09" {
		t.Fatalf("test setup UTC date = %s, want 2026-01-09", got)
	}
}

func TestNormalizedCommandsRejectsUnknownEvent(t *testing.T) {
	_, err := normalizedCommands(uuid.New(), []AwardCommand{{
		Event:          Event("unknown"),
		IdempotencyKey: "source:1",
		LocalDate:      day(2026, 1, 10),
	}})
	if !errors.Is(err, ErrInvalidAwardCommand) {
		t.Fatalf("error = %v, want ErrInvalidAwardCommand", err)
	}
}

func TestNormalizedCommandsRejectsOversizedKey(t *testing.T) {
	_, err := normalizedCommands(uuid.New(), []AwardCommand{{
		Event:          EventPrayerTracked,
		IdempotencyKey: strings.Repeat("x", MaxIdempotencyKeyBytes+1),
		LocalDate:      day(2026, 1, 10),
	}})
	if !errors.Is(err, ErrInvalidAwardCommand) {
		t.Fatalf("error = %v, want ErrInvalidAwardCommand", err)
	}
}

func TestActivityCalendarFillsHistoricalGap(t *testing.T) {
	calendar := &activityCalendar{days: make(map[int64]struct{})}
	calendar.add(day(2026, 1, 10))
	calendar.add(day(2026, 1, 12))
	if streak, _ := calendar.activeStreak(); streak != 1 {
		t.Fatalf("gapped streak = %d, want 1", streak)
	}

	calendar.add(day(2026, 1, 11))
	streak, latest := calendar.activeStreak()
	if streak != 3 {
		t.Errorf("filled streak = %d, want 3", streak)
	}
	if got := latest.Format(time.DateOnly); got != "2026-01-12" {
		t.Errorf("latest = %s, want 2026-01-12", got)
	}
}
