# TafakkurAI project rules

These rules apply to the whole repository. More specific rules in `backend/AGENTS.md`
and `shared/AGENTS.md` add to them.

## Workspace and build safety

- Inspect and change files only inside this repository. Outside it, use only the iOS
  Simulator and the toolchains/services needed to build or run this project.
- Never add or run a build hook that downloads or executes code. Treat obfuscated shell,
  `curl | sh`, decoded payloads, unexpected `ProcessBuilder`, and unexplained Xcode shell
  phases as a release blocker.
- Before every build, scan Gradle files and the Xcode project for executable hooks and
  confirm that only the documented Kotlin framework build phase remains.
- Preserve unrelated user changes. Never reset a dirty worktree to make a task easier.

## Product invariant: one connected system

TafakkurAI is not a collection of isolated screens. Every meaningful user action is one
immutable, idempotent `ActivityEvent` and must update every relevant read model from that
event. The feature that owns the action writes its source-of-truth state; integration code
projects the event into the rest of the product. UI code must not perform ad-hoc writes into
another feature's repository.

Required projections for every qualifying activity:

1. Today's plan: insert or update the activity's plan item and completion state.
2. Balance: update the appropriate spiritual, physical, social, educational, financial, or
   productivity input using real recorded data; use unknown/untracked instead of invented data.
3. Habits: connect recurring user intentions to a habit, and record completion when the
   corresponding activity occurs. Never create duplicate habits for repeated events.
4. Progression: award XP once per idempotency key, update streak (`ketma-ketlik`) from active
   local dates, and derive level (`Daraja`) from total XP.
5. Learning: a started Qur'an/surah, book, course, or vocabulary source creates an active
   learning goal and can generate review material and quizzes from the exact scope studied.
6. Insights: journal, gratitude, sleep, finance, social, and learning activity can inform
   recommendations only with transparent provenance and user-respecting privacy.

Examples that are acceptance requirements, not special cases:

- Starting a surah creates/links a Qur'an habit, adds a plan item, affects spiritual and
  educational balance, and makes quizzes for that surah available.
- Saving gratitude records the journal entry, completes/links the gratitude habit, updates
  today's plan and balance, and awards XP exactly once.
- Recording sleep updates today's recovery plan and physical balance without fabricating
  HealthKit data.
- Recording an expense updates finance history, the financial balance dimension, relevant
  budget/goal progress, and today's plan.
- Completing a focus, sport, social, learning, or worship activity follows the same event path.

Each event carries: stable event ID, user ID, feature, activity type, entity ID, occurred-at
instant, local date, timezone, source, schema version, and typed properties. Replays must be
safe. Source write, event append, and server-side projections are atomic or use a transactional
outbox. Local work is offline-first and syncs later without double-awarding or losing actions.

## Architecture and quality

- Use clean dependency direction: presentation -> domain <- data/platform. Domain code has no
  UI, HTTP, SQL, or platform-framework dependency.
- Give every mutable data type one single source of truth. Expose immutable state and explicit
  commands/events. Do not keep parallel mock and real stores in production paths.
- Put reusable controls, navigation chrome, search, fields, loading/error/empty states, cards,
  buttons, and accessibility behavior in shared UI component classes/files. A screen composes
  components; it does not clone them.
- Prefer a small cohesive class/file. Split files that mix routing, persistence, business rules,
  and rendering. Do not create abstraction layers without a real boundary or reuse case.
- All user-visible strings, including accessibility labels, errors, empty states, sample content,
  notifications, and formatted units/plurals, are localized. English, Uzbek Latin, Uzbek Cyrillic,
  and Russian catalogs must have identical key sets.
- Never ship a visible stub, dead button, sample balance, fake social/finance data, hardcoded user,
  or `false && featureGate`. Either connect the real behavior or hide it behind an explicit flag.
- Protect private spiritual, journal, health, finance, and AI data. Collect the minimum, keep
  secrets out of source/logs, and make destructive or externally visible AI actions previewable.

## Definition of done

A change is done only when its source-of-truth write, cross-feature projections, offline/sync
behavior, localization, accessibility, error/empty/loading states, and tests are complete. Run
the narrowest relevant tests first, then all backend and shared tests, and finally exercise the
affected iOS flows in the Simulator. Record any environment blocker honestly; never replace a
failed verification with an assumption.

