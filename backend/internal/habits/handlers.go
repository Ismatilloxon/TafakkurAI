package habits

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"

	"github.com/tafakkur/backend/internal/gamify"
	"github.com/tafakkur/backend/internal/platform"
	"github.com/tafakkur/backend/internal/tracker"
)

// Handler exposes Habits over HTTP.
//
// Note what is NOT here: a `POST /v1/habits/sync` op-log endpoint. The offline queue the
// spec calls for is `POST /v1/sync/batch` — the generic platform every feature already
// uses. A second, habits-shaped sync endpoint would be a second write path with its own
// idempotency, its own conflict rules and its own bugs, for a queue that already works.
//
// The endpoints below therefore serve two audiences: the read models the phone cannot
// compute cheaply for a whole year of history, and a complete write path for clients that
// are not the phone (the Telegram bot, the web app, a future reminder scheduler).
type Handler struct {
	svc     Service
	gamify  gamify.Service
	tracker tracker.Service
}

type transactionalGamifier interface {
	AwardBatchTx(
		context.Context,
		pgx.Tx,
		uuid.UUID,
		[]gamify.AwardCommand,
	) (gamify.State, error)
}

type transactionalTracker interface {
	CreateTx(
		context.Context,
		pgx.Tx,
		uuid.UUID,
		string,
		json.RawMessage,
		time.Time,
		string,
	) (tracker.Entry, error)
}

type atomicEntryService interface {
	recordEntryAtomic(
		context.Context,
		uuid.UUID,
		EntryInput,
		entryProjector,
	) (EntryResult, error)
}

func NewHandler(svc Service, g gamify.Service, t tracker.Service) *Handler {
	return &Handler{svc: svc, gamify: g, tracker: t}
}

func (h *Handler) Register(r chi.Router) {
	r.Route("/habits", func(r chi.Router) {
		// Catalogue. Static content, served so copy and the template list can change
		// without an app release.
		r.Get("/catalog", h.catalog)

		r.Get("/dashboard", h.dashboard)
		r.Get("/statistics", h.statistics)
		r.Get("/calendar", h.calendar)
		r.Get("/achievements", h.achievements)

		r.Post("/", h.create)
		r.Post("/reorder", h.reorder)

		r.Route("/{id}", func(r chi.Router) {
			r.Get("/", h.detail)
			r.Patch("/", h.update)
			r.Delete("/", h.remove)

			r.Post("/archive", h.archive)
			r.Post("/pause", h.pause)
			r.Post("/resume", h.resume)

			r.Post("/entries", h.entries)
			r.Post("/notes", h.addNote)
			r.Delete("/notes/{noteId}", h.deleteNote)
		})
	})
}

// ---- reads ----

func (h *Handler) catalog(w http.ResponseWriter, r *http.Request) {
	platform.JSON(w, http.StatusOK, map[string]any{
		"categories":   Categories,
		"templates":    Templates,
		"achievements": Achievements,
		"slots":        SlotOrder,
		"difficulties": []string{DifficultyEasy, DifficultyMedium, DifficultyHard},
		"goalTypes":    []string{GoalCheck, GoalCount, GoalDuration},
		"frequencies":  []string{FrequencyDaily, FrequencyWeekdays, FrequencyCustom},
	})
}

// today reads the caller's local day. The client always sends it; an absent or malformed
// value falls back to the server's UTC day inside the service rather than 400ing, because
// a dashboard is more useful slightly wrong than not at all.
func today(r *http.Request) string { return r.URL.Query().Get("date") }

func (h *Handler) dashboard(w http.ResponseWriter, r *http.Request) {
	d, err := h.svc.Dashboard(r.Context(), platform.MustUserID(r.Context()), today(r))
	if err != nil {
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not load habits")
		return
	}
	platform.JSON(w, http.StatusOK, d)
}

func (h *Handler) detail(w http.ResponseWriter, r *http.Request) {
	d, err := h.svc.Detail(r.Context(), platform.MustUserID(r.Context()), chi.URLParam(r, "id"), today(r))
	if writeErr(w, err, "could not load habit") {
		return
	}
	platform.JSON(w, http.StatusOK, d)
}

func (h *Handler) statistics(w http.ResponseWriter, r *http.Request) {
	s, err := h.svc.Statistics(r.Context(), platform.MustUserID(r.Context()), today(r))
	if err != nil {
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not load statistics")
		return
	}
	platform.JSON(w, http.StatusOK, s)
}

func (h *Handler) calendar(w http.ResponseWriter, r *http.Request) {
	c, err := h.svc.Calendar(r.Context(), platform.MustUserID(r.Context()), r.URL.Query().Get("month"), today(r))
	if err != nil {
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not load calendar")
		return
	}
	platform.JSON(w, http.StatusOK, c)
}

func (h *Handler) achievements(w http.ResponseWriter, r *http.Request) {
	b, err := h.svc.Board(r.Context(), platform.MustUserID(r.Context()), today(r))
	if err != nil {
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not load achievements")
		return
	}
	platform.JSON(w, http.StatusOK, b)
}

// ---- habit writes ----

// HabitBody is the create/update wire format. Every field is a pointer so PATCH can mean
// "change only this" — the difference between the reminder sheet saving a time and the
// reminder sheet silently reverting the name the edit screen changed a second earlier.
type HabitBody struct {
	ID          string  `json:"id"`
	Name        *string `json:"name"`
	Description *string `json:"description"`
	CategoryID  *string `json:"categoryId"`
	ColorKey    *string `json:"colorKey"`
	Icon        *string `json:"icon"`
	Slot        *string `json:"slot"`
	TimeLocal   *string `json:"timeLocal"`
	Reminder    *bool   `json:"reminderEnabled"`
	ReminderAt  *string `json:"reminderTime"`
	SmartTiming *bool   `json:"smartTiming"`
	Frequency   *string `json:"frequency"`
	Weekdays    *[]int  `json:"weekdays"`
	Difficulty  *string `json:"difficulty"`
	GoalType    *string `json:"goalType"`
	GoalTarget  *int    `json:"goalTarget"`
	GoalUnit    *string `json:"goalUnit"`
	Motivation  *string `json:"motivation"`
	SortIndex   *int    `json:"sortIndex"`
}

func (b HabitBody) input(id string) HabitInput {
	return HabitInput{
		ID: id, Name: b.Name, Description: b.Description, CategoryID: b.CategoryID,
		ColorKey: b.ColorKey, Icon: b.Icon, Slot: b.Slot, TimeLocal: b.TimeLocal,
		Reminder: b.Reminder, ReminderAt: b.ReminderAt, SmartTiming: b.SmartTiming,
		Frequency: b.Frequency, Weekdays: b.Weekdays, Difficulty: b.Difficulty,
		GoalType: b.GoalType, GoalTarget: b.GoalTarget, GoalUnit: b.GoalUnit,
		Motivation: b.Motivation, SortIndex: b.SortIndex,
	}
}

func (h *Handler) create(w http.ResponseWriter, r *http.Request) {
	var in HabitBody
	if !platform.DecodeAndValidate(w, r, &in) {
		return
	}
	// The id is client-generated on purpose: the phone must be able to create a habit
	// offline, navigate straight to it and start completing it without waiting for an
	// identifier from us. A server-minted id would make the offline case impossible.
	id := strings.TrimSpace(in.ID)
	if id == "" {
		id = uuid.NewString()
	}
	v, err := h.svc.Save(r.Context(), platform.MustUserID(r.Context()), in.input(id))
	if writeErr(w, err, "could not save habit") {
		return
	}
	platform.JSON(w, http.StatusCreated, v)
}

func (h *Handler) update(w http.ResponseWriter, r *http.Request) {
	var in HabitBody
	if !platform.DecodeAndValidate(w, r, &in) {
		return
	}
	v, err := h.svc.Save(r.Context(), platform.MustUserID(r.Context()), in.input(chi.URLParam(r, "id")))
	if writeErr(w, err, "could not save habit") {
		return
	}
	platform.JSON(w, http.StatusOK, v)
}

func (h *Handler) remove(w http.ResponseWriter, r *http.Request) {
	err := h.svc.Delete(r.Context(), platform.MustUserID(r.Context()), chi.URLParam(r, "id"))
	if writeErr(w, err, "could not delete habit") {
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (h *Handler) archive(w http.ResponseWriter, r *http.Request) {
	h.setState(w, r, StateArchived, "")
}

// PauseBody carries how long the pause lasts. An empty `until` means "until I resume",
// which is the option the Pause sheet defaults to.
type PauseBody struct {
	Until string `json:"until"`
}

func (h *Handler) pause(w http.ResponseWriter, r *http.Request) {
	var in PauseBody
	// A bodyless POST is a valid "pause indefinitely", so a decode failure is not fatal.
	if r.ContentLength > 0 && !platform.DecodeAndValidate(w, r, &in) {
		return
	}
	h.setState(w, r, StatePaused, in.Until)
}

func (h *Handler) resume(w http.ResponseWriter, r *http.Request) {
	h.setState(w, r, StateActive, "")
}

func (h *Handler) setState(w http.ResponseWriter, r *http.Request, state, until string) {
	v, err := h.svc.SetState(r.Context(), platform.MustUserID(r.Context()), chi.URLParam(r, "id"), state, until)
	if writeErr(w, err, "could not update habit") {
		return
	}
	platform.JSON(w, http.StatusOK, v)
}

type ReorderBody struct {
	IDs []string `json:"ids" validate:"required,min=1,max=200"`
}

func (h *Handler) reorder(w http.ResponseWriter, r *http.Request) {
	var in ReorderBody
	if !platform.DecodeAndValidate(w, r, &in) {
		return
	}
	if err := h.svc.Reorder(r.Context(), platform.MustUserID(r.Context()), in.IDs); err != nil {
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not reorder habits")
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// ---- entries ----

// EntryBody is one op against (habit, day).
//
// `clientId` is accepted and deliberately ignored for idempotency: the row id is derived
// from (habitId, date), which is a stronger guarantee than a client-supplied key. Two
// different client ids for the same day would each create a row under a clientId scheme;
// under this one they collapse, which is the behaviour the user expects when they tap
// twice on a flaky connection.
type EntryBody struct {
	Date     string `json:"date"`
	Op       string `json:"op" validate:"required,oneof=complete undo skip progress"`
	Value    int    `json:"value"`
	Reason   string `json:"reason"`
	ClientID string `json:"clientId"`
}

func (h *Handler) entries(w http.ResponseWriter, r *http.Request) {
	var in EntryBody
	if !platform.DecodeAndValidate(w, r, &in) {
		return
	}
	uid := platform.MustUserID(r.Context())
	atomicService, serviceOK := h.svc.(atomicEntryService)
	atomicGamify, gamifyOK := h.gamify.(transactionalGamifier)
	atomicTracker, trackerOK := h.tracker.(transactionalTracker)
	if !serviceOK || !gamifyOK || !trackerOK {
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "transactional habit entry write is unavailable")
		return
	}

	res, err := atomicService.recordEntryAtomic(r.Context(), uid, EntryInput{
		HabitID: chi.URLParam(r, "id"),
		DateKey: in.Date,
		Op:      in.Op,
		Value:   in.Value,
		Reason:  in.Reason,
	}, func(ctx context.Context, tx pgx.Tx, result EntryResult) error {
		if result.Entry == nil || result.Entry.Status != StatusDone {
			return nil
		}
		entry := *result.Entry
		localDate, ok := ParseDay(entry.DateKey)
		if !ok {
			return fmt.Errorf("project habit entry %q: invalid local date", entry.ID)
		}
		payload := json.RawMessage(`{"source":"habits","habit_id":` + strconv.Quote(entry.HabitID) +
			`,"entry_id":` + strconv.Quote(entry.ID) +
			`,"value":` + strconv.Itoa(entry.Value) + `}`)
		if _, err := atomicTracker.CreateTx(
			ctx,
			tx,
			uid,
			"habit",
			payload,
			localDate,
			"habit-entry:"+entry.ID,
		); err != nil {
			return fmt.Errorf("mirror habit entry to tracker: %w", err)
		}
		if _, err := atomicGamify.AwardBatchTx(ctx, tx, uid, []gamify.AwardCommand{{
			Event:          gamify.EventTrackerEntry,
			IdempotencyKey: "habit-entry:" + entry.ID,
			LocalDate:      localDate,
		}}); err != nil {
			return fmt.Errorf("award habit entry progression: %w", err)
		}
		return nil
	})
	if writeErr(w, err, "could not record habit entry") {
		return
	}
	platform.JSON(w, http.StatusOK, res)
}

// ---- notes ----

type NoteBody struct {
	Date string `json:"date"`
	Text string `json:"text" validate:"required,min=1,max=2000"`
}

func (h *Handler) addNote(w http.ResponseWriter, r *http.Request) {
	var in NoteBody
	if !platform.DecodeAndValidate(w, r, &in) {
		return
	}
	n, err := h.svc.AddNote(r.Context(), platform.MustUserID(r.Context()), chi.URLParam(r, "id"), in.Date, in.Text)
	if writeErr(w, err, "could not save note") {
		return
	}
	platform.JSON(w, http.StatusCreated, n)
}

func (h *Handler) deleteNote(w http.ResponseWriter, r *http.Request) {
	err := h.svc.DeleteNote(r.Context(), platform.MustUserID(r.Context()), chi.URLParam(r, "noteId"))
	if writeErr(w, err, "could not delete note") {
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// writeErr maps the two domain errors onto status codes and reports whether the response
// has already been written.
func writeErr(w http.ResponseWriter, err error, msg string) bool {
	switch {
	case err == nil:
		return false
	case errors.Is(err, ErrNotFound):
		platform.Error(w, http.StatusNotFound, platform.CodeNotFound, "habit not found")
	case errors.Is(err, ErrInvalidInput):
		platform.Error(w, http.StatusUnprocessableEntity, platform.CodeValidation, "habit name is required")
	default:
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, msg)
	}
	return true
}
