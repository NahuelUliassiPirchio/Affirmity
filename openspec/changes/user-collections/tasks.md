# Tasks: User Collections

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~1500 (a ~350, b ~650, c ~500) |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 (a) -> PR 2 (b) -> PR 3 (c) |
| Delivery strategy | single-pr |
| Chain strategy | size-exception |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: size-exception
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| A | Data + migration, no behaviour change (~350, incl. generated 14.json) | PR 1 | base master |
| B | AppState + feed union + tests (~650) | PR 2 | needs A |
| C | UI + strings (~500) | PR 3 | needs B |

Paths relative to `app/src/main/java/com/pirxhio/affirmity/`. Tests: `./gradlew :app:testDebugUnitTest` (batch runs per task).

## Slice A: Data layer + migration

- [x] 1.1 RED: androidTest `UserCollectionDaoTest` (cascade, enable/disable, order, `createWithItem` atomicity, duplicate item IGNORE)
- [x] 1.2 RED: add `migrate13To14_...` to `AffirmityDatabaseMigrationTest` (data intact, tables empty)
- [x] 1.3 GREEN: create `data/local/UserCollectionEntity.kt`, `UserCollectionItemEntity.kt`, `UserCollectionWithItems.kt`
- [x] 1.4 GREEN: create `UserCollectionDao.kt` (D1/D3 methods per design)
- [x] 1.5 GREEN: `AffirmityDatabase.kt` v14, entities, `userCollectionDao()`, `MIGRATION_13_14`
- [x] 1.6 Build, then diff migration SQL against generated `app/schemas/.../14.json`; schema wins
- [x] 1.7 Add `UserCollectionRepository` + `NoOpUserCollectionRepository` to `data/repository/Repositories.kt`
- [x] 1.8 Create `RoomUserCollectionRepository.kt`
- [x] 1.9 REFACTOR: run unit suite; run `connectedDebugAndroidTest` (migration/DAO) if device available (unit suite green; androidTest compiled only, NOT executed: no device)

## Slice B: AppState + feed union

- [x] 2.1 RED: `UserCollectionsTest` for `validateCollectionName` (trim, blank, 41, case-insensitive dup, Unicode), `canCreateUserCollection`
- [x] 2.2 RED: tests for `resolveEnabledCollectionRows` (dedupe, hidden, locked, owned-only pre-resolution) and `isDraftThemeSelectionValid` collections-only/nothing
- [x] 2.3 GREEN: create `data/UserCollections.kt` (model, constants, pure functions)
- [x] 2.4 RED: `RecordingUserCollectionRepository` fake + `AffirmityAppStateUserCollectionsTest` (create, Free limit, downgrade, toggle ON bumps / OFF does not, last chip off allowed, Mutex order)
- [x] 2.5 RED: feed tests (union, disabled, two collections once, hidden, Pro-locked, pre-resolution, no reshuffle)
- [x] 2.6 RED: cleanup tests (`removeAffirmation`, replace-import clears owned ids only)
- [x] 2.7 GREEN: `AffirmityAppState.kt` ctor params (`collectionRepository`, clock, id factory), collector, `userCollections`, `canCreateCollection`, `userCollectionIdsFor`
- [x] 2.8 GREEN: write API (create/rename/delete/add/remove/enable/toggle) under `userCollectionMutex`
- [x] 2.9 GREEN: collections segment in `filteredAffirmations`, validity rule, cleanup hooks
- [x] 2.10 GREEN: wire `RoomUserCollectionRepository` at composition root (~L2121)
- [x] 2.11 REFACTOR: tidy, full unit suite green

## Slice C: UI

- [x] 3.1 Add strings (picker, dialog errors, chips, delete confirm, empty state pointing to collections) to `res/values/strings.xml` and `values-en/strings.xml`
- [x] 3.2 `AffirmationsScreen.kt`: `onAddToCollection` param + `PlaylistAdd` row in `AffirmationActionsSheet`
- [x] 3.3 Create `ui/collections/CollectionNameDialog.kt` (inline error from `CollectionNameResult`)
- [x] 3.4 Create `ui/collections/CollectionPickerSheet.kt` (membership toggles, create new, limit state)
- [x] 3.5 Create `ui/collections/CollectionChipsRow.kt` (tap toggle, long-press rename/delete); fix pass: `CollectionManageHost.kt` split out, `CollectionsFeedOverlay.kt` host added
- [x] 3.6 `MainActivity.kt`: picker state, chips row in L1698 Box (hidden in clean screen/when none), delete confirmation
- [x] 3.7 Replace misleading empty-feed copy with collections empty state
- [ ] 3.8 Visual check of chip row vs `FloatingStatusOverlay`; adjust placement (MANUAL, NOT EXECUTED: placement chosen by layout arithmetic only)
- [x] 3.9 Run `assembleDebug`, `lintDebug`, unit suite (assembleDebug + unit suite 1061/0 after the fix pass; lintDebug NOT run)
