package user

import (
	"context"
	"time"

	"github.com/google/uuid"

	"github.com/tafakkur/backend/internal/gamify"
)

// Service is the user bounded context's public API. auth depends only on
// UpsertByProvider; the rest is consumed by this module's own handlers.
type Service interface {
	UpsertByProvider(ctx context.Context, ident ProviderIdentity) (Account, bool, error)
	GetAccount(ctx context.Context, userID uuid.UUID) (Account, error)
	GetMe(ctx context.Context, userID uuid.UUID) (MeResponse, error)
	UpdateProfile(ctx context.Context, userID uuid.UUID, in UpdateProfileInput) (Account, error)
	ReplaceInterests(ctx context.Context, userID uuid.UUID, in ReplaceInterestsInput) (Account, error)
	UpdatePrayerSettings(ctx context.Context, userID uuid.UUID, in PrayerSettingsInput) (Account, error)
	CompleteOnboarding(ctx context.Context, userID uuid.UUID) (Account, error)
	SetPhoto(ctx context.Context, userID uuid.UUID, url string) (Account, error)
	PublicProfiles(ctx context.Context, ids []uuid.UUID) (map[uuid.UUID]PublicProfile, error)
}

type service struct {
	repo   *Repo
	gamify gamify.Service
}

func NewService(repo *Repo, g gamify.Service) Service {
	return &service{repo: repo, gamify: g}
}

func (s *service) UpsertByProvider(ctx context.Context, ident ProviderIdentity) (Account, bool, error) {
	userID, isNew, err := s.repo.UpsertUser(ctx, ident)
	if err != nil {
		return Account{}, false, err
	}
	if err := s.repo.CreateProfileIfAbsent(ctx, userID, ident); err != nil {
		return Account{}, false, err
	}
	if err := s.gamify.EnsureExists(ctx, userID); err != nil {
		return Account{}, false, err
	}
	acc, err := s.repo.GetAccount(ctx, userID)
	return acc, isNew, err
}

func (s *service) GetAccount(ctx context.Context, userID uuid.UUID) (Account, error) {
	return s.repo.GetAccount(ctx, userID)
}

func (s *service) GetMe(ctx context.Context, userID uuid.UUID) (MeResponse, error) {
	acc, err := s.repo.GetAccount(ctx, userID)
	if err != nil {
		return MeResponse{}, err
	}
	st, err := s.gamify.Get(ctx, userID)
	if err != nil {
		return MeResponse{}, err
	}
	return MeResponse{
		User: ToUserProfileDTO(acc),
		Gamification: GamificationDTO{
			Streak: st.Streak, Level: st.Level, XP: st.XP, XPNext: st.XPNext,
		},
	}, nil
}

func (s *service) UpdateProfile(ctx context.Context, userID uuid.UUID, in UpdateProfileInput) (Account, error) {
	var birth *time.Time
	if in.BirthDate != nil {
		t, err := time.Parse(birthDateLayout, *in.BirthDate)
		if err != nil {
			return Account{}, err
		}
		birth = &t
	}
	if err := s.repo.UpdateProfile(ctx, userID, in, birth); err != nil {
		return Account{}, err
	}
	return s.repo.GetAccount(ctx, userID)
}

func (s *service) ReplaceInterests(ctx context.Context, userID uuid.UUID, in ReplaceInterestsInput) (Account, error) {
	if err := s.repo.ReplaceInterests(ctx, userID, in.Interests); err != nil {
		return Account{}, err
	}
	return s.repo.GetAccount(ctx, userID)
}

func (s *service) UpdatePrayerSettings(ctx context.Context, userID uuid.UUID, in PrayerSettingsInput) (Account, error) {
	if err := s.repo.UpdatePrayerSettings(ctx, userID, in); err != nil {
		return Account{}, err
	}
	return s.repo.GetAccount(ctx, userID)
}

func (s *service) CompleteOnboarding(ctx context.Context, userID uuid.UUID) (Account, error) {
	if err := s.repo.SetOnboardingDone(ctx, userID); err != nil {
		return Account{}, err
	}
	return s.repo.GetAccount(ctx, userID)
}

func (s *service) SetPhoto(ctx context.Context, userID uuid.UUID, url string) (Account, error) {
	if err := s.repo.SetPhoto(ctx, userID, url); err != nil {
		return Account{}, err
	}
	return s.repo.GetAccount(ctx, userID)
}

func (s *service) PublicProfiles(ctx context.Context, ids []uuid.UUID) (map[uuid.UUID]PublicProfile, error) {
	return s.repo.PublicProfiles(ctx, ids)
}
