package auth

import (
	"context"
	"errors"

	"github.com/google/uuid"

	"github.com/tafakkur/backend/internal/user"
)

var ErrBadProviderInput = errors.New("missing or invalid fields for provider")

type Service struct {
	users     user.Service
	jwt       *Manager
	providers *Verifier
	otp       *OTP
}

func NewService(users user.Service, jwt *Manager, providers *Verifier, otp *OTP) *Service {
	return &Service{users: users, jwt: jwt, providers: providers, otp: otp}
}

func (s *Service) CreateSession(ctx context.Context, req SessionRequest) (SessionResponse, error) {
	var ident user.ProviderIdentity
	var err error

	switch req.Provider {
	case "google":
		if req.IDToken == "" {
			return SessionResponse{}, ErrBadProviderInput
		}
		ident, err = s.providers.VerifyGoogle(ctx, req.IDToken)
	case "apple":
		if req.IDToken == "" {
			return SessionResponse{}, ErrBadProviderInput
		}
		ident, err = s.providers.VerifyApple(ctx, req.IDToken)
	case "phone":
		if req.VerificationID == "" || req.OTPCode == "" {
			return SessionResponse{}, ErrBadProviderInput
		}
		vid, perr := uuid.Parse(req.VerificationID)
		if perr != nil {
			return SessionResponse{}, ErrBadProviderInput
		}
		var phone string
		phone, err = s.otp.Verify(ctx, vid, req.OTPCode)
		if err == nil {
			ident = user.ProviderIdentity{Provider: "phone", Subject: phone, Phone: &phone}
		}
	default:
		return SessionResponse{}, ErrBadProviderInput
	}
	if err != nil {
		return SessionResponse{}, err
	}

	acc, _, err := s.users.UpsertByProvider(ctx, ident)
	if err != nil {
		return SessionResponse{}, err
	}
	return issueSession(s.jwt, acc)
}

func (s *Service) StartOTP(ctx context.Context, phone string) (uuid.UUID, error) {
	return s.otp.Start(ctx, phone)
}

// StartAddPhone sends a code to a number the signed-in user wants to attach to
// their account. The number is checked for a clash *before* the SMS goes out, so
// the user is not asked for a code that could never be redeemed.
func (s *Service) StartAddPhone(ctx context.Context, userID uuid.UUID, phone string) (uuid.UUID, error) {
	free, err := s.users.PhoneAvailable(ctx, userID, phone)
	if err != nil {
		return uuid.Nil, err
	}
	if !free {
		return uuid.Nil, user.ErrPhoneTaken
	}
	return s.otp.Start(ctx, phone)
}

// VerifyAddPhone redeems that code and writes the number onto the account. It
// deliberately trusts only the number the OTP challenge was created for, never
// one the client repeats back.
func (s *Service) VerifyAddPhone(ctx context.Context, userID uuid.UUID, verificationID uuid.UUID, code string) (user.UserProfileDTO, error) {
	phone, err := s.otp.Verify(ctx, verificationID, code)
	if err != nil {
		return user.UserProfileDTO{}, err
	}
	acc, err := s.users.SetPhone(ctx, userID, phone)
	if err != nil {
		return user.UserProfileDTO{}, err
	}
	return user.ToUserProfileDTO(acc), nil
}

func (s *Service) Refresh(ctx context.Context, refreshToken string) (RefreshResponse, error) {
	uid, err := s.jwt.VerifyRefresh(refreshToken)
	if err != nil {
		return RefreshResponse{}, err
	}
	access, err := s.jwt.IssueAccess(uid)
	if err != nil {
		return RefreshResponse{}, err
	}
	return RefreshResponse{
		AccessToken: access,
		TokenType:   "Bearer",
		ExpiresIn:   int64(s.jwt.AccessTTL().Seconds()),
	}, nil
}
