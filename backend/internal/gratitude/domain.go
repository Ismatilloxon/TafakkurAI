// Package gratitude implements the Shukr journal: rich entries (mood, emotions,
// media, location), guided journeys, per-user settings, streak stats, insights and
// export. It is the durable source for the mobile Gratitude module, which writes
// local-first and syncs through this API.
//
// tracker_entries(kind='gratitude') stays the lightweight generic log; the Today
// card still reads it. Creating an entry here also awards XP, exactly like a
// tracker entry, so both surfaces feel the same.
package gratitude

import (
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/google/uuid"
)

const DateLayout = "2006-01-02"

// ---- entry ----

// Entry is one journal entry. Deleted entries keep their row (DeletedAt set) so a
// delta pull can tell an offline client that it must drop its local copy.
type Entry struct {
	ID            uuid.UUID
	UserID        uuid.UUID
	ClientID      string
	Mode          string
	Body          string
	Items         []string
	Why           *string
	Category      string
	Mood          *string
	Emotions      []string
	PhotoCount    int
	HasAudio      bool
	HasVideo      bool
	LocationLabel *string
	Favourite     bool
	EntryDate     time.Time
	CreatedAt     time.Time
	UpdatedAt     time.Time
	DeletedAt     *time.Time
}

// Category keys, mirrored by GCATS in the client. Unknown keys fall back to
// "moments" rather than failing a write — a newer client must never lose an entry
// against an older server.
var categories = map[string]bool{
	"people": true, "nature": true, "home": true, "work": true,
	"learning": true, "mind": true, "health": true, "moments": true,
	"faith": true, "wins": true, "hard": true,
}

// CategoryLabels are the display names used in insights copy.
var CategoryLabels = map[string]string{
	"people": "People", "nature": "Nature", "home": "Home", "work": "Work",
	"learning": "Learning", "mind": "Mind", "health": "Health",
	"moments": "Small moments", "faith": "Faith", "wins": "Achievements",
	"hard": "Difficult",
}

const DefaultCategory = "moments"

func NormalizeCategory(c string) string {
	c = strings.ToLower(strings.TrimSpace(c))
	if categories[c] {
		return c
	}
	return DefaultCategory
}

// Moods, ordered low → wonderful. The index doubles as the value plotted on the
// weekly mood chart.
var Moods = []string{"low", "difficult", "okay", "good", "wonderful"}

func MoodIndex(m string) int {
	for i, v := range Moods {
		if v == m {
			return i
		}
	}
	return -1
}

func ValidMood(m string) bool { return MoodIndex(m) >= 0 }

var modes = map[string]bool{"quick": true, "three": true, "reflect": true}

func ValidMode(m string) bool { return modes[m] }

// ---- prompts ----

// Prompts are the rotating "Today's gratitude" questions. The client rotates
// locally; this endpoint exists so the copy can change without an app release.
var Prompts = []string{
	"What is one small thing that made today better?",
	"What made you smile today?",
	"Who made your day better?",
	"What is something you often take for granted?",
	"What small moment felt meaningful today?",
	"What are you grateful for about yourself?",
	"What made today easier?",
	"What is something beautiful you noticed today?",
	"What comfort did you find on a difficult day?",
	"What is a lesson today quietly offered you?",
}

// ---- challenges ----

// Challenge is a guided journey. The catalogue is static; only participation and
// completed days are stored per user.
type Challenge struct {
	ID    string   `json:"id"`
	Title string   `json:"title"`
	Sub   string   `json:"sub"`
	Total int      `json:"total"`
	Days  []string `json:"days,omitempty"`
}

var Challenges = []Challenge{
	{
		ID: "c7", Title: "7-Day Gratitude Journey", Sub: "A gentle week of noticing", Total: 7,
		Days: []string{
			"Small things", "People", "Yourself", "Nature",
			"Growth", "Difficult moments", "Looking back",
		},
	},
	{ID: "c30", Title: "30-Day Gratitude Journey", Sub: "Deepen the habit", Total: 30},
}

func ChallengeByID(id string) (Challenge, bool) {
	for _, c := range Challenges {
		if c.ID == id {
			return c, true
		}
	}
	return Challenge{}, false
}

// ---- settings ----

type Settings struct {
	ReminderSlot string
	ReminderTime string
	FaceID       bool
	AppLock      bool
	HidePreview  bool
	CloudSync    bool
	UpdatedAt    time.Time
}

func DefaultSettings() Settings {
	return Settings{
		ReminderSlot: "evening",
		ReminderTime: "19:30",
		HidePreview:  true,
		CloudSync:    true,
	}
}

var reminderSlots = map[string]bool{"off": true, "morning": true, "afternoon": true, "evening": true}

func ValidReminderSlot(s string) bool { return reminderSlots[s] }

// ---- pure stats ----

// dayNumber is days since the epoch for a civil date, which makes "consecutive
// day" arithmetic plain integer maths — no timezone database, directly testable.
func dayNumber(t time.Time) int {
	y, m, d := t.Date()
	return int(time.Date(y, m, d, 0, 0, 0, 0, time.UTC).Unix() / 86400)
}

// Stats is the streak/volume summary behind the Gratitude home card.
type Stats struct {
	Streak    int
	Longest   int
	Total     int
	MonthDays int
	Week      [7]bool // oldest → today
}

// ComputeStats derives the summary from the distinct days the user wrote on.
//
// The current streak still counts when today has nothing yet but yesterday does:
// the day is not over, so the run must not read as broken until it actually is.
func ComputeStats(days []time.Time, total int, today time.Time) Stats {
	s := Stats{Total: total}
	if len(days) == 0 {
		return s
	}

	set := make(map[int]bool, len(days))
	nums := make([]int, 0, len(days))
	for _, d := range days {
		n := dayNumber(d)
		if set[n] {
			continue
		}
		set[n] = true
		nums = append(nums, n)
	}
	sort.Ints(nums)

	// longest run anywhere in the history
	run := 1
	s.Longest = 1
	for i := 1; i < len(nums); i++ {
		if nums[i] == nums[i-1]+1 {
			run++
		} else {
			run = 1
		}
		if run > s.Longest {
			s.Longest = run
		}
	}

	// current run ending today (or yesterday)
	t := dayNumber(today)
	cursor := 0
	switch {
	case set[t]:
		cursor = t
	case set[t-1]:
		cursor = t - 1
	default:
		cursor = 0
	}
	if cursor != 0 {
		for set[cursor] {
			s.Streak++
			cursor--
		}
	}

	// this calendar month + trailing week
	y, mo, _ := today.Date()
	for _, d := range days {
		dy, dm, _ := d.Date()
		if dy == y && dm == mo {
			s.MonthDays++
		}
	}
	for i := 0; i < 7; i++ {
		s.Week[i] = set[t-6+i]
	}
	return s
}

// CategoryShare is one row of the "What you're grateful for" breakdown.
type CategoryShare struct {
	Key     string
	Label   string
	Percent int
	Count   int
}

// CategoryBreakdown converts raw counts into the top [limit] categories by share,
// rounded to whole percent and ordered high → low (ties broken by key so the list
// is stable between requests).
func CategoryBreakdown(counts map[string]int, limit int) []CategoryShare {
	total := 0
	for _, n := range counts {
		total += n
	}
	if total == 0 {
		return nil
	}
	out := make([]CategoryShare, 0, len(counts))
	for k, n := range counts {
		out = append(out, CategoryShare{
			Key:     k,
			Label:   CategoryLabels[k],
			Count:   n,
			Percent: int((float64(n)/float64(total))*100 + 0.5),
		})
	}
	sort.Slice(out, func(i, j int) bool {
		if out[i].Count != out[j].Count {
			return out[i].Count > out[j].Count
		}
		return out[i].Key < out[j].Key
	})
	if limit > 0 && len(out) > limit {
		out = out[:limit]
	}
	return out
}

// Reflection is the short "AI reflection" line on the insights screen. It is
// derived from the user's own data rather than generated, so it costs nothing,
// never hallucinates and works offline-first for every user.
func Reflection(cats []CategoryShare, entries, days int) string {
	if entries == 0 {
		return "Your journal is still empty. One line today is enough to begin."
	}
	if len(cats) == 0 {
		return "You have been writing steadily. Keep noticing the small things."
	}
	top := cats[0]
	switch top.Key {
	case "people":
		return "You often mention the people close to you. Relationships seem to be one of the strongest sources of gratitude in your journal this month."
	case "faith":
		return "Much of your gratitude returns to faith and quiet worship — a steady anchor running through the month."
	case "learning":
		return "Learning shows up again and again. Growth appears to be where you find most of your gratitude right now."
	case "nature", "moments":
		return "Small moments and the world around you carry most of your gratitude — you notice what many people walk past."
	case "hard":
		return "You have been finding gratitude inside difficult days. That is the harder kind, and you have done it " + plural(top.Count, "time", "times") + "."
	default:
		return "Most of your gratitude this month gathers around " + strings.ToLower(top.Label) + " — " + plural(entries, "entry", "entries") + " across " + plural(days, "day", "days") + "."
	}
}

// MonthLine is the sentence at the top of the monthly reflection.
func MonthLine(month string, cats []CategoryShare, entries int) string {
	if entries == 0 {
		return month + " has no entries yet — there is still time to notice something."
	}
	names := make([]string, 0, 3)
	for _, c := range cats {
		if len(names) == 3 {
			break
		}
		names = append(names, strings.ToLower(c.Label))
	}
	return month + " was full of moments connected to " + joinNatural(names) + " — a steady, gentle month."
}

func joinNatural(v []string) string {
	switch len(v) {
	case 0:
		return "quiet days"
	case 1:
		return v[0]
	case 2:
		return v[0] + " and " + v[1]
	default:
		return strings.Join(v[:len(v)-1], ", ") + " and " + v[len(v)-1]
	}
}

func plural(n int, one, many string) string {
	if n == 1 {
		return "1 " + one
	}
	return strconv.Itoa(n) + " " + many
}
