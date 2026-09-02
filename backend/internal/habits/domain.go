// Package habits serves the Habits module: the dashboard, statistics, calendar and
// achievement read models, plus a REST write path for clients that are not the phone.
//
// # Where the data lives
//
// There is no habits table. Habit rows live in `entity_state` — the projection the
// generic sync platform (package sync, migration 0009) writes — under feature "habits"
// and one of four entities: habit, habitEntry, habitNote, habitAchievement. The phone
// writes them local-first into SQLDelight and the sync triggers carry them here; this
// package reads that same projection and writes back into it.
//
// The alternative — habit tables of our own, filled by REST — was rejected because it
// creates two sources of truth for one habit. A completion tapped on the plane would
// land in entity_state, a completion tapped in the Telegram bot would land in
// habits_entries, and nothing would reconcile them. Writing through the projection means
// a habit created anywhere reaches every device on its next delta pull, for free.
//
// # What this package must never assume
//
// The payloads are written by the client and are versioned by the client. A field this
// build does not know about is preserved untouched (see store.patch), and a field the
// client has not sent yet decodes to its zero value. An older server must not corrupt a
// newer app's habit.
package habits

import (
	"encoding/json"
	"errors"
	"strconv"
	"strings"
	"time"
)

// Feature is the sync feature label every habits entity is filed under. It matches the
// `feature` argument the client's sync triggers emit, and changing it would orphan every
// row already stored.
const Feature = "habits"

// Entity names. These are the SQLDelight table names on the device: the trigger uses the
// table name as the entity, so these strings are a contract with the .sq files, not a
// naming preference.
const (
	EntityHabit       = "habit"
	EntityEntry       = "habitEntry"
	EntityNote        = "habitNote"
	EntityAchievement = "habitAchievement"
)

// DateLayout is the "yyyy-MM-dd" civil-date key both sides use. No timezone: a habit day
// is the user's local day, which the device decided when it wrote the row.
const DateLayout = "2006-01-02"

var (
	ErrNotFound     = errors.New("habit not found")
	ErrInvalidInput = errors.New("invalid habit input")
)

// ---------------------------------------------------------------------------
// Wire-compatible scalars
//
// The payloads are produced by SQLite, which has no boolean and no array type. A BOOLEAN
// column arrives as 0/1 and a list column arrives as a *string* holding JSON. These two
// types accept both that shape and the natural JSON one, and always marshal back in
// SQLite's shape so a row this package writes is byte-identical to a row the phone wrote.
// ---------------------------------------------------------------------------

// Bool decodes 0/1, "0"/"1" and true/false; encodes as 0/1.
type Bool bool

func (b *Bool) UnmarshalJSON(data []byte) error {
	s := strings.TrimSpace(string(data))
	switch s {
	case "", "null":
		*b = false
		return nil
	case "true", `"true"`, "1", `"1"`:
		*b = true
		return nil
	case "false", `"false"`, "0", `"0"`:
		*b = false
		return nil
	}
	// Any other number is truthy, matching SQLite's own coercion.
	if f, err := strconv.ParseFloat(strings.Trim(s, `"`), 64); err == nil {
		*b = f != 0
		return nil
	}
	return nil
}

func (b Bool) MarshalJSON() ([]byte, error) {
	if b {
		return []byte("1"), nil
	}
	return []byte("0"), nil
}

// IntList decodes both `[0,1,2]` and `"[0,1,2]"`; encodes as the quoted string form.
type IntList []int

func (l *IntList) UnmarshalJSON(data []byte) error {
	trimmed := strings.TrimSpace(string(data))
	if trimmed == "" || trimmed == "null" {
		*l = nil
		return nil
	}
	if trimmed[0] == '"' {
		var inner string
		if err := json.Unmarshal(data, &inner); err != nil {
			return err
		}
		if strings.TrimSpace(inner) == "" {
			*l = nil
			return nil
		}
		var out []int
		if err := json.Unmarshal([]byte(inner), &out); err != nil {
			// A malformed list must not fail the whole habit — it degrades to "every day".
			*l = nil
			return nil
		}
		*l = out
		return nil
	}
	var out []int
	if err := json.Unmarshal(data, &out); err != nil {
		*l = nil
		return nil
	}
	*l = out
	return nil
}

func (l IntList) MarshalJSON() ([]byte, error) {
	if l == nil {
		l = IntList{}
	}
	inner, err := json.Marshal([]int(l))
	if err != nil {
		return nil, err
	}
	return json.Marshal(string(inner))
}

func (l IntList) contains(v int) bool {
	for _, x := range l {
		if x == v {
			return true
		}
	}
	return false
}

// ---------------------------------------------------------------------------
// Vocabulary
// ---------------------------------------------------------------------------

// Slots order the home screen. "anytime" is not a time of day — it is the explicit
// "no particular moment" bucket, which is why it sits between afternoon and evening
// rather than being inferred from a clock time that does not exist.
const (
	SlotMorning   = "morning"
	SlotAfternoon = "afternoon"
	SlotAnytime   = "anytime"
	SlotEvening   = "evening"
)

var slots = map[string]bool{SlotMorning: true, SlotAfternoon: true, SlotAnytime: true, SlotEvening: true}

func NormalizeSlot(s string) string {
	s = strings.ToLower(strings.TrimSpace(s))
	if slots[s] {
		return s
	}
	return SlotAnytime
}

// SlotOrder is the display order, and the order every grouped response uses.
var SlotOrder = []string{SlotMorning, SlotAfternoon, SlotAnytime, SlotEvening}

const (
	DifficultyEasy   = "easy"
	DifficultyMedium = "medium"
	DifficultyHard   = "hard"
)

var difficulties = map[string]int{DifficultyEasy: 1, DifficultyMedium: 2, DifficultyHard: 3}

func NormalizeDifficulty(d string) string {
	d = strings.ToLower(strings.TrimSpace(d))
	if _, ok := difficulties[d]; ok {
		return d
	}
	return DifficultyMedium
}

// DifficultyWeight is the 1..3 the client draws as dots, and the weight the consistency
// score uses: finishing three hard habits is not the same achievement as three easy ones.
func DifficultyWeight(d string) int {
	if n, ok := difficulties[d]; ok {
		return n
	}
	return 2
}

const (
	GoalCheck    = "check"    // done / not done
	GoalCount    = "count"    // N repetitions (8 glasses)
	GoalDuration = "duration" // N minutes
)

var goalTypes = map[string]bool{GoalCheck: true, GoalCount: true, GoalDuration: true}

func NormalizeGoalType(g string) string {
	g = strings.ToLower(strings.TrimSpace(g))
	if goalTypes[g] {
		return g
	}
	return GoalCheck
}

const (
	FrequencyDaily    = "daily"
	FrequencyWeekdays = "weekdays"
	FrequencyCustom   = "custom"
)

var frequencies = map[string]bool{FrequencyDaily: true, FrequencyWeekdays: true, FrequencyCustom: true}

func NormalizeFrequency(f string) string {
	f = strings.ToLower(strings.TrimSpace(f))
	if frequencies[f] {
		return f
	}
	return FrequencyDaily
}

// Habit lifecycle. Paused freezes the streak without deleting history; archived hides it
// from every list but keeps it answering "what was my longest streak ever".
const (
	StateActive   = "active"
	StatePaused   = "paused"
	StateArchived = "archived"
)

var habitStates = map[string]bool{StateActive: true, StatePaused: true, StateArchived: true}

func NormalizeState(s string) string {
	s = strings.ToLower(strings.TrimSpace(s))
	if habitStates[s] {
		return s
	}
	return StateActive
}

// Entry status. There is no "missed": a miss is the *absence* of a row, which is what
// makes the write path idempotent — undoing a completion deletes the row rather than
// writing a fourth state that then has to be distinguished from never having tried.
const (
	StatusDone    = "done"
	StatusSkipped = "skipped"
	StatusPartial = "partial"
)

var entryStatuses = map[string]bool{StatusDone: true, StatusSkipped: true, StatusPartial: true}

func ValidStatus(s string) bool { return entryStatuses[s] }

// ---------------------------------------------------------------------------
// Categories
//
// Mirrors HCATS in the prototype. `Col` is a palette token, not a hex value: the app has
// seven themes and the same habit must read as "gold" in all of them.
// ---------------------------------------------------------------------------

type Category struct {
	ID     string `json:"id"`
	Label  string `json:"label"`
	Native string `json:"native"`
	Icon   string `json:"icon"`
	Color  string `json:"color"`
	// Axis groups the category onto one of the five balance-radar spokes.
	Axis string `json:"axis"`
}

// Radar axes. Five, because more than five is unreadable at phone width.
const (
	AxisIslam = "Islam"
	AxisLearn = "Learn"
	AxisFocus = "Focus"
	AxisBody  = "Health"
	AxisRest  = "Rest"
)

var RadarAxes = []string{AxisIslam, AxisBody, AxisLearn, AxisFocus, AxisRest}

// Categories in display order. Unknown ids fall back to DefaultCategory rather than
// failing a write: a newer client must never lose a habit against an older server.
var Categories = []Category{
	{ID: "islam", Label: "Islam", Native: "Ibodat", Icon: "moon", Color: "gold", Axis: AxisIslam},
	{ID: "programming", Label: "Programming", Native: "Dasturlash", Icon: "terminal", Color: "cyan", Axis: AxisFocus},
	{ID: "english", Label: "English", Native: "Ingliz tili", Icon: "languages", Color: "purple", Axis: AxisLearn},
	{ID: "reading", Label: "Reading", Native: "Mutolaa", Icon: "book", Color: "teal", Axis: AxisLearn},
	{ID: "workout", Label: "Workout", Native: "Mashq", Icon: "dumbbell", Color: "rose", Axis: AxisBody},
	{ID: "health", Label: "Health", Native: "Salomatlik", Icon: "heart", Color: "green", Axis: AxisBody},
	{ID: "sleep", Label: "Sleep", Native: "Uyqu", Icon: "bedDouble", Color: "purple", Axis: AxisRest},
	{ID: "mindfulness", Label: "Mindfulness", Native: "Xotirjamlik", Icon: "leaf", Color: "green", Axis: AxisRest},
	{ID: "study", Label: "Study", Native: "Oʻqish", Icon: "graduationCap", Color: "cyan", Axis: AxisLearn},
	{ID: "family", Label: "Family", Native: "Oila", Icon: "users", Color: "rose", Axis: AxisRest},
	{ID: "business", Label: "Business", Native: "Biznes", Icon: "briefcase", Color: "gold", Axis: AxisFocus},
	{ID: "custom", Label: "Custom", Native: "Boshqa", Icon: "sparkles", Color: "teal", Axis: AxisFocus},
}

const DefaultCategory = "custom"

var categoryByID = func() map[string]Category {
	m := make(map[string]Category, len(Categories))
	for _, c := range Categories {
		m[c.ID] = c
	}
	return m
}()

func CategoryByID(id string) Category {
	if c, ok := categoryByID[id]; ok {
		return c
	}
	return categoryByID[DefaultCategory]
}

func NormalizeCategory(id string) string {
	id = strings.ToLower(strings.TrimSpace(id))
	if _, ok := categoryByID[id]; ok {
		return id
	}
	return DefaultCategory
}

// ---------------------------------------------------------------------------
// Models — these ARE the sync payloads
//
// Field names match the SQLDelight column names one for one. The device's sync payload is
// assembled by SQLite from `pragma_table_info`, so a mismatch here does not fail loudly:
// it silently drops the field. Renaming a column means renaming it in both places.
// ---------------------------------------------------------------------------

// Habit is one habit's definition. Streak, longest and completion rate are deliberately
// NOT stored: they are derived from entries on read (see stats.go). A stored streak is a
// denormalisation that goes wrong the first time an entry syncs out of order, and it
// cannot be recomputed once it has been overwritten.
type Habit struct {
	ID          string `json:"id"`
	Name        string `json:"name"`
	Description string `json:"description"`
	CategoryID  string `json:"categoryId"`
	ColorKey    string `json:"colorKey"`
	Icon        string `json:"icon"`

	Slot      string `json:"slot"`
	TimeLocal string `json:"timeLocal"` // "07:00", or "" for an anytime habit

	ReminderEnabled Bool   `json:"reminderEnabled"`
	ReminderTime    string `json:"reminderTime"`
	SmartTiming     Bool   `json:"smartTiming"`

	Frequency string  `json:"frequency"`
	Weekdays  IntList `json:"weekdays"` // 0 = Monday … 6 = Sunday, used when frequency=custom

	Difficulty string `json:"difficulty"`

	GoalType   string `json:"goalType"`
	GoalTarget int    `json:"goalTarget"`
	GoalUnit   string `json:"goalUnit"`

	Motivation string `json:"motivation"`

	State       string `json:"state"`
	PausedUntil string `json:"pausedUntil"` // date key, "" = until resumed

	SortIndex int   `json:"sortIndex"`
	CreatedAt int64 `json:"createdAt"` // epoch millis
	UpdatedAt int64 `json:"updatedAt"`
	Deleted   Bool  `json:"deleted"`
}

// Entry is one habit on one day. Its id is derived — "<habitId>:<dateKey>" — which is
// what makes completion idempotent on (habitId, date) end to end: a replayed op lands on
// the same row on the device, in the outbox and in entity_state.
type Entry struct {
	ID          string `json:"id"`
	HabitID     string `json:"habitId"`
	DateKey     string `json:"dateKey"`
	Status      string `json:"status"`
	Value       int    `json:"value"`  // progress toward the goal
	Target      int    `json:"target"` // goal target at the moment of completion
	SkipReason  string `json:"skipReason"`
	CompletedAt int64  `json:"completedAt"`
	CreatedAt   int64  `json:"createdAt"`
	UpdatedAt   int64  `json:"updatedAt"`
}

// EntryID is the only place the derived key is built. Both the device and this package
// call it, so the two can never disagree about a row's identity.
func EntryID(habitID, dateKey string) string { return habitID + ":" + dateKey }

type Note struct {
	ID      string `json:"id"`
	HabitID string `json:"habitId"`
	DateKey string `json:"dateKey"`
	// Tagged "body" rather than "text" because the column is `body`: `text` is a type
	// keyword and every other note-shaped table in the app (gratitudeEntry) avoids it.
	Text      string `json:"body"`
	CreatedAt int64  `json:"createdAt"`
	UpdatedAt int64  `json:"updatedAt"`
	Deleted   Bool   `json:"deleted"`
}

// AchievementState is the per-user unlock record. Progress is stored as well as derived
// so the "you were at 88%" copy survives a habit being deleted.
type AchievementState struct {
	ID         string  `json:"id"`
	UnlockedAt int64   `json:"unlockedAt"` // 0 = still locked
	Progress   float64 `json:"progress"`   // 0..1
	UpdatedAt  int64   `json:"updatedAt"`
}

// ---------------------------------------------------------------------------
// Normalisation
// ---------------------------------------------------------------------------

// Normalize repairs a habit that came off the wire. Never rejects: an unknown enum value
// from a newer client becomes this build's nearest sane default so the habit still shows,
// still completes and still syncs.
func (h *Habit) Normalize() {
	h.Name = strings.TrimSpace(h.Name)
	h.CategoryID = NormalizeCategory(h.CategoryID)
	cat := CategoryByID(h.CategoryID)
	if h.ColorKey == "" {
		h.ColorKey = cat.Color
	}
	if h.Icon == "" {
		h.Icon = cat.Icon
	}
	h.Slot = NormalizeSlot(h.Slot)
	h.Difficulty = NormalizeDifficulty(h.Difficulty)
	h.GoalType = NormalizeGoalType(h.GoalType)
	h.Frequency = NormalizeFrequency(h.Frequency)
	h.State = NormalizeState(h.State)

	if h.GoalType == GoalCheck {
		h.GoalTarget = 1
		h.GoalUnit = ""
	} else if h.GoalTarget < 1 {
		h.GoalTarget = 1
	}
	if h.Frequency == FrequencyCustom && len(h.Weekdays) == 0 {
		// "Custom, no days chosen" would be a habit that is never scheduled and whose
		// completion rate is 0/0 forever. Treat it as daily instead.
		h.Frequency = FrequencyDaily
	}
	if !h.ReminderEnabled {
		h.ReminderTime = ""
	}
}

// Active reports whether the habit belongs in today's list.
func (h Habit) Active() bool { return !bool(h.Deleted) && h.State == StateActive }

// ScheduledOn reports whether this habit is due on the given day.
//
// A habit is not scheduled before it was created: counting the days before a user had
// the habit as misses would open every new habit at 0% and a broken streak.
func (h Habit) ScheduledOn(day time.Time) bool {
	if h.CreatedAt > 0 && dayNumber(day) < dayNumber(time.UnixMilli(h.CreatedAt).UTC()) {
		return false
	}
	switch h.Frequency {
	case FrequencyWeekdays:
		return WeekdayIndex(day) <= 4
	case FrequencyCustom:
		return h.Weekdays.contains(WeekdayIndex(day))
	default:
		return true
	}
}

// WeekdayIndex is Monday-first (0=Mon … 6=Sun), matching the client's day picker and
// every weekday chart in the module. Go's own Weekday is Sunday-first.
func WeekdayIndex(t time.Time) int { return (int(t.Weekday()) + 6) % 7 }

// WeekdayNames indexed by WeekdayIndex.
var WeekdayNames = []string{"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"}

// TimeBucket maps a habit onto one of the four time-of-day rows in Statistics. Derived
// from the clock time when there is one, and from the slot when there is not.
func (h Habit) TimeBucket() string {
	if h.TimeLocal != "" {
		if hour, err := strconv.Atoi(strings.SplitN(h.TimeLocal, ":", 2)[0]); err == nil {
			switch {
			case hour < 11:
				return "Morning"
			case hour < 17:
				return "Midday"
			case hour < 21:
				return "Evening"
			default:
				return "Night"
			}
		}
	}
	switch h.Slot {
	case SlotMorning:
		return "Morning"
	case SlotAfternoon:
		return "Midday"
	case SlotEvening:
		return "Evening"
	default:
		return "Midday"
	}
}

// TimeBuckets in display order, with the icon each row draws.
var TimeBuckets = []struct{ Label, Icon string }{
	{"Morning", "sunrise"},
	{"Midday", "sun"},
	{"Evening", "sunset"},
	{"Night", "moon"},
}

// ---------------------------------------------------------------------------
// Civil-date helpers
//
// Integer day arithmetic rather than time.AddDate everywhere, so "consecutive day" is
// plain subtraction and no DST transition can make a streak gain or lose a day.
// ---------------------------------------------------------------------------

func dayNumber(t time.Time) int {
	y, m, d := t.Date()
	return int(time.Date(y, m, d, 0, 0, 0, 0, time.UTC).Unix() / 86400)
}

func dayFromNumber(n int) time.Time { return time.Unix(int64(n)*86400, 0).UTC() }

// ParseDay parses a "yyyy-MM-dd" key. Returns ok=false rather than an error because every
// caller's response to a malformed key is the same: ignore the row.
func ParseDay(key string) (time.Time, bool) {
	t, err := time.Parse(DateLayout, key)
	if err != nil {
		return time.Time{}, false
	}
	return t.UTC(), true
}

func DayKey(t time.Time) string { return t.UTC().Format(DateLayout) }

// AddDays returns the key n days from key. Empty when the key does not parse.
func AddDays(key string, n int) string {
	t, ok := ParseDay(key)
	if !ok {
		return ""
	}
	return DayKey(dayFromNumber(dayNumber(t) + n))
}
