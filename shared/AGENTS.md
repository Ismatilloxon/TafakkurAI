# KMP mobile engineering rules

These rules apply to `shared/`, `androidApp/`, and the Compose-facing parts of `iosApp/`. They
supplement the repository rules.

## Sources and priorities

Google/Android architecture and Compose guidance:

- https://developer.android.com/topic/architecture
- https://developer.android.com/topic/architecture/recommendations
- https://developer.android.com/topic/architecture/ui-layer
- https://developer.android.com/develop/ui/compose/performance/bestpractices
- https://developer.android.com/guide/topics/resources/localization

Apple platform and Human Interface guidance:

- https://developer.apple.com/documentation/swiftui/navigationstack
- https://developer.apple.com/design/human-interface-guidelines/gestures
- https://developer.apple.com/documentation/swiftui/search
- https://developer.apple.com/design/human-interface-guidelines/search-fields
- https://developer.apple.com/design/human-interface-guidelines/accessibility
- https://developer.apple.com/documentation/healthkit
- https://developer.apple.com/documentation/xcode/localizing-and-varying-text-with-a-string-catalog

## Clean KMP architecture

- Organize each feature as `domain`, `data`, and `presentation`; platform adapters live in
  `androidMain`/`iosMain`. `commonMain` domain logic must remain platform-independent and testable.
- A screen consumes one immutable `UiState` and sends typed `UiEvent`s to a state holder/ViewModel.
  State flows down; events flow up. Composables do not call SQL, Koin lookups, HTTP, or another
  feature's store directly.
- Repositories own data reconciliation and expose one single source of truth. For offline-first
  features, SQLDelight is the source of truth; network sync updates it and UI observes it.
- Use cases represent user actions and cross-feature workflows. A use case writes source state and
  emits one `ActivityEvent`; an integration projector connects today's plan, balance, habits, XP,
  streak, level, recommendations, and quizzes. Screens never duplicate that orchestration.
- Use `expect`/`actual` or injected interfaces only for true platform capabilities: navigation host,
  native search, image picker, secure storage, notifications, locale/date formatting, HealthKit/
  Health Connect, audio, and on-device AI.
- Do not pass database/network DTOs into UI. Map transport -> data -> domain -> UI models when the
  shapes or lifecycles differ.

## Native platform behavior

- On iOS, use SwiftUI `NavigationStack`/UIKit navigation for app-level hierarchical destinations
  when a native host can own the stack. This preserves platform push/pop animation, Back controls,
  state restoration, and edge-swipe behavior. If a Compose-owned nested flow cannot use the host,
  provide the familiar Back button plus an iOS edge-back gesture and matching directional animation.
- Never use a crossfade for hierarchical push/pop navigation. Tabs may switch without a push
  animation; modal work uses sheets; detail navigation moves forward/back according to direction.
- Searchable screens use the shared search component. Its iOS implementation uses native search
  behavior (`.searchable`/`UISearchController` or `UISearchBar`) with immediate filtering, clear,
  cancel, keyboard Search, accessibility labels, and optional scopes/suggestions. Do not clone
  hand-built text fields in individual screens.
- Respect safe areas, keyboard insets, Dynamic Type/font scaling, VoiceOver/TalkBack semantics,
  Reduce Motion, contrast, 44x44pt iOS touch targets, Android minimum touch targets, and RTL layout.
- Prefer familiar native wording and interactions. Never draw fake status bars, notches, home
  indicators, or system permission UI inside the shipped application.

## Reusable UI components

- Put shared primitives and patterns under `com.example.tafakkurai.ui.components`: screen scaffold,
  top/back bar, large title, search, text/secure fields, buttons, cards, rows, tabs/segments, dialogs,
  sheets, avatar/photo picker, progress/XP/streak/level, and loading/error/empty/offline states.
- Components own visuals, semantics, focus/keyboard handling, disabled/loading states, and design
  tokens. Feature screens supply data and events. If a pattern appears twice, extract it before a
  third copy appears.
- Keep feature-specific components beside the feature. Split files that exceed a coherent screen or
  mix models, persistence, calculations, and hundreds of UI helpers.

## Compose performance

- Do no I/O or expensive sorting/filtering/calculation in a composable body. Produce it in a
  ViewModel/use case or cache with correctly keyed `remember`; use `derivedStateOf` for rapidly
  changing derived UI state.
- Give every lazy item a stable unique `key` and, for heterogeneous lists, `contentType`. Do not use
  `Column`/`Row` for unbounded collections. Keep lambdas and models stable where measurement shows
  recomposition cost.
- Collect lifecycle-aware state, cancel work with the owning scope, debounce expensive search, and
  avoid polling when callbacks/flows exist. Never retry location or network work in a tight UI loop.
- Measure release builds with platform tooling before claiming a performance improvement. Add a
  benchmark or regression test for optimized calculations.

## Localization and content

- No literal user-facing text in composables, ViewModels, domain errors, notifications, AI action
  labels, accessibility semantics, or sample/default content. Use a typed key and the shared catalog.
- English is the complete fallback catalog. Uzbek Latin, Uzbek Cyrillic, and Russian must contain
  every key. Add locale-aware plurals, dates, time, numbers, percentages, and currency; never build
  sentences by concatenating translated fragments.
- Adhkar, Hafiz/Qur'an, Journal, Sleep, Social, Finance, Settings, onboarding, and every empty/error
  state follow the selected app language. Preserve Arabic source text where religious content
  requires it, but localize transliteration, meaning, labels, and guidance.
- Religious content is sourced and reviewed; the AI offers reflection and study assistance, not
  fatwas. Make source/scope visible for generated Qur'an quizzes.

## Platform data and on-device AI

- HealthKit/Health Connect access is opt-in, minimal, and explained at the moment of use. Read sleep
  stages and sources correctly, deduplicate overlapping samples, retain provenance, and never show
  synthetic values as device data. Manual entries remain distinguishable.
- On-device AI produces schema-constrained `ProposedAction`s, not prose that mutates state. Validate
  every tool argument, resolve named sources/scopes, show a preview for bulk/destructive/external
  actions, execute through the same domain use cases as manual UI, and return per-item results.
- A request such as “create flashcards for Destination B2 themes 1–10” must identify an available,
  lawfully supplied source; extract the exact requested scope; deduplicate cards; preview counts;
  create a deck; emit learning activities; and never invent copyrighted book contents when absent.

## Verification gate

- Add unit tests for domain rules, state holders/Flows, mapping, localization key parity, event
  projections, idempotency, and platform adapters through fakes. Add navigation regression tests and
  semantics for critical controls.
- Run common tests and platform compilations, then verify affected iOS flows in Simulator in every
  supported language, including edge-back, search, keyboard, offline/error states, profile photo,
  and large text. Never mark a flow working because its static screen renders.

