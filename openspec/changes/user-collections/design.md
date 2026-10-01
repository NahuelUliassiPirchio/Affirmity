# Design: User Collections (local, additive feed source)

## Technical Approach

Follows the exploration's Approach 1 and copies the favorites precedent. Two new Room tables sit behind an intent-based `UserCollectionRepository`. The repository is injected into `AffirmityAppState` as a ctor param with a NoOp default, OUTSIDE `DataSession`. The feed gains a collections segment that a pure function resolves. Chips write `enabled` straight away, with no draft step. Every write goes through one `Mutex`.

Verified against the source: `isDraftThemeSelectionValid` is at L306 and `filteredAffirmations` at L583-646, both in `data/AffirmityAppState.kt`. Favorites are collected at L954. The cleanup hooks are at L1493 and L1531. The sheet's `confirmValueChange` is at MainActivity L1648, and the Box around `AffirmationsScreen` is at L1698. `AffirmationActionsSheet` is at L477 and takes 4 lambdas. The long-press `showActions` is at L296/L309, not L306 as the exploration says. The DB is at v13 with `MIGRATION_12_13`.

## Architecture Decisions

| # | Option chosen | Rejected | Rationale |
|---|---|---|---|
| D1 | Keep `enabled` + `lastUsedAtMillis` as columns on `user_collections` | Enabled ids in `FeedSources` (DataStore) | A collection delete removes its enabled state in the same write. This avoids Mockito re-stubbing in 10 test files. |
| D2 | Repository outside `DataSession`, default param `NoOpUserCollectionRepository` | Inside `DataSession` | Collections are local-only. No Firestore, migrator or rules changes. Existing `buildState` helpers keep compiling. |
| D3 | Split enable/disable into `enable(id, nowMillis)` and `disable(id)` | `setEnabled(id, Boolean, now)` | The signature itself enforces "lastUsedAt bumps only on toggle ON", so fakes cannot drift from the rule. |
| D4 | Check name uniqueness in Kotlin (`trim().lowercase(Locale.ROOT)`), re-checked under the Mutex | `COLLATE NOCASE` unique index | NOCASE only folds ASCII, so "Ánimo" and "ánimo" would both pass. It would also change the exported schema. |
| D5 | Rely on the FK `ON DELETE CASCADE` for items; no FK on `affirmationId` | Manual item delete | Affirmation ids live in two stores (owned in Room/Firestore, catalog `cat_`). Room turns on `PRAGMA foreign_keys` when entities declare FKs. |
| D6 | Pure generic `resolveEnabledCollectionRows` | Inline logic in the getter | Unit-testable without Compose state. It reuses the same eligibility lambda as favorites. |
| D7 | `suspend createCollection`/`renameCollection` validate against the repository under the Mutex and return that authoritative result (revised in the batch 2 fix pass) | Sync in-memory pre-check + async write (returned Ok while the write could silently fail) | The returned value is exactly what was persisted, and the Mutex closes the double-tap race. |
| D8 | Host the collection picker in MainActivity; `AffirmationsScreen` only emits `onAddToCollection(id)` | Picker inside `AffirmationsScreen` | Keeps the screen presentational and avoids threading 6 more params through it. |
| D9 | Inject clock and UUID factory as ctor params (`collectionClock`, `collectionIdFactory`) with defaults | `System.currentTimeMillis()` inline | Makes ordering and ids testable in tests. Existing tests are unaffected. |

## Data Model and Migration v13 -> v14

```kotlin
@Entity(tableName = "user_collections")
data class UserCollectionEntity(
    @PrimaryKey val id: String, val name: String,
    val createdAtMillis: Long, val enabled: Boolean, val lastUsedAtMillis: Long,
)
@Entity(
    tableName = "user_collection_items",
    primaryKeys = ["userCollectionId", "affirmationId"],
    foreignKeys = [ForeignKey(entity = UserCollectionEntity::class, parentColumns = ["id"],
        childColumns = ["userCollectionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("affirmationId")],
)
data class UserCollectionItemEntity(val userCollectionId: String, val affirmationId: String, val addedAtMillis: Long)
data class UserCollectionWithItems(
    @Embedded val collection: UserCollectionEntity,
    @Relation(parentColumn = "id", entityColumn = "userCollectionId",
        entity = UserCollectionItemEntity::class, projection = ["affirmationId"])
    val affirmationIds: List<String>,
)
```

`MIGRATION_13_14`, which must match the generated `14.json` (no DEFAULTs, because the entities declare none):

```sql
CREATE TABLE IF NOT EXISTS `user_collections` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `createdAtMillis` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `lastUsedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`));
CREATE TABLE IF NOT EXISTS `user_collection_items` (`userCollectionId` TEXT NOT NULL, `affirmationId` TEXT NOT NULL, `addedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`userCollectionId`, `affirmationId`), FOREIGN KEY(`userCollectionId`) REFERENCES `user_collections`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );
CREATE INDEX IF NOT EXISTS `index_user_collection_items_affirmationId` ON `user_collection_items` (`affirmationId`);
```

Rule for apply: build first, then diff the SQL above against `14.json` `createSql`. The exported schema wins.

`UserCollectionDao`:
- `@Transaction observeAll(): Flow<List<UserCollectionWithItems>>` with `ORDER BY lastUsedAtMillis DESC, createdAtMillis DESC, id ASC` (one shared constant)
- `getAll()`
- `insertCollection` (ABORT)
- `insertItem` (IGNORE)
- `@Transaction createWithItem`
- `rename`
- `deleteById`
- `deleteItem`
- `enable(id, now)` runs `SET enabled=1, lastUsedAtMillis=:now`
- `disable(id)`
- `deleteItemsForAffirmations(ids)`

## Interfaces / Contracts

```kotlin
// data/repository/Repositories.kt
interface UserCollectionRepository {
    fun observeCollections(): Flow<List<UserCollection>>   // chip order
    suspend fun getCollections(): List<UserCollection>
    suspend fun create(id: String, name: String, nowMillis: Long, initialAffirmationId: String?)
    suspend fun rename(id: String, name: String)
    suspend fun delete(id: String)
    suspend fun addItem(userCollectionId: String, affirmationId: String, nowMillis: Long)
    suspend fun removeItem(userCollectionId: String, affirmationId: String)
    suspend fun enable(id: String, nowMillis: Long)
    suspend fun disable(id: String)
    suspend fun removeAffirmations(affirmationIds: Collection<String>)
}
object NoOpUserCollectionRepository : UserCollectionRepository { /* flowOf(emptyList()), Unit */ }

// data/UserCollections.kt (pure)
data class UserCollection(val id: String, val name: String, val createdAtMillis: Long,
    val enabled: Boolean, val lastUsedAtMillis: Long, val affirmationIds: List<String>)
data class UserCollectionUi(val id: String, val name: String, val enabled: Boolean, val resolvedItemCount: Int)
sealed interface CollectionNameResult { data class Ok(val name: String); Blank; TooLong; Duplicate; LimitReached }
const val FREE_COLLECTION_LIMIT = 2; const val COLLECTION_NAME_MAX = 40   // code points
internal fun validateCollectionName(raw: String, existing: List<UserCollection>, excludingId: String? = null): CollectionNameResult
internal fun canCreateUserCollection(tier: AccessTier, count: Int): Boolean = tier == AccessTier.PRO || count < FREE_COLLECTION_LIMIT
internal fun <T> resolveEnabledCollectionRows(collections: List<UserCollection>, byId: (String) -> T?, eligible: (T) -> Boolean): List<T>
internal fun isDraftThemeSelectionValid(draftThemeIds: Set<String>, feedSources: FeedSources, anyCollectionEnabled: Boolean = false): Boolean
```

AppState API:
- `userCollections: List<UserCollectionUi>`. `resolvedItemCount` is computed from `allAffirmations`, so orphan ids do not count.
- `canCreateCollection`
- `userCollectionIdsFor(affirmationId): Set<String>`
- `suspend createCollection(name, withAffirmationId: String? = null): CollectionNameResult`
- `renameCollection(id, name): CollectionNameResult`
- `deleteCollection(id)`
- `addToCollection(cid, aid)` and `removeFromCollection(cid, aid)`
- `setCollectionEnabled(id, enabled)` and `toggleCollection(id)`

All writes run as `scope.launch { userCollectionMutex.withLock { … } }`. On a downgrade nothing is deleted. Only `canCreateCollection` goes false while a Free user has 2 or more collections.

## Feed Composition

`filteredAffirmations` becomes `own + favorites + collections + themed`, then `.distinctBy { it.id }`, then `orderFeed`. Collection rows use the same eligibility check as the favorites lambda: `id !in hiddenIds && (id in ownIds || catalogRowUnlocked(it))`. In the pre-resolution branch, collection rows pass only if they are owned (same as favorites). Hidden ids outrank membership, and the membership row is kept. `isDraftThemeSelectionValid` passes `collectionsState.any { it.enabled }`. The sheet's `confirmValueChange` and `YourFeedSheetContent.isValid` already read that property, so MainActivity L1648 needs no edit.

## Sequence Flows

```mermaid
sequenceDiagram
  participant U as User
  participant S as AffirmationsScreen
  participant M as MainActivity (picker)
  participant A as AppState
  participant R as UserCollectionRepository
  U->>S: long-press > "Add to collection"
  S->>M: onAddToCollection(aid)
  M->>A: userCollectionIdsFor(aid), canCreateCollection
  U->>M: "Create new" + name
  M->>A: createCollection(name, aid)
  A-->>M: Ok | Blank | TooLong | Duplicate | LimitReached (sync)
  A->>R: [Mutex] re-validate via getCollections(); create(uuid, name, now, aid)
  R-->>A: observeCollections() emits; chips + feed recompose
  U->>M: tap chip (off -> on)
  M->>A: setCollectionEnabled(id, true)
  A->>R: [Mutex] enable(id, now)  // disable(id) never touches lastUsedAt
```

## Cleanup Hooks

- `removeAffirmation`: after `favorites.remove(id)`, call `collectionRepository.removeAffirmations(listOf(id))` under the Mutex. The ctor param is named `collectionRepository` so it does not clash with the `userCollections` UI property.
- JSON import with replace: snapshot `affirmations.map { it.id }` BEFORE `deleteAll()`, then call `removeAffirmations(ownedIds)`. Collections themselves and catalog memberships are kept (see Open Questions).

## File Changes

| File | Action |
|---|---|
| `data/local/UserCollectionEntity.kt`, `UserCollectionItemEntity.kt`, `UserCollectionWithItems.kt`, `UserCollectionDao.kt` | Create |
| `data/local/AffirmityDatabase.kt` | Modify: add entities, set v14, add `MIGRATION_13_14`, add `userCollectionDao()` |
| `data/repository/Repositories.kt` | Modify: add the interface and NoOp |
| `data/repository/RoomUserCollectionRepository.kt` | Create |
| `data/UserCollections.kt` | Create: model plus pure functions |
| `data/AffirmityAppState.kt` | Modify: ctor params, collector, API, feed segment, validity, cleanup, wiring at L2121 |
| `ui/affirmations/AffirmationsScreen.kt` | Modify: `onAddToCollection` param and action row (`PlaylistAdd`) |
| `ui/collections/CollectionPickerSheet.kt`, `CollectionNameDialog.kt`, `CollectionChipsRow.kt` | Create |
| `MainActivity.kt` | Modify: picker state, chips row in the L1698 Box (hidden in clean screen or when empty), chip long-press to rename/delete plus delete confirmation |
| `res/values/strings.xml`, `res/values-en/strings.xml` | Modify |
| `app/schemas/.../14.json` | Generated |

All paths are under `app/src/main/java/com/pirxhio/affirmity/` unless stated otherwise.

## Testing Strategy (Strict TDD)

| Layer | What | Approach |
|---|---|---|
| Unit | `validateCollectionName`, `canCreateUserCollection`, `resolveEnabledCollectionRows`, the new validity case | Plain JUnit4 |
| Unit | AppState: create/limit/downgrade, toggle bumps `lastUsedAt` only when going ON, the feed union (dedupe, hidden, Pro-locked), cleanup hooks, Mutex ordering | New `AffirmityAppStateUserCollectionsTest` with hand-written `RecordingUserCollectionRepository` (MutableStateFlow + event log), `runTest`, and an injected clock/UUID. Existing Mockito helpers are untouched. |
| androidTest | DAO: cascade, enable/disable, order, `createWithItem` atomicity, IGNORE on duplicate items | `UserCollectionDaoTest` (in-memory DB) |
| androidTest | Migration from 13 to 14 with existing data intact | Add `migrate13To14_…` to `AffirmityDatabaseMigrationTest` |

## Slices

- (a) Data + migration: entities, DAO, DB v14, `14.json`, repository, NoOp, androidTest. No behaviour change.
- (b) AppState + feed: `UserCollections.kt`, AppState API, feed segment, validity, cleanup hooks, unit tests, composition-root wiring.
- (c) UI: action row, picker, dialog, chips, strings.

Each slice compiles and ships on its own.

## Migration / Rollout

The migration is additive. Rollback for released builds is a forward migration from v14 to v15 that drops both tables (this follows the proposal).

## Resolved open questions

- **Last carrier off**: ALLOWED. Turning off the last enabled chip is never blocked; the feed may become empty. A dedicated empty state points the user to collections (the current empty-feed copy points to Progress and is misleading); its string lives in `strings.xml`.
- **Import replace scope**: replace-import clears memberships only for the owned affirmations it deletes. Collections and catalog memberships are kept.
- **Rename/delete entry point**: long-press on a chip (delete requires confirmation).
- **Chip placement**: overlap with `FloatingStatusOverlay` is deferred to the UI slice (visual check).

## Open Questions / Proposal Deltas (superseded where listed above)

- [ ] **Import replace scope**: the design clears only memberships of owned ids. The proposal's "clear memberships" could be read as clearing all of them. Favorites' precedent clears all favorites.
- [ ] **Last carrier off**: a live chip OFF can leave the committed feed empty (no themes, sources off). Recommended: allow it and show an empty-feed message instead of the current "Agrega tu primera afirmación" copy. Needs a product call.
- [ ] **Empty enabled collection**: it counts as a valid carrier, as the proposal says, so the feed can be empty.
- [ ] **Rename/delete entry point**: chip long-press is assumed, since the proposal has no detail screen.
- [ ] **Chip placement**: possible overlap with `FloatingStatusOverlay` (TopEnd). Needs a visual check.
