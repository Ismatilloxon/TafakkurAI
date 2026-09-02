// Package sync is the generic event-sync platform: one batch endpoint and one
// delta endpoint that serve every feature in the app, present and future.
//
// Mutable feature state stays open: there is no switch on "prayer" or "dhikr", no
// per-feature DTO and no per-feature table. A feature is just a `(feature, entity)`
// string pair the client chose, and its payload is opaque jsonb. The deliberate
// exception is activityEvent, the app-wide immutable integration contract whose stable
// envelope is validated before it can affect history or progression.
//
// The one switch this package does contain is over *conflict strategies*, which is
// a closed set of five that describes how any two versions of a row merge. Adding a
// feature never adds a strategy; it picks one (or takes the default).
package sync

import (
	"crypto/sha256"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	stdsync "sync"
	"time"
	_ "time/tzdata" // Distroless runtime has no guaranteed host timezone database.

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
	MaxBatchEvents   = 100
	MaxPayloadBytes  = 64 * 1024
	MaxMetadataBytes = 4 * 1024
	MaxIDLen         = 512
	MaxNameLen       = 64
	MaxTimezoneLen   = 64
	DefaultPullSize  = 500
	MaxPullSize      = 1000

	// ActivityEventEntity is the one append-only fact carried by the otherwise
	// state-oriented sync contract. Its payload has a versioned, validated envelope.
	ActivityEventEntity  = "activityEvent"
	MaxActivityTypeLen   = 64
	MaxActivitySourceLen = 32
)

var (
	ErrBatchTooLarge = errors.New("too many events in one batch")
	ErrEmptyBatch    = errors.New("batch contains no events")
	timezoneCache    stdsync.Map
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
	case len(e.Metadata) > MaxMetadataBytes:
		return errors.New("metadata exceeds 4KB")
	case e.DeviceTime <= 0:
		return errors.New("deviceTime must be epoch millis")
	case e.Timezone == "" || len(e.Timezone) > MaxTimezoneLen:
		return errors.New("timezone must be a 1..64 character IANA timezone id")
	}
	if _, err := loadLocation(e.Timezone); err != nil {
		return errors.New("timezone must be a valid IANA timezone id")
	}
	if err := validateJSONObject(e.Payload, e.Operation == OpDelete, "payload"); err != nil {
		return err
	}
	if err := validateJSONObject(e.Metadata, true, "metadata"); err != nil {
		return err
	}
	if e.Entity == ActivityEventEntity {
		return e.validateActivityEvent()
	}
	// A device clock running far ahead would pin the row at last-write-wins forever
	// and no later change could ever displace it. Clamp rather than reject: the user
	// did do the thing, their clock is just wrong.
	return nil
}

// activityEnvelope is deliberately only the stable envelope. Everything else in the
// JSON object is a feature-owned typed property and remains opaque to this package.
type activityEnvelope struct {
	Type          string `json:"type"`
	OccurredAt    int64  `json:"at"`
	EntityID      string `json:"entityId"`
	LocalDate     string `json:"localDate"`
	Timezone      string `json:"timezone"`
	Source        string `json:"source"`
	SchemaVersion int    `json:"schemaVersion"`
}

// validateActivityEvent protects the immutable product fact before it reaches either
// the idempotency ledger or any projection. The outer sync fields and inner envelope
// intentionally repeat identity/time information; requiring them to agree prevents one
// event from producing different history, streak and delta-feed interpretations.
func (e *Event) validateActivityEvent() error {
	if e.Operation != OpInsert {
		return errors.New("activityEvent is append-only and must use insert")
	}
	if e.EntityID != e.ID {
		return errors.New("activityEvent entityId must equal event id")
	}
	if err := e.normalizeLegacyActivityEvent(); err != nil {
		return err
	}
	if len(e.Payload) > MaxPayloadBytes {
		return errors.New("normalized activityEvent payload exceeds 64KB")
	}

	var activity activityEnvelope
	if err := json.Unmarshal(e.Payload, &activity); err != nil {
		return errors.New("activityEvent payload is invalid")
	}
	switch {
	case strings.TrimSpace(activity.Type) != activity.Type || activity.Type == "" || len(activity.Type) > MaxActivityTypeLen:
		return errors.New("activityEvent type must be 1..64 trimmed characters")
	case activity.OccurredAt <= 0:
		return errors.New("activityEvent at must be epoch millis")
	case activity.OccurredAt != e.DeviceTime:
		return errors.New("activityEvent at must equal deviceTime")
	case strings.TrimSpace(activity.EntityID) != activity.EntityID || activity.EntityID == "" || len(activity.EntityID) > MaxIDLen:
		return errors.New("activityEvent source entityId must be 1..512 trimmed characters")
	case activity.Timezone != e.Timezone:
		return errors.New("activityEvent timezone must equal event timezone")
	case strings.TrimSpace(activity.Source) != activity.Source || activity.Source == "" || len(activity.Source) > MaxActivitySourceLen:
		return errors.New("activityEvent source must be 1..32 trimmed characters")
	case activity.SchemaVersion <= 0:
		return errors.New("activityEvent schemaVersion must be positive")
	}

	localDate, err := time.Parse(time.DateOnly, activity.LocalDate)
	if err != nil || localDate.Format(time.DateOnly) != activity.LocalDate {
		return errors.New("activityEvent localDate must be a valid yyyy-mm-dd date")
	}
	// localDate is the product's civil-day attribution, not necessarily the wall-clock
	// day of OccurredAt. A compensation made today can legitimately target yesterday's
	// sleep or prayer row; requiring derivation from the instant would reject that fact.
	return nil
}

// normalizeLegacyActivityEvent is a wire-compatibility bridge for events already queued
// by the pre-envelope client. Those payloads carried only type/at plus properties. The
// authenticated owner and outer sync contract let the server complete the immutable v1
// envelope before storing it, so peers never receive an untyped historical row. Once a
// schemaVersion is present, no repair is attempted: malformed versioned data is rejected.
func (e *Event) normalizeLegacyActivityEvent() error {
	fields := make(map[string]json.RawMessage)
	if err := json.Unmarshal(e.Payload, &fields); err != nil {
		return errors.New("activityEvent payload is invalid")
	}
	if _, versioned := fields["schemaVersion"]; versioned {
		return nil
	}

	var activityType string
	if raw, ok := fields["type"]; !ok || json.Unmarshal(raw, &activityType) != nil || strings.TrimSpace(activityType) == "" {
		return errors.New("legacy activityEvent type is required")
	}
	location, err := loadLocation(e.Timezone)
	if err != nil {
		return errors.New("activityEvent timezone must be a valid IANA timezone id")
	}
	local := time.UnixMilli(e.DeviceTime).In(location)
	localDate := time.Date(local.Year(), local.Month(), local.Day(), 0, 0, 0, 0, time.UTC).Format(time.DateOnly)

	putJSON := func(name string, value any) error {
		encoded, err := json.Marshal(value)
		if err != nil {
			return err
		}
		fields[name] = encoded
		return nil
	}
	for name, value := range map[string]any{
		"at":            e.DeviceTime,
		"entityId":      e.EntityID,
		"localDate":     localDate,
		"timezone":      e.Timezone,
		"source":        "legacy",
		"schemaVersion": 1,
	} {
		if err := putJSON(name, value); err != nil {
			return fmt.Errorf("normalize legacy activityEvent: %w", err)
		}
	}
	normalized, err := json.Marshal(fields)
	if err != nil {
		return fmt.Errorf("normalize legacy activityEvent payload: %w", err)
	}
	e.Payload = normalized
	return nil
}

func validateJSONObject(raw json.RawMessage, optional bool, name string) error {
	trimmed := strings.TrimSpace(string(raw))
	if trimmed == "" || trimmed == "null" {
		if optional {
			return nil
		}
		return fmt.Errorf("%s must be a JSON object", name)
	}
	if !json.Valid(raw) || trimmed[0] != '{' {
		return fmt.Errorf("%s must be a JSON object", name)
	}
	return nil
}

// fingerprint is the semantic identity stored beside an idempotency key. JSON is
// decoded and re-encoded first so harmless whitespace or object-key ordering changes
// are still a replay, while changing any projected field becomes a rejected collision.
// Metadata is transport-only and intentionally does not change source semantics.
func (e *Event) fingerprint() (string, error) {
	var payload any
	decoder := json.NewDecoder(strings.NewReader(e.payloadOrEmpty()))
	decoder.UseNumber()
	if err := decoder.Decode(&payload); err != nil {
		return "", err
	}
	canonicalPayload, err := json.Marshal(payload)
	if err != nil {
		return "", err
	}
	document := struct {
		Feature    string          `json:"feature"`
		Entity     string          `json:"entity"`
		EntityID   string          `json:"entityId"`
		Operation  Operation       `json:"operation"`
		Payload    json.RawMessage `json:"payload"`
		DeviceTime int64           `json:"deviceTime"`
		Timezone   string          `json:"timezone"`
	}{
		Feature: e.Feature, Entity: e.Entity, EntityID: e.EntityID,
		Operation: e.Operation, Payload: canonicalPayload,
		DeviceTime: e.DeviceTime, Timezone: e.Timezone,
	}
	encoded, err := json.Marshal(document)
	if err != nil {
		return "", err
	}
	return fmt.Sprintf("%x", sha256.Sum256(encoded)), nil
}

func (e *Event) activityLocalDate() (time.Time, bool) {
	if e.Entity != ActivityEventEntity {
		return time.Time{}, false
	}
	var activity activityEnvelope
	if err := json.Unmarshal(e.Payload, &activity); err != nil {
		return time.Time{}, false
	}
	date, err := time.Parse(time.DateOnly, activity.LocalDate)
	return date, err == nil
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

// localDate returns the civil date on which the action occurred in the timezone
// recorded with that action. The result uses UTC only as a date-only container;
// it is not the UTC date of the original instant.
func (e *Event) localDate(now time.Time) time.Time {
	location, err := loadLocation(e.Timezone)
	if err != nil {
		// validate rejects this before an event reaches the write path. Keeping the
		// helper total avoids a second error branch inside the transaction loop.
		location = time.UTC
	}
	local := e.deviceTimeUTC(now).In(location)
	return time.Date(local.Year(), local.Month(), local.Day(), 0, 0, 0, 0, time.UTC)
}

func loadLocation(name string) (*time.Location, error) {
	if cached, ok := timezoneCache.Load(name); ok {
		return cached.(*time.Location), nil
	}
	location, err := time.LoadLocation(name)
	if err != nil {
		return nil, err
	}
	actual, _ := timezoneCache.LoadOrStore(name, location)
	return actual.(*time.Location), nil
}

// payloadOrEmpty guarantees valid jsonb for the column. A delete carries no payload.
func (e *Event) payloadOrEmpty() string {
	if len(e.Payload) == 0 || string(e.Payload) == "null" {
		return "{}"
	}
	return string(e.Payload)
}
