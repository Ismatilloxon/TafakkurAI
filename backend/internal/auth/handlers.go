package auth

import (
	"errors"
	"net/http"

	"github.com/go-chi/chi/v5"

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

// RegisterPublic mounts the unauthenticated auth endpoints.
func (h *Handler) RegisterPublic(r chi.Router) {
	r.Post("/auth/session", h.session)
	r.Post("/auth/otp/start", h.otpStart)
	r.Post("/auth/refresh", h.refresh)
}

// RegisterProtected mounts endpoints that require a valid access token.
func (h *Handler) RegisterProtected(r chi.Router) {
	r.Post("/auth/logout", h.logout)
}

func (h *Handler) rateLimited(w http.ResponseWriter, r *http.Request) bool {
	if !h.limiter.Allow(platform.ClientIP(r)) {
		platform.Error(w, http.StatusTooManyRequests, platform.CodeRateLimited, "too many requests")
		return true
	}
	return false
}

func (h *Handler) session(w http.ResponseWriter, r *http.Request) {
	if h.rateLimited(w, r) {
		return
	}
	var req SessionRequest
	if !platform.DecodeAndValidate(w, r, &req) {
		return
	}
	resp, err := h.svc.CreateSession(r.Context(), req)
	if err != nil {
		writeAuthError(w, err)
		return
	}
	platform.JSON(w, http.StatusOK, resp)
}

func (h *Handler) otpStart(w http.ResponseWriter, r *http.Request) {
	if h.rateLimited(w, r) {
		return
	}
	var req OTPStartRequest
	if !platform.DecodeAndValidate(w, r, &req) {
		return
	}
	id, err := h.svc.StartOTP(r.Context(), req.Phone)
	if err != nil {
		platform.Error(w, http.StatusBadGateway, platform.CodeInternal, "could not send code")
		return
	}
	platform.JSON(w, http.StatusOK, OTPStartResponse{VerificationID: id.String()})
}

func (h *Handler) refresh(w http.ResponseWriter, r *http.Request) {
	if h.rateLimited(w, r) {
		return
	}
	var req RefreshRequest
	if !platform.DecodeAndValidate(w, r, &req) {
		return
	}
	resp, err := h.svc.Refresh(r.Context(), req.RefreshToken)
	if err != nil {
		platform.Error(w, http.StatusUnauthorized, platform.CodeUnauthorized, "invalid refresh token")
		return
	}
	platform.JSON(w, http.StatusOK, resp)
}

// logout is a no-op for stateless JWTs; the client discards its tokens. Swap in
// a refresh-token denylist here if you need server-side revocation.
func (h *Handler) logout(w http.ResponseWriter, r *http.Request) {
	platform.JSON(w, http.StatusOK, map[string]bool{"ok": true})
}

func writeAuthError(w http.ResponseWriter, err error) {
	switch {
	case errors.Is(err, ErrBadProviderInput):
		platform.Error(w, http.StatusBadRequest, platform.CodeBadRequest, "missing or invalid provider fields")
	case errors.Is(err, ErrProviderNotConfigured):
		platform.Error(w, http.StatusServiceUnavailable, platform.CodeUnavailable, "identity provider not configured")
	case errors.Is(err, ErrProviderVerify):
		platform.Error(w, http.StatusUnauthorized, platform.CodeUnauthorized, "identity verification failed")
	case errors.Is(err, ErrOTPInvalid):
		platform.Error(w, http.StatusUnauthorized, platform.CodeUnauthorized, "invalid code")
	case errors.Is(err, ErrOTPExpired):
		platform.Error(w, http.StatusGone, platform.CodeUnauthorized, "code expired")
	case errors.Is(err, ErrOTPTooManyAttempts):
		platform.Error(w, http.StatusTooManyRequests, platform.CodeRateLimited, "too many attempts")
	case errors.Is(err, ErrOTPNotFound):
		platform.Error(w, http.StatusNotFound, platform.CodeNotFound, "verification not found")
	default:
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "internal error")
	}
}
