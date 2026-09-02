package sync

import (
	"encoding/json"
	"testing"
	"time"
)

// Activity events intentionally have no server-side feature registration. A new UUID per
// action makes the generic LWW projection append-only, and shape-only validation must accept
// the entity so mobile releases can add activities without a coordinated backend deploy.
func TestActivityEventUsesGenericSyncContract(t *testing.T) {
	e := Event{
		ID:         "c05e7a85-9c7d-4f41-8ce3-cc34f42a73cb",
		Feature:    "productivity",
		Entity:     "activityEvent",
		EntityID:   "c05e7a85-9c7d-4f41-8ce3-cc34f42a73cb",
		Operation:  OpInsert,
		Payload:    json.RawMessage(`{"type":"focus_started","at":42,"remainingSeconds":1500}`),
		Metadata:   json.RawMessage(`{"platform":"android"}`),
		DeviceTime: time.Now().UnixMilli(),
		Timezone:   "Asia/Tashkent",
	}

	if err := e.validate(); err != nil {
		t.Fatalf("activity event should satisfy the generic sync contract: %v", err)
	}
}

func TestEventLocalDateUsesRecordedTimezone(t *testing.T) {
	instant := time.Date(2026, 1, 9, 19, 30, 0, 0, time.UTC)
	e := Event{
		DeviceTime: instant.UnixMilli(),
		Timezone:   "Asia/Tashkent",
	}

	if got := e.localDate(instant).Format(time.DateOnly); got != "2026-01-10" {
		t.Errorf("local date = %s, want 2026-01-10", got)
	}
}

func TestEventValidateRejectsUnknownTimezone(t *testing.T) {
	e := Event{
		ID:         "c05e7a85-9c7d-4f41-8ce3-cc34f42a73cb",
		Feature:    "productivity",
		Entity:     "activityEvent",
		EntityID:   "c05e7a85-9c7d-4f41-8ce3-cc34f42a73cb",
		Operation:  OpInsert,
		Payload:    json.RawMessage(`{}`),
		DeviceTime: time.Now().UnixMilli(),
		Timezone:   "Mars/Olympus_Mons",
	}

	if err := e.validate(); err == nil {
		t.Fatal("unknown timezone should be rejected")
	}
}
