// Package config loads all runtime configuration from environment variables
// into a single typed struct. No other package reads os.Getenv directly.
package config

import (
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

	// Identity providers
	GoogleClientID string `env:"GOOGLE_CLIENT_ID"`
	AppleBundleID  string `env:"APPLE_BUNDLE_ID"`

	// Phone OTP (Twilio)
	TwilioAccountSID string        `env:"TWILIO_ACCOUNT_SID"`
	TwilioAuthToken  string        `env:"TWILIO_AUTH_TOKEN"`
	TwilioFrom       string        `env:"TWILIO_FROM"`
	OTPTTL           time.Duration `env:"OTP_TTL" envDefault:"5m"`
	OTPMaxAttempts   int           `env:"OTP_MAX_ATTEMPTS" envDefault:"5"`

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
	return c, nil
}

func (c Config) IsProd() bool { return c.Env == "prod" || c.Env == "production" }
