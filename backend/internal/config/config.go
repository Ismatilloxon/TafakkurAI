// Package config loads all runtime configuration from environment variables
// into a single typed struct. No other package reads os.Getenv directly.
package config

import (
	"fmt"
	"net/url"
	"strconv"
	"strings"
	"time"

	"github.com/caarlos0/env/v11"
)

type Config struct {
	Env string `env:"ENV" envDefault:"dev"`
	// HTTPPort is sourced from the platform-standard PORT variable. HTTP_PORT is
	// retained as a compatibility fallback for existing local/Fly deployments.
	HTTPPort       string `env:"PORT"`
	LegacyHTTPPort string `env:"HTTP_PORT"`

	// Database
	DatabaseURL   string `env:"DATABASE_URL,required"`
	MigrationsDir string `env:"MIGRATIONS_DIR" envDefault:"migrations"`
	AutoMigrate   bool   `env:"AUTO_MIGRATE" envDefault:"true"`

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
	// Sync batches arrive on a timer (every 200-300s), on a queue threshold, on
	// backgrounding and on reconnect. 20/min leaves room for a device draining a long
	// offline queue in 100-event pages without ever throttling normal use.
	RateSyncPerMin int `env:"RATE_SYNC_PER_MIN" envDefault:"20"`
}

// MigrationConfig is deliberately smaller than Config so the short-lived
// production migration container never needs JWT or provider secrets.
type MigrationConfig struct {
	DatabaseURL   string `env:"DATABASE_URL,required"`
	AppDBUser     string `env:"APP_DB_USER,required"`
	AppDBPassword string `env:"APP_DB_PASSWORD,required"`
	MigrationsDir string `env:"MIGRATIONS_DIR" envDefault:"migrations"`
}

func Load() (Config, error) {
	var c Config
	if err := env.Parse(&c); err != nil {
		return Config{}, err
	}
	c.Env = strings.ToLower(strings.TrimSpace(c.Env))
	if c.HTTPPort == "" {
		c.HTTPPort = strings.TrimSpace(c.LegacyHTTPPort)
	}
	if c.HTTPPort == "" {
		c.HTTPPort = "8080"
	}
	// envSeparator on an empty string yields []string{""}, which would make an
	// empty GOOGLE_CLIENT_ID look like one configured (empty) audience.
	c.GoogleClientIDs = nonEmpty(c.GoogleClientIDs)
	c.CORSAllowedOrigins = nonEmpty(c.CORSAllowedOrigins)
	if err := c.Validate(); err != nil {
		return Config{}, err
	}
	return c, nil
}

// LoadMigration loads only the values required by the one-shot migrator.
func LoadMigration() (MigrationConfig, error) {
	var c MigrationConfig
	if err := env.Parse(&c); err != nil {
		return MigrationConfig{}, err
	}
	c.DatabaseURL = strings.TrimSpace(c.DatabaseURL)
	c.AppDBUser = strings.TrimSpace(c.AppDBUser)
	c.AppDBPassword = strings.TrimSpace(c.AppDBPassword)
	c.MigrationsDir = strings.TrimSpace(c.MigrationsDir)
	if c.DatabaseURL == "" {
		return MigrationConfig{}, errorsFor("DATABASE_URL", "is required")
	}
	if c.MigrationsDir == "" {
		return MigrationConfig{}, errorsFor("MIGRATIONS_DIR", "is required")
	}
	if !validDatabaseIdentifier(c.AppDBUser) {
		return MigrationConfig{}, errorsFor("APP_DB_USER", "must be 1-63 lowercase letters, digits, or underscores, start with a letter or underscore, and not use the reserved pg_ prefix")
	}
	if !validLowerHexSecret(c.AppDBPassword, 32) {
		return MigrationConfig{}, errorsFor("APP_DB_PASSWORD", "must contain at least 32 lowercase hexadecimal characters")
	}
	parsed, err := url.Parse(c.DatabaseURL)
	if err != nil || parsed.User == nil || parsed.User.Username() == "" {
		return MigrationConfig{}, errorsFor("DATABASE_URL", "must include the migration database user")
	}
	if c.AppDBUser == parsed.User.Username() {
		return MigrationConfig{}, errorsFor("APP_DB_USER", "must differ from the migration database user")
	}
	if adminPassword, ok := parsed.User.Password(); ok && adminPassword == c.AppDBPassword {
		return MigrationConfig{}, errorsFor("APP_DB_PASSWORD", "must differ from the migration database password")
	}
	return c, nil
}

func validDatabaseIdentifier(value string) bool {
	if len(value) == 0 || len(value) > 63 || strings.HasPrefix(value, "pg_") {
		return false
	}
	for i, r := range value {
		if (r >= 'a' && r <= 'z') || r == '_' || (i > 0 && r >= '0' && r <= '9') {
			continue
		}
		return false
	}
	return true
}

func validLowerHexSecret(value string, minimum int) bool {
	if len(value) < minimum {
		return false
	}
	for _, r := range value {
		if (r < '0' || r > '9') && (r < 'a' || r > 'f') {
			return false
		}
	}
	return true
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

// Validate rejects unsafe or incomplete runtime combinations without ever
// including configured values in returned errors (startup errors are logged).
func (c Config) Validate() error {
	port, err := strconv.Atoi(c.HTTPPort)
	if err != nil || port < 1 || port > 65535 {
		return errorsFor("PORT", "must be an integer from 1 to 65535")
	}
	if strings.TrimSpace(c.DatabaseURL) == "" {
		return errorsFor("DATABASE_URL", "is required")
	}
	if strings.TrimSpace(c.JWTSecret) == "" {
		return errorsFor("JWT_SECRET", "is required")
	}

	if err := allOrNone(
		[]string{"GOOGLE_OAUTH_CLIENT_ID", "GOOGLE_OAUTH_CLIENT_SECRET"},
		[]string{c.GoogleOAuthClientID, c.GoogleOAuthClientSecret},
	); err != nil {
		return err
	}
	if err := allOrNone(
		[]string{"TWILIO_ACCOUNT_SID", "TWILIO_AUTH_TOKEN", "TWILIO_FROM"},
		[]string{c.TwilioAccountSID, c.TwilioAuthToken, c.TwilioFrom},
	); err != nil {
		return err
	}
	if c.TelegramWebhookBase != "" {
		if c.TelegramBotToken == "" || c.TelegramWebhookSecret == "" {
			return errorsFor("TELEGRAM_WEBHOOK_BASE", "requires TELEGRAM_BOT_TOKEN and TELEGRAM_WEBHOOK_SECRET")
		}
		if err := validateOrigin("TELEGRAM_WEBHOOK_BASE", c.TelegramWebhookBase, c.IsProd()); err != nil {
			return err
		}
	}

	if c.PublicBaseURL != "" {
		if err := validateOrigin("PUBLIC_BASE_URL", c.PublicBaseURL, c.IsProd()); err != nil {
			return err
		}
	}
	for _, origin := range c.CORSAllowedOrigins {
		if origin == "*" {
			if c.IsProd() {
				return errorsFor("CORS_ALLOWED_ORIGINS", "must not contain a wildcard in production")
			}
			continue
		}
		if err := validateOrigin("CORS_ALLOWED_ORIGINS", origin, c.IsProd()); err != nil {
			return err
		}
	}

	if c.IsProd() {
		if len(c.JWTSecret) < 32 {
			return errorsFor("JWT_SECRET", "must contain at least 32 characters in production")
		}
		if c.PublicBaseURL == "" {
			return errorsFor("PUBLIC_BASE_URL", "is required in production")
		}
		if c.TelegramBotToken != "" && c.TelegramWebhookBase == "" {
			return errorsFor("TELEGRAM_WEBHOOK_BASE", "is required when Telegram login is enabled in production")
		}
	}

	return nil
}

func allOrNone(names, values []string) error {
	configured := 0
	for _, value := range values {
		if strings.TrimSpace(value) != "" {
			configured++
		}
	}
	if configured != 0 && configured != len(values) {
		return fmt.Errorf("configuration: %s must be configured together", strings.Join(names, ", "))
	}
	return nil
}

func validateOrigin(name, raw string, requireHTTPS bool) error {
	parsed, err := url.Parse(strings.TrimSpace(raw))
	if err != nil || parsed.Host == "" || (parsed.Scheme != "http" && parsed.Scheme != "https") {
		return errorsFor(name, "must be an absolute HTTP(S) origin")
	}
	if parsed.User != nil || parsed.RawQuery != "" || parsed.Fragment != "" || (parsed.Path != "" && parsed.Path != "/") {
		return errorsFor(name, "must be an origin without credentials, path, query, or fragment")
	}
	if requireHTTPS && parsed.Scheme != "https" {
		return errorsFor(name, "must use HTTPS in production")
	}
	return nil
}

func errorsFor(name, requirement string) error {
	return fmt.Errorf("configuration: %s %s", name, requirement)
}
