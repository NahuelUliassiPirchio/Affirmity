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
