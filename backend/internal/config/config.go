// Package config loads all runtime configuration from environment variables
// into a single typed struct. No other package reads os.Getenv directly.
package config

import (
	"strings"
	"time"

	"github.com/caarlos0/env/v11"
)

type Config struct {
	Env      string `env:"ENV" envDefault:"dev"`
	HTTPPort string `env:"HTTP_PORT" envDefault:"8080"`

	// Database
	DatabaseURL   string `env:"DATABASE_URL,required"`
	MigrationsDir string `env:"MIGRATIONS_DIR" envDefault:"migrations"`

	// JWT
	JWTSecret       string        `env:"JWT_SECRET,required"`
	JWTIssuer       string        `env:"JWT_ISSUER" envDefault:"tafakkur"`
	AccessTokenTTL  time.Duration `env:"ACCESS_TOKEN_TTL" envDefault:"15m"`
	RefreshTokenTTL time.Duration `env:"REFRESH_TOKEN_TTL" envDefault:"720h"`

	// PublicBaseURL is where this server is reachable from a browser. It builds
	// the Google OAuth redirect URI, so it has to match the one registered in the
	// Google Cloud console exactly. Empty falls back to http://localhost:<port>.
	PublicBaseURL string `env:"PUBLIC_BASE_URL"`

	// Identity providers.
	//
	// GoogleClientIDs is a list because Google issues a *different* client id per
	// platform, and the id token's `aud` is whichever one minted it. Verifying
	// against a single value rejects every token from the other platforms.
	GoogleClientIDs []string `env:"GOOGLE_CLIENT_ID" envSeparator:","`
	AppleBundleID   string   `env:"APPLE_BUNDLE_ID"`

	// Google's browser sign-in flow, driven entirely by this server: the app
	// opens a URL and polls, so neither platform needs the Google SDK. Requires a
	// "Web application" OAuth client (the only kind that gets a secret).
	GoogleOAuthClientID     string        `env:"GOOGLE_OAUTH_CLIENT_ID"`
	GoogleOAuthClientSecret string        `env:"GOOGLE_OAUTH_CLIENT_SECRET"`
	OAuthStateTTL           time.Duration `env:"OAUTH_STATE_TTL" envDefault:"10m"`

	// Phone OTP (Twilio)
	TwilioAccountSID string        `env:"TWILIO_ACCOUNT_SID"`
	TwilioAuthToken  string        `env:"TWILIO_AUTH_TOKEN"`
	TwilioFrom       string        `env:"TWILIO_FROM"`
	OTPTTL           time.Duration `env:"OTP_TTL" envDefault:"5m"`
	OTPMaxAttempts   int           `env:"OTP_MAX_ATTEMPTS" envDefault:"5"`

	// Telegram bot login. With no TELEGRAM_WEBHOOK_BASE the bot falls back to
	// long-polling, which is what makes sign-in work on a laptop with no public URL.
	TelegramBotToken      string        `env:"TELEGRAM_BOT_TOKEN"`
	TelegramWebhookBase   string        `env:"TELEGRAM_WEBHOOK_BASE"`
	TelegramWebhookSecret string        `env:"TELEGRAM_WEBHOOK_SECRET"`
	TelegramCodeTTL       time.Duration `env:"TELEGRAM_CODE_TTL" envDefault:"5m"`

	// Anthropic / Claude
	AnthropicAPIKey  string `env:"ANTHROPIC_API_KEY"`
	ClaudeModel      string `env:"CLAUDE_MODEL" envDefault:"claude-sonnet-5"`
	ClaudeDeepModel  string `env:"CLAUDE_DEEP_MODEL" envDefault:"claude-opus-4-8"`
	ClaudeMaxTokens  int    `env:"CLAUDE_MAX_TOKENS" envDefault:"1024"`
	AnthropicVersion string `env:"ANTHROPIC_VERSION" envDefault:"2023-06-01"`

	// Object storage (S3 / MinIO)
	S3Endpoint     string `env:"S3_ENDPOINT"`
	S3Region       string `env:"S3_REGION" envDefault:"us-east-1"`
	S3Bucket       string `env:"S3_BUCKET" envDefault:"tafakkur-media"`
	S3AccessKey    string `env:"S3_ACCESS_KEY"`
	S3SecretKey    string `env:"S3_SECRET_KEY"`
	S3UsePathStyle bool   `env:"S3_USE_PATH_STYLE" envDefault:"true"`
	S3PublicBase   string `env:"S3_PUBLIC_BASE"`

	// CORS
	CORSAllowedOrigins []string `env:"CORS_ALLOWED_ORIGINS" envSeparator:","`

	// Rate limits (requests per minute)
	RateAuthPerMin  int `env:"RATE_AUTH_PER_MIN" envDefault:"20"`
	RateCoachPerMin int `env:"RATE_COACH_PER_MIN" envDefault:"15"`
	RatePostPerMin  int `env:"RATE_POST_PER_MIN" envDefault:"30"`
}

func Load() (Config, error) {
	var c Config
	if err := env.Parse(&c); err != nil {
		return Config{}, err
	}
	// envSeparator on an empty string yields []string{""}, which would make an
	// empty GOOGLE_CLIENT_ID look like one configured (empty) audience.
	c.GoogleClientIDs = nonEmpty(c.GoogleClientIDs)
	return c, nil
}

func nonEmpty(in []string) []string {
	out := in[:0]
	for _, s := range in {
		if s = strings.TrimSpace(s); s != "" {
			out = append(out, s)
		}
	}
	return out
}

// BaseURL is PublicBaseURL with a working local default, without a trailing slash.
func (c Config) BaseURL() string {
	if c.PublicBaseURL != "" {
		return strings.TrimRight(c.PublicBaseURL, "/")
	}
	return "http://localhost:" + c.HTTPPort
}

func (c Config) IsProd() bool { return c.Env == "prod" || c.Env == "production" }
