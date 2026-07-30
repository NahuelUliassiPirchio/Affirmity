# Proposal: Firestore Data Sync (Firebase migration stage 2 of 3)

## Intent

Stage 1 delivered a stable Firebase Auth UID that nothing consumes. Affirmations, daily completions and preferences still live in device-only Room/DataStore: reinstalling or changing phone loses everything, and signing in buys the user nothing. This stage makes signed-in data durable and account-scoped in Firestore, and gives stage 3 (Cloud Functions/FCM) a server-readable streak source.

## Scope

### In Scope
- `firebase-firestore` in `gradle/libs.versions.toml` (BOM-managed, catalog only). Firestore 26.x requires minSdk 23 — compatible with minSdk 24. **`firebase-storage` is deliberately NOT added.**
- Schema `users/{uid}` subcollections: `affirmations/{id}`, `dailyCompletions/{epochDay}`, `settings/preferences`, `meta/migrated`. Fields mirror `AffirmationEntity` / `DailyCompletionEntity` 1:1 — raw per-day rows only, **no cached streak field** (streak stays re-derived by `DailyCompletionStats`).
- Repository interfaces over the four existing data dependencies + Room-backed and Firestore-backed implementations.
- Migrate-on-first-sign-in coordinator: on `SignedOut -> SignedIn(uid)` with no `meta/migrated` marker, snapshot Room/DataStore once, batch-write to Firestore, write marker.
- **Single-writer cutover**: after migration, a signed-in user reads *and* writes Firestore exclusively. Signed-out users stay 100% on Room. Never both at once. No permanent dual-write.
- `AffirmityAppState` upgraded from construction-time-fixed dependencies to **runtime-swappable** repositories reacting to live `authState` — including cancelling in-flight Room collectors on swap. This is the main new complexity of the stage.
- `firestore.rules` checked into the repo root (path rule `request.auth.uid == uid`), deployed manually by the user via console/CLI. No CI deploy automation.
- Unit tests (TDD per `config.yaml`) for mapping and migration logic against fake seams; a regression test preserving the "broken streak" guarantee on the Firestore-backed path.
- Only `meditationDurationSeconds` + both `NotificationPreferences` channels sync.

### Out of Scope
- **Image sync / Firebase Storage.** `backgroundValue` syncs verbatim as a device-local path (metadata only). Image-type affirmations will render broken on a second device — an accepted, user-confirmed limitation of this stage.
- **Day-boundary change.** `DayClock`/`DailyCompletionStats`/`DayRolloverWorker` are untouched; `epochDay` stays device-local. Multi-device timezone drift is a named deferred risk (see Risks), to be resolved no later than stage 3 design.
- `affirmationsViewedToday`, `NotificationDebugLog` — local-only by design.
- Deleting or modifying Room; conflict resolution/merge UI; multi-device realtime presence; offline-cache tuning (Firestore SDK default); DI framework; FCM and Cloud Functions (stage 3).

## Capabilities

### New Capabilities
- `data-sync`: account-scoped Firestore persistence of affirmations, daily completions and preferences, including one-time migration and single-writer cutover.

### Modified Capabilities
- None. (`user-auth` is consumed unchanged — `uid` only.)

## Approach

Introduce repository interfaces at the exact `AffirmityAppState` constructor seam stage 1 proved, then give it two interchangeable implementation sets (Room-backed = today's classes; Firestore-backed = new `data/remote/`). `AffirmityAppState` observes `authState` and swaps the active set, awaiting migration completion before the swap so no write can land in the wrong store. Migration is a pure function over snapshots, unit-testable with fakes; Firebase glue stays thin and untested by design, per stage 1's convention.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `gradle/libs.versions.toml`, `app/build.gradle.kts` | Modified | `firebase-firestore` (BOM) |
| `firestore.rules` (repo root) | New | uid-path security rules |
| `data/repository/` | New | Four repository interfaces |
| `data/remote/` | New | Firestore implementations, mappers, migration coordinator |
| `data/local/*Dao`, `*Preferences` | Modified | Adapted to implement interfaces; behavior unchanged |
| `data/AffirmityAppState.kt` | Modified | Runtime-reactive repository swapping |
| `data/DayClock.kt`, `DailyCompletionStats.kt` | Unchanged | Explicitly untouched |
| `app/src/test/.../data/` | New | Mapping + migration unit tests |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Repository swap races: stale Room `Flow` collector still attached after sign-in | High | Swap only after migration confirmation; cancel/restart collection scope on swap; explicit design-phase sequence diagram |
| Migration partially written (batch fails mid-way) | Med | Write `meta/migrated` marker last, in the same batch; unmigrated marker ⇒ migration retries idempotently (doc IDs are deterministic) |
| Sign-out shows stale pre-migration Room data | High (by design) | **Accepted, user-confirmed tradeoff.** Data is not lost, just invisible while signed out. Documented in design + UI copy consideration |
| Multi-device timezone drift on device-local `epochDay` | Med | Deferred on the record to stage 3 design (Cloud Functions have no device timezone). Not silently accepted |
| Image affirmations broken cross-device | High (by design) | Accepted stage limitation; candidate follow-up stage |
| Dual-source-of-truth drift (the historical streak bug class) | Med | Single-writer guarantee is a hard architectural rule; no permanent dual-write path may be introduced |
| Security rules mis-deployed ⇒ open or locked-out data | Med | Rules checked in and reviewed; user verifies in console before enabling sign-in on release |

## Rollback Plan

1. Revert the feature branch. Room/DataStore code is unmodified in behavior, so the signed-out path (and any not-yet-migrated user) is byte-for-byte the pre-change app.
2. Migrated users: reverting means the app reads Room again — the pre-migration snapshot. Firestore data remains intact and is picked up when the branch is restored; nothing is deleted.
3. Partial rollback: force the Firestore-backed set off (constant/flag in the composition root) to fall back to Room-only for everyone, keeping the interfaces.
4. Console rollback: tighten `firestore.rules` to deny-all; the app degrades to error states, never a crash.

## Dependencies

- Stage 1 (`firebase-auth`) merged — supplies the `uid` contract.
- User console prerequisite: Firestore database created (production mode) in the existing Firebase project; rules deployed.
- `firebase-firestore` via the existing Firebase BOM (34.16.0), minSdk 23 ⇒ OK for minSdk 24.

## Success Criteria

- [ ] `gradlew.bat assembleDebug` and `gradlew.bat testDebugUnitTest` pass.
- [ ] Signed-out behavior byte-for-byte unchanged; existing `androidTest` suite green unmodified.
- [ ] First sign-in copies existing affirmations, completions and preferences into `users/{uid}/...`, visible in the Firebase console.
- [ ] After migration, a new affirmation/completion created while signed in appears in Firestore and **not** in Room.
- [ ] Reinstalling the app and signing in restores affirmations, completions and preferences.
- [ ] The "broken streak" guarantee (missed day does not erase earlier completions) holds on the Firestore-backed path.
- [ ] A second account's data is unreadable — verified against `firestore.rules`.
- [ ] No `firebase-storage`, FCM, or `DayClock` changes in the diff.

## Proposal question round

Both previously open questions were confirmed by the user (image metadata-only sync; sign-out reverts to the stale Room snapshot). One assumption remains for review:

1. **Security rules ownership**: this proposal puts `firestore.rules` in scope as a checked-in repo-root file with **manual** console/CLI deployment by you (no `firebase.json`, no CI deploy). Confirm, or say if you would rather keep rules entirely console-managed and out of the repo.
