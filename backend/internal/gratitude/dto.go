package gratitude

import "time"

// ---- requests ----

type CreateEntryInput struct {
	ClientID      string   `json:"client_id" validate:"omitempty,max=64"`
	Mode          string   `json:"mode" validate:"omitempty,oneof=quick three reflect"`
	Text          string   `json:"text" validate:"required,max=8000"`
	Items         []string `json:"items" validate:"omitempty,max=10,dive,max=500"`
	Why           *string  `json:"why" validate:"omitempty,max=2000"`
	Category      string   `json:"category" validate:"omitempty,max=32"`
	Mood          *string  `json:"mood" validate:"omitempty,oneof=low difficult okay good wonderful"`
	Emotions      []string `json:"emotions" validate:"omitempty,max=16,dive,max=32"`
	PhotoCount    int      `json:"photo_count" validate:"omitempty,min=0,max=20"`
	HasAudio      bool     `json:"has_audio"`
	HasVideo      bool     `json:"has_video"`
	LocationLabel *string  `json:"location_label" validate:"omitempty,max=200"`
	Favourite     bool     `json:"favourite"`
	EntryDate     *string  `json:"entry_date" validate:"omitempty,datetime=2006-01-02"`
}

type UpdateEntryInput struct {
	Text      *string   `json:"text" validate:"omitempty,max=8000"`
	Why       *string   `json:"why" validate:"omitempty,max=2000"`
	Category  *string   `json:"category" validate:"omitempty,max=32"`
	Mood      *string   `json:"mood" validate:"omitempty,oneof=low difficult okay good wonderful"`
	Emotions  *[]string `json:"emotions" validate:"omitempty,max=16,dive,max=32"`
	Favourite *bool     `json:"favourite"`
}

type SettingsInput struct {
	ReminderSlot string `json:"reminder_slot" validate:"required,oneof=off morning afternoon evening"`
	ReminderTime string `json:"reminder_time" validate:"required,max=5"`
	FaceID       bool   `json:"face_id"`
	AppLock      bool   `json:"app_lock"`
	HidePreview  bool   `json:"hide_preview"`
	CloudSync    bool   `json:"cloud_sync"`
}

type JoinChallengeInput struct {
	Joined bool `json:"joined"`
}

type CompleteDayInput struct {
	Day int `json:"day" validate:"required,min=1,max=30"`
}

// ---- responses ----

type EntryDTO struct {
	ID            string   `json:"id"`
	ClientID      string   `json:"client_id"`
	Mode          string   `json:"mode"`
	Text          string   `json:"text"`
	Items         []string `json:"items"`
	Why           *string  `json:"why,omitempty"`
	Category      string   `json:"category"`
	Mood          *string  `json:"mood,omitempty"`
	Emotions      []string `json:"emotions"`
	PhotoCount    int      `json:"photo_count"`
	HasAudio      bool     `json:"has_audio"`
	HasVideo      bool     `json:"has_video"`
	LocationLabel *string  `json:"location_label,omitempty"`
	Favourite     bool     `json:"favourite"`
	EntryDate     string   `json:"entry_date"`
	CreatedAt     string   `json:"created_at"`
	UpdatedAt     string   `json:"updated_at"`
	Deleted       bool     `json:"deleted"`
}

func toDTO(e Entry) EntryDTO {
	items := e.Items
	if items == nil {
		items = []string{}
	}
	emotions := e.Emotions
	if emotions == nil {
		emotions = []string{}
	}
	return EntryDTO{
		ID:            e.ID.String(),
		ClientID:      e.ClientID,
		Mode:          e.Mode,
		Text:          e.Body,
		Items:         items,
		Why:           e.Why,
		Category:      e.Category,
		Mood:          e.Mood,
		Emotions:      emotions,
		PhotoCount:    e.PhotoCount,
		HasAudio:      e.HasAudio,
		HasVideo:      e.HasVideo,
		LocationLabel: e.LocationLabel,
		Favourite:     e.Favourite,
		EntryDate:     e.EntryDate.Format(DateLayout),
		CreatedAt:     e.CreatedAt.Format(time.RFC3339),
		UpdatedAt:     e.UpdatedAt.Format(time.RFC3339),
		Deleted:       e.DeletedAt != nil,
	}
}

func toDTOs(entries []Entry) []EntryDTO {
	out := make([]EntryDTO, 0, len(entries))
	for _, e := range entries {
		out = append(out, toDTO(e))
	}
	return out
}

type StatsDTO struct {
	Streak    int    `json:"streak"`
	Longest   int    `json:"longest"`
	Total     int    `json:"total"`
	MonthDays int    `json:"month_days"`
	Week      []bool `json:"week"`
}

func statsDTO(s Stats) StatsDTO {
	week := make([]bool, 7)
	copy(week, s.Week[:])
	return StatsDTO{Streak: s.Streak, Longest: s.Longest, Total: s.Total, MonthDays: s.MonthDays, Week: week}
}

type CategoryShareDTO struct {
	Key     string `json:"key"`
	Label   string `json:"label"`
	Percent int    `json:"percent"`
	Count   int    `json:"count"`
}

type MonthDTO struct {
	Name    string `json:"name"`
	Year    int    `json:"year"`
	Entries int    `json:"entries"`
	Days    int    `json:"days"`
	Longest int    `json:"longest"`
	TopCat  string `json:"top_category"`
	Line    string `json:"line"`
}

type InsightsDTO struct {
	Stats      StatsDTO           `json:"stats"`
	MoodWeek   []int              `json:"mood_week"`
	Categories []CategoryShareDTO `json:"categories"`
	Reflection string             `json:"reflection"`
	Month      MonthDTO           `json:"month"`
}

func insightsDTO(in Insights) InsightsDTO {
	cats := make([]CategoryShareDTO, 0, len(in.Categories))
	for _, c := range in.Categories {
		cats = append(cats, CategoryShareDTO{Key: c.Key, Label: c.Label, Percent: c.Percent, Count: c.Count})
	}
	return InsightsDTO{
		Stats:      statsDTO(in.Stats),
		MoodWeek:   in.MoodWeek,
		Categories: cats,
		Reflection: in.Reflection,
		Month: MonthDTO{
			Name: in.Month.Name, Year: in.Month.Year, Entries: in.Month.Entries,
			Days: in.Month.Days, Longest: in.Month.Longest, TopCat: in.Month.TopCat, Line: in.Month.Line,
		},
	}
}

type ChallengeDTO struct {
	ID     string   `json:"id"`
	Title  string   `json:"title"`
	Sub    string   `json:"sub"`
	Total  int      `json:"total"`
	Days   []string `json:"days,omitempty"`
	Joined bool     `json:"joined"`
	Done   int      `json:"done"`
	DoneAt []int    `json:"done_days"`
}

func challengeDTO(p ChallengeProgress) ChallengeDTO {
	days := p.Days
	if days == nil {
		days = []int{}
	}
	return ChallengeDTO{
		ID: p.ID, Title: p.Title, Sub: p.Sub, Total: p.Total, Days: p.Challenge.Days,
		Joined: p.Joined, Done: p.Done, DoneAt: days,
	}
}

type SettingsDTO struct {
	ReminderSlot string `json:"reminder_slot"`
	ReminderTime string `json:"reminder_time"`
	FaceID       bool   `json:"face_id"`
	AppLock      bool   `json:"app_lock"`
	HidePreview  bool   `json:"hide_preview"`
	CloudSync    bool   `json:"cloud_sync"`
	UpdatedAt    string `json:"updated_at,omitempty"`
}

func settingsDTO(s Settings) SettingsDTO {
	dto := SettingsDTO{
		ReminderSlot: s.ReminderSlot, ReminderTime: s.ReminderTime, FaceID: s.FaceID,
		AppLock: s.AppLock, HidePreview: s.HidePreview, CloudSync: s.CloudSync,
	}
	if !s.UpdatedAt.IsZero() {
		dto.UpdatedAt = s.UpdatedAt.Format(time.RFC3339)
	}
	return dto
}
