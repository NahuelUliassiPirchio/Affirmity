# Verification Report: user-collections

Mode: openspec, Strict TDD. Commits e4bd3d4, 0d2a1da, 81a0881 (+ docs df5cdac, 67b58c2, bb1c564).
Verdict: PASS WITH WARNINGS (0 CRITICAL, 5 WARNING, 4 SUGGESTION)

## Execution evidence
- `./gradlew -Dorg.gradle.workers.max=2 :app:cleanTestDebugUnitTest :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin`: BUILD SUCCESSFUL, exit 0.
- Unit suite (parsed from build/test-results XML after a clean): 1061 tests, 0 failures, 0 errors, 0 skipped.
  Change tests: UserCollectionsTest 18, AffirmityAppStateUserCollectionsTest 31, CollectionUiLogicTest 16, RoomUserCollectionRepositoryTest 3, NeutralSpanishResourcesTest 2 (guard).
- assembleDebug OK; compileDebugAndroidTestKotlin OK. lintDebug and connectedDebugAndroidTest NOT run (as instructed).
- Coverage tool: none configured; coverage skipped.

## Tasks
31/32 checked. Only 3.8 (manual visual check) unchecked, expected. Task 3.9 is checked although lintDebug was not run (documented in tasks.md).

## Compliance matrix
Legend: EXEC = covered by executed unit test; AT = only by compiled (not run) androidTest.
Test files: UC = data/UserCollectionsTest.kt, AS = data/AffirmityAppStateUserCollectionsTest.kt, UI = ui/collections/CollectionUiLogicTest.kt, RR = data/repository/RoomUserCollectionRepositoryTest.kt, DAO = androidTest UserCollectionDaoTest.kt, MIG = androidTest AffirmityDatabaseMigrationTest.kt.

### user-collections
| Requirement / scenario | Implementation | Test | Status |
|---|---|---|---|
| Name trimmed | UserCollections.kt:55,60 | UC:61, AS:91 | EXEC |
| Duplicate rejected (create) | UserCollections.kt:58-60 | UC:91,98, AS:134 | EXEC |
| Duplicate rejected (rename) | AffirmityAppState.renameCollection | UC:104, AS:333,374 | EXEC |
| Blank / 41 chars rejected | UserCollections.kt:56-57 | UC:66,72,81, AS:119 | EXEC |
| Free blocked at third | UserCollections.kt:67-76,92 | UC:28,113, AS:154 | EXEC |
| Pro unlimited | canCreateUserCollection | UC:121, AS:173 | EXEC |
| Downgrade keeps collections | rename/toggle/add not tier-gated | AS:183 | EXEC |
| Create with affirmation (enabled, member, lastUsed==created) | AppState.createCollection; RoomUserCollectionRepository.create | AS:91, RR:41 | EXEC |
| Add/remove, idempotent | AppState add/removeFromCollection; DAO insertItem IGNORE | AS:389 (delegation, fake), DAO:114 (IGNORE) | EXEC for add/remove; idempotency at DAO level AT |
| Several collections per affirmation, empty collection allowed | schema PK(collection, affirmation) | AS:446, AS:264 | EXEC |
| Hide keeps membership | feed filter only, no write | AS:470 | EXEC |
| Orphan not counted | toUserCollectionUi | UC:44, AS:409 | EXEC |
| Toggle ON reorders | writeCollectionEnabled -> enable(now); DAO order | AS:208 (fake), DAO:57,72,84 | EXEC (AppState call) / ordering SQL AT |
| Toggle OFF keeps order | disable(id) | AS:229,250 | EXEC |
| Toggles serialized | userCollectionMutex | AS:286,320 | EXEC |
| Delete cascades memberships | FK ON DELETE CASCADE | DAO:42, MIG:343 | AT |
| Delete requires UI confirmation | CollectionManageHost / reducer | UI:111,146 (reducer only) | EXEC (logic); dialog rendering unverified |
| Owned delete cleans memberships | AppState.removeAffirmation (~L1599) | AS:537,558 | EXEC |
| Import replace clears owned only | AppState import (~L1553) | AS:572,596 | EXEC |
| Upgrade v13->v14 | MIGRATION_13_14 | MIG:343 | AT (SQL verified equal to 14.json) |
| Persist across sign-out | repo outside DataSession; signOut does not touch it | none | UNTESTED (structural only) |

### feed-composition
| Requirement / scenario | Implementation | Test | Status |
|---|---|---|---|
| Union | AppState.filteredAffirmations fromCollections | AS:428 | EXEC |
| Disabled contributes nothing | resolveEnabledCollectionRows filter | UC:131, AS:437 | EXEC |
| Two collections once | distinct + distinctBy | UC:144, AS:446 | EXEC |
| Hidden outranks, stays member | eligible lambda | AS:470, UC:157 | EXEC |
| Locked after downgrade | catalogRowUnlocked in lambda | AS:481 | EXEC |
| Unresolved themes: owned only | collectionRows { id in ownIds } | UC:167, AS:493 | EXEC |
| Sheet valid: collections only | isDraftThemeSelectionValid | UC:180, AS:349 | EXEC |
| Sheet invalid: nothing | same | UC:191, AS:349 | EXEC |
| Last chip off allowed, feed empty | toggleCollection/disable | AS:264 | EXEC |
| Empty state pointing to collections shown | feedEmptyState (CollectionUiLogic.kt:47), MainActivity:1734, AffirmationsScreen:136 | UI:78,86 (selection only) | EXEC (decision) / rendering unverified |
| Re-enable shows members | toggleCollection ON | AS:505 (OFF->ON adds row); from empty-state start not tested; chips remain visible when feed empty by overlay condition (CollectionsFeedOverlay.kt:52) | EXEC (partial) |
| Live toggles, no draft | direct repo write | AS:250 | EXEC |
| No reshuffle | orderFeed, collection slot | AS:505 (both orderings, ON then OFF) | EXEC |

Summary: 31 rows; 22 EXEC, 2 EXEC-partial, 5 AT-involved (migration, cascade, DAO order/idempotency), 1 UNTESTED (sign-out), 0 NOT-IMPLEMENTED.

## TDD compliance
- Evidence tables present for all 3 batches: PASS.
- RED test files exist (UC, AS, UI, RR, DAO, MIG, CollectionChipsRowTest, CollectionNameDialogTest): PASS.
- GREEN confirmed by execution for all JVM tests (1061/0). Tasks 1.1, 1.2 (androidTest) and 3.5 chips: compiled only, not executed (WARNING).
- Triangulation: adequate (multiple distinct-value cases; both orderings in no-reshuffle; mutation checks recorded).
- Fix-pass items "characterisation, passed first run" are disclosed honestly as non-RED.
- Assertion quality: no tautologies, no ghost loops, no type-only asserts. Eight isEmpty assertions all have a companion non-empty assertion in the same test or a sibling. Mock/assertion ratio fine (fakes, not mocks). 0 CRITICAL, 0 WARNING.
- Layers: unit 68 change tests across 5 files; androidTest 11+ not run.

## Design adherence
Matches D1-D9, slices, file list, cleanup hooks, migration. Deviations recorded in apply-progress, none contradicts a spec or confirmed decision:
- anyCollectionEnabled public; createCollection checks limit before name; chips custom Surface (not FilterChip); empty state keyed on "user owns any collection"; onAddToCollection nullable; paywall reuses PaywallSource.OTHER; FeedEmptyState.None -> error().
Confirmed decisions checked against code: Free limit 2, additive, new starts enabled, lastUsedAt only on enable, last chip off allowed, hidden outranks, Pro-locked excluded but kept, name rules (trim/40 code points/lowercase(Locale.ROOT)), replace-import clears owned only, no detail screen (none exists). All OK.

## Migration check
MIGRATION_13_14 statements (3) compared programmatically with 14.json createSql for user_collections, user_collection_items and index_user_collection_items_affirmationId: identical. DB version 14, entities and MIGRATION_13_14 registered.

## Leftovers
No TODO/FIXME, no commented-out code (only KDoc), no debug logs in new files (AppState adds one Log.e on flow failure, matches existing pattern). No hardcoded user-facing strings in new composables. values/ and values-en/ both have the same 24 collection_*/feed_empty_* keys. No AI attribution trailers in the 6 commit bodies (the two docs commits have empty bodies).

## Issues
CRITICAL: none.

WARNING
1. Task 3.8 visual check of chip row vs FloatingStatusOverlay not executed; placement (TopStart, 80dp) is layout arithmetic only.
2. Migration, DAO cascade/order/IGNORE/atomicity and chips/dialog androidTests are compiled but never executed (no device). Migration and cascade requirements rest solely on them.
3. "Persistence across sign-out" has no test. Holds structurally (repo outside DataSession, signOut does not touch it).
4. Task 3.9 is checked although lintDebug was not run (documented; 372 pre-existing lint errors).
5. "Re-enable" scenario is only covered via an OFF->ON toggle on a non-empty feed; no test that chips stay visible in the empty state (rendering only).

SUGGESTION
1. design.md is stale in places: sequence diagram says createCollection returns sync, API list shows non-suspend renameCollection, Open Questions section still unchecked though resolved; tasks.md forecast says chain strategy "size-exception" vs 3 commits.
2. apply-progress says 23 string keys; actual 24.
3. `userCollections` getter rebuilds an id HashSet on each read (deferred derivedStateOf item).
4. Add a unit test for chip visibility rule (hidden only when clean screen or no collections) by extracting it to CollectionUiLogic.

## Unverified (no device)
All androidTests (UserCollectionDaoTest, migrate13To14, CollectionChipsRowTest, CollectionNameDialogTest, existing migration chain), visual check 3.8 (chip placement, overlap with heart and FloatingStatusOverlay, touch targets), actual rendering of picker sheet, name dialog, manage sheet, delete confirmation and both empty-state copies, rotation restore of dialogs, real Room FK enforcement and ORDER BY at runtime, lintDebug.

## Next
sdd-archive may proceed after the user accepts the unverified device items (or runs connectedDebugAndroidTest and the 3.8 visual check).

## Post-verify follow-up
The stale docs from suggestions 1 and 2 were fixed (design.md: suspend create/rename, resolved open questions, UI hooks; tasks.md: final single-PR size:exception decision, 3.9 lint note). apply-progress.md string count corrected to 24. Findings above are unchanged. Still unverified: all androidTests (DAO, migration, chips, name dialog), the 3.8 visual check, and lintDebug.
