## Exploration: user-collections (local-only Room collections, feed chips)

Source of truth: Engram `sdd/user-collections/explore` (#1752).

### Current State
- Feed = `AffirmityAppState.filteredAffirmations` (AffirmityAppState.kt L583-646): own (if `FeedSources.includeOwn`) + favorites (if `includeFavorites`, access-gated via `catalogRowUnlocked`) + catalog rows whose theme is in committed `selectedThemeIds` and unlocked; `distinctBy` id; then `orderFeed` (FeedOrdering.kt, hash-sort keyed by orderSeed+id, so adding rows never reshuffles others). Hidden ids (DataStore) outrank every source. Pre-resolution branch (`selectedThemeIds == null`) is owned-only.
- `FeedSources` (TrackerPreferences.kt L35) = DataStore booleans + seed. Draft/commit: `setDraftFeedSources` (memory only) -> `applyThemeSelection` (L1939) commits and persists; `isDraftThemeSelectionValid` (L306) = draftThemes nonEmpty || includeFavorites || includeOwn; `isFeedDraftDirty` compares draft vs committed.
- Favorites precedent: `FavoriteAffirmationEntity` (PK affirmationId, no FK), Dao, interface + NoOp in Repositories.kt, `RoomFavoriteAffirmationRepository`, injected as `AffirmityAppState` ctor param `favorites` (default NoOp), OUTSIDE `DataSession`. Collected in init L954. Cleanup hooks: `removeAffirmation` L1531 (`favorites.remove`), `importAffirmationsFromJson` replaceExisting L1493 (`favorites.clear`).
- Long press: AffirmationsScreen.kt L306 sets `showActions`; `AffirmationActionsSheet` L477 takes 4 lambdas, 3 `AffirmationActionRow` rows.
- Feed composition: MainActivity.kt ~L1655-1730 (BottomSheetScaffold with `YourFeedSheetContent`; `AffirmationsScreen` gets `appState.filteredAffirmations`). Chips go in the Box around `AffirmationsScreen` (L1698), NOT in `YourFeedSheetContent`.
- Room: `AffirmityDatabase` version 13, exportSchema, `MIGRATION_12_13` last, registered in `addMigrations`; androidTest has `AffirmityDatabaseMigrationTest` + `FavoriteAffirmationDaoTest`.
- Unit tests use Mockito (`mock(TrackerPreferences)`, `mock(Notifier)`; 10 `AffirmityAppState*Test` files with per-file `buildState` helpers). A new ctor param with a NoOp default avoids touching them. A new TrackerPreferences flow would need stubbing everywhere (kdoc at TrackerPreferences L128).
- Naming: `catalogCollectionsById()` / `Affirmation.collectionId` already mean catalog access unit -> use `UserCollection` / `user_collections`.

### Integration points and risks
- Persistence: Room. `user_collections(id TEXT PK UUID, name, createdAtMillis, enabled INTEGER)` and `user_collection_items(collectionId, affirmationId, addedAtMillis; composite PK; FK collectionId -> user_collections ON DELETE CASCADE; index on affirmationId)`. NO FK on affirmationId (ids live in two stores). Migration 13->14 (additive), hand-written SQL must match Room's exported schema incl. FK + index names; new `14.json`; androidTest migration test.
- Enabled state: Room column on `user_collections` (atomic with delete, no stale-id pruning, no FeedSources/Mockito churn). Caveat: `enabled` is device-local if sync is added later.
- Chips: LIVE toggle (immediate write), independent of the sheet's draft. `isDraftThemeSelectionValid` and the sheet's `confirmValueChange` must treat "any collection enabled" as a feed carrier.
- `filteredAffirmations`: collections segment between favorites and catalog. Owned ids pass if present; catalog ids pass if present AND `catalogRowUnlocked` AND not hidden. `distinctBy` dedupes. Extract union as a pure function.
- Proposed API: `userCollections: List<UserCollectionUi(id,name,enabled,resolvedItemCount)>`, `collectionIdsFor(affirmationId)`, `createCollection(name, withAffirmationId?)`, `renameCollection`, `deleteCollection`, `addToCollection/removeFromCollection`, `setCollectionEnabled/toggle`. Serialize writes with a Mutex.
- UI hooks: `AffirmationActionsSheet.onAddToCollection`; second sheet listing collections (checked if member) + "Create new collection"; chips row in MainActivity. Strings via strings.xml.

### Id stability
- Owned ids: UUID, preserved into Firestore on migration.
- Catalog ids: `cat_` + source id; seeder `replaceAll` on version bump; removed ids become harmless orphans. Counts must come from RESOLVED affirmations.
- Owned delete and JSON import replace must also clear memberships.
- Hide outranks membership; membership retained.
- Pro-locked catalog rows stay out of the feed after downgrade.

### Firestore / DataSession
- Collections live outside DataSession like favorites: no FirestoreMigrator/MigrationPlan/firestore.rules changes. Only `NoOpUserCollectionRepository` as ctor default.
- Seam for later sync: UUID ids, intent-based repository API.
- Gotcha: local collections persist across sign-out and across accounts on a shared device.

### Approaches
1. Room tables + `enabled` column, repository outside DataSession, live chip toggles (RECOMMENDED, Medium).
2. Room for collections/items, enabled ids in FeedSources with draft/commit (Medium-High).
3. Everything in DataStore (Low-Medium, not recommended).

### Recommendation
Approach 1, additive-source semantics, live toggles, pure union function, strict TDD against a fake repository. Estimate 600-800 changed lines (over the 400 budget): data+migration (~150), repo (~80), AppState (~120), UI (~150), tests (~300+). Natural slices: (a) data layer + migration, (b) AppState + feed union, (c) UI.

### Risks
- Additive vs exclusive semantics change `filteredAffirmations` and the validity rule.
- Sheet validity / `confirmValueChange` must include collections.
- Migration SQL must match the exported schema exactly.
- Orphan rows and inflated counts.
- Cross-account persistence on shared devices.
- `AffirmityAppState` size / Mockito helper coupling.

### Open product questions
1. Additive (like favorites) vs exclusive (playlist) mode? Zero chips on?
2. Empty collections allowed? New collection enabled by default?
3. Free vs Pro limits on collections/items?
4. Hiding removes from collections? Delete confirmation?
5. Name rules (duplicates, max length), rename/delete, chip ordering?
6. Pro-locked rows in a collection after downgrade (recommend: keep, not shown).
7. Detail screen now or later?
8. Keep local collections on sign-out / account switch?

### Ready for Proposal
Yes, after answers to questions 1 and 3; the rest have stated defaults.
