package platform

import (
	"encoding/json"
	"errors"
	"log/slog"
	"net/http"

	"github.com/go-playground/validator/v10"
)

// APIError is the single error envelope returned by every endpoint:
// {"error": {"code": "...", "message": "..."}}.
type APIError struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}

type errorEnvelope struct {
	Error APIError `json:"error"`
}

// Common error codes (stable strings the mobile client can switch on).
const (
	CodeBadRequest    = "bad_request"
	CodeValidation    = "validation_error"
	CodeUnauthorized  = "unauthorized"
	CodeForbidden     = "forbidden"
	CodeNotFound      = "not_found"
	CodeConflict      = "conflict"
	CodeRateLimited   = "rate_limited"
	CodeInternal      = "internal_error"
	CodeUnavailable   = "unavailable"
	CodeUnprocessable = "unprocessable"
)

var validate = validator.New()

// JSON writes v as a JSON response with the given status code.
func JSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	if v == nil {
		return
	}
	if err := json.NewEncoder(w).Encode(v); err != nil {
		slog.Error("encode response", "error", err)
	}
}

// Error writes the standard error envelope.
func Error(w http.ResponseWriter, status int, code, message string) {
	JSON(w, status, errorEnvelope{Error: APIError{Code: code, Message: message}})
}

// DecodeAndValidate decodes a JSON body into dst (rejecting unknown fields) and
// runs go-playground/validator struct validation.
func DecodeAndValidate(w http.ResponseWriter, r *http.Request, dst any) bool {
	dec := json.NewDecoder(r.Body)
	dec.DisallowUnknownFields()
	if err := dec.Decode(dst); err != nil {
		Error(w, http.StatusBadRequest, CodeBadRequest, "invalid or malformed JSON body")
		return false
	}
	if err := validate.Struct(dst); err != nil {
		var ve validator.ValidationErrors
		msg := "request validation failed"
		if errors.As(err, &ve) && len(ve) > 0 {
			msg = "invalid field: " + ve[0].Field()
		}
		Error(w, http.StatusUnprocessableEntity, CodeValidation, msg)
		return false
	}
	return true
}
