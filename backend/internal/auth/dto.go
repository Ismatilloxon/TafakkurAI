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

// ---- Google browser sign-in ----

type GoogleStartResponse struct {
	State          string `json:"state"`
	AuthURL        string `json:"auth_url"`
	PollIntervalMS int64  `json:"poll_interval_ms"`
	ExpiresIn      int64  `json:"expires_in"`
}

type GooglePollRequest struct {
	State string `json:"state" validate:"required"`
}

// GooglePollResponse carries the session only once the browser leg has finished;
// Status is "pending" until then.
type GooglePollResponse struct {
	Status  string           `json:"status"`
	Session *SessionResponse `json:"session,omitempty"`
}

// ---- Attaching a phone to an account that signed in another way ----

type AddPhoneStartRequest struct {
	Phone string `json:"phone" validate:"required,e164"`
}

type AddPhoneVerifyRequest struct {
	VerificationID string `json:"verification_id" validate:"required,uuid"`
	Code           string `json:"code" validate:"required,len=6,numeric"`
}

type TelegramBotResponse struct {
	BotUsername string `json:"bot_username"`
	DeepLink    string `json:"deep_link"`
	CodeLength  int    `json:"code_length"`
}

type TelegramVerifyRequest struct {
	Code string `json:"code" validate:"required,len=6,numeric"`
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
