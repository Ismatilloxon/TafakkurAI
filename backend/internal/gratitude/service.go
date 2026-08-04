package gratitude

import (
	"context"
	"strings"
	"time"

	"github.com/google/uuid"
)

// Service is the module's use-case surface. Handlers depend on this interface,
// never on Repo, so the HTTP layer stays free of SQL and the rules below (mood
// validation, category fallback, insight assembly) have one home.
type Service interface {
	Create(ctx context.Context, userID uuid.UUID, in NewEntry) (Entry, error)
	List(ctx context.Context, userID uuid.UUID, f Filter) ([]Entry, error)
	Get(ctx context.Context, userID, id uuid.UUID) (Entry, error)
	Update(ctx context.Context, userID, id uuid.UUID, p Patch) (Entry, error)
	ToggleFavourite(ctx context.Context, userID, id uuid.UUID) (Entry, error)
	Delete(ctx context.Context, userID, id uuid.UUID) error
	Changes(ctx context.Context, userID uuid.UUID, since time.Time, limit int) ([]Entry, error)

	Stats(ctx context.Context, userID uuid.UUID) (Stats, error)
	Insights(ctx context.Context, userID uuid.UUID, month time.Time) (Insights, error)
	Random(ctx context.Context, userID uuid.UUID) (Entry, error)
	Throwback(ctx context.Context, userID uuid.UUID) (Entry, error)

	Challenges(ctx context.Context, userID uuid.UUID) ([]ChallengeProgress, error)
	Join(ctx context.Context, userID uuid.UUID, id string, join bool) error
	CompleteDay(ctx context.Context, userID uuid.UUID, id string, day int) error

	Settings(ctx context.Context, userID uuid.UUID) (Settings, error)
	SaveSettings(ctx context.Context, userID uuid.UUID, s Settings) (Settings, error)
}

// NewEntry is a create/replace request from the client. ClientID makes the write
// idempotent; everything else mirrors the composer.
type NewEntry struct {
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
}

type service struct {
	repo *Repo
	now  func() time.Time
}

func NewService(repo *Repo) Service { return &service{repo: repo, now: func() time.Time { return time.Now().UTC() } } }

func (s *service) Create(ctx context.Context, userID uuid.UUID, in NewEntry) (Entry, error) {
	// The client owns the id; an entry saved offline keeps the same identity when
	// it finally reaches us, however many times the outbox retries.
	if strings.TrimSpace(in.ClientID) == "" {
		in.ClientID = uuid.NewString()
	}
	if !ValidMode(in.Mode) {
		in.Mode = "quick"
	}
	if in.Mood != nil && !ValidMood(*in.Mood) {
		in.Mood = nil
	}
	if in.EntryDate.IsZero() {
		in.EntryDate = s.now()
	}
	if in.Items == nil {
		in.Items = []string{}
	}
	if in.Emotions == nil {
		in.Emotions = []string{}
	}
	if in.PhotoCount < 0 {
		in.PhotoCount = 0
	}

	return s.repo.Upsert(ctx, Entry{
		UserID:        userID,
		ClientID:      in.ClientID,
		Mode:          in.Mode,
		Body:          strings.TrimSpace(in.Body),
		Items:         in.Items,
		Why:           in.Why,
		Category:      NormalizeCategory(in.Category),
		Mood:          in.Mood,
		Emotions:      in.Emotions,
		PhotoCount:    in.PhotoCount,
		HasAudio:      in.HasAudio,
		HasVideo:      in.HasVideo,
		LocationLabel: in.LocationLabel,
		Favourite:     in.Favourite,
		EntryDate:     in.EntryDate,
	})
}

func (s *service) List(ctx context.Context, userID uuid.UUID, f Filter) ([]Entry, error) {
	if f.Category != "" {
		f.Category = NormalizeCategory(f.Category)
	}
	return s.repo.List(ctx, userID, f)
}

func (s *service) Get(ctx context.Context, userID, id uuid.UUID) (Entry, error) {
	return s.repo.Get(ctx, userID, id)
}

func (s *service) Update(ctx context.Context, userID, id uuid.UUID, p Patch) (Entry, error) {
	if p.Category != nil {
		c := NormalizeCategory(*p.Category)
		p.Category = &c
	}
	if p.Mood != nil && *p.Mood != "" && !ValidMood(*p.Mood) {
		p.Mood = nil
	}
	return s.repo.Update(ctx, userID, id, p)
}

func (s *service) ToggleFavourite(ctx context.Context, userID, id uuid.UUID) (Entry, error) {
	return s.repo.ToggleFavourite(ctx, userID, id)
}

func (s *service) Delete(ctx context.Context, userID, id uuid.UUID) error {
	return s.repo.SoftDelete(ctx, userID, id)
}

func (s *service) Changes(ctx context.Context, userID uuid.UUID, since time.Time, limit int) ([]Entry, error) {
	return s.repo.ChangedSince(ctx, userID, since, limit)
}

func (s *service) Stats(ctx context.Context, userID uuid.UUID) (Stats, error) {
	days, total, err := s.repo.ActiveDays(ctx, userID)
	if err != nil {
		return Stats{}, err
	}
	return ComputeStats(days, total, s.now()), nil
}

func (s *service) Random(ctx context.Context, userID uuid.UUID) (Entry, error) {
	return s.repo.Random(ctx, userID)
}

func (s *service) Throwback(ctx context.Context, userID uuid.UUID) (Entry, error) {
	return s.repo.Throwback(ctx, userID, s.now().AddDate(0, 0, -30))
}

// Insights is everything the insights + monthly reflection screens render.
type Insights struct {
	Stats      Stats
	MoodWeek   []int // 7 values, oldest → today; -1 = no mood logged
	Categories []CategoryShare
	Reflection string
	Month      MonthSummary
}

type MonthSummary struct {
	Name    string
	Year    int
	Entries int
	Days    int
	Longest int
	TopCat  string
	Line    string
}

func (s *service) Insights(ctx context.Context, userID uuid.UUID, month time.Time) (Insights, error) {
	now := s.now()
	if month.IsZero() {
		month = now
	}
	monthStart := time.Date(month.Year(), month.Month(), 1, 0, 0, 0, 0, time.UTC)
	monthEnd := monthStart.AddDate(0, 1, -1)

	stats, err := s.Stats(ctx, userID)
	if err != nil {
		return Insights{}, err
	}

	counts, monthEntries, err := s.repo.CategoryCounts(ctx, userID, monthStart, monthEnd)
	if err != nil {
		return Insights{}, err
	}
	cats := CategoryBreakdown(counts, 5)

	weekFrom := now.AddDate(0, 0, -6)
	moods, err := s.repo.MoodByDay(ctx, userID, weekFrom, now)
	if err != nil {
		return Insights{}, err
	}
	week := make([]int, 7)
	for i := 0; i < 7; i++ {
		day := weekFrom.AddDate(0, 0, i).Format(DateLayout)
		week[i] = MoodIndex(moods[day]) // MoodIndex("") == -1, i.e. nothing logged
	}

	monthDays, _, err := s.repo.DaysInRange(ctx, userID, monthStart, monthEnd)
	if err != nil {
		return Insights{}, err
	}
	monthStats := ComputeStats(monthDays, monthEntries, monthEnd)

	topCat := ""
	if len(cats) > 0 {
		topCat = cats[0].Label
	}
	name := monthStart.Format("January")

	return Insights{
		Stats:      stats,
		MoodWeek:   week,
		Categories: cats,
		Reflection: Reflection(cats, monthEntries, len(monthDays)),
		Month: MonthSummary{
			Name:    name,
			Year:    monthStart.Year(),
			Entries: monthEntries,
			Days:    len(monthDays),
			Longest: monthStats.Longest,
			TopCat:  topCat,
			Line:    MonthLine(name, cats, monthEntries),
		},
	}, nil
}

// ChallengeProgress is a catalogue entry merged with this user's progress.
type ChallengeProgress struct {
	Challenge
	Joined bool
	Done   int
	Days   []int
}

func (s *service) Challenges(ctx context.Context, userID uuid.UUID) ([]ChallengeProgress, error) {
	states, err := s.repo.ChallengeStates(ctx, userID)
	if err != nil {
		return nil, err
	}
	out := make([]ChallengeProgress, 0, len(Challenges))
	for _, c := range Challenges {
		p := ChallengeProgress{Challenge: c, Days: []int{}}
		if st, ok := states[c.ID]; ok {
			p.Joined = st.Joined
			p.Days = st.Days
			p.Done = len(st.Days)
		}
		out = append(out, p)
	}
	return out, nil
}

func (s *service) Join(ctx context.Context, userID uuid.UUID, id string, join bool) error {
	if _, ok := ChallengeByID(id); !ok {
		return ErrNotFound
	}
	if join {
		return s.repo.JoinChallenge(ctx, userID, id)
	}
	return s.repo.LeaveChallenge(ctx, userID, id)
}

func (s *service) CompleteDay(ctx context.Context, userID uuid.UUID, id string, day int) error {
	c, ok := ChallengeByID(id)
	if !ok || day < 1 || day > c.Total {
		return ErrNotFound
	}
	// Completing a day implies participation — joining first is a UI nicety, not a
	// precondition the API should enforce.
	if err := s.repo.JoinChallenge(ctx, userID, id); err != nil {
		return err
	}
	return s.repo.CompleteDay(ctx, userID, id, day)
}

func (s *service) Settings(ctx context.Context, userID uuid.UUID) (Settings, error) {
	return s.repo.Settings(ctx, userID)
}

func (s *service) SaveSettings(ctx context.Context, userID uuid.UUID, in Settings) (Settings, error) {
	if !ValidReminderSlot(in.ReminderSlot) {
		in.ReminderSlot = "evening"
	}
	if strings.TrimSpace(in.ReminderTime) == "" {
		in.ReminderTime = "19:30"
	}
	return s.repo.PutSettings(ctx, userID, in)
}
