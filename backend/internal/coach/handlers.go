package coach

import (
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"
	"github.com/google/uuid"

	"github.com/tafakkur/backend/internal/platform"
	"github.com/tafakkur/backend/internal/ratelimit"
)

type Handler struct {
	svc     *Service
	limiter *ratelimit.Limiter
}

func NewHandler(svc *Service, limiter *ratelimit.Limiter) *Handler {
	return &Handler{svc: svc, limiter: limiter}
}

func (h *Handler) Register(r chi.Router) {
	r.Post("/coach/message", h.message)
	r.Get("/coach/history", h.history)
}

type MessageInput struct {
	ConversationID *string `json:"conversation_id" validate:"omitempty,uuid"`
	Message        string  `json:"message" validate:"required,min=1,max=4000"`
}

func (h *Handler) message(w http.ResponseWriter, r *http.Request) {
	uid := platform.MustUserID(r.Context())
	if !h.limiter.Allow(uid.String()) {
		platform.Error(w, http.StatusTooManyRequests, platform.CodeRateLimited, "slow down — too many messages")
		return
	}

	var in MessageInput
	if !platform.DecodeAndValidate(w, r, &in) {
		return
	}
	var convID *uuid.UUID
	if in.ConversationID != nil {
		id, err := uuid.Parse(*in.ConversationID)
		if err != nil {
			platform.Error(w, http.StatusBadRequest, platform.CodeBadRequest, "invalid conversation_id")
			return
		}
		convID = &id
	}

	flusher, ok := w.(http.Flusher)
	if !ok {
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "streaming unsupported")
		return
	}

	w.Header().Set("Content-Type", "text/event-stream")
	w.Header().Set("Cache-Control", "no-cache")
	w.Header().Set("Connection", "keep-alive")
	w.Header().Set("X-Accel-Buffering", "no")
	w.WriteHeader(http.StatusOK)

	onDelta := func(delta string) error {
		writeSSE(w, "message", map[string]string{"type": "delta", "text": delta})
		flusher.Flush()
		return nil
	}

	cid, err := h.svc.StreamMessage(r.Context(), uid, convID, in.Message, onDelta)
	if err != nil {
		msg := "coach is unavailable right now"
		if errors.Is(err, ErrConversationNotFound) {
			msg = "conversation not found"
		} else if errors.Is(err, ErrNotConfigured) {
			msg = "coach is not configured"
		}
		writeSSE(w, "error", map[string]string{"message": msg})
		flusher.Flush()
		return
	}

	writeSSE(w, "done", map[string]string{"type": "done", "conversation_id": cid.String()})
	flusher.Flush()
}

func (h *Handler) history(w http.ResponseWriter, r *http.Request) {
	convID, err := uuid.Parse(r.URL.Query().Get("conversation_id"))
	if err != nil {
		platform.Error(w, http.StatusBadRequest, platform.CodeBadRequest, "conversation_id is required")
		return
	}
	limit, _ := strconv.Atoi(r.URL.Query().Get("limit"))
	page, err := h.svc.History(r.Context(), platform.MustUserID(r.Context()), convID, r.URL.Query().Get("cursor"), limit)
	if err != nil {
		if errors.Is(err, ErrConversationNotFound) {
			platform.Error(w, http.StatusNotFound, platform.CodeNotFound, "conversation not found")
			return
		}
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "could not load history")
		return
	}
	platform.JSON(w, http.StatusOK, page)
}

// writeSSE writes one Server-Sent Event frame: an event: line plus a data: line.
func writeSSE(w http.ResponseWriter, event string, payload any) {
	b, _ := json.Marshal(payload)
	fmt.Fprintf(w, "event: %s\ndata: %s\n\n", event, b)
}
