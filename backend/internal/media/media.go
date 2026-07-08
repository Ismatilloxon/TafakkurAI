// Package media stores binary assets (profile photos) in S3-compatible object
// storage. In dev this points at MinIO; in prod at real S3. Other modules
// depend only on the Service interface.
package media

import (
	"context"
	"errors"
	"fmt"
	"io"
	"strings"

	"github.com/aws/aws-sdk-go-v2/aws"
	awsconfig "github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/credentials"
	"github.com/aws/aws-sdk-go-v2/feature/s3/manager"
	"github.com/aws/aws-sdk-go-v2/service/s3"

	"github.com/tafakkur/backend/internal/config"
)

var ErrNotConfigured = errors.New("media storage is not configured")

type Service interface {
	// Upload stores body at key and returns a publicly resolvable URL.
	Upload(ctx context.Context, key, contentType string, body io.Reader) (string, error)
}

type s3Service struct {
	uploader   *manager.Uploader
	bucket     string
	publicBase string
}

type disabledService struct{}

func (disabledService) Upload(context.Context, string, string, io.Reader) (string, error) {
	return "", ErrNotConfigured
}

// NewService returns an S3-backed service, or a disabled one that errors on
// upload when storage env vars are absent (so the API still boots in dev).
func NewService(ctx context.Context, cfg config.Config) (Service, error) {
	if cfg.S3Endpoint == "" || cfg.S3AccessKey == "" {
		return disabledService{}, nil
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
		uploader:   manager.NewUploader(client),
		bucket:     cfg.S3Bucket,
		publicBase: strings.TrimRight(base, "/"),
	}, nil
}

func (s *s3Service) Upload(ctx context.Context, key, contentType string, body io.Reader) (string, error) {
	_, err := s.uploader.Upload(ctx, &s3.PutObjectInput{
		Bucket:      aws.String(s.bucket),
		Key:         aws.String(key),
		Body:        body,
		ContentType: aws.String(contentType),
	})
	if err != nil {
		return "", fmt.Errorf("s3 upload: %w", err)
	}
	return s.publicBase + "/" + key, nil
}
