package gratitude

import (
	"encoding/csv"
	"errors"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/google/uuid"

	"github.com/tafakkur/backend/internal/gamify"
	"github.com/tafakkur/backend/internal/platform"
	"github.com/tafakkur/backend/internal/tracker"
)

// Handler exposes the Gratitude module over HTTP. It also writes a mirror row to
// the generic tracker log on create, so the Today card ("3 gratitudes today")
// keeps working without knowing this module exists.
type Handler struct {
	svc     Service
	gamify  gamify.Service
	tracker tracker.Service
}

func NewHandler(svc Service, g gamify.Service, t tracker.Service) *Handler {
	return &Handler{svc: svc, gamify: g, tracker: t}
}

func (h *Handler) Register(r chi.Router) {
	r.Route("/gratitude", func(r chi.Router) {
		r.Get("/prompts", h.prompts)

		r.Post("/entries", h.create)
		r.Get("/entries", h.list)
		r.Get("/entries/{id}", h.get)
		r.Patch("/entries/{id}", h.update)
		r.Post("/entries/{id}/favourite", h.favourite)
		r.Delete("/entries/{id}", h.remove)

		r.Get("/changes", h.changes)
		r.Get("/stats", h.stats)
		r.Get("/insights", h.insights)
		r.Get("/random", h.random)
		r.Get("/throwback", h.throwback)
		r.Get("/export", h.export)

		r.Get("/challenges", h.challenges)
		r.Post("/challenges/{id}/join", h.join)
		r.Post("/challenges/{id}/complete", h.completeDay)

		r.Get("/settings", h.settings)
		r.Put("/settings", h.saveSettings)
	})
}

// ---- entries ----

func (h *Handler) create(w http.ResponseWriter, r *http.Request) {
	var in CreateEntryInput
	if !platform.DecodeAndValidate(w, r, &in) {
		return
	}
	var date time.Time
	if in.EntryDate != nil {
		date, _ = time.Parse(DateLayout, *in.EntryDate)
	}

	uid := platform.MustUserID(r.Context())
	e, err := h.svc.Create(r.Context(), uid, NewEntry{
		ClientID: in.ClientID, Mode: in.Mode, Body: in.Text, Items: in.Items, Why: in.Why,
		Category: in.Category, Mood: in.Mood, Emotions: in.Emotions, PhotoCount: in.PhotoCount,
		HasAudio: in.HasAudio, HasVideo: in.HasVideo, LocationLabel: in.LocationLabel,
		Favourite: in.Favourite, EntryDate: date,
	})
	if err != nil {
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not save gratitude")
		return
	}

	// Best-effort side effects: XP and the generic tracker mirror. Neither may fail
	// the write the user actually asked for.
	if h.gamify != nil {
		_, _ = h.gamify.Award(r.Context(), uid, gamify.EventTrackerEntry)
	}
	h.mirrorToTracker(r, uid, e)

	platform.JSON(w, http.StatusCreated, toDTO(e))
}

// mirrorToTracker writes the lightweight tracker_entries row the Today card reads.
func (h *Handler) mirrorToTracker(r *http.Request, uid uuid.UUID, e Entry) {
	if h.tracker == nil {
		return
	}
	payload := `{"source":"gratitude","entry_id":"` + e.ID.String() +
		`","category":"` + e.Category + `","text":` + strconv.Quote(e.Body) + `}`
	_, _ = h.tracker.Create(r.Context(), uid, "gratitude", []byte(payload), e.EntryDate)
}

func (h *Handler) list(w http.ResponseWriter, r *http.Request) {
	f := filterFrom(r)
	entries, err := h.svc.List(r.Context(), platform.MustUserID(r.Context()), f)
	if err != nil {
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not list entries")
		return
	}
	out := map[string]any{"entries": toDTOs(entries)}
	// Keyset cursor: pass the last created_at back as ?before= for the next page.
	if len(entries) == f.Limit && f.Limit > 0 {
		out["next_cursor"] = entries[len(entries)-1].CreatedAt.Format(time.RFC3339Nano)
	}
	platform.JSON(w, http.StatusOK, out)
}

func filterFrom(r *http.Request) Filter {
	q := r.URL.Query()
	f := Filter{
		Query:     q.Get("q"),
		Category:  q.Get("category"),
		Favourite: q.Get("favourite") == "true",
		Limit:     50,
	}
	if v, err := time.Parse(DateLayout, q.Get("from")); err == nil {
		f.From = v
	}
	if v, err := time.Parse(DateLayout, q.Get("to")); err == nil {
		f.To = v
	}
	if n, err := strconv.Atoi(q.Get("limit")); err == nil && n > 0 {
		f.Limit = n
	}
	if v, err := time.Parse(time.RFC3339Nano, q.Get("before")); err == nil {
		f.Before = v
	}
	return f
}

func (h *Handler) get(w http.ResponseWriter, r *http.Request) {
	id, ok := entryID(w, r)
	if !ok {
		return
	}
	e, err := h.svc.Get(r.Context(), platform.MustUserID(r.Context()), id)
	if err != nil {
		writeEntryErr(w, err, "could not load entry")
		return
	}
	platform.JSON(w, http.StatusOK, toDTO(e))
}

func (h *Handler) update(w http.ResponseWriter, r *http.Request) {
	id, ok := entryID(w, r)
	if !ok {
		return
	}
	var in UpdateEntryInput
	if !platform.DecodeAndValidate(w, r, &in) {
		return
	}
	e, err := h.svc.Update(r.Context(), platform.MustUserID(r.Context()), id, Patch{
		Body: in.Text, Why: in.Why, Category: in.Category,
		Mood: in.Mood, Emotions: in.Emotions, Favourite: in.Favourite,
	})
	if err != nil {
		writeEntryErr(w, err, "could not update entry")
		return
	}
	platform.JSON(w, http.StatusOK, toDTO(e))
}

func (h *Handler) favourite(w http.ResponseWriter, r *http.Request) {
	id, ok := entryID(w, r)
	if !ok {
		return
	}
	e, err := h.svc.ToggleFavourite(r.Context(), platform.MustUserID(r.Context()), id)
	if err != nil {
		writeEntryErr(w, err, "could not update entry")
		return
	}
	platform.JSON(w, http.StatusOK, toDTO(e))
}

func (h *Handler) remove(w http.ResponseWriter, r *http.Request) {
	id, ok := entryID(w, r)
	if !ok {
		return
	}
	if err := h.svc.Delete(r.Context(), platform.MustUserID(r.Context()), id); err != nil {
		writeEntryErr(w, err, "could not delete entry")
		return
	}
	platform.JSON(w, http.StatusNoContent, nil)
}

// changes is the pull half of offline sync: everything touched since a timestamp,
// deletions included.
func (h *Handler) changes(w http.ResponseWriter, r *http.Request) {
	since, err := time.Parse(time.RFC3339Nano, r.URL.Query().Get("since"))
	if err != nil {
		since = time.Unix(0, 0).UTC()
	}
	limit, _ := strconv.Atoi(r.URL.Query().Get("limit"))

	entries, err := h.svc.Changes(r.Context(), platform.MustUserID(r.Context()), since, limit)
	if err != nil {
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not load changes")
		return
	}
	cursor := since
	if len(entries) > 0 {
		cursor = entries[len(entries)-1].UpdatedAt
	}
	platform.JSON(w, http.StatusOK, map[string]any{
		"entries": toDTOs(entries),
		"cursor":  cursor.Format(time.RFC3339Nano),
		"more":    len(entries) > 0 && (limit == 0 || len(entries) == limit),
	})
}

// ---- read models ----

func (h *Handler) prompts(w http.ResponseWriter, r *http.Request) {
	platform.JSON(w, http.StatusOK, map[string]any{"prompts": Prompts})
}

func (h *Handler) stats(w http.ResponseWriter, r *http.Request) {
	s, err := h.svc.Stats(r.Context(), platform.MustUserID(r.Context()))
	if err != nil {
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not load stats")
		return
	}
	platform.JSON(w, http.StatusOK, statsDTO(s))
}

func (h *Handler) insights(w http.ResponseWriter, r *http.Request) {
	var month time.Time
	if v := r.URL.Query().Get("month"); v != "" {
		if t, err := time.Parse("2006-01", v); err == nil {
			month = t
		}
	}
	in, err := h.svc.Insights(r.Context(), platform.MustUserID(r.Context()), month)
	if err != nil {
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not load insights")
		return
	}
	platform.JSON(w, http.StatusOK, insightsDTO(in))
}

func (h *Handler) random(w http.ResponseWriter, r *http.Request) {
	e, err := h.svc.Random(r.Context(), platform.MustUserID(r.Context()))
	if err != nil {
		writeEntryErr(w, err, "could not pick a memory")
		return
	}
	platform.JSON(w, http.StatusOK, toDTO(e))
}

func (h *Handler) throwback(w http.ResponseWriter, r *http.Request) {
	e, err := h.svc.Throwback(r.Context(), platform.MustUserID(r.Context()))
	if err != nil {
		writeEntryErr(w, err, "no memory to revisit yet")
		return
	}
	platform.JSON(w, http.StatusOK, toDTO(e))
}

// export returns the whole journal in the format the settings screen offers.
// PDF is deliberately not produced server-side — the client renders it from JSON
// so the export never leaves the device unencrypted for a formatting step.
func (h *Handler) export(w http.ResponseWriter, r *http.Request) {
	format := strings.ToLower(r.URL.Query().Get("format"))
	if format == "" {
		format = "json"
	}
	entries, err := h.svc.List(r.Context(), platform.MustUserID(r.Context()), Filter{Limit: 200})
	if err != nil {
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not export")
		return
	}

	switch format {
	case "json":
		platform.JSON(w, http.StatusOK, map[string]any{"entries": toDTOs(entries)})
	case "csv":
		w.Header().Set("Content-Type", "text/csv; charset=utf-8")
		w.Header().Set("Content-Disposition", `attachment; filename="gratitude.csv"`)
		cw := csv.NewWriter(w)
		_ = cw.Write([]string{"date", "category", "mood", "emotions", "text", "why", "favourite"})
		for _, e := range entries {
			_ = cw.Write([]string{
				e.EntryDate.Format(DateLayout), e.Category, deref(e.Mood),
				strings.Join(e.Emotions, "|"), e.Body, deref(e.Why),
				strconv.FormatBool(e.Favourite),
			})
		}
		cw.Flush()
	case "txt", "text":
		w.Header().Set("Content-Type", "text/plain; charset=utf-8")
		w.Header().Set("Content-Disposition", `attachment; filename="gratitude.txt"`)
		var b strings.Builder
		for _, e := range entries {
			b.WriteString(e.EntryDate.Format(DateLayout))
			b.WriteString(" · ")
			b.WriteString(CategoryLabels[e.Category])
			b.WriteString("\n")
			b.WriteString(e.Body)
			if e.Why != nil && *e.Why != "" {
				b.WriteString("\n(")
				b.WriteString(*e.Why)
				b.WriteString(")")
			}
			b.WriteString("\n\n")
		}
		_, _ = w.Write([]byte(b.String()))
	default:
		platform.Error(w, http.StatusBadRequest, platform.CodeBadRequest, "unsupported export format")
	}
}

func deref(s *string) string {
	if s == nil {
		return ""
	}
	return *s
}

// ---- challenges ----

func (h *Handler) challenges(w http.ResponseWriter, r *http.Request) {
	list, err := h.svc.Challenges(r.Context(), platform.MustUserID(r.Context()))
	if err != nil {
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not load journeys")
		return
	}
	out := make([]ChallengeDTO, 0, len(list))
	for _, p := range list {
		out = append(out, challengeDTO(p))
	}
	platform.JSON(w, http.StatusOK, map[string]any{"challenges": out})
}

func (h *Handler) join(w http.ResponseWriter, r *http.Request) {
	var in JoinChallengeInput
	if !platform.DecodeAndValidate(w, r, &in) {
		return
	}
	err := h.svc.Join(r.Context(), platform.MustUserID(r.Context()), chi.URLParam(r, "id"), in.Joined)
	if errors.Is(err, ErrNotFound) {
		platform.Error(w, http.StatusNotFound, platform.CodeNotFound, "unknown journey")
		return
	}
	if err != nil {
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not update journey")
		return
	}
	h.challenges(w, r)
}

func (h *Handler) completeDay(w http.ResponseWriter, r *http.Request) {
	var in CompleteDayInput
	if !platform.DecodeAndValidate(w, r, &in) {
		return
	}
	uid := platform.MustUserID(r.Context())
	err := h.svc.CompleteDay(r.Context(), uid, chi.URLParam(r, "id"), in.Day)
	if errors.Is(err, ErrNotFound) {
		platform.Error(w, http.StatusNotFound, platform.CodeNotFound, "unknown journey or day")
		return
	}
	if err != nil {
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not record the day")
		return
	}
	if h.gamify != nil {
		_, _ = h.gamify.Award(r.Context(), uid, gamify.EventTrackerEntry)
	}
	h.challenges(w, r)
}

// ---- settings ----

func (h *Handler) settings(w http.ResponseWriter, r *http.Request) {
	s, err := h.svc.Settings(r.Context(), platform.MustUserID(r.Context()))
	if err != nil {
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not load settings")
		return
	}
	platform.JSON(w, http.StatusOK, settingsDTO(s))
}

func (h *Handler) saveSettings(w http.ResponseWriter, r *http.Request) {
	var in SettingsInput
	if !platform.DecodeAndValidate(w, r, &in) {
		return
	}
	s, err := h.svc.SaveSettings(r.Context(), platform.MustUserID(r.Context()), Settings{
		ReminderSlot: in.ReminderSlot, ReminderTime: in.ReminderTime, FaceID: in.FaceID,
		AppLock: in.AppLock, HidePreview: in.HidePreview, CloudSync: in.CloudSync,
	})
	if err != nil {
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not save settings")
		return
	}
	platform.JSON(w, http.StatusOK, settingsDTO(s))
}

// ---- helpers ----

func entryID(w http.ResponseWriter, r *http.Request) (uuid.UUID, bool) {
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		platform.Error(w, http.StatusBadRequest, platform.CodeBadRequest, "invalid entry id")
		return uuid.Nil, false
	}
	return id, true
}

func writeEntryErr(w http.ResponseWriter, err error, msg string) {
	if errors.Is(err, ErrNotFound) {
		platform.Error(w, http.StatusNotFound, platform.CodeNotFound, "entry not found")
		return
	}
	platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, msg)
}
