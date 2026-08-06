package sync

import (
	"sync/atomic"
	"time"
)

// Metrics are process-wide counters for the sync platform. Cheap enough (atomic adds)
// to update on every batch, and the only way to answer the questions that matter when
// sync misbehaves in the field:
//
//	duplicates high        → clients are retrying batches they already delivered
//	stale high             → device clocks are wrong, or two devices are fighting
//	rejected high          → a client is shipping malformed events; find which entity
//	applyNanos/batches     → whether the bulk UPSERT is still one fast statement
//	eventsIn/batches       → real batch size; if it sits at 1 the coalescing is broken
type Metrics struct {
	Batches    atomic.Int64
	EventsIn   atomic.Int64
	Applied    atomic.Int64
	Duplicates atomic.Int64
	Rejected   atomic.Int64
	Stale      atomic.Int64
	Failures   atomic.Int64
	ApplyNanos atomic.Int64

	Pulls       atomic.Int64
	ChangesSent atomic.Int64
	PullNanos   atomic.Int64
}

// Snapshot is a readable copy for logging or an admin endpoint.
type Snapshot struct {
	Batches       int64   `json:"batches"`
	EventsIn      int64   `json:"events_in"`
	Applied       int64   `json:"applied"`
	Duplicates    int64   `json:"duplicates"`
	Rejected      int64   `json:"rejected"`
	Stale         int64   `json:"stale"`
	Failures      int64   `json:"failures"`
	AvgBatchSize  float64 `json:"avg_batch_size"`
	AvgApplyMs    float64 `json:"avg_apply_ms"`
	Pulls         int64   `json:"pulls"`
	ChangesSent   int64   `json:"changes_sent"`
	AvgPullMs     float64 `json:"avg_pull_ms"`
	DuplicateRate float64 `json:"duplicate_rate"`
}

func (m *Metrics) Snapshot() Snapshot {
	batches := m.Batches.Load()
	eventsIn := m.EventsIn.Load()
	pulls := m.Pulls.Load()
	s := Snapshot{
		Batches:     batches,
		EventsIn:    eventsIn,
		Applied:     m.Applied.Load(),
		Duplicates:  m.Duplicates.Load(),
		Rejected:    m.Rejected.Load(),
		Stale:       m.Stale.Load(),
		Failures:    m.Failures.Load(),
		Pulls:       pulls,
		ChangesSent: m.ChangesSent.Load(),
	}
	if batches > 0 {
		s.AvgBatchSize = float64(eventsIn) / float64(batches)
		s.AvgApplyMs = float64(m.ApplyNanos.Load()) / float64(batches) / float64(time.Millisecond)
	}
	if pulls > 0 {
		s.AvgPullMs = float64(m.PullNanos.Load()) / float64(pulls) / float64(time.Millisecond)
	}
	if eventsIn > 0 {
		s.DuplicateRate = float64(s.Duplicates) / float64(eventsIn)
	}
	return s
}
