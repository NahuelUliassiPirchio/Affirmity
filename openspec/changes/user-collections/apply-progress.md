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

## Batch 3 - Slice C (UI): 8/9 tasks complete (3.8 manual visual check NOT executed)
Mode: Strict TDD. Delivery: single-pr with size:exception. Nothing committed.

- [x] 3.1 strings in values/ (neutral Spanish, default) and values-en/ (23 keys: collection_* and feed_empty_*)
- [x] 3.2 `AffirmationsScreen`: nullable `onAddToCollection` (row hidden when null) + `PlaylistAdd` row; new `emptyState` param
- [x] 3.3 `ui/collections/CollectionNameDialog.kt` (suspend onSubmit, inline error from returned `CollectionNameResult`)
- [x] 3.4 `CollectionPickerSheet.kt` (membership toggles, create new with `withAffirmationId`, limit message + `onUpgrade`)
- [x] 3.5 `CollectionChipsRow.kt` (tap toggle, long press) + `CollectionManageHost` (actions, rename, delete confirmation)
- [x] 3.6 `MainActivity`: picker state, chips row in the feed Box (TopStart, top 80dp; hidden in clean screen/when none), manage host
- [x] 3.7 empty feed uses `feedEmptyState` (Collections vs Generic copy, both from strings.xml)
- [ ] 3.8 MANUAL visual check: NOT executed
- [x] 3.9 `assembleDebug` OK, unit suite 1063 tests / 0 failures; `lintDebug` NOT run

### TDD Cycle Evidence (batch 3)
| Task | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| 3.1-3.7 pure logic | `ui/collections/CollectionUiLogicTest.kt` | Unit (pure) | NeutralSpanishResourcesTest guards new es strings (2/2) | Compile failed: unresolved `errorStringRes`, `collection_name_error_*` (run) | 18/18 pass | error mapping x3, picker rows x4, chip label, empty state x5, manage flow x5 | None |
| 3.5 chips | `androidTest/.../CollectionChipsRowTest.kt` | Instrumented | N/A | n/a | Compiles; NOT executed (no device) | tap + long press | None |

## Deviations (batch 3)
- Chips are a custom `Surface` + `combinedClickable` chip, not `FilterChip`: Material chips own their click and expose no long press.
- Empty state: Collections whenever the user owns any collection (all off or enabled-but-empty), Generic only with none. Existing hardcoded copy moved to `feed_empty_generic`.
- `onAddToCollection` is nullable (row hidden if null) instead of a no-op default.
- Paywall hook reuses `onUpgradeClick(PaywallSource.OTHER)` (no new PaywallSource value).
- Placement documented in MainActivity: TopStart, 80dp down, below favourite heart and the TopEnd status overlay.

## Not executed (batch 3)
No visual verification (3.8), no instrumented tests run, `lintDebug` not run.

## Batch 3 fix pass (review fixes, slice C) - still uncommitted
Mode: Strict TDD. Task list unchanged (8/9 checked; 3.8 stays unchecked and NOT executed).

Behaviour (RED -> GREEN):
- `feedEmptyState` dropped the redundant `anyCollectionEnabled` param; tests updated first. RED: compile errors (missing arg), GREEN after change.
- `deleteIdFor` removed (ConfirmingDelete branch calls `onDelete(id)` directly); replaced by a reducer test: ChooseDelete only reaches ConfirmingDelete from Actions.
- New `CollectionManageStateSaver` (primitives list) with a round-trip test over all four states. RED: unresolved `CollectionManageStateSaver`. GREEN: `--tests *CollectionUiLogicTest *NeutralSpanishResourcesTest` BUILD SUCCESSFUL.
- Removed two restating tests (distinct resources, empty state strings differ) and the duplicate enabled-but-empty empty-state test (identical input after param drop); `calm` fixture moved to top.
- Compose-only changes (compile-verified, NOT executed): chip touch target via `minimumInteractiveComponentSize` on an outer Box, `Role.Switch` + `toggleableState` + `selected`, click/long-click labels (new strings in both locales); `CollectionNameDialog` try/finally (rethrows, does not swallow); picker list in a `LazyColumn` with the create/limit footer pinned; picker rows `toggleable(role = Checkbox)`; actions dialog replaced by a bottom sheet with Rename and Delete rows.
- Structure: `CollectionsFeedOverlay` (BoxScope extension) owns chips, manage host and picker; `CollectionManageHost` moved to its own file; MainActivity keeps only `collectionPickerAffirmationId`. Recreation: both picker id and manage state are `rememberSaveable`. Named constants for chip offsets. Removed unused `collection_picker_done`, unused imports, FQN `R`. `messageRes()` for None now `error(...)`.
- androidTests added/extended: `CollectionNameDialogTest` (Duplicate then Ok) and `assertIsSelected`/`assertIsNotSelected` in `CollectionChipsRowTest`: compile only.

Evidence: `./gradlew -Dorg.gradle.workers.max=2 :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin` BUILD SUCCESSFUL; unit suite 1061 tests, 0 failures/errors.
Deviations: `error()` chosen for FeedEmptyState.None (empty branch and emptyState share the same list, so it is unreachable); Saver stores the chip snapshot (name/count) so a restored dialog shows the pre-recreation name.
Deferred (not done): shared AffirmationActionRow, string key merge/rename, unwrapping the onAddToCollection lambda, stale-name snapshot in delete dialog.
Not executed: visual check (3.8), all instrumented tests, lintDebug.

