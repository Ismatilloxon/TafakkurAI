package user

import (
	"context"
	"errors"
	"image"
	_ "image/jpeg"
	_ "image/png"
	"io"
	"net/http"
	"path"
	"strings"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/google/uuid"

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

const (
	maxPhotoBytes                   = 8 << 20 // 8 MiB
	maxPhotoMultipartOverhead       = 64 << 10
	multipartMemoryBytes            = 256 << 10
	maxPhotoDimension               = 10_000
	maxPhotoPixels            int64 = 32_000_000
)

var (
	errPhotoEmpty       = errors.New("photo is empty")
	errPhotoTooLarge    = errors.New("photo is too large")
	errPhotoUnsupported = errors.New("photo format is unsupported")
	errPhotoDimensions  = errors.New("photo dimensions are unsafe")
)

type profilePhotoFormat struct {
	contentType string
	extension   string
}

func (h *Handler) uploadPhoto(w http.ResponseWriter, r *http.Request) {
	uid := platform.MustUserID(r.Context())

	if r.ContentLength > maxPhotoBytes+maxPhotoMultipartOverhead {
		platform.Error(w, http.StatusRequestEntityTooLarge, platform.CodePayloadTooLarge, "photo must be 8 MiB or smaller")
		return
	}
	r.Body = http.MaxBytesReader(w, r.Body, maxPhotoBytes+maxPhotoMultipartOverhead)
	if err := r.ParseMultipartForm(multipartMemoryBytes); err != nil {
		var sizeErr *http.MaxBytesError
		if errors.As(err, &sizeErr) {
			platform.Error(w, http.StatusRequestEntityTooLarge, platform.CodePayloadTooLarge, "photo must be 8 MiB or smaller")
			return
		}
		platform.Error(w, http.StatusBadRequest, platform.CodeBadRequest, "invalid multipart form")
		return
	}
	defer r.MultipartForm.RemoveAll()

	photoFiles := r.MultipartForm.File["photo"]
	if len(photoFiles) != 1 {
		platform.Error(w, http.StatusBadRequest, platform.CodeBadRequest, "exactly one 'photo' file is required")
		return
	}
	for field, files := range r.MultipartForm.File {
		if field != "photo" && len(files) > 0 {
			platform.Error(w, http.StatusBadRequest, platform.CodeBadRequest, "unexpected file field")
			return
		}
	}

	header := photoFiles[0]
	file, err := header.Open()
	if err != nil {
		platform.Error(w, http.StatusBadRequest, platform.CodeBadRequest, "could not read photo")
		return
	}
	defer file.Close()

	format, err := inspectProfilePhoto(file, header.Size)
	if err != nil {
		if errors.Is(err, errPhotoTooLarge) {
			platform.Error(w, http.StatusRequestEntityTooLarge, platform.CodePayloadTooLarge, "photo must be 8 MiB or smaller")
			return
		}
		platform.Error(w, http.StatusUnprocessableEntity, platform.CodeUnprocessable, "photo must be a valid JPEG or PNG within safe dimensions")
		return
	}
	key := "avatars/" + uid.String() + "/" + uuid.NewString() + format.extension

	url, err := h.media.Upload(r.Context(), media.Upload{
		Key:         key,
		ContentType: format.contentType,
		Size:        header.Size,
		Body:        file,
	})
	if err != nil {
		if errors.Is(err, media.ErrNotConfigured) {
			platform.Error(w, http.StatusServiceUnavailable, platform.CodeUnavailable, "media storage not configured")
			return
		}
		if errors.Is(err, media.ErrInvalidUpload) {
			platform.Error(w, http.StatusUnprocessableEntity, platform.CodeUnprocessable, "invalid photo upload")
			return
		}
		platform.Error(w, http.StatusBadGateway, platform.CodeInternal, "upload failed")
		return
	}
	previousURL, err := h.svc.SetPhoto(r.Context(), uid, url)
	if err != nil {
		// No profile references this object if the source-of-truth update failed.
		// Best-effort cleanup prevents orphaned public media and storage growth.
		cleanupCtx, cancel := context.WithTimeout(context.WithoutCancel(r.Context()), photoCleanupTimeout)
		_ = h.media.Delete(cleanupCtx, key)
		cancel()
		h.respondErr(w, err)
		return
	}
	if previousURL != nil {
		// A provider avatar or another bucket's URL is never a deletion target. Even below our
		// public base, accept only this user's exact server-generated avatar key shape.
		if previousKey, ok := h.media.ObjectKey(*previousURL); ok &&
			previousKey != key && isOwnedAvatarKey(uid, previousKey) {
			cleanupCtx, cancel := context.WithTimeout(context.WithoutCancel(r.Context()), photoCleanupTimeout)
			_ = h.media.Delete(cleanupCtx, previousKey)
			cancel()
		}
	}
	platform.JSON(w, http.StatusOK, map[string]string{"url": url})
}

const photoCleanupTimeout = 5 * time.Second

func isOwnedAvatarKey(userID uuid.UUID, key string) bool {
	prefix := "avatars/" + userID.String() + "/"
	if !strings.HasPrefix(key, prefix) {
		return false
	}
	name := strings.TrimPrefix(key, prefix)
	if name == "" || strings.Contains(name, "/") {
		return false
	}
	extension := path.Ext(name)
	if extension != ".jpg" && extension != ".png" {
		return false
	}
	_, err := uuid.Parse(strings.TrimSuffix(name, extension))
	return err == nil
}

func inspectProfilePhoto(file io.ReadSeeker, size int64) (profilePhotoFormat, error) {
	switch {
	case size <= 0:
		return profilePhotoFormat{}, errPhotoEmpty
	case size > maxPhotoBytes:
		return profilePhotoFormat{}, errPhotoTooLarge
	}

	var signature [512]byte
	n, err := io.ReadFull(file, signature[:])
	if err != nil && !errors.Is(err, io.EOF) && !errors.Is(err, io.ErrUnexpectedEOF) {
		return profilePhotoFormat{}, errPhotoUnsupported
	}
	detected := http.DetectContentType(signature[:n])
	format, ok := map[string]profilePhotoFormat{
		"image/jpeg": {contentType: "image/jpeg", extension: ".jpg"},
		"image/png":  {contentType: "image/png", extension: ".png"},
	}[detected]
	if !ok {
		return profilePhotoFormat{}, errPhotoUnsupported
	}

	if _, err := file.Seek(0, io.SeekStart); err != nil {
		return profilePhotoFormat{}, errPhotoUnsupported
	}
	config, decodedFormat, err := image.DecodeConfig(io.LimitReader(file, maxPhotoBytes))
	if err != nil || decodedFormat != formatName(format) {
		return profilePhotoFormat{}, errPhotoUnsupported
	}
	if config.Width <= 0 || config.Height <= 0 ||
		config.Width > maxPhotoDimension || config.Height > maxPhotoDimension ||
		int64(config.Width)*int64(config.Height) > maxPhotoPixels {
		return profilePhotoFormat{}, errPhotoDimensions
	}
	if _, err := file.Seek(0, io.SeekStart); err != nil {
		return profilePhotoFormat{}, errPhotoUnsupported
	}
	return format, nil
}

func formatName(format profilePhotoFormat) string {
	if format.contentType == "image/jpeg" {
		return "jpeg"
	}
	return "png"
}
