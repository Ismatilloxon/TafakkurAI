package user

// UserProfileDTO matches the KMP client's UserProfile model exactly (snake_case).
type UserProfileDTO struct {
	UID                string   `json:"uid"`
	DisplayName        string   `json:"display_name"`
	Email              *string  `json:"email"`
	Phone              *string  `json:"phone"`
	PhotoURL           *string  `json:"photo_url"`
	Provider           string   `json:"provider"`
	Handle             string   `json:"handle"`
	Gender             *string  `json:"gender"`
	BirthDate          *string  `json:"birth_date"` // YYYY-MM-DD
	City               *string  `json:"city"`
	Country            *string  `json:"country"`
	LanguageTag        string   `json:"language_tag"`
	Madhab             string   `json:"madhab"`
	CalcMethod         string   `json:"calc_method"`
	Interests          []string `json:"interests"`
	OnboardingComplete bool     `json:"onboarding_complete"`
}

type GamificationDTO struct {
	Streak int `json:"streak"`
	Level  int `json:"level"`
	XP     int `json:"xp"`
	XPNext int `json:"xp_next"`
}

// MeResponse is returned by GET /v1/me.
type MeResponse struct {
	User         UserProfileDTO  `json:"user"`
	Gamification GamificationDTO `json:"gamification"`
}

const birthDateLayout = "2006-01-02"

func ToUserProfileDTO(a Account) UserProfileDTO {
	dto := UserProfileDTO{
		UID:                a.User.ID.String(),
		DisplayName:        a.Profile.DisplayName,
		Email:              a.User.Email,
		Phone:              a.User.Phone,
		PhotoURL:           a.Profile.PhotoURL,
		Provider:           a.User.Provider,
		Handle:             a.Profile.Handle,
		Gender:             a.Profile.Gender,
		City:               a.Profile.City,
		Country:            a.Profile.Country,
		LanguageTag:        a.Profile.Language,
		Madhab:             a.Profile.Madhab,
		CalcMethod:         a.Profile.CalcMethod,
		Interests:          a.Interests,
		OnboardingComplete: a.Profile.OnboardingDone,
	}
	if dto.Interests == nil {
		dto.Interests = []string{}
	}
	if a.Profile.BirthDate != nil {
		s := a.Profile.BirthDate.Format(birthDateLayout)
		dto.BirthDate = &s
	}
	return dto
}

// ---- request DTOs ----

type UpdateProfileInput struct {
	DisplayName *string `json:"display_name" validate:"omitempty,min=1,max=80"`
	Handle      *string `json:"handle" validate:"omitempty,min=3,max=30"`
	Gender      *string `json:"gender" validate:"omitempty,oneof=male female other prefer_not"`
	BirthDate   *string `json:"birth_date" validate:"omitempty,datetime=2006-01-02"`
	Language    *string `json:"language" validate:"omitempty,min=2,max=10"`
}

type ReplaceInterestsInput struct {
	Interests []string `json:"interests" validate:"required,max=50,dive,min=1,max=40"`
}

type PrayerSettingsInput struct {
	City       *string `json:"city" validate:"omitempty,max=80"`
	Country    *string `json:"country" validate:"omitempty,max=80"`
	Madhab     string  `json:"madhab" validate:"required,oneof=hanafi shafii maliki hanbali"`
	CalcMethod string  `json:"calc_method" validate:"required,oneof=MWL ISNA Egypt Makkah Karachi Tehran Jafari"`
}
