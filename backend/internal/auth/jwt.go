// Package auth verifies external provider identities (Google, Apple, phone OTP)
// and issues/validates this service's own JWT access & refresh tokens. It
// creates users through user.Service and never touches other domains' tables.
package auth

import (
	"errors"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"

	"github.com/tafakkur/backend/internal/config"
)

var ErrInvalidToken = errors.New("invalid token")

const (
	tokenTypeAccess  = "access"
	tokenTypeRefresh = "refresh"
)

type Claims struct {
	jwt.RegisteredClaims
	TokenType string `json:"typ"`
}

// Manager issues and verifies HS256 JWTs. It satisfies server.TokenVerifier.
type Manager struct {
	secret     []byte
	issuer     string
	accessTTL  time.Duration
	refreshTTL time.Duration
}

func NewManager(cfg config.Config) *Manager {
	return &Manager{
		secret:     []byte(cfg.JWTSecret),
		issuer:     cfg.JWTIssuer,
		accessTTL:  cfg.AccessTokenTTL,
		refreshTTL: cfg.RefreshTokenTTL,
	}
}

func (m *Manager) AccessTTL() time.Duration { return m.accessTTL }

func (m *Manager) issue(userID uuid.UUID, typ string, ttl time.Duration) (string, error) {
	now := time.Now()
	claims := Claims{
		RegisteredClaims: jwt.RegisteredClaims{
			Subject:   userID.String(),
			Issuer:    m.issuer,
			IssuedAt:  jwt.NewNumericDate(now),
			ExpiresAt: jwt.NewNumericDate(now.Add(ttl)),
		},
		TokenType: typ,
	}
	return jwt.NewWithClaims(jwt.SigningMethodHS256, claims).SignedString(m.secret)
}

func (m *Manager) IssueAccess(userID uuid.UUID) (string, error) {
	return m.issue(userID, tokenTypeAccess, m.accessTTL)
}

func (m *Manager) IssueRefresh(userID uuid.UUID) (string, error) {
	return m.issue(userID, tokenTypeRefresh, m.refreshTTL)
}

func (m *Manager) parse(token, expectedType string) (uuid.UUID, error) {
	var claims Claims
	_, err := jwt.ParseWithClaims(token, &claims, func(t *jwt.Token) (any, error) {
		if _, ok := t.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, ErrInvalidToken
		}
		return m.secret, nil
	}, jwt.WithValidMethods([]string{"HS256"}), jwt.WithIssuer(m.issuer))
	if err != nil {
		return uuid.Nil, ErrInvalidToken
	}
	if claims.TokenType != expectedType {
		return uuid.Nil, ErrInvalidToken
	}
	id, err := uuid.Parse(claims.Subject)
	if err != nil {
		return uuid.Nil, ErrInvalidToken
	}
	return id, nil
}

// VerifyAccess implements server.TokenVerifier.
func (m *Manager) VerifyAccess(token string) (uuid.UUID, error) {
	return m.parse(token, tokenTypeAccess)
}

func (m *Manager) VerifyRefresh(token string) (uuid.UUID, error) {
	return m.parse(token, tokenTypeRefresh)
}
