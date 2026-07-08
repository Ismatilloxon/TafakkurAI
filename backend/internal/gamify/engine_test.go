package gamify

import (
	"testing"
	"time"
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
