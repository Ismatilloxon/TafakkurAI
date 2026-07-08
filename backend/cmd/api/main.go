// Command api is the single entrypoint for the TafakkurAI modular monolith. It
// loads config, opens one Postgres pool, runs migrations, constructs every
// module, injects them into the router, and serves with graceful shutdown.
package main

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/tafakkur/backend/internal/auth"
	"github.com/tafakkur/backend/internal/coach"
	"github.com/tafakkur/backend/internal/config"
	"github.com/tafakkur/backend/internal/feed"
	"github.com/tafakkur/backend/internal/gamify"
	"github.com/tafakkur/backend/internal/learn"
	"github.com/tafakkur/backend/internal/media"
	"github.com/tafakkur/backend/internal/platform"
	"github.com/tafakkur/backend/internal/prayer"
	"github.com/tafakkur/backend/internal/ratelimit"
	"github.com/tafakkur/backend/internal/server"
	"github.com/tafakkur/backend/internal/tracker"
	"github.com/tafakkur/backend/internal/user"
)

func main() {
	slog.SetDefault(slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: slog.LevelInfo})))

	if err := run(); err != nil {
		slog.Error("fatal", "error", err)
		os.Exit(1)
	}
}

func run() error {
	cfg, err := config.Load()
	if err != nil {
		return err
	}

	ctx := context.Background()

	pool, err := platform.NewPool(ctx, cfg.DatabaseURL)
	if err != nil {
		return err
	}
	defer pool.Close()

	if err := platform.RunMigrations(ctx, pool, cfg.MigrationsDir); err != nil {
		return err
	}
	slog.Info("migrations applied")

	// ---- construct modules (dependencies flow through interfaces) ----
	gamSvc := gamify.NewService(gamify.NewRepo(pool))

	userSvc := user.NewService(user.NewRepo(pool), gamSvc)
	mediaSvc, err := media.NewService(ctx, cfg)
	if err != nil {
		return err
	}
	userH := user.NewHandler(userSvc, mediaSvc)

	jwtMgr := auth.NewManager(cfg)
	authSvc := auth.NewService(
		userSvc, jwtMgr, auth.NewVerifier(cfg),
		auth.NewOTP(auth.NewRepo(pool), cfg, auth.NewSMSSender(cfg)),
	)
	authH := auth.NewHandler(authSvc, ratelimit.NewPerMinute(cfg.RateAuthPerMin))

	trackerSvc := tracker.NewService(tracker.NewRepo(pool))
	trackerH := tracker.NewHandler(trackerSvc, gamSvc)

	prayerH := prayer.NewHandler(prayer.NewService(userSvc, gamSvc, trackerSvc))

	coachSvc := coach.NewService(coach.NewRepo(pool), coach.NewClient(cfg))
	coachH := coach.NewHandler(coachSvc, ratelimit.NewPerMinute(cfg.RateCoachPerMin))

	feedSvc := feed.NewService(feed.NewRepo(pool), userSvc, gamSvc)
	feedH := feed.NewHandler(feedSvc, ratelimit.NewPerMinute(cfg.RatePostPerMin))

	learnH := learn.NewHandler(learn.NewService(learn.NewRepo(pool)), gamSvc)

	router := server.NewRouter(server.Deps{
		Config:   cfg,
		Verifier: jwtMgr,
		Ready:    pool.Ping,
		Auth:     authH,
		User:     userH,
		Prayer:   prayerH,
		Coach:    coachH,
		Feed:     feedH,
		Learn:    learnH,
		Tracker:  trackerH,
	})

	srv := &http.Server{
		Addr:              ":" + cfg.HTTPPort,
		Handler:           router,
		ReadHeaderTimeout: 10 * time.Second,
	}

	serverErr := make(chan error, 1)
	go func() {
		slog.Info("http server listening", "port", cfg.HTTPPort, "env", cfg.Env)
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			serverErr <- err
		}
	}()

	stop := make(chan os.Signal, 1)
	signal.Notify(stop, os.Interrupt, syscall.SIGTERM)

	select {
	case err := <-serverErr:
		return err
	case <-stop:
		slog.Info("shutdown signal received, draining")
		shutdownCtx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
		defer cancel()
		return srv.Shutdown(shutdownCtx)
	}
}
