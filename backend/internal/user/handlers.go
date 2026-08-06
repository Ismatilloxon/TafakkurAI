package user

import (
	"errors"
	"fmt"
	"net/http"
	"path/filepath"
	"strings"
	"time"

	"github.com/go-chi/chi/v5"

	"github.com/tafakkur/backend/internal/media"
	"github.com/tafakkur/backend/internal/platform"
)

type Handler struct {
	svc   Service
	media media.Service
}

func NewHandler(svc Service, m media.Service) *Handler {
	return &Handler{svc: svc, media: m}
}

// Register mounts the profile routes onto an already-authenticated router group.
func (h *Handler) Register(r chi.Router) {
	r.Get("/me", h.getMe)
	r.Put("/me/profile", h.updateProfile)
	r.Put("/me/interests", h.replaceInterests)
	r.Put("/me/prayer-settings", h.updatePrayerSettings)
	r.Put("/me/email", h.setEmail)
	r.Post("/me/onboarding/complete", h.completeOnboarding)
	r.Post("/me/photo", h.uploadPhoto)
}

func (h *Handler) respondErr(w http.ResponseWriter, err error) {
	switch {
	case errors.Is(err, ErrNotFound):
		platform.Error(w, http.StatusNotFound, platform.CodeNotFound, "user not found")
	case errors.Is(err, ErrHandleTaken):
		platform.Error(w, http.StatusConflict, platform.CodeConflict, "handle already taken")
	case errors.Is(err, ErrEmailTaken):
		platform.Error(w, http.StatusConflict, platform.CodeConflict, "email already linked to another account")
	case errors.Is(err, ErrPhoneTaken):
		platform.Error(w, http.StatusConflict, platform.CodeConflict, "phone already linked to another account")
	default:
		platform.Error(w, http.StatusInternalServerError, platform.CodeInternal, "internal error")
	}
}

func (h *Handler) getMe(w http.ResponseWriter, r *http.Request) {
	uid := platform.MustUserID(r.Context())
	me, err := h.svc.GetMe(r.Context(), uid)
	if err != nil {
		h.respondErr(w, err)
		return
	}
	platform.JSON(w, http.StatusOK, me)
}

func (h *Handler) updateProfile(w http.ResponseWriter, r *http.Request) {
	var in UpdateProfileInput
	if !platform.DecodeAndValidate(w, r, &in) {
		return
	}
	acc, err := h.svc.UpdateProfile(r.Context(), platform.MustUserID(r.Context()), in)
	if err != nil {
		h.respondErr(w, err)
		return
	}
	platform.JSON(w, http.StatusOK, ToUserProfileDTO(acc))
}

func (h *Handler) replaceInterests(w http.ResponseWriter, r *http.Request) {
	var in ReplaceInterestsInput
	if !platform.DecodeAndValidate(w, r, &in) {
		return
	}
	acc, err := h.svc.ReplaceInterests(r.Context(), platform.MustUserID(r.Context()), in)
	if err != nil {
		h.respondErr(w, err)
		return
	}
	platform.JSON(w, http.StatusOK, ToUserProfileDTO(acc))
}

func (h *Handler) updatePrayerSettings(w http.ResponseWriter, r *http.Request) {
	var in PrayerSettingsInput
	if !platform.DecodeAndValidate(w, r, &in) {
		return
	}
	acc, err := h.svc.UpdatePrayerSettings(r.Context(), platform.MustUserID(r.Context()), in)
	if err != nil {
		h.respondErr(w, err)
		return
	}
	platform.JSON(w, http.StatusOK, ToUserProfileDTO(acc))
}

// setEmail fills in the address for someone who signed in with a phone number
// or Telegram. The phone side of the same gap needs an SMS round trip and lives
// in the auth module (POST /v1/me/phone/start).
func (h *Handler) setEmail(w http.ResponseWriter, r *http.Request) {
	var in SetEmailInput
	if !platform.DecodeAndValidate(w, r, &in) {
		return
	}
	acc, err := h.svc.SetEmail(r.Context(), platform.MustUserID(r.Context()), in.Email)
	if err != nil {
		h.respondErr(w, err)
		return
	}
	platform.JSON(w, http.StatusOK, ToUserProfileDTO(acc))
}

func (h *Handler) completeOnboarding(w http.ResponseWriter, r *http.Request) {
	acc, err := h.svc.CompleteOnboarding(r.Context(), platform.MustUserID(r.Context()))
	if err != nil {
		h.respondErr(w, err)
		return
	}
	platform.JSON(w, http.StatusOK, ToUserProfileDTO(acc))
}

const maxPhotoBytes = 8 << 20 // 8 MiB

func (h *Handler) uploadPhoto(w http.ResponseWriter, r *http.Request) {
	uid := platform.MustUserID(r.Context())

	if err := r.ParseMultipartForm(maxPhotoBytes); err != nil {
		platform.Error(w, http.StatusBadRequest, platform.CodeBadRequest, "invalid multipart form")
		return
	}
	file, header, err := r.FormFile("photo")
	if err != nil {
		platform.Error(w, http.StatusBadRequest, platform.CodeBadRequest, "missing 'photo' file field")
		return
	}
	defer file.Close()

	contentType := header.Header.Get("Content-Type")
	if !strings.HasPrefix(contentType, "image/") {
		platform.Error(w, http.StatusUnprocessableEntity, platform.CodeUnprocessable, "file must be an image")
		return
	}
	ext := filepath.Ext(header.Filename)
	if ext == "" {
		ext = ".jpg"
	}
	key := fmt.Sprintf("avatars/%s/%d%s", uid, time.Now().UTC().Unix(), ext)

	url, err := h.media.Upload(r.Context(), key, contentType, file)
	if err != nil {
		if errors.Is(err, media.ErrNotConfigured) {
			platform.Error(w, http.StatusServiceUnavailable, platform.CodeUnavailable, "media storage not configured")
			return
		}
		platform.Error(w, http.StatusBadGateway, platform.CodeInternal, "upload failed")
		return
	}
	if _, err := h.svc.SetPhoto(r.Context(), uid, url); err != nil {
		h.respondErr(w, err)
		return
	}
	platform.JSON(w, http.StatusOK, map[string]string{"url": url})
}
