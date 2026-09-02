//go:build integration

package integration

import (
	"context"
	"sync"
	"testing"
	"time"

	"github.com/google/uuid"

	"github.com/tafakkur/backend/internal/gamify"
)

func TestGamificationAwardsAreAtomicAndIdempotent(t *testing.T) {
	ctx := context.Background()
	pool, teardown := setup(t)
	defer teardown()

	insertUser := func(subject string) uuid.UUID {
		t.Helper()
		userID := uuid.New()
		if _, err := pool.Exec(ctx, `
			INSERT INTO users (id, provider, provider_subject)
			VALUES ($1, 'test', $2)`, userID, subject); err != nil {
			t.Fatalf("insert user: %v", err)
		}
		return userID
	}

	t.Run("replay returns first result without duplicate XP", func(t *testing.T) {
		userID := insertUser("gamify-idempotency")
		service := gamify.NewService(gamify.NewRepo(pool))
		command := gamify.AwardCommand{
			Event:          gamify.EventTrackerEntry,
			IdempotencyKey: "tracker:entry-1",
			LocalDate:      civilDay(2026, time.January, 10),
		}

		first, err := service.AwardOnce(ctx, userID, command)
		if err != nil {
			t.Fatalf("first award: %v", err)
		}
		second, err := service.AwardOnce(ctx, userID, command)
		if err != nil {
			t.Fatalf("replayed award: %v", err)
		}
		if !first.Applied || second.Applied {
			t.Fatalf("applied flags = (%v, %v), want (true, false)", first.Applied, second.Applied)
		}
		if first.State != second.State {
			t.Fatalf("replay state = %+v, want original %+v", second.State, first.State)
		}

		state, err := service.Get(ctx, userID)
		if err != nil {
			t.Fatalf("get state: %v", err)
		}
		if state.XP != 5 {
			t.Fatalf("xp = %d, want 5", state.XP)
		}

		changedDate := command
		changedDate.LocalDate = civilDay(2026, time.January, 11)
		changedReplay, err := service.AwardOnce(ctx, userID, changedDate)
		if err != nil {
			t.Fatalf("replay after midnight: %v", err)
		}
		if changedReplay.Applied || changedReplay.State != first.State {
			t.Fatalf("replay after midnight = %+v, want original %+v", changedReplay, first)
		}
	})

	t.Run("historical day fills streak gap", func(t *testing.T) {
		userID := insertUser("gamify-local-days")
		service := gamify.NewService(gamify.NewRepo(pool))

		commands := []gamify.AwardCommand{
			{
				Event:          gamify.EventDailyCheckin,
				IdempotencyKey: "sync:daily:2026-01-10",
				LocalDate:      civilDay(2026, time.January, 10),
			},
			{
				Event:          gamify.EventDailyCheckin,
				IdempotencyKey: "sync:daily:2026-01-12",
				LocalDate:      civilDay(2026, time.January, 12),
			},
		}
		if _, err := service.AwardBatch(ctx, userID, commands); err != nil {
			t.Fatalf("initial awards: %v", err)
		}

		filled, err := service.AwardOnce(ctx, userID, gamify.AwardCommand{
			Event:          gamify.EventDailyCheckin,
			IdempotencyKey: "sync:daily:2026-01-11",
			LocalDate:      civilDay(2026, time.January, 11),
		})
		if err != nil {
			t.Fatalf("historical award: %v", err)
		}
		if filled.State.Streak != 3 {
			t.Fatalf("streak = %d, want 3", filled.State.Streak)
		}
		if got := filled.State.LastActiveDate.Format(time.DateOnly); got != "2026-01-12" {
			t.Fatalf("last active date = %s, want 2026-01-12", got)
		}
	})

	t.Run("concurrent unique commands do not lose XP", func(t *testing.T) {
		userID := insertUser("gamify-concurrent-unique")
		service := gamify.NewService(gamify.NewRepo(pool))
		const count = 16

		start := make(chan struct{})
		errs := make(chan error, count)
		var workers sync.WaitGroup
		workers.Add(count)
		for i := 0; i < count; i++ {
			i := i
			go func() {
				defer workers.Done()
				<-start
				_, err := service.AwardOnce(ctx, userID, gamify.AwardCommand{
					Event:          gamify.EventTrackerEntry,
					IdempotencyKey: "tracker:" + uuid.NewSHA1(uuid.Nil, []byte{byte(i)}).String(),
					LocalDate:      civilDay(2026, time.January, 20),
				})
				errs <- err
			}()
		}
		close(start)
		workers.Wait()
		close(errs)
		for err := range errs {
			if err != nil {
				t.Fatalf("concurrent award: %v", err)
			}
		}

		state, err := service.Get(ctx, userID)
		if err != nil {
			t.Fatalf("get state: %v", err)
		}
		if state.XP != count*5 {
			t.Fatalf("xp = %d, want %d", state.XP, count*5)
		}
		if state.Streak != 1 {
			t.Fatalf("streak = %d, want 1", state.Streak)
		}
	})

	t.Run("concurrent retries apply once", func(t *testing.T) {
		userID := insertUser("gamify-concurrent-replay")
		service := gamify.NewService(gamify.NewRepo(pool))
		const count = 12

		command := gamify.AwardCommand{
			Event:          gamify.EventLessonCompleted,
			IdempotencyKey: "lesson:shared-command",
			LocalDate:      civilDay(2026, time.January, 21),
		}
		start := make(chan struct{})
		errs := make(chan error, count)
		applied := make(chan bool, count)
		var workers sync.WaitGroup
		workers.Add(count)
		for i := 0; i < count; i++ {
			go func() {
				defer workers.Done()
				<-start
				result, err := service.AwardOnce(ctx, userID, command)
				errs <- err
				applied <- result.Applied
			}()
		}
		close(start)
		workers.Wait()
		close(errs)
		close(applied)

		for err := range errs {
			if err != nil {
				t.Fatalf("concurrent replay: %v", err)
			}
		}
		appliedCount := 0
		for wasApplied := range applied {
			if wasApplied {
				appliedCount++
			}
		}
		if appliedCount != 1 {
			t.Fatalf("applied count = %d, want 1", appliedCount)
		}

		state, err := service.Get(ctx, userID)
		if err != nil {
			t.Fatalf("get state: %v", err)
		}
		if state.XP != 20 {
			t.Fatalf("xp = %d, want 20", state.XP)
		}
	})
}

func civilDay(year int, month time.Month, day int) time.Time {
	return time.Date(year, month, day, 0, 0, 0, 0, time.UTC)
}
