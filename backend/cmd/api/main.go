// Command api is the single entrypoint for the TafakkurAI modular monolith. It
// loads config, opens one Postgres pool, runs migrations, constructs every
// module, injects them into the router, and serves with graceful shutdown.
package main

import (
	"context"
	"errors"
	"log/slog"
	"net"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"github.com/tafakkur/backend/internal/auth"
	"github.com/tafakkur/backend/internal/coach"
	"github.com/tafakkur/backend/internal/config"
	"github.com/tafakkur/backend/internal/feed"
	"github.com/tafakkur/backend/internal/gamify"
	"github.com/tafakkur/backend/internal/gratitude"
	"github.com/tafakkur/backend/internal/habits"
	"github.com/tafakkur/backend/internal/learn"
	"github.com/tafakkur/backend/internal/media"
	"github.com/tafakkur/backend/internal/platform"
	"github.com/tafakkur/backend/internal/prayer"
	"github.com/tafakkur/backend/internal/quran"
	"github.com/tafakkur/backend/internal/ratelimit"
	"github.com/tafakkur/backend/internal/server"
	"github.com/tafakkur/backend/internal/sleep"
	syncsvc "github.com/tafakkur/backend/internal/sync"
	"github.com/tafakkur/backend/internal/telegram"
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

	if cfg.AutoMigrate {
		migrationCtx, cancelMigrations := context.WithTimeout(ctx, 10*time.Minute)
		migrationErr := platform.RunMigrations(migrationCtx, pool, cfg.MigrationsDir)
		cancelMigrations()
		if migrationErr != nil {
			return migrationErr
		}
		slog.Info("migrations applied")
	} else {
		slog.Info("automatic migrations disabled")
	}

	// ---- construct modules (dependencies flow through interfaces) ----
	gamSvc := gamify.NewService(gamify.NewRepo(pool))

	userSvc := user.NewService(user.NewRepo(pool), gamSvc)
	mediaSvc, err := media.NewService(ctx, cfg)
	if err != nil {
		return err
	}
	userH := user.NewHandler(userSvc, mediaSvc)

	jwtMgr := auth.NewManager(cfg)
	verifier := auth.NewVerifier(cfg)
	authSvc := auth.NewService(
		userSvc, jwtMgr, verifier,
		auth.NewOTP(auth.NewRepo(pool), cfg, auth.NewSMSSender(cfg)),
	)

	googleOAuth := auth.NewGoogleOAuth(auth.NewRepo(pool), userSvc, jwtMgr, verifier, cfg)
	if !googleOAuth.Enabled() {
		slog.Warn("GOOGLE_OAUTH_CLIENT_ID/SECRET not set — google sign-in disabled")
	}
	// Telegram sign-in. The bot needs a handler that can mint a confirmation code,
	// and the login service needs the bot's @username for the deep link — hence the
	// two-step construction (bot first with a closure, resolved right after).
	var tgLogin *auth.TelegramLogin
	tgClient := telegram.NewClient(cfg.TelegramBotToken)
	tgBot := telegram.NewBot(tgClient, telegram.NewRepo(pool), func(ctx context.Context, from telegram.User, c telegram.Contact) (string, error) {
		return tgLogin.IssueCode(ctx, from, c)
	})
	tgLogin = auth.NewTelegramLogin(auth.NewRepo(pool), userSvc, jwtMgr, tgBot, cfg)
	tgBot.CodeTTL = tgLogin.CodeTTL()

	var tgWebhook http.HandlerFunc
	botCtx, stopBot := context.WithCancel(ctx)
	defer stopBot()
	if tgBot.Enabled() {
		if err := tgBot.Init(ctx); err != nil {
			return err
		}
		if cfg.TelegramWebhookBase != "" {
			url := strings.TrimRight(cfg.TelegramWebhookBase, "/") + "/telegram/webhook"
			if err := tgClient.SetWebhook(ctx, url, cfg.TelegramWebhookSecret); err != nil {
				return err
			}
			slog.Info("telegram webhook registered", "url", url)
			tgWebhook = tgBot.WebhookHandler(cfg.TelegramWebhookSecret)
		} else {
			go tgBot.RunPoller(botCtx)
		}
	} else {
		slog.Warn("TELEGRAM_BOT_TOKEN not set — telegram sign-in disabled")
	}

	authH := auth.NewHandler(authSvc, tgLogin, googleOAuth, ratelimit.NewPerMinute(cfg.RateAuthPerMin))

	trackerSvc := tracker.NewService(tracker.NewRepo(pool))
	trackerH := tracker.NewHandler(trackerSvc, gamSvc)

	prayerH := prayer.NewHandler(prayer.NewService(userSvc, gamSvc, trackerSvc, prayer.NewCompletionRepo(pool)))

	coachSvc := coach.NewService(coach.NewRepo(pool), coach.NewClient(cfg))
	coachH := coach.NewHandler(coachSvc, ratelimit.NewPerMinute(cfg.RateCoachPerMin))

	feedSvc := feed.NewService(feed.NewRepo(pool), userSvc, gamSvc)
	feedH := feed.NewHandler(feedSvc, ratelimit.NewPerMinute(cfg.RatePostPerMin))

	gratitudeH := gratitude.NewHandler(gratitude.NewService(gratitude.NewRepo(pool)), gamSvc, trackerSvc)

	// Habits reads and writes the shared entity_state projection rather than tables of
	// its own, so it needs the pool and nothing else. See internal/habits/domain.go.
	habitsH := habits.NewHandler(habits.NewService(habits.NewStore(pool)), gamSvc, trackerSvc)

	learnH := learn.NewHandler(learn.NewService(learn.NewRepo(pool)), gamSvc)

	quranH := quran.NewHandler(quran.NewService(quran.NewRepo(pool)), gamSvc)

	sleepH := sleep.NewHandler(sleep.NewService(sleep.NewRepo(pool)), gamSvc, trackerSvc)

	// The generic sync platform. Its rate limit is per user and deliberately generous:
	// a client only calls it every few minutes or when its queue crosses a threshold,
	// so anything hitting the ceiling is a bug or an attack, not normal use.
	syncH := syncsvc.NewHandler(
		syncsvc.NewService(syncsvc.NewRepo(pool), gamSvc),
		ratelimit.NewPerMinute(cfg.RateSyncPerMin),
	)

	router := server.NewRouter(server.Deps{
		Config:          cfg,
		Verifier:        jwtMgr,
		Ready:           pool.Ping,
		Auth:            authH,
		User:            userH,
		Prayer:          prayerH,
		Coach:           coachH,
		Feed:            feedH,
		Learn:           learnH,
		Tracker:         trackerH,
		Gratitude:       gratitudeH,
		Habits:          habitsH,
		Quran:           quranH,
		Sleep:           sleepH,
		Sync:            syncH,
		TelegramWebhook: tgWebhook,
	})

	srv := &http.Server{
		Addr:              net.JoinHostPort("0.0.0.0", cfg.HTTPPort),
		Handler:           router,
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       30 * time.Second,
		// Streaming coach responses can legitimately outlive a fixed write
		// deadline. Their outbound Anthropic client owns a bounded timeout.
		WriteTimeout:   0,
		IdleTimeout:    2 * time.Minute,
		MaxHeaderBytes: 1 << 20,
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
	defer signal.Stop(stop)

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
