//go:build integration

// Package integration exercises the repositories and services against a real
// Postgres (spun up with testcontainers). Run with: go test -tags=integration ./...
package integration

import (
	"context"
	"testing"
	"time"

	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/modules/postgres"
	"github.com/testcontainers/testcontainers-go/wait"

	"github.com/tafakkur/backend/internal/coach"
	"github.com/tafakkur/backend/internal/feed"
	"github.com/tafakkur/backend/internal/gamify"
	"github.com/tafakkur/backend/internal/platform"
	"github.com/tafakkur/backend/internal/user"

	"github.com/jackc/pgx/v5/pgxpool"
)

func setup(t *testing.T) (*pgxpool.Pool, func()) {
	t.Helper()
	ctx := context.Background()

	pgC, err := postgres.Run(ctx, "postgres:16-alpine",
		postgres.WithDatabase("tafakkur"),
		postgres.WithUsername("tafakkur"),
		postgres.WithPassword("tafakkur"),
		testcontainers.WithWaitStrategy(
			wait.ForLog("database system is ready to accept connections").
				WithOccurrence(2).WithStartupTimeout(60*time.Second)),
	)
	if err != nil {
		t.Fatalf("start postgres: %v", err)
	}

	dsn, err := pgC.ConnectionString(ctx, "sslmode=disable")
	if err != nil {
		t.Fatalf("dsn: %v", err)
	}
	pool, err := platform.NewPool(ctx, dsn)
	if err != nil {
		t.Fatalf("pool: %v", err)
	}
	if err := platform.RunMigrations(ctx, pool, "../../migrations"); err != nil {
		t.Fatalf("migrate: %v", err)
	}

	return pool, func() {
		pool.Close()
		_ = pgC.Terminate(ctx)
	}
}

func TestAuthUpsertProfileFeedCoach(t *testing.T) {
	ctx := context.Background()
	pool, teardown := setup(t)
	defer teardown()

	gam := gamify.NewService(gamify.NewRepo(pool))
	users := user.NewService(user.NewRepo(pool), gam)

	// --- auth upsert (idempotent on provider identity) ---
	email := "aisha@example.com"
	name := "Aisha"
	ident := user.ProviderIdentity{Provider: "google", Subject: "google-sub-1", Email: &email, DisplayName: &name}

	acc, isNew, err := users.UpsertByProvider(ctx, ident)
	if err != nil {
		t.Fatalf("upsert: %v", err)
	}
	if !isNew {
		t.Fatal("first upsert should be new")
	}
	if acc.User.Provider != "google" || acc.Profile.OnboardingDone {
		t.Fatalf("unexpected account: %+v", acc)
	}

	_, isNew2, err := users.UpsertByProvider(ctx, ident)
	if err != nil || isNew2 {
		t.Fatalf("second upsert should be existing (isNew=%v err=%v)", isNew2, err)
	}

	// gamification row was created
	if st, err := gam.Get(ctx, acc.User.ID); err != nil || st.Level != 1 {
		t.Fatalf("gamification not initialised: st=%+v err=%v", st, err)
	}

	// --- profile update ---
	newName := "Aisha R."
	updated, err := users.UpdateProfile(ctx, acc.User.ID, user.UpdateProfileInput{DisplayName: &newName})
	if err != nil {
		t.Fatalf("update profile: %v", err)
	}
	if updated.Profile.DisplayName != "Aisha R." {
		t.Fatalf("display name not updated: %q", updated.Profile.DisplayName)
	}

	// --- feed publish + read back with author name ---
	feedSvc := feed.NewService(feed.NewRepo(pool), users, gam)
	post, err := feedSvc.Publish(ctx, acc.User.ID, "Alhamdulillah for today")
	if err != nil {
		t.Fatalf("publish: %v", err)
	}
	if post.AuthorName != "Aisha R." {
		t.Fatalf("author name = %q, want %q", post.AuthorName, "Aisha R.")
	}

	page, err := feedSvc.Feed(ctx, acc.User.ID, "", 10)
	if err != nil {
		t.Fatalf("feed: %v", err)
	}
	if len(page.Posts) != 1 || page.Posts[0].Text != "Alhamdulillah for today" {
		t.Fatalf("unexpected feed: %+v", page.Posts)
	}
	// publishing awarded XP
	if st, _ := gam.Get(ctx, acc.User.ID); st.XP < 5 {
		t.Fatalf("publish should award XP, got %d", st.XP)
	}

	// --- coach conversation persistence ---
	cRepo := coach.NewRepo(pool)
	convID, err := cRepo.CreateConversation(ctx, acc.User.ID)
	if err != nil {
		t.Fatalf("create conversation: %v", err)
	}
	if _, err := cRepo.AddMessage(ctx, convID, "user", "Assalamu alaikum"); err != nil {
		t.Fatalf("add user msg: %v", err)
	}
	if _, err := cRepo.AddMessage(ctx, convID, "assistant", "Wa alaikum assalam."); err != nil {
		t.Fatalf("add assistant msg: %v", err)
	}
	msgs, err := cRepo.ContextMessages(ctx, convID, 10)
	if err != nil {
		t.Fatalf("context messages: %v", err)
	}
	if len(msgs) != 2 || msgs[0].Role != "user" || msgs[1].Role != "assistant" {
		t.Fatalf("unexpected messages: %+v", msgs)
	}
}
