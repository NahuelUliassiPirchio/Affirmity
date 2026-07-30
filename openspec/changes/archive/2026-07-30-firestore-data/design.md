# Design: Firestore Data Sync (stage 2 of 3)

## Technical Approach

Four repository interfaces (`data/repository/`) are extracted at the exact `AffirmityAppState`
constructor seam stage 1 proved. Two implementation sets exist: Room/DataStore-backed adapters that
delegate to today's untouched DAO/Preferences classes, and Firestore-backed ones in `data/remote/`.
The four are bundled into a single `DataSession` value so identity swaps are atomic. `AffirmityAppState`
derives a `StateFlow<DataSession>` from `authRepository.authState`; every read collector is a
`flatMapLatest` over that flow (structured cancellation kills stale Room collectors for free), and
every write awaits a non-`Migrating` session before touching a store. Single-writer is therefore a
type-level property, not a convention. Satisfies `specs/data-sync/spec.md`.

## Architecture Decisions

| Decision | Choice | Alternatives rejected | Rationale |
|---|---|---|---|
| Swap mechanism | `flatMapLatest` over `StateFlow<DataSession>` in each `init` collector | Manual `Job` bookkeeping (`job?.cancelAndJoin()` per swap); recreating `AffirmityAppState` on sign-in | `flatMapLatest` cancels the previous inner Flow before subscribing to the new one — the High-likelihood stale-Room-collector risk is eliminated by the operator, not by discipline. Recreating the state object would drop Compose state and re-run `ensureScheduled`. |
| Swap granularity | One `DataSession` bundle of all four repositories | Four independent swappable fields | A bundle cannot be half-swapped; prevents "affirmations on Firestore, completions still on Room". |
| Migration gating | `sealed DataSession { Local, Migrating(uid), Remote(uid) }`; `Migrating` exposes Room **reads** but writes suspend on `session.first { it !is Migrating }` | Blocking the UI; discarding writes during migration | No blank screen mid-migration, and the migrator is provably the only writer while it runs. |
| Room adapters | New wrapper classes implementing the interfaces, delegating to the DAO | Making `@Dao` interfaces extend the repository interfaces | Keeps `data/local/` literally unmodified (rollback guarantee + Room KSP constraints on inherited suspend/Flow methods). Diverges from the proposal's "*Dao modified" wording; behavior is identical. |
| Completion doc IDs | Doc ID = `epochDay.toString()`, **plus** a numeric `epochDay` field used for range queries | Range query on `documentId()` | Lexicographic doc-ID ordering breaks for negative/variable-length numbers. Deterministic IDs stay for idempotency. |
| Completion writes | `set(mapOf(...), SetOptions.merge())` | `update()` (fails if absent), read-then-write | Exactly reproduces `insertIfAbsent + UPDATE` semantics in one idempotent op. |
| Batch size | Chunk at 450 ops; `meta/migrated` written in the final chunk | One batch (500-op Firestore hard limit; 370-day lookback can exceed it) | Marker-last still holds. Partial failure is safe: deterministic IDs + `set` make retry idempotent. |
| Rules location | `firestore.rules` at repo root, manual `firebase deploy --only firestore:rules` | Console-only; `firebase.json` + CI | Reviewable in the diff; no CI/Firebase-tooling dependency this stage. |

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{uid}/{document=**} {
      allow read, write: if request.auth != null && request.auth.uid == uid;
    }
  }
}
```

## Interfaces / Contracts

```kotlin
interface AffirmationRepository {
    fun observeAll(): Flow<List<AffirmationEntity>>
    suspend fun insert(entity: AffirmationEntity)
    suspend fun deleteById(id: String)
    suspend fun deleteAll()
}
interface DailyCompletionRepository {
    fun observeRange(from: Long, to: Long): Flow<List<DailyCompletionEntity>>
    suspend fun getRange(from: Long, to: Long): List<DailyCompletionEntity>
    suspend fun markMeditation(epochDay: Long)
    suspend fun markAffirmation(epochDay: Long)
}
interface MeditationPreferencesRepository {
    fun observeMeditationDurationSeconds(): Flow<Int?>
    suspend fun saveMeditationDurationSeconds(seconds: Int)
}
interface NotificationSettingsRepository {
    fun observe(channel: NotificationChannelSpec): Flow<ChannelSettings>
    suspend fun setEnabled(channel: NotificationChannelSpec, enabled: Boolean)
    suspend fun setWindow(channel: NotificationChannelSpec, startMinute: Int, endMinute: Int)
}

sealed interface DataSession {
    val affirmations: AffirmationRepository
    val completions: DailyCompletionRepository
    val meditation: MeditationPreferencesRepository
    val notifications: NotificationSettingsRepository
    class Local(...) : DataSession
    class Migrating(val uid: String, private val local: Local) : DataSession // reads delegate to local
    class Remote(val uid: String, ...) : DataSession
}
```

`AffirmationEntity`/`DailyCompletionEntity`/`ChannelSettings` are reused as the transport shape —
`data/remote/FirestoreMappers.kt` converts them to/from `Map<String, Any>` 1:1. `backgroundValue`
is copied verbatim (device-local path for images — accepted stage limitation).
`affirmationsViewedToday` and `NotificationDebugLog` stay on `TrackerPreferences`/local, never swapped.

## Data Flow

```kotlin
private val session: StateFlow<DataSession> = authRepository.authState
    .map { (it as? AuthState.SignedIn)?.uid }
    .distinctUntilChanged()
    .transformLatest { uid ->
        if (uid == null) { emit(local) } else {
            emit(DataSession.Migrating(uid, local))
            migrator.ensureMigrated(uid, local)   // no-op if meta/migrated exists
            emit(remoteFor(uid))
        }
    }
    .stateIn(scope, SharingStarted.Eagerly, local)

// every existing init collector becomes:
scope.launch { session.flatMapLatest { it.affirmations.observeAll() }.collect { ... } }
// every existing write becomes:
private suspend fun ready(): DataSession = session.first { it !is DataSession.Migrating }
```

### The swap moment, step by step (`SignedOut -> SignedIn(uid)`)

1. `FirebaseAuthRepository` emits `SignedIn(uid)`; `session` upstream produces a new uid key.
2. `transformLatest` **cancels its own previous body**, and each `flatMapLatest` collector cancels its
   in-flight Room `Flow` subscription the moment a new `DataSession` is emitted. No Room collector
   survives the swap — this is the mitigation for the High-likelihood stale-flow risk.
3. `Migrating(uid)` is emitted. Read collectors immediately resubscribe to the *same* Room flows
   (UI keeps showing current data, no flicker); any write in flight or started now suspends in `ready()`.
4. `migrator.ensureMigrated(uid)` reads `users/{uid}/meta/migrated`. If present → returns immediately.
   If absent → snapshots Room once (`observeAll().first()`, `getRange(today-370, today+6)`,
   `observeMeditationDurationSeconds().first()`, both channels), builds a pure `MigrationPlan`,
   commits it in ≤450-op batches, marker last.
5. `Remote(uid)` is emitted. Collectors cancel the Room subscriptions and attach Firestore snapshot
   listeners; suspended writers resume against Firestore. Room is now untouched for this session.
6. On sign-out the same path runs in reverse to `Local` — Firestore listeners are cancelled by
   `flatMapLatest`, and the (stale, pre-migration) Room snapshot becomes visible again. Accepted tradeoff.
7. Migration failure: `ensureMigrated` throws → caught, `syncError` state set, session **stays**
   `Local` (never `Remote`). The user keeps a working offline app; retry occurs on the next sign-in.

## File Changes

| File | Action | Description |
|---|---|---|
| `gradle/libs.versions.toml`, `app/build.gradle.kts` | Modify | `firebase-firestore` via existing BOM 34.16.0. No `firebase-storage`. |
| `firestore.rules` (repo root) | Create | Rules above; manual deploy |
| `data/repository/Repositories.kt`, `DataSession.kt` | Create | The four interfaces + session bundle |
| `data/repository/Room*Repository.kt` | Create | Thin adapters over the untouched DAO/Preferences classes |
| `data/remote/FirestorePaths.kt`, `FirestoreMappers.kt` | Create | Path builders + entity ↔ map mapping (pure, unit-tested) |
| `data/remote/Firestore*Repository.kt` | Create | Snapshot-listener `callbackFlow` reads, `set`/`merge` writes |
| `data/remote/MigrationPlan.kt` | Create | Pure `snapshot -> List<DocWrite>` (unit-tested) |
| `data/remote/FirestoreMigrator.kt` | Create | Marker check + chunked batch commit (thin glue, untested by design) |
| `data/AffirmityAppState.kt` | Modify | Constructor takes `DataSession.Local` + a `remoteSessionFactory`; `session` StateFlow; collectors → `flatMapLatest`; writes → `ready()`; new `syncError` state; `rememberAffirmityAppState()` wiring |
| `data/local/*`, `data/DayClock.kt`, `DailyCompletionStats.kt` | Unchanged | Explicitly untouched |
| `app/src/test/.../data/remote/` | Create | Mapper, migration-plan, session-swap and streak-regression tests |

## Testing Strategy

| Layer | What to test | Approach |
|---|---|---|
| Unit (`gradlew.bat testDebugUnitTest`) | Entity ↔ map round-trip; `MigrationPlan` completeness, deterministic IDs, marker-last ordering, 450-op chunking; `ensureMigrated` no-ops when the marker exists | JUnit 4 against fake repositories/fake Firestore seam; no Android or Firebase types in the pure code |
| Unit (swap) | Sign-in cancels the Room collector (fake `Flow` records cancellation); writes issued during `Migrating` land in the remote fake, never in the Room fake; migration failure keeps the session `Local` | `FakeAuthRepository` emitting `SignedOut→SignedIn→SignedOut` over a `TestScope` + turbine-free manual collection |
| Unit (regression) | "Broken streak" guarantee on the Firestore-backed path: Mon+Wed done, Tue missed → `completedDays[0]`/`[2]` true, `streakDays` correct | Feed the fake Firestore rows through `DailyCompletionStats` (mirrors `AffirmityAppStateInstrumentedTest.brokenMeditationStreak_...`) |
| Instrumented | Existing suite must stay green **unmodified** (signed-out path unchanged) | `gradlew.bat connectedDebugAndroidTest`, no edits |
| Manual | Console verification of migrated docs, second-account denial against `firestore.rules`, reinstall-and-restore | Proposal success criteria |

TDD per `openspec/config.yaml` (`apply.tdd: true`): RED/GREEN/REFACTOR per phase group.
Suggested chained PRs: **PR1** deps + `firestore.rules` + interfaces + Room adapters (behavior-neutral);
**PR2** mappers + `MigrationPlan` + Firestore repositories (pure logic + fakes);
**PR3** `DataSession` swap wiring in `AffirmityAppState` + swap/regression tests.

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or
process-integration boundary. Authorization is delegated to `firestore.rules` (path-scoped `request.auth.uid`).

## Migration / Rollout

One-time, per-account, on first `SignedOut -> SignedIn(uid)`; idempotent via deterministic doc IDs and
the `meta/migrated` marker (`{ migratedAt, schemaVersion: 1, source: "room" }`). No Room writes, no
deletions, so rollback is the proposal's branch revert; a `USE_REMOTE_SESSION = false` constant in
`rememberAffirmityAppState()` forces everyone back to `Local` without reverting code. Prerequisite:
the user creates the Firestore database and deploys `firestore.rules` before signed-in release use.

## Open Questions

- [ ] `syncError` needs Spanish UI copy in `strings.xml` (Settings account card) — wording is
      `sdd-tasks`/UI-level, not blocking this design.
- [ ] Room adapters live in `data/repository/` rather than modifying `data/local/*Dao` as the
      proposal's Affected Areas table implies. Behavior-equivalent; carry the adapter shape into tasks.
- [ ] Multi-device `epochDay` timezone drift remains deferred on the record to stage 3 design.
