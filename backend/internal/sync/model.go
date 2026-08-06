// Package sync is the generic event-sync platform: one batch endpoint and one
// delta endpoint that serve every feature in the app, present and future.
//
// The defining property of this package is that it contains NO feature names.
// There is no switch on "prayer" or "dhikr", no per-feature DTO and no per-feature
// table. A feature is just a `(feature, entity)` string pair the client chose, and
// the payload is opaque jsonb. That is what makes "add a feature without touching
// the backend" true rather than aspirational.
//
// The one switch this package does contain is over *conflict strategies*, which is
// a closed set of five that describes how any two versions of a row merge. Adding a
// feature never adds a strategy; it picks one (or takes the default).
package sync

import (
	"encoding/json"
	"errors"
	"time"

	"github.com/google/uuid"
)

// Strategy names how two versions of the same row are reconciled. Stored per entity
// in the sync_policy table, so changing one is an UPDATE and not a deploy.
type Strategy string

const (
	// StrategyLWW keeps whichever version has the newer device clock. The default,
	// and the right answer for a row that only ever has one meaningful writer:
	// today's prayer mark, a journal entry's text, a profile field.
	StrategyLWW Strategy = "lww"

	// StrategyMerge merges key by key, incoming value winning per key. For rows
	// whose fields are edited by independent screens — a whole-object overwrite
	// would revert fields the other screen had just changed.
	StrategyMerge Strategy = "merge"

	// StrategyUnion unions array-valued keys. For set-shaped rows: bookmarks, tags,
	// completed items. Two devices each adding a different element keep both.
	StrategyUnion Strategy = "union"

	// StrategyIncrement adds numeric keys. Only correct when the client sends deltas,
	// which ours deliberately does not — kept because a future feature may need it.
	StrategyIncrement Strategy = "increment"

	// StrategyMax keeps the greater of two numeric keys. The correct strategy for
	// absolute counters: an out-of-order replay can never lower a total.
	StrategyMax Strategy = "max"
)

// setPayloadExpr is the SQL fragment that computes the merged payload for a strategy.
// `es` is the existing row, `excluded` the incoming one.
//
// This map is the entire per-strategy surface of the platform. Everything else —
// validation, batching, idempotency, cursors — is strategy-agnostic.
var setPayloadExpr = map[Strategy]string{
	StrategyLWW:       "EXCLUDED.payload",
	StrategyMerge:     "es.payload || EXCLUDED.payload",
	StrategyUnion:     "sync_union(es.payload, EXCLUDED.payload)",
	StrategyIncrement: "sync_increment(es.payload, EXCLUDED.payload)",
	StrategyMax:       "sync_max(es.payload, EXCLUDED.payload)",
}

func (s Strategy) valid() bool {
	_, ok := setPayloadExpr[s]
	return ok
}

// Operation is what happened to the row locally.
type Operation string

const (
	OpInsert Operation = "insert"
	OpUpdate Operation = "update"
	OpDelete Operation = "delete"
)

func (o Operation) valid() bool {
	return o == OpInsert || o == OpUpdate || o == OpDelete
}

// Limits. The batch cap bounds the transaction and the request body; the payload cap
// stops one pathological row (a base64 image pasted into a note) from making a batch
// unparseable for everything queued behind it.
const (
	MaxBatchEvents  = 100
	MaxPayloadBytes = 64 * 1024
	MaxIDLen        = 512
	MaxNameLen      = 64
	DefaultPullSize = 500
	MaxPullSize     = 1000
)

var (
	ErrBatchTooLarge = errors.New("too many events in one batch")
	ErrEmptyBatch    = errors.New("batch contains no events")
)

// Event is one local mutation, as the client recorded it. Identical shape for every
// feature — this struct is the whole wire contract.
type Event struct {
	// ID is generated on the device and is the idempotency key. Replaying a batch
	// after a timeout is safe because the second arrival collides on this.
	ID string `json:"id"`

	Feature   string          `json:"feature"`
	Entity    string          `json:"entity"`
	EntityID  string          `json:"entityId"`
	Operation Operation       `json:"operation"`
	Payload   json.RawMessage `json:"payload"`
	Metadata  json.RawMessage `json:"metadata"`

	// DeviceTime is epoch millis from the device clock at the moment of the local
	// write — not the moment of upload. An event that waited a week in an offline
	// queue must not out-rank a change made yesterday on another device.
	DeviceTime int64  `json:"deviceTime"`
	Timezone   string `json:"timezone"`
}

// EventResult reports the fate of one event so the client knows exactly which rows
// it may mark synced. Anything not reported applied stays in the local queue.
type EventResult struct {
	ID     string `json:"id"`
	Status string `json:"status"` // applied | duplicate | rejected | stale
	Reason string `json:"reason,omitempty"`
}

const (
	ResultApplied   = "applied"
	ResultDuplicate = "duplicate"
	ResultRejected  = "rejected"
	ResultStale     = "stale"
)

// Change is one row of the delta feed sent back to a device.
type Change struct {
	Feature   string          `json:"feature"`
	Entity    string          `json:"entity"`
	EntityID  string          `json:"entityId"`
	Payload   json.RawMessage `json:"payload"`
	Deleted   bool            `json:"deleted"`
	Seq       int64           `json:"seq"`
	UpdatedAt time.Time       `json:"updatedAt"`
}

// validate checks an event against the shape every feature must satisfy. It never
// looks at what the feature *is*: an unknown entity is a new feature, not an error.
func (e *Event) validate() error {
	if _, err := uuid.Parse(e.ID); err != nil {
		return errors.New("id must be a uuid")
	}
	switch {
	case e.Feature == "" || len(e.Feature) > MaxNameLen:
		return errors.New("feature must be 1..64 chars")
	case e.Entity == "" || len(e.Entity) > MaxNameLen:
		return errors.New("entity must be 1..64 chars")
	case e.EntityID == "" || len(e.EntityID) > MaxIDLen:
		return errors.New("entityId must be 1..512 chars")
	case !e.Operation.valid():
		return errors.New("operation must be insert, update or delete")
	case len(e.Payload) > MaxPayloadBytes:
		return errors.New("payload exceeds 64KB")
	case e.DeviceTime <= 0:
		return errors.New("deviceTime must be epoch millis")
	}
	// A device clock running far ahead would pin the row at last-write-wins forever
	// and no later change could ever displace it. Clamp rather than reject: the user
	// did do the thing, their clock is just wrong.
	return nil
}

// deviceTimeUTC converts the device clock to a timestamp, clamping anything more than
// a day in the future to now. Cheaper than trusting it and far cheaper than the
// support burden of a row nobody can ever update again.
func (e *Event) deviceTimeUTC(now time.Time) time.Time {
	t := time.UnixMilli(e.DeviceTime).UTC()
	if t.After(now.Add(24 * time.Hour)) {
		return now
	}
	return t
}

// payloadOrEmpty guarantees valid jsonb for the column. A delete carries no payload.
func (e *Event) payloadOrEmpty() string {
	if len(e.Payload) == 0 || string(e.Payload) == "null" {
		return "{}"
	}
	return string(e.Payload)
}
