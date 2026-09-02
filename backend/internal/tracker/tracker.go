// Package tracker stores generic per-user tracker entries (prayer, journal,
// gratitude, quran, dhikr, sleep, sport, focus, productivity, finance, career)
// as jsonb payloads. Gamification is awarded by callers, not here, so the
// prayer module can award a prayer-specific event for the same write.
package tracker

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strings"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/tafakkur/backend/internal/gamify"
	"github.com/tafakkur/backend/internal/platform"
)

var (
	ErrInvalidKind         = errors.New("invalid tracker kind")
	ErrInvalidIdempotency  = errors.New("invalid tracker idempotency key")
	ErrIdempotencyConflict = errors.New("tracker idempotency key reused with different content")
)

const maxIdempotencyKeyBytes = 200

var validKinds = map[string]bool{
	"prayer": true, "journal": true, "gratitude": true, "quran": true,
	"dhikr": true, "sleep": true, "sport": true, "focus": true,
	"productivity": true, "finance": true, "career": true, "habit": true,
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

func (r *Repo) begin(ctx context.Context) (pgx.Tx, error) {
	return r.pool.BeginTx(ctx, pgx.TxOptions{})
}

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

// createTx inserts one REST tracker command inside the caller's transaction. A stable
// idempotency key maps to a stable, user-and-kind-scoped UUID without requiring a second
// mutable identity column. On replay the original semantic result is returned; reusing
// the key with different content is rejected instead of silently overwriting history.
func (r *Repo) createTx(
	ctx context.Context,
	tx pgx.Tx,
	userID uuid.UUID,
	kind string,
	payload json.RawMessage,
	date time.Time,
	idempotencyKey string,
) (Entry, error) {
	id := uuid.New()
	if idempotencyKey != "" {
		name := "tafakkur.ai/tracker/" + userID.String() + "/" + kind + "/" + idempotencyKey
		id = uuid.NewSHA1(uuid.NameSpaceURL, []byte(name))
	}

	e := Entry{ID: id, UserID: userID, Kind: kind, Payload: payload, EntryDate: date}
	err := tx.QueryRow(ctx, `
		INSERT INTO tracker_entries (id, user_id, kind, payload, entry_date)
		VALUES ($1, $2, $3, $4, $5)
		ON CONFLICT (id) DO NOTHING
		RETURNING created_at`,
		e.ID, e.UserID, e.Kind, e.Payload, e.EntryDate,
	).Scan(&e.CreatedAt)
	if err == nil {
		return e, nil
	}
	if !errors.Is(err, pgx.ErrNoRows) {
		return Entry{}, fmt.Errorf("insert tracker entry: %w", err)
	}

	// This is a separate statement on purpose. Under READ COMMITTED it gets a fresh
	// snapshot after a concurrent INSERT ... ON CONFLICT has finished waiting.
	err = tx.QueryRow(ctx, `
		SELECT id, user_id, kind, payload, entry_date, created_at
		FROM tracker_entries
		WHERE id = $1
		  AND user_id = $2
		  AND kind = $3
		  AND payload = $4::jsonb
		  AND entry_date = $5`,
		e.ID, e.UserID, e.Kind, e.Payload, e.EntryDate,
	).Scan(&e.ID, &e.UserID, &e.Kind, &e.Payload, &e.EntryDate, &e.CreatedAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return Entry{}, ErrIdempotencyConflict
	}
	if err != nil {
		return Entry{}, fmt.Errorf("load replayed tracker entry: %w", err)
	}
	return e, nil
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

// CreateTx is the transaction-capable projection seam used by feature workflows such
// as habits. It is intentionally additive to Service so existing consumers and fakes do
// not gain transaction ownership accidentally.
func (s *service) CreateTx(
	ctx context.Context,
	tx pgx.Tx,
	userID uuid.UUID,
	kind string,
	payload json.RawMessage,
	date time.Time,
	idempotencyKey string,
) (Entry, error) {
	if tx == nil {
		return Entry{}, errors.New("tracker transaction is required")
	}
	if !ValidKind(kind) {
		return Entry{}, ErrInvalidKind
	}
	if err := validateIdempotencyKey(idempotencyKey); err != nil {
		return Entry{}, err
	}
	if date.IsZero() {
		date = time.Now().UTC()
	}
	return s.repo.createTx(ctx, tx, userID, kind, payload, date, idempotencyKey)
}

type transactionalGamifier interface {
	AwardBatchTx(
		ctx context.Context,
		tx pgx.Tx,
		userID uuid.UUID,
		commands []gamify.AwardCommand,
	) (gamify.State, error)
}

// createAtomic is the REST command path. The tracker source row and its progression
// ledger/aggregate share one transaction, so a retry can repair neither half in
// isolation and an award failure cannot leave a committed tracker row behind.
func (s *service) createAtomic(
	ctx context.Context,
	userID uuid.UUID,
	kind string,
	payload json.RawMessage,
	date time.Time,
	idempotencyKey string,
	progression transactionalGamifier,
) (Entry, error) {
	if progression == nil {
		return Entry{}, errors.New("transactional gamification is unavailable")
	}
	tx, err := s.repo.begin(ctx)
	if err != nil {
		return Entry{}, fmt.Errorf("begin tracker command: %w", err)
	}
	defer func() { _ = tx.Rollback(ctx) }()

	e, err := s.CreateTx(ctx, tx, userID, kind, payload, date, idempotencyKey)
	if err != nil {
		return Entry{}, err
	}
	if _, err := progression.AwardBatchTx(ctx, tx, userID, []gamify.AwardCommand{{
		Event:          gamify.EventTrackerEntry,
		IdempotencyKey: "tracker:" + e.ID.String(),
		LocalDate:      e.EntryDate,
	}}); err != nil {
		return Entry{}, fmt.Errorf("award tracker progression: %w", err)
	}
	if err := tx.Commit(ctx); err != nil {
		return Entry{}, fmt.Errorf("commit tracker command: %w", err)
	}
	return e, nil
}

func validateIdempotencyKey(key string) error {
	if len(key) > maxIdempotencyKeyBytes {
		return fmt.Errorf("%w: exceeds %d bytes", ErrInvalidIdempotency, maxIdempotencyKeyBytes)
	}
	if strings.TrimSpace(key) != key {
		return fmt.Errorf("%w: surrounding whitespace", ErrInvalidIdempotency)
	}
	return nil
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
	EntryDate      *string         `json:"entry_date" validate:"omitempty,datetime=2006-01-02"`
	Payload        json.RawMessage `json:"payload" validate:"required"`
	IdempotencyKey string          `json:"idempotency_key,omitempty" validate:"omitempty,max=200"`
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
	var e Entry
	var err error
	if kind == "prayer" {
		// Prayer owns its richer source/progression workflow. Preserve this legacy mirror
		// path rather than awarding the generic tracker event as well.
		e, err = h.svc.Create(r.Context(), uid, kind, in.Payload, date)
	} else {
		atomicService, serviceOK := h.svc.(interface {
			createAtomic(
				context.Context,
				uuid.UUID,
				string,
				json.RawMessage,
				time.Time,
				string,
				transactionalGamifier,
			) (Entry, error)
		})
		atomicGamify, gamifyOK := h.gamify.(transactionalGamifier)
		if !serviceOK || !gamifyOK {
			platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "transactional tracker write is unavailable")
			return
		}
		e, err = atomicService.createAtomic(
			r.Context(), uid, kind, in.Payload, date, in.IdempotencyKey, atomicGamify,
		)
	}
	if err != nil {
		if errors.Is(err, ErrInvalidIdempotency) {
			platform.Error(w, http.StatusUnprocessableEntity, platform.CodeValidation, "invalid idempotency key")
			return
		}
		if errors.Is(err, ErrIdempotencyConflict) {
			platform.Error(w, http.StatusConflict, platform.CodeConflict, "idempotency key was already used by different content")
			return
		}
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not save entry")
		return
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
