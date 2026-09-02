# Sleep & Rest

Local-first sleep tracking, scoring, recovery and routines. Shared Compose Multiplatform UI,
shared business logic, one ViewModel.

## Layout

```
sleep/
  SleepModels.kt      domain types — sessions, stages, scores, goals, stats
  SleepTime.kt        clock arithmetic; circular statistics for bed/wake times
  SleepScoring.kt     sleep score, recovery score, nap verdicts
  SleepStatistics.kt  baselines, debt, consistency, trends, streaks, social jetlag
  SmartBedtime.kt     works backwards from wake time; cycle alignment
  SleepCoach.kt       deterministic recommendation rules
  SleepRoutine.kt     bedtime/morning steps + SleepSettings
  SleepStore.kt       SQLDelight-backed observable store + import conflict resolution
  SleepActivity.kt    activity event taxonomy over the app-wide ActivityLog
  SleepViewModel.kt   the one ViewModel behind every screen
  health/             HealthSleepBridge contract (+ iosMain / androidMain actuals)
  ui/                 components and screens
```

Backend: `backend/internal/sleep/`, migration `0010_sleep`.

## The three invariants

1. **Weights are redistributed, never defaulted to zero.** A phone-only user has no deep/REM
   data. Scoring that as 0 is why so many sleep apps show those users a permanent 60. See
   `SleepScoring.Components`.

2. **Bed/wake times use circular statistics.** 23:50 and 00:10 are 20 minutes apart, not 23h40m.
   Every average and the whole consistency score depend on this. See `SleepTime`.

3. **Source rank decides conflicts, not recency.** A phone estimate must never overwrite a
   watch measurement. The rule is implemented three times — `SleepMerge.strength` (Kotlin),
   `Session.Strength` (Go), `sleep_strength()` (SQL) — and **all three must agree**, or a
   device and the server will disagree about a night forever.

## Sync

Nothing to register. `sleepSession` and `sleepRoutineMark` are ordinary tables, so `SyncEngine`
discovers them at the next `attach()`, installs triggers and backfills. Activity events go
through `ActivityLog` into the same outbox with an inline payload.

## Setup required before first build

**Android — Health Connect.** Not enabled in the current build. It must be added as one complete
change: the official client dependency, minimum read permissions in the manifest, a permission
launcher owned by `MainActivity`, an adapter, and device tests. Until then Android deliberately
uses `NoOpHealthSleepBridge`; it must not claim that device sleep is connected.

**iOS — HealthKit.** No Gradle change: `platform.HealthKit` ships with the Kotlin/Native Apple
platform libraries. Needs the HealthKit capability on the app target and
`NSHealthShareUsageDescription` in `Info.plist`, or authorization fails silently.

**Wiring.** `initKoin(..., healthBridge = HealthKitSleepBridge())` on iOS. Android currently uses
the default `NoOpHealthSleepBridge`, which leaves manual logging, routines, smart bedtime and the
coach usable while honestly reporting automatic device import as unavailable.

## Not implemented

Widgets, Lock Screen widgets, Live Activities, Dynamic Island, and the Apple Watch / Wear OS
companion apps are **not** in this module. Each needs a new build target — an Xcode
target/extension or a Gradle module — not just source files.

`SleepViewModel` already exposes everything they need as plain values
(`sleepScore`, `recoveryScore`, `minutesUntilBedtime`, `bedtimePlan`, `stats`,
`SleepCoach.morningSummary`), and `SleepActivityType` already carries their event cases
(`WIDGET_TAPPED`, `LIVE_ACTIVITY_STARTED`, `WATCH_CONNECTED`).

Notification *content* and *timing* are likewise decided here — `SleepCoach.bedtimeNudge`,
`SleepCoach.morningSummary`, `SmartBedtime.plan().windDownStartMinutes` — but nothing
schedules them; that needs `UNUserNotificationCenter` / `WorkManager` at the platform layer.

## Tests

`shared/src/commonTest/kotlin/com/example/tafakkurai/sleep/` — 80+ cases over the pure logic:
clock arithmetic, every scoring curve, weight redistribution, baselines, debt, consistency
across midnight, trends, smart bedtime, import conflict resolution, and the routine opt-in.

They pass explicit `offsetHours = 0.0` throughout so no test depends on a device clock.
