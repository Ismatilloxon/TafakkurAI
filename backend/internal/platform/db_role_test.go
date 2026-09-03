package platform

import (
	"strings"
	"testing"
)

func TestSafeDatabaseRole(t *testing.T) {
	for _, role := range []string{"tafakkur_app", "_service", "app2"} {
		if !safeDatabaseRole(role) {
			t.Errorf("safeDatabaseRole(%q) = false, want true", role)
		}
	}
	for _, role := range []string{"", "2app", "App", "app-role", "app role", "pg_read_all_data", string(make([]byte, 64))} {
		if safeDatabaseRole(role) {
			t.Errorf("safeDatabaseRole(%q) = true, want false", role)
		}
	}
}

func TestSafeDatabasePassword(t *testing.T) {
	if !safeDatabasePassword("0123456789abcdef0123456789abcdef") {
		t.Fatal("safeDatabasePassword() rejected a 32-character lowercase hex secret")
	}
	for _, password := range []string{
		"0123456789abcdef",
		"0123456789ABCDEF0123456789ABCDEF",
		"0123456789abcdef0123456789abcde'",
	} {
		if safeDatabasePassword(password) {
			t.Fatal("safeDatabasePassword() accepted an unsafe secret")
		}
	}
}

func TestPostgresSCRAMVerifier(t *testing.T) {
	const password = "0123456789abcdef0123456789abcdef"
	salt := []byte("0123456789abcdef")
	verifier := postgresSCRAMVerifier(password, salt)

	if strings.Contains(verifier, password) {
		t.Fatal("postgresSCRAMVerifier() exposed the cleartext password")
	}
	const want = "SCRAM-SHA-256$4096:MDEyMzQ1Njc4OWFiY2RlZg==$25d+aWLIlJ0hkMNOPqUdol8wtOM/9rVomUmdpvE3Rbk=:RS9p8Dx716UosxQRUuddDGXwqWEVCEMq+XscOjvcTuY="
	if verifier != want {
		t.Fatalf("postgresSCRAMVerifier() = %q, want known verifier", verifier)
	}

	first, err := newPostgresSCRAMVerifier(password)
	if err != nil {
		t.Fatalf("newPostgresSCRAMVerifier() error = %v", err)
	}
	second, err := newPostgresSCRAMVerifier(password)
	if err != nil {
		t.Fatalf("newPostgresSCRAMVerifier() second error = %v", err)
	}
	if first == second {
		t.Fatal("newPostgresSCRAMVerifier() reused a salt")
	}
}
