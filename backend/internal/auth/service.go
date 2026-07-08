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

	access, err := s.jwt.IssueAccess(acc.User.ID)
	if err != nil {
		return SessionResponse{}, err
	}
	refresh, err := s.jwt.IssueRefresh(acc.User.ID)
	if err != nil {
		return SessionResponse{}, err
	}

	return SessionResponse{
		AccessToken:     access,
		RefreshToken:    refresh,
		TokenType:       "Bearer",
		ExpiresIn:       int64(s.jwt.AccessTTL().Seconds()),
		User:            user.ToUserProfileDTO(acc),
		NeedsOnboarding: !acc.Profile.OnboardingDone,
	}, nil
}

func (s *Service) StartOTP(ctx context.Context, phone string) (uuid.UUID, error) {
	return s.otp.Start(ctx, phone)
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
