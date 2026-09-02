package sync

import (
	"context"
	"errors"
	"log/slog"
	"sort"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"

	"github.com/tafakkur/backend/internal/gamify"
)

// Service applies batches and serves deltas. Mutable state remains feature-blind; the
// one typed entity is the app-wide immutable ActivityEvent integration contract.
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

type transactionalGamifier interface {
	AwardBatchTx(
		ctx context.Context,
		tx pgx.Tx,
		userID uuid.UUID,
		commands []gamify.AwardCommand,
	) (gamify.State, error)
}

func NewService(repo *Repo, g gamify.Service) Service {
	return &service{repo: repo, gamify: g, metrics: &Metrics{}, now: func() time.Time { return time.Now().UTC() }}
}

func (s *service) Metrics() Snapshot { return s.metrics.Snapshot() }

// ApplyBatch is the whole write path.
//
// Order of operations, and the reason for each:
//
//  1. validate — generic rows keep the open feature contract; immutable activityEvent
//     facts additionally validate their versioned envelope and repeated fields.
//  2. collapse updates *within* the batch — two events for one row in one statement
//     would abort the UPSERT ("cannot affect row a second time"). The client's outbox
//     already coalesces, so this is defence against a buggy or older client.
//  3. one transaction — claim ids, bulk-project state, and award one idempotent daily
//     progression command per distinct ActivityEvent local date before one commit.
func (s *service) ApplyBatch(ctx context.Context, userID uuid.UUID, events []Event) (BatchResult, error) {
	started := s.now()
	s.metrics.Batches.Add(1)
	s.metrics.EventsIn.Add(int64(len(events)))

	res := BatchResult{}
	res.Stats.Received = len(events)
	if userID == uuid.Nil {
		s.metrics.Failures.Add(1)
		return res, errors.New("sync user id is required")
	}

	verdicts := make([]EventResult, len(events))
	decided := make([]bool, len(events))
	setVerdict := func(index int, status, reason string) {
		verdicts[index] = EventResult{ID: events[index].ID, Status: status, Reason: reason}
		decided[index] = true
	}
	finalize := func() {
		res.Results = make([]EventResult, 0, len(events))
		for i := range verdicts {
			if !decided[i] {
				// This is an internal invariant guard. It should never be visible, but
				// retaining the event on the device is safer than acknowledging it.
				verdicts[i] = EventResult{ID: events[i].ID, Status: ResultRejected, Reason: "event was not processed"}
			}
			res.Results = append(res.Results, verdicts[i])
			switch verdicts[i].Status {
			case ResultApplied:
				res.Stats.Applied++
				s.metrics.Applied.Add(1)
			case ResultDuplicate:
				res.Stats.Duplicates++
				s.metrics.Duplicates.Add(1)
			case ResultRejected:
				res.Stats.Rejected++
				s.metrics.Rejected.Add(1)
			case ResultStale:
				res.Stats.Stale++
				s.metrics.Stale.Add(1)
			}
		}
	}

	// 1 + 2: validate, reject conflicting repeated IDs, and retain all unique valid
	// events for the ledger. Row-level coalescing happens only after claim outcomes are
	// known, so a conflicting latest event cannot accidentally consume an older valid one.
	valid := make([]Event, 0, len(events))
	indices := make(map[string]int, len(events))
	fingerprints := make(map[string]string, len(events))
	groups := make(map[applyKey][]Event, len(events))

	for i := range events {
		e := events[i]
		if err := e.validate(); err != nil {
			setVerdict(i, ResultRejected, err.Error())
			continue
		}
		fingerprint, err := e.fingerprint()
		if err != nil {
			setVerdict(i, ResultRejected, "event payload could not be fingerprinted")
			continue
		}
		if prior, seen := fingerprints[e.ID]; seen {
			if prior == fingerprint {
				setVerdict(i, ResultDuplicate, "duplicate event id within batch")
			} else {
				setVerdict(i, ResultRejected, "event id is reused with different content")
			}
			continue
		}
		fingerprints[e.ID] = fingerprint
		indices[e.ID] = i
		valid = append(valid, e)
		k := applyKey{entity: e.Entity, entityID: e.EntityID}
		groups[k] = append(groups[k], e)
	}

	if len(valid) == 0 {
		var err error
		res.Cursor, err = s.repo.HeadSeq(ctx, userID)
		if err != nil {
			s.metrics.Failures.Add(1)
			return res, err
		}
		finalize()
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

	// 3a: idempotency. Replays never re-apply; a UUID with changed semantics is rejected.
	claims, err := s.repo.ClaimEvents(ctx, tx, userID, valid)
	if err != nil {
		s.metrics.Failures.Add(1)
		return res, err
	}

	// Choose the latest non-conflicting event per row. A ledger duplicate is eligible:
	// its effect already committed on an earlier request and therefore supersedes an
	// older fresh update that was coalesced out of that request.
	winners := make(map[applyKey]string, len(groups))
	for key, group := range groups {
		var winner Event
		hasWinner := false
		for _, e := range group {
			if claims[e.ID] == claimConflict {
				continue
			}
			if !hasWinner || e.DeviceTime >= winner.DeviceTime {
				winner = e
				hasWinner = true
			}
		}
		if hasWinner {
			winners[key] = winner.ID
		}
	}

	byStrategy := make(map[Strategy][]Event, 4)
	activityDates := make(map[string]time.Time)
	for _, e := range valid {
		index := indices[e.ID]
		switch claims[e.ID] {
		case claimConflict:
			setVerdict(index, ResultRejected, "event id was already used by different content")
			continue
		case claimDuplicate:
			setVerdict(index, ResultDuplicate, "")
			// A pre-atomic server may have committed the immutable fact before its
			// best-effort progression write. Re-running the date command repairs that
			// historical gap, and gamification's own ledger makes the repair a no-op
			// when the original award did commit.
			if localDate, ok := e.activityLocalDate(); ok {
				activityDates[localDate.Format(time.DateOnly)] = localDate
			}
			continue
		case claimFresh:
			key := applyKey{entity: e.Entity, entityID: e.EntityID}
			if winners[key] != e.ID {
				// The newer event carries this row's semantic effect. Its idempotency
				// claim still commits so a later retry cannot resurrect stale state.
				setVerdict(index, ResultApplied, "")
				continue
			}
		default:
			return res, errors.New("sync event has no claim outcome")
		}

		st, ok := policies[e.Entity]
		if !ok {
			st = StrategyLWW // an entity nobody configured — i.e. a new feature
		}
		byStrategy[st] = append(byStrategy[st], e)
	}

	// 3b: one bulk statement per strategy group.
	now := s.now()
	written := make(map[applyKey]bool, len(valid))
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

	for _, group := range byStrategy {
		for _, e := range group {
			index := indices[e.ID]
			k := applyKey{entity: e.Entity, entityID: e.EntityID}
			if written[k] {
				setVerdict(index, ResultApplied, "")
				if localDate, ok := e.activityLocalDate(); ok {
					activityDates[localDate.Format(time.DateOnly)] = localDate
				}
				continue
			}
			// Filtered by the last-write-wins guard: a newer version is already
			// stored. Reported (not retried) so the device stops carrying it.
			setVerdict(index, ResultStale, "")
		}
	}

	// 3c: progression participates in this exact transaction. Generic table/settings
	// sync never earns XP; only a validated, successfully projected ActivityEvent does.
	if len(activityDates) > 0 {
		dateKeys := make([]string, 0, len(activityDates))
		for key := range activityDates {
			dateKeys = append(dateKeys, key)
		}
		sort.Strings(dateKeys)

		commands := make([]gamify.AwardCommand, 0, len(dateKeys))
		for _, key := range dateKeys {
			commands = append(commands, gamify.AwardCommand{
				Event:          gamify.EventDailyCheckin,
				IdempotencyKey: "sync:daily:" + key,
				LocalDate:      activityDates[key],
			})
		}
		atomicGamify, ok := s.gamify.(transactionalGamifier)
		if !ok {
			s.metrics.Failures.Add(1)
			return res, errors.New("transactional gamification is unavailable")
		}
		st, err := atomicGamify.AwardBatchTx(ctx, tx, userID, commands)
		if err != nil {
			s.metrics.Failures.Add(1)
			return res, err
		}
		res.Gamify = &st
	}

	res.Cursor, err = s.repo.HeadSeqTx(ctx, tx, userID)
	if err != nil {
		s.metrics.Failures.Add(1)
		return res, err
	}
	if err := tx.Commit(ctx); err != nil {
		s.metrics.Failures.Add(1)
		return res, err
	}
	finalize()

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
