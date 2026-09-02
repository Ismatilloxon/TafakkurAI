package user

import (
	"bytes"
	"context"
	"image"
	"image/color"
	"image/jpeg"
	"image/png"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/google/uuid"

	"github.com/tafakkur/backend/internal/platform"
)

func TestInspectProfilePhotoUsesVerifiedBytes(t *testing.T) {
	tests := []struct {
		name        string
		encode      func(*bytes.Buffer) error
		contentType string
		extension   string
	}{
		{
			name: "jpeg",
			encode: func(dst *bytes.Buffer) error {
				return jpeg.Encode(dst, testImage(), &jpeg.Options{Quality: 80})
			},
			contentType: "image/jpeg",
			extension:   ".jpg",
		},
		{
			name: "png",
			encode: func(dst *bytes.Buffer) error {
				return png.Encode(dst, testImage())
			},
			contentType: "image/png",
			extension:   ".png",
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			var encoded bytes.Buffer
			if err := test.encode(&encoded); err != nil {
				t.Fatalf("encode fixture: %v", err)
			}
			reader := bytes.NewReader(encoded.Bytes())
			format, err := inspectProfilePhoto(reader, int64(encoded.Len()))
			if err != nil {
				t.Fatalf("inspect photo: %v", err)
			}
			if format.contentType != test.contentType || format.extension != test.extension {
				t.Fatalf("format = %+v, want %s %s", format, test.contentType, test.extension)
			}
			position, err := reader.Seek(0, 1)
			if err != nil {
				t.Fatalf("read position: %v", err)
			}
			if position != 0 {
				t.Fatalf("reader position = %d, want 0", position)
			}
		})
	}
}

func TestInspectProfilePhotoRejectsUntrustedContentAndSize(t *testing.T) {
	if _, err := inspectProfilePhoto(bytes.NewReader([]byte("<svg><script/></svg>")), 22); err == nil {
		t.Fatal("SVG/script content should be rejected")
	}
	if _, err := inspectProfilePhoto(bytes.NewReader([]byte("not an image")), maxPhotoBytes+1); err != errPhotoTooLarge {
		t.Fatalf("oversized error = %v, want errPhotoTooLarge", err)
	}
}

func TestUploadPhotoRejectsOversizedRequestBeforeParsing(t *testing.T) {
	handler := &Handler{}
	request := httptest.NewRequest(http.MethodPost, "/v1/me/photo", bytes.NewReader(nil))
	request.ContentLength = maxPhotoBytes + maxPhotoMultipartOverhead + 1
	request = request.WithContext(platform.WithUserID(context.Background(), uuid.New()))
	response := httptest.NewRecorder()

	handler.uploadPhoto(response, request)

	if response.Code != http.StatusRequestEntityTooLarge {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusRequestEntityTooLarge)
	}
}

func testImage() image.Image {
	img := image.NewRGBA(image.Rect(0, 0, 4, 3))
	for y := 0; y < 3; y++ {
		for x := 0; x < 4; x++ {
			img.Set(x, y, color.RGBA{R: uint8(30 * x), G: uint8(40 * y), B: 120, A: 255})
		}
	}
	return img
}
