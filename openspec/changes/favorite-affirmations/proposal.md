# Proposal: Favorite Affirmations

## Intent

Users swipe through a `VerticalPager` feed of affirmations and have no way to keep the ones that land. A line that resonates is lost as soon as the pager moves on; the only "keep" affordance today is manually recreating it in "Mis afirmaciones". This change adds a zero-friction double-tap-to-favorite gesture on the affirmation card plus a Favorites screen listing what the user kept, so resonant affirmations are recoverable on demand.

## Scope

### In Scope
- **Double-tap toggle** on `AffirmationCard`: double-tap favorites, double-tap again unfavorites. Works **anywhere on the card, including on a bracketed `[token]`** — the outer card double-tap detector wins over the token's single-tap-to-edit `LinkAnnotation.Clickable`. Single-tap-to-edit stays unchanged (different gesture type, not a different hit zone).
- **Local Room persistence** in a new standalone `favorite_affirmations` table (`affirmationId` PK, `favoritedAtMillis`), following the `ad_unlock` / `daily_completion` standalone-table convention — **not** a boolean column on `AffirmationEntity`. Additive migration `7 -> 8`.
- **Favorites screen** reached via a menu item, using the existing `MyAffirmationsScreen` pattern: a button toggles a `showFavorites` boolean that renders a full-screen `Scaffold` + `BackHandler` overlay before the `NavigationSuiteScaffold` branch. **Not** a new `AppDestinations` entry.
- **Instant unlike** from the Favorites list — no confirmation dialog, no undo snackbar, matching the directness of double-tap-to-add.
- **Empty state** when nothing is favorited; list ordered by `favoritedAtMillis` (most recent first).
- `AffirmityAppState.toggleFavorite(id)` + derived `favoriteAffirmations` following the existing `setTokenOverride` / `removeAffirmation` mutation pattern.
- **On-device verification** that double-tap does not conflict with `VerticalPager` drag arbitration (see Risks — this is an acceptance item, not a deferral).

### Out of Scope
- **Firestore sync** — favorites are local-only this slice. Acknowledged future step.
- Visual polish of the card feedback and the list (heart animation, colors, typography) — routed through the `impeccable` skill separately. This change specifies functional/structural shape only: list layout exists, remove affordance exists, empty state exists.
- Favorites in the Glance widget, notifications, or the affirmation feed filter.
- Favoriting from `MyAffirmationsScreen`, ordering/reordering by user, folders, or export.
- Entitlement gating — available to all users.

## Capabilities

### New Capabilities
- `affirmation-favorites`: double-tap toggle semantics, local favorite persistence, Favorites screen listing, instant unlike, empty state.

### Modified Capabilities
- None. Persistence is local-only, so the `data-sync` per-user Firestore schema is unchanged.

## Approach

Add a standalone Room table rather than a column so favorites carry their own lifecycle and a free chronological order, and so a later Firestore sync can diff by collection (mirroring `FirestoreAdUnlockRepository`). Expose favorite IDs as observable state on `AffirmityAppState` and derive the Favorites list from the existing in-memory `affirmations` list, so no duplicate affirmation storage exists. Attach `Modifier.pointerInput(affirmation.id) { detectTapGestures(onDoubleTap = ...) }` to `AffirmationCard`'s outer `Box`, which currently carries no pointer-input modifier. Extract the toggle handler as a plain function (like `handleGuidedMeditationSessionEnded` in `MainActivity.kt`) so the post-gesture logic is JVM-unit-testable independently of Compose.

**Strict TDD**: DAO round-trip, repository, `toggleFavorite` state logic, and derived-list ordering are plain JUnit tests (`app/src/test`). The gesture recognition itself and the pager/link arbitration are **not** provable by JVM unit tests — they require Compose UI test or manual on-device verification, which is an explicit task, not an assumption.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `data/local/FavoriteAffirmationEntity.kt` | New | `affirmationId` PK, `favoritedAtMillis` |
| `data/local/FavoriteAffirmationDao.kt` | New | `insert`/`delete`/`observeFavoriteIds(): Flow<Set<String>>` |
| `data/local/AffirmityDatabase.kt` | Modified | `MIGRATION_7_8` (CREATE TABLE), version `7 -> 8`, new entity |
| `data/repository/RoomFavoriteAffirmationRepository.kt` | New | Toggle + observe, per `RoomAdUnlockRepository` shape |
| `data/repository/Repositories.kt`, `DataSession.kt` | Modified | Wire the new repository into the session |
| `data/AffirmityAppState.kt` | Modified | `favoriteAffirmationIds`, `favoriteAffirmations`, `toggleFavorite` |
| `ui/affirmations/AffirmationsScreen.kt` | Modified | `detectTapGestures(onDoubleTap)` on `AffirmationCard`'s outer `Box` |
| `ui/favorites/FavoritesScreen.kt` | New | Overlay `Scaffold` + `BackHandler`, list, unlike, empty state |
| `MainActivity.kt` | Modified | `showFavorites` state, overlay branch, extracted toggle handler |
| `ui/groups/AffirmationGroupSelectorSheet.kt` | Modified | Menu entry point for Favorites |
| `app/src/test/.../` | New | DAO, repository, state-toggle, ordering tests |
| `app/src/androidTest/.../AffirmityDatabaseMigrationTest.kt` | Modified | `7 -> 8` migration coverage |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| **`VerticalPager` consumes the double-tap as part of drag detection** | Med | Explicit on-device verification task before merge. **Done means**: double-tap toggles reliably AND vertical swipe still changes affirmation, both verified manually. If they genuinely conflict, the fallback is a long-press toggle on the card (same zero-dialog directness); the DAO/state/screen layers are unchanged by that swap, so the fallback is a one-modifier edit, not a redesign |
| Double-tap on a `[token]`: first tap consumed by the token's `LinkAnnotation` | Med | Resolved by product decision — the card's double-tap wins. Design must confirm the concrete Compose mechanism (e.g. `pointerInput` in the initial pass or link-click guarded by a double-tap window) and cover it in the on-device check |
| Migration `7 -> 8` fails on existing installs | Low | Purely additive `CREATE TABLE IF NOT EXISTS`, mirrors `MIGRATION_1_2` / `MIGRATION_5_6`; `androidTest` migration test |
| Favorite row orphaned when its affirmation is deleted | Med | Favorites list derives from the live `affirmations` list, so orphans never render; design decides whether `removeAffirmation` also deletes the favorite row or orphans are pruned lazily |
| No sync: favorites lost on reinstall / new device | Med (by design) | Explicitly out of scope; standalone-table shape is chosen precisely to make the later Firestore collection sync a clean addition |
| No undo on unlike — accidental taps lose a favorite | Low | Accepted per product decision; re-favoriting costs one double-tap |
| PR exceeds the 400-line review budget | Med | Flag for `sdd-tasks` forecast; natural slice boundary is persistence+state (slice 1) vs. gesture+screen (slice 2) |

## Rollback Plan

1. Revert the feature branch. The `favorite_affirmations` table is standalone and additive — no existing table or column is touched, so pre-change code reads every affirmation unchanged.
2. Room downgrade: an already-migrated device is at v8 and Room rejects an unknown-higher version. Either keep `MIGRATION_7_8` registered on the rollback build or ship an `8 -> 7` `DROP TABLE` migration — decide in design before merge.
3. Firestore: untouched. No remote data written, nothing to clean up.
4. Partial rollback: remove the double-tap modifier and the menu entry point while keeping the schema and repository. The feature becomes invisible with zero data loss.

## Dependencies

- None external. No new Gradle dependency — Room, Compose foundation gestures, and the existing repository/session wiring cover it.
- Builds on the existing `DataSession` / `ready()` gate and the `MyAffirmationsScreen` overlay pattern.

## Success Criteria

- [ ] `gradlew.bat assembleDebug` and `gradlew.bat testDebugUnitTest` pass.
- [ ] Double-tapping an affirmation card favorites it; double-tapping again unfavorites it.
- [ ] Double-tapping directly on a bracketed `[token]` toggles the favorite and does **not** open the token editor; a single tap on a token still opens the editor.
- [ ] **On-device verified**: double-tap toggling and `VerticalPager` vertical swipe both work without interfering. If not achievable, the long-press fallback is implemented and verified instead, and the deviation is recorded.
- [ ] Favorites are reachable from the menu, rendered as a full-screen overlay dismissible with the back gesture — no new bottom-nav entry.
- [ ] The Favorites list shows favorited affirmations most-recent-first and an empty state when there are none.
- [ ] Unliking from the Favorites list removes the item immediately with no dialog and no snackbar.
- [ ] Favorites survive app restart (Room persistence verified by DAO/repository unit tests).
- [ ] Room `7 -> 8` migration test green; existing `androidTest` suite green.

## Proposal question round

Product decisions were locked by the user before this proposal: double-tap toggle (card-wide, token included), menu-item entry point per the `MyAffirmationsScreen` pattern, instant unlike with no confirmation/undo, local-only Room storage, standalone table, and visual polish deferred to the `impeccable` skill. All follow-up items are now confirmed:

1. **Favoriting mid-edit.** Confirmed: the favorite toggles and any in-progress token edit is left as-is (not committed, not cancelled). Favoriting and editing are independent state.
2. **Deleting a favorited affirmation.** Confirmed: `removeAffirmation` also hard-deletes the corresponding `favorite_affirmations` row immediately — no orphaned rows.
3. **Double-tap on other surfaces.** Confirmed: scope is the `AffirmationsScreen` feed card only. `MyAffirmationsScreen` cards do not gain the gesture in this change.
