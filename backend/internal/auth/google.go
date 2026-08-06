package auth

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/tafakkur/backend/internal/config"
	"github.com/tafakkur/backend/internal/user"
)

var (
	ErrOAuthNotConfigured = errors.New("google oauth not configured")
	ErrOAuthStateInvalid  = errors.New("oauth state invalid or already used")
	ErrOAuthStateExpired  = errors.New("oauth state expired")
	ErrOAuthExchange      = errors.New("oauth code exchange failed")
)

const (
	googleAuthEndpoint  = "https://accounts.google.com/o/oauth2/v2/auth"
	googleTokenEndpoint = "https://oauth2.googleapis.com/token"
	googleCallbackPath  = "/v1/auth/google/callback"
)

// GoogleOAuth implements sign-in with Google as a browser round trip driven by
// this server, so neither mobile target has to ship Google's SDK:
//
//	app    → POST /v1/auth/google/start     → {state, auth_url}
//	app    → opens auth_url in the system browser
//	user   → picks an account on accounts.google.com
//	Google → GET  /v1/auth/google/callback  → code exchanged here, session minted
//	app    → POST /v1/auth/google/poll      → access + refresh token
//
// The client secret and the id token never reach the device: the app only ever
// holds an opaque state string, which is single-use and expires in minutes.
type GoogleOAuth struct {
	repo      *Repo
	users     user.Service
	jwt       *Manager
	verifier  *Verifier
	http      *http.Client
	clientID  string
	secret    string
	redirect  string
	ttl       time.Duration
	authURL   string
	tokenURL  string
	pollEvery time.Duration
}

func NewGoogleOAuth(repo *Repo, users user.Service, jwt *Manager, verifier *Verifier, cfg config.Config) *GoogleOAuth {
	return &GoogleOAuth{
		repo:      repo,
		users:     users,
		jwt:       jwt,
		verifier:  verifier,
		http:      &http.Client{Timeout: 15 * time.Second},
		clientID:  cfg.GoogleOAuthClientID,
		secret:    cfg.GoogleOAuthClientSecret,
		redirect:  cfg.BaseURL() + googleCallbackPath,
		ttl:       cfg.OAuthStateTTL,
		authURL:   googleAuthEndpoint,
		tokenURL:  googleTokenEndpoint,
		pollEvery: 2 * time.Second,
	}
}

// SetEndpoints points the flow at a stand-in Google. Tests only.
func (g *GoogleOAuth) SetEndpoints(authURL, tokenURL, redirect string) {
	g.authURL, g.tokenURL, g.redirect = authURL, tokenURL, redirect
}

func (g *GoogleOAuth) Enabled() bool {
	return g != nil && g.clientID != "" && g.secret != ""
}

// Start records a pending sign-in and returns the URL the app should open.
func (g *GoogleOAuth) Start(ctx context.Context) (GoogleStartResponse, error) {
	if !g.Enabled() {
		return GoogleStartResponse{}, ErrOAuthNotConfigured
	}
	state, err := randomURLSafe(32)
	if err != nil {
		return GoogleStartResponse{}, err
	}
	verifier, err := randomURLSafe(32)
	if err != nil {
		return GoogleStartResponse{}, err
	}
	if err := g.repo.CreateOAuthState(ctx, state, "google", verifier, time.Now().Add(g.ttl)); err != nil {
		return GoogleStartResponse{}, err
	}

	challenge := sha256.Sum256([]byte(verifier))
	q := url.Values{
		"client_id":             {g.clientID},
		"redirect_uri":          {g.redirect},
		"response_type":         {"code"},
		"scope":                 {"openid email profile"},
		"state":                 {state},
		"code_challenge":        {base64.RawURLEncoding.EncodeToString(challenge[:])},
		"code_challenge_method": {"S256"},
		// Without this a signed-in browser skips the chooser, so a user who wants
		// a different account has no way to reach one.
		"prompt": {"select_account"},
	}
	return GoogleStartResponse{
		State:          state,
		AuthURL:        g.authURL + "?" + q.Encode(),
		PollIntervalMS: int64(g.pollEvery / time.Millisecond),
		ExpiresIn:      int64(g.ttl.Seconds()),
	}, nil
}

// Callback is Google's redirect target: it swaps the authorization code for an
// id token, upserts the user, and marks the state approved for the polling app.
func (g *GoogleOAuth) Callback(ctx context.Context, state, code string) error {
	if !g.Enabled() {
		return ErrOAuthNotConfigured
	}
	st, err := g.repo.GetOAuthState(ctx, state)
	if err != nil {
		return err
	}
	if st.ConsumedAt != nil || st.ApprovedAt != nil {
		return ErrOAuthStateInvalid
	}
	if time.Now().After(st.ExpiresAt) {
		return ErrOAuthStateExpired
	}

	idToken, err := g.exchange(ctx, code, st.CodeVerifier)
	if err != nil {
		return err
	}
	// Verified against Google's JWKS like any other id token — the exchange
	// proves it came from Google, the signature proves it was not altered.
	ident, err := g.verifier.VerifyGoogle(ctx, idToken)
	if err != nil {
		return err
	}
	acc, _, err := g.users.UpsertByProvider(ctx, ident)
	if err != nil {
		return err
	}
	return g.repo.ApproveOAuthState(ctx, state, acc.User.ID)
}

// Poll hands the session to the app once the browser leg is done. Pending is not
// an error: the app calls this every couple of seconds while the user picks an
// account.
func (g *GoogleOAuth) Poll(ctx context.Context, state string) (GooglePollResponse, error) {
	if !g.Enabled() {
		return GooglePollResponse{}, ErrOAuthNotConfigured
	}
	st, err := g.repo.GetOAuthState(ctx, state)
	if err != nil {
		return GooglePollResponse{}, err
	}
	if st.ConsumedAt != nil {
		return GooglePollResponse{}, ErrOAuthStateInvalid
	}
	if time.Now().After(st.ExpiresAt) {
		return GooglePollResponse{}, ErrOAuthStateExpired
	}
	if st.ApprovedAt == nil || st.UserID == nil {
		return GooglePollResponse{Status: "pending"}, nil
	}
	// Conditional on consumed_at IS NULL, so two concurrent polls cannot both
	// mint a session from one browser sign-in.
	ok, err := g.repo.ConsumeOAuthState(ctx, state)
	if err != nil {
		return GooglePollResponse{}, err
	}
	if !ok {
		return GooglePollResponse{}, ErrOAuthStateInvalid
	}

	acc, err := g.users.GetAccount(ctx, *st.UserID)
	if err != nil {
		return GooglePollResponse{}, err
	}
	sess, err := issueSession(g.jwt, acc)
	if err != nil {
		return GooglePollResponse{}, err
	}
	return GooglePollResponse{Status: "ready", Session: &sess}, nil
}

type googleTokenResponse struct {
	IDToken string `json:"id_token"`
	Error   string `json:"error"`
}

func (g *GoogleOAuth) exchange(ctx context.Context, code, codeVerifier string) (string, error) {
	form := url.Values{
		"code":          {code},
		"client_id":     {g.clientID},
		"client_secret": {g.secret},
		"redirect_uri":  {g.redirect},
		"grant_type":    {"authorization_code"},
		"code_verifier": {codeVerifier},
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, g.tokenURL, strings.NewReader(form.Encode()))
	if err != nil {
		return "", err
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")

	resp, err := g.http.Do(req)
	if err != nil {
		return "", fmt.Errorf("%w: %v", ErrOAuthExchange, err)
	}
	defer resp.Body.Close()

	var body googleTokenResponse
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		return "", fmt.Errorf("%w: decode: %v", ErrOAuthExchange, err)
	}
	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("%w: status %d (%s)", ErrOAuthExchange, resp.StatusCode, body.Error)
	}
	if body.IDToken == "" {
		return "", fmt.Errorf("%w: no id_token in response", ErrOAuthExchange)
	}
	return body.IDToken, nil
}

func randomURLSafe(n int) (string, error) {
	b := make([]byte, n)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	return base64.RawURLEncoding.EncodeToString(b), nil
}

// issueSession is the one place access + refresh tokens and the profile payload
// are assembled, shared by every provider.
func issueSession(jwt *Manager, acc user.Account) (SessionResponse, error) {
	access, err := jwt.IssueAccess(acc.User.ID)
	if err != nil {
		return SessionResponse{}, err
	}
	refresh, err := jwt.IssueRefresh(acc.User.ID)
	if err != nil {
		return SessionResponse{}, err
	}
	return SessionResponse{
		AccessToken:     access,
		RefreshToken:    refresh,
		TokenType:       "Bearer",
		ExpiresIn:       int64(jwt.AccessTTL().Seconds()),
		User:            user.ToUserProfileDTO(acc),
		NeedsOnboarding: !acc.Profile.OnboardingDone,
	}, nil
}
