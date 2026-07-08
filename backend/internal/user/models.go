// Package user owns the user, profile and interests bounded context. It is the
// only package that touches those tables; auth and everything else go through
// the Service interface.
package user

import (
	"time"

	"github.com/google/uuid"
)

// ProviderIdentity is the verified identity handed to us by the auth module
// after it has validated a Google/Apple token or a phone OTP.
type ProviderIdentity struct {
	Provider    string // "google" | "apple" | "phone"
	Subject     string // stable provider subject (sub) or E.164 phone
	Email       *string
	Phone       *string
	DisplayName *string
	PhotoURL    *string
}

type User struct {
	ID        uuid.UUID
	Provider  string
	Email     *string
	Phone     *string
	CreatedAt time.Time
	UpdatedAt time.Time
}

type Profile struct {
	UserID         uuid.UUID
	DisplayName    string
	Handle         string
	Gender         *string
	BirthDate      *time.Time
	Language       string
	City           *string
	Country        *string
	Madhab         string
	CalcMethod     string
	PhotoURL       *string
	OnboardingDone bool
}

type Account struct {
	User      User
	Profile   Profile
	Interests []string
}

// PublicProfile is the subset of a profile safe to show other users (e.g. as a
// feed post author). Other modules fetch these in batch via the Service rather
// than joining the profiles table directly.
type PublicProfile struct {
	UserID      uuid.UUID
	DisplayName string
	Handle      string
	PhotoURL    *string
}
