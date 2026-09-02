// Package quran serves one user's memorization schedule. It is deliberately separate
// from package learn: learn carries the shared curriculum, this carries per-user state.
//
// The client is the source of truth here. FSRS runs on-device so a session works with no
// connection, and grades arrive afterwards from the client's outbox — possibly a whole
// session at once, possibly days late, always at-least-once. Every write is therefore an
// idempotent upsert keyed on (user, surah, ayah), and a replayed grade lands on the same
// row rather than creating a second one.
//
// No ayah text passes through this package. A reference is the whole record.
package quran

import (
	"context"
	"errors"
	"net/http"
	"strconv"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/tafakkur/backend/internal/gamify"
	"github.com/tafakkur/backend/internal/platform"
)

// Canonical Hafs ʻan ʻĀsim ayah counts, indexed by surah number. Structural data, not
// scripture — it is what makes an out-of-range reference rejectable rather than stored.
var ayahCounts = [115]int{
	0,
	7, 286, 200, 176, 120, 165, 206, 75, 129, 109,
	123, 111, 43, 52, 99, 128, 111, 110, 98, 135,
	112, 78, 118, 64, 77, 227, 93, 88, 69, 60,
	34, 30, 73, 54, 45, 83, 182, 88, 75, 85,
	54, 53, 89, 59, 37, 35, 38, 29, 18, 45,
	60, 49, 62, 55, 78, 96, 29, 22, 24, 13,
	14, 11, 11, 18, 12, 12, 30, 52, 52, 44,
	28, 28, 20, 56, 40, 31, 50, 40, 46, 42,
	29, 19, 36, 25, 22, 17, 19, 26, 30, 20,
	15, 21, 11, 8, 8, 19, 5, 8, 8, 11,
	11, 8, 3, 9, 5, 4, 7, 3, 6, 3,
	5, 4, 5, 6,
}

// ValidRef reports whether a surah/ayah pair exists in the mushaf.
func ValidRef(surah, ayah int) bool {
	if surah < 1 || surah > 114 {
		return false
	}
	return ayah >= 1 && ayah <= ayahCounts[surah]
}

type Card struct {
	Surah      int
	Ayah       int
	State      int
	Stability  float64
	Difficulty float64
	Due        time.Time
	LastReview *time.Time
	Reps       int
	Lapses     int
	LastRating *int
}

type Plan struct {
	DailyNew  int
	ReviewCap int
}

// Grade is one graded ayah as it arrives from the client's outbox.
type Grade struct {
	Surah      int
	Ayah       int
	Rating     int
	ReviewedAt time.Time
	// Schedule the device computed. Stored as-is: recomputing it here would risk two
	// different answers for the same card, and the device's is the one the user sees.
	State      int
	Stability  float64
	Difficulty float64
	Due        time.Time
}

var ErrInvalidRef = errors.New("invalid ayah reference")

// ---- repo ----

type Repo struct{ pool *pgxpool.Pool }

func NewRepo(pool *pgxpool.Pool) *Repo { return &Repo{pool: pool} }

func (r *Repo) ListCards(ctx context.Context, userID uuid.UUID) ([]Card, error) {
	rows, err := r.pool.Query(ctx,
		`SELECT surah, ayah, state, stability, difficulty, due, last_review, reps, lapses, last_rating
		   FROM hifz_cards WHERE user_id = $1 ORDER BY surah, ayah`, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []Card
	for rows.Next() {
		var c Card
		if err := rows.Scan(&c.Surah, &c.Ayah, &c.State, &c.Stability, &c.Difficulty,
			&c.Due, &c.LastReview, &c.Reps, &c.Lapses, &c.LastRating); err != nil {
			return nil, err
		}
		out = append(out, c)
	}
	return out, rows.Err()
}

// UpsertGrade records a review. Replaying the same op is safe: it lands on the same row and
// leaves it in the same state, apart from reps, which is guarded by last_review so a retry
// of an already-applied grade does not inflate the count.
func (r *Repo) UpsertGrade(ctx context.Context, userID uuid.UUID, g Grade) error {
	lapse := 0
	if g.Rating == ratingAgain {
		lapse = 1
	}
	_, err := r.pool.Exec(ctx,
		`INSERT INTO hifz_cards
		     (user_id, surah, ayah, state, stability, difficulty, due, last_review,
		      reps, lapses, last_rating, updated_at)
		 VALUES ($1, $2, $3, $4, $5, $6, $7, $8, 1, $9, $10, now())
		 ON CONFLICT (user_id, surah, ayah) DO UPDATE SET
		     state       = EXCLUDED.state,
		     stability   = EXCLUDED.stability,
		     difficulty  = EXCLUDED.difficulty,
		     due         = EXCLUDED.due,
		     last_review = EXCLUDED.last_review,
		     reps        = hifz_cards.reps +
		         CASE WHEN hifz_cards.last_review IS DISTINCT FROM EXCLUDED.last_review THEN 1 ELSE 0 END,
		     lapses      = hifz_cards.lapses +
		         CASE WHEN hifz_cards.last_review IS DISTINCT FROM EXCLUDED.last_review THEN $9 ELSE 0 END,
		     last_rating = EXCLUDED.last_rating,
		     updated_at  = now()`,
		userID, g.Surah, g.Ayah, g.State, g.Stability, g.Difficulty, g.Due, g.ReviewedAt,
		lapse, g.Rating)
	return err
}

func (r *Repo) GetPlan(ctx context.Context, userID uuid.UUID) (Plan, error) {
	var p Plan
	err := r.pool.QueryRow(ctx,
		`SELECT daily_new, review_cap FROM hifz_plans WHERE user_id = $1`, userID,
	).Scan(&p.DailyNew, &p.ReviewCap)
	if errors.Is(err, pgx.ErrNoRows) {
		return Plan{DailyNew: defaultDailyNew, ReviewCap: defaultReviewCap}, nil
	}
	return p, err
}

func (r *Repo) SavePlan(ctx context.Context, userID uuid.UUID, p Plan) error {
	_, err := r.pool.Exec(ctx,
		`INSERT INTO hifz_plans (user_id, daily_new, review_cap, updated_at)
		 VALUES ($1, $2, $3, now())
		 ON CONFLICT (user_id) DO UPDATE SET
		     daily_new = EXCLUDED.daily_new,
		     review_cap = EXCLUDED.review_cap,
		     updated_at = now()`,
		userID, p.DailyNew, p.ReviewCap)
	return err
}

// ---- service ----

type Service interface {
	Cards(ctx context.Context, userID uuid.UUID) ([]Card, error)
	Grade(ctx context.Context, userID uuid.UUID, g Grade) error
	Plan(ctx context.Context, userID uuid.UUID) (Plan, error)
	SavePlan(ctx context.Context, userID uuid.UUID, p Plan) (Plan, error)
}

type service struct{ repo *Repo }

func NewService(repo *Repo) Service { return &service{repo: repo} }

func (s *service) Cards(ctx context.Context, userID uuid.UUID) ([]Card, error) {
	return s.repo.ListCards(ctx, userID)
}

func (s *service) Grade(ctx context.Context, userID uuid.UUID, g Grade) error {
	if !ValidRef(g.Surah, g.Ayah) {
		return ErrInvalidRef
	}
	return s.repo.UpsertGrade(ctx, userID, g)
}

func (s *service) Plan(ctx context.Context, userID uuid.UUID) (Plan, error) {
	return s.repo.GetPlan(ctx, userID)
}

func (s *service) SavePlan(ctx context.Context, userID uuid.UUID, p Plan) (Plan, error) {
	if err := s.repo.SavePlan(ctx, userID, p); err != nil {
		return Plan{}, err
	}
	return p, nil
}

// ---- DTOs ----

type CardDTO struct {
	Surah      int     `json:"surah"`
	Ayah       int     `json:"ayah"`
	State      int     `json:"state"`
	Stability  float64 `json:"stability"`
	Difficulty float64 `json:"difficulty"`
	Due        int64   `json:"due"`
	LastReview *int64  `json:"last_review"`
	Reps       int     `json:"reps"`
	Lapses     int     `json:"lapses"`
}

type CardsResponseDTO struct {
	Cards []CardDTO `json:"cards"`
}

type GradeRequestDTO struct {
	Surah      int     `json:"surah" validate:"required,min=1,max=114"`
	Ayah       int     `json:"ayah" validate:"required,min=1"`
	Rating     int     `json:"rating" validate:"required,min=1,max=4"`
	ReviewedAt int64   `json:"reviewed_at" validate:"required"`
	State      int     `json:"state" validate:"min=0,max=3"`
	Stability  float64 `json:"stability" validate:"min=0"`
	Difficulty float64 `json:"difficulty" validate:"min=0"`
	Due        int64   `json:"due"`
}

type PlanDTO struct {
	DailyNew  int `json:"daily_new" validate:"required,min=1,max=20"`
	ReviewCap int `json:"review_cap" validate:"required,min=5,max=200"`
}

func cardDTO(c Card) CardDTO {
	dto := CardDTO{
		Surah: c.Surah, Ayah: c.Ayah, State: c.State,
		Stability: c.Stability, Difficulty: c.Difficulty,
		Due:  c.Due.UnixMilli(),
		Reps: c.Reps, Lapses: c.Lapses,
	}
	if c.LastReview != nil {
		ms := c.LastReview.UnixMilli()
		dto.LastReview = &ms
	}
	return dto
}

// ---- handler ----

type Handler struct {
	svc    Service
	gamify gamify.Service
}

func NewHandler(svc Service, g gamify.Service) *Handler { return &Handler{svc: svc, gamify: g} }

func (h *Handler) Register(r chi.Router) {
	r.Get("/quran/hifz", h.cards)
	r.Post("/quran/hifz/grade", h.grade)
	r.Put("/quran/hifz/plan", h.savePlan)
}

func (h *Handler) cards(w http.ResponseWriter, r *http.Request) {
	cards, err := h.svc.Cards(r.Context(), platform.MustUserID(r.Context()))
	if err != nil {
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not load hifz cards")
		return
	}
	out := make([]CardDTO, 0, len(cards))
	for _, c := range cards {
		out = append(out, cardDTO(c))
	}
	platform.JSON(w, http.StatusOK, CardsResponseDTO{Cards: out})
}

func (h *Handler) grade(w http.ResponseWriter, r *http.Request) {
	var req GradeRequestDTO
	if !platform.DecodeAndValidate(w, r, &req) {
		return
	}
	if !ValidRef(req.Surah, req.Ayah) {
		platform.Error(w, http.StatusBadRequest, platform.CodeBadRequest, "no such ayah")
		return
	}
	userID := platform.MustUserID(r.Context())
	g := Grade{
		Surah:      req.Surah,
		Ayah:       req.Ayah,
		Rating:     req.Rating,
		ReviewedAt: time.UnixMilli(req.ReviewedAt).UTC(),
		State:      req.State,
		Stability:  req.Stability,
		Difficulty: req.Difficulty,
		Due:        time.UnixMilli(req.Due).UTC(),
	}
	if err := h.svc.Grade(r.Context(), userID, g); err != nil {
		if errors.Is(err, ErrInvalidRef) {
			platform.Error(w, http.StatusBadRequest, platform.CodeBadRequest, "no such ayah")
			return
		}
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not record the review")
		return
	}

	// XP is for the card graduating into review — the moment an ayah is actually held,
	// not every time one is drilled. Awarding per review would pay for repetition rather
	// than for memorizing, and a long backlog would dump hundreds of XP at once.
	state := gamify.State{}
	if req.State == stateReview {
		award, err := h.gamify.AwardOnce(r.Context(), userID, gamify.AwardCommand{
			Event: gamify.EventAyahMemorized,
			IdempotencyKey: "ayah:" + strconv.Itoa(req.Surah) + ":" +
				strconv.Itoa(req.Ayah),
			LocalDate: g.ReviewedAt,
		})
		if err != nil {
			platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not record progress")
			return
		}
		state = award.State
	}
	platform.JSON(w, http.StatusOK, map[string]any{
		"ok": true,
		"gamification": map[string]int{
			"streak": state.Streak, "level": state.Level, "xp": state.XP, "xp_next": state.XPNext,
		},
	})
}

func (h *Handler) savePlan(w http.ResponseWriter, r *http.Request) {
	var req PlanDTO
	if !platform.DecodeAndValidate(w, r, &req) {
		return
	}
	plan, err := h.svc.SavePlan(r.Context(), platform.MustUserID(r.Context()),
		Plan{DailyNew: req.DailyNew, ReviewCap: req.ReviewCap})
	if err != nil {
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not save the plan")
		return
	}
	platform.JSON(w, http.StatusOK, PlanDTO{DailyNew: plan.DailyNew, ReviewCap: plan.ReviewCap})
}

const (
	ratingAgain = 1
	// CardState.REVIEW on the client — a card that has graduated out of the learning steps.
	stateReview      = 2
	defaultDailyNew  = 2
	defaultReviewCap = 40
)
