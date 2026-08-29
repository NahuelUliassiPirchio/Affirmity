# Tasks: Favorite Affirmations

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~750–950 |
| 400-line budget risk | High |
| Chained PRs recommended | Yes (would normally be recommended) |
| Suggested split | Single PR (`size:exception`) — user locked `single-pr` |
| Delivery strategy | single-pr |
| Chain strategy | size-exception |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: size-exception
400-line budget risk: High

New pure arbiter + Room entity/DAO/migration + repository layer + `AffirmityAppState` wiring +
two-tier gesture UI + new `FavoritesScreen` + menu wiring + JVM tests + `androidTest` DAO/migration
tests + generated `8.json` schema cross four layers in one coherent slice (proposal Risk table
flagged this). Design's natural cut (persistence+state vs. gesture+screen) exists but
`delivery_strategy=single-pr` overrides it — `sdd-apply` must record `size:exception` before
starting rather than splitting.

### Suggested Work Units

| Unit | Goal | Scope | Verification |
|------|------|-------|---------------|
| 1 | Single PR, `size:exception` | All phases below | `gradlew.bat testDebugUnitTest` + `assembleDebug` + manual on-device gesture/migration checks (Phase 6) |

## Phase 1: Pure Arbiter Layer (TDD)

- [x] 1.1 RED `app/src/test/.../ui/affirmations/FavoriteTapArbiterTest.kt`: single tap on key `k` → `Wait(k, 300)`; two taps on `k` at `t`/`t+299` → `Wait` then `ToggleFavorite`; two taps at `t`/`t+300` (boundary, exclusive) → `Wait` then `Wait`; tap `k1` then `k2` inside window → `Wait(k1)` then `Wait(k2)`; triple tap inside window → `Wait`/`ToggleFavorite`/`Wait`; `reset()` makes next tap unconditionally first.
- [x] 1.2 GREEN `ui/affirmations/FavoriteTapArbiter.kt`: `TokenTapDecision` (`Wait`/`StartEditing`/`ToggleFavorite`), `FavoriteTapArbiter.onTokenTap`/`reset`, `DEFAULT_DOUBLE_TAP_WINDOW_MILLIS = 300L` (design D1/D2).

## Phase 2: Room Persistence (TDD)

- [x] 2.1 Create `data/local/FavoriteAffirmationEntity.kt`: `@Entity(tableName = "favorite_affirmations")`, `affirmationId` PK, `favoritedAtMillis: Long`, no `@ForeignKey` (D5).
- [x] 2.2 Create `data/local/FavoriteAffirmationDao.kt`: `insert` (`OnConflictStrategy.REPLACE`), `deleteById`, `deleteAll`, `observeFavoriteIds(): Flow<List<String>>` (`ORDER BY favoritedAtMillis DESC`, D10), `isFavorite` (`SELECT EXISTS`).
- [x] 2.3 Modify `data/local/AffirmityDatabase.kt`: `version = 8`, append `FavoriteAffirmationEntity::class` to `entities`, add `favoriteAffirmationDao()` accessor, add `MIGRATION_7_8` (`CREATE TABLE IF NOT EXISTS favorite_affirmations`, additive-only), append to `addMigrations(...)`. No `MIGRATION_8_7`, no `fallbackToDestructiveMigrationOnDowngrade`.
- [x] 2.4 Generate/commit `app/schemas/com.pirxhio.affirmity.data.local.AffirmityDatabase/8.json` via KSP (`assembleDebug`/`testDebugUnitTest`); verify it matches `MIGRATION_7_8` exactly.
- [x] 2.5 Modify `app/src/androidTest/.../AffirmityDatabaseMigrationTest.kt`: add `migrate7To8_createsEmptyFavoriteAffirmationsTableAndPreservesAffirmations` — existing tables/rows unchanged, new table exists and is empty.
- [x] 2.6 Create `app/src/androidTest/.../data/local/FavoriteAffirmationDaoTest.kt`: real Room round-trip (insert/observe/delete), `DESC` ordering, `REPLACE` refreshes recency for a duplicate id — per `AdUnlockDaoTest` shape.

## Phase 3: Repository Layer (TDD)

- [x] 3.1 Modify `data/repository/Repositories.kt`: add `FavoriteAffirmationRepository` interface (`observeFavoriteIds`, `isFavorite`, `add`, `remove`, `clear`) and `NoOpFavoriteAffirmationRepository` object. Do **not** touch `DataSession` (D4).
- [x] 3.2 RED `app/src/test/.../data/repository/RoomFavoriteAffirmationRepositoryTest.kt`: `add` builds `FavoriteAffirmationEntity(id, millis)`; `remove`/`clear`/`isFavorite` delegate 1:1; `observeFavoriteIds` passes the DAO `Flow` through untouched — against a hand-written fake DAO.
- [x] 3.3 GREEN `data/repository/RoomFavoriteAffirmationRepository.kt`: 1:1 DAO delegation, per `RoomAdUnlockRepository`.
- [x] 3.4 Wire `RoomFavoriteAffirmationRepository(database.favoriteAffirmationDao())` into `rememberAffirmityAppState`.

## Phase 4: App-State Wiring (TDD)

- [x] 4.1 RED `app/src/test/.../data/AffirmityAppStateFavoritesTest.kt` (toggle): unfavorited id → `favorites.add` with a monotonic timestamp; favorited id → `favorites.remove`; two concurrent `toggleFavorite` calls resolve add-then-remove, not add-then-add (mutex regression, D6) via `kotlinx-coroutines-test` `runTest` + a fake repo whose `isFavorite` suspends on a controllable gate.
- [x] 4.2 GREEN `data/AffirmityAppState.kt`: `favorites: FavoriteAffirmationRepository = NoOpFavoriteAffirmationRepository` ctor param, `favoriteAffirmationIds`/`favoriteOrderedIds` `mutableStateOf`, `favoriteToggleMutex`, `toggleFavorite(id)` (D6/D9), one collector in `init` from `favorites.observeFavoriteIds()` (not `session.flatMapLatest`, D4).
- [x] 4.3 RED (same test file): `favoriteAffirmations` follows `observeFavoriteIds` order (most recent first); an id with no matching affirmation is dropped (D7); an affirmation outside the current group selection still appears.
- [x] 4.4 GREEN: `favoriteAffirmations` derived getter — `favoriteOrderedIds.value.mapNotNull(byId::get)` (D7).
- [x] 4.5 RED: `removeAffirmation` calls `affirmations.deleteById` **then** `favorites.remove` — assert both calls and their order (D8) via recording fakes with a shared call log.
- [x] 4.6 GREEN: wire the cascade into `removeAffirmation` (`data/AffirmityAppState.kt`).
- [x] 4.7 RED: `importAffirmationsFromJson(replaceExisting = true)` calls `favorites.clear()` after `affirmationsRepo.deleteAll()`; `replaceExisting = false` never clears (D8 — corrects the proposal, which only covered `removeAffirmation`).
- [x] 4.8 GREEN: wire `favorites.clear()` into the `replaceExisting` branch of `importAffirmationsFromJson`.

## Phase 5: UI Wiring — Two-Tier Gesture + Favorites Screen

- [x] 5.1 Modify `ui/affirmations/AffirmationsScreen.kt`: `AffirmationsScreen` gains `favoriteIds`, `onToggleFavorite`, `favoriteGesture: FavoriteGesture = DOUBLE_TAP` params; `AffirmationCard` outer `Box` gets `Modifier.pointerInput(affirmation.id, favoriteGesture) { detectTapGestures(onDoubleTap = { onToggleFavorite() }) }` (Tier 1, D1) plus a heart indicator driven by `isFavorite`.
- [x] 5.2 Modify `ui/affirmations/TokenizedAffirmationText.kt`: add `onFavoriteToggleFromToken` param; route the `LinkAnnotation.Clickable` handler through `FavoriteTapArbiter.onTokenTap`; `pendingEditKey` state + keyed `LaunchedEffect(pendingEditKey)` deferring `startEditing` by `DEFAULT_DOUBLE_TAP_WINDOW_MILLIS` (Tier 2, D2); `ToggleFavorite` clears only `pendingEditKey`, never `editingKey`/`editingValue` (locked decision #1 / D11 — the active edit field itself is not a favorite hit target).
- [x] 5.3 Create `ui/favorites/FavoritesScreen.kt`: stateless `FavoritesScreen(favorites, onUnfavorite, modifier)`; empty state via `R.string.favorites_empty_state`; non-empty `LazyColumn` reusing `TokenizedAffirmationText(editable = false)` for title/subtitle, trailing unlike `IconButton` (instant, no dialog/snackbar).
- [x] 5.4 Modify `ui/groups/AffirmationGroupSelectorSheet.kt`: add `onFavoritesClick` param + `FavoritesEntryCard` item above `AddCustomAffirmationsCard`, mirroring its `Card`/`clickable`/`Icon`/`Text` shape with `Icons.Filled.Favorite` and `R.string.affirmation_group_open_favorites`.
- [x] 5.5 Modify `MainActivity.kt`: `showFavorites by rememberSaveable`, overlay block after the existing `showMyAffirmations` block (`BackHandler` + `Scaffold` + `TopAppBar` + `FavoritesScreen` + `PaywallHost`, no new `AppDestinations` entry); wire `onFavoritesClick = { showFavorites = true }`; feed call site gets `favoriteIds = appState.favoriteAffirmationIds.value`, `onToggleFavorite = appState::toggleFavorite`.
- [x] 5.6 Modify `res/values*/strings.xml`: add `favorites_title`, `favorites_empty_state`, `favorites_unlike_content_description`, `affirmation_group_open_favorites`.

## Phase 6: Verification

- [x] 6.1 Run `gradlew.bat testDebugUnitTest` — full suite green, no regression in existing `AffirmityAppState`/repository tests. (435 tests, 0 failures — verified locally; two RecordingFavoritesRepository-based tests needed an extra `runCurrent()` sync barrier before `advanceUntilIdle()`, a test-authoring fix, not a production bug.)
- [x] 6.2 Run `gradlew.bat assembleDebug` — build succeeds. (Verified locally, BUILD SUCCESSFUL.)
- [ ] 6.3 Run `gradlew.bat connectedDebugAndroidTest` — `FavoriteAffirmationDaoTest` and the `migrate7To8_...` migration test both green (requires device/emulator). New androidTest sources (`FavoriteAffirmationDaoTest.kt`, `FavoritesScreenTest.kt`, migration test addition) compile cleanly (`compileDebugAndroidTestKotlin` verified locally); actual execution still requires a connected device/emulator. Note: `compileDebugAndroidTestKotlin` also surfaces a pre-existing, unrelated compile error in `AffirmityAppStateInstrumentedTest.kt` (missing `moods`/`healerUses`/`adUnlocks` constructor args) — zero diff from this change, not introduced by it.
- [x] 6.4 **MANUAL/ON-DEVICE — blocking acceptance item, not a follow-up (per proposal + design Testing Strategy).** Verify: (a) double-tap anywhere on the card toggles the favorite AND `VerticalPager` vertical swipe still changes affirmation; (b) double-tap directly on a `[token]` toggles the favorite and does **not** open the token editor; (c) single-tap on a `[token]` still opens the editor (confirm the ~300 ms deferral reads as acceptable); (d) double-tap inside the *active* edit field selects a word and does not toggle; (e) favoriting mid-edit-on-another-token leaves that edit open and uncommitted. If (a) or (b) fails, execute the Phase 7 `LONG_PRESS` fallback and re-run this checklist, recording the deviation. (User-verified on device: `DOUBLE_TAP` works as designed, no conflict with pager swipe. Long-press fallback (Phase 7) not needed.)

## Phase 7: Long-Press Fallback (conditional — only if Phase 6.4 (a)/(b) fails)

- [ ] 7.1 Add `enum class FavoriteGesture { DOUBLE_TAP, LONG_PRESS }` (if not already added in 1.2/5.1) threaded through `AffirmationsScreen`/`AffirmationCard`/`TokenizedAffirmationText`.
- [ ] 7.2 Swap Tier 1 to `detectTapGestures(onLongPress = { onToggleFavorite() })` under `LONG_PRESS`.
- [ ] 7.3 Swap Tier 2 under `LONG_PRESS`: render tokens via `withStyle(tokenStyle)` (non-clickable) instead of `LinkAnnotation.Clickable`; card-level detector hit-tests the long-press against `TextLayoutResult` (per-`Text` `onTextLayout` + `onGloballyPositioned`) for both title and subtitle (D3 — a real rewrite of Tier 2 only; entity/DAO/migration/repository/`toggleFavorite`/derived list/cascade/`FavoritesScreen` are unchanged).
- [ ] 7.4 Re-run the Phase 6.4 on-device checklist under `LONG_PRESS` and record the deviation from the locked double-tap decision.

## Phase 8: Cleanup

- [x] 8.1 Confirm rollback path: `favorite_affirmations` stays additive; no `MIGRATION_8_7` shipped (documented, matches `customizable-affirmation-placeholders`' own precedent); partial rollback = remove Tier-1 modifier + Tier-2 arbiter routing + `FavoritesEntryCard` while keeping schema/repository (zero data loss).
- [x] 8.2 Record final gesture choice (`DOUBLE_TAP` or the `LONG_PRESS` fallback) and the on-device verification outcome in the proposal/design as the shipped state. **Shipped: `DOUBLE_TAP`**, verified on-device by the user — no conflict with `VerticalPager` swipe or token single-tap-to-edit. Phase 7 `LONG_PRESS` fallback not needed and not implemented.
