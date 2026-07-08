package feed

import (
	"errors"
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"
	"github.com/google/uuid"

	"github.com/tafakkur/backend/internal/platform"
	"github.com/tafakkur/backend/internal/ratelimit"
)

type Handler struct {
	svc     Service
	limiter *ratelimit.Limiter
}

func NewHandler(svc Service, limiter *ratelimit.Limiter) *Handler {
	return &Handler{svc: svc, limiter: limiter}
}

func (h *Handler) Register(r chi.Router) {
	r.Get("/thoughts", h.feed)
	r.Post("/thoughts", h.publish)
	r.Post("/thoughts/{id}/like", h.like)
}

type PublishInput struct {
	Text string `json:"text" validate:"required,min=1,max=500"`
}

func (h *Handler) feed(w http.ResponseWriter, r *http.Request) {
	limit, _ := strconv.Atoi(r.URL.Query().Get("limit"))
	page, err := h.svc.Feed(r.Context(), platform.MustUserID(r.Context()), r.URL.Query().Get("cursor"), limit)
	if err != nil {
		platform.Error(w, http.StatusBadRequest, platform.CodeBadRequest, "invalid cursor")
		return
	}
	platform.JSON(w, http.StatusOK, page)
}

func (h *Handler) publish(w http.ResponseWriter, r *http.Request) {
	uid := platform.MustUserID(r.Context())
	if !h.limiter.Allow(uid.String()) {
		platform.Error(w, http.StatusTooManyRequests, platform.CodeRateLimited, "posting too fast")
		return
	}
	var in PublishInput
	if !platform.DecodeAndValidate(w, r, &in) {
		return
	}
	post, err := h.svc.Publish(r.Context(), uid, in.Text)
	if err != nil {
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not publish")
		return
	}
	platform.JSON(w, http.StatusCreated, post)
}

func (h *Handler) like(w http.ResponseWriter, r *http.Request) {
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		platform.Error(w, http.StatusBadRequest, platform.CodeBadRequest, "invalid thought id")
		return
	}
	liked, count, err := h.svc.ToggleLike(r.Context(), platform.MustUserID(r.Context()), id)
	if err != nil {
		if errors.Is(err, ErrNotFound) {
			platform.Error(w, http.StatusNotFound, platform.CodeNotFound, "thought not found")
			return
		}
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not toggle like")
		return
	}
	platform.JSON(w, http.StatusOK, map[string]any{"liked": liked, "like_count": count})
}
