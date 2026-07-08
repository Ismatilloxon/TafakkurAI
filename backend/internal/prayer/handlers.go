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
