// Package server builds the chi router, wires cross-cutting middleware, and
// mounts every domain module's routes. It depends on each module's Handler and
// on the TokenVerifier interface — never on their repos.
package server

import (
	"context"
	"net/http"

	"github.com/go-chi/chi/v5"

	"github.com/tafakkur/backend/internal/auth"
	"github.com/tafakkur/backend/internal/coach"
	"github.com/tafakkur/backend/internal/config"
	"github.com/tafakkur/backend/internal/feed"
	"github.com/tafakkur/backend/internal/learn"
	"github.com/tafakkur/backend/internal/platform"
	"github.com/tafakkur/backend/internal/prayer"
	"github.com/tafakkur/backend/internal/tracker"
	"github.com/tafakkur/backend/internal/user"
)

// Deps carries everything NewRouter needs. main.go constructs and injects it.
type Deps struct {
	Config   config.Config
	Verifier TokenVerifier
	Ready    func(ctx context.Context) error

	Auth    *auth.Handler
	User    *user.Handler
	Prayer  *prayer.Handler
	Coach   *coach.Handler
	Feed    *feed.Handler
	Learn   *learn.Handler
	Tracker *tracker.Handler
}

func NewRouter(d Deps) http.Handler {
	r := chi.NewRouter()
	r.Use(RequestID, Recoverer, Logger, SecurityHeaders, CORS(d.Config))

	r.Get("/healthz", func(w http.ResponseWriter, r *http.Request) {
		platform.JSON(w, http.StatusOK, map[string]string{"status": "ok"})
	})
	r.Get("/readyz", func(w http.ResponseWriter, r *http.Request) {
		if d.Ready != nil {
			if err := d.Ready(r.Context()); err != nil {
				platform.Error(w, http.StatusServiceUnavailable, platform.CodeUnavailable, "not ready")
				return
			}
		}
		platform.JSON(w, http.StatusOK, map[string]string{"status": "ready"})
	})

	r.Route("/v1", func(r chi.Router) {
		// Public (no access token required).
		r.Group(func(pub chi.Router) {
			d.Auth.RegisterPublic(pub)
		})
		// Protected (valid access token required).
		r.Group(func(pr chi.Router) {
			pr.Use(Auth(d.Verifier))
			d.Auth.RegisterProtected(pr)
			d.User.Register(pr)
			d.Prayer.Register(pr)
			d.Coach.Register(pr)
			d.Feed.Register(pr)
			d.Learn.Register(pr)
			d.Tracker.Register(pr)
		})
	})

	return r
}
