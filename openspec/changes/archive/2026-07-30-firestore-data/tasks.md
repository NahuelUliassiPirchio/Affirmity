# Tasks: Firestore Data Sync (Stage 2 of 3)

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~950–1200 |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR1 → PR2 → PR3 |
| Delivery strategy | auto-chain |
| Chain strategy | feature-branch-chain |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: feature-branch-chain
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 | `firebase-firestore` dep + `firestore.rules` + repository interfaces + `DataSession` + Room adapters (behavior-neutral) | PR1 (base: `feature/firestore-data`) | `gradlew.bat assembleDebug` | N/A — Room adapters delegate 1:1, no new callers yet | Revert catalog line, delete `firestore.rules`, delete `data/repository/` |
| 2 | Mappers + `MigrationPlan` + `FirestoreMigrator` + Firestore-backed repositories | PR2 (base: PR1 branch) | `gradlew.bat testDebugUnitTest --tests "com.pirxhio.affirmity.data.remote.*"` | N/A — repo has no callers yet | Delete `data/remote/` + its test dir |
| 3 | `DataSession` swap wiring in `AffirmityAppState` + swap/regression tests + `syncError` UI copy | PR3 (base: PR2 branch) | `gradlew.bat testDebugUnitTest` | Manual: sign in, verify Firestore console docs; reinstall-and-restore | Revert `AffirmityAppState` constructor/collector changes to the PR2 state; `USE_REMOTE_SESSION = false` kill switch |

Only the tracker branch merges to `main`.

## Resolved Discrepancy: Room `*Dao` Interfaces Are NOT Modified

The proposal's Affected Areas table lists `data/local/*Dao, *Preferences` as **Modified**.
`design.md` (Architecture Decisions: "Room adapters") overrides this: new wrapper classes are
introduced in `data/repository/` that implement the four repository interfaces and delegate to
the existing, **untouched** `@Dao`/`Preferences` classes. Reasons given in design.md: keeps
`data/local/` literally unmodified (rollback guarantee) and avoids Room KSP constraints on
inherited suspend/Flow methods on `@Dao` interfaces.

**Tasks below follow design.md, the more detailed and authoritative artifact.** No file under
`app/src/main/java/com/pirxhio/affirmity/data/local/` is edited by this change. This is called
out again as an explicit checklist item in Phase 8 (Cleanup) so it is verified, not assumed.

## Phase 1: Build & Dependency Foundation (PR1)

- [x] 1.1 Add `firebase-firestore` to `gradle/libs.versions.toml` (BOM-managed, catalog only — no explicit version, no `firebase-storage`).
- [x] 1.2 Add `libs.firebase.firestore` dependency in `app/build.gradle.kts`.
- [x] 1.3 Create repo-root `firestore.rules` with the `users/{uid}/{document=**}` path rule from design.md (`request.auth != null && request.auth.uid == uid`).
- [x] 1.4 Verify `gradlew.bat assembleDebug` — confirms the new dependency resolves and nothing else broke; no functional code depends on it yet.

## Phase 2: Repository Interfaces & DataSession (PR1)

- [x] 2.1 Create `data/repository/Repositories.kt`: `AffirmationRepository`, `DailyCompletionRepository`, `MeditationPreferencesRepository`, `NotificationSettingsRepository` interfaces exactly per design.md's Interfaces/Contracts section.
- [x] 2.2 Create `data/repository/DataSession.kt`: `sealed interface DataSession` with `Local`, `Migrating(uid, local)`, `Remote(uid, ...)` variants, each exposing the four repository properties.

## Phase 3: Room Adapters — Behavior-Neutral (PR1)

- [x] 3.1 Create `data/repository/RoomAffirmationRepository.kt`: thin wrapper implementing `AffirmationRepository`, delegating every call 1:1 to the existing `AffirmationDao` (unmodified).
- [x] 3.2 Create `data/repository/RoomDailyCompletionRepository.kt`: thin wrapper implementing `DailyCompletionRepository`, delegating 1:1 to the existing `DailyCompletionDao` (unmodified).
- [x] 3.3 Create `data/repository/RoomMeditationPreferencesRepository.kt`: thin wrapper implementing `MeditationPreferencesRepository`, delegating 1:1 to the existing meditation `Preferences`/DataStore class (unmodified).
- [x] 3.4 Create `data/repository/RoomNotificationSettingsRepository.kt`: thin wrapper implementing `NotificationSettingsRepository`, delegating 1:1 to the existing notification settings store (unmodified).
- [x] 3.5 Confirm by diff inspection that no file under `data/local/` changed in this phase (pure delegation, no signature edits) — first checkpoint for the Resolved Discrepancy note above. Confirmed via `git status --porcelain -- app/src/main/java/com/pirxhio/affirmity/data/local/` (empty output).

## Phase 4: Firestore Mappers — TDD (PR2)

- [x] 4.1 RED `app/src/test/.../data/remote/FirestoreMappersTest.kt`: `AffirmationEntity` → `Map<String, Any>` → `AffirmationEntity` round-trips losslessly, including `backgroundValue` copied verbatim.
- [x] 4.2 RED (same file): `DailyCompletionEntity` → map → entity round-trips; map contains both the doc-ID string and a numeric `epochDay` field for range queries.
- [x] 4.3 RED (same file): `ChannelSettings` → map → `ChannelSettings` round-trips for both notification channels; no streak field is ever produced by any mapper.
- [x] 4.4 GREEN `data/remote/FirestoreMappers.kt`: pure `toMap`/`fromMap` functions for the three entity shapes satisfying 4.1–4.3.
- [x] 4.5 RED: `FirestorePathsTest.kt` — path builders produce `users/{uid}/affirmations/{id}`, `users/{uid}/dailyCompletions/{epochDay}`, `users/{uid}/settings/preferences`, `users/{uid}/meta/migrated` for representative inputs.
- [x] 4.6 GREEN `data/remote/FirestorePaths.kt`: pure path-builder functions satisfying 4.5.

## Phase 5: MigrationPlan — TDD (PR2)

- [x] 5.1 RED `app/src/test/.../data/remote/MigrationPlanTest.kt`: a snapshot of N affirmations + M completions + preferences produces a `MigrationPlan` whose writes cover every input record exactly once (completeness).
- [x] 5.2 RED (same file): doc IDs are deterministic — the same snapshot run twice produces byte-identical write targets (idempotency guarantee for retry).
- [x] 5.3 RED (same file): the `meta/migrated` marker write is always the last op in the plan's final chunk, never earlier.
- [x] 5.4 RED (same file): a snapshot large enough to exceed 450 ops is split into multiple chunks of ≤450 ops each, with the marker only in the last chunk.
- [x] 5.5 GREEN `data/remote/MigrationPlan.kt`: pure `snapshot -> List<DocWrite>` (or `List<List<DocWrite>>` chunked) function satisfying 5.1–5.4.
- [x] 5.6 REFACTOR: extract the 450-op chunking logic into a small named pure function if `MigrationPlan.kt` mixes concerns after GREEN. (Already extracted as `chunkWithMarkerLast` during GREEN — no further refactor needed.)

## Phase 6: FirestoreMigrator & Firestore Repositories (PR2)

- [x] 6.1 RED `app/src/test/.../data/remote/FirestoreMigratorTest.kt`: `ensureMigrated(uid)` against a fake Firestore doc seam returns without producing any writes when `users/{uid}/meta/migrated` already exists.
- [x] 6.2 GREEN `data/remote/FirestoreMigrator.kt`: marker-check branch satisfying 6.1, plus the chunked-batch-commit path built on `MigrationPlan` (batch-commit mechanics themselves are thin Firebase SDK glue, untested by design — consistent with stage 1's convention).
- [x] 6.3 Create `data/remote/FirestoreAffirmationRepository.kt`: `callbackFlow`-based snapshot-listener reads, `set(..., SetOptions.merge())` writes, implementing `AffirmationRepository` (thin glue, untested by design).
- [x] 6.4 Create `data/remote/FirestoreDailyCompletionRepository.kt`: same pattern for `DailyCompletionRepository`, doc ID = `epochDay.toString()` per design.md.
- [x] 6.5 Create `data/remote/FirestoreMeditationPreferencesRepository.kt` and `data/remote/FirestoreNotificationSettingsRepository.kt`: same pattern for the two settings interfaces, single `settings/preferences` document.

## Phase 7: DataSession Swap Wiring — TDD (PR3)

- [x] 7.1 RED `app/src/test/.../data/AffirmityAppStateSwapTest.kt` (or equivalent): using a `FakeAuthRepository` over a `TestScope`, a `SignedOut -> SignedIn(uid)` transition cancels the in-flight Room `Flow` collector (fake Flow records cancellation) before any Firestore fake is subscribed.
- [x] 7.2 RED (same file): writes issued while the session is `Migrating` land in the remote fake and never in the Room fake, once migration completes.
- [x] 7.3 RED (same file): a migration failure (fake throws) keeps the session `Local` — never transitions to `Remote` — and sets a `syncError` state.
- [x] 7.4 RED (same file): `SignedIn -> SignedOut` cancels the Firestore listener and resumes the stale pre-migration Room snapshot (per spec's "Sign-Out Reverts to Stale Room Snapshot" requirement).
- [x] 7.5 GREEN `data/AffirmityAppState.kt`: constructor takes `DataSession.Local` + a `remoteSessionFactory`; add `private val session: StateFlow<DataSession>` derived from `authRepository.authState` via `transformLatest` exactly per design.md's Data Flow section; convert every existing read `init` collector to `session.flatMapLatest { ... }`; convert every existing write to await `ready()` (`session.first { it !is DataSession.Migrating }`); add `syncError` Compose state.
- [x] 7.6 Confirm no pre-existing `AffirmityAppState` public method signature changed (additive-only), matching stage 1's convention.
- [x] 7.7 Wire `USE_REMOTE_SESSION` kill-switch constant in `rememberAffirmityAppState()` per design.md's Migration/Rollout section.

## Phase 8: Broken-Streak Regression — TDD (PR3)

- [x] 8.1 RED `app/src/test/.../data/remote/FirestoreStreakRegressionTest.kt`: feed fake Firestore-shaped `DailyCompletionEntity` rows (Mon+Wed done, Tue missed) through `DailyCompletionStats`; assert `completedDays[0]`/`[2]` true and `streakDays` matches the existing Room-path guarantee (mirrors `AffirmityAppStateInstrumentedTest.brokenMeditationStreak_...`).
- [x] 8.2 GREEN: no production change expected if `DailyCompletionStats` already consumes raw per-day rows structurally — if 8.1 fails, fix the Firestore repository/mapper (not `DailyCompletionStats`, which stays unchanged per scope) until it passes. Confirmed: 8.1 was GREEN on first run, no production change needed.

## Phase 9: Verification

- [x] 9.1 `gradlew.bat testDebugUnitTest` — full suite green, including all new `data.remote`/`data.AppState` tests, no pre-existing test broken.
- [x] 9.2 `gradlew.bat assembleDebug` — BUILD SUCCESSFUL.
- [ ] 9.3 `gradlew.bat connectedDebugAndroidTest` — existing suite green **unmodified** (signed-out path unaffected); requires a connected device/emulator, optional if unavailable to the apply agent. **Not run — no connected device/emulator available to the apply agent.** `compileDebugAndroidTestKotlin` was verified green as a proxy (see apply-progress); a human must run the real suite before merge.
- [ ] 9.4 Manual (requires the user's own Firebase console + device): first sign-in copies existing affirmations/completions/preferences into `users/{uid}/...`, visible in console; `meta/migrated` exists afterward.
- [ ] 9.5 Manual: post-migration, a new affirmation/completion created while signed in appears in Firestore and not in Room.
- [ ] 9.6 Manual: reinstall the app and sign in — data is restored from Firestore.
- [ ] 9.7 Manual: second account's data is unreadable — verify `firestore.rules` denies cross-uid access (requires the user to have deployed the rules).

## Phase 10: Cleanup / Open Questions Resolution

- [x] 10.1 **Resolved discrepancy checkpoint**: diff the full change against `app/src/main/java/com/pirxhio/affirmity/data/local/` — confirm zero lines changed in that directory. Document the result here (pass/fail) before merging PR1. This follows design.md over the proposal's "Modified" wording, per the Resolved Discrepancy note at the top of this file. **Result: PASS** — `git status --porcelain -- app/src/main/java/com/pirxhio/affirmity/data/local/` is empty across all three PRs.
- [x] 10.2 Add Spanish `syncError` UI copy to `res/values/strings.xml` (Settings account card), per design.md's Open Questions. Wired into `AccountSettingsCard`/`SettingsScreen`/`MainActivity` (additive `syncError: String? = null` params), shown next to the existing `authError` copy.
- [x] 10.3 Confirm `data/DayClock.kt` and `data/DailyCompletionStats.kt` have zero diff — multi-device `epochDay` timezone drift stays deferred on the record to stage 3 design, no action taken this stage. Confirmed via `git status --porcelain` — neither file appears in the diff.
- [x] 10.4 Confirm no `firebase-storage` dependency, no FCM code, no Cloud Functions code appears in the diff (out of scope for this stage). Confirmed — `gradle/libs.versions.toml`/`app/build.gradle.kts` diff only adds `firebase-firestore` (BOM-managed); no `firebase-storage`, FCM, or Cloud Functions references anywhere in the diff.
