package habits

import (
	"encoding/json"
	"testing"
	"time"
)

func day(t *testing.T, key string) time.Time {
	t.Helper()
	d, ok := ParseDay(key)
	if !ok {
		t.Fatalf("bad date key %q", key)
	}
	return d
}

// entriesOf builds a day→Entry map from "date=status" pairs, which keeps each test's
// fixture readable as the calendar it is meant to describe.
func entriesOf(pairs ...[2]string) map[string]Entry {
	out := make(map[string]Entry, len(pairs))
	for _, p := range pairs {
		out[p[0]] = Entry{ID: EntryID("h", p[0]), HabitID: "h", DateKey: p[0], Status: p[1]}
	}
	return out
}

func done(dates ...string) map[string]Entry {
	out := make(map[string]Entry, len(dates))
	for _, d := range dates {
		out[d] = Entry{ID: EntryID("h", d), HabitID: "h", DateKey: d, Status: StatusDone}
	}
	return out
}

func dailyHabit(createdKey string) Habit {
	h := Habit{ID: "h", Name: "Test", Frequency: FrequencyDaily, GoalType: GoalCheck, State: StateActive}
	if createdKey != "" {
		d, _ := ParseDay(createdKey)
		h.CreatedAt = d.UnixMilli()
	}
	h.Normalize()
	return h
}

// ---------------------------------------------------------------------------
// Wire compatibility with the payloads SQLite produces
// ---------------------------------------------------------------------------

func TestBoolAcceptsSQLiteAndJSONShapes(t *testing.T) {
	cases := map[string]bool{
		`1`: true, `0`: false, `"1"`: true, `"0"`: false,
		`true`: true, `false`: false, `null`: false,
	}
	for raw, want := range cases {
		var b Bool
		if err := json.Unmarshal([]byte(raw), &b); err != nil {
			t.Fatalf("%s: %v", raw, err)
		}
		if bool(b) != want {
			t.Fatalf("%s decoded to %v, want %v", raw, bool(b), want)
		}
	}
	// Re-encoding must produce SQLite's shape, or a row this package writes would not
	// match one the phone wrote and `merge` would see two different key types.
	out, _ := json.Marshal(Bool(true))
	if string(out) != "1" {
		t.Fatalf("Bool(true) encoded as %s, want 1", out)
	}
}

func TestIntListAcceptsStringEncodedArray(t *testing.T) {
	var l IntList
	if err := json.Unmarshal([]byte(`"[0,2,4]"`), &l); err != nil {
		t.Fatal(err)
	}
	if len(l) != 3 || l[0] != 0 || l[2] != 4 {
		t.Fatalf("got %v", l)
	}
	if err := json.Unmarshal([]byte(`[1,3]`), &l); err != nil {
		t.Fatal(err)
	}
	if len(l) != 2 || l[1] != 3 {
		t.Fatalf("got %v", l)
	}
	out, _ := json.Marshal(IntList{1, 2})
	if string(out) != `"[1,2]"` {
		t.Fatalf("encoded as %s", out)
	}
	// Garbage degrades to "no custom days" rather than failing the whole habit.
	if err := json.Unmarshal([]byte(`"not json"`), &l); err != nil || l != nil {
		t.Fatalf("garbage should decode to nil, got %v (%v)", l, err)
	}
}

func TestHabitPayloadRoundTrip(t *testing.T) {
	// The exact shape a SQLDelight row arrives in: booleans as 0/1, list as a string.
	raw := `{"id":"ha","name":"Adhkar","categoryId":"islam","colorKey":"gold","icon":"moon",
		"slot":"morning","timeLocal":"05:10","reminderEnabled":1,"reminderTime":"05:00",
		"smartTiming":0,"frequency":"custom","weekdays":"[0,1,2,3,4]","difficulty":"easy",
		"goalType":"check","goalTarget":1,"goalUnit":"","state":"active","sortIndex":0,
		"createdAt":1700000000000,"updatedAt":1700000000000,"deleted":0}`
	var h Habit
	if err := json.Unmarshal([]byte(raw), &h); err != nil {
		t.Fatal(err)
	}
	h.Normalize()
	if !bool(h.ReminderEnabled) || bool(h.SmartTiming) || bool(h.Deleted) {
		t.Fatalf("booleans wrong: %+v", h)
	}
	if len(h.Weekdays) != 5 {
		t.Fatalf("weekdays = %v", h.Weekdays)
	}
	if h.Frequency != FrequencyCustom || h.Difficulty != DifficultyEasy {
		t.Fatalf("enums wrong: %+v", h)
	}
}

func TestNormalizeFallsBackRatherThanRejecting(t *testing.T) {
	h := Habit{ID: "x", Name: " Read ", CategoryID: "quantum-physics", Slot: "whenever",
		Difficulty: "impossible", GoalType: "vibes", Frequency: "fortnightly"}
	h.Normalize()
	if h.Name != "Read" || h.CategoryID != DefaultCategory || h.Slot != SlotAnytime ||
		h.Difficulty != DifficultyMedium || h.GoalType != GoalCheck || h.Frequency != FrequencyDaily {
		t.Fatalf("unknown values were not defaulted: %+v", h)
	}
	// A category fallback must still supply an icon and colour or the card renders blank.
	if h.Icon == "" || h.ColorKey == "" {
		t.Fatalf("icon/colour not derived: %+v", h)
	}
}

func TestCustomFrequencyWithNoDaysBecomesDaily(t *testing.T) {
	h := Habit{ID: "x", Name: "n", Frequency: FrequencyCustom}
	h.Normalize()
	if h.Frequency != FrequencyDaily {
		t.Fatalf("a never-scheduled habit was allowed: %+v", h)
	}
}

// ---------------------------------------------------------------------------
// Streaks
// ---------------------------------------------------------------------------

func TestStreakCountsConsecutiveDoneDays(t *testing.T) {
	h := dailyHabit("2026-08-01")
	st := ComputeHabitStats(h, done("2026-08-05", "2026-08-06", "2026-08-07"), day(t, "2026-08-07"))
	if st.Current != 3 {
		t.Fatalf("current = %d, want 3", st.Current)
	}
	if st.Longest != 3 {
		t.Fatalf("longest = %d, want 3", st.Longest)
	}
}

func TestTodayPendingDoesNotBreakTheStreak(t *testing.T) {
	// The single most visible bug this module can have: the streak dropping to 0 at
	// midnight and only coming back once the user completes something.
	h := dailyHabit("2026-08-05")
	st := ComputeHabitStats(h, done("2026-08-05", "2026-08-06"), day(t, "2026-08-07"))
	if st.Current != 2 {
		t.Fatalf("current = %d, want 2 (yesterday's run still stands)", st.Current)
	}
	if st.Missed != 0 {
		t.Fatalf("today counted as a miss: %+v", st)
	}
	if st.Scheduled != 2 {
		t.Fatalf("today was counted in the rate denominator: %+v", st)
	}
}

func TestSkipKeepsTheStreakAndIsNotAMiss(t *testing.T) {
	h := dailyHabit("2026-08-04")
	st := ComputeHabitStats(h, entriesOf(
		[2]string{"2026-08-04", StatusDone},
		[2]string{"2026-08-05", StatusSkipped},
		[2]string{"2026-08-06", StatusDone},
		[2]string{"2026-08-07", StatusDone},
	), day(t, "2026-08-07"))
	if st.Current != 3 {
		t.Fatalf("current = %d, want 3 — a skip must not break the run", st.Current)
	}
	if st.Missed != 0 || st.Skipped != 1 {
		t.Fatalf("skip accounting wrong: %+v", st)
	}
	if st.Rate != 1 {
		t.Fatalf("rate = %v, want 1 — a skip must leave the denominator alone", st.Rate)
	}
}

func TestMissBreaksTheStreakAndCountsAgainstTheRate(t *testing.T) {
	h := dailyHabit("2026-08-02")
	st := ComputeHabitStats(h, done("2026-08-02", "2026-08-03", "2026-08-06", "2026-08-07"),
		day(t, "2026-08-07"))
	if st.Current != 2 {
		t.Fatalf("current = %d, want 2", st.Current)
	}
	if st.Longest != 2 {
		t.Fatalf("longest = %d, want 2", st.Longest)
	}
	if st.Missed != 2 { // 04 and 05
		t.Fatalf("missed = %d, want 2", st.Missed)
	}
	if st.Scheduled != 6 || st.Done != 4 {
		t.Fatalf("counts wrong: %+v", st)
	}
}

func TestWeekdayOnlyHabitSurvivesTheWeekend(t *testing.T) {
	// 2026-08-07 is a Friday; 08 and 09 are the weekend, 10 is the Monday.
	h := dailyHabit("2026-08-06")
	h.Frequency = FrequencyWeekdays
	if WeekdayIndex(day(t, "2026-08-07")) != 4 {
		t.Fatalf("fixture assumption broken: 2026-08-07 is not a Friday")
	}
	st := ComputeHabitStats(h, done("2026-08-06", "2026-08-07", "2026-08-10"), day(t, "2026-08-10"))
	if st.Current != 3 {
		t.Fatalf("current = %d, want 3 — Sat/Sun are not scheduled", st.Current)
	}
	if st.Missed != 0 {
		t.Fatalf("weekend counted as missed: %+v", st)
	}
}

func TestNothingBeforeCreationCounts(t *testing.T) {
	h := dailyHabit("2026-08-06")
	st := ComputeHabitStats(h, done("2026-08-06"), day(t, "2026-08-07"))
	if st.Scheduled != 1 || st.Missed != 0 {
		t.Fatalf("days before creation were counted: %+v", st)
	}
	if st.Rate != 1 {
		t.Fatalf("a one-day-old perfect habit should read 100%%, got %v", st.Rate)
	}
}

func TestPauseFreezesTheStreak(t *testing.T) {
	h := dailyHabit("2026-07-20")
	h.State = StatePaused
	entries := done("2026-07-20", "2026-07-21", "2026-07-22")
	st := ComputeHabitStats(h, entries, day(t, "2026-08-07"))
	if st.Current != 3 {
		t.Fatalf("current = %d, want 3 — a pause must not accrue misses", st.Current)
	}
	if st.Missed != 0 {
		t.Fatalf("paused days counted as misses: %+v", st)
	}
}

func TestTodayStatusIsSurfaced(t *testing.T) {
	h := dailyHabit("2026-08-01")
	entries := entriesOf([2]string{"2026-08-07", StatusPartial})
	e := entries["2026-08-07"]
	e.Value = 5
	entries["2026-08-07"] = e
	st := ComputeHabitStats(h, entries, day(t, "2026-08-07"))
	if st.TodayStatus != StatusPartial || st.TodayValue != 5 {
		t.Fatalf("today not surfaced: %+v", st)
	}
	if st.Current != 0 {
		t.Fatalf("a partial day should not count as done: %+v", st)
	}
}

// ---------------------------------------------------------------------------
// Day rolls and account-level aggregates
// ---------------------------------------------------------------------------

func twoHabits() []Habit {
	a := dailyHabit("2026-08-01")
	a.ID = "a"
	b := dailyHabit("2026-08-01")
	b.ID = "b"
	return []Habit{a, b}
}

func idxOf(entries ...Entry) EntryIndex { return IndexEntries(entries) }

func doneOn(habitID, dateKey string) Entry {
	return Entry{ID: EntryID(habitID, dateKey), HabitID: habitID, DateKey: dateKey, Status: StatusDone}
}

func TestRollDaysLevelsAndPerfect(t *testing.T) {
	hs := twoHabits()
	idx := idxOf(
		doneOn("a", "2026-08-05"), doneOn("b", "2026-08-05"), // perfect
		doneOn("a", "2026-08-06"), // half
	)
	rolls := RollDays(hs, idx, day(t, "2026-08-05"), day(t, "2026-08-07"))
	if len(rolls) != 3 {
		t.Fatalf("got %d rolls", len(rolls))
	}
	if !rolls[0].Perfect || rolls[0].Level != 4 {
		t.Fatalf("perfect day wrong: %+v", rolls[0])
	}
	if rolls[1].Perfect || rolls[1].Level != 3 {
		t.Fatalf("half day wrong: %+v", rolls[1])
	}
	if rolls[2].Level != 0 || rolls[2].Done != 0 {
		t.Fatalf("empty day wrong: %+v", rolls[2])
	}
}

func TestAccountStreakIsAtLeastOneHabitPerDay(t *testing.T) {
	hs := twoHabits()
	idx := idxOf(
		doneOn("a", "2026-08-04"),
		doneOn("b", "2026-08-05"),
		doneOn("a", "2026-08-06"),
	)
	rolls := RollDays(hs, idx, day(t, "2026-08-01"), day(t, "2026-08-07"))
	current, longest := AccountStreak(rolls)
	if current != 3 {
		t.Fatalf("current = %d, want 3 (today still pending)", current)
	}
	if longest != 3 {
		t.Fatalf("longest = %d, want 3", longest)
	}
}

func TestLongestPerfectRunAndDaysSinceMiss(t *testing.T) {
	hs := twoHabits()
	var es []Entry
	for _, d := range []string{"2026-08-03", "2026-08-04", "2026-08-05", "2026-08-06"} {
		es = append(es, doneOn("a", d), doneOn("b", d))
	}
	rolls := RollDays(hs, idxOf(es...), day(t, "2026-08-01"), day(t, "2026-08-07"))
	if got := LongestPerfectRun(rolls); got != 4 {
		t.Fatalf("perfect run = %d, want 4", got)
	}
	// 08-01 and 08-02 were misses; 03..06 clean; 07 is today and does not count yet.
	if got := DaysSinceMiss(rolls); got != 4 {
		t.Fatalf("days since miss = %d, want 4", got)
	}
}

func TestConsistencyRewardsContinuityOverRawRate(t *testing.T) {
	// Same completion rate, different streaks: the longer streak must score higher, which
	// is the whole reason this is not just the completion percentage again.
	low := ConsistencyScore(0.8, 2)
	high := ConsistencyScore(0.8, 30)
	if !(high > low) {
		t.Fatalf("streak did not raise consistency: %d vs %d", low, high)
	}
	if got := ConsistencyScore(1, 21); got != 100 {
		t.Fatalf("a perfect month with a three-week streak should be 100, got %d", got)
	}
	if got := ConsistencyScore(0, 0); got != 0 {
		t.Fatalf("empty should be 0, got %d", got)
	}
}

func TestWeekdayRatesPickBestAndWorst(t *testing.T) {
	hs := twoHabits()
	// Complete both habits every Monday, nothing on Saturdays.
	var es []Entry
	for _, d := range []string{"2026-08-03", "2026-08-10", "2026-08-17"} { // Mondays
		es = append(es, doneOn("a", d), doneOn("b", d))
	}
	rolls := RollDays(hs, idxOf(es...), day(t, "2026-08-01"), day(t, "2026-08-20"))
	values, best, worst := WeekdayRates(rolls)
	if best != 0 {
		t.Fatalf("best weekday = %d (%s), want Monday", best, WeekdayNames[best])
	}
	if values[0] <= values[worst] {
		t.Fatalf("Monday (%v) should beat the worst day (%v)", values[0], values[worst])
	}
}

func TestMonthlyRatesReturnsSixLabelledMonths(t *testing.T) {
	hs := twoHabits()
	rolls := RollDays(hs, EntryIndex{}, day(t, "2026-03-01"), day(t, "2026-08-07"))
	values, labels := MonthlyRates(rolls, day(t, "2026-08-07"), 6)
	if len(values) != 6 || len(labels) != 6 {
		t.Fatalf("got %d values / %d labels", len(values), len(labels))
	}
	if labels[5] != "Aug" || labels[0] != "Mar" {
		t.Fatalf("labels = %v, want Mar..Aug", labels)
	}
}

// ---------------------------------------------------------------------------
// Achievements
// ---------------------------------------------------------------------------

func viewByID(views []AchievementView, id string) AchievementView {
	for _, v := range views {
		if v.ID == id {
			return v
		}
	}
	return AchievementView{}
}

func TestAchievementsUnlockOnThreshold(t *testing.T) {
	views := EvaluateAchievements(AchievementFacts{HabitCount: 1, BestStreak: 30}, nil, time.Now())
	if !viewByID(views, "w7").Unlocked || !viewByID(views, "w30").Unlocked {
		t.Fatal("30-day streak should unlock both the 7 and 30 day badges")
	}
	if viewByID(views, "w100").Unlocked {
		t.Fatal("100-day badge unlocked too early")
	}
	if p := viewByID(views, "w100").Progress; p < 0.29 || p > 0.31 {
		t.Fatalf("w100 progress = %v, want ~0.30", p)
	}
}

func TestAnUnlockedAchievementIsNeverRelocked(t *testing.T) {
	stored := map[string]AchievementState{"w30": {ID: "w30", UnlockedAt: 1700000000000}}
	// The user deleted the habit that carried the streak: facts now say zero.
	views := EvaluateAchievements(AchievementFacts{}, stored, time.Now())
	got := viewByID(views, "w30")
	if !got.Unlocked || got.Progress != 1 {
		t.Fatalf("earned badge was taken away: %+v", got)
	}
	if got.UnlockedAt != 1700000000000 {
		t.Fatalf("unlock date was rewritten: %+v", got)
	}
}

func TestLegendCountsTheOtherBadgesOnly(t *testing.T) {
	// Everything reachable at once.
	facts := AchievementFacts{
		HabitCount: 3, BestStreak: 400, PerfectRun: 40, MonthRate: 1,
		MorningDone: 50, EveningDone: 50, DaysSinceMiss: 90,
	}
	views := EvaluateAchievements(facts, nil, time.Now())
	if !viewByID(views, "legend").Unlocked {
		t.Fatal("legend should unlock when the other eleven are earned")
	}
	empty := EvaluateAchievements(AchievementFacts{}, nil, time.Now())
	if viewByID(empty, "legend").Progress != 0 {
		t.Fatalf("legend progress on an empty account = %v", viewByID(empty, "legend").Progress)
	}
}

func TestNewlyUnlockedOnlyReportsTransitions(t *testing.T) {
	stored := map[string]AchievementState{"first": {ID: "first", UnlockedAt: 1}}
	views := EvaluateAchievements(AchievementFacts{HabitCount: 1, BestStreak: 7}, stored, time.Now())
	fresh := NewlyUnlocked(views, stored)
	for _, v := range fresh {
		if v.ID == "first" {
			t.Fatal("an already-stored unlock was reported as new — it would be rewritten every request")
		}
	}
	found := false
	for _, v := range fresh {
		if v.ID == "w7" {
			found = true
		}
	}
	if !found {
		t.Fatalf("w7 crossed the threshold but was not reported: %+v", fresh)
	}
}

// ---------------------------------------------------------------------------
// Date helpers
// ---------------------------------------------------------------------------

func TestAddDaysCrossesMonthAndYearBoundaries(t *testing.T) {
	if got := AddDays("2026-08-31", 1); got != "2026-09-01" {
		t.Fatalf("got %q", got)
	}
	if got := AddDays("2026-01-01", -1); got != "2025-12-31" {
		t.Fatalf("got %q", got)
	}
	if got := AddDays("not-a-date", 1); got != "" {
		t.Fatalf("got %q", got)
	}
}

func TestWeekdayIndexIsMondayFirst(t *testing.T) {
	// 2026-08-03 is a Monday, 2026-08-09 a Sunday.
	if got := WeekdayIndex(day(t, "2026-08-03")); got != 0 {
		t.Fatalf("Monday = %d", got)
	}
	if got := WeekdayIndex(day(t, "2026-08-09")); got != 6 {
		t.Fatalf("Sunday = %d", got)
	}
}

func TestTimeBucketPrefersClockTimeOverSlot(t *testing.T) {
	h := Habit{Slot: SlotMorning, TimeLocal: "22:30"}
	if got := h.TimeBucket(); got != "Night" {
		t.Fatalf("got %q, want Night", got)
	}
	h = Habit{Slot: SlotAnytime}
	if got := h.TimeBucket(); got != "Midday" {
		t.Fatalf("got %q, want Midday", got)
	}
}
