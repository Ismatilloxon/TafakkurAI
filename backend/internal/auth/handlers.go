package auth

import (
	"errors"
	"fmt"
	"log/slog"
	"net/http"

	"github.com/go-chi/chi/v5"
	"github.com/google/uuid"

	"github.com/tafakkur/backend/internal/platform"
	"github.com/tafakkur/backend/internal/ratelimit"
	"github.com/tafakkur/backend/internal/user"
)

type Handler struct {
	svc     *Service
	tg      *TelegramLogin
	google  *GoogleOAuth
	limiter *ratelimit.Limiter
}

func NewHandler(svc *Service, tg *TelegramLogin, google *GoogleOAuth, limiter *ratelimit.Limiter) *Handler {
	return &Handler{svc: svc, tg: tg, google: google, limiter: limiter}
}

// RegisterPublic mounts the unauthenticated auth endpoints.
func (h *Handler) RegisterPublic(r chi.Router) {
	r.Post("/auth/session", h.session)
	r.Post("/auth/otp/start", h.otpStart)
	r.Post("/auth/refresh", h.refresh)
	r.Get("/auth/telegram/bot", h.telegramBot)
	r.Post("/auth/telegram/verify", h.telegramVerify)
	r.Post("/auth/google/start", h.googleStart)
	r.Post("/auth/google/poll", h.googlePoll)
}

// RegisterProtected mounts endpoints that require a valid access token.
func (h *Handler) RegisterProtected(r chi.Router) {
	r.Post("/auth/logout", h.logout)
	// Completing the profile of an account that signed in with Google or Apple:
	// same SMS machinery as sign-in, but it attaches to the caller's account
	// instead of minting a session, so it sits behind the token.
	r.Post("/me/phone/start", h.addPhoneStart)
	r.Post("/me/phone/verify", h.addPhoneVerify)
}

// RegisterCallback mounts Google's browser redirect target. It is hit by the
// user's browser, not by the app, so it lives outside /v1's JSON contract and
// answers in HTML.
func (h *Handler) RegisterCallback(r chi.Router) {
	r.Get(googleCallbackPath, h.googleCallback)
}

func (h *Handler) rateLimited(w http.ResponseWriter, r *http.Request) bool {
	if !h.limiter.Allow(platform.ClientIP(r)) {
		platform.Error(w, http.StatusTooManyRequests, platform.CodeRateLimited, "too many requests")
		return true
	}
	return false
}

func (h *Handler) telegramBot(w http.ResponseWriter, r *http.Request) {
	resp, err := h.tg.BotInfo()
	if err != nil {
		writeAuthError(w, err)
		return
	}
	platform.JSON(w, http.StatusOK, resp)
}

func (h *Handler) telegramVerify(w http.ResponseWriter, r *http.Request) {
	// The auth limiter is what makes a 6-digit code safe: 20 tries a minute per IP
	// turns a million-code space into centuries.
	if h.rateLimited(w, r) {
		return
	}
	var req TelegramVerifyRequest
	if !platform.DecodeAndValidate(w, r, &req) {
		return
	}
	sess, err := h.tg.Verify(r.Context(), req.Code)
	if err != nil {
		writeAuthError(w, err)
		return
	}
	platform.JSON(w, http.StatusOK, sess)
}

func (h *Handler) googleStart(w http.ResponseWriter, r *http.Request) {
	if h.rateLimited(w, r) {
		return
	}
	resp, err := h.google.Start(r.Context())
	if err != nil {
		writeAuthError(w, err)
		return
	}
	platform.JSON(w, http.StatusOK, resp)
}

func (h *Handler) googlePoll(w http.ResponseWriter, r *http.Request) {
	var req GooglePollRequest
	if !platform.DecodeAndValidate(w, r, &req) {
		return
	}
	resp, err := h.google.Poll(r.Context(), req.State)
	if err != nil {
		writeAuthError(w, err)
		return
	}
	platform.JSON(w, http.StatusOK, resp)
}

// googleCallback answers the browser, not the app: the app learns the outcome by
// polling. Errors are shown as a page rather than JSON because a human is
// looking at this tab.
func (h *Handler) googleCallback(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	if e := q.Get("error"); e != "" {
		// The user pressed "Cancel" on Google's consent screen.
		writeCallbackPage(w, http.StatusOK, "Sign-in cancelled", "You can close this tab and try again in the app.")
		return
	}
	state, code := q.Get("state"), q.Get("code")
	if state == "" || code == "" {
		writeCallbackPage(w, http.StatusBadRequest, "Something went wrong", "Google's response was incomplete. Close this tab and try again.")
		return
	}
	if err := h.google.Callback(r.Context(), state, code); err != nil {
		slog.Warn("google oauth callback failed", "error", err)
		writeCallbackPage(w, http.StatusBadRequest, "Sign-in failed", "Close this tab and try again in the app.")
		return
	}
	writeCallbackPage(w, http.StatusOK, "You're signed in", "Return to TafakkurAI — this tab can be closed.")
}

func writeCallbackPage(w http.ResponseWriter, status int, title, body string) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.WriteHeader(status)
	fmt.Fprintf(w, `<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>%s</title>
<style>body{margin:0;min-height:100vh;display:grid;place-items:center;background:#0e1512;color:#e8f0ec;
font:16px/1.5 -apple-system,BlinkMacSystemFont,Segoe UI,Roboto,sans-serif;text-align:center;padding:24px}
h1{font-size:22px;margin:0 0 8px}p{margin:0;color:#9db3a9}</style></head>
<body><div><h1>%s</h1><p>%s</p></div></body></html>`, title, title, body)
}

func (h *Handler) addPhoneStart(w http.ResponseWriter, r *http.Request) {
	if h.rateLimited(w, r) {
		return
	}
	var req AddPhoneStartRequest
	if !platform.DecodeAndValidate(w, r, &req) {
		return
	}
	id, err := h.svc.StartAddPhone(r.Context(), platform.MustUserID(r.Context()), req.Phone)
	if err != nil {
		writeAuthError(w, err)
		return
	}
	platform.JSON(w, http.StatusOK, OTPStartResponse{VerificationID: id.String()})
}

func (h *Handler) addPhoneVerify(w http.ResponseWriter, r *http.Request) {
	if h.rateLimited(w, r) {
		return
	}
	var req AddPhoneVerifyRequest
	if !platform.DecodeAndValidate(w, r, &req) {
		return
	}
	vid, err := uuid.Parse(req.VerificationID)
	if err != nil {
		platform.Error(w, http.StatusBadRequest, platform.CodeBadRequest, "invalid verification_id")
		return
	}
	profile, err := h.svc.VerifyAddPhone(r.Context(), platform.MustUserID(r.Context()), vid, req.Code)
	if err != nil {
		writeAuthError(w, err)
		return
	}
	platform.JSON(w, http.StatusOK, profile)
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
		// The client is deliberately told nothing beyond "failed" — but a wrong audience, an
		// expired token and a bad signature all look identical from the outside, so the wrapped
		// reason goes to the log or a rejected sign-in is undiagnosable.
		slog.Warn("provider identity verification failed", "error", err)
		platform.Error(w, http.StatusUnauthorized, platform.CodeUnauthorized, "identity verification failed")
	case errors.Is(err, ErrTGNotConfigured):
		platform.Error(w, http.StatusServiceUnavailable, platform.CodeUnavailable, "telegram login not configured")
	case errors.Is(err, ErrTGCodeInvalid):
		platform.Error(w, http.StatusUnauthorized, platform.CodeUnauthorized, "invalid confirmation code")
	case errors.Is(err, ErrTGCodeExpired):
		platform.Error(w, http.StatusGone, platform.CodeUnauthorized, "confirmation code expired")
	case errors.Is(err, ErrOTPInvalid):
		platform.Error(w, http.StatusUnauthorized, platform.CodeUnauthorized, "invalid code")
	case errors.Is(err, ErrOTPExpired):
		platform.Error(w, http.StatusGone, platform.CodeUnauthorized, "code expired")
	case errors.Is(err, ErrOTPTooManyAttempts):
		platform.Error(w, http.StatusTooManyRequests, platform.CodeRateLimited, "too many attempts")
	case errors.Is(err, ErrOTPNotFound):
		platform.Error(w, http.StatusNotFound, platform.CodeNotFound, "verification not found")
	case errors.Is(err, ErrOAuthNotConfigured):
		platform.Error(w, http.StatusServiceUnavailable, platform.CodeUnavailable, "google sign-in not configured")
	case errors.Is(err, ErrOAuthStateInvalid):
		platform.Error(w, http.StatusUnauthorized, platform.CodeUnauthorized, "sign-in request invalid or already used")
	case errors.Is(err, ErrOAuthStateExpired):
		platform.Error(w, http.StatusGone, platform.CodeUnauthorized, "sign-in request expired")
	case errors.Is(err, ErrOAuthExchange):
		slog.Warn("google token exchange failed", "error", err)
		platform.Error(w, http.StatusBadGateway, platform.CodeInternal, "could not complete google sign-in")
	case errors.Is(err, user.ErrPhoneTaken):
		platform.Error(w, http.StatusConflict, platform.CodeConflict, "phone already linked to another account")
	case errors.Is(err, user.ErrNotFound):
		platform.Error(w, http.StatusNotFound, platform.CodeNotFound, "user not found")
	default:
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "internal error")
	}
}
