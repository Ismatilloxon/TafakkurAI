// Package tracker stores generic per-user tracker entries (prayer, journal,
// gratitude, quran, dhikr, sleep, sport, focus, productivity, finance, career)
// as jsonb payloads. Gamification is awarded by callers, not here, so the
// prayer module can award a prayer-specific event for the same write.
package tracker

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/tafakkur/backend/internal/gamify"
	"github.com/tafakkur/backend/internal/platform"
)

var ErrInvalidKind = errors.New("invalid tracker kind")

var validKinds = map[string]bool{
	"prayer": true, "journal": true, "gratitude": true, "quran": true,
	"dhikr": true, "sleep": true, "sport": true, "focus": true,
	"productivity": true, "finance": true, "career": true,
}

func ValidKind(k string) bool { return validKinds[k] }

type Entry struct {
	ID        uuid.UUID
	UserID    uuid.UUID
	Kind      string
	Payload   json.RawMessage
	EntryDate time.Time
	CreatedAt time.Time
}

// ---- repo ----

type Repo struct{ pool *pgxpool.Pool }

func NewRepo(pool *pgxpool.Pool) *Repo { return &Repo{pool: pool} }

func (r *Repo) Create(ctx context.Context, userID uuid.UUID, kind string, payload json.RawMessage, date time.Time) (Entry, error) {
	e := Entry{ID: uuid.New(), UserID: userID, Kind: kind, Payload: payload, EntryDate: date}
	err := r.pool.QueryRow(ctx, `
		INSERT INTO tracker_entries (id, user_id, kind, payload, entry_date)
		VALUES ($1, $2, $3, $4, $5)
		RETURNING created_at`,
		e.ID, userID, kind, payload, date,
	).Scan(&e.CreatedAt)
	return e, err
}

func (r *Repo) List(ctx context.Context, userID uuid.UUID, kind string, from, to time.Time) ([]Entry, error) {
	rows, err := r.pool.Query(ctx, `
		SELECT id, user_id, kind, payload, entry_date, created_at
		FROM tracker_entries
		WHERE user_id = $1 AND kind = $2 AND entry_date BETWEEN $3 AND $4
		ORDER BY entry_date DESC, created_at DESC`,
		userID, kind, from, to)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var out []Entry
	for rows.Next() {
		var e Entry
		if err := rows.Scan(&e.ID, &e.UserID, &e.Kind, &e.Payload, &e.EntryDate, &e.CreatedAt); err != nil {
			return nil, err
		}
		out = append(out, e)
	}
	return out, rows.Err()
}

// ---- service ----

type Service interface {
	Create(ctx context.Context, userID uuid.UUID, kind string, payload json.RawMessage, date time.Time) (Entry, error)
	List(ctx context.Context, userID uuid.UUID, kind string, from, to time.Time) ([]Entry, error)
}

type service struct{ repo *Repo }

func NewService(repo *Repo) Service { return &service{repo: repo} }

func (s *service) Create(ctx context.Context, userID uuid.UUID, kind string, payload json.RawMessage, date time.Time) (Entry, error) {
	if !ValidKind(kind) {
		return Entry{}, ErrInvalidKind
	}
	if date.IsZero() {
		date = time.Now().UTC()
	}
	return s.repo.Create(ctx, userID, kind, payload, date)
}

func (s *service) List(ctx context.Context, userID uuid.UUID, kind string, from, to time.Time) ([]Entry, error) {
	if !ValidKind(kind) {
		return nil, ErrInvalidKind
	}
	return s.repo.List(ctx, userID, kind, from, to)
}

// ---- DTOs & handler ----

const dateLayout = "2006-01-02"

type CreateEntryInput struct {
	EntryDate *string         `json:"entry_date" validate:"omitempty,datetime=2006-01-02"`
	Payload   json.RawMessage `json:"payload" validate:"required"`
}

type EntryDTO struct {
	ID        string          `json:"id"`
	Kind      string          `json:"kind"`
	Payload   json.RawMessage `json:"payload"`
	EntryDate string          `json:"entry_date"`
	CreatedAt string          `json:"created_at"`
}

func toDTO(e Entry) EntryDTO {
	return EntryDTO{
		ID:        e.ID.String(),
		Kind:      e.Kind,
		Payload:   e.Payload,
		EntryDate: e.EntryDate.Format(dateLayout),
		CreatedAt: e.CreatedAt.Format(time.RFC3339),
	}
}

type Handler struct {
	svc    Service
	gamify gamify.Service
}

func NewHandler(svc Service, g gamify.Service) *Handler {
	return &Handler{svc: svc, gamify: g}
}

func (h *Handler) Register(r chi.Router) {
	r.Post("/trackers/{kind}", h.create)
	r.Get("/trackers/{kind}", h.list)
}

func (h *Handler) create(w http.ResponseWriter, r *http.Request) {
	kind := chi.URLParam(r, "kind")
	if !ValidKind(kind) {
		platform.Error(w, http.StatusNotFound, platform.CodeNotFound, "unknown tracker kind")
		return
	}
	var in CreateEntryInput
	if !platform.DecodeAndValidate(w, r, &in) {
		return
	}
	var date time.Time
	if in.EntryDate != nil {
		date, _ = time.Parse(dateLayout, *in.EntryDate)
	}
	uid := platform.MustUserID(r.Context())
	e, err := h.svc.Create(r.Context(), uid, kind, in.Payload, date)
	if err != nil {
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not save entry")
		return
	}
	// Award XP for logging any tracker entry (prayer has its own richer flow).
	if kind != "prayer" {
		_, _ = h.gamify.Award(r.Context(), uid, gamify.EventTrackerEntry)
	}
	platform.JSON(w, http.StatusCreated, toDTO(e))
}

func (h *Handler) list(w http.ResponseWriter, r *http.Request) {
	kind := chi.URLParam(r, "kind")
	if !ValidKind(kind) {
		platform.Error(w, http.StatusNotFound, platform.CodeNotFound, "unknown tracker kind")
		return
	}
	from := parseDateOr(r.URL.Query().Get("from"), time.Now().AddDate(0, 0, -30))
	to := parseDateOr(r.URL.Query().Get("to"), time.Now())

	uid := platform.MustUserID(r.Context())
	entries, err := h.svc.List(r.Context(), uid, kind, from, to)
	if err != nil {
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not list entries")
		return
	}
	out := make([]EntryDTO, 0, len(entries))
	for _, e := range entries {
		out = append(out, toDTO(e))
	}
	platform.JSON(w, http.StatusOK, map[string]any{"entries": out})
}

func parseDateOr(s string, fallback time.Time) time.Time {
	if s == "" {
		return fallback
	}
	if t, err := time.Parse(dateLayout, s); err == nil {
		return t
	}
	return fallback
}
