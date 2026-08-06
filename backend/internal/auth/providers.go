package auth

import (
	"context"
	"crypto/rsa"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"math/big"
	"net/http"
	"sync"
	"time"

	"github.com/golang-jwt/jwt/v5"

	"github.com/tafakkur/backend/internal/config"
	"github.com/tafakkur/backend/internal/user"
)

const (
	googleJWKSURL = "https://www.googleapis.com/oauth2/v3/certs"
	appleJWKSURL  = "https://appleid.apple.com/auth/keys"
	appleIssuer   = "https://appleid.apple.com"
)

var googleIssuers = []string{"accounts.google.com", "https://accounts.google.com"}

var (
	ErrProviderNotConfigured = errors.New("identity provider not configured")
	ErrProviderVerify        = errors.New("provider token verification failed")
)

// Verifier validates Google and Apple ID tokens against the providers' JWKS.
type Verifier struct {
	googleAudiences []string
	appleBundleID   string
	google          *jwksCache
	apple           *jwksCache
}

func NewVerifier(cfg config.Config) *Verifier {
	httpc := &http.Client{Timeout: 10 * time.Second}

	// The browser flow's own client id is always an accepted audience: it is this
	// server that asked Google for the token, so forgetting to repeat it in
	// GOOGLE_CLIENT_ID would reject the sign-in the server just performed.
	auds := append([]string(nil), cfg.GoogleClientIDs...)
	if cfg.GoogleOAuthClientID != "" {
		auds = append(auds, cfg.GoogleOAuthClientID)
	}

	return &Verifier{
		googleAudiences: auds,
		appleBundleID:   cfg.AppleBundleID,
		google:          &jwksCache{url: googleJWKSURL, http: httpc, ttl: time.Hour},
		apple:           &jwksCache{url: appleJWKSURL, http: httpc, ttl: time.Hour},
	}
}

// SetJWKSURLs points the verifier at stand-in providers. Tests only — production
// keys must come from Google and Apple.
func (v *Verifier) SetJWKSURLs(google, apple string) {
	if google != "" {
		v.google.url = google
	}
	if apple != "" {
		v.apple.url = apple
	}
}

type oidcClaims struct {
	jwt.RegisteredClaims
	Email   string `json:"email"`
	Name    string `json:"name"`
	Picture string `json:"picture"`
}

func (v *Verifier) VerifyGoogle(ctx context.Context, idToken string) (user.ProviderIdentity, error) {
	if len(v.googleAudiences) == 0 {
		return user.ProviderIdentity{}, ErrProviderNotConfigured
	}
	claims, err := verifyOIDC(ctx, v.google, idToken, v.googleAudiences, googleIssuers)
	if err != nil {
		return user.ProviderIdentity{}, err
	}
	return toIdentity("google", claims), nil
}

func (v *Verifier) VerifyApple(ctx context.Context, idToken string) (user.ProviderIdentity, error) {
	if v.appleBundleID == "" {
		return user.ProviderIdentity{}, ErrProviderNotConfigured
	}
	claims, err := verifyOIDC(ctx, v.apple, idToken, []string{v.appleBundleID}, []string{appleIssuer})
	if err != nil {
		return user.ProviderIdentity{}, err
	}
	return toIdentity("apple", claims), nil
}

func toIdentity(provider string, c *oidcClaims) user.ProviderIdentity {
	id := user.ProviderIdentity{Provider: provider, Subject: c.Subject}
	if c.Email != "" {
		e := c.Email
		id.Email = &e
	}
	if c.Name != "" {
		n := c.Name
		id.DisplayName = &n
	}
	if c.Picture != "" {
		p := c.Picture
		id.PhotoURL = &p
	}
	return id
}

func verifyOIDC(ctx context.Context, cache *jwksCache, idToken string, audiences, issuers []string) (*oidcClaims, error) {
	var claims oidcClaims
	_, err := jwt.ParseWithClaims(idToken, &claims, func(t *jwt.Token) (any, error) {
		if _, ok := t.Method.(*jwt.SigningMethodRSA); !ok {
			return nil, ErrProviderVerify
		}
		kid, _ := t.Header["kid"].(string)
		return cache.key(ctx, kid)
	},
		jwt.WithValidMethods([]string{"RS256"}),
		jwt.WithExpirationRequired(),
	)
	if err != nil {
		return nil, fmt.Errorf("%w: %v", ErrProviderVerify, err)
	}

	// Checked here rather than with jwt.WithAudience because that option takes a
	// single value, and one app legitimately has one Google client id per platform.
	okAudience := false
	for _, want := range audiences {
		for _, got := range claims.Audience {
			if got == want {
				okAudience = true
			}
		}
	}
	if !okAudience {
		return nil, fmt.Errorf("%w: audience %v is not one of %v", ErrProviderVerify, claims.Audience, audiences)
	}

	okIssuer := false
	for _, iss := range issuers {
		if claims.Issuer == iss {
			okIssuer = true
			break
		}
	}
	if !okIssuer {
		return nil, fmt.Errorf("%w: unexpected issuer %q", ErrProviderVerify, claims.Issuer)
	}
	if claims.Subject == "" {
		return nil, fmt.Errorf("%w: missing subject", ErrProviderVerify)
	}
	return &claims, nil
}

// ---- JWKS cache ----

type jwk struct {
	Kid string `json:"kid"`
	Kty string `json:"kty"`
	N   string `json:"n"`
	E   string `json:"e"`
}

type jwksResponse struct {
	Keys []jwk `json:"keys"`
}

type jwksCache struct {
	url  string
	http *http.Client
	ttl  time.Duration

	mu        sync.Mutex
	keys      map[string]*rsa.PublicKey
	fetchedAt time.Time
}

func (c *jwksCache) key(ctx context.Context, kid string) (*rsa.PublicKey, error) {
	c.mu.Lock()
	defer c.mu.Unlock()

	if k, ok := c.keys[kid]; ok && time.Since(c.fetchedAt) < c.ttl {
		return k, nil
	}
	if err := c.refreshLocked(ctx); err != nil {
		return nil, err
	}
	k, ok := c.keys[kid]
	if !ok {
		return nil, fmt.Errorf("%w: unknown key id", ErrProviderVerify)
	}
	return k, nil
}

func (c *jwksCache) refreshLocked(ctx context.Context) error {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, c.url, nil)
	if err != nil {
		return err
	}
	resp, err := c.http.Do(req)
	if err != nil {
		return fmt.Errorf("fetch jwks: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("fetch jwks: status %d", resp.StatusCode)
	}

	var body jwksResponse
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		return fmt.Errorf("decode jwks: %w", err)
	}

	keys := make(map[string]*rsa.PublicKey, len(body.Keys))
	for _, k := range body.Keys {
		if k.Kty != "RSA" {
			continue
		}
		pub, err := k.toRSA()
		if err != nil {
			continue
		}
		keys[k.Kid] = pub
	}
	c.keys = keys
	c.fetchedAt = time.Now()
	return nil
}

func (k jwk) toRSA() (*rsa.PublicKey, error) {
	nBytes, err := base64.RawURLEncoding.DecodeString(k.N)
	if err != nil {
		return nil, err
	}
	eBytes, err := base64.RawURLEncoding.DecodeString(k.E)
	if err != nil {
		return nil, err
	}
	return &rsa.PublicKey{
		N: new(big.Int).SetBytes(nBytes),
		E: int(new(big.Int).SetBytes(eBytes).Int64()),
	}, nil
}
