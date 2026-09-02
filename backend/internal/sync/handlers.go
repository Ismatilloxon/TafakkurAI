package sync

import (
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"

	"github.com/tafakkur/backend/internal/platform"
	"github.com/tafakkur/backend/internal/ratelimit"
)

// Handler exposes the two endpoints the whole app synchronises through. There will
// never be a third: a new feature reuses these unchanged.
type Handler struct {
	svc     Service
	limiter *ratelimit.Limiter
}

func NewHandler(svc Service, limiter *ratelimit.Limiter) *Handler {
	return &Handler{svc: svc, limiter: limiter}
}

func (h *Handler) Register(r chi.Router) {
	r.Post("/sync/batch", h.batch)
	r.Get("/sync/changes", h.changes)
	r.Get("/sync/stats", h.stats)
}

// BatchRequest is the push wire format. `events` is capped at MaxBatchEvents; a client
// with a longer queue splits it and sends several batches, which is why the cap can stay
// low enough to bound the transaction.
type BatchRequest struct {
	Events []Event `json:"events" validate:"required,min=1,max=100,dive"`
}

const maxBatchBodyBytes = int64(MaxBatchEvents * (MaxPayloadBytes + MaxMetadataBytes + 2048))

func (h *Handler) batch(w http.ResponseWriter, r *http.Request) {
	uid := platform.MustUserID(r.Context())
	if h.limiter != nil && !h.limiter.Allow(uid.String()) {
		platform.Error(w, http.StatusTooManyRequests, platform.CodeRateLimited, "too many sync requests")
		return
	}
	if r.ContentLength > maxBatchBodyBytes {
		platform.Error(w, http.StatusRequestEntityTooLarge, platform.CodePayloadTooLarge, "sync batch is too large")
		return
	}
	r.Body = http.MaxBytesReader(w, r.Body, maxBatchBodyBytes)

	var in BatchRequest
	if !platform.DecodeAndValidate(w, r, &in) {
		return
	}
	if len(in.Events) == 0 {
		platform.Error(w, http.StatusBadRequest, platform.CodeBadRequest, ErrEmptyBatch.Error())
		return
	}
	if len(in.Events) > MaxBatchEvents {
		platform.Error(w, http.StatusBadRequest, platform.CodeBadRequest, ErrBatchTooLarge.Error())
		return
	}

	res, err := h.svc.ApplyBatch(r.Context(), uid, in.Events)
	if err != nil {
		// Deliberately 500 with nothing applied: the client keeps the whole batch
		// queued and retries. Losing events is the one failure mode not tolerated.
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not apply sync batch")
		return
	}
	platform.JSON(w, http.StatusOK, res)
}

// changes is the delta pull. `cursor` is the last `seq` the device stored; 0 means
// "give me everything", which is exactly what a fresh install or a new device needs.
func (h *Handler) changes(w http.ResponseWriter, r *http.Request) {
	uid := platform.MustUserID(r.Context())
	cursor, _ := strconv.ParseInt(r.URL.Query().Get("cursor"), 10, 64)
	if cursor < 0 {
		cursor = 0
	}
	limit, _ := strconv.Atoi(r.URL.Query().Get("limit"))

	set, err := h.svc.Changes(r.Context(), uid, cursor, limit)
	if err != nil {
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not load changes")
		return
	}
	platform.JSON(w, http.StatusOK, set)
}

// stats returns process-wide sync counters. Behind the auth middleware, and it exposes
// no user data — only aggregate health of the sync platform itself.
func (h *Handler) stats(w http.ResponseWriter, r *http.Request) {
	platform.JSON(w, http.StatusOK, h.svc.Metrics())
}
