package prayer

import (
	"math"
	"net/http"
	"strconv"
	"time"

	"github.com/go-chi/chi/v5"

	"github.com/tafakkur/backend/internal/platform"
)

type Handler struct{ svc *Service }

func NewHandler(svc *Service) *Handler { return &Handler{svc: svc} }

func (h *Handler) Register(r chi.Router) {
	r.Get("/prayer/times", h.times)
	r.Post("/prayer/track", h.track)
	r.Post("/prayer/complete", h.complete)
	r.Get("/prayer/completions", h.completions)
}

func fixedZoneFromLng(lng float64) *time.Location {
	offset := int(math.Round(lng/15)) * 3600
	return time.FixedZone("UTC", offset)
}

func (h *Handler) times(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()

	lat, err1 := strconv.ParseFloat(q.Get("lat"), 64)
	lng, err2 := strconv.ParseFloat(q.Get("lng"), 64)
	if err1 != nil || err2 != nil {
		platform.Error(w, http.StatusBadRequest, platform.CodeBadRequest, "lat and lng query params are required")
		return
	}

	loc := fixedZoneFromLng(lng)
	if tz := q.Get("tz"); tz != "" {
		if l, err := time.LoadLocation(tz); err == nil {
			loc = l
		}
	}

	date := time.Now().In(loc)
	if ds := q.Get("date"); ds != "" {
		if d, err := time.ParseInLocation("2006-01-02", ds, loc); err == nil {
			date = d
		} else {
			platform.Error(w, http.StatusBadRequest, platform.CodeBadRequest, "date must be YYYY-MM-DD")
			return
		}
	}

	resp, err := h.svc.Times(r.Context(), platform.MustUserID(r.Context()),
		date, lat, lng, loc, q.Get("method"), q.Get("madhab"))
	if err != nil {
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not compute times")
		return
	}
	platform.JSON(w, http.StatusOK, resp)
}

type TrackInput struct {
	Prayer string  `json:"prayer" validate:"required,oneof=fajr dhuhr asr maghrib isha"`
	Date   *string `json:"date" validate:"omitempty,datetime=2006-01-02"`
}

func (h *Handler) track(w http.ResponseWriter, r *http.Request) {
	var in TrackInput
	if !platform.DecodeAndValidate(w, r, &in) {
		return
	}
	var date time.Time
	if in.Date != nil {
		date, _ = time.Parse("2006-01-02", *in.Date)
	}
	state, err := h.svc.Track(r.Context(), platform.MustUserID(r.Context()), in.Prayer, date)
	if err != nil {
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not track prayer")
		return
	}
	platform.JSON(w, http.StatusOK, map[string]any{
		"ok": true,
		"gamification": map[string]int{
			"streak": state.Streak, "level": state.Level, "xp": state.XP, "xp_next": state.XPNext,
		},
	})
}

type completeRequest struct {
	Prayer        string   `json:"prayer" validate:"required,oneof=fajr dhuhr asr maghrib isha"`
	ScheduledTime string   `json:"scheduled_time" validate:"required"`
	CompletedAt   string   `json:"completed_at" validate:"required"`
	Latitude      *float64 `json:"latitude"`
	Longitude     *float64 `json:"longitude"`
	LocationLabel *string  `json:"location_label"`
}

type CompletionDTO struct {
	ID            string  `json:"id"`
	Prayer        string  `json:"prayer"`
	ScheduledTime string  `json:"scheduled_time"`
	CompletedAt   string  `json:"completed_at"`
	DelayMinutes  int     `json:"delay_minutes"`
	OnTime        bool    `json:"on_time"`
	Late          bool    `json:"late"`
	LocationLabel *string `json:"location_label,omitempty"`
}

func toCompletionDTO(c Completion) CompletionDTO {
	return CompletionDTO{
		ID:            c.ID.String(),
		Prayer:        c.Prayer,
		ScheduledTime: c.ScheduledTime.Format(time.RFC3339),
		CompletedAt:   c.CompletedAt.Format(time.RFC3339),
		DelayMinutes:  c.DelayMinutes,
		OnTime:        c.OnTime,
		Late:          c.Late,
		LocationLabel: c.LocationLabel,
	}
}

func (h *Handler) complete(w http.ResponseWriter, r *http.Request) {
	var in completeRequest
	if !platform.DecodeAndValidate(w, r, &in) {
		return
	}
	scheduled, err := time.Parse(time.RFC3339, in.ScheduledTime)
	if err != nil {
		platform.Error(w, http.StatusBadRequest, platform.CodeBadRequest, "scheduled_time must be RFC3339")
		return
	}
	completed, err := time.Parse(time.RFC3339, in.CompletedAt)
	if err != nil {
		platform.Error(w, http.StatusBadRequest, platform.CodeBadRequest, "completed_at must be RFC3339")
		return
	}

	res, err := h.svc.Complete(r.Context(), platform.MustUserID(r.Context()), CompleteInput{
		Prayer:        in.Prayer,
		ScheduledTime: scheduled,
		CompletedAt:   completed,
		Latitude:      in.Latitude,
		Longitude:     in.Longitude,
		LocationLabel: in.LocationLabel,
	})
	if err != nil {
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not save completion")
		return
	}

	platform.JSON(w, http.StatusCreated, map[string]any{
		"completion": toCompletionDTO(res.Completion),
		"gamification": map[string]int{
			"streak": res.Gamification.Streak, "level": res.Gamification.Level,
			"xp": res.Gamification.XP, "xp_next": res.Gamification.XPNext,
		},
	})
}

func (h *Handler) completions(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	from := time.Now().AddDate(0, 0, -30)
	to := time.Now()
	if s := q.Get("from"); s != "" {
		if t, err := time.Parse("2006-01-02", s); err == nil {
			from = t
		}
	}
	if s := q.Get("to"); s != "" {
		if t, err := time.Parse("2006-01-02", s); err == nil {
			to = t.Add(24 * time.Hour)
		}
	}

	list, err := h.svc.History(r.Context(), platform.MustUserID(r.Context()), from, to)
	if err != nil {
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not list completions")
		return
	}
	out := make([]CompletionDTO, 0, len(list))
	for _, c := range list {
		out = append(out, toCompletionDTO(c))
	}
	platform.JSON(w, http.StatusOK, map[string]any{"completions": out})
}
