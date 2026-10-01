# Proposal: User Collections (local, additive feed source)

## Intent

Users cannot group affirmations they care about into reusable sets. Add Spotify-playlist-like **user collections**: build them via long-press, toggle them as feed chips. Ship the state/API first so a future designed UI can bind to it.

## Scope

### In Scope
- Room tables `user_collections` (UUID id, name, createdAtMillis, enabled, lastUsedAtMillis) and `user_collection_items`; migration v13 -> v14.
- `UserCollectionRepository` (intent-based, NoOp default) outside `DataSession`.
- AppState API: list, create (optionally with an affirmation), rename, delete (cascade), add/remove membership, toggle enabled.
- Rules: Free max 2 collections, Pro unlimited; downgrade keeps everything usable, blocks only new creation while over limit. Names trimmed, non-blank, max 40, case-insensitive unique. New collections start enabled.
- Feed: enabled collections are ADDED to themes/favorites/own, deduped; hidden outranks; Pro-locked rows are excluded; "any collection enabled" counts as a valid feed source.
- Chips: live toggles, ordered by `lastUsedAtMillis` desc (set at creation, updated only on toggle ON).
- Long-press "Add to collection" row -> list (checked if member) + "Create new collection"; delete confirmation.
- Owned-affirmation delete and JSON import replace clear memberships.

### Out of Scope
- Firestore sync, collection detail screen, chip reordering, item limits, final visual design.

## Capabilities

### New Capabilities
- `user-collections`: collection lifecycle, naming rules, tier limit, membership, enabled state, chip ordering, cleanup hooks, persistence/migration.
- `feed-composition`: feed source union (own, favorites, themes, enabled collections), dedupe, hidden/access precedence, draft validity.

### Modified Capabilities
- None (no existing spec covers the feed or collections).

## Approach

Exploration Approach 1: Room tables with `enabled` column, repository mirroring favorites (ctor param, NoOp default), a pure id-union function for the feed, live chip writes serialized by a Mutex. Tier read from `entitlementTier`. Strict TDD against a fake repository. No new dependencies (minSdk 24 unaffected).

## Affected Areas

| Area | Impact |
|------|--------|
| `app/src/main/java/com/pirxhio/affirmity/data/` (entities, DAO, `AffirmityDatabase`, `Repositories.kt`, Room repo) | New/Modified |
| `data/AffirmityAppState.kt` (`filteredAffirmations`, validity, cleanup hooks, API) | Modified |
| `AffirmationsScreen.kt` (actions sheet), `MainActivity.kt` (chips), `strings.xml` | Modified |
| `app/schemas/.../14.json`, androidTest migration/DAO tests, unit tests | New |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Migration SQL differs from exported schema (crash on upgrade) | Med | `MigrationTestHelper` test against `14.json` |
| Sheet blocks a feed carried only by collections | Med | Extend validity function and test it |
| Orphan memberships inflate counts | Med | Counts from resolved affirmations; cleanup hooks |
| Cross-account name leak on shared device | Low | Known limitation, documented |
| `enabled` is device-local if sync arrives | Low | Keep out of future sync DTO |

## Rollback Plan

Migration is additive (CREATE TABLE/INDEX only). Revert the PR to remove code; Room cannot downgrade v14 -> v13 without `fallbackToDestructiveMigrationOnDowngrade`, so for released builds ship a forward v15 that drops both tables instead of reverting the version number.

## Review Workload Forecast

- Estimated changed lines: 600-800 (data ~150, repo ~80, AppState ~120, UI ~150, tests ~300).
- 400-line budget risk: High
- Chained PRs recommended: Yes
- Decision needed before apply: Yes (`single-pr` requires `size:exception`, or split into (a) data + migration, (b) AppState + feed union, (c) UI).

## Success Criteria

- [ ] v13 -> v14 migration test passes; existing data intact.
- [ ] Enabled collections add their affirmations once; hidden and Pro-locked rows excluded.
- [ ] Free user blocked at a 3rd collection; downgraded users keep all collections.
- [ ] Chip order follows last toggle-on; deleting an owned affirmation or replace-import leaves no memberships.
- [ ] `./gradlew :app:testDebugUnitTest` green.
