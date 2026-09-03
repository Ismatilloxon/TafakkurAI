// Command migrate applies TafakkurAI's forward-only migrations and exits. The
// production Compose stack runs it with the database owner credential before
// starting the API, whose runtime role has no schema-creation privileges.
package main

import (
	"context"
	"errors"
	"log/slog"
	"os"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/tafakkur/backend/internal/config"
	"github.com/tafakkur/backend/internal/platform"
)

func main() {
	slog.SetDefault(slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: slog.LevelInfo})))

	cfg, err := config.LoadMigration()
	if err != nil {
		slog.Error("migration configuration invalid", "error", err)
		os.Exit(1)
	}

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Minute)
	defer cancel()

	connectCtx, cancelConnect := context.WithTimeout(ctx, 90*time.Second)
	pool, err := connectMigrationDatabase(connectCtx, cfg.DatabaseURL)
	cancelConnect()
	if err != nil {
		slog.Error("migration database unavailable", "error", err)
		os.Exit(1)
	}
	defer pool.Close()

	if err := platform.RunMigrationsAndReconcileAppRole(
		ctx,
		pool,
		cfg.MigrationsDir,
		cfg.AppDBUser,
		cfg.AppDBPassword,
	); err != nil {
		slog.Error("migration or application database role reconciliation failed", "error", err)
		os.Exit(1)
	}
	slog.Info("migrations applied and application database role reconciled")
}

func connectMigrationDatabase(ctx context.Context, databaseURL string) (*pgxpool.Pool, error) {
	const retryInterval = 2 * time.Second
	retry := time.NewTicker(retryInterval)
	defer retry.Stop()

	for {
		pool, err := platform.NewPool(ctx, databaseURL)
		if err == nil {
			return pool, nil
		}

		select {
		case <-ctx.Done():
			return nil, errors.New("database did not become reachable within the startup retry window")
		case <-retry.C:
			slog.Warn("migration database is not ready; retrying")
		}
	}
}
