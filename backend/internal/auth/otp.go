package auth

import (
	"context"
	"crypto/rand"
	"errors"
	"fmt"
	"log/slog"
	"math/big"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/google/uuid"
	"golang.org/x/crypto/bcrypt"

	"github.com/tafakkur/backend/internal/config"
)

var (
	ErrOTPInvalid         = errors.New("invalid otp code")
	ErrOTPExpired         = errors.New("otp challenge expired")
	ErrOTPTooManyAttempts = errors.New("too many otp attempts")
	ErrOTPNotFound        = errors.New("otp challenge not found")
)

// SMSSender delivers the OTP code out of band.
type SMSSender interface {
	Send(ctx context.Context, to, body string) error
}

type OTP struct {
	repo        *Repo
	ttl         time.Duration
	maxAttempts int
	sender      SMSSender
}

func NewOTP(repo *Repo, cfg config.Config, sender SMSSender) *OTP {
	return &OTP{repo: repo, ttl: cfg.OTPTTL, maxAttempts: cfg.OTPMaxAttempts, sender: sender}
}

func genCode() (string, error) {
	n, err := rand.Int(rand.Reader, big.NewInt(1_000_000))
	if err != nil {
		return "", err
	}
	return fmt.Sprintf("%06d", n.Int64()), nil
}

func (o *OTP) Start(ctx context.Context, phone string) (uuid.UUID, error) {
	phone = strings.TrimSpace(phone)
	code, err := genCode()
	if err != nil {
		return uuid.Nil, err
	}
	hash, err := bcrypt.GenerateFromPassword([]byte(code), bcrypt.DefaultCost)
	if err != nil {
		return uuid.Nil, err
	}
	id, err := o.repo.CreateChallenge(ctx, phone, string(hash), time.Now().Add(o.ttl))
	if err != nil {
		return uuid.Nil, err
	}
	if err := o.sender.Send(ctx, phone, "Your TafakkurAI code is "+code); err != nil {
		return uuid.Nil, err
	}
	return id, nil
}

func (o *OTP) Verify(ctx context.Context, id uuid.UUID, code string) (string, error) {
	ch, err := o.repo.GetChallenge(ctx, id)
	if err != nil {
		return "", err
	}
	if time.Now().After(ch.ExpiresAt) {
		_ = o.repo.DeleteChallenge(ctx, id)
		return "", ErrOTPExpired
	}
	if ch.Attempts >= o.maxAttempts {
		_ = o.repo.DeleteChallenge(ctx, id)
		return "", ErrOTPTooManyAttempts
	}
	if bcrypt.CompareHashAndPassword([]byte(ch.CodeHash), []byte(code)) != nil {
		_ = o.repo.IncrAttempts(ctx, id)
		return "", ErrOTPInvalid
	}
	_ = o.repo.DeleteChallenge(ctx, id)
	return ch.Phone, nil
}

// NewSMSSender returns a Twilio sender when configured, else a dev logger that
// prints the code (never use the logger in production).
func NewSMSSender(cfg config.Config) SMSSender {
	if cfg.TwilioAccountSID != "" && cfg.TwilioAuthToken != "" {
		return &twilioSender{cfg: cfg, http: &http.Client{Timeout: 10 * time.Second}}
	}
	slog.Warn("phone OTP: Twilio not configured, using dev log sender (codes printed to logs)")
	return logSender{}
}

type logSender struct{}

func (logSender) Send(_ context.Context, to, body string) error {
	slog.Info("DEV OTP", "to", to, "body", body)
	return nil
}

type twilioSender struct {
	cfg  config.Config
	http *http.Client
}

func (t *twilioSender) Send(ctx context.Context, to, body string) error {
	endpoint := "https://api.twilio.com/2010-04-01/Accounts/" + t.cfg.TwilioAccountSID + "/Messages.json"
	form := url.Values{}
	form.Set("To", to)
	form.Set("From", t.cfg.TwilioFrom)
	form.Set("Body", body)

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, endpoint, strings.NewReader(form.Encode()))
	if err != nil {
		return err
	}
	req.SetBasicAuth(t.cfg.TwilioAccountSID, t.cfg.TwilioAuthToken)
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")

	resp, err := t.http.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 300 {
		return fmt.Errorf("twilio send failed: status %d", resp.StatusCode)
	}
	return nil
}
