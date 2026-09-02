// Package learn serves learning content: subjects and their lessons. Content is
// read-mostly and seeded via migrations.
package learn

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"time"

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
	Completed  bool
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

func (r *Repo) ListLessons(ctx context.Context, userID, subjectID uuid.UUID) ([]Lesson, error) {
	rows, err := r.pool.Query(ctx,
		`SELECT lesson.id, lesson.subject_id, lesson.title, lesson.body, lesson.order_index,
		        completion.lesson_id IS NOT NULL
		 FROM lessons AS lesson
		 LEFT JOIN lesson_completions AS completion
		   ON completion.user_id = $1 AND completion.lesson_id = lesson.id
		 WHERE lesson.subject_id = $2
		 ORDER BY lesson.order_index, lesson.title`, userID, subjectID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []Lesson
	for rows.Next() {
		var l Lesson
		if err := rows.Scan(&l.ID, &l.SubjectID, &l.Title, &l.Body, &l.OrderIndex, &l.Completed); err != nil {
			return nil, err
		}
		out = append(out, l)
	}
	return out, rows.Err()
}

func (r *Repo) GetLesson(ctx context.Context, userID, id uuid.UUID) (Lesson, error) {
	var l Lesson
	err := r.pool.QueryRow(ctx,
		`SELECT lesson.id, lesson.subject_id, lesson.title, lesson.body, lesson.order_index,
		        completion.lesson_id IS NOT NULL
		 FROM lessons AS lesson
		 LEFT JOIN lesson_completions AS completion
		   ON completion.user_id = $1 AND completion.lesson_id = lesson.id
		 WHERE lesson.id = $2`, userID, id,
	).Scan(&l.ID, &l.SubjectID, &l.Title, &l.Body, &l.OrderIndex, &l.Completed)
	if errors.Is(err, pgx.ErrNoRows) {
		return Lesson{}, ErrNotFound
	}
	return l, err
}

// ---- service ----

type Service interface {
	Subjects(ctx context.Context) ([]Subject, error)
	Lessons(ctx context.Context, userID, subjectID uuid.UUID) ([]Lesson, error)
	Lesson(ctx context.Context, userID, id uuid.UUID) (Lesson, error)
	Complete(ctx context.Context, userID, lessonID uuid.UUID, command CompleteCommand) (CompleteResult, error)
	withGamify(gamify.Service) Service
}

type service struct {
	repo   *Repo
	gamify gamify.Service
	now    func() time.Time
}

func NewService(repo *Repo) Service {
	return &service{repo: repo, now: func() time.Time { return time.Now().UTC() }}
}

func (s *service) withGamify(g gamify.Service) Service {
	configured := *s
	configured.gamify = g
	return &configured
}

func (s *service) Subjects(ctx context.Context) ([]Subject, error) { return s.repo.ListSubjects(ctx) }
func (s *service) Lessons(ctx context.Context, userID, subjectID uuid.UUID) ([]Lesson, error) {
	return s.repo.ListLessons(ctx, userID, subjectID)
}
func (s *service) Lesson(ctx context.Context, userID, id uuid.UUID) (Lesson, error) {
	return s.repo.GetLesson(ctx, userID, id)
}

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
	Completed  bool   `json:"completed"`
}

func subjectDTO(s Subject) SubjectDTO {
	return SubjectDTO{ID: s.ID.String(), Title: s.Title, Description: s.Description, OrderIndex: s.OrderIndex}
}
func lessonDTO(l Lesson) LessonDTO {
	return LessonDTO{
		ID: l.ID.String(), SubjectID: l.SubjectID.String(), Title: l.Title,
		Body: l.Body, OrderIndex: l.OrderIndex, Completed: l.Completed,
	}
}

// ---- handler ----

type Handler struct {
	svc Service
}

func NewHandler(svc Service, g gamify.Service) *Handler {
	return &Handler{svc: svc.withGamify(g)}
}

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
	ls, err := h.svc.Lessons(r.Context(), platform.MustUserID(r.Context()), id)
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
	l, err := h.svc.Lesson(r.Context(), platform.MustUserID(r.Context()), id)
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

type CompleteRequestDTO struct {
	EventID   string `json:"event_id"`
	LocalDate string `json:"local_date"`
	Timezone  string `json:"timezone"`
}

type CompletionDTO struct {
	EventID     string `json:"event_id"`
	LessonID    string `json:"lesson_id"`
	LocalDate   string `json:"local_date"`
	Timezone    string `json:"timezone"`
	CompletedAt string `json:"completed_at"`
}

func completionDTO(completion Completion) CompletionDTO {
	return CompletionDTO{
		EventID:     completion.EventID.String(),
		LessonID:    completion.LessonID.String(),
		LocalDate:   completion.LocalDate.Format(time.DateOnly),
		Timezone:    completion.Timezone,
		CompletedAt: completion.CompletedAt.UTC().Format(time.RFC3339Nano),
	}
}

func decodeCompleteRequest(w http.ResponseWriter, r *http.Request) (CompleteRequestDTO, bool) {
	r.Body = http.MaxBytesReader(w, r.Body, 2*1024)
	decoder := json.NewDecoder(r.Body)
	decoder.DisallowUnknownFields()

	var request CompleteRequestDTO
	if err := decoder.Decode(&request); err != nil {
		if errors.Is(err, io.EOF) {
			// Compatibility with clients released before completion metadata existed.
			return CompleteRequestDTO{}, true
		}
		platform.Error(w, http.StatusBadRequest, platform.CodeBadRequest, "invalid or malformed JSON body")
		return CompleteRequestDTO{}, false
	}
	if err := decoder.Decode(&struct{}{}); !errors.Is(err, io.EOF) {
		platform.Error(w, http.StatusBadRequest, platform.CodeBadRequest, "request body must contain one JSON object")
		return CompleteRequestDTO{}, false
	}
	return request, true
}

func completeCommand(request CompleteRequestDTO, lessonID uuid.UUID, now time.Time) CompleteCommand {
	if request.EventID == "" && request.LocalDate == "" && request.Timezone == "" {
		// Legacy clients did not send a body. The lesson UUID is stable per user and
		// exactly matches migration 0012's old "lesson:<uuid>" award key.
		return CompleteCommand{
			EventID:   lessonID.String(),
			LocalDate: now.UTC().Format(time.DateOnly),
			Timezone:  "UTC",
		}
	}
	return CompleteCommand{
		EventID: request.EventID, LocalDate: request.LocalDate, Timezone: request.Timezone,
	}
}

func (h *Handler) complete(w http.ResponseWriter, r *http.Request) {
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		platform.Error(w, http.StatusBadRequest, platform.CodeBadRequest, "invalid lesson id")
		return
	}
	request, ok := decodeCompleteRequest(w, r)
	if !ok {
		return
	}
	result, err := h.svc.Complete(
		r.Context(),
		platform.MustUserID(r.Context()),
		id,
		completeCommand(request, id, time.Now()),
	)
	switch {
	case errors.Is(err, ErrInvalidCompletion):
		platform.Error(w, http.StatusUnprocessableEntity, platform.CodeValidation, "invalid lesson completion")
		return
	case errors.Is(err, ErrCompletionConflict):
		platform.Error(w, http.StatusConflict, platform.CodeConflict, "completion event belongs to another lesson")
		return
	case errors.Is(err, ErrNotFound):
		platform.Error(w, http.StatusNotFound, platform.CodeNotFound, "lesson not found")
		return
	case err != nil:
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not record completion")
		return
	}
	state := result.Gamification
	platform.JSON(w, http.StatusOK, map[string]any{
		"ok":         true,
		"completion": completionDTO(result.Completion),
		"gamification": map[string]int{
			"streak": state.Streak, "level": state.Level, "xp": state.XP, "xp_next": state.XPNext,
		},
	})
}
