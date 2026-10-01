# Apply Progress: user-collections

## Batch 1 - Slice A (data layer + migration): 9/9 tasks complete
Mode: Strict TDD. Delivery: single-pr with size:exception. Nothing committed.

- [x] 1.1 RED `UserCollectionDaoTest` (8 tests)
- [x] 1.2 RED `migrate13To14_...` in `AffirmityDatabaseMigrationTest`
- [x] 1.3 Entities + `UserCollectionWithItems`
- [x] 1.4 `UserCollectionDao`
- [x] 1.5 `AffirmityDatabase` v14, `MIGRATION_13_14`, `userCollectionDao()`
- [x] 1.6 Hand-written SQL matches generated `14.json` exactly (no drift)
- [x] 1.7 `UserCollectionRepository` + NoOp
- [x] 1.8 `RoomUserCollectionRepository`
- [x] 1.9 Unit suite green; androidTest compiled only

## TDD Cycle Evidence
| Task | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-----|-----|-----|
| 1.1 | `androidTest/.../UserCollectionDaoTest.kt` | Instrumented | N/A (new) | Written; compile failed (unresolved refs) | Compiles; NOT executed (no device) | 8 cases | None needed |
| 1.2 | `androidTest/.../AffirmityDatabaseMigrationTest.kt` | Instrumented | N/A (existing tests need device) | Written; unresolved `MIGRATION_13_14` | Compiles; NOT executed | Single (data intact + FK cascade) | None needed |
| 1.3-1.8 | (covered by 1.1/1.2) | - | - | - | `compileDebugAndroidTestKotlin` + `testDebugUnitTest` pass; KSP generated 14.json | - | - |

## Not executed
`UserCollectionDaoTest` and `migrate13To14_...` need a device/emulator (`connectedDebugAndroidTest`).

## Deviations
- `UserCollection` domain data class created in `data/UserCollections.kt` in slice A (repository contract needs it); slice B extends the file.
- `createWithItem(collection, item: UserCollectionItemEntity?)` takes a nullable item so create without an initial affirmation is one call.
- `removeAffirmations` is a no-op for an empty collection (avoids `IN ()`).

## Files
Created: data/local/UserCollection{Entity,ItemEntity,WithItems,Dao}.kt, data/UserCollections.kt, data/repository/RoomUserCollectionRepository.kt, androidTest UserCollectionDaoTest.kt, app/schemas/.../14.json
Modified: AffirmityDatabase.kt, Repositories.kt, AffirmityDatabaseMigrationTest.kt, tasks.md

## Batch 1 fix pass (review fixes + rename)
- Rename: join column `collectionId` -> `userCollectionId` in `user_collection_items` (entity, PK/FK, relation, DAO, migration SQL, repository params, tests, design.md). Catalog `collectionId` untouched. 14.json regenerated; `createSql` matches `MIGRATION_13_14` exactly (scripted diff, True).
- Fix 1: `create` now enabled=true, lastUsedAtMillis=nowMillis (magic 0L removed). JVM test `RoomUserCollectionRepositoryTest`.
- Fix 2: `removeAffirmations` chunks by 500; JVM tests for 1201 ids and empty input.
- Fix 3: shared private `CHIP_ORDER` const adds `id ASC` tiebreak; androidTest `observeAll_breaksFullTiesById`.
- Fix 4: migration test runs `PRAGMA foreign_keys=ON` before cascade DELETE.
- Fix 5: import order, KDoc style, UserCollections.kt KDoc reworded, `assertThrows` replaces empty catch.
- Evidence: `./gradlew -Dorg.gradle.workers.max=2 :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest` BUILD SUCCESSFUL. androidTests still NOT executed (no device).

## Batch 2 - Slice B (AppState + feed union): 11/11 tasks complete
Mode: Strict TDD. Delivery: single-pr with size:exception. Nothing committed.

- [x] 2.1 RED `UserCollectionsTest` name validation + `canCreateUserCollection`
- [x] 2.2 RED `resolveEnabledCollectionRows` + `isDraftThemeSelectionValid` collections cases
- [x] 2.3 GREEN `data/UserCollections.kt` (UserCollectionUi, CollectionNameResult, constants, pure functions); `isDraftThemeSelectionValid(anyCollectionEnabled = false)`
- [x] 2.4 RED `RecordingUserCollectionRepository` + `AffirmityAppStateUserCollectionsTest` (create, limit, downgrade, toggle, last-off, Mutex order)
- [x] 2.5 RED feed tests (union, disabled, dedupe, order slot, hidden, Pro-locked, pre-resolution, no reshuffle x2 orderings)
- [x] 2.6 RED cleanup tests (removeAffirmation, catalog guard, replace import owned-only, append import)
- [x] 2.7 GREEN ctor params `collectionRepository`/`collectionClock`/`collectionIdFactory`, collector, `userCollections`, `anyCollectionEnabled`, `canCreateCollection`, `collectionIdsFor`
- [x] 2.8 GREEN write API under `userCollectionMutex`
- [x] 2.9 GREEN collections segment in `filteredAffirmations`, validity rule, cleanup hooks
- [x] 2.10 GREEN `RoomUserCollectionRepository` wired in `rememberAffirmityAppState`
- [x] 2.11 REFACTOR: full unit suite 1038 tests, 0 failures; `compileDebugAndroidTestKotlin` OK

### TDD Cycle Evidence (batch 2)
| Task | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| 2.1-2.3 | `UserCollectionsTest.kt` | Unit (pure) | N/A (new) | Compile failed: unresolved `CollectionNameResult`, `validateCollectionName` (run) | 15/15 pass | 15 cases (trim, blank, 40/41, code points, case/Unicode dup, self-rename, tiers, dedupe, hidden/locked, owned-only, validity) | KDoc tidy |
| 2.4-2.9 | `AffirmityAppStateUserCollectionsTest.kt` + `RecordingUserCollectionRepository.kt` | Unit (AppState, fakes) | Existing AppState tests compile unchanged (full suite green) | Compile failed: unresolved `createCollection`, `userCollections`, `canCreateCollection` (run) | 27/27 pass; mutation check (dropping the collections segment) fails 5 tests | create/limit/downgrade/toggle ON vs OFF/last-off/Mutex/dup race/feed x8/cleanup x4 | None needed |
| 2.10 | composition root | n/a | n/a | n/a | compiles; not unit-testable (Compose root) | - | - |

## Not executed (batch 2)
Composition-root wiring is exercised only by compile; androidTests still not executed (no device).

## Deviations (batch 2)
- Added `anyCollectionEnabled` (public) to expose the empty-state input for slice C (not named in design).
- `createCollection` checks the tier limit BEFORE name validity (returns LimitReached even for a blank name).
- Replace-import snapshot uses in-memory `affirmations` (as design says) and skips the repository call when there are no owned ids.

## Batch 2 fix pass (review fixes, slice B) - still uncommitted
Mode: Strict TDD. Task list unchanged (11/11 remain checked); no new tasks.

Behaviour fixes (RED -> GREEN):
1. add after delete: fake `addItem` now throws on a missing collection (models the FK). RED: `add after the collection was deleted performs no insert and does not throw` failed (events contained `addItem:c1:owned-1`). GREEN: `userCollectionExists` guard under the Mutex in add/remove/setEnabled (toggle already read the persisted state).
2. `createCollection`/`renameCollection` are now `suspend` and return the verdict computed under the lock (authoritative). Tests rewritten to `launch` two concurrent calls and assert `[Ok, Duplicate]` as returned values. RED: compile failure against the old non-suspend API, then GREEN. Slice C consumes the returned `CollectionNameResult`.
3. sheet validity: new test asserts `state.isDraftThemeSelectionValid` true with an enabled collection and nothing else, false with none enabled. Mutation check: dropping `anyCollectionEnabled` from the property made it FAIL (line 344); restored.
4. New tests: rapid toggles alternate (enable, disable, enable); rename duplicate race under the lock. Both characterise already-correct behaviour (passed first run; not a RED).

Readability: comment/helper order in `filteredAffirmations` fixed (`collectionRows` uses `anyCollectionEnabled`); `collectionId` -> `userCollectionId` params, `collectionIdsFor` -> `userCollectionIdsFor`; `validateNewCollection(name, existing, tier)` and `List<UserCollection>.toUserCollectionUi(knownIds)` moved to `data/UserCollections.kt` with 3 unit tests; shared private `writeCollectionEnabled`; `COLLECTION_NAME_MAX + 1` in tests.

Evidence: `./gradlew -Dorg.gradle.workers.max=2 :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin` BUILD SUCCESSFUL; 1045 tests, 0 failures (was 1038, +7).
Deviation: `createCollection` no longer does a synchronous in-memory pre-check; it always takes the Mutex (authoritative only). Dialog inline errors now come from the returned value.
Deferred (not done): derivedStateOf id-set cache, flow retry after .catch, shared test fixture, lock-and-launch helper, typed event log.
Not executed: androidTests (no device).
