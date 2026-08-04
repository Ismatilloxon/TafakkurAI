package gratitude

import (
	"testing"
	"time"
)

func day(s string) time.Time {
	t, err := time.Parse(DateLayout, s)
	if err != nil {
		panic(err)
	}
	return t
}

func TestComputeStatsStreakCountsToday(t *testing.T) {
	today := day("2026-07-29")
	days := []time.Time{day("2026-07-27"), day("2026-07-28"), day("2026-07-29")}

	got := ComputeStats(days, 5, today)

	if got.Streak != 3 {
		t.Fatalf("streak = %d, want 3", got.Streak)
	}
	if got.Total != 5 {
		t.Fatalf("total = %d, want 5", got.Total)
	}
	if !got.Week[6] {
		t.Fatal("today should be marked in the trailing week")
	}
}

// The day is not over: a run that reached yesterday must still read as alive.
func TestComputeStatsStreakSurvivesUnwrittenToday(t *testing.T) {
	today := day("2026-07-29")
	days := []time.Time{day("2026-07-26"), day("2026-07-27"), day("2026-07-28")}

	if got := ComputeStats(days, 3, today).Streak; got != 3 {
		t.Fatalf("streak = %d, want 3", got)
	}
}

func TestComputeStatsBrokenStreakIsZero(t *testing.T) {
	today := day("2026-07-29")
	days := []time.Time{day("2026-07-20"), day("2026-07-21")}

	got := ComputeStats(days, 2, today)

	if got.Streak != 0 {
		t.Fatalf("streak = %d, want 0", got.Streak)
	}
	if got.Longest != 2 {
		t.Fatalf("longest = %d, want 2", got.Longest)
	}
}

func TestComputeStatsLongestRunAndMonthDays(t *testing.T) {
	today := day("2026-07-29")
	days := []time.Time{
		day("2026-06-01"), day("2026-06-02"), day("2026-06-03"), day("2026-06-04"),
		day("2026-07-10"), day("2026-07-11"),
	}

	got := ComputeStats(days, 6, today)

	if got.Longest != 4 {
		t.Fatalf("longest = %d, want 4", got.Longest)
	}
	if got.MonthDays != 2 {
		t.Fatalf("monthDays = %d, want 2 (July only)", got.MonthDays)
	}
}

func TestComputeStatsIgnoresDuplicateDays(t *testing.T) {
	today := day("2026-07-29")
	days := []time.Time{day("2026-07-29"), day("2026-07-29"), day("2026-07-28")}

	if got := ComputeStats(days, 3, today).Streak; got != 2 {
		t.Fatalf("streak = %d, want 2", got)
	}
}

func TestCategoryBreakdownOrdersByShareAndTruncates(t *testing.T) {
	counts := map[string]int{"people": 5, "faith": 3, "nature": 1, "work": 1}

	got := CategoryBreakdown(counts, 2)

	if len(got) != 2 {
		t.Fatalf("len = %d, want 2", len(got))
	}
	if got[0].Key != "people" || got[0].Percent != 50 {
		t.Fatalf("top = %+v, want people at 50%%", got[0])
	}
	if got[1].Key != "faith" {
		t.Fatalf("second = %s, want faith", got[1].Key)
	}
	if got[0].Label != "People" {
		t.Fatalf("label = %q, want People", got[0].Label)
	}
}

func TestCategoryBreakdownEmpty(t *testing.T) {
	if got := CategoryBreakdown(map[string]int{}, 5); got != nil {
		t.Fatalf("got %v, want nil", got)
	}
}

func TestNormalizeCategoryFallsBackInsteadOfFailing(t *testing.T) {
	if got := NormalizeCategory("People"); got != "people" {
		t.Fatalf("got %q, want people", got)
	}
	if got := NormalizeCategory("not-a-category"); got != DefaultCategory {
		t.Fatalf("got %q, want %q", got, DefaultCategory)
	}
}

func TestMoodIndexOrdersLowToWonderful(t *testing.T) {
	if MoodIndex("low") != 0 || MoodIndex("wonderful") != 4 {
		t.Fatal("mood order changed; the weekly chart plots the index")
	}
	if MoodIndex("") != -1 {
		t.Fatal("a missing mood must be -1 so the chart can skip the day")
	}
}

func TestReflectionEmptyJournal(t *testing.T) {
	if got := Reflection(nil, 0, 0); got == "" {
		t.Fatal("empty journal still needs a line")
	}
}

func TestMonthLineNamesTopCategories(t *testing.T) {
	cats := []CategoryShare{
		{Key: "people", Label: "People"},
		{Key: "learning", Label: "Learning"},
	}

	got := MonthLine("July", cats, 24)

	if want := "July was full of moments connected to people and learning — a steady, gentle month."; got != want {
		t.Fatalf("got %q, want %q", got, want)
	}
}

func TestChallengeCatalogue(t *testing.T) {
	c, ok := ChallengeByID("c7")
	if !ok || c.Total != 7 || len(c.Days) != 7 {
		t.Fatalf("c7 = %+v, ok = %v", c, ok)
	}
	if _, ok := ChallengeByID("nope"); ok {
		t.Fatal("unknown journey must not resolve")
	}
}
