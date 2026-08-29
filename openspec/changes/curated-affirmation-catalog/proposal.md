# Proposal: Curated Affirmation Catalog

## Intent

Three of the app's four affirmation groups (`bienestar`, `autocuidado`, `fuerza_de_voluntad`) are access scaffolding with **zero seeded content** — a user who unlocks Pro today gets empty groups. The only real content path is `personalizadas`, where the user writes everything themselves. A curated 2,712-item Spanish corpus exists and is ready to import. This change ships that catalog as shared, read-only content organized into ~15 groups, keeping the shipped tap-to-edit placeholder and favorites features working on top of it.

## Scope

### In Scope

- **Shared read-only catalog** at a new top-level Firestore path `catalogAffirmations/{catalogAffirmationId}` (flat, with `groupId` + `themeId` fields for querying). One copy for all users — no per-user duplication of 2,712 docs.
- **New Room cache table** `catalog_affirmations`, separate from `affirmations`/`AffirmationEntity`. Catalog rows are not user-owned, not editable, not deletable. Additive migration `8 -> 9`.
- **Stable catalog ID scheme**: `cat_{universeSlug}_{themeSlug}_{nnn}`. The `cat_` prefix guarantees zero collision with `UUID.randomUUID()` (UUIDs contain no underscores). IDs are permanent — favorites and overrides reference them. `UUID.randomUUID()` stays untouched for user-owned content.
- **New per-user overrides sync surface**: `users/{uid}/catalogOverrides/{catalogAffirmationId}` + Room table `catalog_affirmation_overrides`, keyed by catalog ID. Required because `overrides` currently lives on the same mutable row as title/subtitle (`AffirmationEntity.kt:15`) — a shared read-only row has no per-user slot.
- **14 Universes become 14 new `AffirmationGroup` entries**; the selector grows from 4 to ~15.
- **`ContentType` extension** with a new constant for theme-level gating, so a theme can carry its own Free/Pro/ad-unlock tier independent of its parent group. `wireName` MUST be camelCase (e.g. `affirmationTheme`) — the `ContentKey.storageKey` invariant splits on the FIRST underscore (`ContentKey.kt:3-5`). Effective access = theme's own `ContentAccess` if declared, else the parent group's.
- **Bracket sanitization as a pre-import gate**: scan all 2,712 texts for literal `[`/`]`, strip/rewrite every hit, and fail the import if any remain. `AffirmationTemplateParser` is NOT modified.
- **New catalog seeding mechanism** reusing the `chunkWithMarkerLast` pattern (`MigrationPlan.kt:37-95`) — idempotent chunked batches, version marker written last, gated on a `catalogVersion` doc. Explicitly NOT routed through `FirestoreMigrator`.
- **Favorites across two ID spaces**: favorites query/UI merges personal + catalog affirmations.
- **Firestore security rules**: `catalogAffirmations` is world-readable, client-write-denied.

### Out of Scope

- **Theme/Collection as UI browse levels.** Themes exist as content metadata + access units only; the UI still browses one level (groups).
- **i18n / locale.** Spanish only. No `locale` field on any schema. English content is a separate future change.
- **Metadata registries** (`tones`, `semanticAngles`, `desiredStates`, `contexts`, `moments`, `conceptTags`) — no consumer exists; not imported.
- ChatGPT's dotted deterministic IDs, its flat `affirmationUniverses/` layout, and its `Collection.access{tier, rewardedUnlockHours}` model.
- `FirestoreMigrator` reuse for seeding.
- Mood/tone-based filtering, search, or recommendation over the catalog.
- Editing, deleting, or reordering catalog affirmations. Users can only override placeholder tokens and favorite them.

## Capabilities

### New Capabilities

- `affirmation-catalog`: shared read-only catalog storage, catalog ID scheme, group taxonomy expansion, catalog seeding + versioning, bracket sanitization gate.
- `catalog-token-overrides`: per-user placeholder overrides on shared catalog content, keyed by `(uid, catalogAffirmationId)`.

### Modified Capabilities

- `data-sync`: the per-user Firestore schema gains `users/{uid}/catalogOverrides`, and a new shared, non-per-user top-level `catalogAffirmations` collection is introduced alongside it. Security rules requirement extends to the shared path.
- `affirmation-favorites`: `affirmationId` may now reference a catalog ID; the list merges two sources and must not orphan on catalog content.

## Approach

Keep catalog and user data on **separate storage paths** rather than discriminating one table, so "not mine, not editable" is a type-level fact instead of a runtime flag — user-owned write paths physically cannot touch catalog rows. The read model unifies them: a presentation-level affirmation is `catalog row + optional user overrides` or `owned row`, resolved above the repository layer so `AffirmationsScreen` and `AffirmationTemplateParser` stay unchanged.

Offline is served by the Room cache: the seeded catalog syncs down once and is refreshed only when the remote `catalogVersion` marker advances, so steady-state reads are local and free.

Sanitization runs **before** any write, as a build/import-time step over the source JSON producing a clean seed artifact — content arrives clean rather than the parser learning an escape syntax.

**Strict TDD**: ID-scheme generation/validation, sanitization scanning, the seed-plan chunking, override merge resolution, effective-access resolution (theme vs. group), and favorites cross-space merge are all plain JVM unit tests. Migration `8 -> 9` gets `androidTest` coverage.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `data/remote/FirestorePaths.kt` | Modified | `catalogAffirmationsCollection()`, `catalogAffirmationDoc()`, `catalogVersionDoc()`, `catalogOverridesCollection(uid)`, `catalogOverrideDoc(uid, id)` |
| `data/local/CatalogAffirmationEntity.kt` | New | Shared catalog row: `id` PK (`cat_*`), title, subtitle, background, `groupId`, `themeId` |
| `data/local/CatalogOverrideEntity.kt` | New | `catalogAffirmationId` PK, `overrides: Map<String, String>` |
| `data/local/AffirmityDatabase.kt` | Modified | `MIGRATION_8_9` (two `CREATE TABLE`), version `8 -> 9`, two new entities + DAOs |
| `data/repository/` | New/Modified | Catalog repository (read-only) + catalog-override repository; wired into `Repositories.kt` / `DataSession.kt` |
| `data/remote/CatalogSeedPlan.kt` | New | Chunked idempotent seed plan, marker-last, modeled on `MigrationPlan.kt:37-95` |
| `access/ContentKey.kt` | Modified | New `ContentType` constant with underscore-free `wireName` |
| `access/ContentAccess.kt` + resolver | Modified | Theme-level access with group fallback |
| `ui/groups/AffirmationGroup.kt` | Modified | 14 new groups from Universes; selector grows 4 → ~15 |
| `data/AffirmityAppState.kt` | Modified | Merged catalog+owned read model, override writes on catalog IDs |
| `ui/favorites/FavoritesScreen.kt` | Modified | Merge two ID spaces |
| `tools/` or `app/src/main/assets/` | New | Sanitized seed artifact + sanitization/scan step |
| `app/src/test/`, `app/src/androidTest/` | New/Modified | Unit coverage above; `8 -> 9` migration test |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Bracket collision: literal `[`/`]` in source text silently becomes an editable token | **High** | Sanitization is a hard pre-import gate — import fails if any survive. Verified by a scan test over the seed artifact |
| New `ContentType.wireName` contains `_` and silently corrupts every `ContentKey.storageKey` | Med | Existing unit-tested invariant (`ContentKey.kt:3-5`); add an explicit test for the new constant |
| Selector UX degrades at ~15 groups | Med | Flag for design — grouping/scroll/search in the selector sheet. Data model is unaffected either way |
| Catalog seeding partially applied (network drop mid-batch) | Med | Marker-last + idempotent chunking: a re-run re-applies safely; no version marker means "not seeded" |
| Cold-start cost of syncing 2,712 rows into Room | Med | Ship the sanitized seed as a bundled asset and treat Firestore as delta/refresh only — design decides bundled-vs-remote-first |
| Overrides orphaned when a catalog item is archived | Low | "Archive not delete" for catalog content; orphaned overrides never render because the read model derives from live catalog rows |
| Client writes to `catalogAffirmations` | Low | Security rules deny all client writes; seeding runs privileged |
| PR far exceeds the 400-line review budget | **High** | Flag for `sdd-tasks`. Natural slices: (1) sanitization + seed artifact + ID scheme, (2) storage/schema/migration + seeding, (3) access + groups taxonomy, (4) overrides + favorites + UI merge |

## Rollback Plan

1. Revert the feature branch. Both new Room tables are standalone and additive — `affirmations`, `favorite_affirmations`, and every existing column are untouched, so pre-change code reads unchanged.
2. Room downgrade: an already-migrated device sits at v9. Either keep `MIGRATION_8_9` registered on the rollback build or ship a `9 -> 8` `DROP TABLE` migration — decide in design before merge.
3. Firestore: `catalogAffirmations` and the version marker can be left in place harmlessly (no client reads them post-revert) or deleted; `users/{uid}/catalogOverrides` is per-user, additive, and ignored by pre-change code.
4. Partial rollback: remove the 14 new groups from `selectableAffirmationGroups()` while keeping schema, seeding, and repositories. The catalog becomes unreachable with zero data loss.

## Dependencies

- The 2,712-item source corpus (`affirmations-catalog.v1.json`) must pass sanitization before any other task starts.
- A privileged seeding path (admin SDK, Firebase console import, or a gated debug build) — client code cannot write the shared collection.
- Builds on the existing `DataSession`/`ready()` gate, `ContentAccess` primitives, and `AffirmationTemplateParser` (unchanged).

## Success Criteria

- [ ] `gradlew.bat assembleDebug` and `gradlew.bat testDebugUnitTest` pass.
- [ ] Zero literal `[` or `]` survive in the seeded catalog; the sanitization scan test proves it.
- [ ] Every catalog ID matches `cat_*` and no catalog ID can collide with a `UUID.randomUUID()` value (tested).
- [ ] Seeding is idempotent: running it twice produces the same state and writes the version marker last.
- [ ] The group selector shows the new Universe-derived groups, and selecting one renders real affirmations from the shared catalog.
- [ ] A theme with its own Pro/ad-unlock tier gates independently of its parent group; a theme without one inherits the group's access.
- [ ] Tapping a `[token]` on a catalog affirmation opens the editor, and the saved override persists per user without mutating the shared catalog row.
- [ ] Double-tap favorites a catalog affirmation, and the Favorites screen lists personal and catalog favorites together.
- [ ] Catalog content renders offline after first sync.
- [ ] Room `8 -> 9` migration test green; existing `androidTest` suite green.

## Proposal question round

The five open decisions from exploration were closed by the user before this proposal and are settled scope — not re-opened here:

1. **Storage path**: shared read-only catalog (not per-user copy).
2. **Taxonomy depth**: Universes become groups; Themes/Collections stay internal metadata, not a UI browse level.
3. **Access scope**: per theme/collection, requiring the `ContentType` extension.
4. **Bracket tokens**: scan and sanitize the source; parser untouched.
5. **Locale**: Spanish only, no i18n infrastructure.

Open items routed to `sdd-design` (implementation shape, not product direction):

- Bundled seed asset vs. remote-first sync for the initial 2,712-row cold start.
- Selector UX at ~15 groups (grouping, scroll, or search in the sheet).
- Whether `catalogAffirmations` stays flat or is subcollectioned by group for query economy.
