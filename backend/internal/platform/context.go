package platform

import (
	"context"

	"github.com/google/uuid"
)

type ctxKey int

const (
	ctxKeyUserID ctxKey = iota
	ctxKeyRequestID
)

// WithUserID stores the authenticated user id (set by the auth middleware).
func WithUserID(ctx context.Context, id uuid.UUID) context.Context {
	return context.WithValue(ctx, ctxKeyUserID, id)
}

// UserID returns the authenticated user id and whether one was present.
func UserID(ctx context.Context) (uuid.UUID, bool) {
	id, ok := ctx.Value(ctxKeyUserID).(uuid.UUID)
	return id, ok
}

// MustUserID returns the user id or the zero UUID. Handlers behind the auth
// middleware can rely on it being set.
func MustUserID(ctx context.Context) uuid.UUID {
	id, _ := UserID(ctx)
	return id
}

func WithRequestID(ctx context.Context, id string) context.Context {
	return context.WithValue(ctx, ctxKeyRequestID, id)
}

func RequestID(ctx context.Context) string {
	id, _ := ctx.Value(ctxKeyRequestID).(string)
	return id
}
