// Package learn serves learning content: subjects and their lessons. Content is
// read-mostly and seeded via migrations.
package learn

import (
	"context"
	"errors"
	"net/http"

	"github.com/go-chi/chi/v5"
	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/tafakkur/backend/internal/gamify"
	"github.com/tafakkur/backend/internal/platform"
)

var ErrNotFound = errors.New("not found")

type Subject struct {
	ID          uuid.UUID
	Title       string
	Description string
	OrderIndex  int
}

type Lesson struct {
	ID         uuid.UUID
	SubjectID  uuid.UUID
	Title      string
	Body       string
	OrderIndex int
}

// ---- repo ----

type Repo struct{ pool *pgxpool.Pool }

func NewRepo(pool *pgxpool.Pool) *Repo { return &Repo{pool: pool} }

func (r *Repo) ListSubjects(ctx context.Context) ([]Subject, error) {
	rows, err := r.pool.Query(ctx,
		`SELECT id, title, description, order_index FROM subjects ORDER BY order_index, title`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []Subject
	for rows.Next() {
		var s Subject
		if err := rows.Scan(&s.ID, &s.Title, &s.Description, &s.OrderIndex); err != nil {
			return nil, err
		}
		out = append(out, s)
	}
	return out, rows.Err()
}

func (r *Repo) ListLessons(ctx context.Context, subjectID uuid.UUID) ([]Lesson, error) {
	rows, err := r.pool.Query(ctx,
		`SELECT id, subject_id, title, body, order_index FROM lessons
		 WHERE subject_id = $1 ORDER BY order_index, title`, subjectID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []Lesson
	for rows.Next() {
		var l Lesson
		if err := rows.Scan(&l.ID, &l.SubjectID, &l.Title, &l.Body, &l.OrderIndex); err != nil {
			return nil, err
		}
		out = append(out, l)
	}
	return out, rows.Err()
}

func (r *Repo) GetLesson(ctx context.Context, id uuid.UUID) (Lesson, error) {
	var l Lesson
	err := r.pool.QueryRow(ctx,
		`SELECT id, subject_id, title, body, order_index FROM lessons WHERE id = $1`, id,
	).Scan(&l.ID, &l.SubjectID, &l.Title, &l.Body, &l.OrderIndex)
	if errors.Is(err, pgx.ErrNoRows) {
		return Lesson{}, ErrNotFound
	}
	return l, err
}

// ---- service ----

type Service interface {
	Subjects(ctx context.Context) ([]Subject, error)
	Lessons(ctx context.Context, subjectID uuid.UUID) ([]Lesson, error)
	Lesson(ctx context.Context, id uuid.UUID) (Lesson, error)
}

type service struct{ repo *Repo }

func NewService(repo *Repo) Service { return &service{repo: repo} }

func (s *service) Subjects(ctx context.Context) ([]Subject, error) { return s.repo.ListSubjects(ctx) }
func (s *service) Lessons(ctx context.Context, subjectID uuid.UUID) ([]Lesson, error) {
	return s.repo.ListLessons(ctx, subjectID)
}
func (s *service) Lesson(ctx context.Context, id uuid.UUID) (Lesson, error) {
	return s.repo.Lesson(ctx, id)
}

// small adapter so the interface method name matches repo
func (r *Repo) Lesson(ctx context.Context, id uuid.UUID) (Lesson, error) { return r.GetLesson(ctx, id) }

// ---- DTOs ----

type SubjectDTO struct {
	ID          string `json:"id"`
	Title       string `json:"title"`
	Description string `json:"description"`
	OrderIndex  int    `json:"order_index"`
}

type LessonDTO struct {
	ID         string `json:"id"`
	SubjectID  string `json:"subject_id"`
	Title      string `json:"title"`
	Body       string `json:"body"`
	OrderIndex int    `json:"order_index"`
}

func subjectDTO(s Subject) SubjectDTO {
	return SubjectDTO{ID: s.ID.String(), Title: s.Title, Description: s.Description, OrderIndex: s.OrderIndex}
}
func lessonDTO(l Lesson) LessonDTO {
	return LessonDTO{ID: l.ID.String(), SubjectID: l.SubjectID.String(), Title: l.Title, Body: l.Body, OrderIndex: l.OrderIndex}
}

// ---- handler ----

type Handler struct {
	svc    Service
	gamify gamify.Service
}

func NewHandler(svc Service, g gamify.Service) *Handler { return &Handler{svc: svc, gamify: g} }

func (h *Handler) Register(r chi.Router) {
	r.Get("/subjects", h.subjects)
	r.Get("/subjects/{id}/lessons", h.lessons)
	r.Get("/lessons/{id}", h.lesson)
	r.Post("/lessons/{id}/complete", h.complete)
}

func (h *Handler) subjects(w http.ResponseWriter, r *http.Request) {
	subs, err := h.svc.Subjects(r.Context())
	if err != nil {
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not load subjects")
		return
	}
	out := make([]SubjectDTO, 0, len(subs))
	for _, s := range subs {
		out = append(out, subjectDTO(s))
	}
	platform.JSON(w, http.StatusOK, map[string]any{"subjects": out})
}

func (h *Handler) lessons(w http.ResponseWriter, r *http.Request) {
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		platform.Error(w, http.StatusBadRequest, platform.CodeBadRequest, "invalid subject id")
		return
	}
	ls, err := h.svc.Lessons(r.Context(), id)
	if err != nil {
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not load lessons")
		return
	}
	out := make([]LessonDTO, 0, len(ls))
	for _, l := range ls {
		out = append(out, lessonDTO(l))
	}
	platform.JSON(w, http.StatusOK, map[string]any{"lessons": out})
}

func (h *Handler) lesson(w http.ResponseWriter, r *http.Request) {
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		platform.Error(w, http.StatusBadRequest, platform.CodeBadRequest, "invalid lesson id")
		return
	}
	l, err := h.svc.Lesson(r.Context(), id)
	if err != nil {
		if errors.Is(err, ErrNotFound) {
			platform.Error(w, http.StatusNotFound, platform.CodeNotFound, "lesson not found")
			return
		}
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not load lesson")
		return
	}
	platform.JSON(w, http.StatusOK, lessonDTO(l))
}

func (h *Handler) complete(w http.ResponseWriter, r *http.Request) {
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		platform.Error(w, http.StatusBadRequest, platform.CodeBadRequest, "invalid lesson id")
		return
	}
	if _, err := h.svc.Lesson(r.Context(), id); err != nil {
		platform.Error(w, http.StatusNotFound, platform.CodeNotFound, "lesson not found")
		return
	}
	state, err := h.gamify.Award(r.Context(), platform.MustUserID(r.Context()), gamify.EventLessonCompleted)
	if err != nil {
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not record completion")
		return
	}
	platform.JSON(w, http.StatusOK, map[string]any{
		"ok": true,
		"gamification": map[string]int{
			"streak": state.Streak, "level": state.Level, "xp": state.XP, "xp_next": state.XPNext,
		},
	})
}
