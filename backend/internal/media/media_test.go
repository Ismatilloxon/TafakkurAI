package media

import (
	"errors"
	"strings"
	"testing"

	"github.com/tafakkur/backend/internal/config"
)

func TestValidateUpload(t *testing.T) {
	valid := Upload{
		Key:         "avatars/user-id/photo.jpg",
		ContentType: "image/jpeg",
		Size:        3,
		Body:        strings.NewReader("jpg"),
	}
	if err := validateUpload(valid); err != nil {
		t.Fatalf("valid upload: %v", err)
	}

	invalidKeys := []string{
		"",
		".",
		"/avatars/photo.jpg",
		"avatars/../photo.jpg",
		`avatars\photo.jpg`,
		"avatars/photo\n.jpg",
	}
	for _, key := range invalidKeys {
		upload := valid
		upload.Key = key
		if err := validateUpload(upload); !errors.Is(err, ErrInvalidUpload) {
			t.Errorf("key %q error = %v, want ErrInvalidUpload", key, err)
		}
	}
}

func TestValidateConfigRequiresCompleteSecureProductionConfig(t *testing.T) {
	base := config.Config{
		Env:          "production",
		S3Endpoint:   "https://objects.example.com",
		S3Bucket:     "tafakkur-media",
		S3AccessKey:  "access",
		S3SecretKey:  "secret",
		S3PublicBase: "https://media.example.com",
	}
	if err := validateConfig(base); err != nil {
		t.Fatalf("valid config: %v", err)
	}

	insecure := base
	insecure.S3PublicBase = "http://media.example.com"
	if err := validateConfig(insecure); err == nil {
		t.Fatal("production HTTP public base should be rejected")
	}

	partial := base
	partial.S3SecretKey = ""
	if err := validateConfig(partial); err == nil {
		t.Fatal("partial credentials should be rejected")
	}
	withCredentials := base
	withCredentials.S3Endpoint = "https://user:password@objects.example.com"
	if err := validateConfig(withCredentials); err == nil {
		t.Fatal("URL credentials should be rejected")
	}
}
