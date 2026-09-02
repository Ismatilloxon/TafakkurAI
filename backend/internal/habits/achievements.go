package habits

import (
	"math"
	"time"
)

// Achievement is one badge in the catalogue. The catalogue is static and lives in code,
// not in the database: it is content, every user sees the same twelve, and a new one
// should ship with the release whose copy describes it.
//
// Only *unlock state* is per user, and that is stored as an `habitAchievement` row so it
// syncs to every device like anything else.
type Achievement struct {
	ID    string `json:"id"`
	Name  string `json:"name"`
	Sub   string `json:"sub"`
	Icon  string `json:"icon"`
	Color string `json:"color"`
	// Goal is the human-readable threshold, shown on a locked badge so "70%" means
	// something ("42 of 60 days") rather than being a bare number.
	Goal string `json:"goal"`
}

var Achievements = []Achievement{
	{ID: "first", Name: "First step", Sub: "Create your first habit", Icon: "sparkles", Color: "teal", Goal: "1 habit"},
	{ID: "w7", Name: "7-day streak", Sub: "A week, unbroken", Icon: "flame", Color: "gold", Goal: "7 days"},
	{ID: "w30", Name: "30-day streak", Sub: "A month of showing up", Icon: "flame", Color: "gold", Goal: "30 days"},
	{ID: "morning", Name: "Morning hero", Sub: "20 habits before 8am", Icon: "sunrise", Color: "gold", Goal: "20 completions"},
	{ID: "perfectw", Name: "Perfect week", Sub: "Every habit, 7 days", Icon: "checkCircle", Color: "green", Goal: "7 perfect days"},
	{ID: "w100", Name: "100-day streak", Sub: "Triple digits", Icon: "flame", Color: "rose", Goal: "100 days"},
	{ID: "consist", Name: "Consistency master", Sub: "90% for a month", Icon: "target", Color: "cyan", Goal: "90% / 30 days"},
	{ID: "night", Name: "Night owl", Sub: "30 evening habits", Icon: "moon", Color: "purple", Goal: "30 completions"},
	{ID: "perfectm", Name: "Perfect month", Sub: "Every habit, 30 days", Icon: "calendar", Color: "green", Goal: "30 perfect days"},
	{ID: "nomiss", Name: "Never missed", Sub: "60 days, zero misses", Icon: "shieldCheck", Color: "cyan", Goal: "60 days"},
	{ID: "w365", Name: "365-day streak", Sub: "A full year", Icon: "star", Color: "gold", Goal: "365 days"},
	{ID: "legend", Name: "Legend", Sub: "All achievements earned", Icon: "zap", Color: "purple", Goal: "11 badges"},
}

// AchievementFacts is everything the catalogue is evaluated against. Assembling it once
// and passing it in keeps every rule a pure expression over numbers, which is what makes
// the whole set unit-testable without a database.
type AchievementFacts struct {
	HabitCount    int
	BestStreak    int // longest run any single habit has ever held
	PerfectRun    int // longest run of days where every scheduled habit was done
	MonthRate     float64
	MorningDone   int // completions of habits scheduled before 08:00
	EveningDone   int // completions of habits in the Evening/Night buckets
	DaysSinceMiss int
}

// AchievementView is one badge as the client renders it.
type AchievementView struct {
	Achievement
	Unlocked   bool    `json:"unlocked"`
	UnlockedAt int64   `json:"unlockedAt,omitempty"` // epoch millis
	Progress   float64 `json:"progress"`             // 0..1
}

// EvaluateAchievements scores the catalogue against [facts] and folds in what the user
// has already unlocked.
//
// Two rules that are easy to get wrong and matter a lot:
//
//   - An unlocked badge stays unlocked. Progress is recomputed every request, and a
//     deleted habit can legitimately drop today's numbers below yesterday's threshold.
//     Re-locking a badge somebody earned is the single most demoralising bug this
//     screen can have.
//   - "Legend" is scored against the other eleven, never against itself, or it would be
//     self-referential and could never be reached.
func EvaluateAchievements(facts AchievementFacts, stored map[string]AchievementState, now time.Time) []AchievementView {
	progress := map[string]float64{
		"first":    ratio(facts.HabitCount, 1),
		"w7":       ratio(facts.BestStreak, 7),
		"w30":      ratio(facts.BestStreak, 30),
		"w100":     ratio(facts.BestStreak, 100),
		"w365":     ratio(facts.BestStreak, 365),
		"morning":  ratio(facts.MorningDone, 20),
		"night":    ratio(facts.EveningDone, 30),
		"perfectw": ratio(facts.PerfectRun, 7),
		"perfectm": ratio(facts.PerfectRun, 30),
		"consist":  clamp01(facts.MonthRate / 0.9),
		"nomiss":   ratio(facts.DaysSinceMiss, 60),
	}

	// Pass one: everything except Legend, so its own input is complete before it is scored.
	earned := 0
	for _, a := range Achievements {
		if a.ID == "legend" {
			continue
		}
		if progress[a.ID] >= 1 || (stored[a.ID].UnlockedAt > 0) {
			earned++
		}
	}
	progress["legend"] = ratio(earned, len(Achievements)-1)

	out := make([]AchievementView, 0, len(Achievements))
	nowMs := now.UnixMilli()
	for _, a := range Achievements {
		p := clamp01(progress[a.ID])
		st := stored[a.ID]
		v := AchievementView{Achievement: a, Progress: p}
		switch {
		case st.UnlockedAt > 0:
			v.Unlocked = true
			v.UnlockedAt = st.UnlockedAt
			v.Progress = 1
		case p >= 1:
			v.Unlocked = true
			v.UnlockedAt = nowMs
			v.Progress = 1
		}
		out = append(out, v)
	}
	return out
}

// NewlyUnlocked returns the badges that crossed the line during this evaluation, so the
// service can persist them (and only them) rather than rewriting twelve rows per request.
func NewlyUnlocked(views []AchievementView, stored map[string]AchievementState) []AchievementView {
	var out []AchievementView
	for _, v := range views {
		if v.Unlocked && stored[v.ID].UnlockedAt == 0 {
			out = append(out, v)
		}
	}
	return out
}

func ratio(have, want int) float64 {
	if want <= 0 {
		return 1
	}
	return clamp01(float64(have) / float64(want))
}

func clamp01(v float64) float64 {
	if math.IsNaN(v) || v < 0 {
		return 0
	}
	if v > 1 {
		return 1
	}
	return v
}
