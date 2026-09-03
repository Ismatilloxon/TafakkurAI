// Package media stores binary assets (profile photos) in S3-compatible object
// storage. In dev this points at MinIO; in prod at real S3. Other modules
// depend only on the Service interface.
package media

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net/url"
	"path"
	"strings"

	"github.com/aws/aws-sdk-go-v2/aws"
	awsconfig "github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/credentials"
	"github.com/aws/aws-sdk-go-v2/feature/s3/manager"
	"github.com/aws/aws-sdk-go-v2/service/s3"

	"github.com/tafakkur/backend/internal/config"
)

var (
	ErrNotConfigured = errors.New("media storage is not configured")
	ErrInvalidUpload = errors.New("invalid media upload")
)

type Upload struct {
	Key         string
	ContentType string
	Size        int64
	Body        io.Reader
}

type Service interface {
	// Upload stores body at key and returns a publicly resolvable URL.
	Upload(ctx context.Context, upload Upload) (string, error)
	Delete(ctx context.Context, key string) error

	// ObjectKey returns the storage key only when rawURL is an unmodified URL below this
	// service's configured public base. Callers must still enforce resource ownership.
	ObjectKey(rawURL string) (string, bool)
}

type s3Service struct {
	client     *s3.Client
	uploader   *manager.Uploader
	bucket     string
	publicBase string
}

type disabledService struct{}

func (disabledService) Upload(context.Context, Upload) (string, error) {
	return "", ErrNotConfigured
}

func (disabledService) Delete(context.Context, string) error { return ErrNotConfigured }

func (disabledService) ObjectKey(string) (string, bool) { return "", false }

// NewService returns an S3-backed service, or a disabled one that errors on
// upload when storage env vars are absent (so the API still boots in dev).
func NewService(ctx context.Context, cfg config.Config) (Service, error) {
	configured := cfg.S3Endpoint != "" || cfg.S3AccessKey != "" || cfg.S3SecretKey != "" || cfg.S3PublicBase != ""
	if !configured {
		return disabledService{}, nil
	}
	if err := validateConfig(cfg); err != nil {
		return nil, err
	}

	awsCfg, err := awsconfig.LoadDefaultConfig(ctx,
		awsconfig.WithRegion(cfg.S3Region),
		awsconfig.WithCredentialsProvider(
			credentials.NewStaticCredentialsProvider(cfg.S3AccessKey, cfg.S3SecretKey, ""),
		),
	)
	if err != nil {
		return nil, fmt.Errorf("load aws config: %w", err)
	}

	client := s3.NewFromConfig(awsCfg, func(o *s3.Options) {
		o.BaseEndpoint = aws.String(cfg.S3Endpoint)
		o.UsePathStyle = cfg.S3UsePathStyle
	})

	base := cfg.S3PublicBase
	if base == "" {
		base = strings.TrimRight(cfg.S3Endpoint, "/") + "/" + cfg.S3Bucket
	}

	return &s3Service{
		client:     client,
		uploader:   manager.NewUploader(client),
		bucket:     cfg.S3Bucket,
		publicBase: strings.TrimRight(base, "/"),
	}, nil
}

func validateConfig(cfg config.Config) error {
	switch {
	case strings.TrimSpace(cfg.S3Endpoint) == "":
		return errors.New("media config: S3_ENDPOINT is required")
	case strings.TrimSpace(cfg.S3AccessKey) == "":
		return errors.New("media config: S3_ACCESS_KEY is required")
	case strings.TrimSpace(cfg.S3SecretKey) == "":
		return errors.New("media config: S3_SECRET_KEY is required")
	case strings.TrimSpace(cfg.S3Bucket) == "":
		return errors.New("media config: S3_BUCKET is required")
	}
	if err := validateBaseURL("S3_ENDPOINT", cfg.S3Endpoint, cfg.IsProd()); err != nil {
		return err
	}
	if cfg.S3PublicBase != "" {
		if err := validateBaseURL("S3_PUBLIC_BASE", cfg.S3PublicBase, cfg.IsProd()); err != nil {
			return err
		}
	}
	return nil
}

func validateBaseURL(name, raw string, requireHTTPS bool) error {
	parsed, err := url.Parse(raw)
	if err != nil || parsed.Host == "" || (parsed.Scheme != "http" && parsed.Scheme != "https") {
		return fmt.Errorf("media config: %s must be an absolute HTTP(S) URL", name)
	}
	if parsed.User != nil {
		return fmt.Errorf("media config: %s must not contain credentials", name)
	}
	if parsed.RawQuery != "" || parsed.Fragment != "" {
		return fmt.Errorf("media config: %s must not contain a query or fragment", name)
	}
	if requireHTTPS && parsed.Scheme != "https" {
		return fmt.Errorf("media config: %s must use HTTPS in production", name)
	}
	return nil
}

func (s *s3Service) Upload(ctx context.Context, upload Upload) (string, error) {
	if err := validateUpload(upload); err != nil {
		return "", err
	}
	_, err := s.uploader.Upload(ctx, &s3.PutObjectInput{
		Bucket:             aws.String(s.bucket),
		Key:                aws.String(upload.Key),
		Body:               upload.Body,
		ContentLength:      aws.Int64(upload.Size),
		ContentType:        aws.String(upload.ContentType),
		ContentDisposition: aws.String("inline"),
		CacheControl:       aws.String("public, max-age=31536000, immutable"),
	})
	if err != nil {
		return "", fmt.Errorf("s3 upload: %w", err)
	}
	return s.publicBase + "/" + upload.Key, nil
}

func (s *s3Service) Delete(ctx context.Context, key string) error {
	if !safeObjectKey(key) {
		return fmt.Errorf("%w: unsafe key", ErrInvalidUpload)
	}
	if _, err := s.client.DeleteObject(ctx, &s3.DeleteObjectInput{
		Bucket: aws.String(s.bucket),
		Key:    aws.String(key),
	}); err != nil {
		return fmt.Errorf("s3 delete: %w", err)
	}
	return nil
}

func (s *s3Service) ObjectKey(rawURL string) (string, bool) {
	return objectKeyFromPublicURL(s.publicBase, rawURL)
}

func objectKeyFromPublicURL(publicBase, rawURL string) (string, bool) {
	base, err := url.Parse(publicBase)
	if err != nil {
		return "", false
	}
	candidate, err := url.Parse(rawURL)
	if err != nil {
		return "", false
	}
	if candidate.Scheme == "" || candidate.Host == "" || candidate.User != nil ||
		candidate.RawQuery != "" || candidate.Fragment != "" || candidate.RawPath != "" ||
		!strings.EqualFold(candidate.Scheme, base.Scheme) ||
		!strings.EqualFold(candidate.Host, base.Host) {
		return "", false
	}

	basePath := strings.TrimRight(base.Path, "/")
	prefix := basePath + "/"
	if !strings.HasPrefix(candidate.Path, prefix) {
		return "", false
	}
	key := strings.TrimPrefix(candidate.Path, prefix)
	if !safeObjectKey(key) {
		return "", false
	}
	return key, true
}

func validateUpload(upload Upload) error {
	switch {
	case upload.Key == "":
		return fmt.Errorf("%w: key is required", ErrInvalidUpload)
	case !safeObjectKey(upload.Key):
		return fmt.Errorf("%w: unsafe key", ErrInvalidUpload)
	case upload.ContentType == "":
		return fmt.Errorf("%w: content type is required", ErrInvalidUpload)
	case upload.Size <= 0:
		return fmt.Errorf("%w: size must be positive", ErrInvalidUpload)
	case upload.Body == nil:
		return fmt.Errorf("%w: body is required", ErrInvalidUpload)
	default:
		return nil
	}
}

func safeObjectKey(key string) bool {
	if key == "" || key == "." || len(key) > 512 || path.Clean(key) != key ||
		strings.HasPrefix(key, "/") || strings.Contains(key, `\`) {
		return false
	}
	for _, char := range key {
		if char < 0x20 || char == 0x7f {
			return false
		}
	}
	return true
}
