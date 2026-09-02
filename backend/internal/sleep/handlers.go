package sleep

import (
	"errors"
	"net/http"
	"strconv"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/google/uuid"

	"github.com/tafakkur/backend/internal/gamify"
	"github.com/tafakkur/backend/internal/platform"
	"github.com/tafakkur/backend/internal/tracker"
)

// Handler exposes the Sleep module over HTTP.
//
// Like the Gratitude handler, it writes a mirror row into the generic tracker log so the
// Today card keeps working without knowing this module exists.
type Handler struct {
	svc     Service
	gamify  gamify.Service
	tracker tracker.Service
}

func NewHandler(svc Service, g gamify.Service, t tracker.Service) *Handler {
	return &Handler{svc: svc, gamify: g, tracker: t}
}

func (h *Handler) Register(r chi.Router) {
	r.Route("/sleep", func(r chi.Router) {
		r.Post("/sessions", h.upsert)
		r.Post("/sessions/batch", h.upsertBatch)
		r.Get("/sessions", h.list)
		r.Get("/sessions/{id}", h.get)
		r.Delete("/sessions/{id}", h.remove)

		r.Get("/changes", h.changes)
		r.Get("/stats", h.stats)

		r.Get("/routine", h.routine)
		r.Post("/routine", h.markRoutine)

		r.Get("/settings", h.settings)
		r.Put("/settings", h.saveSettings)
	})
}

// ---- sessions ----

func (h *Handler) upsert(w http.ResponseWriter, r *http.Request) {
	var in UpsertSessionInput
	if !platform.DecodeAndValidate(w, r, &in) {
		return
	}
	uid := platform.MustUserID(r.Context())

	session, err := h.svc.Upsert(r.Context(), uid, in)
	switch {
	case errors.Is(err, ErrSuperseded):
		// Not a failure. A stronger record for this night is already stored, and the client
		// should stop resending — 409 with an explicit code says exactly that.
		platform.Error(w, http.StatusConflict, platform.CodeConflict, "a stronger session is already stored for this night")
		return
	case errors.Is(err, ErrInvalidInput):
		platform.Error(w, http.StatusUnprocessableEntity, platform.CodeValidation, "invalid sleep session")
		return
	case err != nil:
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not save sleep session")
		return
	}

	// Best-effort side effects. Neither may fail the write the user actually asked for.
	if session.Kind == KindNight {
		if h.gamify != nil {
			_, _ = h.gamify.AwardOnce(r.Context(), uid, gamify.AwardCommand{
				Event:          gamify.EventTrackerEntry,
				IdempotencyKey: "sleep-session:" + session.ID.String(),
				LocalDate:      session.DateKey,
			})
		}
		h.mirrorToTracker(r, uid, session)
	}

	platform.JSON(w, http.StatusOK, toDTO(session))
}

// mirrorToTracker writes the lightweight tracker_entries row the Today card reads.
func (h *Handler) mirrorToTracker(r *http.Request, uid uuid.UUID, s Session) {
	if h.tracker == nil {
		return
	}
	payload := `{"source":"sleep","session_id":"` + s.ID.String() +
		`","asleep_minutes":` + strconv.Itoa(s.AsleepMinutes)
	if s.Score != nil {
		payload += `,"score":` + strconv.Itoa(int(*s.Score))
	}
	payload += `}`
	_, _ = h.tracker.Create(r.Context(), uid, "sleep", []byte(payload), s.DateKey)
}

func (h *Handler) upsertBatch(w http.ResponseWriter, r *http.Request) {
	var in BatchUpsertInput
	if !platform.DecodeAndValidate(w, r, &in) {
		return
	}
	results, err := h.svc.UpsertBatch(r.Context(), platform.MustUserID(r.Context()), in.Sessions)
	if err != nil {
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not import sessions")
		return
	}
	applied := 0
	for _, res := range results {
		if res.Status == "applied" {
			applied++
		}
	}
	platform.JSON(w, http.StatusOK, map[string]any{
		"results": results,
		"applied": applied,
		"total":   len(results),
	})
}

func (h *Handler) list(w http.ResponseWriter, r *http.Request) {
	f := filterFrom(r)
	sessions, err := h.svc.List(r.Context(), platform.MustUserID(r.Context()), f)
	if err != nil {
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not list sessions")
		return
	}
	out := map[string]any{"sessions": toDTOs(sessions)}
	// Keyset cursor: pass the last start_at back as ?before= for the next page.
	if len(sessions) == f.Limit && f.Limit > 0 {
		out["next_cursor"] = sessions[len(sessions)-1].StartAt.Format(time.RFC3339Nano)
	}
	platform.JSON(w, http.StatusOK, out)
}

func filterFrom(r *http.Request) Filter {
	q := r.URL.Query()
	f := Filter{Limit: 50}
	if v, err := time.Parse(DateLayout, q.Get("from")); err == nil {
		f.From = v
	}
	if v, err := time.Parse(DateLayout, q.Get("to")); err == nil {
		f.To = v
	}
	if k := Kind(q.Get("kind")); k.Valid() {
		f.Kind = k
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
	id, ok := sessionID(w, r)
	if !ok {
		return
	}
	session, err := h.svc.Get(r.Context(), platform.MustUserID(r.Context()), id)
	if err != nil {
		writeErr(w, err, "could not load session")
		return
	}
	platform.JSON(w, http.StatusOK, toDTO(session))
}

func (h *Handler) remove(w http.ResponseWriter, r *http.Request) {
	id, ok := sessionID(w, r)
	if !ok {
		return
	}
	if err := h.svc.Delete(r.Context(), platform.MustUserID(r.Context()), id); err != nil {
		writeErr(w, err, "could not delete session")
		return
	}
	platform.JSON(w, http.StatusNoContent, nil)
}

// changes is the pull half of offline sync: sessions and routine marks touched since a
// timestamp, deletions included.
func (h *Handler) changes(w http.ResponseWriter, r *http.Request) {
	since, err := time.Parse(time.RFC3339Nano, r.URL.Query().Get("since"))
	if err != nil {
		since = time.Unix(0, 0).UTC()
	}
	limit, _ := strconv.Atoi(r.URL.Query().Get("limit"))

	sessions, marks, err := h.svc.Changes(r.Context(), platform.MustUserID(r.Context()), since, limit)
	if err != nil {
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not load changes")
		return
	}

	// One cursor for both streams: the furthest point BOTH have been read to.
	cursor := since
	for _, s := range sessions {
		if s.UpdatedAt.After(cursor) {
			cursor = s.UpdatedAt
		}
	}
	for _, m := range marks {
		if m.UpdatedAt.After(cursor) {
			cursor = m.UpdatedAt
		}
	}

	markDTOs := make([]RoutineMarkDTO, 0, len(marks))
	for _, m := range marks {
		markDTOs = append(markDTOs, toMarkDTO(m))
	}

	platform.JSON(w, http.StatusOK, ChangesResponse{
		Sessions: toDTOs(sessions),
		Marks:    markDTOs,
		Cursor:   cursor.Format(time.RFC3339Nano),
		More:     (limit > 0 && (len(sessions) == limit || len(marks) == limit)),
	})
}

// ---- read models ----

func (h *Handler) stats(w http.ResponseWriter, r *http.Request) {
	to := time.Now().UTC().Truncate(24 * time.Hour)
	days := 30
	if n, err := strconv.Atoi(r.URL.Query().Get("days")); err == nil && n > 0 && n <= 365 {
		days = n
	}
	from := to.AddDate(0, 0, -days)

	st, err := h.svc.Stats(r.Context(), platform.MustUserID(r.Context()), from, to)
	if err != nil {
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not load stats")
		return
	}

	// Aggregates change at most once a night. A short private cache spares the database the
	// repeat hits from a user flipping between ranges, without ever serving one user's sleep
	// to another — hence private, never public.
	w.Header().Set("Cache-Control", "private, max-age=300")
	platform.JSON(w, http.StatusOK, st)
}

// ---- routine ----

func (h *Handler) routine(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	to, err := time.Parse(DateLayout, q.Get("to"))
	if err != nil {
		to = time.Now().UTC().Truncate(24 * time.Hour)
	}
	from, err := time.Parse(DateLayout, q.Get("from"))
	if err != nil {
		from = to.AddDate(0, 0, -30)
	}

	marks, err := h.svc.Routine(r.Context(), platform.MustUserID(r.Context()), from, to)
	if err != nil {
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not load routine")
		return
	}
	out := make([]RoutineMarkDTO, 0, len(marks))
	for _, m := range marks {
		out = append(out, toMarkDTO(m))
	}
	platform.JSON(w, http.StatusOK, map[string]any{"marks": out})
}

func (h *Handler) markRoutine(w http.ResponseWriter, r *http.Request) {
	var in RoutineMarkInput
	if !platform.DecodeAndValidate(w, r, &in) {
		return
	}
	uid := platform.MustUserID(r.Context())
	if err := h.svc.MarkRoutine(r.Context(), uid, in); err != nil {
		if errors.Is(err, ErrInvalidInput) {
			platform.Error(w, http.StatusUnprocessableEntity, platform.CodeValidation, "invalid routine mark")
			return
		}
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not record the step")
		return
	}
	if in.Completed && h.gamify != nil {
		if localDate, err := time.Parse(DateLayout, in.DateKey); err == nil {
			_, _ = h.gamify.AwardOnce(r.Context(), uid, gamify.AwardCommand{
				Event:          gamify.EventTrackerEntry,
				IdempotencyKey: "sleep-routine:" + in.MarkID,
				LocalDate:      localDate,
			})
		}
	}
	platform.JSON(w, http.StatusNoContent, nil)
}

// ---- settings ----

func (h *Handler) settings(w http.ResponseWriter, r *http.Request) {
	s, err := h.svc.Settings(r.Context(), platform.MustUserID(r.Context()))
	if err != nil {
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not load settings")
		return
	}
	platform.JSON(w, http.StatusOK, toSettingsDTO(s))
}

func (h *Handler) saveSettings(w http.ResponseWriter, r *http.Request) {
	var in SettingsInput
	if !platform.DecodeAndValidate(w, r, &in) {
		return
	}
	s, err := h.svc.SaveSettings(r.Context(), platform.MustUserID(r.Context()), in)
	if err != nil {
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not save settings")
		return
	}
	platform.JSON(w, http.StatusOK, toSettingsDTO(s))
}

// ---- helpers ----

func sessionID(w http.ResponseWriter, r *http.Request) (uuid.UUID, bool) {
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		platform.Error(w, http.StatusBadRequest, platform.CodeBadRequest, "invalid session id")
		return uuid.Nil, false
	}
	return id, true
}

func writeErr(w http.ResponseWriter, err error, msg string) {
	if errors.Is(err, ErrNotFound) {
		platform.Error(w, http.StatusNotFound, platform.CodeNotFound, "session not found")
		return
	}
	platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, msg)
}
