package habits

import (
	"math"
	"sort"
	"time"
)

// maxHistoryDays bounds every backward walk. Three years is longer than the app has
// existed and far longer than any streak a user will hold, and it keeps the cost of a
// dashboard request proportional to the window rather than to the account's age.
const maxHistoryDays = 1095

// Window sizes the read models use. Named because they appear in copy the user reads
// ("90% for a month"), so changing one changes what an achievement means.
const (
	WindowMonth = 30
	WindowYear  = 371 // 53 weeks, so the GitHub-style heatmap is a whole number of columns
	DetailWeeks = 17  // the habit-detail contribution grid
)

// EntryIndex is entries grouped the way every calculation wants them: by habit, then by
// day. Built once per request in the service and passed down, because each of the six
// read models below would otherwise re-bucket the same slice.
type EntryIndex map[string]map[string]Entry

func IndexEntries(entries []Entry) EntryIndex {
	idx := make(EntryIndex, 16)
	for _, e := range entries {
		if e.HabitID == "" || e.DateKey == "" {
			continue
		}
		byDay, ok := idx[e.HabitID]
		if !ok {
			byDay = make(map[string]Entry, 32)
			idx[e.HabitID] = byDay
		}
		// Two rows for one (habit, day) should be impossible — the id is derived from the
		// pair — but a client that wrote a legacy random id would produce one. Newest wins,
		// matching the sync platform's own last-write-wins rule.
		if prev, exists := byDay[e.DateKey]; !exists || e.UpdatedAt >= prev.UpdatedAt {
			byDay[e.DateKey] = e
		}
	}
	return idx
}

func (idx EntryIndex) For(habitID string) map[string]Entry {
	if m, ok := idx[habitID]; ok {
		return m
	}
	return map[string]Entry{}
}

// ---------------------------------------------------------------------------
// Per-habit
// ---------------------------------------------------------------------------

// HabitStats is everything derived about a single habit. Nothing here is stored: it is
// recomputed from entries on every read, which is what lets an entry that syncs a week
// late still land in the right streak.
type HabitStats struct {
	HabitID   string  `json:"habitId"`
	Current   int     `json:"currentStreak"`
	Longest   int     `json:"longestStreak"`
	Rate      float64 `json:"completionRate"` // 0..1 over scheduled, un-skipped days
	Scheduled int     `json:"scheduledDays"`
	Done      int     `json:"doneDays"`
	Skipped   int     `json:"skippedDays"`
	Missed    int     `json:"missedDays"`
	LastDone  string  `json:"lastDone,omitempty"`
	// TodayStatus is "", "done", "skipped" or "partial" — what the home card renders.
	TodayStatus string `json:"todayStatus,omitempty"`
	TodayValue  int    `json:"todayValue"`
}

// ComputeHabitStats derives one habit's streaks and rate.
//
// Three rules decide everything, and each exists because the naive version is wrong:
//
//   - A day the habit is not scheduled on is invisible. A weekday-only habit must not
//     lose its streak every Saturday.
//   - A skipped day is neutral: it neither extends the streak nor breaks it, and it is
//     excluded from the completion-rate denominator. That is the promise the Skip sheet
//     makes ("it won't count as a miss").
//   - Today is not a miss until it is over. Without this, every habit reads as broken
//     each morning and the user's streak visibly drops at midnight.
func ComputeHabitStats(h Habit, entries map[string]Entry, today time.Time) HabitStats {
	st := HabitStats{HabitID: h.ID}

	todayNum := dayNumber(today)
	start := todayNum
	if h.CreatedAt > 0 {
		start = dayNumber(time.UnixMilli(h.CreatedAt).UTC())
	}
	lastActivity := 0
	for key := range entries {
		d, ok := ParseDay(key)
		if !ok {
			continue
		}
		n := dayNumber(d)
		if n < start {
			start = n
		}
		if n > lastActivity {
			lastActivity = n
		}
	}
	if start > todayNum {
		start = todayNum
	}
	if todayNum-start > maxHistoryDays {
		start = todayNum - maxHistoryDays
	}

	// A paused habit freezes: days after its last activity are neither misses nor
	// opportunities. Without this a two-week pause would wipe a 40-day streak, which is
	// the exact opposite of what the Pause sheet promises.
	horizon := todayNum
	if h.State == StatePaused {
		horizon = lastActivity
		if horizon < start {
			horizon = start - 1 // paused with no history at all: nothing to walk
		}
	}

	if e, ok := entries[DayKey(today)]; ok {
		st.TodayStatus = e.Status
		st.TodayValue = e.Value
	}

	// Forward pass: totals and the longest run.
	run := 0
	for n := start; n <= horizon; n++ {
		day := dayFromNumber(n)
		if !h.ScheduledOn(day) {
			continue
		}
		key := DayKey(day)
		e, has := entries[key]
		switch {
		case has && e.Status == StatusDone:
			st.Scheduled++
			st.Done++
			run++
			if run > st.Longest {
				st.Longest = run
			}
			if key > st.LastDone {
				st.LastDone = key
			}
		case has && e.Status == StatusSkipped:
			st.Skipped++ // neutral — run survives, denominator untouched
		case n == todayNum:
			// Pending, not missed. Also left out of the denominator.
		default:
			st.Scheduled++
			st.Missed++
			run = 0
		}
	}

	// Backward pass: the current run. Separate from the forward pass because it starts at
	// a different place — today is skipped over when it is still pending, which is what
	// keeps yesterday's streak on screen all morning.
	cursor := horizon
	if cursor == todayNum && h.ScheduledOn(today) {
		if e, ok := entries[DayKey(today)]; !ok || (e.Status != StatusDone && e.Status != StatusSkipped) {
			cursor = todayNum - 1
		}
	}
	for n := cursor; n >= start; n-- {
		day := dayFromNumber(n)
		if !h.ScheduledOn(day) {
			continue
		}
		e, has := entries[DayKey(day)]
		if has && e.Status == StatusDone {
			st.Current++
			continue
		}
		if has && e.Status == StatusSkipped {
			continue
		}
		break
	}

	if st.Scheduled > 0 {
		st.Rate = float64(st.Done) / float64(st.Scheduled)
	}
	return st
}

// ---------------------------------------------------------------------------
// Per-day, across habits
// ---------------------------------------------------------------------------

// DayRoll is one calendar day summarised across every habit due that day. It is the unit
// behind the heatmap, the month calendar, the perfect-day count and the account streak.
type DayRoll struct {
	Date      string `json:"date"`
	Scheduled int    `json:"scheduled"`
	Done      int    `json:"done"`
	Skipped   int    `json:"skipped"`
	// Level is the 0..4 heatmap intensity: 0 nothing, 1 only skips, 2..4 by how much of
	// the day was completed. Computed here rather than in the client so the web, the
	// phone and the Telegram bot all shade the same day identically.
	Level   int  `json:"level"`
	Perfect bool `json:"perfect"`
}

// RollDays summarises [from, to] inclusive. `to` is normally the user's today; days after
// it are not produced, so a caller cannot accidentally render the future as missed.
func RollDays(habits []Habit, idx EntryIndex, from, to time.Time) []DayRoll {
	fromN, toN := dayNumber(from), dayNumber(to)
	if toN < fromN {
		return nil
	}
	out := make([]DayRoll, 0, toN-fromN+1)
	for n := fromN; n <= toN; n++ {
		day := dayFromNumber(n)
		key := DayKey(day)
		roll := DayRoll{Date: key}
		for _, h := range habits {
			if bool(h.Deleted) || h.State == StateArchived || !h.ScheduledOn(day) {
				continue
			}
			e, has := idx.For(h.ID)[key]
			switch {
			case has && e.Status == StatusDone:
				roll.Scheduled++
				roll.Done++
			case has && e.Status == StatusSkipped:
				roll.Skipped++
			default:
				roll.Scheduled++
			}
		}
		roll.Level = heatLevel(roll)
		roll.Perfect = roll.Scheduled > 0 && roll.Done == roll.Scheduled
		out = append(out, roll)
	}
	return out
}

func heatLevel(r DayRoll) int {
	if r.Done == 0 {
		if r.Skipped > 0 {
			return 1
		}
		return 0
	}
	if r.Scheduled == 0 {
		return 4
	}
	switch frac := float64(r.Done) / float64(r.Scheduled); {
	case frac >= 1:
		return 4
	case frac >= 0.5:
		return 3
	default:
		return 2
	}
}

// AccountStreak is the run of consecutive days on which the user completed at least one
// habit, ending today (with the same "today is not over" grace as a habit streak).
//
// "At least one" rather than "all of them" on purpose: an all-or-nothing account streak
// resets on the first busy evening and then means nothing for the rest of the year, which
// is precisely the failure mode habit apps are criticised for.
func AccountStreak(rolls []DayRoll) (current, longest int) {
	run := 0
	for _, r := range rolls {
		if r.Done > 0 {
			run++
			if run > longest {
				longest = run
			}
			continue
		}
		if r.Skipped > 0 && r.Done == 0 && r.Scheduled == 0 {
			continue // an entirely skipped day is neutral
		}
		run = 0
	}
	// Walk back for the current run, letting a still-pending today pass.
	i := len(rolls) - 1
	if i >= 0 && rolls[i].Done == 0 {
		i--
	}
	for ; i >= 0; i-- {
		if rolls[i].Done > 0 {
			current++
			continue
		}
		if rolls[i].Scheduled == 0 && rolls[i].Skipped == 0 {
			continue // no habit was due — not a break
		}
		break
	}
	if current > longest {
		longest = current
	}
	return current, longest
}

// LongestPerfectRun is the longest run of days on which every scheduled habit was done.
// Backs the "Perfect week" and "Perfect month" achievements.
func LongestPerfectRun(rolls []DayRoll) int {
	run, best := 0, 0
	for _, r := range rolls {
		if r.Scheduled == 0 && r.Skipped == 0 {
			continue // nothing was due; neither breaks nor extends
		}
		if r.Perfect {
			run++
			if run > best {
				best = run
			}
			continue
		}
		run = 0
	}
	return best
}

// DaysSinceMiss counts back from the end of [rolls] to the last day with a miss.
// Backs "Never missed".
func DaysSinceMiss(rolls []DayRoll) int {
	n := 0
	for i := len(rolls) - 1; i >= 0; i-- {
		r := rolls[i]
		if r.Scheduled == 0 {
			continue
		}
		if r.Done < r.Scheduled {
			// Today only counts as a miss once it is over, and `rolls` ends at today.
			if i == len(rolls)-1 {
				continue
			}
			return n
		}
		n++
	}
	return n
}

// Rate is done/scheduled over a set of rolls, ignoring days on which nothing was due.
func Rate(rolls []DayRoll) float64 {
	done, sched := 0, 0
	for _, r := range rolls {
		done += r.Done
		sched += r.Scheduled
	}
	if sched == 0 {
		return 0
	}
	return float64(done) / float64(sched)
}

// ConsistencyScore is the 0..100 the hero shows next to the ring.
//
// It is deliberately NOT the completion rate — the UI already shows that, and a second
// identical number would be noise. It blends the last month's completion (70%) with
// streak health (30%, saturating at three weeks), so it answers "am I keeping this up"
// rather than "what fraction did I finish", and it rewards a user who is 80% consistent
// every week over one who was perfect for four days and vanished.
func ConsistencyScore(monthRate float64, currentStreak int) int {
	streakHealth := math.Min(1, float64(currentStreak)/21.0)
	return int(math.Round(100 * (0.7*monthRate + 0.3*streakHealth)))
}

// ---------------------------------------------------------------------------
// Breakdowns
// ---------------------------------------------------------------------------

type LabelledRate struct {
	Key   string  `json:"key"`
	Label string  `json:"label"`
	Value float64 `json:"value"`
	Icon  string  `json:"icon,omitempty"`
	Color string  `json:"color,omitempty"`
}

// WeekdayRates is completion per weekday (Mon-first) over the window, plus the best and
// worst day. Ties break toward the earlier weekday so the answer is stable request to
// request — an unstable "your best day is Tuesday… no, Wednesday" reads as a bug.
func WeekdayRates(rolls []DayRoll) (values [7]float64, best, worst int) {
	var done, sched [7]int
	for _, r := range rolls {
		d, ok := ParseDay(r.Date)
		if !ok {
			continue
		}
		i := WeekdayIndex(d)
		done[i] += r.Done
		sched[i] += r.Scheduled
	}
	best, worst = -1, -1
	for i := 0; i < 7; i++ {
		if sched[i] == 0 {
			continue
		}
		values[i] = float64(done[i]) / float64(sched[i])
		if best < 0 || values[i] > values[best] {
			best = i
		}
		if worst < 0 || values[i] < values[worst] {
			worst = i
		}
	}
	if best < 0 {
		best, worst = 0, 0
	}
	return values, best, worst
}

// MonthlyRates is completion for each of the last [months] calendar months, oldest first.
func MonthlyRates(rolls []DayRoll, today time.Time, months int) (values []float64, labels []string) {
	type bucket struct{ done, sched int }
	acc := make(map[string]*bucket, months)
	order := make([]string, 0, months)
	cursor := time.Date(today.Year(), today.Month(), 1, 0, 0, 0, 0, time.UTC).AddDate(0, -(months - 1), 0)
	for i := 0; i < months; i++ {
		k := cursor.Format("2006-01")
		acc[k] = &bucket{}
		order = append(order, k)
		labels = append(labels, cursor.Format("Jan"))
		cursor = cursor.AddDate(0, 1, 0)
	}
	for _, r := range rolls {
		d, ok := ParseDay(r.Date)
		if !ok {
			continue
		}
		if b, in := acc[d.Format("2006-01")]; in {
			b.done += r.Done
			b.sched += r.Scheduled
		}
	}
	for _, k := range order {
		b := acc[k]
		if b.sched == 0 {
			values = append(values, 0)
			continue
		}
		values = append(values, float64(b.done)/float64(b.sched))
	}
	return values, labels
}

// CategoryRates is completion per category, high → low. Only categories the user actually
// has habits in appear: an empty "Business 0%" row is discouraging and says nothing.
func CategoryRates(habits []Habit, stats map[string]HabitStats) []LabelledRate {
	type acc struct{ done, sched int }
	byCat := map[string]*acc{}
	for _, h := range habits {
		if bool(h.Deleted) || h.State == StateArchived {
			continue
		}
		s, ok := stats[h.ID]
		if !ok {
			continue
		}
		a, in := byCat[h.CategoryID]
		if !in {
			a = &acc{}
			byCat[h.CategoryID] = a
		}
		a.done += s.Done
		a.sched += s.Scheduled
	}
	out := make([]LabelledRate, 0, len(byCat))
	for id, a := range byCat {
		if a.sched == 0 {
			continue
		}
		cat := CategoryByID(id)
		out = append(out, LabelledRate{
			Key: id, Label: cat.Label, Icon: cat.Icon, Color: cat.Color,
			Value: float64(a.done) / float64(a.sched),
		})
	}
	sort.Slice(out, func(i, j int) bool {
		if out[i].Value != out[j].Value {
			return out[i].Value > out[j].Value
		}
		return out[i].Key < out[j].Key
	})
	return out
}

// RadarValues folds categories onto the five balance axes. Axes with no habits report 0
// rather than being dropped, because a radar with a missing spoke is a different shape
// and would read as a data error.
func RadarValues(habits []Habit, stats map[string]HabitStats) []LabelledRate {
	type acc struct{ done, sched int }
	byAxis := map[string]*acc{}
	for _, axis := range RadarAxes {
		byAxis[axis] = &acc{}
	}
	for _, h := range habits {
		if bool(h.Deleted) || h.State == StateArchived {
			continue
		}
		s, ok := stats[h.ID]
		if !ok {
			continue
		}
		a := byAxis[CategoryByID(h.CategoryID).Axis]
		if a == nil {
			continue
		}
		a.done += s.Done
		a.sched += s.Scheduled
	}
	out := make([]LabelledRate, 0, len(RadarAxes))
	for _, axis := range RadarAxes {
		a := byAxis[axis]
		v := 0.0
		if a.sched > 0 {
			v = float64(a.done) / float64(a.sched)
		}
		out = append(out, LabelledRate{Key: axis, Label: axis, Value: v})
	}
	return out
}

// TimeOfDayRates is completion by Morning / Midday / Evening / Night.
func TimeOfDayRates(habits []Habit, stats map[string]HabitStats) []LabelledRate {
	type acc struct{ done, sched int }
	byBucket := map[string]*acc{}
	for _, b := range TimeBuckets {
		byBucket[b.Label] = &acc{}
	}
	for _, h := range habits {
		if bool(h.Deleted) || h.State == StateArchived {
			continue
		}
		s, ok := stats[h.ID]
		if !ok {
			continue
		}
		a := byBucket[h.TimeBucket()]
		if a == nil {
			continue
		}
		a.done += s.Done
		a.sched += s.Scheduled
	}
	out := make([]LabelledRate, 0, len(TimeBuckets))
	for _, b := range TimeBuckets {
		a := byBucket[b.Label]
		v := 0.0
		if a.sched > 0 {
			v = float64(a.done) / float64(a.sched)
		}
		out = append(out, LabelledRate{Key: b.Label, Label: b.Label, Icon: b.Icon, Value: v})
	}
	return out
}
