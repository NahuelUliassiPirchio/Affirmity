# Exploration: Curated affirmation catalog (2,712 ES affirmations) integration

## Current State (re-verified against real files)

**Room entity** — `app/src/main/java/com/pirxhio/affirmity/data/local/AffirmationEntity.kt:7-18`:
```kotlin
@Entity(tableName = "affirmations")
data class AffirmationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val subtitle: String,
    val backgroundType: String,
    val backgroundValue: String,
    @ColumnInfo(defaultValue = PERSONALIZADAS_GROUP_ID) val groupId: String = PERSONALIZADAS_GROUP_ID,
    @ColumnInfo(defaultValue = "{}") val overrides: Map<String, String> = emptyMap(),
)
```
No `text`/`tone`/`semanticAngle`/`collectionId`/`themeId`/`universeId` fields exist. `groupId` is a flat string, one level only.

**ID generation** — `app/src/main/java/com/pirxhio/affirmity/data/AffirmityAppState.kt:133`: `val id: String = UUID.randomUUID().toString()`. Random per-instance UUIDs, no deterministic/stable ID scheme today.

**Bracket placeholder tokens** — `app/src/main/java/com/pirxhio/affirmity/data/AffirmationTemplate.kt:39-97`. `AffirmationTemplateParser.parse` scans title/subtitle with `Regex("""\[([^\[\]]*)]""")` and turns every `[...]` span into an editable `Token` segment; `[]` (empty) is demoted to literal. There is NO escape mechanism and NO way to mark a field "not tokenizable" — any literal `[`/`]` in imported catalog text (e.g. stylistic brackets, citations) will silently become a tap-to-edit token in the UI. Real risk for the 2,712-item ES corpus; needs a scan/sanitization pass before import.

**Groups / access** — `app/src/main/java/com/pirxhio/affirmity/ui/groups/AffirmationGroup.kt:21-88`. Exactly 4 groups exist: `personalizadas` (`PERSONALIZADAS_GROUP`, id from `PERSONALIZADAS_GROUP_ID`, always-selected, Pro-badged, `isThematic = false`) plus `bienestar` (Free), `autocuidado` (Pro), `fuerza_de_voluntad` (ProOrAdPerUse) — all three defined only as UI/access scaffolding with zero seeded affirmations today. No Universe→Theme→Collection→Affirmation hierarchy exists anywhere in the codebase; `groupId` is the only categorization axis.

**Access model** — `app/src/main/java/com/pirxhio/affirmity/access/ContentAccess.kt:23-33` (`ContentAccess(requiredTier: AccessTier, adUnlock: AdUnlockPolicy)`, tiers FREE/PRO, adUnlock NONE/PER_USE/ONE_TIME_TRIAL) and `app/src/main/java/com/pirxhio/affirmity/access/ContentKey.kt:6-9`. **Critical granularity finding**: `ContentType` enum has exactly 3 members — `AFFIRMATION_GROUP`, `MEDITATION`, `CUSTOM_AFFIRMATION_SLOT`. There is no `ContentType.AFFIRMATION_COLLECTION` or per-affirmation access type. Access today resolves **per group**, not per collection/affirmation. ChatGPT's `Collection.access{tier, rewardedUnlockHours}` (confirmed in `AFFIRMATIONS_MIGRATION.md` lines 216-248, 315-317, 361-362, and `editorialPolicy.accessInheritance: "affirmation_inherits_collection"` in the JSON header) assumes per-collection gating one level below Theme — finer-grained than anything `ContentAccess`/`ContentType` support today. Adding collection-level access would require extending `ContentType` (new enum constant, non-breaking per the file's own doc comment) — a real design decision, not just data mapping.

**Storage model — per-user, not shared** — `app/src/main/java/com/pirxhio/affirmity/data/remote/FirestorePaths.kt:10`: `fun affirmationsCollection(uid: String): String = "users/$uid/affirmations"`. Every affirmation lives under `users/{uid}/affirmations/{id}` — confirmed no shared/global Firestore path anywhere in `FirestorePaths.kt` (all 13 files in `data/remote/` checked, none define a top-level `catalogAffirmations` or similar). Each user's copy is independently mutable (title/subtitle/overrides).

**Migration mechanism — wrong tool for catalog seeding** — `app/src/main/java/com/pirxhio/affirmity/data/remote/FirestoreMigrator.kt:41-44`: `ensureMigrated` is a no-op once `users/{uid}/meta/migrated` exists (checked via `FirestorePaths.migratedMarkerDoc`). This is a ONE-TIME per-user Room→Firestore migration gate, confirmed by its own doc comment ("First sign-in copies existing data"). Reusing it for catalog seeding is wrong: already-migrated users would never receive new catalog content (marker already set), and if reused per-catalog-item it would write private per-user copies rather than a shared catalog — duplicating 2,712 docs × N users instead of seeding once.

**Reusable pattern** — `MigrationPlan.chunkWithMarkerLast` (`app/src/main/java/com/pirxhio/affirmity/data/remote/MigrationPlan.kt:37-95`, `MAX_OPS_PER_CHUNK = 450`) is a clean, tested, idempotent chunked-batch-with-trailing-marker pattern (`DocWrite` list → chunks of ≤450 ops, marker doc always last). Worth reusing as a *pattern* for a new, separate catalog-seeding planner — not by routing through `FirestoreMigrator` itself.

**Favorites** — `app/src/main/java/com/pirxhio/affirmity/data/local/FavoriteAffirmationEntity.kt:6-10`: `@PrimaryKey val affirmationId: String` + `favoritedAtMillis: Long`. Loosely references `AffirmationEntity.id` by string, no FK constraint. Low risk as long as catalog affirmation IDs are unique and stable (never reassigned/reused).

## ChatGPT source material — confirmed structure

`/Users/pirxhion/Downloads/AFFIRMATIONS_MIGRATION.md` (692 lines) + `affirmations-catalog.v1.json` (31,430 lines):
- Header: `schemaVersion 1`, `catalogVersion "1.0.0"`, `locale: "es"`, hierarchy `Universe > Theme > Collection > Affirmation`, `editorialPolicy.accessInheritance: "affirmation_inherits_collection"`, `archiveInsteadOfDelete: true`.
- Registries: `tones` (soft/direct/powerful/manifestation), `semanticAngles` (identity/reframe/acceptance/self_compassion/agency/action/permission/perspective/evidence/awareness/presence/grounding/...), plus `desiredStates`, `contexts`, `moments`, `conceptTags` — closed vocabularies, migration must fail before writing if a reference isn't registered.
- Access on `Collection`: `{ tier: "free"|"pro", rewardedUnlockHours: number|null }`, invariant `tier=free ⇒ rewardedUnlockHours=null` (line 397) — maps loosely onto `AccessTier` + `AdUnlockPolicy.ONE_TIME_TRIAL`/`PER_USE` conceptually but declared one level (Collection) below where `ContentType` currently gates (Group).
- Deterministic dotted IDs (`self_worth.feeling_enough.intrinsic_worth`) declared non-negotiable, never Firestore auto-IDs — directly conflicts with Affirmity's `UUID.randomUUID()` scheme, confirming the user's explicit instruction to keep Affirmity's ID logic is the right call. Catalog affirmations still need *some* ID strategy, just not ChatGPT's dotted scheme reused verbatim.
- "No tocar datos de usuario" and idempotency principles are compatible with keeping catalog and per-user data on separate storage paths.

## Two viable integration paths (not choosing — proposal-phase decision)

### Path A — Per-user copy (like `personalizadas` today)
- Schema: extend `AffirmationEntity`/Firestore doc with an optional `catalogId`/`sourceId` (stable pointer back to catalog item); otherwise reuses the existing per-user `users/{uid}/affirmations/{id}` shape as-is.
- Group/access wiring: catalog affirmations get copied into new groups (seeded `bienestar`/`autocuidado`/`fuerza_de_voluntad`, or new groups mapped from Universes/Themes) at first access or sign-up; access stays resolved at group granularity exactly as today — no `ContentType` change needed.
- Placeholder tokens: fully compatible as-is (each user's copy is already token-parsed); still needs bracket-sanitization on import since catalog text was never authored with tokens in mind.
- Offline: works out of the box — Room already caches per-user rows.
- Sync cost: heavy on first grant — copying up to 2,712 docs into every user's `affirmations` collection is expensive (storage ×N users, write cost, sync bandwidth) unless scoped down to only the groups/themes a user actually selects.
- Favorites: trivial, no change — `affirmationId` still points at a real per-user row.
- Effort: **Medium** — reuses 100% of existing read/write/sync/token/access code paths; main work is the seeding/copy mechanism and taxonomy→group mapping.

### Path B — Shared, read-only catalog (new `catalogAffirmations/{groupId}/{id}` or similar top-level path)
- Schema: brand-new shared Firestore collection + new Room cache table separate from `AffirmationEntity` (or a discriminated union with a `source` flag) — materially bigger schema change, since `AffirmationEntity` has no concept of "not mine, not editable, globally shared" today.
- Group/access wiring: needs the `ContentType` extension noted above if access should gate at Collection/Theme granularity rather than whole-group; simpler if access still resolves per-group.
- Placeholder tokens: `overrides` today are per-user and stored on the same row as title/subtitle. A read-only shared catalog row has no natural per-user override slot — needs a *separate* per-user overrides table/doc keyed by `(uid, catalogAffirmationId)`, a real new sync surface, not a reuse of the existing pattern.
- Offline: needs an explicit local cache/sync strategy for the shared catalog (bundled JSON asset + Firestore delta sync, or full Firestore offline persistence) — more design work than "Room already has it."
- Sync cost: cheap and correct — content seeded once server-side, all users read the same docs; no N-times duplication.
- Favorites: needs to work across two ID spaces (own affirmations + catalog affirmations) — `FavoriteAffirmationEntity.affirmationId` still works as a loose string FK either way, but favoriting UI/query logic must merge two sources.
- Effort: **High** — new collection, new Room tables/cache, new per-user override sync, possible `ContentType` extension, offline strategy design.

## Keep vs discard from ChatGPT material

**Keep**: the 2,712 ES affirmation texts (content only), and the general idea that richer thematic categorization beyond 4 flat groups adds value (Universe/Theme concepts as *inspiration* for a taxonomy, not literal import).

**Discard / do not import verbatim**:
- Deterministic dotted ID scheme — conflicts with existing `UUID.randomUUID()` convention; user explicitly wants existing ID logic kept. A new ID strategy for catalog content still needs deciding (proposal phase), but it is not "reuse ChatGPT's IDs as-is."
- Full 4-level Universe→Theme→Collection→Affirmation hierarchy as a literal schema — `groupId` is flat by design; 4 levels is a bigger architectural change than "richer categorization," needs scoping down.
- `Collection.access{tier, rewardedUnlockHours}` model — doesn't map onto `ContentAccess`/`ContentType` at that granularity today; must be re-derived from existing access primitives, not imported as a parallel system.
- Flat top-level Firestore collections (`affirmationUniverses/`, etc.) — conflicts with today's `users/{uid}/...` per-user schema convention; only relevant under Path B, and even then needs to fit existing `FirestorePaths` conventions.
- Metadata registries (tones, semanticAngles, desiredStates, contexts, moments, conceptTags) — no current consumer in the codebase; importing them is speculative scope unless a concrete feature (e.g. mood-based filtering) is scoped for this change.
- `FirestoreMigrator`/per-user migration reuse — explicitly wrong, confirmed above.

**Reuse as pattern (not verbatim)**: `MigrationPlan.chunkWithMarkerLast`'s idempotent chunked-batch-with-marker-last discipline, and the source material's "archive not delete" principle — both fit cleanly regardless of which path is chosen.

## Additional risks/unknowns surfaced by fresh reads

1. **`ContentType` enum extension is a real design fork, not incidental.** Only 3 `ContentType` values exist and access resolves per-group. If the product wants Theme/Collection-level tiering (as ChatGPT's model assumes), that's a deliberate `ContentAccess`/`ContentType` extension decision that belongs in `sdd-design`, not something that falls out of "just map the data."
2. **Bracket token collision is a concrete data-quality risk**, not just theoretical: `AffirmationTemplateParser`'s regex has no escape syntax. The 2,712-item corpus needs to be scanned for literal `[`/`]` characters before any import; hits must be removed/rewritten in content or the parser extended with an escape mechanism (e.g. `\[`). A real scan of `affirmations-catalog.v1.json` for `[`/`]` inside affirmation text fields is recommended before proposal.
3. **`overrides` placement differs materially by path.** Path A gets it for free (same row). Path B requires an entirely new per-user override sync surface — a bigger hidden cost than "new collection" alone suggests, and directly affects whether tap-to-edit placeholders (shipped feature, commit `f8bf6aa`) keep working on catalog content at all.
4. **No `locale` field exists anywhere in `AffirmationEntity` or the app** — the ChatGPT catalog is "es" only, matching the app's apparent target language, but there is zero infrastructure for locale-aware content today; worth confirming this is out of scope rather than assumed.
5. **Group cardinality mismatch**: 14 Universes vs. today's 4 groups. Mapping Universe→Group 1:1 would grow the group selector UI (`selectableAffirmationGroups()`) from 4 to potentially 15 entries — a UX/product decision with real UI implications (`ui/groups/AffirmationGroup.kt`), not purely backend data.

## Ready for Proposal

Yes. Both paths are concretely scoped with file-level implications. The proposal phase needs to close on:
1. Per-user-copy vs shared-catalog-read.
2. Taxonomy depth (flat groups vs Universe-as-groups vs deeper).
3. Whether Theme/Collection-level access gating is in scope for v1 or deferred.
4. Locale scope confirmation.
5. Bracket-token collision handling strategy after a real scan of the source JSON.
