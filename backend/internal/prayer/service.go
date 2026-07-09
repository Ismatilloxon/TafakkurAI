package prayer

import (
	"context"
	"encoding/json"
	"time"

	"github.com/google/uuid"

	"github.com/tafakkur/backend/internal/gamify"
	"github.com/tafakkur/backend/internal/tracker"
	"github.com/tafakkur/backend/internal/user"
)

type Service struct {
	users       user.Service
	gamify      gamify.Service
	tracker     tracker.Service
	completions *CompletionRepo
}

func NewService(u user.Service, g gamify.Service, tr tracker.Service, completions *CompletionRepo) *Service {
	return &Service{users: u, gamify: g, tracker: tr, completions: completions}
}

// onTimeWindowMinutes: completions within this many minutes of the scheduled
// adhan time count as on-time; beyond it, late.
const onTimeWindowMinutes = 15

type PrayerTimeDTO struct {
	Name string `json:"name"`
	Time string `json:"time"`
}

type NextPrayerDTO struct {
	Name             string `json:"name"`
	Time             string `json:"time"`
	SecondsRemaining int64  `json:"seconds_remaining"`
}

type TimesResponse struct {
	Date      string          `json:"date"`
	Latitude  float64         `json:"latitude"`
	Longitude float64         `json:"longitude"`
	Method    string          `json:"method"`
	Madhab    string          `json:"madhab"`
	Times     []PrayerTimeDTO `json:"times"`
	Next      NextPrayerDTO   `json:"next"`
}

// Times computes the day's prayer times. method/madhab fall back to the user's
// saved profile settings when the query omits them.
func (s *Service) Times(ctx context.Context, userID uuid.UUID, date time.Time, lat, lng float64, loc *time.Location, method, madhab string) (TimesResponse, error) {
	if method == "" || madhab == "" {
		if acc, err := s.users.GetAccount(ctx, userID); err == nil {
			if method == "" {
				method = acc.Profile.CalcMethod
			}
			if madhab == "" {
				madhab = acc.Profile.Madhab
			}
		}
	}
	if madhab == "" {
		madhab = "hanafi"
	}
	m, ok := MethodByName(method)
	if !ok {
		m, method = Methods["MWL"], "MWL"
	}

	factor := AsrShadowFactor(madhab)
	today := Calculate(date, lat, lng, loc, m, factor)

	ordered := []struct {
		name string
		t    time.Time
	}{
		{"fajr", today.Fajr},
		{"dhuhr", today.Dhuhr},
		{"asr", today.Asr},
		{"maghrib", today.Maghrib},
		{"isha", today.Isha},
	}

	resp := TimesResponse{
		Date:      date.Format("2006-01-02"),
		Latitude:  lat,
		Longitude: lng,
		Method:    method,
		Madhab:    madhab,
	}
	resp.Times = []PrayerTimeDTO{
		{"fajr", today.Fajr.Format(time.RFC3339)},
		{"sunrise", today.Sunrise.Format(time.RFC3339)},
		{"dhuhr", today.Dhuhr.Format(time.RFC3339)},
		{"asr", today.Asr.Format(time.RFC3339)},
		{"maghrib", today.Maghrib.Format(time.RFC3339)},
		{"isha", today.Isha.Format(time.RFC3339)},
	}

	now := time.Now().In(loc)
	for _, p := range ordered {
		if p.t.After(now) {
			resp.Next = NextPrayerDTO{
				Name:             p.name,
				Time:             p.t.Format(time.RFC3339),
				SecondsRemaining: int64(p.t.Sub(now).Seconds()),
			}
			return resp, nil
		}
	}
	// All of today's prayers have passed — next is tomorrow's Fajr.
	tomorrow := Calculate(date.AddDate(0, 0, 1), lat, lng, loc, m, factor)
	resp.Next = NextPrayerDTO{
		Name:             "fajr",
		Time:             tomorrow.Fajr.Format(time.RFC3339),
		SecondsRemaining: int64(tomorrow.Fajr.Sub(now).Seconds()),
	}
	return resp, nil
}

// Track records that the user prayed a given prayer and awards XP.
func (s *Service) Track(ctx context.Context, userID uuid.UUID, prayer string, date time.Time) (gamify.State, error) {
	payload, _ := json.Marshal(map[string]string{"prayer": prayer})
	if _, err := s.tracker.Create(ctx, userID, "prayer", payload, date); err != nil {
		return gamify.State{}, err
	}
	return s.gamify.Award(ctx, userID, gamify.EventPrayerTracked)
}

// CompleteInput carries a full completion record from the client, which
// already knows the scheduled adhan time (via PrayerTimelineService) and the
// moment the user tapped "Complete Prayer".
type CompleteInput struct {
	Prayer        string
	ScheduledTime time.Time
	CompletedAt   time.Time
	Latitude      *float64
	Longitude     *float64
	LocationLabel *string
}

type CompleteResult struct {
	Completion   Completion
	Gamification gamify.State
}

// Complete records a rich prayer completion (delay, on-time/late) and awards
// XP — a base amount for logging, plus a bonus for praying on time.
func (s *Service) Complete(ctx context.Context, userID uuid.UUID, in CompleteInput) (CompleteResult, error) {
	delay := int(in.CompletedAt.Sub(in.ScheduledTime).Minutes())
	if delay < 0 {
		delay = 0
	}
	onTime := delay <= onTimeWindowMinutes

	c, err := s.completions.Create(ctx, Completion{
		UserID:        userID,
		Prayer:        in.Prayer,
		ScheduledTime: in.ScheduledTime,
		CompletedAt:   in.CompletedAt,
		DelayMinutes:  delay,
		OnTime:        onTime,
		Late:          !onTime,
		Latitude:      in.Latitude,
		Longitude:     in.Longitude,
		LocationLabel: in.LocationLabel,
	})
	if err != nil {
		return CompleteResult{}, err
	}

	// Mirror into the generic tracker log so other features (feed, coach)
	// that read tracker_entries still see prayer activity.
	payload, _ := json.Marshal(map[string]any{"prayer": in.Prayer, "on_time": onTime, "delay_minutes": delay})
	if _, err := s.tracker.Create(ctx, userID, "prayer", payload, in.CompletedAt); err != nil {
		return CompleteResult{}, err
	}

	state, err := s.gamify.Award(ctx, userID, gamify.EventPrayerTracked)
	if err != nil {
		return CompleteResult{}, err
	}
	if onTime {
		state, err = s.gamify.Award(ctx, userID, gamify.EventPrayerOnTime)
		if err != nil {
			return CompleteResult{}, err
		}
	}

	return CompleteResult{Completion: c, Gamification: state}, nil
}

// History returns completions in [from, to] for the Prayer History screen.
func (s *Service) History(ctx context.Context, userID uuid.UUID, from, to time.Time) ([]Completion, error) {
	return s.completions.List(ctx, userID, from, to)
}
