package auth

import "github.com/tafakkur/backend/internal/user"

type SessionRequest struct {
	Provider       string `json:"provider" validate:"required,oneof=google apple phone"`
	IDToken        string `json:"id_token"`
	Phone          string `json:"phone"`
	OTPCode        string `json:"otp_code"`
	VerificationID string `json:"verification_id"`
}

type OTPStartRequest struct {
	Phone string `json:"phone" validate:"required,e164"`
}

type RefreshRequest struct {
	RefreshToken string `json:"refresh_token" validate:"required"`
}

type SessionResponse struct {
	AccessToken     string              `json:"access_token"`
	RefreshToken    string              `json:"refresh_token"`
	TokenType       string              `json:"token_type"`
	ExpiresIn       int64               `json:"expires_in"`
	User            user.UserProfileDTO `json:"user"`
	NeedsOnboarding bool                `json:"needs_onboarding"`
}

type OTPStartResponse struct {
	VerificationID string `json:"verification_id"`
}

type RefreshResponse struct {
	AccessToken string `json:"access_token"`
	TokenType   string `json:"token_type"`
	ExpiresIn   int64  `json:"expires_in"`
}
