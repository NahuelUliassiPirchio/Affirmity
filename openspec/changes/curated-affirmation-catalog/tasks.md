# Tasks: Curated Affirmation Catalog

Strict TDD Mode active. RED = failing test first, GREEN = minimum code to pass. Design revision 3 (D1-D19) and specs (affirmation-catalog, catalog-token-overrides, data-sync, affirmation-favorites) are authoritative. Where the spec's ID scheme (`cat_{universeSlug}_{themeSlug}_{nnn}`) conflicts with design D3 (`cat_` + verbatim dotted source id), **follow D3** — the design explicitly corrects the spec on measured grounds; spec text should be reconciled at archive time.

## Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines | ~2,600-3,200 (incl. generated `catalog.v1.json` ~150KB counted separately, excluded from review budget as generated data) |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR1 -> PR2 -> PR3 -> PR4 -> PR5 |
| Delivery strategy | auto-chain |
| Chain strategy | feature-branch-chain |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: feature-branch-chain
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Base branch | Notes |
|---|---|---|---|
| PR1 | `TIMED_REPEATABLE` ad-unlock policy, standalone | `feature/curated-affirmation-catalog` (tracker) | Touches shipped monetization code; independently revertible per design's own recommendation. |
| PR2 | Catalog foundation: transform, Room cache, seeding, access combinator | PR1 branch | Largest slice (~2712-row asset + taxonomy + migration). |
| PR3 | Override sync surface + read-model integration (favorites/feed merge) | PR2 branch | Depends on PR2's entities/DAOs. |
| PR4 | Legacy group removal + default-selection fix (D17/D18) + fixture repair | PR3 branch | Must land atomically: tests reference deleted groups. |
| PR5 | Selector UI (15 groups, partial-lock badge) + `functions/tools/seedCatalog.ts` + acceptance checks | PR4 branch | Merges into tracker; tracker merges to `master`. |

---

## Phase 1: PR1 — TIMED_REPEATABLE ad-unlock policy (standalone, first)

- [x] 1.1 RED `access/ContentAccessTest.kt`: `ContentAccess(TIMED_REPEATABLE, unlockWindowHours=null)` throws; any other policy with non-null hours throws; `ProOrAdTimed(24)` constructs. (Spec: Repeating Time-Limited Ad Unlock)
- [x] 1.2 GREEN `access/ContentAccess.kt`: add `AdUnlockPolicy.TIMED_REPEATABLE`, `unlockWindowHours: Int?`, `init` invariant, `ProOrAdTimed(hours)`.
- [x] 1.3 RED `access/AccessResolutionTest.kt`: no record -> `LockedAdUnlockable(TIMED_REPEATABLE)`; live record -> `UnlockedByAd`; **expired record -> `LockedAdUnlockable` again** (re-earnable, not spent); boundary `now == expiresAtMillis` is expired. Regression: full existing `ONE_TIME_TRIAL` table still passes byte-for-byte.
- [x] 1.4 GREEN `access/AccessResolution.kt`: add `TIMED_REPEATABLE` branch reading `grants.timedUnlocks[key]`; `ONE_TIME_TRIAL` branch untouched.
- [x] 1.5 GREEN `access/AdUnlockGrant.kt`: add `AdUnlockState.timedUnlocks: Map<ContentKey, AdUnlockRecord>`. `AdUnlockRecord` unchanged.
- [x] 1.6 GREEN `data/local/TimedAdUnlockEntity.kt` (new) + `data/local/TimedAdUnlockDao.kt` (new, `@Insert(REPLACE)`, `observeAll`).
- [x] 1.7 GREEN `data/local/AffirmityDatabase.kt`: `version = 9`, add `TimedAdUnlockEntity` + `MIGRATION_8_9` creating only `timed_ad_unlock` (catalog tables added in Phase 2). Generate `app/schemas/.../9.json`.
- [x] 1.8 RED `AffirmityDatabaseMigrationTest`: `migrate8To9` creates `timed_ad_unlock` empty; pre-existing `ad_unlock` row untouched. GREEN via 1.7's migration (androidTest).
- [x] 1.9 RED `AdUnlockDaoTest`/`TimedAdUnlockDaoTest`: second insert for same `contentKey` **REPLACES** — the exact inverse of `AdUnlockDao.insertIfAbsent`. Assert both DAOs in one suite (androidTest).
- [x] 1.10 GREEN `data/repository/Repositories.kt`: `AdUnlockRepository.observeTimedUnlocks()` / `grantTimedUnlock(record)`.
- [x] 1.11 GREEN `data/repository/RoomAdUnlockRepository.kt`, `data/remote/FirestoreAdUnlockRepository.kt`: implement the two new members.
- [x] 1.12 GREEN `data/remote/FirestorePaths.kt`: `timedUnlocksCollection(uid)`, `timedUnlockDoc(uid, key)`.
- [x] 1.13 RED `RewardedAdUnlockSourceTest`: `requestAdUnlock(key, TIMED_REPEATABLE, 24)` on `Earned` calls `grantTimedUnlock` with `expiresAtMillis == grantedAt + 86_400_000` and never `grantDurableUnlock`; null window grants nothing; `adUnitIdFor(TIMED_REPEATABLE)` returns timed unit, blank -> null.
- [x] 1.14 GREEN `data/AffirmityAppState.kt` (`requestAdUnlock` 3rd param, `TIMED_REPEATABLE` branch), `access/RewardedAdGateway.kt` (`AdUnitIds.timedRepeatable`), `access/RewardedAdUnlockSource.kt` (3rd `when` branch).
- [x] 1.15 GREEN `app/build.gradle.kts`: `admob.rewardedUnit.timedRepeatable` optional secret, falls back to `oneTimeTrial` unit id (not `requiredAdSecret`).
- [x] 1.16 GREEN `firestore.rules`: new `users/{uid}/timedUnlocks/{contentKey}` block (create+update allowed, delete denied, closed field set, id-identity check). `adUnlocks` block byte-identical.
- [x] 1.17 RED (`npm run test:rules`): owner create+update succeeds, delete fails, id-mismatch fails, `expiresAtMillis<=grantedAtMillis` fails, cross-uid denied. Regression: existing `adUnlocks` update/delete-denied suite still passes.

## Phase 2: PR2 — Catalog foundation (transform, cache, access combinator)

- [x] 2.1 GREEN `tools/catalog/generate-catalog.mjs`: source JSON -> `catalog.v1.json` + `CatalogTaxonomy.kt`. Fails on literal `[`/`]`, dup id, unknown ref, `free`+non-null-hours, non-positive hours. Emits `CATALOG_GATED_GROUP_IDS`. **Run manually and commit output.**
- [x] 2.2 RED `CatalogTextSanitizerTest`: `[`,`]`,`[]`,nested,unicode-adjacent -> correct offsets; clean text -> empty; **smoke assertion over the committed `catalog.v1.json`: zero illegal brackets across all 2712 texts**. (Spec: Pre-Import Bracket Sanitization Gate)
- [x] 2.3 GREEN `data/catalog/CatalogTextSanitizer.kt`: `findIllegalBrackets` (pure, no rewrite logic per D11).
- [x] 2.4 **Verification step (blocking, pre-merge):** run the sanitizer's smoke test against the generated `catalog.v1.json` and confirm 0 hits before any further slice depends on the asset. Record the run in the PR description. — **Ran, 0 hits across 2712 texts** (`CatalogTextSanitizerTest`, see apply-progress).
- [x] 2.5 RED ID-scheme test: every id matches `^cat_[a-z0-9_]+(\.[a-z0-9_]+)+$`; all 2712 unique; none is a valid `UUID`; no `groupId` equals `personalizadas` or a legacy group id. Data-driven over committed asset.
- [x] 2.6 GREEN `data/catalog/CatalogAssetParser.kt`: parse + validate (dup id, unknown collectionId, bracket each throw naming the offending id). RED first with 3-row JSON fixtures, then GREEN. **Deviation**: `free`+non-null-hours is validated in the generator only (2.1), not duplicated here — the bundled asset carries no `access` field by design, so there is nothing to re-check at this layer; see apply-progress.
- [x] 2.7 GREEN `data/local/CatalogAffirmationEntity.kt` (id/text/groupId/themeId/collectionId/sortOrder + 2 indices), `data/local/CatalogAffirmationDao.kt` (`observeAll`, `observeByGroupIds`, `getByIds`, `count`, `replaceAll`).
- [x] 2.8 RED `CatalogSeederTest`: seeds when marker absent/stale; no-ops when current; throwing `saveSeededCatalogVersion` still leaves rows committed, re-seeds cleanly next call; marker written **after** DAO call (call-order assertion on recording fake).
- [x] 2.9 GREEN `data/catalog/CatalogSeeder.kt`, `data/local/CatalogPreferences.kt` (DataStore marker).
- [x] 2.10 GREEN `data/local/AffirmityDatabase.kt`: extend `MIGRATION_8_9` with `catalog_affirmations` + `catalog_affirmation_overrides` `CREATE TABLE IF NOT EXISTS` + 2 indices (index names must match Room's generated names exactly). Update `app/schemas/.../9.json`.
- [x] 2.11 RED `AffirmityDatabaseMigrationTest` (extend 1.8's suite): `migrate8To9` now creates **all three** new tables empty, both catalog indices present, every pre-existing column untouched (incl. `overrides`). GREEN via 2.10. (androidTest — cannot execute in this sandbox, no emulator; see apply-progress's pre-existing androidTest compile blocker.)
- [x] 2.12 RED `ContentKeyTest`: `AFFIRMATION_COLLECTION.wireName` contains no `_` (asserted over `ContentType.entries`); `parse(storageKey)` round-trips for **all 226** real collection ids from committed taxonomy; `fromWireName("affirmationGroup")` unaffected; `storageKey` satisfies `firestore.rules:71` identity. (Spec: ContentType Extension)
- [x] 2.13 GREEN `access/ContentKey.kt`: add `AFFIRMATION_COLLECTION("affirmationCollection")`.
- [x] 2.14 RED `AccessCombinationTest`: full 4x4 `mostRestrictive` truth table; `LockedNeedsPro` absorbing; `ONE_TIME_TRIAL` > `TIMED_REPEATABLE` > `PER_USE` strictness; `UnlockedByAd` provenance survives; commutative, associative, `Unlocked` identity.
- [x] 2.15 GREEN `access/AccessCombination.kt`: `mostRestrictive(a, b)`.
- [x] 2.16 RED `CatalogAccessPolicyTest`: `catalogAccessDecision` — `alwaysSelected` short-circuits first; free collection in Pro group -> locked (D6(a) regression guard); Pro collection in Free group -> locked; `collection == null` -> group decision unchanged.
- [x] 2.17 GREEN `ui/groups/CatalogAccessPolicy.kt`: `catalogAccessDecision(...)` (badge functions land in Phase 5).
- [x] 2.18 GREEN `data/repository/Repositories.kt`: `CatalogAffirmationRepository` + `NoOpCatalogAffirmationRepository`. `data/repository/RoomCatalogAffirmationRepository.kt` (new, 1:1 DAO delegation).

## Phase 3: PR3 — Override sync surface + read-model integration

- [x] 3.1 GREEN `data/local/CatalogOverrideEntity.kt`, `data/local/CatalogOverrideDao.kt` (`observeAll`, `upsert`, `deleteById`; empty map deletes row).
- [x] 3.2 GREEN `data/repository/Repositories.kt`: `CatalogOverrideRepository` + `NoOp`. `data/repository/RoomCatalogOverrideRepository.kt` (new).
- [x] 3.3 GREEN `data/remote/FirestoreCatalogOverrideRepository.kt` (new): mirrors `users/{uid}/catalogOverrides`; empty map deletes doc.
- [x] 3.4 GREEN `data/remote/FirestorePaths.kt`: `catalogAffirmationsCollection/Doc`, `catalogUniversesCollection/Doc`, `catalogThemesCollection/Doc`, `catalogCollectionsCollection/Doc`, `catalogVersionDoc`, `catalogOverridesCollection/Doc`.
- [x] 3.5 GREEN `data/repository/DataSession.kt`: add `catalogOverrides` as a per-user sealed-interface member (Room vs Firestore impl on auth swap, D9 revised). Update both session constructions and every `AffirmityAppState` test that builds a session. **Deviation**: given a NoOp `CatalogOverrideRepository` default on both `Local`/`Remote` instead of touching all 7 existing test-file `DataSession.Local(`/`.Remote(` call sites -- unlike `adUnlocks` (no default, would silently drop durable grant data), an empty override map is a safe default: v1.0.0's catalog is measured bracket-free (D11), so no existing fixture that omits this argument loses meaningful data. Documented on the `catalogOverrides` property itself. The two REAL production session constructions in `AffirmityAppState.kt` and the new catalog test file both wire real/recording repositories explicitly.
- [x] 3.6 RED `AffirmityAppStateCatalogTest` (write routing, D14): `setTokenOverride("cat_…")` hits `catalogOverrides`, never `ready().affirmations`; `setTokenOverride(uuid)` unchanged; `removeAffirmation("cat_…")` performs **zero** repository calls (Remote-write-tombstone guard). Recording fakes, assert absence not just presence.
- [x] 3.7 GREEN `data/AffirmityAppState.kt`: `Affirmation.source`/`collectionId`, `AffirmationSource` enum, `setTokenOverride` prefix routing, `removeAffirmation` early-return guard.
- [x] 3.8 RED `AffirmityAppStateCatalogTest` `filteredAffirmations` test (D7): owned-row filter verbatim (regression, covered by existing `AffirmityAppStateFavoritesTest`); locked-collection catalog row absent; same row present for Pro tier; deselecting a group removes its catalog rows.
- [x] 3.9 RED `AffirmityAppStateCatalogTest` `favoriteAffirmations` cross-space test (D10): personal + catalog id favorited together resolve in recency order; id in neither space drops out; **favorited catalog row whose collection is locked STILL appears** (pinned decision, Open Question 4).
- [x] 3.10 GREEN `data/AffirmityAppState.kt`: `allAffirmations` concatenation, catalog collector (`catalog.observeByGroupIds` + `session.flatMapLatest { catalogOverrides.observeAll() }`), `filteredAffirmations` access filter, `favoriteAffirmations` merge.
- [x] 3.11 RED `CatalogBackgroundsTest` (D4): deterministic per id across calls; ids within a universe span the palette; every universe id resolves.
- [x] 3.12 GREEN `ui/affirmations/CatalogBackgrounds.kt`.
- [x] 3.13 GREEN `firestore.rules`: 5 read-only catalog blocks (`catalogAffirmations`/`catalogUniverses`/`catalogThemes`/`catalogCollections`/`catalogMeta`, `allow read: if true; allow write: if false`) + owner-only `catalogOverrides` block.
- [x] 3.14 RED (`npm run test:rules`): unauthenticated read of `catalogAffirmations` succeeds; any client write fails; `catalogOverrides` unreadable/unwritable cross-uid, owner read/write succeeds. (Spec: Shared Catalog Storage Path, Per-User Override Sync Surface). **Written, not executed** -- `firebase` CLI is not installed in this sandbox (`sh: firebase: command not found`), same class of environment blocker as PR1's 1.17/task-C.12 suites in `functions/test/firestore.rules.test.ts`.

**PR3 carry-forward notes (recorded here, not silently fixed):**
- `CatalogSeeder` (built in PR2) is still never invoked from app startup anywhere in `main/`. No task in Phase 1-3 assigns wiring it into `rememberAffirmityAppState`/`MainActivity`'s init path, so the bundled catalog will not actually populate `catalog_affirmations` on a real device yet. Flagged for whoever owns Phase 4/5 or a dedicated follow-up task -- this is NOT a PR3 regression, the gap pre-dates PR3.
- Design D8's UI verification item ("`AffirmationCard` must render a blank subtitle without a layout gap") was inspected, not modified: `AffirmationsScreen.kt`'s `AffirmationCard` already guards the divider + subtitle `TokenizedAffirmationText` behind `if (affirmation.subtitle.isNotBlank())`, so an empty catalog subtitle already renders with zero layout gap. No code change needed. The formal on-device acceptance check remains task 5.10.1 in Phase 5.

## Phase 4: PR4 — Legacy group removal + default-selection fix + fixture repair

- [ ] 4.1 RED `ResolveSelectedGroupIdsTest` (D18, extend existing Android-free suite): `persisted = null` -> all 14 universes + `personalizadas`; `persisted = {"personalizadas","bienestar"}` w/ new 15 `knownIds` -> falls back to the 14 (**the Pro-tier bug case**); `persisted = {"personalizadas","self_worth"}` -> returned verbatim (healthy selection untouched); `persisted = emptySet()` -> fallback fires. Plus wiring assertion: `defaultAffirmationGroups().filter{isThematic && requiredTier==FREE}` has size 14. **This is the explicit RED-first Pro-tier invariant fix (D18) — write before touching `resolveSelectedGroupIds`.**
- [ ] 4.2 GREEN `data/AffirmityAppState.kt`: `defaultThematicGroupIds` re-expressed via `isThematic`; `resolveSelectedGroupIds` gains the thematic-emptiness fallback, tier-independent (moves invariant out of the `AccessTier.FREE`-guarded `deselectLockedGroups` branch).
- [ ] 4.3 RED legacy-removal test: `selectableAffirmationGroups()` has 15 entries, contains `personalizadas`, contains none of the 3 legacy ids; `resolveSelectedGroupIds(persisted=setOf("bienestar"),…)` drops it and lands on a valid default.
- [ ] 4.4 GREEN `ui/groups/AffirmationGroup.kt`: delete `bienestar`/`autocuidado`/`fuerza_de_voluntad`; `defaultAffirmationGroups() = catalogUniverseGroups()` (14).
- [ ] 4.5 GREEN `res/values/strings.xml`, `res/values-en/strings.xml`: delete the 6 legacy group strings; add 14 group titles + 14 descriptions per locale (from `universes[].title`/`coreNeed`). **Note (PR2 deviation):** the 14 `values/strings.xml` (es) title/description pairs were added early, in PR2, because `CatalogTaxonomy.kt`'s `catalogUniverseGroups()` needs real `R.string` ids to compile — this task now only needs to delete the 6 legacy strings; `values-en/` still needs the 14 entries (or an explicit decision to let it fall back to `values/`, matching the "Spanish only" scope note).
- [ ] 4.6 GREEN — **fixture repair, explicit tasks, not discovered mid-apply.** Repoint the 4 suites that source Free/Pro/PER_USE group fixtures from `defaultAffirmationGroups()` by literal legacy id to locally-constructed `AffirmationGroup` fixtures:
  - [ ] 4.6.1 `GroupAccessPolicyTest`
  - [ ] 4.6.2 `AdUnlockEndToEndTest`
  - [ ] 4.6.3 `AffirmityAppStateAdFunnelAnalyticsTest`
  - [ ] 4.6.4 `AccessDecisionPurityAnalyticsTest`
  - Acceptance: each suite resolves (compiles) and passes without referencing `bienestar`/`autocuidado`/`fuerza_de_voluntad`. Suites using legacy ids as opaque strings (`ContentKeyTest`, `AccessResolutionTest`, `FirestorePathsTest`, `FirestoreMappersTest`, `AdUnlockDaoTest`, `AdUnlockMigrationTest`, `ResolveSelectedGroupIdsTest`) are **not** touched.
- [ ] 4.7 Regression check: `proOnlyGroupIds` (`AffirmityAppState.kt:1295`) now evaluates empty; assert `EntitlementResolution.stripProOnlyGroups` is a documented no-op, not deleted.

## Phase 5: PR5 — Selector UI, partial-lock badge, seeder script, acceptance

- [ ] 5.1 RED `CatalogAccessPolicyTest` (D19): `deriveCatalogBadge` — unlocked+`isPartiallyLocked=true` -> `PARTIALLY_LOCKED`; unlocked+`false` -> null; **locked group -> PREMIUM/AD_UNLOCK, never PARTIALLY_LOCKED**; `personalizadas` -> its own PREMIUM override, never partial. Regression: full existing `GroupAccessPolicyTest` badge table passes byte-for-byte (`deriveBadge` untouched).
- [ ] 5.2 RED `CatalogAccessPolicyTest`: `partiallyLockedGroupIds` — `PRO` -> `emptySet()`, zero collection resolutions; `FREE` + empty grants -> exactly `CATALOG_GATED_GROUP_IDS`; `FREE` + live grant covering the only gated collection -> that universe drops out; expired grant -> universe returns. Plus consistency assertion: `CATALOG_GATED_GROUP_IDS` equals the set derived from `catalogCollections()` at runtime.
- [ ] 5.3 GREEN `ui/groups/CatalogAccessPolicy.kt`: `deriveCatalogBadge`, `partiallyLockedGroupIds`. `ui/groups/AffirmationGroup.kt`: add `GroupBadge.PARTIALLY_LOCKED`. `ui/groups/GroupAccessPolicy.kt` **left unmodified** — do not widen `deriveBadge`.
- [ ] 5.4 GREEN `ui/groups/AffirmationGroupSelectorSheet.kt`: sticky-header sections (*Mis afirmaciones* pinned first incl. Favorites/Add-custom cards, then *Temáticas*); new `partiallyLockedIds: Set<String> = emptySet()` param; line-213 call site uses `deriveCatalogBadge`; new `PARTIALLY_LOCKED` branch in `AffirmationGroupAccessBadge`. Lock/toggle/dim logic untouched.
- [ ] 5.5 GREEN `MainActivity.kt`: `partiallyLockedIds` memoized on `(entitlementTier, adUnlockState)`, passed to selector sheet.
- [ ] 5.6 GREEN `res/values/strings.xml` (es "Incluye Pro"), `res/values-en/strings.xml` (en "Includes Pro"): `affirmation_group_badge_partial`.
- [ ] 5.7 **Badge-uniformity acceptance check (blocking, pre-merge):** inspect the generator's emitted `CATALOG_GATED_GROUP_IDS` value directly — confirm it is the *measured* set from `generate-catalog.mjs`, not hardcoded/assumed to be all-14. Record the actual set size in the PR description even if it is 14/14.
- [ ] 5.8 GREEN `functions/tools/seedCatalog.ts` (new): Admin SDK, chunk at <=450 ops/batch, taxonomy first then affirmations, `catalogMeta/version` written last, idempotent `set(merge:true)`.
- [ ] 5.9 RED/GREEN `functions/test/seedCatalog.test.ts` (vitest + Firestore emulator): chunk boundaries respected; marker is the last op of the last chunk; second run is a no-op-equivalent; mid-run abort leaves no marker.
- [ ] 5.10 **Gap found in PR3 review, unassigned until now:** `CatalogSeeder` (built in PR2) is never invoked anywhere in the app-startup path — the local `catalog_affirmations` Room table stays empty on a real device without this. RED: a startup-path test asserting `CatalogSeeder.seedIfNeeded()` (or equivalent) is invoked once, idempotently, on `AffirmityAppState` construction (or the earliest reasonable hook — e.g. `MainActivity.onCreate`/app `Application` class, verify the existing pattern for other one-time-on-launch work such as `FirestoreMigrator.ensureMigrated` and follow it). GREEN: wire the call. Must not block first paint (fire on a background coroutine, matching D2's cold-start requirement) and must be safe to call on every launch (idempotent via `CatalogPreferences.seededCatalogVersion`, per D13).
- [ ] 5.11 Manual/on-device acceptance (blocking, before merge, not provable by unit suite):
  - [ ] 5.11.1 D8: catalog card with empty subtitle renders with no layout gap.
  - [ ] 5.11.2 Cold-start seed of 2712 rows does not visibly block first paint.
  - [ ] 5.11.3 15-row selector navigable, Aplicar reachable.
  - [ ] 5.11.4 D19: partially-locked row shows badge, remains checkable, not dimmed.
  - [ ] 5.11.5 D18: fresh install (cleared app data) opens with all 14 universes checked.
  - [ ] 5.11.6 D16: watch ad on a `TIMED_REPEATABLE` collection -> unlocks; advance device clock past window -> re-locks and re-offers ad (not `LockedNeedsPro`); watch again -> second grant persists.
  - [ ] 5.11.7 5.10's seeder wiring actually populates `catalog_affirmations` on a real first launch (confirms 5.11.2/5.11.3/5.11.5 have real data behind them, not an empty table).

## Phase 6: Cross-cutting close-out (part of PR5)

- [ ] 6.1 Confirm `AffirmationsScreen` received no new parameter (D9) — read-only verification, no code change expected.
- [ ] 6.2 Confirm `FavoriteAffirmationDao`/`FavoriteAffirmationEntity`/`FavoriteAffirmationRepository` unmodified (D10) — read-only verification.
- [ ] 6.3 Confirm no `MIGRATION_9_8` was added and `fallbackToDestructiveMigrationOnDowngrade` stays disabled (D15).
- [x] 6.4 Spec/design reconciliation — DONE during PR2/PR3 review, not deferred to archive: `specs/affirmation-catalog/spec.md`'s ID-scheme requirement corrected to `cat_` + verbatim dotted id (matches D3), and its access-resolution requirement corrected from theme-level to collection-level (matches D5, corrected after the PR2 fresh review found the drift).
