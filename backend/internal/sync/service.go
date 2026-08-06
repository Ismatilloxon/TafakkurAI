package sync

import (
	"context"
	"log/slog"
	"time"

	"github.com/google/uuid"

	"github.com/tafakkur/backend/internal/gamify"
)

// Service applies batches and serves deltas. It is the only orchestration layer, and
// it is feature-blind by construction: the only thing it does with `entity` is look up
// a merge strategy for it.
type Service interface {
	ApplyBatch(ctx context.Context, userID uuid.UUID, events []Event) (BatchResult, error)
	Changes(ctx context.Context, userID uuid.UUID, cursor int64, limit int) (ChangeSet, error)
	Metrics() Snapshot
}

// BatchResult is what the device needs to advance its queue: a verdict per event, plus
// the head cursor so a client that just pushed can pull from the right place without a
// second round trip.
type BatchResult struct {
	Results []EventResult    `json:"results"`
	Cursor  int64            `json:"cursor"`
	Gamify  *gamify.State    `json:"gamification,omitempty"`
	Stats   BatchResultStats `json:"stats"`
}

type BatchResultStats struct {
	Received   int `json:"received"`
	Applied    int `json:"applied"`
	Duplicates int `json:"duplicates"`
	Rejected   int `json:"rejected"`
	Stale      int `json:"stale"`
}

type ChangeSet struct {
	Changes []Change `json:"changes"`
	Cursor  int64    `json:"cursor"`
	More    bool     `json:"more"`
}

type service struct {
	repo    *Repo
	gamify  gamify.Service
	metrics *Metrics
	now     func() time.Time
}

func NewService(repo *Repo, g gamify.Service) Service {
	return &service{repo: repo, gamify: g, metrics: &Metrics{}, now: func() time.Time { return time.Now().UTC() }}
}

func (s *service) Metrics() Snapshot { return s.metrics.Snapshot() }

// ApplyBatch is the whole write path.
//
// Order of operations, and the reason for each:
//
//  1. validate — shape only. An unrecognised entity is a feature that shipped after
//     this server did, which must work, so it is never an error.
//  2. collapse duplicates *within* the batch — two events for one row in one statement
//     would abort the UPSERT ("cannot affect row a second time"). The client's outbox
//     already coalesces, so this is defence against a buggy or older client.
//  3. one transaction — claim ids, then one bulk statement per strategy group. A batch
//     of 100 events touching four strategies is four statements and one commit.
//  4. one gamification write for the whole batch, not one per event.
func (s *service) ApplyBatch(ctx context.Context, userID uuid.UUID, events []Event) (BatchResult, error) {
	started := s.now()
	s.metrics.Batches.Add(1)
	s.metrics.EventsIn.Add(int64(len(events)))

	res := BatchResult{Results: make([]EventResult, 0, len(events))}
	res.Stats.Received = len(events)

	// 1 + 2: validate and collapse. `latest` keeps the newest event per row.
	type slot struct {
		event Event
		index int
	}
	latest := make(map[applyKey]slot, len(events))
	order := make([]applyKey, 0, len(events))
	accepted := make([]Event, 0, len(events))

	for i := range events {
		e := events[i]
		if err := e.validate(); err != nil {
			res.Results = append(res.Results, EventResult{ID: e.ID, Status: ResultRejected, Reason: err.Error()})
			res.Stats.Rejected++
			s.metrics.Rejected.Add(1)
			continue
		}
		k := applyKey{entity: e.Entity, entityID: e.EntityID}
		prev, seen := latest[k]
		if !seen {
			order = append(order, k)
			latest[k] = slot{event: e, index: i}
			continue
		}
		// Superseded within the same batch: the older one is reported applied so the
		// device clears it, because the newer one carries its effect.
		if e.DeviceTime >= prev.event.DeviceTime {
			res.Results = append(res.Results, EventResult{ID: prev.event.ID, Status: ResultApplied})
			res.Stats.Applied++
			latest[k] = slot{event: e, index: i}
		} else {
			res.Results = append(res.Results, EventResult{ID: e.ID, Status: ResultApplied})
			res.Stats.Applied++
		}
	}
	for _, k := range order {
		accepted = append(accepted, latest[k].event)
	}

	if len(accepted) == 0 {
		res.Cursor, _ = s.repo.HeadSeq(ctx, userID)
		return res, nil
	}

	policies, err := s.repo.Policies(ctx)
	if err != nil {
		s.metrics.Failures.Add(1)
		return res, err
	}

	tx, err := s.repo.Begin(ctx)
	if err != nil {
		s.metrics.Failures.Add(1)
		return res, err
	}
	defer func() { _ = tx.Rollback(ctx) }()

	// 3a: idempotency. Events already seen are reported duplicate and never re-applied.
	fresh, err := s.repo.ClaimEvents(ctx, tx, userID, accepted)
	if err != nil {
		s.metrics.Failures.Add(1)
		return res, err
	}

	byStrategy := make(map[Strategy][]Event, 4)
	for _, e := range accepted {
		if !fresh[e.ID] {
			res.Results = append(res.Results, EventResult{ID: e.ID, Status: ResultDuplicate})
			res.Stats.Duplicates++
			s.metrics.Duplicates.Add(1)
			continue
		}
		st, ok := policies[e.Entity]
		if !ok {
			st = StrategyLWW // an entity nobody configured — i.e. a new feature
		}
		byStrategy[st] = append(byStrategy[st], e)
	}

	// 3b: one bulk statement per strategy group.
	now := s.now()
	written := make(map[applyKey]bool, len(accepted))
	applyStart := time.Now()
	for st, group := range byStrategy {
		w, err := s.repo.Apply(ctx, tx, userID, st, group, now)
		if err != nil {
			s.metrics.Failures.Add(1)
			return res, err
		}
		for k := range w {
			written[k] = true
		}
	}
	s.metrics.ApplyNanos.Add(time.Since(applyStart).Nanoseconds())

	appliedToday := false
	for _, group := range byStrategy {
		for _, e := range group {
			k := applyKey{entity: e.Entity, entityID: e.EntityID}
			if written[k] {
				res.Results = append(res.Results, EventResult{ID: e.ID, Status: ResultApplied})
				res.Stats.Applied++
				s.metrics.Applied.Add(1)
				if sameUTCDay(e.deviceTimeUTC(now), now) {
					appliedToday = true
				}
			} else {
				// Filtered by the last-write-wins guard: a newer version is already
				// stored. Reported (not retried) so the device stops carrying it.
				res.Results = append(res.Results, EventResult{ID: e.ID, Status: ResultStale})
				res.Stats.Stale++
				s.metrics.Stale.Add(1)
			}
		}
	}

	if err := tx.Commit(ctx); err != nil {
		s.metrics.Failures.Add(1)
		return res, err
	}

	// 4: exactly one gamification write per batch, and only for the streak.
	//
	// XP for a *specific* action stays with that action's own endpoint. Awarding XP per
	// synced event would make a user's level depend on how often their phone happened
	// to flush the queue, and would put per-feature meaning back into a package whose
	// whole point is not having any.
	if appliedToday && s.gamify != nil {
		if st, err := s.gamify.Award(ctx, userID, gamify.EventDailyCheckin); err == nil {
			res.Gamify = &st
		} else {
			slog.Warn("sync: gamify award failed", "user", userID, "error", err)
		}
	}

	res.Cursor, _ = s.repo.HeadSeq(ctx, userID)

	slog.Info("sync batch",
		"user", userID,
		"received", res.Stats.Received,
		"applied", res.Stats.Applied,
		"duplicates", res.Stats.Duplicates,
		"stale", res.Stats.Stale,
		"rejected", res.Stats.Rejected,
		"ms", time.Since(started).Milliseconds())

	return res, nil
}

func (s *service) Changes(ctx context.Context, userID uuid.UUID, cursor int64, limit int) (ChangeSet, error) {
	start := time.Now()
	if limit <= 0 || limit > MaxPullSize {
		limit = DefaultPullSize
	}
	changes, next, err := s.repo.Changes(ctx, userID, cursor, limit)
	if err != nil {
		s.metrics.Failures.Add(1)
		return ChangeSet{}, err
	}
	s.metrics.Pulls.Add(1)
	s.metrics.ChangesSent.Add(int64(len(changes)))
	s.metrics.PullNanos.Add(time.Since(start).Nanoseconds())

	return ChangeSet{
		Changes: changes,
		Cursor:  next,
		// A full page means there is probably another one. The client keeps pulling
		// until this is false, which is how a fresh device backfills from cursor 0.
		More: len(changes) == limit,
	}, nil
}

func sameUTCDay(a, b time.Time) bool {
	ay, am, ad := a.Date()
	by, bm, bd := b.Date()
	return ay == by && am == bm && ad == bd
}
