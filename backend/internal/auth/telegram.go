package auth

import (
	"context"
	"crypto/rand"
	"errors"
	"fmt"
	"math/big"
	"strconv"
	"strings"
	"time"

	"github.com/tafakkur/backend/internal/config"
	"github.com/tafakkur/backend/internal/telegram"
	"github.com/tafakkur/backend/internal/user"
)

var (
	ErrTGNotConfigured = errors.New("telegram login not configured")
	ErrTGCodeInvalid   = errors.New("confirmation code invalid")
	ErrTGCodeExpired   = errors.New("confirmation code expired")
)

// TelegramLogin implements the confirmation-code sign-in:
//
//	app  → GET  /v1/auth/telegram/bot     → {bot_username, deep_link}
//	user → opens the bot, presses Start, taps "share my number"
//	bot  → verifies the contact is the sender's own, replies with a 6-digit code
//	app  → POST /v1/auth/telegram/verify  → access + refresh token
//
// The phone number never passes through the app and no SMS is sent: Telegram
// itself vouches for it. The code is single-use and short-lived, and the
// endpoint sits behind the auth rate limiter, so guessing it is not practical.
type TelegramLogin struct {
	repo  *Repo
	users user.Service
	jwt   *Manager
	bot   *telegram.Bot
	ttl   time.Duration
}

func NewTelegramLogin(repo *Repo, users user.Service, jwt *Manager, bot *telegram.Bot, cfg config.Config) *TelegramLogin {
	return &TelegramLogin{repo: repo, users: users, jwt: jwt, bot: bot, ttl: cfg.TelegramCodeTTL}
}

func (t *TelegramLogin) Enabled() bool { return t != nil && t.bot.Enabled() }

func (t *TelegramLogin) CodeTTL() time.Duration { return t.ttl }

// BotInfo tells the app which bot to open. Resolved from getMe at boot, so the
// bot can be swapped by changing one env var.
func (t *TelegramLogin) BotInfo() (TelegramBotResponse, error) {
	if !t.Enabled() {
		return TelegramBotResponse{}, ErrTGNotConfigured
	}
	name := t.bot.Username()
	return TelegramBotResponse{
		BotUsername: name,
		DeepLink:    fmt.Sprintf("https://t.me/%s?start=login", name),
		CodeLength:  codeLength,
	}, nil
}

// IssueCode is the telegram.ContactHandler: the user shared their own number, so
// mint the code they will type into the app.
func (t *TelegramLogin) IssueCode(ctx context.Context, from telegram.User, contact telegram.Contact) (string, error) {
	code, err := newConfirmCode()
	if err != nil {
		return "", err
	}
	phone := normalisePhone(contact.PhoneNumber)
	// Replaces any earlier unused code for this account, so only the newest code
	// the user was shown can ever be redeemed.
	if err := t.repo.ReplaceAuthCode(ctx, code, from, phone, time.Now().Add(t.ttl)); err != nil {
		return "", err
	}
	return code, nil
}

// Verify redeems a code exactly once and issues the session.
func (t *TelegramLogin) Verify(ctx context.Context, code string) (SessionResponse, error) {
	if !t.Enabled() {
		return SessionResponse{}, ErrTGNotConfigured
	}
	ac, err := t.repo.GetAuthCode(ctx, strings.TrimSpace(code))
	if err != nil {
		return SessionResponse{}, err
	}
	if ac.ConsumedAt != nil {
		return SessionResponse{}, ErrTGCodeInvalid
	}
	if time.Now().After(ac.ExpiresAt) {
		return SessionResponse{}, ErrTGCodeExpired
	}
	// Conditional on consumed_at IS NULL, so two concurrent requests can never
	// both mint a session from one code.
	ok, err := t.repo.ConsumeAuthCode(ctx, ac.Code)
	if err != nil {
		return SessionResponse{}, err
	}
	if !ok {
		return SessionResponse{}, ErrTGCodeInvalid
	}

	phone := ac.Phone
	ident := user.ProviderIdentity{
		Provider: "telegram",
		Subject:  strconv.FormatInt(ac.TelegramID, 10),
		Phone:    &phone,
	}
	if name := displayName(ac); name != "" {
		ident.DisplayName = &name
	}

	acc, _, err := t.users.UpsertByProvider(ctx, ident)
	if err != nil {
		return SessionResponse{}, err
	}
	access, err := t.jwt.IssueAccess(acc.User.ID)
	if err != nil {
		return SessionResponse{}, err
	}
	refresh, err := t.jwt.IssueRefresh(acc.User.ID)
	if err != nil {
		return SessionResponse{}, err
	}
	return SessionResponse{
		AccessToken:     access,
		RefreshToken:    refresh,
		TokenType:       "Bearer",
		ExpiresIn:       int64(t.jwt.AccessTTL().Seconds()),
		User:            user.ToUserProfileDTO(acc),
		NeedsOnboarding: !acc.Profile.OnboardingDone,
	}, nil
}

func displayName(ac authCode) string {
	name := strings.TrimSpace(ac.FirstName + " " + ac.LastName)
	if name == "" {
		name = ac.Username
	}
	return name
}

// normalisePhone puts Telegram's number into the E.164 form the users table and
// every other provider already store ("998901234567" → "+998901234567").
func normalisePhone(p string) string {
	p = strings.TrimSpace(p)
	if p != "" && !strings.HasPrefix(p, "+") {
		return "+" + p
	}
	return p
}

const codeLength = 6

func newConfirmCode() (string, error) {
	const digits = "0123456789"
	b := make([]byte, codeLength)
	for i := range b {
		// crypto/rand, not math/rand: this code is a bearer credential for the
		// few minutes it lives, and a predictable one is a free account takeover.
		n, err := rand.Int(rand.Reader, big.NewInt(int64(len(digits))))
		if err != nil {
			return "", err
		}
		b[i] = digits[n.Int64()]
	}
	return string(b), nil
}
