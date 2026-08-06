// Package integration's auth-flow suite drives the real chi router over HTTP
// against a real Postgres, with only the two things that live outside the
// process faked: Google's OAuth/JWKS endpoints and the SMS sender.
//
// It needs a reachable Postgres and skips otherwise. Every run gets its own
// schema inside the existing database, so it never needs CREATEDB rights and
// never touches the dev data:
//
//	go test ./internal/integration/ -run TestAuth -v
//
// Point it elsewhere with TEST_DATABASE_URL or DATABASE_URL.
package integration

import (
	"context"
	"crypto/rand"
	"crypto/rsa"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"math/big"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"regexp"
	"strings"
	"testing"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/tafakkur/backend/internal/auth"
	"github.com/tafakkur/backend/internal/coach"
	"github.com/tafakkur/backend/internal/config"
	"github.com/tafakkur/backend/internal/feed"
	"github.com/tafakkur/backend/internal/gamify"
	"github.com/tafakkur/backend/internal/gratitude"
	"github.com/tafakkur/backend/internal/learn"
	"github.com/tafakkur/backend/internal/media"
	"github.com/tafakkur/backend/internal/platform"
	"github.com/tafakkur/backend/internal/prayer"
	"github.com/tafakkur/backend/internal/quran"
	"github.com/tafakkur/backend/internal/ratelimit"
	"github.com/tafakkur/backend/internal/server"
	"github.com/tafakkur/backend/internal/telegram"
	"github.com/tafakkur/backend/internal/tracker"
	"github.com/tafakkur/backend/internal/user"
)

const (
	testWebClientID = "web-client.apps.googleusercontent.com"
	testIOSClientID = "ios-client.apps.googleusercontent.com"
	testGoogleSub   = "google-sub-8891"
	testGoogleEmail = "aisha@gmail.com"
)

// ---------------------------------------------------------------- test harness

// testDB gives each run its own schema in the configured database. No CREATEDB
// right is required, and the dev tables are never in scope.
func testDB(t *testing.T) *pgxpool.Pool {
	t.Helper()

	dsn := firstNonEmpty(
		os.Getenv("TEST_DATABASE_URL"),
		os.Getenv("DATABASE_URL"),
		"postgres://tafakkur:tafakkur@localhost:5432/tafakkur?sslmode=disable",
	)
	ctx := context.Background()

	admin, err := pgxpool.New(ctx, dsn)
	if err != nil {
		t.Skipf("no postgres at %s: %v", dsn, err)
	}
	if err := admin.Ping(ctx); err != nil {
		admin.Close()
		t.Skipf("no postgres at %s: %v", dsn, err)
	}

	schema := fmt.Sprintf("authtest_%d", time.Now().UnixNano())
	if _, err := admin.Exec(ctx, "CREATE SCHEMA "+schema); err != nil {
		admin.Close()
		t.Fatalf("create schema: %v", err)
	}

	sep := "?"
	if strings.Contains(dsn, "?") {
		sep = "&"
	}
	pool, err := pgxpool.New(ctx, dsn+sep+"search_path="+schema)
	if err != nil {
		t.Fatalf("pool: %v", err)
	}
	if err := platform.RunMigrations(ctx, pool, "../../migrations"); err != nil {
		t.Fatalf("migrate: %v", err)
	}

	t.Cleanup(func() {
		pool.Close()
		if _, err := admin.Exec(context.Background(), "DROP SCHEMA "+schema+" CASCADE"); err != nil {
			t.Logf("drop schema %s: %v", schema, err)
		}
		admin.Close()
	})
	return pool
}

// smsSpy stands in for Twilio and keeps the code, which is the only way a test
// can complete an OTP round trip.
type smsSpy struct{ lastBody string }

func (s *smsSpy) Send(_ context.Context, _, body string) error {
	s.lastBody = body
	return nil
}

var sixDigits = regexp.MustCompile(`\d{6}`)

func (s *smsSpy) code(t *testing.T) string {
	t.Helper()
	code := sixDigits.FindString(s.lastBody)
	if code == "" {
		t.Fatalf("no 6-digit code in SMS body %q", s.lastBody)
	}
	return code
}

// fakeGoogle serves the two Google endpoints the flow depends on: the JWKS the
// id token is verified against, and the token endpoint the authorization code is
// exchanged at. It asserts the exchange request is well formed, so a malformed
// one fails the test rather than silently working against a permissive stub.
type fakeGoogle struct {
	*httptest.Server
	t   *testing.T
	key *rsa.PrivateKey

	audience string // aud stamped into the minted id token
	sub      string
	email    string
	name     string

	lastForm url.Values
}

func newFakeGoogle(t *testing.T) *fakeGoogle {
	t.Helper()
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatalf("generate key: %v", err)
	}
	g := &fakeGoogle{
		t: t, key: key,
		audience: testWebClientID,
		sub:      testGoogleSub,
		email:    testGoogleEmail,
		name:     "Aisha",
	}

	mux := http.NewServeMux()
	mux.HandleFunc("/certs", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]any{"keys": []any{map[string]string{
			"kid": "test-key",
			"kty": "RSA",
			"n":   base64.RawURLEncoding.EncodeToString(key.N.Bytes()),
			"e":   base64.RawURLEncoding.EncodeToString(big.NewInt(int64(key.E)).Bytes()),
		}}})
	})
	mux.HandleFunc("/token", func(w http.ResponseWriter, r *http.Request) {
		if err := r.ParseForm(); err != nil {
			http.Error(w, "bad form", http.StatusBadRequest)
			return
		}
		g.lastForm = r.PostForm
		for _, want := range []string{"code", "client_id", "client_secret", "redirect_uri", "grant_type", "code_verifier"} {
			if r.PostForm.Get(want) == "" {
				w.WriteHeader(http.StatusBadRequest)
				_ = json.NewEncoder(w).Encode(map[string]string{"error": "missing " + want})
				return
			}
		}
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]string{"id_token": g.mintIDToken()})
	})

	g.Server = httptest.NewServer(mux)
	t.Cleanup(g.Close)
	return g
}

func (g *fakeGoogle) mintIDToken() string {
	g.t.Helper()
	tok := jwt.NewWithClaims(jwt.SigningMethodRS256, jwt.MapClaims{
		"iss":     "https://accounts.google.com",
		"aud":     g.audience,
		"sub":     g.sub,
		"email":   g.email,
		"name":    g.name,
		"picture": "https://lh3.googleusercontent.com/a/test",
		"iat":     time.Now().Unix(),
		"exp":     time.Now().Add(time.Hour).Unix(),
	})
	tok.Header["kid"] = "test-key"
	signed, err := tok.SignedString(g.key)
	if err != nil {
		g.t.Fatalf("sign id token: %v", err)
	}
	return signed
}

// app is the whole backend behind one HTTP client.
type app struct {
	*httptest.Server
	t      *testing.T
	sms    *smsSpy
	google *fakeGoogle
}

func newApp(t *testing.T) *app {
	t.Helper()
	pool := testDB(t)
	ctx := context.Background()

	google := newFakeGoogle(t)
	sms := &smsSpy{}

	cfg := config.Config{
		Env:                     "test",
		HTTPPort:                "8080",
		JWTSecret:               "test-secret-that-is-long-enough-to-be-a-key",
		JWTIssuer:               "tafakkur",
		AccessTokenTTL:          15 * time.Minute,
		RefreshTokenTTL:         720 * time.Hour,
		OTPTTL:                  5 * time.Minute,
		OTPMaxAttempts:          5,
		OAuthStateTTL:           10 * time.Minute,
		GoogleClientIDs:         []string{testIOSClientID},
		GoogleOAuthClientID:     testWebClientID,
		GoogleOAuthClientSecret: "test-client-secret",
		// High enough that the suite's own request volume never trips it.
		RateAuthPerMin: 10000, RateCoachPerMin: 10000, RatePostPerMin: 10000,
	}

	gam := gamify.NewService(gamify.NewRepo(pool))
	users := user.NewService(user.NewRepo(pool), gam)
	mediaSvc, err := media.NewService(ctx, cfg)
	if err != nil {
		t.Fatalf("media: %v", err)
	}

	jwtMgr := auth.NewManager(cfg)
	verifier := auth.NewVerifier(cfg)
	verifier.SetJWKSURLs(google.URL+"/certs", "")

	authSvc := auth.NewService(users, jwtMgr, verifier, auth.NewOTP(auth.NewRepo(pool), cfg, sms))
	oauth := auth.NewGoogleOAuth(auth.NewRepo(pool), users, jwtMgr, verifier, cfg)
	oauth.SetEndpoints(google.URL+"/authorize", google.URL+"/token", "http://localhost:8080/v1/auth/google/callback")

	tgBot := telegram.NewBot(telegram.NewClient(""), telegram.NewRepo(pool), nil)
	tgLogin := auth.NewTelegramLogin(auth.NewRepo(pool), users, jwtMgr, tgBot, cfg)

	trackerSvc := tracker.NewService(tracker.NewRepo(pool))
	router := server.NewRouter(server.Deps{
		Config:    cfg,
		Verifier:  jwtMgr,
		Ready:     pool.Ping,
		Auth:      auth.NewHandler(authSvc, tgLogin, oauth, ratelimit.NewPerMinute(cfg.RateAuthPerMin)),
		User:      user.NewHandler(users, mediaSvc),
		Prayer:    prayer.NewHandler(prayer.NewService(users, gam, trackerSvc, prayer.NewCompletionRepo(pool))),
		Coach:     coach.NewHandler(coach.NewService(coach.NewRepo(pool), coach.NewClient(cfg)), ratelimit.NewPerMinute(cfg.RateCoachPerMin)),
		Feed:      feed.NewHandler(feed.NewService(feed.NewRepo(pool), users, gam), ratelimit.NewPerMinute(cfg.RatePostPerMin)),
		Learn:     learn.NewHandler(learn.NewService(learn.NewRepo(pool)), gam),
		Tracker:   tracker.NewHandler(trackerSvc, gam),
		Gratitude: gratitude.NewHandler(gratitude.NewService(gratitude.NewRepo(pool)), gam, trackerSvc),
		Quran:     quran.NewHandler(quran.NewService(quran.NewRepo(pool)), gam),
	})

	srv := httptest.NewServer(router)
	t.Cleanup(srv.Close)
	return &app{Server: srv, t: t, sms: sms, google: google}
}

// do sends a request and decodes the JSON body into out (may be nil).
func (a *app) do(method, path, token string, body, out any) int {
	a.t.Helper()

	var reader *strings.Reader
	if body != nil {
		raw, err := json.Marshal(body)
		if err != nil {
			a.t.Fatalf("marshal body: %v", err)
		}
		reader = strings.NewReader(string(raw))
	} else {
		reader = strings.NewReader("")
	}

	req, err := http.NewRequest(method, a.URL+path, reader)
	if err != nil {
		a.t.Fatalf("new request: %v", err)
	}
	req.Header.Set("Content-Type", "application/json")
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}

	resp, err := a.Client().Do(req)
	if err != nil {
		a.t.Fatalf("%s %s: %v", method, path, err)
	}
	defer resp.Body.Close()

	if out != nil {
		if err := json.NewDecoder(resp.Body).Decode(out); err != nil {
			a.t.Fatalf("%s %s: decode response: %v", method, path, err)
		}
	}
	return resp.StatusCode
}

// signInWithGoogle walks the whole browser flow: start, Google's redirect back
// into the callback, then the poll the app makes while the user is in Safari.
func (a *app) signInWithGoogle() sessionResponse {
	a.t.Helper()

	var start struct {
		State   string `json:"state"`
		AuthURL string `json:"auth_url"`
	}
	if code := a.do(http.MethodPost, "/v1/auth/google/start", "", nil, &start); code != http.StatusOK {
		a.t.Fatalf("google/start: status %d", code)
	}
	if start.State == "" || !strings.Contains(start.AuthURL, "code_challenge=") {
		a.t.Fatalf("google/start returned %+v", start)
	}

	// Before the browser comes back, the app is told to keep waiting.
	var pending struct {
		Status string `json:"status"`
	}
	if code := a.do(http.MethodPost, "/v1/auth/google/poll", "", map[string]string{"state": start.State}, &pending); code != http.StatusOK {
		a.t.Fatalf("google/poll (pending): status %d", code)
	}
	if pending.Status != "pending" {
		a.t.Fatalf("poll before callback = %q, want pending", pending.Status)
	}

	// Google redirects the user's browser here with the authorization code.
	cb, err := a.Client().Get(a.URL + "/v1/auth/google/callback?state=" + start.State + "&code=auth-code-xyz")
	if err != nil {
		a.t.Fatalf("callback: %v", err)
	}
	defer cb.Body.Close()
	if cb.StatusCode != http.StatusOK {
		a.t.Fatalf("callback: status %d", cb.StatusCode)
	}

	var ready struct {
		Status  string          `json:"status"`
		Session sessionResponse `json:"session"`
	}
	if code := a.do(http.MethodPost, "/v1/auth/google/poll", "", map[string]string{"state": start.State}, &ready); code != http.StatusOK {
		a.t.Fatalf("google/poll (ready): status %d", code)
	}
	if ready.Status != "ready" {
		a.t.Fatalf("poll after callback = %q, want ready", ready.Status)
	}
	return ready.Session
}

// signInWithPhone runs the OTP sign-in, reading the code out of the SMS spy.
func (a *app) signInWithPhone(phone string) sessionResponse {
	a.t.Helper()

	var start struct {
		VerificationID string `json:"verification_id"`
	}
	if code := a.do(http.MethodPost, "/v1/auth/otp/start", "", map[string]string{"phone": phone}, &start); code != http.StatusOK {
		a.t.Fatalf("otp/start: status %d", code)
	}

	var sess sessionResponse
	status := a.do(http.MethodPost, "/v1/auth/session", "", map[string]string{
		"provider":        "phone",
		"verification_id": start.VerificationID,
		"otp_code":        a.sms.code(a.t),
	}, &sess)
	if status != http.StatusOK {
		a.t.Fatalf("auth/session (phone): status %d", status)
	}
	return sess
}

type profile struct {
	UID        string  `json:"uid"`
	Provider   string  `json:"provider"`
	Email      *string `json:"email"`
	Phone      *string `json:"phone"`
	NeedsPhone bool    `json:"needs_phone"`
	NeedsEmail bool    `json:"needs_email"`
}

type sessionResponse struct {
	AccessToken string  `json:"access_token"`
	User        profile `json:"user"`
}

// ---------------------------------------------------------------------- tests

// A Google sign-in stores what Google knows, then the profile asks for the one
// thing it does not: the phone number.
func TestAuthGoogleSignInThenAddPhone(t *testing.T) {
	a := newApp(t)

	sess := a.signInWithGoogle()

	if sess.AccessToken == "" {
		t.Fatal("no access token issued")
	}
	if sess.User.Provider != "google" {
		t.Fatalf("provider = %q, want google", sess.User.Provider)
	}
	if sess.User.Email == nil || *sess.User.Email != testGoogleEmail {
		t.Fatalf("email = %v, want %s (Google's data was not saved)", sess.User.Email, testGoogleEmail)
	}
	if !sess.User.NeedsPhone || sess.User.NeedsEmail {
		t.Fatalf("needs_phone=%v needs_email=%v, want true/false", sess.User.NeedsPhone, sess.User.NeedsEmail)
	}
	// PKCE and the client secret both have to reach Google, or the real exchange
	// would fail where the stub happily would not.
	if a.google.lastForm.Get("client_secret") != "test-client-secret" {
		t.Fatalf("exchange sent client_secret %q", a.google.lastForm.Get("client_secret"))
	}

	// --- the profile screen's ask: verify a number and attach it ---
	const phone = "+998901112233"
	var otp struct {
		VerificationID string `json:"verification_id"`
	}
	if code := a.do(http.MethodPost, "/v1/me/phone/start", sess.AccessToken, map[string]string{"phone": phone}, &otp); code != http.StatusOK {
		t.Fatalf("me/phone/start: status %d", code)
	}

	var wrong map[string]any
	if code := a.do(http.MethodPost, "/v1/me/phone/verify", sess.AccessToken, map[string]string{
		"verification_id": otp.VerificationID,
		"code":            "000000",
	}, &wrong); code != http.StatusUnauthorized {
		t.Fatalf("wrong code: status %d, want 401", code)
	}

	// A fresh challenge — the wrong attempt above consumed nothing, but starting
	// again is what the app does after showing "wrong code".
	if code := a.do(http.MethodPost, "/v1/me/phone/start", sess.AccessToken, map[string]string{"phone": phone}, &otp); code != http.StatusOK {
		t.Fatalf("me/phone/start (retry): status %d", code)
	}
	var updated profile
	if code := a.do(http.MethodPost, "/v1/me/phone/verify", sess.AccessToken, map[string]string{
		"verification_id": otp.VerificationID,
		"code":            a.sms.code(t),
	}, &updated); code != http.StatusOK {
		t.Fatalf("me/phone/verify: status %d", code)
	}
	if updated.Phone == nil || *updated.Phone != phone {
		t.Fatalf("phone = %v, want %s", updated.Phone, phone)
	}
	if updated.NeedsPhone {
		t.Fatal("needs_phone still true after attaching a number")
	}

	// It survives the round trip to the database, not just the response.
	var me struct {
		User profile `json:"user"`
	}
	if code := a.do(http.MethodGet, "/v1/me", sess.AccessToken, nil, &me); code != http.StatusOK {
		t.Fatalf("GET /v1/me: status %d", code)
	}
	if me.User.Phone == nil || *me.User.Phone != phone || me.User.Email == nil {
		t.Fatalf("GET /v1/me returned %+v", me.User)
	}

	// Signing in again is the same account, not a second one.
	again := a.signInWithGoogle()
	if again.User.UID != sess.User.UID {
		t.Fatalf("second google sign-in made a new user (%s vs %s)", again.User.UID, sess.User.UID)
	}
	if again.User.Phone == nil {
		t.Fatal("attached phone lost on re-login")
	}
}

// The mirror image: a phone sign-in stores the number, and the profile asks for
// the email.
func TestAuthPhoneSignInThenAddEmail(t *testing.T) {
	a := newApp(t)

	const phone = "+998907778899"
	sess := a.signInWithPhone(phone)

	if sess.User.Provider != "phone" {
		t.Fatalf("provider = %q, want phone", sess.User.Provider)
	}
	if sess.User.Phone == nil || *sess.User.Phone != phone {
		t.Fatalf("phone = %v, want %s (the number was not saved)", sess.User.Phone, phone)
	}
	if !sess.User.NeedsEmail || sess.User.NeedsPhone {
		t.Fatalf("needs_email=%v needs_phone=%v, want true/false", sess.User.NeedsEmail, sess.User.NeedsPhone)
	}

	var bad map[string]any
	if code := a.do(http.MethodPut, "/v1/me/email", sess.AccessToken, map[string]string{"email": "not-an-email"}, &bad); code != http.StatusUnprocessableEntity && code != http.StatusBadRequest {
		t.Fatalf("invalid email: status %d, want a 4xx rejection", code)
	}

	var updated profile
	if code := a.do(http.MethodPut, "/v1/me/email", sess.AccessToken, map[string]string{"email": "Aisha.R@Example.COM"}, &updated); code != http.StatusOK {
		t.Fatalf("PUT /v1/me/email: status %d", code)
	}
	if updated.Email == nil || *updated.Email != "aisha.r@example.com" {
		t.Fatalf("email = %v, want the lowercased address", updated.Email)
	}
	if updated.NeedsEmail {
		t.Fatal("needs_email still true after saving an address")
	}

	var me struct {
		User profile `json:"user"`
	}
	if code := a.do(http.MethodGet, "/v1/me", sess.AccessToken, nil, &me); code != http.StatusOK {
		t.Fatalf("GET /v1/me: status %d", code)
	}
	if me.User.Email == nil || *me.User.Email != "aisha.r@example.com" {
		t.Fatalf("GET /v1/me returned %+v", me.User)
	}
}

// Two accounts cannot end up owning one contact detail.
func TestAuthContactsCannotBeStolen(t *testing.T) {
	a := newApp(t)

	googleSess := a.signInWithGoogle()
	phoneSess := a.signInWithPhone("+998905554433")

	// The phone user tries to claim the Google user's address.
	var conflict map[string]any
	if code := a.do(http.MethodPut, "/v1/me/email", phoneSess.AccessToken,
		map[string]string{"email": testGoogleEmail}, &conflict); code != http.StatusConflict {
		t.Fatalf("taken email: status %d, want 409", code)
	}

	// The Google user tries to claim the phone user's number — refused before an
	// SMS is ever sent.
	if code := a.do(http.MethodPost, "/v1/me/phone/start", googleSess.AccessToken,
		map[string]string{"phone": "+998905554433"}, &conflict); code != http.StatusConflict {
		t.Fatalf("taken phone: status %d, want 409", code)
	}
}

// A state string is a bearer credential for one sign-in only.
func TestAuthGoogleStateIsSingleUse(t *testing.T) {
	a := newApp(t)

	var start struct {
		State string `json:"state"`
	}
	a.do(http.MethodPost, "/v1/auth/google/start", "", nil, &start)

	cb, err := a.Client().Get(a.URL + "/v1/auth/google/callback?state=" + start.State + "&code=auth-code-1")
	if err != nil {
		t.Fatalf("callback: %v", err)
	}
	cb.Body.Close()

	var first struct {
		Status string `json:"status"`
	}
	a.do(http.MethodPost, "/v1/auth/google/poll", "", map[string]string{"state": start.State}, &first)
	if first.Status != "ready" {
		t.Fatalf("first poll = %q, want ready", first.Status)
	}

	var second map[string]any
	if code := a.do(http.MethodPost, "/v1/auth/google/poll", "", map[string]string{"state": start.State}, &second); code != http.StatusUnauthorized {
		t.Fatalf("replayed state: status %d, want 401", code)
	}
	var unknown map[string]any
	if code := a.do(http.MethodPost, "/v1/auth/google/poll", "", map[string]string{"state": "never-issued"}, &unknown); code != http.StatusUnauthorized {
		t.Fatalf("unknown state: status %d, want 401", code)
	}
}

// The regression that made the button fail in the first place: an id token whose
// `aud` is the iOS client id must be accepted even though the browser flow uses
// the web client id, and a token minted for someone else's app must not be.
func TestAuthGoogleAcceptsEveryConfiguredClientID(t *testing.T) {
	a := newApp(t)

	a.google.audience = testIOSClientID
	a.google.sub = "google-sub-ios"
	a.google.email = "ios-user@gmail.com"

	var sess sessionResponse
	if code := a.do(http.MethodPost, "/v1/auth/session", "", map[string]string{
		"provider": "google",
		"id_token": a.google.mintIDToken(),
	}, &sess); code != http.StatusOK {
		t.Fatalf("session with the iOS client id: status %d, want 200", code)
	}
	if sess.User.Email == nil || *sess.User.Email != "ios-user@gmail.com" {
		t.Fatalf("email = %v", sess.User.Email)
	}

	a.google.audience = "some-other-app.apps.googleusercontent.com"
	var rejected map[string]any
	if code := a.do(http.MethodPost, "/v1/auth/session", "", map[string]string{
		"provider": "google",
		"id_token": a.google.mintIDToken(),
	}, &rejected); code != http.StatusUnauthorized {
		t.Fatalf("foreign audience: status %d, want 401", code)
	}
}

func firstNonEmpty(vals ...string) string {
	for _, v := range vals {
		if v != "" {
			return v
		}
	}
	return ""
}
