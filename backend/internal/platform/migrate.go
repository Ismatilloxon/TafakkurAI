package platform

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

const migrationLockID int64 = 0x544146414b4b5552 // "TAFAKKUR"

// RunMigrations applies every *.up.sql file in dir (lexicographic order) that
// has not been applied yet, tracking state in a schema_migrations table. It is
// a tiny, dependency-free forward-only runner. Do not point another migration
// tool at this database: its tracking schema is intentionally private to this
// runner. Recover by fixing forward or restoring a reviewed backup.
func RunMigrations(ctx context.Context, pool *pgxpool.Pool, dir string) error {
	return withMigrationLock(ctx, pool, func(conn *pgxpool.Conn) error {
		return runMigrations(ctx, conn, dir)
	})
}

// RunMigrationsAndReconcileAppRole holds one session advisory lock across both
// schema changes and runtime-role password/grant reconciliation. This prevents
// concurrent deploys from modifying the application role at the same time.
func RunMigrationsAndReconcileAppRole(
	ctx context.Context,
	pool *pgxpool.Pool,
	dir string,
	role string,
	password string,
) error {
	return withMigrationLock(ctx, pool, func(conn *pgxpool.Conn) error {
		if err := runMigrations(ctx, conn, dir); err != nil {
			return err
		}
		return reconcileAppRole(ctx, conn, role, password)
	})
}

func withMigrationLock(
	ctx context.Context,
	pool *pgxpool.Pool,
	work func(*pgxpool.Conn) error,
) error {
	conn, err := pool.Acquire(ctx)
	if err != nil {
		return fmt.Errorf("acquire migration connection: %w", err)
	}
	defer func() {
		unlockCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		var unlocked bool
		unlockErr := conn.QueryRow(unlockCtx, `SELECT pg_advisory_unlock($1)`, migrationLockID).Scan(&unlocked)
		if unlockErr != nil || !unlocked {
			// Never return a possibly locked session to the pool. Hijacking removes
			// it from the pool even if closing the underlying connection also fails.
			hijacked := conn.Hijack()
			_ = hijacked.Close(unlockCtx)
			return
		}
		conn.Release()
	}()

	if _, err := conn.Exec(ctx, `SELECT pg_advisory_lock($1)`, migrationLockID); err != nil {
		return fmt.Errorf("acquire migration lock: %w", err)
	}
	return work(conn)
}

func runMigrations(ctx context.Context, conn *pgxpool.Conn, dir string) error {
	if _, err := conn.Exec(ctx, `CREATE TABLE IF NOT EXISTS schema_migrations (
		version    text PRIMARY KEY,
		checksum   text,
		applied_at timestamptz NOT NULL DEFAULT now()
	)`); err != nil {
		return fmt.Errorf("ensure schema_migrations: %w", err)
	}
	if _, err := conn.Exec(ctx, `ALTER TABLE schema_migrations ADD COLUMN IF NOT EXISTS checksum text`); err != nil {
		return fmt.Errorf("ensure migration checksum: %w", err)
	}

	entries, err := os.ReadDir(dir)
	if err != nil {
		return fmt.Errorf("read migrations dir %q: %w", dir, err)
	}

	versions := migrationVersions(entries)

	for _, v := range versions {
		sqlBytes, err := os.ReadFile(filepath.Join(dir, v+".up.sql"))
		if err != nil {
			return fmt.Errorf("read migration %s: %w", v, err)
		}
		digest := sha256.Sum256(sqlBytes)
		checksum := hex.EncodeToString(digest[:])

		var appliedChecksum *string
		err = conn.QueryRow(ctx,
			`SELECT checksum FROM schema_migrations WHERE version = $1`, v,
		).Scan(&appliedChecksum)
		switch {
		case errors.Is(err, pgx.ErrNoRows):
			// Not applied yet.
		case err != nil:
			return fmt.Errorf("check migration %s: %w", v, err)
		case appliedChecksum == nil:
			// Upgrade legacy rows created before checksums were tracked. From this
			// point onward any edit to an applied migration fails startup.
			if _, err := conn.Exec(ctx,
				`UPDATE schema_migrations SET checksum = $2 WHERE version = $1 AND checksum IS NULL`,
				v, checksum,
			); err != nil {
				return fmt.Errorf("record checksum for migration %s: %w", v, err)
			}
			continue
		case *appliedChecksum != checksum:
			return fmt.Errorf("migration %s checksum does not match the applied version", v)
		default:
			continue
		}

		tx, err := conn.Begin(ctx)
		if err != nil {
			return fmt.Errorf("begin migration %s: %w", v, err)
		}
		if _, err := tx.Exec(ctx, string(sqlBytes)); err != nil {
			_ = tx.Rollback(ctx)
			return fmt.Errorf("apply migration %s: %w", v, err)
		}
		if _, err := tx.Exec(ctx,
			`INSERT INTO schema_migrations (version, checksum) VALUES ($1, $2)`, v, checksum); err != nil {
			_ = tx.Rollback(ctx)
			return fmt.Errorf("record migration %s: %w", v, err)
		}
		if err := tx.Commit(ctx); err != nil {
			return fmt.Errorf("commit migration %s: %w", v, err)
		}
	}
	return nil
}

func migrationVersions(entries []os.DirEntry) []string {
	var versions []string
	for _, e := range entries {
		name := e.Name()
		// macOS can emit AppleDouble sidecar files named ._*. A hidden file is
		// never a migration, even if its metadata filename ends in .up.sql.
		if !e.IsDir() && !strings.HasPrefix(name, ".") && strings.HasSuffix(name, ".up.sql") {
			versions = append(versions, strings.TrimSuffix(name, ".up.sql"))
		}
	}
	sort.Strings(versions)
	return versions
}
