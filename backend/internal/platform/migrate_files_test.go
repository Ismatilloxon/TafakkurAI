package platform

import (
	"os"
	"path/filepath"
	"reflect"
	"testing"
)

func TestMigrationVersionsIgnoresHiddenAndNonUpFiles(t *testing.T) {
	dir := t.TempDir()
	for _, name := range []string{
		"0002_second.up.sql",
		"0001_first.up.sql",
		"0001_first.down.sql",
		"._0003_appledouble.up.sql",
		".hidden.up.sql",
		"notes.txt",
	} {
		if err := os.WriteFile(filepath.Join(dir, name), nil, 0o600); err != nil {
			t.Fatalf("write test entry %q: %v", name, err)
		}
	}
	if err := os.Mkdir(filepath.Join(dir, "0003_directory.up.sql"), 0o700); err != nil {
		t.Fatalf("create test directory: %v", err)
	}

	entries, err := os.ReadDir(dir)
	if err != nil {
		t.Fatalf("read test directory: %v", err)
	}
	got := migrationVersions(entries)
	want := []string{"0001_first", "0002_second"}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("migrationVersions() = %v, want %v", got, want)
	}
}
