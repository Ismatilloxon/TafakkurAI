package platform

import (
	"context"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"golang.org/x/crypto/pbkdf2"
)

const (
	managedAppRoleComment   = "tafakkurai:managed-app-role:v1"
	postgresSCRAMIterations = 4096
	postgresSCRAMSaltBytes  = 16
)

// ReconcileAppRole creates or rotates the non-owner role used by the API and
// reapplies its least-privilege grants after every migration run. Keeping this
// beside migrations makes both fresh databases and existing volumes converge
// to the same privilege state.
func ReconcileAppRole(ctx context.Context, pool *pgxpool.Pool, role, password string) error {
	return withMigrationLock(ctx, pool, func(conn *pgxpool.Conn) error {
		return reconcileAppRole(ctx, conn, role, password)
	})
}

type transactionStarter interface {
	BeginTx(context.Context, pgx.TxOptions) (pgx.Tx, error)
}

func reconcileAppRole(ctx context.Context, database transactionStarter, role, password string) error {
	if !safeDatabaseRole(role) {
		return fmt.Errorf("application database role name is invalid")
	}
	if !safeDatabasePassword(password) {
		return fmt.Errorf("application database role password is invalid")
	}
	passwordVerifier, err := newPostgresSCRAMVerifier(password)
	if err != nil {
		return err
	}

	tx, err := database.BeginTx(ctx, pgx.TxOptions{})
	if err != nil {
		return fmt.Errorf("begin application role reconciliation: %w", err)
	}
	defer func() {
		rollbackCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		_ = tx.Rollback(rollbackCtx)
	}()

	// Even a failed CREATE/ALTER ROLE must not put its password verifier into
	// PostgreSQL's error or DDL statement logs. The high-entropy cleartext
	// password never crosses the database protocol at all.
	if _, err := tx.Exec(ctx, `SET LOCAL log_statement = 'none'`); err != nil {
		return fmt.Errorf("disable role reconciliation statement logging: %w", err)
	}
	if _, err := tx.Exec(ctx, `SET LOCAL log_min_error_statement = 'panic'`); err != nil {
		return fmt.Errorf("disable role reconciliation error statement logging: %w", err)
	}

	var databaseName, migrationRole string
	if err := tx.QueryRow(ctx, `SELECT current_database(), current_user`).Scan(&databaseName, &migrationRole); err != nil {
		return fmt.Errorf("read migration database identity: %w", err)
	}
	if role == migrationRole {
		return fmt.Errorf("application database role must differ from migration role")
	}

	var roleExists bool
	if err := tx.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = $1)`, role).Scan(&roleExists); err != nil {
		return fmt.Errorf("inspect application database role: %w", err)
	}
	if roleExists {
		var roleComment *string
		err := tx.QueryRow(ctx,
			`SELECT shobj_description(oid, 'pg_authid') FROM pg_roles WHERE rolname = $1`,
			role,
		).Scan(&roleComment)
		if errors.Is(err, pgx.ErrNoRows) {
			return fmt.Errorf("application database role disappeared during reconciliation")
		}
		if err != nil {
			return fmt.Errorf("inspect application database role marker: %w", err)
		}
		if roleComment == nil || *roleComment != managedAppRoleComment {
			return fmt.Errorf("application database role already exists but is not managed by TafakkurAI")
		}
	}

	quotedRole := pgx.Identifier{role}.Sanitize()
	roleStatement := "CREATE ROLE " + quotedRole
	if roleExists {
		roleStatement = "ALTER ROLE " + quotedRole
	}
	roleStatement += " WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS CONNECTION LIMIT 20 PASSWORD '" + passwordVerifier + "' VALID UNTIL 'infinity'"
	if _, err := tx.Exec(ctx, roleStatement); err != nil {
		return fmt.Errorf("create or rotate application database role: %w", err)
	}
	commentStatement := "COMMENT ON ROLE " + quotedRole + " IS '" + managedAppRoleComment + "'"
	if _, err := tx.Exec(ctx, commentStatement); err != nil {
		return fmt.Errorf("mark application database role as managed: %w", err)
	}

	var ownsObjects bool
	if err := tx.QueryRow(ctx, `
		SELECT EXISTS (
			SELECT 1 FROM pg_database d JOIN pg_roles r ON r.oid = d.datdba WHERE r.rolname = $1
			UNION ALL
			SELECT 1 FROM pg_namespace n JOIN pg_roles r ON r.oid = n.nspowner WHERE r.rolname = $1
			UNION ALL
			SELECT 1 FROM pg_class c JOIN pg_roles r ON r.oid = c.relowner WHERE r.rolname = $1
			UNION ALL
			SELECT 1 FROM pg_proc p JOIN pg_roles r ON r.oid = p.proowner WHERE r.rolname = $1
			UNION ALL
			SELECT 1 FROM pg_type t JOIN pg_roles r ON r.oid = t.typowner WHERE r.rolname = $1
		)
	`, role).Scan(&ownsObjects); err != nil {
		return fmt.Errorf("inspect application role ownership: %w", err)
	}
	if ownsObjects {
		return fmt.Errorf("application database role owns database objects; refusing an unsafe privilege reconciliation")
	}

	rows, err := tx.Query(ctx, `
		SELECT granted.rolname
		FROM pg_auth_members membership
		JOIN pg_roles granted ON granted.oid = membership.roleid
		JOIN pg_roles member ON member.oid = membership.member
		WHERE member.rolname = $1
	`, role)
	if err != nil {
		return fmt.Errorf("inspect application role memberships: %w", err)
	}
	var memberships []string
	for rows.Next() {
		var membership string
		if err := rows.Scan(&membership); err != nil {
			rows.Close()
			return fmt.Errorf("read application role membership: %w", err)
		}
		memberships = append(memberships, membership)
	}
	if err := rows.Err(); err != nil {
		rows.Close()
		return fmt.Errorf("read application role memberships: %w", err)
	}
	rows.Close()
	for _, membership := range memberships {
		statement := "REVOKE " + pgx.Identifier{membership}.Sanitize() + " FROM " + quotedRole
		if _, err := tx.Exec(ctx, statement); err != nil {
			return fmt.Errorf("revoke application role membership: %w", err)
		}
	}

	rows, err = tx.Query(ctx, `
		SELECT member.rolname
		FROM pg_auth_members membership
		JOIN pg_roles granted ON granted.oid = membership.roleid
		JOIN pg_roles member ON member.oid = membership.member
		WHERE granted.rolname = $1
	`, role)
	if err != nil {
		return fmt.Errorf("inspect application role members: %w", err)
	}
	var roleMembers []string
	for rows.Next() {
		var member string
		if err := rows.Scan(&member); err != nil {
			rows.Close()
			return fmt.Errorf("read application role member: %w", err)
		}
		roleMembers = append(roleMembers, member)
	}
	if err := rows.Err(); err != nil {
		rows.Close()
		return fmt.Errorf("read application role members: %w", err)
	}
	rows.Close()
	for _, member := range roleMembers {
		statement := "REVOKE " + quotedRole + " FROM " + pgx.Identifier{member}.Sanitize()
		if _, err := tx.Exec(ctx, statement); err != nil {
			return fmt.Errorf("revoke member access to application role: %w", err)
		}
	}

	quotedDatabase := pgx.Identifier{databaseName}.Sanitize()
	quotedMigrationRole := pgx.Identifier{migrationRole}.Sanitize()
	statements := []string{
		"REVOKE ALL PRIVILEGES ON DATABASE " + quotedDatabase + " FROM " + quotedRole,
		"GRANT CONNECT ON DATABASE " + quotedDatabase + " TO " + quotedRole,
		"REVOKE ALL PRIVILEGES ON SCHEMA public FROM " + quotedRole,
		"REVOKE CREATE ON SCHEMA public FROM PUBLIC",
		"GRANT USAGE ON SCHEMA public TO " + quotedRole,
		"REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM " + quotedRole,
		"REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public FROM " + quotedRole,
		"GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO " + quotedRole,
		"GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO " + quotedRole,
		"REVOKE ALL PRIVILEGES ON TABLE schema_migrations FROM " + quotedRole,
		"REVOKE ALL PRIVILEGES ON TABLE schema_migrations FROM PUBLIC",
		"ALTER DEFAULT PRIVILEGES FOR ROLE " + quotedMigrationRole + " IN SCHEMA public REVOKE ALL PRIVILEGES ON TABLES FROM " + quotedRole,
		"ALTER DEFAULT PRIVILEGES FOR ROLE " + quotedMigrationRole + " IN SCHEMA public REVOKE ALL PRIVILEGES ON SEQUENCES FROM " + quotedRole,
		"ALTER DEFAULT PRIVILEGES FOR ROLE " + quotedMigrationRole + " IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO " + quotedRole,
		"ALTER DEFAULT PRIVILEGES FOR ROLE " + quotedMigrationRole + " IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO " + quotedRole,
	}
	for _, statement := range statements {
		if _, err := tx.Exec(ctx, statement); err != nil {
			return fmt.Errorf("apply application database privileges: %w", err)
		}
	}

	if err := tx.Commit(ctx); err != nil {
		return fmt.Errorf("commit application role reconciliation: %w", err)
	}
	return nil
}

func safeDatabaseRole(value string) bool {
	if len(value) == 0 || len(value) > 63 || strings.HasPrefix(value, "pg_") {
		return false
	}
	for i, r := range value {
		if (r >= 'a' && r <= 'z') || r == '_' || (i > 0 && r >= '0' && r <= '9') {
			continue
		}
		return false
	}
	return true
}

func safeDatabasePassword(value string) bool {
	if len(value) < 32 {
		return false
	}
	for _, r := range value {
		if (r < '0' || r > '9') && (r < 'a' || r > 'f') {
			return false
		}
	}
	return true
}

func newPostgresSCRAMVerifier(password string) (string, error) {
	salt := make([]byte, postgresSCRAMSaltBytes)
	if _, err := rand.Read(salt); err != nil {
		return "", fmt.Errorf("generate application database password verifier: %w", err)
	}
	return postgresSCRAMVerifier(password, salt), nil
}

func postgresSCRAMVerifier(password string, salt []byte) string {
	saltedPassword := pbkdf2.Key(
		[]byte(password),
		salt,
		postgresSCRAMIterations,
		sha256.Size,
		sha256.New,
	)
	clientKey := hmacSHA256(saltedPassword, []byte("Client Key"))
	storedKey := sha256.Sum256(clientKey)
	serverKey := hmacSHA256(saltedPassword, []byte("Server Key"))

	return fmt.Sprintf(
		"SCRAM-SHA-256$%d:%s$%s:%s",
		postgresSCRAMIterations,
		base64.StdEncoding.EncodeToString(salt),
		base64.StdEncoding.EncodeToString(storedKey[:]),
		base64.StdEncoding.EncodeToString(serverKey),
	)
}

func hmacSHA256(key, data []byte) []byte {
	mac := hmac.New(sha256.New, key)
	_, _ = mac.Write(data)
	return mac.Sum(nil)
}
