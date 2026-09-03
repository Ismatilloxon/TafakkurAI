package config

import (
	"strings"
	"testing"
)

func TestLoadMigrationValidatesSeparatedCredentials(t *testing.T) {
	const (
		adminPassword = "11111111111111111111111111111111"
		appPassword   = "22222222222222222222222222222222"
	)

	tests := []struct {
		name        string
		databaseURL string
		appUser     string
		appPassword string
		wantError   bool
	}{
		{
			name:        "valid",
			databaseURL: "postgres://tafakkur_admin:" + adminPassword + "@postgres:5432/tafakkur?sslmode=disable",
			appUser:     "tafakkur_app",
			appPassword: appPassword,
		},
		{
			name:        "same role",
			databaseURL: "postgres://tafakkur_app:" + adminPassword + "@postgres:5432/tafakkur?sslmode=disable",
			appUser:     "tafakkur_app",
			appPassword: appPassword,
			wantError:   true,
		},
		{
			name:        "same password",
			databaseURL: "postgres://tafakkur_admin:" + appPassword + "@postgres:5432/tafakkur?sslmode=disable",
			appUser:     "tafakkur_app",
			appPassword: appPassword,
			wantError:   true,
		},
		{
			name:        "unsafe role",
			databaseURL: "postgres://tafakkur_admin:" + adminPassword + "@postgres:5432/tafakkur?sslmode=disable",
			appUser:     "Tafakkur-App",
			appPassword: appPassword,
			wantError:   true,
		},
		{
			name:        "predefined postgres role",
			databaseURL: "postgres://tafakkur_admin:" + adminPassword + "@postgres:5432/tafakkur?sslmode=disable",
			appUser:     "pg_read_all_data",
			appPassword: appPassword,
			wantError:   true,
		},
		{
			name:        "short password",
			databaseURL: "postgres://tafakkur_admin:" + adminPassword + "@postgres:5432/tafakkur?sslmode=disable",
			appUser:     "tafakkur_app",
			appPassword: "abcd",
			wantError:   true,
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			t.Setenv("DATABASE_URL", test.databaseURL)
			t.Setenv("APP_DB_USER", test.appUser)
			t.Setenv("APP_DB_PASSWORD", test.appPassword)
			t.Setenv("MIGRATIONS_DIR", "migrations")

			_, err := LoadMigration()
			if test.wantError && err == nil {
				t.Fatal("LoadMigration() error = nil, want error")
			}
			if !test.wantError && err != nil {
				t.Fatalf("LoadMigration() error = %v", err)
			}
			if err != nil && strings.Contains(err.Error(), test.appPassword) {
				t.Fatal("LoadMigration() error exposed a database password")
			}
		})
	}
}
